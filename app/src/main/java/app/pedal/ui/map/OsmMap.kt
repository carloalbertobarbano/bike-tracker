package app.pedal.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pedal.data.MapStyle
import app.pedal.tracking.LiveLocation
import app.pedal.ui.theme.MapColors
import app.pedal.util.LatLon
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Compose wrapper around an osmdroid MapView.
 *
 * @param track recorded track, one list per segment
 * @param route route being followed / previewed
 * @param follow keep the map centred on [location]
 * @param fitPoints zoom to fit these points once (re-fits when a different list instance is passed)
 * @param centerOffsetY vertical pixel offset for the map centre (negative = higher), used to keep
 *        the location visible above the stats panel
 */
@Composable
fun OsmMap(
    style: MapStyle,
    modifier: Modifier = Modifier,
    track: List<List<LatLon>> = emptyList(),
    route: List<LatLon>? = null,
    location: LiveLocation? = null,
    follow: Boolean = false,
    fitPoints: List<LatLon>? = null,
    showEndpoints: Boolean = false,
    centerOffsetY: Int = 0,
    onUserPan: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { MapHolder(context) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.map.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.dispose()
        }
    }

    AndroidView(
        factory = { holder.map },
        modifier = modifier,
        update = {
            holder.onUserPan = onUserPan
            holder.setStyle(style)
            holder.setCenterOffset(centerOffsetY)
            holder.setRoute(route)
            holder.setTrack(track)
            holder.setEndpoints(showEndpoints, track, route)
            holder.setLocation(location, follow)
            holder.fit(fitPoints)
            holder.map.invalidate()
        },
    )
}

private class TrackLine(val casing: Polyline, val line: Polyline, var drawn: Int = 0)

private class MapHolder(private val context: Context) {
    private val density = context.resources.displayMetrics.density

    val map: MapView = MapView(context).apply {
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        isVerticalMapRepetitionEnabled = false
        isHorizontalMapRepetitionEnabled = true
        minZoomLevel = 3.0
        maxZoomLevel = 20.0
        setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
        controller.setZoom(5.0)
        controller.setCenter(GeoPoint(45.0, 10.0))
    }

    var onUserPan: () -> Unit = {}

    private val routeCasing = polyline(MapColors.CASING, 9f)
    private val routeLine = polyline(MapColors.ROUTE, 5.5f).apply { outlinePaint.alpha = 230 }
    private val trackLines = ArrayList<TrackLine>()
    private val endpoints = EndpointsOverlay(density, MapColors.START, MapColors.END)
    private val locationDot = LocationDotOverlay(density, MapColors.LOCATION)
    private var labels: TilesOverlay? = null

    private var style: MapStyle? = null
    private var routeRef: List<LatLon>? = null
    private var fitRef: List<LatLon>? = null
    private var centerOffset = Int.MIN_VALUE
    private var centeredOnce = false
    private var lastFollow = false
    private var lastFollowed: LiveLocation? = null

    init {
        attachTouchListener()
        rebuildOverlays()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchListener() {
        map.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                // Keep scrolling parents (e.g. detail screens) from stealing map gestures.
                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) onUserPan()
            }
            false
        }
    }

    private fun polyline(color: Int, widthDp: Float) = Polyline(map).apply {
        outlinePaint.apply {
            this.color = color
            strokeWidth = widthDp * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        infoWindow = null
        setOnClickListener { _, _, _ -> false }
    }

    fun setStyle(s: MapStyle) {
        if (s == style) return
        style = s
        map.setTileSource(TileSources.forStyle(s))
        if (s == MapStyle.SATELLITE && labels == null) {
            labels = TilesOverlay(MapTileProviderBasic(context, TileSources.satelliteLabels), context).apply {
                loadingBackgroundColor = Color.TRANSPARENT
                loadingLineColor = Color.TRANSPARENT
            }
        }
        rebuildOverlays()
    }

    fun setCenterOffset(y: Int) {
        if (y == centerOffset) return
        centerOffset = y
        map.setMapCenterOffset(0, y)
    }

    fun setRoute(route: List<LatLon>?) {
        if (route === routeRef) return
        routeRef = route
        val pts = route?.map { GeoPoint(it.lat, it.lon) } ?: emptyList()
        routeCasing.setPoints(pts)
        routeLine.setPoints(pts)
        rebuildOverlays()
    }

    fun setTrack(segments: List<List<LatLon>>) {
        var changed = false
        while (trackLines.size < segments.size) {
            trackLines += TrackLine(polyline(MapColors.CASING, 8f), polyline(MapColors.TRACK, 4.5f))
            changed = true
        }
        while (trackLines.size > segments.size) {
            trackLines.removeAt(trackLines.lastIndex)
            changed = true
        }
        segments.forEachIndexed { i, seg ->
            val tl = trackLines[i]
            if (seg.size > tl.drawn && tl.drawn > 0) {
                // Live recording: append only the new points.
                for (j in tl.drawn until seg.size) {
                    val gp = GeoPoint(seg[j].lat, seg[j].lon)
                    tl.casing.addPoint(gp)
                    tl.line.addPoint(gp)
                }
            } else if (seg.size != tl.drawn) {
                val pts = seg.map { GeoPoint(it.lat, it.lon) }
                tl.casing.setPoints(pts)
                tl.line.setPoints(pts)
            }
            tl.drawn = seg.size
        }
        if (changed) rebuildOverlays()
    }

    fun setEndpoints(show: Boolean, track: List<List<LatLon>>, route: List<LatLon>?) {
        if (!show) {
            endpoints.start = null; endpoints.end = null
            return
        }
        val first = track.firstOrNull { it.isNotEmpty() }?.first() ?: route?.firstOrNull()
        val last = track.lastOrNull { it.isNotEmpty() }?.last() ?: route?.lastOrNull()
        endpoints.start = first?.let { GeoPoint(it.lat, it.lon) }
        endpoints.end = last?.let { GeoPoint(it.lat, it.lon) }
    }

    fun setLocation(loc: LiveLocation?, follow: Boolean) {
        locationDot.location = loc?.let { GeoPoint(it.lat, it.lon) }
        locationDot.accuracy = loc?.accuracy ?: 0f
        locationDot.bearing = loc?.bearing
        if (loc != null && follow && (loc != lastFollowed || !lastFollow)) {
            val gp = GeoPoint(loc.lat, loc.lon)
            if (!centeredOnce) {
                map.controller.setZoom(16.5)
                map.controller.setCenter(gp)
                centeredOnce = true
            } else {
                map.controller.animateTo(gp, null, 500L)
            }
            lastFollowed = loc
        }
        lastFollow = follow
    }

    fun fit(points: List<LatLon>?) {
        if (points.isNullOrEmpty() || points === fitRef) return
        fitRef = points
        centeredOnce = true
        val geo = points.map { GeoPoint(it.lat, it.lon) }
        val doFit = {
            val box = BoundingBox.fromGeoPointsSafe(geo)
            if (box.latitudeSpan < 1e-4 && box.longitudeSpanWithDateLine < 1e-4) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(box.centerWithDateLine)
            } else {
                map.zoomToBoundingBox(box, false, (40 * density).toInt())
            }
        }
        if (map.width > 0 && map.height > 0) doFit()
        else map.addOnFirstLayoutListener { _, _, _, _, _ -> doFit() }
    }

    fun dispose() {
        labels?.let { if (it !in map.overlays) it.onDetach(map) }
        map.onDetach()
    }

    private fun rebuildOverlays() {
        map.overlays.clear()
        if (style == MapStyle.SATELLITE) labels?.let { map.overlays.add(it) }
        if (!routeRef.isNullOrEmpty()) {
            map.overlays.add(routeCasing)
            map.overlays.add(routeLine)
        }
        trackLines.forEach { map.overlays.add(it.casing) }
        trackLines.forEach { map.overlays.add(it.line) }
        map.overlays.add(endpoints)
        map.overlays.add(locationDot)
        map.invalidate()
    }
}
