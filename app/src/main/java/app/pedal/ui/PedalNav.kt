package app.pedal.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pedal.container
import app.pedal.tracking.TrackingEvent
import app.pedal.tracking.TrackingSession
import app.pedal.ui.history.HistoryScreen
import app.pedal.ui.history.RideDetailScreen
import app.pedal.ui.ride.RideScreen
import app.pedal.ui.routes.RouteDetailScreen
import app.pedal.ui.routes.RoutesScreen
import app.pedal.ui.settings.SettingsScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One-off UI events that may be produced before the UI is listening (buffered). */
object AppEvents {
    val messages = Channel<String>(Channel.BUFFERED)
    val openRoute = Channel<Long>(Channel.BUFFERED)
}

fun importGpx(context: Context, uri: Uri) {
    val container = context.container
    container.appScope.launch {
        runCatching { container.repository.importGpx(uri) }
            .onSuccess {
                AppEvents.openRoute.send(it)
                AppEvents.messages.send("Route imported")
            }
            .onFailure { AppEvents.messages.send(it.message ?: "Couldn't import this file") }
    }
}

private fun NavController.safePop() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}

internal const val TAB_RIDE = 0
internal const val TAB_HISTORY = 1
internal const val TAB_ROUTES = 2

@Composable
fun PedalNav() {
    val nav = rememberNavController()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(TAB_RIDE) }
    val snackbar = remember { SnackbarHostState() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importGpx(context, uri)
    }

    LaunchedEffect(Unit) {
        TrackingSession.events.collect { e ->
            if (e is TrackingEvent.RideSaved) {
                tab = TAB_HISTORY
                nav.navigate("ride/${e.rideId}") { launchSingleTop = true }
            }
        }
    }
    LaunchedEffect(Unit) {
        AppEvents.openRoute.receiveAsFlow().collect { id ->
            tab = TAB_ROUTES
            nav.navigate("route/$id") { launchSingleTop = true }
        }
    }
    LaunchedEffect(Unit) {
        AppEvents.messages.receiveAsFlow().collect { snackbar.showSnackbar(it) }
    }

    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 10 } },
    ) {
        composable("home") {
            HomeScreen(
                tab = tab,
                onTab = { tab = it },
                snackbar = snackbar,
                onOpenRide = { nav.navigate("ride/$it") },
                onOpenRoute = { nav.navigate("route/$it") },
                onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
                onImport = { importLauncher.launch(arrayOf("*/*")) },
            )
        }
        composable("ride/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            RideDetailScreen(
                rideId = entry.arguments!!.getLong("id"),
                onBack = { nav.safePop() },
                onOpenRoute = { id -> nav.navigate("route/$id") },
            )
        }
        composable("route/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            RouteDetailScreen(
                routeId = entry.arguments!!.getLong("id"),
                onBack = { nav.safePop() },
                onFollow = {
                    tab = TAB_RIDE
                    nav.popBackStack("home", inclusive = false)
                },
            )
        }
        composable("settings") { SettingsScreen(onBack = { nav.safePop() }) }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("Ride", Icons.AutoMirrored.Filled.DirectionsBike),
    TabItem("History", Icons.Filled.History),
    TabItem("Routes", Icons.Filled.Route),
)

@Composable
private fun HomeScreen(
    tab: Int,
    onTab: (Int) -> Unit,
    snackbar: SnackbarHostState,
    onOpenRide: (Long) -> Unit,
    onOpenRoute: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
) {
    val tracking by TrackingSession.state.collectAsStateWithLifecycle()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { PedalBottomBar(tab, tracking.isActive, onTab) },
    ) { padding ->
        val bottomOnly = PaddingValues(bottom = padding.calculateBottomPadding())
        when (tab) {
            TAB_RIDE -> RideScreen(contentPadding = bottomOnly, onOpenSettings = onOpenSettings)
            TAB_HISTORY -> HistoryScreen(contentPadding = bottomOnly, onOpenRide = onOpenRide, onOpenSettings = onOpenSettings)
            else -> RoutesScreen(contentPadding = bottomOnly, onImport = onImport, onOpenRoute = onOpenRoute)
        }
    }
}

@Composable
internal fun PedalBottomBar(tab: Int, recording: Boolean, onTab: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        tabs.forEachIndexed { i, item ->
            NavigationBarItem(
                selected = tab == i,
                onClick = { onTab(i) },
                icon = {
                    BadgedBox(badge = { if (i == TAB_RIDE && recording && tab != TAB_RIDE) Badge() }) {
                        Icon(item.icon, item.label)
                    }
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}
