package app.pedal.util

object Angles {
    /** Signed shortest rotation from [from] to [to] in degrees, in (-180, 180]. */
    fun delta(from: Float, to: Float): Float {
        var d = (to - from) % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /** Normalises an angle to [0, 360). */
    fun normalize(a: Float): Float = ((a % 360f) + 360f) % 360f
}

/**
 * Smooths GPS bearings (which jitter by several degrees between fixes) with an exponential
 * filter that wraps correctly around north. A null bearing (standing still) keeps the last value.
 */
class HeadingFilter(private val alpha: Float = 0.4f) {
    var value: Float? = null
        private set

    fun update(bearing: Float?): Float? {
        if (bearing == null) return value
        val current = value
        value = if (current == null) Angles.normalize(bearing)
        else Angles.normalize(current + Angles.delta(current, bearing) * alpha)
        return value
    }
}
