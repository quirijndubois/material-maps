package app.materialmaps

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Pin overlays: places of a kind, or Wikipedia articles. */
val Overlay.isPins get() = pins != null || this == Overlay.WikiArticles

/**
 * The map's own points of interest have these kinds (as OpenMapTiles subclasses), so their
 * pins come straight from the map, with nothing more to load. The others are looked up.
 */
val Overlay.inMapTiles get() = this in setOf(Overlay.Charging, Overlay.Water, Overlay.Toilets, Overlay.BikeParking)

/** A place shown by a pin overlay. */
class AreaPin(val overlay: Overlay, val place: Place)

/**
 * The overlays that are looked up for the part of the map in view rather than drawn from
 * tiles: places as pins, and bus routes. Each is fetched for a margin around the view, and
 * fetched again only once the view moves out of that.
 */
object AreaOverlays {
    /** Closer in than this there's too much to look up. */
    const val PINS_ZOOM = 13.0
    const val WIKI_ZOOM = 12.0
    const val BUS_ZOOM = 13.0

    const val PINS_LAYER = "area-pins"
    const val TILE_PINS_LAYER = "area-tile-pins"
    private const val PINS_SOURCE = "area-pins"
    private const val BUS_SOURCE = "area-buses"
    private const val BUS_LAYER = "area-buses"
    private const val BUS_LABELS = "area-bus-labels"

    /**
     * Remembers what was fetched for which area and [key], to fetch again only when needed:
     * when the view leaves the area, or (with [zoom]) when it's zoomed in or out a level or
     * more, for things whose detail goes with the zoom. The area is the view with [margin] of
     * it added on each side, so a little panning needs nothing new.
     */
    class AreaCache<T>(private val margin: Double = 0.5) {
        private var area: LatLngBounds? = null
        private var key: Any? = null
        private var value: T? = null
        private var zoom: Double? = null

        suspend fun get(view: LatLngBounds, key: Any, zoom: Double? = null, fetch: suspend (LatLngBounds) -> T): T {
            val known = value
            val sameZoom = zoom == null || this.zoom?.let { kotlin.math.abs(it - zoom) < 1 } == true
            if (known != null && this.key == key && sameZoom && area?.contains(view) == true) return known
            val span = view.span
            val padded = LatLngBounds.from(
                (view.latitudeNorth + span.latitudeSpan * margin).coerceAtMost(85.0),
                view.longitudeEast + span.longitudeSpan * margin,
                (view.latitudeSouth - span.latitudeSpan * margin).coerceAtLeast(-85.0),
                view.longitudeWest - span.longitudeSpan * margin,
            )
            return fetch(padded).also {
                area = padded
                this.key = key
                this.zoom = zoom
                value = it
            }
        }
    }

    /** Places of the [overlays] in [area]: one Overpass query for all kinds, plus Wikipedia. */
    suspend fun pins(overlays: Set<Overlay>, area: LatLngBounds, zoom: Double): List<AreaPin> {
        val kinds = overlays.filter { it.pins != null && !it.inMapTiles && zoom >= PINS_ZOOM }
        val places = if (kinds.isEmpty()) emptyList() else {
            Loading.track(kinds.joinToString(", ") { it.label }) { Overpass.findAny(kinds.map { it.pins!! }, area) }.mapNotNull { place ->
                kinds.firstOrNull { it.pins!!.key == place.key && it.pins.value == place.type }?.let { AreaPin(it, place) }
            }
        }
        val articles = if (Overlay.WikiArticles in overlays && zoom >= WIKI_ZOOM) {
            Loading.track(Overlay.WikiArticles.label) { Wikipedia.inArea(area) }.map { AreaPin(Overlay.WikiArticles, it) }
        } else emptyList()
        return places + articles
    }

    /** Shows [pins] as round icons, from [icons], just below [below]. */
    fun Style.showPins(pins: List<AreaPin>, icons: Map<Overlay, Bitmap>, below: String) {
        for ((overlay, bitmap) in icons) {
            val name = "pin-${overlay.name}"
            if (getImage(name) == null) addImage(name, bitmap)
        }
        if (getSource(PINS_SOURCE) == null) addSource(GeoJsonSource(PINS_SOURCE))
        if (getLayer(PINS_LAYER) == null) {
            val layer = SymbolLayer(PINS_LAYER, PINS_SOURCE).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                // Where they'd crowd each other out, some are left out until zoomed in further.
                PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.iconPadding(1f),
                PropertyFactory.iconIgnorePlacement(false),
            )
            if (getLayer(below) != null) addLayerBelow(layer, below) else addLayer(layer)
        }
        getSourceAs<GeoJsonSource>(PINS_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(pins.mapIndexed { i, pin ->
                Feature.fromGeometry(Point.fromLngLat(pin.place.lon, pin.place.lat)).apply {
                    addNumberProperty("index", i)
                    addStringProperty("icon", "pin-${pin.overlay.name}")
                }
            })
        )
    }

    /**
     * Pins for the [overlays] the map's own data has, drawn from its tiles: they show from
     * street level, where the tiles have every place.
     */
    fun Style.showTilePins(overlays: Set<Overlay>, icons: Map<Overlay, Bitmap>, below: String) {
        val kinds = overlays.filter { it.inMapTiles }
        if (kinds.isEmpty()) {
            if (getLayer(TILE_PINS_LAYER) != null) removeLayer(TILE_PINS_LAYER)
            return
        }
        for (overlay in kinds) {
            val name = "pin-${overlay.name}"
            if (getImage(name) == null) icons[overlay]?.let { addImage(name, it) }
        }
        val source = layers.firstNotNullOfOrNull { (it as? SymbolLayer)?.takeIf { l -> l.sourceLayer == "poi" }?.sourceId } ?: return
        var layer = getLayerAs<SymbolLayer>(TILE_PINS_LAYER)
        if (layer == null) {
            layer = SymbolLayer(TILE_PINS_LAYER, source).apply {
                sourceLayer = "poi"
                minZoom = 14f
                setProperties(
                    PropertyFactory.iconAllowOverlap(false),
                    PropertyFactory.iconPadding(1f),
                )
            }
            if (getLayer(below) != null) addLayerBelow(layer, below) else addLayer(layer)
        }
        val subclasses = kinds.map { it.pins!!.value }
        layer.setFilter(Expression.any(*subclasses.map { Expression.eq(Expression.get("subclass"), it) }.toTypedArray()))
        layer.setProperties(
            PropertyFactory.iconImage(Expression.match(
                Expression.get("subclass"), Expression.literal(""),
                *kinds.map { Expression.stop(it.pins!!.value, "pin-${it.name}") }.toTypedArray(),
            )),
        )
    }

    fun Style.hidePins() {
        if (getLayer(PINS_LAYER) != null) removeLayer(PINS_LAYER)
        if (getSource(PINS_SOURCE) != null) removeSource(PINS_SOURCE)
    }

    /**
     * Bus routes in [area], from what Transitous has running there in the next half hour:
     * every stretch between two stops, with the numbers of all lines along it.
     */
    suspend fun buses(area: LatLngBounds, zoom: Double): FeatureCollection {
        val now = java.time.Instant.now()
        val answer = Loading.track(Overlay.Buses.label) {
            httpGet(
                "https://api.transitous.org/api/v1/map/trips?min=${area.latitudeSouth},${area.longitudeWest}" +
                    "&max=${area.latitudeNorth},${area.longitudeEast}&startTime=$now&endTime=${now.plusSeconds(1800)}" +
                    "&zoom=${zoom.toInt()}",
                readTimeout = 30_000,
            )
        }
        val segments = org.json.JSONArray(answer)
        // Stretches between the same two stops share one line, with all their numbers.
        val stretches = LinkedHashMap<Pair<String, String>, Pair<String, MutableSet<String>>>()
        for (i in 0 until segments.length()) {
            val segment = segments.getJSONObject(i)
            if (segment.optString("mode") !in setOf("BUS", "TROLLEYBUS", "COACH")) continue
            val polyline = segment.optString("polyline").takeIf { it.isNotEmpty() } ?: continue
            val from = segment.optJSONObject("from")?.optString("stopId").orEmpty()
            val to = segment.optJSONObject("to")?.optString("stopId").orEmpty()
            val refs = stretches.getOrPut(from to to) { polyline to sortedSetOf(compareBy({ it.length }, { it })) }.second
            val trips = segment.optJSONArray("trips") ?: continue
            for (t in 0 until trips.length()) trips.getJSONObject(t).optString("routeShortName").takeIf { it.isNotBlank() }?.let { refs += it }
        }
        return FeatureCollection.fromFeatures(stretches.values.mapNotNull { (polyline, refs) ->
            val points = decodePolyline(polyline, 5).map { Point.fromLngLat(it.longitude, it.latitude) }
            if (points.size < 2) return@mapNotNull null
            Feature.fromGeometry(LineString.fromLngLats(points)).apply { addStringProperty("refs", refs.joinToString("  ·  ")) }
        })
    }

    /** Shows the bus routes, in the [Overlay.Buses] color, just below [below]. */
    fun Style.showBuses(lines: FeatureCollection, dark: Boolean, below: String) {
        if (getSource(BUS_SOURCE) == null) addSource(GeoJsonSource(BUS_SOURCE))
        if (getLayer(BUS_LAYER) == null) {
            val font = layers.filterIsInstance<SymbolLayer>()
                .firstNotNullOfOrNull { (it.textFont.value as? Array<*>)?.filterIsInstance<String>()?.takeIf { f -> f.isNotEmpty() } }
                ?.toTypedArray() ?: arrayOf("Noto Sans Regular")
            val line = LineLayer(BUS_LAYER, BUS_SOURCE).withProperties(
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineWidth(Expression.interpolate(
                    Expression.exponential(1.4f), Expression.zoom(),
                    Expression.stop(13, 2f), Expression.stop(17, 4.5f), Expression.stop(20, 8f),
                )),
            )
            val labels = SymbolLayer(BUS_LABELS, BUS_SOURCE).withProperties(
                PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                PropertyFactory.textField(Expression.get("refs")),
                PropertyFactory.textFont(font),
                PropertyFactory.textSize(11f),
                PropertyFactory.textHaloWidth(1.6f),
                PropertyFactory.symbolSpacing(350f),
            )
            for (layer in listOf(line, labels)) if (getLayer(below) != null) addLayerBelow(layer, below) else addLayer(layer)
        }
        val color = if (dark) Overlay.Buses.darkColor else Overlay.Buses.color
        getLayer(BUS_LAYER)?.setProperties(PropertyFactory.lineColor(color))
        getLayer(BUS_LABELS)?.setProperties(
            PropertyFactory.textColor(color),
            PropertyFactory.textHaloColor(if (dark) "rgba(0,0,0,0.8)" else "rgba(255,255,255,0.9)"),
        )
        getSourceAs<GeoJsonSource>(BUS_SOURCE)?.setGeoJson(lines)
    }

    fun Style.hideBuses() {
        for (id in listOf(BUS_LABELS, BUS_LAYER)) if (getLayer(id) != null) removeLayer(id)
        if (getSource(BUS_SOURCE) != null) removeSource(BUS_SOURCE)
    }
}

/** The pin overlays' icons: white on a dot in their own color. */
@Composable
fun rememberPinIcons(): Map<Overlay, Bitmap> {
    val density = LocalDensity.current
    val painters = Overlay.entries.filter { it.isPins }.associateWith { rememberVectorPainter(it.icon) }
    return remember(density) {
        val px = with(density) { 26.dp.toPx() }
        val border = with(density) { 2.dp.toPx() }
        painters.mapValues { (overlay, painter) ->
            val image = ImageBitmap(px.toInt(), px.toInt())
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(px, px)) {
                drawCircle(Color.White, radius = px / 2, center = Offset(px / 2, px / 2))
                drawCircle(Color(overlay.color), radius = px / 2 - border, center = Offset(px / 2, px / 2))
                translate(px * 0.22f, px * 0.22f) {
                    with(painter) { draw(Size(px * 0.56f, px * 0.56f), colorFilter = ColorFilter.tint(Color.White)) }
                }
            }
            image.asAndroidBitmap()
        }
    }
}

/** Rain radar pictures from RainViewer, which is free for apps like this one. */
object RainRadar {
    /** Refresh this often: a new picture comes every ten minutes. */
    const val REFRESH_MS = 5 * 60_000L

    /** A picture taken at [time] (epoch seconds), and its tile URL. */
    data class Frame(val time: Long, val tiles: String)

    /**
     * The pictures of the last two hours, oldest first: universal blue colors, smoothed,
     * with snow shown. RainViewer no longer has a forecast for free.
     */
    suspend fun frames(): List<Frame> {
        val json = JSONObject(httpGet("https://api.rainviewer.com/public/weather-maps.json"))
        val host = json.optString("host").takeIf { it.isNotBlank() } ?: return emptyList()
        val past = json.optJSONObject("radar")?.optJSONArray("past") ?: return emptyList()
        return (0 until past.length()).mapNotNull { i ->
            val frame = past.getJSONObject(i)
            val path = frame.optString("path").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Frame(frame.optLong("time"), "$host$path/256/{z}/{x}/{y}/2/1_1.png")
        }.sortedBy { it.time }
    }
}
