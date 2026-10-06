package app.pedal.ui.history

import android.app.Application
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pedal.container
import app.pedal.data.MapStyle
import app.pedal.data.RideEntity
import app.pedal.data.TrackPointEntity
import app.pedal.data.Units
import app.pedal.ui.components.ConfirmDialog
import app.pedal.ui.components.LineChart
import app.pedal.ui.components.RenameDialog
import app.pedal.ui.components.StatTile
import app.pedal.ui.components.StatValue
import app.pedal.ui.components.TwoColumnGrid
import app.pedal.ui.map.OsmMap
import app.pedal.util.Fmt
import app.pedal.util.Geo
import app.pedal.util.LatLon
import app.pedal.util.Measure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Track data prepared for drawing: map polylines plus downsampled chart series. */
class TrackData(
    val segments: List<List<LatLon>>,
    val all: List<LatLon>,
    val chartDistance: FloatArray,
    val chartSpeed: FloatArray,
    val chartElevation: FloatArray?,
    val minEle: Double?,
    val maxEle: Double?,
) {
    companion object {
        private const val CHART_POINTS = 300

        fun from(points: List<TrackPointEntity>): TrackData {
            val segments = points.groupBy { it.segment }.toSortedMap().values.map { seg -> seg.map { LatLon(it.lat, it.lon) } }
            val dist = DoubleArray(points.size)
            for (i in 1 until points.size) {
                val a = points[i - 1]; val b = points[i]
                dist[i] = dist[i - 1] + if (a.segment == b.segment) Geo.distance(a.lat, a.lon, b.lat, b.lon) else 0.0
            }
            // Smooth speed with a centred moving average to tame GPS noise.
            val speed = DoubleArray(points.size) { i ->
                val from = (i - 3).coerceAtLeast(0); val to = (i + 3).coerceAtMost(points.lastIndex)
                (from..to).sumOf { points[it].speed.toDouble() } / (to - from + 1)
            }
            val eles = points.map { it.ele }
            val hasEle = eles.count { it != null } > points.size / 2
            val idx = Geo.downsample(points.indices.toList(), CHART_POINTS)
            var lastEle = eles.firstOrNull { it != null } ?: 0.0
            val chartEle = if (hasEle) FloatArray(idx.size) { k ->
                (eles[idx[k]] ?: lastEle).also { lastEle = it }.toFloat()
            } else null
            return TrackData(
                segments = segments,
                all = points.map { LatLon(it.lat, it.lon) },
                chartDistance = FloatArray(idx.size) { dist[idx[it]].toFloat() },
                chartSpeed = FloatArray(idx.size) { speed[idx[it]].toFloat() },
                chartElevation = chartEle,
                minEle = eles.filterNotNull().minOrNull(),
                maxEle = eles.filterNotNull().maxOrNull(),
            )
        }
    }
}

class RideDetailViewModel(app: Application, val rideId: Long) : AndroidViewModel(app) {
    private val repo = app.container.repository
    val ride: StateFlow<RideEntity?> = repo.ride(rideId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val data = MutableStateFlow<TrackData?>(null)

    init {
        viewModelScope.launch(Dispatchers.Default) { data.value = TrackData.from(repo.ridePoints(rideId)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(rideId: Long, onBack: () -> Unit, onOpenRoute: (Long) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: RideDetailViewModel = viewModel(key = "ride-$rideId") { RideDetailViewModel(app, rideId) }
    val repo = context.container.repository
    val ride by vm.ride.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val settings by context.container.settings.state.collectAsStateWithLifecycle()
    val units = settings.units
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { repo.exportRideTo(rideId, it) }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "GPX saved" else "Couldn't save the file")
        }
    }

    fun share() = scope.launch {
        runCatching {
            val uri = repo.exportRideToCache(rideId)
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/gpx+xml")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Share ride"))
        }.onFailure { snackbar.showSnackbar("Export failed") }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = ::share) { Icon(Icons.Filled.Share, "Share GPX") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Save GPX file") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                            onClick = { menu = false; ride?.let { saveLauncher.launch(repo.suggestedFileName(it)) } },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; deleting = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val r = ride
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            MapCard(settings.mapStyle, data?.segments.orEmpty(), null, data?.all)
            Spacer(Modifier.height(20.dp))
            Text(r.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Fmt.date(r.startTime), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row {
                StatValue("Distance", Fmt.distanceLarge(r.distanceM, units), Modifier.weight(1f), valueSize = 30.sp)
                StatValue("Moving time", Measure(Fmt.duration(r.movingTimeMs), ""), Modifier.weight(1f), valueSize = 30.sp)
            }
            Spacer(Modifier.height(16.dp))
            val d = data
            TwoColumnGrid(
                listOf(
                    { m -> StatTile("Avg speed", Fmt.speed(r.avgSpeedMps, units), Icons.Filled.Speed, m) },
                    { m -> StatTile("Max speed", Fmt.speed(r.maxSpeedMps, units), Icons.Filled.Speed, m, MaterialTheme.colorScheme.tertiary) },
                    { m -> StatTile("Climb", Fmt.elevation(r.elevGainM, units), Icons.AutoMirrored.Filled.TrendingUp, m, MaterialTheme.colorScheme.secondary) },
                    { m -> StatTile("Descent", Fmt.elevation(r.elevLossM, units), Icons.AutoMirrored.Filled.TrendingDown, m, MaterialTheme.colorScheme.secondary) },
                    { m -> StatTile("Elapsed", Measure(Fmt.duration(r.elapsedMs), ""), Icons.Filled.Timer, m) },
                    { m ->
                        StatTile(
                            "Highest",
                            d?.maxEle?.let { Fmt.elevation(it, units) } ?: Measure("—", ""),
                            Icons.Filled.Landscape, m, MaterialTheme.colorScheme.secondary,
                        )
                    },
                )
            )
            if (d != null && d.chartDistance.size >= 2) {
                Spacer(Modifier.height(20.dp))
                ChartCard("Speed", MaterialTheme.colorScheme.tertiary, d.chartDistance, d.chartSpeed, units, isSpeed = true)
                d.chartElevation?.let {
                    Spacer(Modifier.height(12.dp))
                    ChartCard("Elevation", MaterialTheme.colorScheme.secondary, d.chartDistance, it, units, isSpeed = false)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = ::share, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.Share, null, Modifier.padding(end = 8.dp)); Text("Export GPX")
                }
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            val id = repo.saveRideAsRoute(rideId)
                            onOpenRoute(id)
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Route, null, Modifier.padding(end = 8.dp)); Text("Save as route")
                }
            }
        }
    }

    if (renaming) {
        RenameDialog("Rename ride", ride?.name.orEmpty(), onDismiss = { renaming = false }) { name ->
            renaming = false
            scope.launch { repo.renameRide(rideId, name) }
        }
    }
    if (deleting) {
        ConfirmDialog("Delete ride?", "This ride and its track will be permanently removed.", "Delete", onDismiss = { deleting = false }) {
            deleting = false
            scope.launch {
                repo.deleteRide(rideId)
                onBack()
            }
        }
    }
}

@Composable
fun MapCard(style: MapStyle, track: List<List<LatLon>>, route: List<LatLon>?, fit: List<LatLon>?) {
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (fit != null) {
            OsmMap(
                style = style,
                modifier = Modifier.fillMaxSize(),
                track = track,
                route = route,
                fitPoints = fit,
                showEndpoints = true,
            )
        }
        Text(
            style.attribution,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.sp),
            color = Color(0xFF333333),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.7f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun ChartCard(title: String, color: Color, xs: FloatArray, ys: FloatArray, units: Units, isSpeed: Boolean) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isSpeed) Icons.Filled.Speed else Icons.Filled.Terrain,
                    null, tint = color, modifier = Modifier.padding(end = 8.dp),
                )
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("drag to inspect", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(4.dp).height(12.dp))
            LineChart(
                xs = xs,
                ys = ys,
                color = color,
                formatX = { Fmt.distanceLarge(it.toDouble(), units).toString() },
                formatY = { if (isSpeed) Fmt.speed(it.toDouble(), units).toString() else Fmt.elevation(it.toDouble(), units).toString() },
            )
        }
    }
}
