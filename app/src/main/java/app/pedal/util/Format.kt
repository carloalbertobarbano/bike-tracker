package app.pedal.util

import app.pedal.data.Units
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A formatted measurement split into number and unit so the UI can style them separately. */
data class Measure(val value: String, val unit: String) {
    override fun toString() = if (unit.isEmpty()) value else "$value $unit"
}

object Fmt {
    private const val M_PER_MI = 1609.344
    private const val FT_PER_M = 3.28084

    fun distance(m: Double, units: Units): Measure = when (units) {
        Units.METRIC -> if (m < 1000) Measure("%.0f".f(m), "m") else Measure(dec(m / 1000.0), "km")
        Units.IMPERIAL -> Measure(dec(m / M_PER_MI), "mi")
    }

    /** Distance always in the large unit (km / mi), handy for totals and charts. */
    fun distanceLarge(m: Double, units: Units): Measure = when (units) {
        Units.METRIC -> Measure(dec(m / 1000.0), "km")
        Units.IMPERIAL -> Measure(dec(m / M_PER_MI), "mi")
    }

    fun speed(mps: Double, units: Units): Measure = when (units) {
        Units.METRIC -> Measure("%.1f".f(mps * 3.6), "km/h")
        Units.IMPERIAL -> Measure("%.1f".f(mps * 2.236936), "mph")
    }

    fun speedValue(mps: Double, units: Units): Double =
        if (units == Units.METRIC) mps * 3.6 else mps * 2.236936

    fun distanceValue(m: Double, units: Units): Double =
        if (units == Units.METRIC) m / 1000.0 else m / M_PER_MI

    fun elevation(m: Double, units: Units): Measure = when (units) {
        Units.METRIC -> Measure("%.0f".f(m), "m")
        Units.IMPERIAL -> Measure("%.0f".f(m * FT_PER_M), "ft")
    }

    fun elevationValue(m: Double, units: Units): Double =
        if (units == Units.METRIC) m else m * FT_PER_M

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.US, h, m, sec) else "%d:%02d".format(Locale.US, m, sec)
    }

    /** Compact duration for summaries, e.g. "3h 12m". */
    fun durationShort(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun date(ms: Long): String = SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale.getDefault()).format(Date(ms))

    fun dateShort(ms: Long): String = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(ms))

    private fun dec(v: Double) = if (v < 100) "%.2f".f(v) else "%.1f".f(v)

    private fun String.f(v: Double) = String.format(Locale.US, this, v)
}
