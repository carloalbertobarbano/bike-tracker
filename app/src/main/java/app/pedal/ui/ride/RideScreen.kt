package app.pedal.ui.ride

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pedal.data.AppSettings
import app.pedal.data.MapStyle
import app.pedal.data.Units
import app.pedal.tracking.LiveLocation
import app.pedal.tracking.RouteProgress
import app.pedal.tracking.RouteTrack
import app.pedal.tracking.TrackingService
import app.pedal.tracking.TrackingState
import app.pedal.tracking.TrackingStatus
import app.pedal.ui.components.StatValue
import app.pedal.ui.map.OsmMap
import app.pedal.ui.theme.Numeric
import app.pedal.util.Angles
import app.pedal.util.Fmt
import app.pedal.util.Measure
import kotlinx.coroutines.delay

@Composable
fun RideScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    vm: RideViewModel = viewModel(),
) {
    val context = LocalContext.current
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val location by vm.location.collectAsStateWithLifecycle()
    val route by vm.activeRoute.collectAsStateWithLifecycle()
    val progress by vm.routeProgress.collectAsStateWithLifecycle()
    val heading by vm.heading.collectAsStateWithLifecycle()

    var follow by rememberSaveable { mutableStateOf(true) }
    var showLayers by remember { mutableStateOf(false) }
    var showStop by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var startAfterGrant by remember { mutableStateOf(false) }

    fun start() {
        if (!isLocationEnabled(context)) {
            Toast.makeText(context, "Turn on location to record a ride", Toast.LENGTH_LONG).show()
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        TrackingService.start(context)
        follow = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasLocationPermission(context)
        if (startAfterGrant && hasFineLocation(context)) start()
        startAfterGrant = false
    }

    fun requestPermissions(thenStart: Boolean) {
        startAfterGrant = thenStart
        permissionLauncher.launch(requiredPermissions())
    }

    fun onStartClicked() {
        if (!hasFineLocation(context) || needsNotificationPermission(context)) {
            if (hasFineLocation(context)) {
                // Notifications are optional; ask but start anyway.
                permissionLauncher.launch(requiredPermissions())
                start()
            } else requestPermissions(thenStart = true)
        } else start()
    }

    // Light location updates while the screen is visible and we're not recording.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, hasPermission, tracking.isActive) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    hasPermission = hasLocationPermission(context)
                    if (hasPermission && !tracking.isActive) vm.startIdleUpdates()
                }
                Lifecycle.Event.ON_STOP -> vm.stopIdleUpdates()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vm.stopIdleUpdates()
        }
    }

    val view = LocalView.current
    DisposableEffect(tracking.isActive, settings.keepScreenOn) {
        view.keepScreenOn = tracking.isActive && settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val offRoute = if (tracking.isActive) tracking.offRoute
    else (progress?.distanceFromRoute ?: 0.0) > settings.offRouteDistanceM

    // Heading-up rotates the map only while it follows you; after a manual pan the rotation freezes
    // until you re-centre. Before the first bearing arrives (standing still) nothing changes.
    val orientation: Float? = when {
        !settings.headingUp -> 0f
        follow -> heading?.let { -it }
        else -> null
    }
    var mapRotation by remember { mutableFloatStateOf(0f) }
    SideEffect { orientation?.let { mapRotation = it } }

    RideContent(
        tracking = tracking,
        settings = settings,
        location = location,
        route = route,
        progress = progress,
        offRoute = offRoute,
        hasPermission = hasPermission,
        follow = follow,
        bottomPadding = contentPadding.calculateBottomPadding(),
        map = { centerOffsetY ->
            OsmMap(
                style = settings.mapStyle,
                modifier = Modifier.fillMaxSize(),
                track = tracking.segments,
                route = route?.points,
                location = location,
                follow = follow,
                centerOffsetY = centerOffsetY,
                orientation = orientation,
                onUserPan = { follow = false },
            )
        },
        onRecenter = { follow = true },
        headingUp = settings.headingUp,
        mapRotation = mapRotation,
        onToggleOrientation = {
            if (!settings.headingUp) follow = true
            vm.toggleHeadingUp()
        },
        onLayers = { showLayers = true },
        onSettings = onOpenSettings,
        onStopFollowing = vm::stopFollowing,
        onAllowLocation = { requestPermissions(thenStart = false) },
        onStart = ::onStartClicked,
        onPause = { TrackingService.pause(context) },
        onResume = { TrackingService.resume(context) },
        onStop = { showStop = true },
    )

    if (showLayers) {
        LayersSheet(settings.mapStyle, onSelect = { vm.setMapStyle(it) }, onDismiss = { showLayers = false })
    }

    if (showStop) {
        AlertDialog(
            onDismissRequest = { showStop = false },
            title = { Text("Finish ride?") },
            text = { Text("Save this ride to your history, or discard it.") },
            confirmButton = {
                Button(onClick = {
                    showStop = false
                    TrackingService.stop(context, discard = false)
                }) { Text("Save ride") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showStop = false
                        TrackingService.stop(context, discard = true)
                    }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showStop = false }) { Text("Keep riding") }
                }
            },
        )
    }
}

/** Stateless ride screen layout; the map is provided as a slot so it can be swapped in previews. */
@Composable
internal fun RideContent(
    tracking: TrackingState,
    settings: AppSettings,
    location: LiveLocation?,
    route: RouteTrack?,
    progress: RouteProgress?,
    offRoute: Boolean,
    hasPermission: Boolean,
    follow: Boolean,
    headingUp: Boolean,
    mapRotation: Float,
    bottomPadding: Dp,
    map: @Composable (centerOffsetY: Int) -> Unit,
    onRecenter: () -> Unit,
    onToggleOrientation: () -> Unit,
    onLayers: () -> Unit,
    onSettings: () -> Unit,
    onStopFollowing: () -> Unit,
    onAllowLocation: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    var panelHeight by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
        map(-panelHeight / 2)

        // Soft scrim so status-bar icons stay legible over bright map tiles.
        Box(
            Modifier.fillMaxWidth().height(80.dp).background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.55f), Color.Transparent))
            )
        )

        // Top: route chip + map buttons
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.weight(1f)) {
                route?.let { r ->
                    RouteChip(r, progress, offRoute, settings.units, onClose = onStopFollowing)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MapButton(Icons.Filled.Layers, "Map style", onClick = onLayers)
                MapButton(Icons.Filled.Settings, "Settings", onClick = onSettings)
                CompassButton(headingUp, mapRotation, onToggleOrientation)
            }
        }

        // Bottom: attribution, recenter, panel
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { panelHeight = it.height }
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    settings.mapStyle.attribution,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.sp),
                    color = Color(0xFF333333),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(!follow && location != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    MapButton(Icons.Filled.MyLocation, "Re-center", tint = MaterialTheme.colorScheme.primary, onClick = onRecenter)
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                !hasPermission -> PermissionPanel(onAllowLocation)
                tracking.isActive -> RecordingPanel(
                    state = tracking,
                    units = settings.units,
                    onPause = onPause,
                    onResume = onResume,
                    onStop = onStop,
                )
                else -> IdlePanel(location, route, settings.units, onStart = onStart)
            }
        }
    }
}

// --- Panels ------------------------------------------------------------------------------------

@Composable
internal fun Panel(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) { content() }
}

@Composable
internal fun IdlePanel(location: LiveLocation?, route: RouteTrack?, units: Units, onStart: () -> Unit) {
    Panel {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ready to ride", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (route != null) "Following ${route.name} · ${Fmt.distanceLarge(route.total, units)}"
                        else "Record your route, speed and climbing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                GpsBadge(location)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Start ride", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
internal fun GpsBadge(location: LiveLocation?) {
    val good = location != null && location.accuracy <= 15f
    val color = when {
        location == null -> MaterialTheme.colorScheme.onSurfaceVariant
        good -> MaterialTheme.colorScheme.primary
        else -> Color(0xFFFFB020)
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            if (location == null) "No GPS" else "±${location.accuracy.toInt()} m",
            style = MaterialTheme.typography.labelMedium.merge(Numeric),
            color = color,
        )
    }
}

@Composable
internal fun RecordingPanel(
    state: TrackingState,
    units: Units,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val paused = state.status == TrackingStatus.PAUSED
    val speed = Fmt.speed(state.speed, units)

    Panel {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(state)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                    Icon(if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, "More stats")
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            speed.value,
                            style = Numeric.copy(fontSize = 64.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2).sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            speed.unit,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                StatValue(
                    "Distance",
                    Fmt.distanceLarge(state.distance, units),
                    valueSize = 34.sp,
                    alignment = Alignment.End,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row {
                StatValue("Moving", Measure(Fmt.duration(state.movingTimeMs), ""), Modifier.weight(1f), valueSize = 22.sp)
                StatValue("Avg", Fmt.speed(state.avgSpeed, units), Modifier.weight(1f), valueSize = 22.sp)
                StatValue("Climb", Fmt.elevation(state.elevGain, units), Modifier.weight(1f), valueSize = 22.sp)
            }
            AnimatedVisibility(expanded) {
                Row(Modifier.padding(top = 14.dp)) {
                    StatValue("Max", Fmt.speed(state.maxSpeed, units), Modifier.weight(1f), valueSize = 22.sp)
                    StatValue("Elapsed", Measure(Fmt.duration(now - state.startTime), ""), Modifier.weight(1f), valueSize = 22.sp)
                    StatValue(
                        "Altitude",
                        state.altitude?.let { Fmt.elevation(it, units) } ?: Measure("—", ""),
                        Modifier.weight(1f),
                        valueSize = 22.sp,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (paused) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier.weight(1f).height(58.dp),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Resume", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onPause,
                        modifier = Modifier.weight(1f).height(58.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.Filled.Pause, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pause", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.size(58.dp),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Stop, "Finish ride")
                }
            }
        }
    }
}

@Composable
internal fun StatusPill(state: TrackingState) {
    val (label, color) = when {
        state.status == TrackingStatus.PAUSED -> "Paused" to Color(0xFFFFB020)
        state.autoPaused -> "Auto-paused" to MaterialTheme.colorScheme.secondary
        state.location == null -> "Waiting for GPS" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "Recording" to MaterialTheme.colorScheme.primary
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    val animate = state.status == TrackingStatus.RECORDING && !state.autoPaused
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).alpha(if (animate) pulse else 1f).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
internal fun PermissionPanel(onAllow: () -> Unit) {
    Panel {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Location access", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Needed to show where you are and record rides. It never leaves your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                Text("Allow location")
            }
        }
    }
}

@Composable
internal fun RouteChip(route: RouteTrack, progress: RouteProgress?, offRoute: Boolean, units: Units, onClose: () -> Unit) {
    val container = if (offRoute) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
    val content = if (offRoute) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Surface(shape = RoundedCornerShape(20.dp), color = container, contentColor = content, shadowElevation = 8.dp) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (offRoute) Icons.Filled.Warning else Icons.Filled.Route,
                    null,
                    tint = if (offRoute) content else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (offRoute) "Off route" else route.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = when {
                        progress == null -> "${Fmt.distanceLarge(route.total, units)} route"
                        offRoute -> "${Fmt.distance(progress.distanceFromRoute, units)} from the route"
                        else -> "${Fmt.distanceLarge(progress.remaining, units)} left · ${(progress.fraction * 100).toInt()}%"
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall.merge(Numeric), color = content.copy(alpha = 0.75f))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, "Stop following", modifier = Modifier.size(18.dp))
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth().padding(end = 10.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (offRoute) content else MaterialTheme.colorScheme.secondary,
                    trackColor = content.copy(alpha = 0.12f),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }
    }
}

/**
 * Toggles north-up / direction-of-travel. The needle always points to north on screen, and the
 * button is highlighted while the map follows your heading.
 */
@Composable
internal fun CompassButton(headingUp: Boolean, mapRotation: Float, onClick: () -> Unit) {
    // Accumulate rotation without wrapping so the needle never spins the long way round 0°/360°.
    val unwrapped = remember { floatArrayOf(mapRotation) }
    unwrapped[0] += Angles.delta(unwrapped[0], mapRotation)
    val needle by animateFloatAsState(unwrapped[0], tween(600), label = "needle")

    val north = MaterialTheme.colorScheme.tertiary
    val south = if (headingUp) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (headingUp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (headingUp) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = if (headingUp) "Map follows direction of travel. Tap for north up"
                else "Map is north up. Tap to follow direction of travel"
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp).rotate(needle)) {
            val cx = size.width / 2
            val half = size.width * 0.22f
            val northPath = Path().apply {
                moveTo(cx, 0f); lineTo(cx + half, size.height / 2); lineTo(cx - half, size.height / 2); close()
            }
            val southPath = Path().apply {
                moveTo(cx, size.height); lineTo(cx + half, size.height / 2); lineTo(cx - half, size.height / 2); close()
            }
            drawPath(northPath, north)
            drawPath(southPath, south)
        }
    }
}

@Composable
internal fun MapButton(
    icon: ImageVector,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 6.dp,
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersSheet(current: MapStyle, onSelect: (MapStyle) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Map style", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapStyle.entries.forEach { style ->
                    StyleOption(style, style == current, Modifier.weight(1f)) {
                        onSelect(style)
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleOption(style: MapStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg, icon) = when (style) {
        MapStyle.STANDARD -> Triple(Color(0xFFF2EFE9), Color(0xFF6B7A8F), Icons.Filled.Map)
        MapStyle.CYCLE -> Triple(Color(0xFFE3F3E8), Color(0xFF1E9E5A), Icons.AutoMirrored.Filled.DirectionsBike)
        MapStyle.TOPO -> Triple(Color(0xFFEDE6D3), Color(0xFF9A7B3F), Icons.Filled.Terrain)
        MapStyle.SATELLITE -> Triple(Color(0xFF26352B), Color(0xFFBFD8C2), Icons.Filled.SatelliteAlt)
    }
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bg)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                ),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = fg, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(
            style.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Permission helpers ------------------------------------------------------------------------

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasFineLocation(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

private fun hasLocationPermission(context: Context) =
    hasFineLocation(context) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun needsNotificationPermission(context: Context) =
    Build.VERSION.SDK_INT >= 33 && !granted(context, Manifest.permission.POST_NOTIFICATIONS)

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun isLocationEnabled(context: Context): Boolean =
    LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))
