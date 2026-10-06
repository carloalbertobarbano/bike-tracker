package app.pedal.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** "You are here" dot with accuracy halo and heading arrow. */
class LocationDotOverlay(private val density: Float, color: Int) : Overlay() {
    var location: GeoPoint? = null
    var accuracy: Float = 0f
    var bearing: Float? = null

    private val point = Point()
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 40; style = Paint.Style.FILL }
    private val haloStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; alpha = 90; style = Paint.Style.STROKE; strokeWidth = density
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL
        setShadowLayer(3 * density, 0f, density, 0x55000000)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private val arrow = Path()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val loc = location ?: return
        val pj = mapView.projection
        pj.toPixels(loc, point)
        val x = point.x.toFloat()
        val y = point.y.toFloat()

        val accPx = pj.metersToPixels(accuracy)
        if (accPx > 12 * density) {
            canvas.drawCircle(x, y, accPx, halo)
            canvas.drawCircle(x, y, accPx, haloStroke)
        }
        bearing?.let { b ->
            canvas.save()
            canvas.rotate(b - mapView.mapOrientation, x, y)
            arrow.reset()
            arrow.moveTo(x, y - 17 * density)
            arrow.lineTo(x - 7 * density, y - 7 * density)
            arrow.lineTo(x + 7 * density, y - 7 * density)
            arrow.close()
            canvas.drawPath(arrow, dot)
            canvas.restore()
        }
        canvas.drawCircle(x, y, 9 * density, ring)
        canvas.drawCircle(x, y, 6.5f * density, dot)
    }
}

/** Small start/finish markers. */
class EndpointsOverlay(private val density: Float, startColor: Int, endColor: Int) : Overlay() {
    var start: GeoPoint? = null
    var end: GeoPoint? = null

    private val point = Point()
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(2 * density, 0f, density, 0x55000000)
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = startColor }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = endColor }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val pj = mapView.projection
        start?.let { draw(canvas, pj.toPixels(it, point), startPaint) }
        end?.let { draw(canvas, pj.toPixels(it, point), endPaint) }
    }

    private fun draw(canvas: Canvas, p: Point, paint: Paint) {
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), 7 * density, white)
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), 4.5f * density, paint)
    }
}
