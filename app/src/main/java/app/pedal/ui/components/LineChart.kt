package app.pedal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight area chart drawn on a Canvas, with touch scrubbing.
 * [xs] must be ascending and the same length as [ys].
 */
@Composable
fun LineChart(
    xs: FloatArray,
    ys: FloatArray,
    color: Color,
    formatX: (Float) -> String,
    formatY: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    if (xs.size < 2 || xs.size != ys.size) return
    val measurer = rememberTextMeasurer()
    var scrubX by remember { mutableStateOf<Float?>(null) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val bubbleColor = MaterialTheme.colorScheme.inverseSurface
    val bubbleText = MaterialTheme.colorScheme.inverseOnSurface
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor, fontFeatureSettings = "tnum")

    val minY = ys.min()
    val maxY = ys.max()
    val padY = ((maxY - minY) * 0.12f).coerceAtLeast(1f)
    val lo = minY - padY
    val hi = maxY + padY
    val x0 = xs.first()
    val x1 = xs.last().takeIf { it > x0 } ?: (x0 + 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    scrubX = it.x
                    tryAwaitRelease()
                    scrubX = null
                })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { scrubX = it.x },
                    onDragEnd = { scrubX = null },
                    onDragCancel = { scrubX = null },
                ) { change, _ -> scrubX = change.position.x }
            },
    ) {
        val bottomPad = 20.dp.toPx()
        val topPad = 8.dp.toPx()
        val plotH = size.height - bottomPad - topPad
        val w = size.width
        fun px(x: Float) = (x - x0) / (x1 - x0) * w
        fun py(y: Float) = topPad + plotH - (y - lo) / (hi - lo) * plotH

        // Grid
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
        for (i in 0..2) {
            val y = topPad + plotH * i / 2f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }

        val line = Path()
        val fill = Path()
        for (i in xs.indices) {
            val x = px(xs[i]); val y = py(ys[i])
            if (i == 0) {
                line.moveTo(x, y); fill.moveTo(x, topPad + plotH); fill.lineTo(x, y)
            } else {
                line.lineTo(x, y); fill.lineTo(x, y)
            }
        }
        fill.lineTo(px(xs.last()), topPad + plotH)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.02f)), startY = topPad, endY = topPad + plotH))
        drawPath(line, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Labels: max/min on the left, distance at the bottom.
        drawText(measurer, formatY(maxY), Offset(4.dp.toPx(), topPad), labelStyle)
        val minLabel = measurer.measure(formatY(minY), labelStyle)
        drawText(minLabel, topLeft = Offset(4.dp.toPx(), topPad + plotH - minLabel.size.height - 2.dp.toPx()))
        drawText(measurer, formatX(x0), Offset(0f, size.height - bottomPad + 4.dp.toPx()), labelStyle)
        val endLabel = measurer.measure(formatX(xs.last()), labelStyle)
        drawText(endLabel, topLeft = Offset(w - endLabel.size.width, size.height - bottomPad + 4.dp.toPx()))

        scrubX?.let { sx ->
            val target = x0 + (sx / w).coerceIn(0f, 1f) * (x1 - x0)
            var idx = xs.binarySearch(target).let { if (it < 0) -it - 1 else it }.coerceIn(0, xs.lastIndex)
            if (idx > 0 && target - xs[idx - 1] < xs[idx] - target) idx--
            val cx = px(xs[idx]); val cy = py(ys[idx])
            drawLine(labelColor.copy(alpha = 0.6f), Offset(cx, topPad), Offset(cx, topPad + plotH), strokeWidth = 1.dp.toPx())
            drawCircle(Color.White, 6.dp.toPx(), Offset(cx, cy))
            drawCircle(color, 4.dp.toPx(), Offset(cx, cy))

            val text = measurer.measure(
                "${formatY(ys[idx])}  ·  ${formatX(xs[idx])}",
                TextStyle(fontSize = 12.sp, color = bubbleText, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            )
            val bw = text.size.width + 20.dp.toPx()
            val bh = text.size.height + 10.dp.toPx()
            val bx = (cx - bw / 2).coerceIn(0f, w - bw)
            drawRoundRect(bubbleColor, Offset(bx, 0f), Size(bw, bh), CornerRadius(bh / 2))
            drawText(text, topLeft = Offset(bx + 10.dp.toPx(), 5.dp.toPx()))
        }
    }
}
