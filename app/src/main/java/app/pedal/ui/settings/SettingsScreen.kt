package app.pedal.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pedal.BuildConfig
import app.pedal.container
import app.pedal.data.MapStyle
import app.pedal.data.Units
import app.pedal.ui.theme.Numeric
import app.pedal.util.Fmt
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store = LocalContext.current.container.settings
    val s by store.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        ) {
            Section("Units") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                    Units.entries.forEachIndexed { i, u ->
                        SegmentedButton(
                            selected = s.units == u,
                            onClick = { store.update { it.copy(units = u) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Units.entries.size),
                        ) { Text(if (u == Units.METRIC) "Metric (km)" else "Imperial (mi)") }
                    }
                }
            }
            Section("Recording") {
                ToggleRow("Auto-pause", "Pause the timer when you stop moving", s.autoPause) { v ->
                    store.update { it.copy(autoPause = v) }
                }
                ToggleRow("Keep screen on", "While recording on the ride screen", s.keepScreenOn) { v ->
                    store.update { it.copy(keepScreenOn = v) }
                }
            }
            Section("Route following") {
                ToggleRow("Off-route alert", "Vibrate and notify when you leave the route", s.offRouteAlert) { v ->
                    store.update { it.copy(offRouteAlert = v) }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Alert distance", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            Fmt.distance(s.offRouteDistanceM.toDouble(), s.units).toString(),
                            style = MaterialTheme.typography.labelLarge.merge(Numeric),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = s.offRouteDistanceM.toFloat(),
                        onValueChange = { v -> store.update { it.copy(offRouteDistanceM = ((v / 5).roundToInt() * 5)) } },
                        valueRange = 25f..200f,
                        enabled = s.offRouteAlert,
                    )
                }
            }
            Section("Map") {
                Text(
                    "Orientation",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    listOf(false to "North up", true to "Direction of travel").forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = s.headingUp == value,
                            onClick = { store.update { it.copy(headingUp = value) } },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label, maxLines = 1) }
                    }
                }
                Text(
                    "Default style",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)) {
                    MapStyle.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = s.mapStyle == m,
                            onClick = { store.update { it.copy(mapStyle = m) } },
                            shape = SegmentedButtonDefaults.itemShape(i, MapStyle.entries.size),
                            icon = {},
                        ) { Text(m.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            Section("About") {
                Column(Modifier.padding(16.dp)) {
                    Text("Pedal ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Maps: © OpenStreetMap contributors (ODbL), CyclOSM, OpenTopoMap (CC-BY-SA), " +
                            "Esri World Imagery. All ride data stays on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
