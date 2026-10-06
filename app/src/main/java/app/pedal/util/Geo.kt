package app.pedal.util

import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

data class SegmentHit(val distance: Double, val t: Double)

object Geo {
    private const val EARTH_RADIUS = 6_371_008.8
    private const val DEG = PI / 180.0

    /** Great-circle distance in meters. */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * DEG
        val dLon = (lon2 - lon1) * DEG
        val a = sin(dLat / 2).pow(2) + cos(lat1 * DEG) * cos(lat2 * DEG) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS * asin(min(1.0, sqrt(a)))
    }

    fun distance(a: LatLon, b: LatLon) = distance(a.lat, a.lon, b.lat, b.lon)

    /**
     * Distance in meters from point p to segment a–b, plus the fractional position of the
     * closest point along the segment. Uses a local equirectangular projection centred on p,
     * which is accurate for the short segments found in GPS tracks.
     */
    fun distanceToSegment(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double,
    ): SegmentHit {
        val k = EARTH_RADIUS * DEG
        val cosLat = cos(pLat * DEG)
        val ax = (aLon - pLon) * cosLat * k
        val ay = (aLat - pLat) * k
        val dx = (bLon - pLon) * cosLat * k - ax
        val dy = (bLat - pLat) * k - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return SegmentHit(sqrt(cx * cx + cy * cy), t)
    }

    /** Cumulative distance along a polyline; result[i] is the distance from the start to point i. */
    fun cumulative(points: List<LatLon>): DoubleArray {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) out[i] = out[i - 1] + distance(points[i - 1], points[i])
        return out
    }

    /** Uniformly downsample a polyline, always keeping the first and last point. */
    fun <T> downsample(points: List<T>, maxPoints: Int): List<T> {
        if (points.size <= maxPoints) return points
        val step = (points.size - 1).toDouble() / (maxPoints - 1)
        return List(maxPoints) { i -> points[(i * step).toInt().coerceAtMost(points.size - 1)] }
    }

    /**
     * Elevation gain/loss with a hysteresis threshold, which filters out GPS/DEM noise
     * that would otherwise inflate the totals.
     */
    fun elevationGainLoss(elevations: List<Double>, threshold: Double = 3.0): Pair<Double, Double> {
        if (elevations.isEmpty()) return 0.0 to 0.0
        var ref = elevations.first()
        var gain = 0.0
        var loss = 0.0
        for (e in elevations) {
            val d = e - ref
            if (d >= threshold) {
                gain += d; ref = e
            } else if (d <= -threshold) {
                loss -= d; ref = e
            }
        }
        return gain to loss
    }

    fun encode(points: List<LatLon>): String =
        points.joinToString(";") { String.format(Locale.US, "%.5f,%.5f", it.lat, it.lon) }

    fun decode(s: String): List<LatLon> {
        if (s.isBlank()) return emptyList()
        return s.split(';').mapNotNull { pair ->
            val i = pair.indexOf(',')
            if (i < 0) return@mapNotNull null
            val lat = pair.substring(0, i).toDoubleOrNull()
            val lon = pair.substring(i + 1).toDoubleOrNull()
            if (lat != null && lon != null) LatLon(lat, lon) else null
        }
    }
}
