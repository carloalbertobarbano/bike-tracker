package app.pedal.tracking

import app.pedal.util.LatLon
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TrackingStatus { IDLE, RECORDING, PAUSED }

data class LiveLocation(
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val bearing: Float?,
    val speed: Float?,
    val time: Long,
)

data class TrackingState(
    val status: TrackingStatus = TrackingStatus.IDLE,
    val rideId: Long? = null,
    val startTime: Long = 0L,
    val distance: Double = 0.0,
    val movingTimeMs: Long = 0L,
    val speed: Double = 0.0,
    val maxSpeed: Double = 0.0,
    val elevGain: Double = 0.0,
    val elevLoss: Double = 0.0,
    val altitude: Double? = null,
    val autoPaused: Boolean = false,
    val segments: List<List<LatLon>> = emptyList(),
    val location: LiveLocation? = null,
    val offRoute: Boolean = false,
) {
    val isActive: Boolean get() = status != TrackingStatus.IDLE
    val avgSpeed: Double get() = if (movingTimeMs > 0) distance / (movingTimeMs / 1000.0) else 0.0
}

sealed interface TrackingEvent {
    data class RideSaved(val rideId: Long) : TrackingEvent
}

/** Process-wide live state shared between the foreground service and the UI. */
object TrackingSession {
    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TrackingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TrackingEvent> = _events.asSharedFlow()

    private val _activeRoute = MutableStateFlow<RouteTrack?>(null)
    val activeRoute: StateFlow<RouteTrack?> = _activeRoute.asStateFlow()

    internal fun publish(state: TrackingState) {
        _state.value = state
    }

    internal fun emit(event: TrackingEvent) {
        _events.tryEmit(event)
    }

    internal fun setActiveRoute(route: RouteTrack?) {
        _activeRoute.value = route
    }
}
