package app.pedal.tracking

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.altitude.AltitudeConverter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.pedal.MainActivity
import app.pedal.R
import app.pedal.container
import app.pedal.data.RideSummary
import app.pedal.data.TrackPointEntity
import app.pedal.tracking.LocationSource.toLive
import app.pedal.util.Fmt
import app.pedal.util.LatLon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Foreground service that records a ride. All mutable state is confined to a single
 * background thread (the [dispatcher]), so no locking is needed.
 */
class TrackingService : Service() {

    companion object {
        private const val ACTION_START = "app.pedal.action.START"
        private const val ACTION_PAUSE = "app.pedal.action.PAUSE"
        private const val ACTION_RESUME = "app.pedal.action.RESUME"
        private const val ACTION_STOP = "app.pedal.action.STOP"
        private const val EXTRA_DISCARD = "discard"

        fun start(context: Context) = ContextCompat.startForegroundService(context, intent(context, ACTION_START))
        fun pause(context: Context) = context.startService(intent(context, ACTION_PAUSE))
        fun resume(context: Context) = context.startService(intent(context, ACTION_RESUME))
        fun stop(context: Context, discard: Boolean) =
            context.startService(intent(context, ACTION_STOP).putExtra(EXTRA_DISCARD, discard))

        private fun intent(context: Context, action: String) =
            Intent(context, TrackingService::class.java).setAction(action)
    }

    private val thread = HandlerThread("tracking").apply { start() }
    private val dispatcher = Handler(thread.looper).asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val repo by lazy { container.repository }
    private val settings by lazy { container.settings }

    // Thread-confined state
    private var rideId = -1L
    private var startTime = 0L
    private var segment = 0
    private var paused = false
    private var acc: StatsAccumulator? = null
    private val segments = ArrayList<ArrayList<LatLon>>()
    private var lastLocation: LiveLocation? = null
    private var locationJob: Job? = null
    private var lastNotificationAt = 0L

    private var follower: RouteFollower? = null
    private var followedRouteId: Long? = null
    private var offRouteStreak = 0
    private var offRoute = false

    private val altitudeConverter by lazy {
        if (Build.VERSION.SDK_INT >= 34) AltitudeConverter() else null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Must enter the foreground promptly after startForegroundService().
                if (!enterForeground()) return START_NOT_STICKY
                scope.launch { startRide() }
            }
            ACTION_PAUSE -> scope.launch { setPaused(true) }
            ACTION_RESUME -> scope.launch { setPaused(false) }
            ACTION_STOP -> {
                val discard = intent.getBooleanExtra(EXTRA_DISCARD, false)
                scope.launch { stopRide(discard) }
            }
            else -> if (rideId == -1L) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        locationJob?.cancel()
        scope.cancel()
        thread.quitSafely()
        // If we die mid-ride, the unfinished ride is recovered from its points on next launch.
        if (TrackingSession.state.value.isActive) TrackingSession.publish(TrackingState())
        super.onDestroy()
    }

    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_TRACKING,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        true
    } catch (e: Exception) {
        // E.g. location permission revoked — we can't track without it.
        stopSelf()
        false
    }

    // --- Ride lifecycle ----------------------------------------------------------------------

    private suspend fun startRide() {
        if (rideId != -1L) return
        val now = System.currentTimeMillis()
        rideId = repo.startRide(now)
        startTime = now
        segment = 0
        paused = false
        acc = StatsAccumulator(settings.value.autoPause)
        segments.clear()
        segments.add(ArrayList())
        publish()
        locationJob = scope.launch {
            LocationSource.updates(
                this@TrackingService,
                intervalMs = 1000L,
                includeNetwork = false,
                emitLastKnown = false,
                looper = thread.looper,
            ).collect { onLocation(it) }
        }
    }

    private fun setPaused(value: Boolean) {
        if (rideId == -1L || paused == value) return
        paused = value
        if (!value) {
            // Resume: start a new segment so the gap isn't bridged with a straight line.
            segment++
            segments.add(ArrayList())
            acc?.breakSegment()
        }
        publish()
        updateNotification(force = true)
    }

    private suspend fun stopRide(discard: Boolean) {
        val id = rideId
        locationJob?.cancel()
        locationJob = null
        val a = acc
        if (id != -1L) {
            if (discard || a == null || a.recordedPoints < 2) {
                repo.deleteRide(id)
            } else {
                repo.finishRide(
                    id,
                    RideSummary(a.distance, a.movingTimeMs, a.maxSpeed, a.elevGain, a.elevLoss),
                    System.currentTimeMillis(),
                )
                TrackingSession.emit(TrackingEvent.RideSaved(id))
            }
        }
        rideId = -1L
        acc = null
        getSystemService(NotificationManager::class.java).cancel(Notifications.ID_ROUTE)
        TrackingSession.publish(TrackingState())
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Location processing -----------------------------------------------------------------

    private suspend fun onLocation(location: Location) {
        val a = acc ?: return
        if (rideId == -1L) return
        a.autoPauseEnabled = settings.value.autoPause

        val altitude = mslAltitude(location)
        val now = System.currentTimeMillis()
        // Guard against devices reporting bogus GPS clocks.
        val time = if (abs(location.time - now) > 60_000) now else location.time
        val live = location.toLive().copy(time = time)
        lastLocation = live

        checkRoute(live)

        if (!paused) {
            val fix = Fix(
                lat = location.latitude,
                lon = location.longitude,
                alt = altitude,
                time = time,
                speed = if (location.hasSpeed()) location.speed else null,
                accuracy = live.accuracy,
            )
            if (a.onFix(fix) == FixResult.RECORDED) {
                segments.last().add(LatLon(fix.lat, fix.lon))
                repo.addPoint(
                    TrackPointEntity(
                        rideId = rideId,
                        segment = segment,
                        lat = fix.lat,
                        lon = fix.lon,
                        ele = altitude,
                        time = time,
                        speed = (fix.speed ?: a.currentSpeed.toFloat()),
                        accuracy = fix.accuracy,
                    )
                )
            }
        }
        publish()
        updateNotification(force = false)
    }

    /** Android reports WGS84 ellipsoid height; convert to sea level where the platform can. */
    private fun mslAltitude(location: Location): Double? {
        if (Build.VERSION.SDK_INT >= 34 && location.hasAltitude()) {
            runCatching { altitudeConverter?.addMslAltitudeToLocation(this, location) }
            if (location.hasMslAltitude()) return location.mslAltitudeMeters
        }
        return if (location.hasAltitude()) location.altitude else null
    }

    private fun checkRoute(live: LiveLocation) {
        val route = TrackingSession.activeRoute.value
        if (route?.id != followedRouteId) {
            followedRouteId = route?.id
            follower = route?.let { RouteFollower(it) }
            offRoute = false
            offRouteStreak = 0
        }
        val f = follower ?: return
        if (live.accuracy > 40f) return
        val progress = f.update(live.lat, live.lon)
        val threshold = settings.value.offRouteDistanceM.toDouble()

        if (progress.distanceFromRoute > threshold) offRouteStreak++
        else if (progress.distanceFromRoute < threshold * 0.7) offRouteStreak = 0

        if (!offRoute && offRouteStreak >= 3) {
            offRoute = true
            if (settings.value.offRouteAlert) alertOffRoute(progress.distanceFromRoute)
        } else if (offRoute && offRouteStreak == 0) {
            offRoute = false
            if (settings.value.offRouteAlert) alertBackOnRoute()
        }
    }

    private fun alertOffRoute(distance: Double) {
        vibrate(longArrayOf(0, 400, 200, 400))
        val units = settings.value.units
        val n = NotificationCompat.Builder(this, Notifications.CHANNEL_ROUTE)
            .setSmallIcon(R.drawable.ic_stat_bike)
            .setContentTitle("Off route")
            .setContentText("You're ${Fmt.distance(distance, units)} away from ${followedRouteName()}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        notify(Notifications.ID_ROUTE, n)
    }

    private fun alertBackOnRoute() {
        vibrate(longArrayOf(0, 120))
        getSystemService(NotificationManager::class.java).cancel(Notifications.ID_ROUTE)
    }

    private fun followedRouteName() = TrackingSession.activeRoute.value?.name ?: "the route"

    private fun vibrate(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    // --- State & notification ----------------------------------------------------------------

    private fun publish() {
        val a = acc
        TrackingSession.publish(
            TrackingState(
                status = when {
                    rideId == -1L -> TrackingStatus.IDLE
                    paused -> TrackingStatus.PAUSED
                    else -> TrackingStatus.RECORDING
                },
                rideId = rideId.takeIf { it != -1L },
                startTime = startTime,
                distance = a?.distance ?: 0.0,
                movingTimeMs = a?.movingTimeMs ?: 0L,
                speed = if (paused || a?.autoPaused == true) 0.0 else a?.currentSpeed ?: 0.0,
                maxSpeed = a?.maxSpeed ?: 0.0,
                elevGain = a?.elevGain ?: 0.0,
                elevLoss = a?.elevLoss ?: 0.0,
                altitude = a?.altitude,
                autoPaused = !paused && a?.autoPaused == true,
                segments = segments.map { it.toList() },
                location = lastLocation,
                offRoute = offRoute,
            )
        )
    }

    private fun updateNotification(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationAt < 5_000) return
        lastNotificationAt = now
        notify(Notifications.ID_TRACKING, buildNotification())
    }

    private fun notify(id: Int, notification: Notification) {
        runCatching { getSystemService(NotificationManager::class.java).notify(id, notification) }
    }

    private fun buildNotification(): Notification {
        val units = settings.value.units
        val a = acc
        val title = when {
            rideId == -1L -> "Starting ride…"
            paused -> "Ride paused"
            a?.autoPaused == true -> "Auto-paused"
            else -> "Recording ride"
        }
        val text = if (a != null) {
            "${Fmt.distance(a.distance, units)} · ${Fmt.duration(a.movingTimeMs)} · ${Fmt.speed(a.currentSpeed, units)}"
        } else "Waiting for GPS"

        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_stat_bike)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColor(0xFF00A676.toInt())
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())

        if (rideId != -1L) {
            if (startTime > 0) builder.setWhen(startTime).setUsesChronometer(!paused).setShowWhen(true)
            builder.addAction(
                0,
                if (paused) "Resume" else "Pause",
                PendingIntent.getService(
                    this, 1,
                    intent(this, if (paused) ACTION_RESUME else ACTION_PAUSE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
