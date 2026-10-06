package app.pedal.ui.map

import app.pedal.data.MapStyle
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

object TileSources {

    private val cyclosm = XYTileSource(
        "CyclOSM", 0, 20, 256, ".png",
        arrayOf(
            "https://a.tile-cyclosm.openstreetmap.fr/cyclosm/",
            "https://b.tile-cyclosm.openstreetmap.fr/cyclosm/",
            "https://c.tile-cyclosm.openstreetmap.fr/cyclosm/",
        ),
        "© OpenStreetMap contributors, CyclOSM",
    )

    private val topo = XYTileSource(
        "OpenTopoMap", 0, 17, 256, ".png",
        arrayOf(
            "https://a.tile.opentopomap.org/",
            "https://b.tile.opentopomap.org/",
            "https://c.tile.opentopomap.org/",
        ),
        "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)",
    )

    /** ArcGIS services use z/y/x ordering. */
    private class ArcGisSource(name: String, maxZoom: Int, ext: String, url: String, copyright: String) :
        OnlineTileSourceBase(name, 0, maxZoom, 256, ext, arrayOf(url), copyright) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
    }

    private val satellite: ITileSource = ArcGisSource(
        "EsriWorldImagery", 19, ".jpg",
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/",
        "Imagery © Esri, Maxar, Earthstar Geographics",
    )

    /** Transparent place-name/boundary labels drawn on top of the satellite imagery. */
    val satelliteLabels: ITileSource = ArcGisSource(
        "EsriReferenceLabels", 19, ".png",
        "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/",
        "© Esri",
    )

    fun forStyle(style: MapStyle): ITileSource = when (style) {
        MapStyle.STANDARD -> TileSourceFactory.MAPNIK
        MapStyle.CYCLE -> cyclosm
        MapStyle.TOPO -> topo
        MapStyle.SATELLITE -> satellite
    }
}
