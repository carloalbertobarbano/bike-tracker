package app.pedal.tracking

import app.pedal.util.Geo
import app.pedal.util.LatLon

/** An imported route prepared for following: points plus cumulative distances. */
class RouteTrack(val id: Long, val name: String, val points: List<LatLon>) {
    val cumulative: DoubleArray = Geo.cumulative(points)
    val total: Double get() = if (cumulative.isEmpty()) 0.0 else cumulative.last()
}

data class RouteProgress(
    val distanceFromRoute: Double,
    val distanceAlong: Double,
    val remaining: Double,
    val total: Double,
) {
    val fraction: Float get() = if (total > 0) (distanceAlong / total).toFloat().coerceIn(0f, 1f) else 0f
}

/**
 * Matches positions to the route. It first searches a window ahead of the last match so
 * that out-and-back routes (same road in both directions) don't jump to the wrong leg,
 * and falls back to a global search when the rider is far from the expected position.
 */
class RouteFollower(private val route: RouteTrack, private val windowThresholdM: Double = 60.0) {
    private var lastIndex = -1

    fun update(lat: Double, lon: Double): RouteProgress {
        val pts = route.points
        if (pts.size < 2) return RouteProgress(0.0, 0.0, 0.0, 0.0)

        var best = if (lastIndex >= 0) {
            search(lat, lon, (lastIndex - 30).coerceAtLeast(0), (lastIndex + 400).coerceAtMost(pts.size - 2))
        } else null
        if (best == null || best.distance > windowThresholdM) {
            val global = search(lat, lon, 0, pts.size - 2)
            if (best == null || global.distance < best.distance) best = global
        }
        if (best.distance <= windowThresholdM * 2) lastIndex = best.index

        val i = best.index
        val along = route.cumulative[i] + (route.cumulative[i + 1] - route.cumulative[i]) * best.t
        return RouteProgress(best.distance, along, (route.total - along).coerceAtLeast(0.0), route.total)
    }

    private data class Match(val index: Int, val distance: Double, val t: Double)

    private fun search(lat: Double, lon: Double, from: Int, to: Int): Match {
        val pts = route.points
        var bestI = from
        var bestD = Double.MAX_VALUE
        var bestT = 0.0
        for (i in from..to) {
            val a = pts[i]
            val b = pts[i + 1]
            val hit = Geo.distanceToSegment(lat, lon, a.lat, a.lon, b.lat, b.lon)
            if (hit.distance < bestD) {
                bestD = hit.distance; bestI = i; bestT = hit.t
            }
        }
        return Match(bestI, bestD, bestT)
    }
}
