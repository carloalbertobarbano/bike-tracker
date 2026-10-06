package app.pedal.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import app.pedal.gpx.GpxOutPoint
import app.pedal.gpx.GpxParser
import app.pedal.gpx.GpxWriter
import app.pedal.tracking.Fix
import app.pedal.tracking.FixResult
import app.pedal.tracking.RouteTrack
import app.pedal.tracking.StatsAccumulator
import app.pedal.util.Geo
import app.pedal.util.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.Calendar

data class RideSummary(
    val distanceM: Double,
    val movingTimeMs: Long,
    val maxSpeedMps: Double,
    val elevGainM: Double,
    val elevLossM: Double,
)

class Repository(private val context: Context, private val db: AppDatabase) {

    val rides: Flow<List<RideEntity>> = db.rides().observeFinished()
    val routes: Flow<List<RouteEntity>> = db.routes().observeAll()

    fun ride(id: Long): Flow<RideEntity?> = db.rides().observe(id)
    fun route(id: Long): Flow<RouteEntity?> = db.routes().observe(id)

    suspend fun ridePoints(id: Long): List<TrackPointEntity> = db.rides().points(id)

    // --- Recording -------------------------------------------------------------------------

    suspend fun startRide(start: Long): Long =
        db.rides().insert(RideEntity(name = defaultRideName(start), startTime = start))

    suspend fun addPoint(point: TrackPointEntity) = db.rides().insertPoint(point)

    suspend fun finishRide(id: Long, summary: RideSummary, end: Long) {
        val ride = db.rides().get(id) ?: return
        val points = db.rides().points(id)
        db.rides().update(
            ride.copy(
                endTime = end,
                distanceM = summary.distanceM,
                movingTimeMs = summary.movingTimeMs,
                elapsedMs = end - ride.startTime,
                maxSpeedMps = summary.maxSpeedMps,
                elevGainM = summary.elevGainM,
                elevLossM = summary.elevLossM,
                preview = Geo.encode(Geo.downsample(points.map { LatLon(it.lat, it.lon) }, PREVIEW_POINTS)),
            )
        )
    }

    suspend fun deleteRide(id: Long) = db.rides().delete(id)

    suspend fun renameRide(id: Long, name: String) = db.rides().rename(id, name.trim())

    /**
     * Rides left unfinished by a crash/kill are finalised from their stored points.
     * Only rides started before [before] are touched, so a ride started right now is safe.
     */
    suspend fun recoverUnfinished(before: Long) {
        for (ride in db.rides().unfinished().filter { it.startTime < before }) {
            val points = db.rides().points(ride.id)
            if (points.size < 2) {
                db.rides().delete(ride.id)
                continue
            }
            finishRide(ride.id, summarize(points), points.last().time)
        }
    }

    private fun summarize(points: List<TrackPointEntity>): RideSummary {
        val acc = StatsAccumulator(autoPauseEnabled = false)
        var segment = points.first().segment
        for (p in points) {
            if (p.segment != segment) {
                acc.breakSegment(); segment = p.segment
            }
            acc.onFix(Fix(p.lat, p.lon, p.ele, p.time, p.speed, p.accuracy))
        }
        return RideSummary(acc.distance, acc.movingTimeMs, acc.maxSpeed, acc.elevGain, acc.elevLoss)
    }

    // --- GPX -------------------------------------------------------------------------------

    suspend fun importGpx(uri: Uri): Long = withContext(Dispatchers.IO) {
        val track = context.contentResolver.openInputStream(uri)?.use { GpxParser.parse(it) }
            ?: error("Couldn't open file")
        val name = track.name ?: displayName(uri)?.removeSuffix(".gpx")?.removeSuffix(".GPX") ?: "Imported route"
        saveRoute(name, track.points.map { RoutePointEntity(routeId = 0, idx = 0, lat = it.lat, lon = it.lon, ele = it.ele) })
    }

    suspend fun saveRideAsRoute(rideId: Long): Long {
        val ride = db.rides().get(rideId) ?: error("Ride not found")
        val points = db.rides().points(rideId)
        return saveRoute(ride.name, points.map { RoutePointEntity(routeId = 0, idx = 0, lat = it.lat, lon = it.lon, ele = it.ele) })
    }

    private suspend fun saveRoute(name: String, raw: List<RoutePointEntity>): Long {
        val points = raw.mapIndexed { i, p -> p.copy(idx = i) }
        val latLons = points.map { LatLon(it.lat, it.lon) }
        val eles = points.mapNotNull { it.ele }
        val hasEle = eles.size > points.size / 2
        val (gain, loss) = if (hasEle) Geo.elevationGainLoss(eles) else 0.0 to 0.0
        val route = RouteEntity(
            name = name,
            createdAt = System.currentTimeMillis(),
            distanceM = Geo.cumulative(latLons).lastOrNull() ?: 0.0,
            elevGainM = gain,
            elevLossM = loss,
            pointCount = points.size,
            hasElevation = hasEle,
            preview = Geo.encode(Geo.downsample(latLons, PREVIEW_POINTS)),
        )
        return db.routes().insertWithPoints(route, points)
    }

    suspend fun routePoints(id: Long): List<RoutePointEntity> = db.routes().points(id)

    suspend fun loadRouteTrack(id: Long): RouteTrack? {
        val route = db.routes().get(id) ?: return null
        return RouteTrack(route.id, route.name, db.routes().points(id).map { LatLon(it.lat, it.lon) })
    }

    suspend fun deleteRoute(id: Long) = db.routes().delete(id)

    suspend fun renameRoute(id: Long, name: String) = db.routes().rename(id, name.trim())

    /** Writes the ride as GPX into the cache and returns a shareable content:// Uri. */
    suspend fun exportRideToCache(id: Long): Uri = withContext(Dispatchers.IO) {
        val ride = db.rides().get(id) ?: error("Ride not found")
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, safeFileName(ride.name) + ".gpx")
        file.bufferedWriter().use { writeGpx(ride, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun exportRideTo(id: Long, out: OutputStream) = withContext(Dispatchers.IO) {
        val ride = db.rides().get(id) ?: error("Ride not found")
        out.bufferedWriter().use { writeGpx(ride, it) }
    }

    private suspend fun writeGpx(ride: RideEntity, writer: java.io.Writer) {
        val segments = db.rides().points(ride.id)
            .groupBy { it.segment }
            .toSortedMap()
            .values
            .map { seg -> seg.map { GpxOutPoint(it.lat, it.lon, it.ele, it.time) } }
        GpxWriter.write(writer, ride.name, ride.startTime, segments)
    }

    fun suggestedFileName(ride: RideEntity) = safeFileName(ride.name) + ".gpx"

    private fun safeFileName(name: String) =
        name.replace(Regex("[^A-Za-z0-9._ -]"), "").trim().ifEmpty { "ride" }.replace(' ', '_')

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    companion object {
        private const val PREVIEW_POINTS = 80

        fun defaultRideName(time: Long): String {
            val hour = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 5..10 -> "Morning ride"
                in 11..13 -> "Lunch ride"
                in 14..17 -> "Afternoon ride"
                in 18..21 -> "Evening ride"
                else -> "Night ride"
            }
        }
    }
}
