package app.pedal

import app.pedal.gpx.GpxException
import app.pedal.gpx.GpxOutPoint
import app.pedal.gpx.GpxParser
import app.pedal.gpx.GpxWriter
import app.pedal.tracking.Fix
import app.pedal.tracking.FixResult
import app.pedal.tracking.RouteFollower
import app.pedal.tracking.RouteTrack
import app.pedal.tracking.StatsAccumulator
import app.pedal.util.Geo
import app.pedal.util.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class GeoTest {
    @Test
    fun haversineMatchesKnownDistance() {
        // 1 degree of latitude ≈ 111.2 km
        assertEquals(111_195.0, Geo.distance(45.0, 9.0, 46.0, 9.0), 50.0)
    }

    @Test
    fun segmentDistance() {
        // Point ~111 m north of the middle of an east-west segment.
        val hit = Geo.distanceToSegment(45.001, 9.0005, 45.0, 9.0, 45.0, 9.001)
        assertEquals(111.2, hit.distance, 1.0)
        assertEquals(0.5, hit.t, 0.01)
    }

    @Test
    fun elevationHysteresisIgnoresNoise() {
        val noisy = listOf(100.0, 101.0, 99.5, 100.8, 99.9, 100.5)
        assertEquals(0.0 to 0.0, Geo.elevationGainLoss(noisy))
        val climb = listOf(100.0, 104.0, 110.0, 108.0, 120.0, 112.0)
        val (gain, loss) = Geo.elevationGainLoss(climb)
        assertEquals(20.0, gain, 0.01)
        assertEquals(8.0, loss, 0.01)
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val pts = listOf(LatLon(45.12345, 9.54321), LatLon(-33.9, 151.2))
        val back = Geo.decode(Geo.encode(pts))
        assertEquals(2, back.size)
        assertEquals(45.12345, back[0].lat, 1e-6)
        assertEquals(151.2, back[1].lon, 1e-6)
    }

    @Test
    fun downsampleKeepsEnds() {
        val list = (0 until 1000).toList()
        val d = Geo.downsample(list, 50)
        assertEquals(50, d.size)
        assertEquals(0, d.first())
        assertEquals(999, d.last())
    }
}

class StatsAccumulatorTest {
    /** Ride north at [speed] m/s, one fix per second. */
    private fun ride(acc: StatsAccumulator, seconds: Int, speed: Double, startLat: Double = 45.0, startTime: Long = 0L, alt: (Int) -> Double? = { null }): Pair<Double, Long> {
        val dLat = speed / 111_195.0
        var lat = startLat
        var t = startTime
        for (i in 0 until seconds) {
            acc.onFix(Fix(lat, 9.0, alt(i), t, speed.toFloat(), 5f))
            lat += dLat
            t += 1000
        }
        return lat to t
    }

    @Test
    fun accumulatesDistanceAndMovingTime() {
        val acc = StatsAccumulator(autoPauseEnabled = true)
        ride(acc, 101, 8.0) // 100 intervals at 8 m/s
        assertEquals(800.0, acc.distance, 5.0)
        assertEquals(100_000L, acc.movingTimeMs)
        assertEquals(8.0, acc.maxSpeed, 0.01)
    }

    @Test
    fun autoPauseStopsTheClock() {
        val acc = StatsAccumulator(autoPauseEnabled = true)
        val (lat, t) = ride(acc, 11, 8.0)
        val moving = acc.movingTimeMs
        // Stand still for a minute, with a bit of jitter.
        var time = t
        for (i in 0 until 60) {
            acc.onFix(Fix(lat + (i % 3) * 0.00001, 9.0, null, time, 0.1f, 5f))
            time += 1000
        }
        assertTrue(acc.autoPaused)
        assertTrue("moving time grew while stopped", acc.movingTimeMs - moving < 6_000)
        ride(acc, 5, 8.0, startLat = lat, startTime = time)
        assertFalse(acc.autoPaused)
    }

    @Test
    fun rejectsInaccurateAndTeleportingFixes() {
        val acc = StatsAccumulator(autoPauseEnabled = false)
        assertEquals(FixResult.RECORDED, acc.onFix(Fix(45.0, 9.0, null, 0, 5f, 5f)))
        assertEquals(FixResult.REJECTED, acc.onFix(Fix(45.0001, 9.0, null, 1000, 5f, 80f)))
        assertEquals(FixResult.REJECTED, acc.onFix(Fix(45.1, 9.0, null, 2000, 5f, 5f))) // 11 km in 2 s
        assertEquals(0.0, acc.distance, 0.0)
    }

    @Test
    fun elevationGainFromClimb() {
        val acc = StatsAccumulator(autoPauseEnabled = false)
        ride(acc, 200, 5.0, alt = { 100.0 + it * 0.5 }) // +100 m
        assertEquals(100.0, acc.elevGain, 6.0)
        assertEquals(0.0, acc.elevLoss, 0.01)
    }

    @Test
    fun breakSegmentDoesNotBridgeGap() {
        val acc = StatsAccumulator(autoPauseEnabled = false)
        ride(acc, 11, 5.0)
        val d = acc.distance
        acc.breakSegment()
        ride(acc, 1, 5.0, startLat = 45.01, startTime = 100_000) // 1 km away, first fix only
        assertEquals(d, acc.distance, 0.001)
    }
}

class RouteFollowerTest {
    private val route = RouteTrack(1, "test", (0..100).map { LatLon(45.0, 9.0 + it * 0.0001) }) // ~787 m east

    @Test
    fun progressAlongRoute() {
        val f = RouteFollower(route)
        val p = f.update(45.0, 9.005) // halfway
        assertEquals(route.total / 2, p.distanceAlong, 5.0)
        assertEquals(0.5f, p.fraction, 0.01f)
        assertTrue(p.distanceFromRoute < 1.0)
    }

    @Test
    fun detectsDistanceFromRoute() {
        val f = RouteFollower(route)
        val p = f.update(45.001, 9.005) // ~111 m north
        assertEquals(111.0, p.distanceFromRoute, 2.0)
    }

    @Test
    fun outAndBackPrefersCurrentLeg() {
        val out = (0..100).map { LatLon(45.0, 9.0 + it * 0.0001) }
        val back = out.reversed()
        val f = RouteFollower(RouteTrack(2, "oab", out + back))
        // Ride out past the midpoint.
        for (i in 0..90 step 5) f.update(45.0, 9.0 + i * 0.0001)
        val p = f.update(45.0, 9.0095)
        assertTrue("matched the return leg too early", p.fraction < 0.5f)
    }
}

class GpxTest {
    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
          <metadata><name>Lake loop</name></metadata>
          <trk><name>ignored</name><trkseg>
            <trkpt lat="45.0" lon="9.0"><ele>200.5</ele><time>2026-05-01T08:00:00Z</time></trkpt>
            <trkpt lat="45.001" lon="9.001"><ele>203</ele><time>2026-05-01T08:00:10+02:00</time></trkpt>
            <trkpt lat="45.002" lon="9.002"></trkpt>
          </trkseg></trk>
        </gpx>
    """.trimIndent()

    @Test
    fun parsesTrack() {
        val t = GpxParser.parse(sample.byteInputStream())
        assertEquals("Lake loop", t.name)
        assertEquals(3, t.points.size)
        assertEquals(200.5, t.points[0].ele!!, 1e-9)
        assertEquals(1777622400000L, t.points[0].time)
        assertNull(t.points[2].ele)
    }

    @Test
    fun parsesRouteWithPrefixedNamespace() {
        val xml = """<g:gpx xmlns:g="http://www.topografix.com/GPX/1/1"><g:rte><g:name>R</g:name>
            <g:rtept lat="1" lon="2"/><g:rtept lat="1.1" lon="2.1"/></g:rte></g:gpx>"""
        val t = GpxParser.parse(xml.byteInputStream())
        assertEquals("R", t.name)
        assertEquals(2, t.points.size)
    }

    @Test(expected = GpxException::class)
    fun rejectsGarbage() {
        GpxParser.parse("not xml at all".byteInputStream())
    }

    @Test
    fun writerRoundTrip() {
        val out = StringWriter()
        GpxWriter.write(
            out, "Ride & <fun>", 0L,
            listOf(
                listOf(GpxOutPoint(45.0, 9.0, 100.0, 0L), GpxOutPoint(45.001, 9.0, 101.0, 1000L)),
                listOf(GpxOutPoint(45.002, 9.0, null, 5000L)),
            ),
        )
        val parsed = GpxParser.parse(out.toString().byteInputStream())
        assertEquals("Ride & <fun>", parsed.name)
        assertEquals(3, parsed.points.size)
        assertEquals(1000L, parsed.points[1].time)
    }
}
