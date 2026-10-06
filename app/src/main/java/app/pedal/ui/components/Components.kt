package app.pedal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pedal.ui.theme.Numeric
import app.pedal.util.LatLon
import app.pedal.util.Measure
import kotlin.math.cos
import kotlin.math.max

/** Label + big number + small unit. */
@Composable
fun StatValue(
    label: String,
    measure: Measure,
    modifier: Modifier = Modifier,
    valueSize: TextUnit = 24.sp,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                measure.value,
                style = Numeric.copy(fontSize = valueSize, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (measure.unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    measure.unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = (valueSize.value * 0.12f).dp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Rounded tile containing a stat, used in grids on detail screens. */
@Composable
fun StatTile(label: String, measure: Measure, icon: ImageVector, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            StatValue(label, measure, valueSize = 19.sp)
        }
    }
}

/** Two-column grid of arbitrary composables. */
@Composable
fun TwoColumnGrid(items: List<@Composable (Modifier) -> Unit>, spacing: Dp = 10.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { it(Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A tiny drawing of a track's shape, used as a thumbnail in lists. */
@Composable
fun TrackGlyph(points: List<LatLon>, color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 2.5.dp) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val cosLat = cos(Math.toRadians(points.sumOf { it.lat } / points.size))
        val xs = points.map { it.lon * cosLat }
        val ys = points.map { -it.lat }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val pad = size.minDimension * 0.14f
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val span = max(maxX - minX, maxY - minY).takeIf { it > 0 } ?: return@Canvas
        val scale = minOf(w / span, h / span).toFloat()
        val offX = pad + (w - ((maxX - minX) * scale).toFloat()) / 2
        val offY = pad + (h - ((maxY - minY) * scale).toFloat()) / 2
        fun pt(i: Int) = Offset(offX + ((xs[i] - minX) * scale).toFloat(), offY + ((ys[i] - minY) * scale).toFloat())
        val path = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until points.size) lineTo(pt(i).x, pt(i).y)
        }
        drawPath(path, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color.White, strokeWidth.toPx() * 1.5f, pt(0))
        drawCircle(color, strokeWidth.toPx() * 0.9f, pt(0))
    }
}

/** Thumbnail tile with a track glyph on a tinted background. */
@Composable
fun GlyphTile(points: List<LatLon>, color: Color, size: Dp = 64.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.12f)),
    ) {
        TrackGlyph(points, color, Modifier.fillMaxSize())
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun MiniStat(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.merge(Numeric),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun RenameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

val SectionTitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium
