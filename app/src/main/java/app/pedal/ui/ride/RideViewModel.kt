package app.pedal.ui.ride

import android.app.Application
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pedal.container
import app.pedal.data.MapStyle
import app.pedal.tracking.LiveLocation
import app.pedal.tracking.LocationSource
import app.pedal.tracking.LocationSource.toLive
import app.pedal.tracking.RouteFollower
import app.pedal.tracking.RouteProgress
import app.pedal.tracking.TrackingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RideViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.container

    val tracking = TrackingSession.state
    val activeRoute = TrackingSession.activeRoute
    val settings = container.settings.state

    private val idleLocation = MutableStateFlow<LiveLocation?>(null)
    private var idleJob: Job? = null

    /** Live location from the recording service when riding, otherwise from a light idle listener. */
    val location: StateFlow<LiveLocation?> =
        combine(tracking, idleLocation) { t, idle -> t.location ?: idle }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var follower: Pair<Long, RouteFollower>? = null

    val routeProgress: StateFlow<RouteProgress?> =
        combine(location, activeRoute) { loc, route ->
            if (loc == null || route == null) return@combine null
            val f = follower?.takeIf { it.first == route.id }?.second
                ?: RouteFollower(route).also { follower = route.id to it }
            f.update(loc.lat, loc.lon)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startIdleUpdates() {
        if (idleJob != null) return
        idleJob = viewModelScope.launch {
            LocationSource.updates(getApplication(), 2_000L, includeNetwork = true, emitLastKnown = true)
                .collect { l ->
                    val live = l.toLive()
                    val current = idleLocation.value
                    // Prefer GPS; accept coarser network fixes only when nothing better is recent.
                    if (current == null || l.provider == LocationManager.GPS_PROVIDER ||
                        live.accuracy <= current.accuracy || live.time - current.time > 10_000
                    ) idleLocation.value = live
                }
        }
    }

    fun stopIdleUpdates() {
        idleJob?.cancel()
        idleJob = null
    }

    fun setMapStyle(style: MapStyle) = container.settings.update { it.copy(mapStyle = style) }

    fun stopFollowing() = container.followRoute(null)
}
