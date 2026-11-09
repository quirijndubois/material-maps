package app.materialmaps

import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.android.maps.Style
import java.util.WeakHashMap

/**
 * Things that can be shown over the map, from the layers button: raster [tiles] from elsewhere,
 * or the [rail] lines (OpenMapTiles subclasses) from the map's own data, drawn in [color].
 * The map's data only has metro and tram lines from street level ([STREET_ZOOM]); further out,
 * the [cityKinds] lines come from VersaTiles, whose tiles have them from city level.
 */
enum class Overlay(
    val label: String,
    val tiles: String? = null,
    val maxZoom: Float = 18f,
    val rail: List<String>? = null,
    val color: Int = 0,
    val darkColor: Int = 0,
    val cityKinds: List<String>? = null,
) {
    /** Shaded relief, so hills and mountains stand out. Drawn from elevation data, not tiles. */
    Terrain("Terrain"),
    Trains("Trains", rail = listOf("rail", "narrow_gauge", "preserved", "funicular"), color = 0xFFC62828.toInt(), darkColor = 0xFFEF5350.toInt()),
    Metro("Metro", rail = listOf("subway", "light_rail", "monorail"), color = 0xFF1565C0.toInt(), darkColor = 0xFF64B5F6.toInt(), cityKinds = listOf("subway", "light_rail", "monorail")),
    Trams("Trams", rail = listOf("tram"), color = 0xFFE65100.toInt(), darkColor = 0xFFFFB74D.toInt(), cityKinds = listOf("tram")),
    Hiking("Hiking", "https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png"),
    Cycling("Cycling", "https://tile.waymarkedtrails.org/cycling/{z}/{x}/{y}.png"),
    Slopes("Ski slopes", "https://tiles.opensnowmap.org/pistes/{z}/{x}/{y}.png"),
    SeaMarks("Sea marks", "https://tiles.openseamap.org/seamark/{z}/{x}/{y}.png"),
}

private const val PREFIX = "layers-"
private const val SATELLITE = "${PREFIX}satellite"
private const val TERRAIN = "${PREFIX}terrain"
private const val SATELLITE_TILES = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
private const val TERRAIN_TILES = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"
private const val CITY_RAIL = "${PREFIX}city-rail"
/** OpenStreetMap in the Shortbread schema; free to use, and fast. */
private const val CITY_RAIL_TILES = "https://tiles.versatiles.org/tiles/osm/{z}/{x}/{y}"

/** From this zoom the map's own data has metro and tram lines. */
private const val STREET_ZOOM = 14f
/** Metro and tram lines from VersaTiles show from this zoom, up to [STREET_ZOOM]. */
private const val CITY_ZOOM = 10f

/** How the style itself had each of its layers, so turning things off can be undone. */
private val styleVisibility = WeakHashMap<Style, Map<String, String>>()

/**
 * Brings the map's layers in line with [settings]: satellite photos, labels, 3D buildings and
 * the [Overlay]s. Overlays go just below [below], so routes and pins stay on top. Safe to call
 * again whenever the settings change.
 */
fun Style.applyLayers(settings: Settings, dark: Boolean, below: String) {
    val original = styleVisibility.getOrPut(this) {
        layers.filterNot { it.id.startsWith(PREFIX) }.associate { it.id to it.visibility.value }
    }
    fun show(layer: Layer, visible: Boolean) {
        val was = original[layer.id] ?: return
        val want = if (visible) was else Property.NONE
        if (layer.visibility.value != want) layer.setProperties(PropertyFactory.visibility(want))
    }
    for (layer in layers) when (layer) {
        is FillExtrusionLayer -> show(layer, settings.buildings3d)
        // On satellite, the photo takes the place of the land, water and parks.
        is FillLayer, is RasterLayer, is HillshadeLayer -> show(layer, !settings.satellite)
        is SymbolLayer -> show(layer, settings.labels)
        else -> Unit
    }

    if (settings.satellite) {
        if (getLayer(SATELLITE) == null) {
            if (getSource(SATELLITE) == null) {
                addSource(RasterSource(SATELLITE, TileSet("2.2.0", SATELLITE_TILES).apply { maxZoom = 19f }, 256))
            }
            val layer = RasterLayer(SATELLITE, SATELLITE)
            val background = layers.firstOrNull { it is BackgroundLayer }
            if (background != null) addLayerAbove(layer, background.id) else addLayerAt(layer, 0)
        }
        getLayer(SATELLITE)?.setProperties(PropertyFactory.rasterBrightnessMax(if (dark) 0.8f else 1f))
    } else {
        remove(SATELLITE)
    }

    if (Overlay.Terrain in settings.overlays) {
        if (getLayer(TERRAIN) == null) {
            if (getSource(TERRAIN) == null) {
                val tiles = TileSet("2.2.0", TERRAIN_TILES).apply {
                    encoding = "terrarium"
                    maxZoom = 15f
                }
                addSource(RasterDemSource(TERRAIN, tiles, 256))
            }
            // Over the land, but under roads, buildings and labels.
            val layer = HillshadeLayer(TERRAIN, TERRAIN)
            val above = layers.firstOrNull {
                it is SymbolLayer || it.sourceLayer() in setOf("transportation", "building", "boundary")
            }?.id ?: below
            if (getLayer(above) != null) addLayerBelow(layer, above) else addLayer(layer)
        }
        getLayer(TERRAIN)?.setProperties(
            PropertyFactory.hillshadeExaggeration(if (settings.satellite) 0.25f else 0.4f),
            PropertyFactory.hillshadeShadowColor(if (dark) "rgba(0,0,0,0.7)" else "rgba(60,50,40,0.55)"),
            PropertyFactory.hillshadeHighlightColor(if (dark) "rgba(255,255,255,0.08)" else "rgba(255,255,255,0.35)"),
            PropertyFactory.hillshadeAccentColor(if (dark) "rgba(0,0,0,0.3)" else "rgba(60,50,40,0.25)"),
        )
    } else {
        remove(TERRAIN)
    }

    // Rail lines from the map's own data. Most metro runs underground, so tunnels are drawn
    // too, only fainter; sidings and yards are left out.
    fun railLayer(id: String, source: String) = LineLayer(id, source).withProperties(
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineWidth(Expression.interpolate(
            Expression.exponential(1.4f), Expression.zoom(),
            Expression.stop(9, 1.2f), Expression.stop(13, 2.5f), Expression.stop(17, 5f), Expression.stop(20, 9f),
        )),
    )
    fun place(layer: Layer) = if (getLayer(below) != null) addLayerBelow(layer, below) else addLayer(layer)

    val wantsCityRail = settings.overlays.any { it.cityKinds != null }
    if (wantsCityRail && getSource(CITY_RAIL) == null) {
        addSource(VectorSource(CITY_RAIL, TileSet("3.0.0", CITY_RAIL_TILES).apply { maxZoom = STREET_ZOOM }))
    }
    val railSource = layers.firstNotNullOfOrNull { (it as? LineLayer)?.takeIf { l -> l.sourceLayer == "transportation" }?.sourceId }
    for (overlay in Overlay.entries) {
        val rail = overlay.rail ?: continue
        val id = PREFIX + overlay.name.lowercase()
        val cityId = "$id-city"
        val on = overlay in settings.overlays
        if (on && railSource != null && getLayer(id) == null) {
            place(railLayer(id, railSource).apply {
                sourceLayer = "transportation"
                if (overlay.cityKinds != null) minZoom = STREET_ZOOM
                setFilter(Expression.all(
                    // Trains are "rail"; metro, light rail and trams are "transit".
                    Expression.any(Expression.eq(Expression.get("class"), "rail"), Expression.eq(Expression.get("class"), "transit")),
                    Expression.any(*rail.map { Expression.eq(Expression.get("subclass"), it) }.toTypedArray()),
                    Expression.not(Expression.has("service")),
                ))
                setProperties(PropertyFactory.lineOpacity(Expression.match(
                    Expression.get("brunnel"), Expression.literal(0.95f), Expression.stop("tunnel", 0.5f),
                )))
            })
        }
        val cityKinds = overlay.cityKinds
        if (on && cityKinds != null && getLayer(cityId) == null) {
            place(railLayer(cityId, CITY_RAIL).apply {
                sourceLayer = "streets"
                minZoom = CITY_ZOOM
                maxZoom = STREET_ZOOM
                setFilter(Expression.all(
                    Expression.any(*cityKinds.map { Expression.eq(Expression.get("kind"), it) }.toTypedArray()),
                    Expression.not(Expression.has("service")),
                ))
                setProperties(PropertyFactory.lineOpacity(Expression.switchCase(
                    Expression.toBool(Expression.get("tunnel")), Expression.literal(0.5f), Expression.literal(0.95f),
                )))
            })
        }
        for (layerId in listOf(id, cityId)) {
            val layer = getLayer(layerId) ?: continue
            if (on) layer.setProperties(PropertyFactory.lineColor(if (dark) overlay.darkColor else overlay.color))
            else removeLayer(layerId)
        }
    }
    if (!wantsCityRail && getSource(CITY_RAIL) != null) removeSource(CITY_RAIL)

    for (overlay in Overlay.entries) {
        val tiles = overlay.tiles ?: continue
        val id = PREFIX + overlay.name.lowercase()
        if (overlay in settings.overlays) {
            if (getLayer(id) == null) {
                if (getSource(id) == null) {
                    addSource(RasterSource(id, TileSet("2.2.0", tiles).apply { maxZoom = overlay.maxZoom }, 256))
                }
                val layer = RasterLayer(id, id)
                if (getLayer(below) != null) addLayerBelow(layer, below) else addLayer(layer)
            }
            getLayer(id)?.setProperties(PropertyFactory.rasterBrightnessMax(if (dark) 0.85f else 1f))
        } else {
            remove(id)
        }
    }
}

private fun Style.remove(id: String) {
    if (getLayer(id) != null) removeLayer(id)
    if (getSource(id) != null) removeSource(id)
}

private fun Layer.sourceLayer(): String? = when (this) {
    is LineLayer -> sourceLayer
    is FillLayer -> sourceLayer
    is FillExtrusionLayer -> sourceLayer
    is SymbolLayer -> sourceLayer
    else -> null
}
