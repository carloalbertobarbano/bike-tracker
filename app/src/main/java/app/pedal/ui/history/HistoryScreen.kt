package app.pedal.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pedal.container
import app.pedal.data.RideEntity
import app.pedal.data.Units
import app.pedal.ui.components.EmptyState
import app.pedal.ui.components.GlyphTile
import app.pedal.ui.components.MiniStat
import app.pedal.ui.components.StatValue
import app.pedal.util.Fmt
import app.pedal.util.Geo
import app.pedal.util.Measure

@Composable
fun HistoryScreen(contentPadding: PaddingValues, onOpenRide: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val rides by context.container.repository.rides.collectAsStateWithLifecycle(initialValue = null)
    val settings by context.container.settings.state.collectAsStateWithLifecycle()
    val units = settings.units

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                Text("Rides", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
            }
        }
        val list = rides ?: return@LazyColumn
        if (list.isEmpty()) {
            item {
                EmptyState(
                    Icons.AutoMirrored.Filled.DirectionsBike,
                    "No rides yet",
                    "Head to the Ride tab and tap Start. Your rides will show up here.",
                    Modifier.fillMaxWidth().padding(top = 60.dp),
                )
            }
            return@LazyColumn
        }
        item { TotalsCard(list, units) }
        item {
            Text(
                "RECENT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
            )
        }
        items(list, key = { it.id }) { ride -> RideCard(ride, units) { onOpenRide(ride.id) } }
    }
}

@Composable
internal fun TotalsCard(rides: List<RideEntity>, units: Units) {
    val distance = rides.sumOf { it.distanceM }
    val time = rides.sumOf { it.movingTimeMs }
    val climb = rides.sumOf { it.elevGainM }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.22f), secondary.copy(alpha = 0.10f))))
                .padding(22.dp),
        ) {
            StatValue("All-time distance", Fmt.distanceLarge(distance, units), valueSize = 44.sp)
            Spacer(Modifier.height(18.dp))
            Row {
                StatValue("Rides", Measure(rides.size.toString(), ""), Modifier.weight(1f), valueSize = 20.sp)
                StatValue("Time", Measure(Fmt.durationShort(time), ""), Modifier.weight(1f), valueSize = 20.sp)
                StatValue("Climb", Fmt.elevation(climb, units), Modifier.weight(1f), valueSize = 20.sp)
            }
        }
    }
}

@Composable
internal fun RideCard(ride: RideEntity, units: Units, onClick: () -> Unit) {
    val preview = remember(ride.preview) { Geo.decode(ride.preview) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphTile(preview, MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(ride.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Fmt.dateShort(ride.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        Fmt.distanceLarge(ride.distanceM, units).toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                    )
                    MiniStat(Fmt.duration(ride.movingTimeMs))
                    MiniStat(Fmt.speed(ride.avgSpeedMps, units).toString())
                    MiniStat("↑ ${Fmt.elevation(ride.elevGainM, units)}")
                }
            }
        }
    }
}
