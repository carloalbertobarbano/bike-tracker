package app.pedal

import android.app.Application
import android.content.Context
import app.pedal.data.AppDatabase
import app.pedal.data.Repository
import app.pedal.data.SettingsStore
import app.pedal.tracking.Notifications
import app.pedal.tracking.TrackingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(context)
    val repository = Repository(context, AppDatabase.create(context))

    /** Sets (or clears) the route to follow; persisted so it survives restarts. */
    fun followRoute(routeId: Long?) {
        settings.update { it.copy(activeRouteId = routeId ?: -1L) }
        appScope.launch {
            TrackingSession.setActiveRoute(routeId?.let { repository.loadRouteTrack(it) })
        }
    }
}

class PedalApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        Configuration.getInstance().apply {
            userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
            val base = File(cacheDir, "osmdroid")
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
            tileFileSystemCacheMaxBytes = 400L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 320L * 1024 * 1024
        }

        Notifications.createChannels(this)

        val launchedAt = System.currentTimeMillis()
        container.appScope.launch {
            container.repository.recoverUnfinished(before = launchedAt)
            val routeId = container.settings.value.activeRouteId
            if (routeId > 0) {
                val track = container.repository.loadRouteTrack(routeId)
                if (track == null) container.settings.update { it.copy(activeRouteId = -1L) }
                TrackingSession.setActiveRoute(track)
            }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as PedalApp).container
