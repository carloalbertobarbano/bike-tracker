package app.pedal

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pedal.data.AppSettings
import app.pedal.data.RideEntity
import app.pedal.data.RouteEntity
import app.pedal.data.Units
import app.pedal.tracking.LiveLocation
import app.pedal.tracking.RouteProgress
import app.pedal.tracking.RouteTrack
import app.pedal.tracking.TrackingState
import app.pedal.tracking.TrackingStatus
import app.pedal.ui.PedalBottomBar
import app.pedal.ui.TAB_HISTORY
import app.pedal.ui.TAB_RIDE
import app.pedal.ui.TAB_ROUTES
import app.pedal.ui.components.StatTile
import app.pedal.ui.components.StatValue
import app.pedal.ui.components.TwoColumnGrid
import app.pedal.ui.history.ChartCard
import app.pedal.ui.history.RideCard
import app.pedal.ui.history.TotalsCard
import app.pedal.ui.ride.RideContent
import app.pedal.ui.routes.RouteCard
import app.pedal.ui.theme.MapColors
import app.pedal.ui.theme.PedalTheme
import app.pedal.util.Fmt
import app.pedal.util.Geo
import app.pedal.util.LatLon
import app.pedal.util.Measure
import com.android.resources.NightMode
import org.junit.Rule
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the real Compose UI on the JVM (no device needed). The live osmdroid map is replaced by
 * a static backdrop of CyclOSM tiles with the track/route drawn on top, mimicking the map overlays.
 * Run: ./gradlew recordPaparazziDebug  → app/src/test/snapshots/images
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.5)

    private fun dark() = paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))

    private val backdrop by lazy {
        BitmapFactory.decodeStream(javaClass.classLoader!!.getResourceAsStream("map_backdrop.png")).asImageBitmap()
    }

    // Normalised screen-space route through the backdrop.
    private fun routeAt(t: Float) = Offset(
        0.18f + 0.62f * t + 0.07f * sin(t * 7f),
        0.80f - 0.68f * t + 0.04f * cos(t * 11f),
    )

    private fun DrawScope.polyline(points: List<Offset>, color: Color, width: Float, casing: Float) {
        val path = Path().apply {
            points.forEachIndexed { i, p ->
                val x = p.x * size.width; val y = p.y * size.height
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(path, Color.White, style = Stroke(casing.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, color, style = Stroke(width.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    @Composable
    private fun FakeMap(showRoute: Boolean, trackUntil: Float?) {
        Box(Modifier.fillMaxSize()) {
            Image(backdrop, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Canvas(Modifier.fillMaxSize()) {
                if (showRoute) {
                    polyline((0..200).map { routeAt(it / 200f) }, Color(MapColors.ROUTE).copy(alpha = 0.9f), 5.5f, 9f)
                }
                val loc = if (trackUntil != null) {
                    val n = (trackUntil * 200).toInt()
                    polyline((0..n).map { routeAt(it / 200f) + Offset(0.002f * sin(it * 1.3f), 0f) }, Color(MapColors.TRACK), 4.5f, 8f)
                    routeAt(trackUntil)
                } else Offset(0.5f, 0.42f)
                val c = Offset(loc.x * size.width, loc.y * size.height)
                drawCircle(Color(MapColors.LOCATION).copy(alpha = 0.16f), 30.dp.toPx(), c)
                drawCircle(Color.White, 9.dp.toPx(), c)
                drawCircle(Color(MapColors.LOCATION), 6.5.dp.toPx(), c)
            }
        }
    }

    private fun loop(seed: Int, n: Int = 60) = (0 until n).map {
        val a = it / n.toDouble() * 2 * Math.PI
        val r = 0.01 * (1 + 0.35 * sin(a * (2 + seed % 3) + seed) + 0.15 * cos(a * 5 + seed))
        LatLon(45.46 + r * sin(a), 9.18 + r * 1.4 * cos(a))
    }

    private val routePoints = (0..200).map { LatLon(45.40 + it * 0.0011, 9.10 + it * 0.0012 + 0.01 * sin(it / 15.0)) }
    private val route = RouteTrack(1, "Navigli to Lake Como", routePoints)
    private val location = LiveLocation(45.46, 9.18, 4f, 30f, 7.4f, 0L)
    private val settings = AppSettings()

    @Composable
    private fun RideFrame(
        state: TrackingState,
        withRoute: Boolean,
        progress: RouteProgress?,
        offRoute: Boolean = false,
        mapRotation: Float? = null, // non-null = heading-up
    ) {
        Scaffold(bottomBar = { PedalBottomBar(TAB_RIDE, state.isActive) {} }) { padding ->
            RideContent(
                tracking = state,
                settings = settings,
                location = location,
                route = if (withRoute) route else null,
                progress = progress,
                offRoute = offRoute,
                hasPermission = true,
                follow = true,
                headingUp = mapRotation != null,
                mapRotation = mapRotation ?: 0f,
                bottomPadding = padding.calculateBottomPadding(),
                map = {
                    // Mimic the real map rotation; scale up so the rotated image still covers the screen.
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        rotationZ = mapRotation ?: 0f
                        if (mapRotation != null) { scaleX = 1.9f; scaleY = 1.9f }
                    }) {
                        FakeMap(showRoute = withRoute, trackUntil = if (state.isActive) 0.46f else null)
                    }
                },
                onRecenter = {}, onToggleOrientation = {}, onLayers = {}, onSettings = {}, onStopFollowing = {}, onAllowLocation = {},
                onStart = {}, onPause = {}, onResume = {}, onStop = {},
            )
        }
    }

    private val recording = TrackingState(
        status = TrackingStatus.RECORDING,
        rideId = 1,
        startTime = System.currentTimeMillis() - 3_050_000,
        distance = 18_420.0,
        movingTimeMs = 2_712_000,
        speed = 7.31,
        maxSpeed = 13.2,
        elevGain = 214.0,
        elevLoss = 96.0,
        altitude = 182.0,
        location = location,
    )

    @Test
    fun ride_idle_light() = paparazzi.snapshot {
        PedalTheme { RideFrame(TrackingState(location = location), withRoute = false, progress = null) }
    }

    @Test
    fun ride_recording_following_dark() {
        dark()
        paparazzi.snapshot {
            PedalTheme {
                RideFrame(recording, withRoute = true, progress = RouteProgress(4.0, 18_420.0, route.total - 18_420.0, route.total))
            }
        }
    }

    @Test
    fun ride_heading_up_light() = paparazzi.snapshot {
        PedalTheme {
            RideFrame(
                recording,
                withRoute = true,
                progress = RouteProgress(4.0, 18_420.0, route.total - 18_420.0, route.total),
                mapRotation = -38f,
            )
        }
    }

    @Test
    fun ride_recording_offroute_light() = paparazzi.snapshot {
        PedalTheme {
            RideFrame(
                recording.copy(status = TrackingStatus.RECORDING, offRoute = true),
                withRoute = true,
                progress = RouteProgress(87.0, 18_420.0, route.total - 18_420.0, route.total),
                offRoute = true,
            )
        }
    }

    private val rides = listOf(
        RideEntity(1, "Evening ride", 1_791_300_000_000, 1_791_303_600_000, 32_480.0, 4_380_000, 5_100_000, 14.1, 312.0, 305.0, Geo.encode(loop(1))),
        RideEntity(2, "Morning ride", 1_791_100_000_000, 1_791_102_000_000, 18_920.0, 2_710_000, 3_000_000, 11.8, 146.0, 150.0, Geo.encode(loop(2))),
        RideEntity(3, "Lunch ride", 1_790_900_000_000, 1_790_901_000_000, 11_300.0, 1_850_000, 2_000_000, 10.2, 58.0, 61.0, Geo.encode(loop(4))),
        RideEntity(4, "Afternoon ride", 1_790_600_000_000, 1_790_608_000_000, 61_700.0, 9_120_000, 10_900_000, 16.9, 820.0, 815.0, Geo.encode(loop(6))),
    )

    @Test
    fun history_light() = paparazzi.snapshot {
        PedalTheme {
            Scaffold(bottomBar = { PedalBottomBar(TAB_HISTORY, false) {} }) { padding ->
                Column(
                    Modifier.padding(padding).padding(horizontal = 16.dp).padding(top = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Rides", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    TotalsCard(rides, Units.METRIC)
                    Text("RECENT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
                    rides.forEach { RideCard(it, Units.METRIC) {} }
                }
            }
        }
    }

    @Test
    fun routes_dark() {
        dark()
        val routes = listOf(
            RouteEntity(1, "Navigli to Lake Como", 0, route.total, 640.0, 410.0, 201, true, Geo.encode(Geo.downsample(routePoints, 80))),
            RouteEntity(2, "Monza park loop", 0, 24_100.0, 85.0, 85.0, 900, true, Geo.encode(loop(3))),
            RouteEntity(3, "Ghisallo climb", 0, 58_300.0, 1_120.0, 300.0, 2400, true, Geo.encode(loop(5).take(40))),
        )
        paparazzi.snapshot {
            PedalTheme {
                Scaffold(bottomBar = { PedalBottomBar(TAB_ROUTES, true) {} }) { padding ->
                    Column(
                        Modifier.padding(padding).padding(horizontal = 16.dp).padding(top = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Routes", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 4.dp))
                        Text("Import a GPX track and follow it on the map", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                        routes.forEachIndexed { i, r -> RouteCard(r, i == 0, Units.METRIC) {} }
                    }
                }
            }
        }
    }

    @Test
    fun ride_detail_dark() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT, screenHeight = 3000))
        val n = 300
        val xs = FloatArray(n) { it * 32_480f / (n - 1) }
        val speed = FloatArray(n) { (7.5 + 2.2 * sin(it / 9.0) + 1.1 * sin(it / 2.7) - if (it in 120..135) 6.0 else 0.0).toFloat().coerceAtLeast(0f) }
        val ele = FloatArray(n) { (140 + 90 * sin(it / 60.0) + 25 * sin(it / 13.0)).toFloat() }
        val r = rides[0]
        paparazzi.snapshot {
            PedalTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 24.dp)) {
                        Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(28.dp))) {
                            FakeMap(showRoute = false, trackUntil = 1f)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(r.name, style = MaterialTheme.typography.headlineMedium)
                        Text(Fmt.date(r.startTime), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        Row {
                            StatValue("Distance", Fmt.distanceLarge(r.distanceM, Units.METRIC), Modifier.weight(1f), valueSize = 30.sp)
                            StatValue("Moving time", Measure(Fmt.duration(r.movingTimeMs), ""), Modifier.weight(1f), valueSize = 30.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        TwoColumnGrid(
                            listOf(
                                { m -> StatTile("Avg speed", Fmt.speed(r.avgSpeedMps, Units.METRIC), Icons.Filled.Speed, m) },
                                { m -> StatTile("Max speed", Fmt.speed(r.maxSpeedMps, Units.METRIC), Icons.Filled.Speed, m, MaterialTheme.colorScheme.tertiary) },
                                { m -> StatTile("Climb", Fmt.elevation(r.elevGainM, Units.METRIC), Icons.AutoMirrored.Filled.TrendingUp, m, MaterialTheme.colorScheme.secondary) },
                                { m -> StatTile("Descent", Fmt.elevation(r.elevLossM, Units.METRIC), Icons.AutoMirrored.Filled.TrendingDown, m, MaterialTheme.colorScheme.secondary) },
                            )
                        )
                        Spacer(Modifier.height(14.dp))
                        ChartCard("Speed", MaterialTheme.colorScheme.tertiary, xs, speed, Units.METRIC, isSpeed = true)
                        Spacer(Modifier.height(10.dp))
                        ChartCard("Elevation", MaterialTheme.colorScheme.secondary, xs, ele, Units.METRIC, isSpeed = false)
                    }
                }
            }
        }
    }
}

