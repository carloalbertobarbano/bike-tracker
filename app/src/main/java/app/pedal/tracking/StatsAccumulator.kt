package app.pedal.tracking

import app.pedal.util.Geo
import kotlin.math.max
import kotlin.math.min

/** A single location fix, independent of the Android Location class (keeps this unit-testable). */
data class Fix(
    val lat: Double,
    val lon: Double,
    val alt: Double?,
    val time: Long,
    val speed: Float?,
    val accuracy: Float,
)

enum class FixResult { REJECTED, IGNORED, RECORDED }

/**
 * Turns a stream of raw GPS fixes into ride statistics. Handles accuracy filtering,
 * stationary jitter, teleport rejection, auto-pause and noise-resistant elevation gain.
 */
class StatsAccumulator(var autoPauseEnabled: Boolean) {
    var distance = 0.0; private set
    var movingTimeMs = 0L; private set
    var maxSpeed = 0.0; private set
    var elevGain = 0.0; private set
    var elevLoss = 0.0; private set
    var currentSpeed = 0.0; private set
    var altitude: Double? = null; private set
    var autoPaused = false; private set
    var recordedPoints = 0; private set

    private var last: Fix? = null
    private var stillSince: Long? = null
    private var smoothAlt: Double? = null
    private var refAlt: Double? = null

    fun onFix(fix: Fix): FixResult {
        if (fix.accuracy > MAX_ACCURACY_M) return FixResult.REJECTED
        val prev = last

        val dist = prev?.let { Geo.distance(it.lat, it.lon, fix.lat, fix.lon) } ?: 0.0
        val dtMs = prev?.let { fix.time - it.time } ?: 0L
        if (prev != null && dtMs <= 0) return FixResult.REJECTED
        val derived = if (dtMs > 0) dist / (dtMs / 1000.0) else 0.0
        // Teleport filter: physically implausible jump for a bike.
        if (prev != null && derived > MAX_PLAUSIBLE_SPEED && dtMs < 30_000) return FixResult.REJECTED

        val speed = fix.speed?.toDouble() ?: derived
        currentSpeed = if (prev == null) speed else currentSpeed * 0.4 + speed * 0.6
        updateAutoPause(speed, fix.time)
        updateAltitude(fix.alt)

        if (prev == null) {
            last = fix
            recordedPoints++
            return FixResult.RECORDED
        }
        if (autoPaused) {
            // Keep the reference fresh so jitter while stopped isn't counted later.
            last = fix
            refAlt = smoothAlt
            return FixResult.IGNORED
        }
        if (dist < MIN_MOVE_M && speed < STILL_SPEED) return FixResult.IGNORED

        distance += dist
        movingTimeMs += min(dtMs, MAX_GAP_MS)
        if (fix.accuracy <= 20f) maxSpeed = max(maxSpeed, min(speed, MAX_PLAUSIBLE_SPEED))
        applyElevation()
        last = fix
        recordedPoints++
        return FixResult.RECORDED
    }

    /** Called after a manual pause: the next fix starts a new segment without bridging distance. */
    fun breakSegment() {
        last = null
        stillSince = null
        autoPaused = false
        refAlt = smoothAlt
    }

    private fun updateAutoPause(speed: Double, time: Long) {
        if (!autoPauseEnabled) {
            autoPaused = false
            return
        }
        if (speed < AUTO_PAUSE_SPEED) {
            val since = stillSince ?: time.also { stillSince = it }
            if (time - since >= AUTO_PAUSE_DELAY_MS) autoPaused = true
        } else {
            stillSince = null
            if (speed > AUTO_RESUME_SPEED) autoPaused = false
        }
    }

    private fun updateAltitude(alt: Double?) {
        if (alt == null) return
        val s = smoothAlt?.let { it + (alt - it) * 0.3 } ?: alt
        smoothAlt = s
        altitude = s
        if (refAlt == null) refAlt = s
    }

    private fun applyElevation() {
        val s = smoothAlt ?: return
        val ref = refAlt ?: return
        val d = s - ref
        if (d >= ELEV_THRESHOLD) {
            elevGain += d; refAlt = s
        } else if (d <= -ELEV_THRESHOLD) {
            elevLoss -= d; refAlt = s
        }
    }

    companion object {
        const val MAX_ACCURACY_M = 30f
        const val MIN_MOVE_M = 2.0
        const val STILL_SPEED = 0.5
        const val AUTO_PAUSE_SPEED = 0.8
        const val AUTO_RESUME_SPEED = 1.5
        const val AUTO_PAUSE_DELAY_MS = 4_000L
        const val MAX_GAP_MS = 15_000L
        const val MAX_PLAUSIBLE_SPEED = 35.0 // m/s ≈ 126 km/h
        const val ELEV_THRESHOLD = 3.0
    }
}
