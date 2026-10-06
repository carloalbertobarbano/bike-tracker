package app.pedal.ui.routes

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pedal.container
import app.pedal.data.RouteEntity
import app.pedal.data.Units
import app.pedal.tracking.TrackingSession
import app.pedal.ui.components.ConfirmDialog
import app.pedal.ui.components.EmptyState
import app.pedal.ui.components.GlyphTile
import app.pedal.ui.components.MiniStat
import app.pedal.ui.components.RenameDialog
import app.pedal.ui.components.StatTile
import app.pedal.ui.components.TwoColumnGrid
import app.pedal.ui.history.ChartCard
import app.pedal.ui.history.MapCard
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

@Composable
fun RoutesScreen(contentPadding: PaddingValues, onImport: () -> Unit, onOpenRoute: (Long) -> Unit) {
    val context = LocalContext.current
    val routes by context.container.repository.routes.collectAsStateWithLifecycle(initialValue = null)
    val active by TrackingSession.activeRoute.collectAsStateWithLifecycle()
    val settings by context.container.settings.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(start = 4.dp, top = 6.dp, bottom = 10.dp)) {
                    Text("Routes", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Import a GPX track and follow it on the map",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val list = routes ?: return@LazyColumn
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.Route,
                        "No routes yet",
                        "Import a .gpx file from Komoot, Strava, RideWithGPS or any other planner.",
                        Modifier.fillMaxWidth().padding(top = 40.dp),
                    )
                }
            }
            items(list, key = { it.id }) { route ->
                RouteCard(route, route.id == active?.id, settings.units) { onOpenRoute(route.id) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onImport,
            icon = { Icon(Icons.Filled.FileOpen, null) },
            text = { Text("Import GPX") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
internal fun RouteCard(route: RouteEntity, active: Boolean, units: Units, onClick: () -> Unit) {
    val preview = remember(route.preview) { Geo.decode(route.preview) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphTile(preview, MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(route.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (active) {
                    Text("Following", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(Fmt.distanceLarge(route.distanceM, units).toString(), style = MaterialTheme.typography.labelLarge)
                    if (route.hasElevation) {
                        MiniStat("↑ ${Fmt.elevation(route.elevGainM, units)}")
                        MiniStat("↓ ${Fmt.elevation(route.elevLossM, units)}")
                    }
                }
            }
        }
    }
}

// --- Route detail --------------------------------------------------------------------------------

class RouteData(val points: List<LatLon>, val xs: FloatArray, val elevation: FloatArray?)

class RouteDetailViewModel(app: Application, val routeId: Long) : AndroidViewModel(app) {
    private val repo = app.container.repository
    val route: StateFlow<RouteEntity?> = repo.route(routeId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val data = MutableStateFlow<RouteData?>(null)

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val pts = repo.routePoints(routeId)
            val latLons = pts.map { LatLon(it.lat, it.lon) }
            val cum = Geo.cumulative(latLons)
            val idx = Geo.downsample(pts.indices.toList(), 300)
            val hasEle = pts.count { it.ele != null } > pts.size / 2
            var last = pts.firstOrNull { it.ele != null }?.ele ?: 0.0
            data.value = RouteData(
                latLons,
                FloatArray(idx.size) { cum[idx[it]].toFloat() },
                if (hasEle) FloatArray(idx.size) { k -> (pts[idx[k]].ele ?: last).also { last = it }.toFloat() } else null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(routeId: Long, onBack: () -> Unit, onFollow: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: RouteDetailViewModel = viewModel(key = "route-$routeId") { RouteDetailViewModel(app, routeId) }
    val container = context.container
    val route by vm.route.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val active by TrackingSession.activeRoute.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val units = settings.units
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val isActive = active?.id == routeId

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; renaming = true },
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
        val r = route
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
            MapCard(settings.mapStyle, emptyList(), data?.points, data?.points)
            Spacer(Modifier.height(20.dp))
            Text(r.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${r.pointCount} points · imported ${Fmt.dateShort(r.createdAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (isActive) container.followRoute(null)
                    else {
                        container.followRoute(routeId)
                        onFollow()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = if (isActive) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
            ) {
                Icon(if (isActive) Icons.Filled.Close else Icons.Filled.Navigation, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isActive) "Stop following" else "Follow this route", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
            val start = data?.points?.firstOrNull()
            val end = data?.points?.lastOrNull()
            val loop = start != null && end != null && Geo.distance(start, end) < 200
            TwoColumnGrid(
                listOf(
                    { m -> StatTile("Distance", Fmt.distanceLarge(r.distanceM, units), Icons.Filled.Straighten, m) },
                    { m -> StatTile("Type", Measure(if (loop) "Loop" else "One way", ""), Icons.Filled.Place, m, MaterialTheme.colorScheme.tertiary) },
                    { m ->
                        StatTile(
                            "Climb", if (r.hasElevation) Fmt.elevation(r.elevGainM, units) else Measure("—", ""),
                            Icons.AutoMirrored.Filled.TrendingUp, m, MaterialTheme.colorScheme.secondary,
                        )
                    },
                    { m ->
                        StatTile(
                            "Descent", if (r.hasElevation) Fmt.elevation(r.elevLossM, units) else Measure("—", ""),
                            Icons.AutoMirrored.Filled.TrendingDown, m, MaterialTheme.colorScheme.secondary,
                        )
                    },
                )
            )
            data?.elevation?.let { ele ->
                Spacer(Modifier.height(16.dp))
                ChartCard("Elevation", MaterialTheme.colorScheme.secondary, data!!.xs, ele, units, isSpeed = false)
            }
        }
    }

    if (renaming) {
        RenameDialog("Rename route", route?.name.orEmpty(), onDismiss = { renaming = false }) { name ->
            renaming = false
            scope.launch {
                container.repository.renameRoute(routeId, name)
                if (isActive) container.followRoute(routeId)
            }
        }
    }
    if (deleting) {
        ConfirmDialog("Delete route?", "The route will be removed from your library.", "Delete", onDismiss = { deleting = false }) {
            deleting = false
            scope.launch {
                if (isActive) container.followRoute(null)
                container.repository.deleteRoute(routeId)
                onBack()
            }
        }
    }
}

