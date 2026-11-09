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
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.maps.Style
import java.util.WeakHashMap

/** Where an [Overlay] is listed in the layers sheet. */
enum class OverlayGroup(val label: String) {
    Details("Map details"),
    Transport("Transport"),
    Outdoors("Outdoors"),
    Weather("Weather"),
    Air("Air"),
    Places("Places"),
    Netherlands("Netherlands"),
}

/**
 * Things that can be shown over the map, from the layers button: raster [tiles] from elsewhere,
 * or the [rail] lines (OpenMapTiles subclasses) from the map's own data, drawn in [color].
 * The map's data only has metro and tram lines from street level ([STREET_ZOOM]); further out,
 * the [cityKinds] lines come from VersaTiles, whose tiles have them from city level.
 * [pins] are places of that kind as dots, looked up for what's in view (see [AreaOverlays]).
 * Tiles only cover [bounds] (west, south, east, north), and only from [minZoom].
 */
enum class Overlay(
    val label: String,
    val group: OverlayGroup,
    val tiles: String? = null,
    val maxZoom: Float = 18f,
    val minZoom: Float = 0f,
    val bounds: FloatArray? = null,
    val opacity: Float = 1f,
    /** Dark lines on a see-through tile, which vanish on the dark map unless turned light. */
    val invertInDark: Boolean = false,
    val rail: List<String>? = null,
    val color: Int = 0,
    val darkColor: Int = 0,
    val cityKinds: List<String>? = null,
    val pins: PlaceKind? = null,
) {
    /** Shaded relief, so hills and mountains stand out. Drawn from elevation data, not tiles. */
    Terrain("Terrain", OverlayGroup.Details),
    /** Height lines, worked out on the phone from the same elevation data (see [ContourTiles]). */
    Contours("Contour lines", OverlayGroup.Details),
    Trains("Trains", OverlayGroup.Transport, rail = listOf("rail", "narrow_gauge", "preserved", "funicular"), color = 0xFFC62828.toInt(), darkColor = 0xFFEF5350.toInt()),
    Metro("Metro", OverlayGroup.Transport, rail = listOf("subway", "light_rail", "monorail"), color = 0xFF1565C0.toInt(), darkColor = 0xFF64B5F6.toInt(), cityKinds = listOf("subway", "light_rail", "monorail")),
    Trams("Trams", OverlayGroup.Transport, rail = listOf("tram"), color = 0xFFE65100.toInt(), darkColor = 0xFFFFB74D.toInt(), cityKinds = listOf("tram")),
    /** Bus routes from OpenStreetMap, for what's in view (see [AreaOverlays]). */
    Buses("Buses", OverlayGroup.Transport, color = 0xFF6A1B9A.toInt(), darkColor = 0xFFCE93D8.toInt()),
    /** Tracks, stations, switches and the like, by OpenRailwayMap. */
    Railways("Rail network", OverlayGroup.Transport, "https://tiles.openrailwaymap.org/standard/{z}/{x}/{y}.png", maxZoom = 19f),
    Hiking("Hiking", OverlayGroup.Outdoors, "https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png"),
    Cycling("Cycling", OverlayGroup.Outdoors, "https://tile.waymarkedtrails.org/cycling/{z}/{x}/{y}.png"),
    Mtb("Mountain biking", OverlayGroup.Outdoors, "https://tile.waymarkedtrails.org/mtb/{z}/{x}/{y}.png"),
    Slopes("Ski slopes", OverlayGroup.Outdoors, "https://tiles.opensnowmap.org/pistes/{z}/{x}/{y}.png"),
    SeaMarks("Sea marks", OverlayGroup.Outdoors, "https://tiles.openseamap.org/seamark/{z}/{x}/{y}.png"),
    /** Rain radar of the last two hours (see [RainRadar]). */
    Rain("Rain radar", OverlayGroup.Weather),
    /** Satellite pictures of the last hours (see [Satellite]). */
    Clouds("Clouds", OverlayGroup.Weather),
    LiveSatellite("Live satellite", OverlayGroup.Weather),
    Lightning("Lightning", OverlayGroup.Weather),
    /** Forecasts, from half a day back to three days ahead (see [OpenMeteo] and [field], or [Cams]). */
    Wind("Wind", OverlayGroup.Weather),
    CloudForecast("Cloud forecast", OverlayGroup.Weather),
    Storms("Storm risk", OverlayGroup.Weather),
    Pm25("PM2.5", OverlayGroup.Air),
    Pm10("PM10", OverlayGroup.Air),
    No2("NO₂", OverlayGroup.Air),
    Ozone("Ozone", OverlayGroup.Air),
    Dust("Dust", OverlayGroup.Air),
    Uv("UV", OverlayGroup.Air),
    Co2("CO₂", OverlayGroup.Air),
    Methane("Methane", OverlayGroup.Air),
    /** Measured now: Dutch stations (see [Luchtmeetnet]) and people's own sensors (see [SensorCommunity]). */
    AirStations("Stations", OverlayGroup.Air),
    AirSensors("Sensors", OverlayGroup.Air),
    Charging("Charging", OverlayGroup.Places, color = 0xFF2E7D32.toInt(), pins = PlaceKind("Charging station", "amenity", "charging_station")),
    Water("Drinking water", OverlayGroup.Places, color = 0xFF0277BD.toInt(), pins = PlaceKind("Drinking water", "amenity", "drinking_water")),
    Toilets("Toilets", OverlayGroup.Places, color = 0xFF5D4037.toInt(), pins = PlaceKind("Toilets", "amenity", "toilets")),
    Defibrillators("AEDs", OverlayGroup.Places, color = 0xFFC62828.toInt(), pins = PlaceKind("Defibrillator (AED)", "emergency", "defibrillator")),
    BikeParking("Bike parking", OverlayGroup.Places, color = 0xFF283593.toInt(), pins = PlaceKind("Bike parking", "amenity", "bicycle_parking")),
    BikeRepair("Bike repair", OverlayGroup.Places, color = 0xFFEF6C00.toInt(), pins = PlaceKind("Bike repair station", "amenity", "bicycle_repair_station")),
    /** Articles about places in view, from Wikipedia's own coordinates. */
    WikiArticles("Wikipedia", OverlayGroup.Places, color = 0xFF455A64.toInt()),
    /**
     * Height of the ground (AHN, without buildings and trees) at half a meter a pixel. PDOK
     * only serves it as WMS, which MapLibre asks for by each tile's box.
     */
    Elevation(
        "Elevation", OverlayGroup.Netherlands,
        "https://$AERIAL_HOST/rws/ahn/wms/v1_0?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=dtm_05m&STYLES=" +
            "&CRS=EPSG:3857&BBOX={bbox-epsg-3857}&WIDTH=512&HEIGHT=512&FORMAT=image/png&TRANSPARENT=true",
        maxZoom = 20f, minZoom = 8f, bounds = NETHERLANDS, opacity = 0.75f,
    ),
    /** Property boundaries and parcel numbers from the Kadaster. */
    Parcels(
        "Parcels", OverlayGroup.Netherlands,
        "https://$AERIAL_HOST/kadaster/brk-kadastrale-kaart/wmts/v5_0/Kadastralekaart/EPSG:3857/{z}/{x}/{y}.png",
        maxZoom = 21f, minZoom = 15f, bounds = NETHERLANDS, invertInDark = true,
    ),
}

/** The Netherlands with the Wadden Islands, so no tiles are asked for elsewhere. */
private val NETHERLANDS = floatArrayOf(3.2f, 50.7f, 7.3f, 53.6f)

/**
 * Aerial photo years PDOK has for the whole country, newest first: high resolution (8 cm)
 * from 2021, 25 cm before.
 */
val AERIAL_YEARS = (2026 downTo 2016).toList()

private fun aerialLayer(year: Int?) = when {
    year == null -> "Actueel_orthoHR"
    year >= 2021 -> "${year}_orthoHR"
    else -> "${year}_ortho25"
}

private const val PREFIX = "layers-"
private const val SATELLITE = "${PREFIX}satellite"
private const val AERIAL = "${PREFIX}aerial"
private const val TERRAIN = "${PREFIX}terrain"
/** Sentinel-2 cloudless 2016 by EOX: the whole world at 10 m a pixel, CC BY 4.0. */
private const val SATELLITE_TILES = "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless_3857/default/g/{z}/{y}/{x}.jpg"
/**
 * Aerial photos of the Netherlands from Beeldmateriaal Nederland via PDOK, at 8 cm a pixel,
 * CC BY 4.0, of [Settings.aerialYear]. Drawn over [SATELLITE] from [AERIAL_ZOOM]; [AerialTiles] makes the white beyond
 * the border see-through.
 */
private fun aerialTiles(layer: String) = "https://$AERIAL_HOST/hwh/luchtfotorgb/wmts/v1_0/$layer/EPSG:3857/{z}/{x}/{y}.jpeg"
private const val AERIAL_ZOOM = 12f

/**
 * [styleJson] with no labels, road numbers or icons, for a preview of what the map looks like:
 * its colors and shapes.
 */
fun withoutLabels(styleJson: String): String {
    val style = org.json.JSONObject(styleJson)
    val layers = style.getJSONArray("layers")
    val kept = org.json.JSONArray()
    for (i in 0 until layers.length()) {
        val layer = layers.getJSONObject(i)
        if (layer.optString("type") != "symbol") kept.put(layer)
    }
    return style.put("layers", kept).toString()
}

/**
 * Only the photos, as a style of its own: for a preview of the map on satellite. Over the
 * Netherlands that's the aerial photos, like on the map.
 */
val SATELLITE_PREVIEW_STYLE =
    """{"version":8,"sources":{"s":{"type":"raster","tiles":["$SATELLITE_TILES"],"tileSize":256,"maxzoom":14},""" +
        """"a":{"type":"raster","tiles":["${aerialTiles(aerialLayer(null))}"],"tileSize":256,"minzoom":12,"maxzoom":20,""" +
        """"bounds":[${NETHERLANDS.joinToString(",")}]}},""" +
        """"layers":[{"id":"s","type":"raster","source":"s"},{"id":"a","type":"raster","source":"a"}]}"""
private const val TERRAIN_TILES = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"
private const val CITY_RAIL = "${PREFIX}city-rail"
/** OpenStreetMap in the Shortbread schema; free to use, and fast. */
private const val CITY_RAIL_TILES = "https://tiles.versatiles.org/tiles/osm/{z}/{x}/{y}"

/** From this zoom the map's own data has metro and tram lines. */
private const val STREET_ZOOM = 14f
/** Metro and tram lines from VersaTiles show from this zoom, up to [STREET_ZOOM]. */
private const val CITY_ZOOM = 10f

/** Which year's aerial photos each style has, as their PDOK layer. */
private val aerialShown = WeakHashMap<Style, String>()

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
        // On satellite, the photos take the place of the land, water and parks.
        is FillLayer, is RasterLayer, is HillshadeLayer -> show(layer, !settings.satellite)
        is SymbolLayer -> show(layer, settings.labels)
        else -> Unit
    }

    if (settings.satellite) {
        if (getLayer(SATELLITE) == null) {
            if (getSource(SATELLITE) == null) {
                addSource(RasterSource(SATELLITE, TileSet("2.2.0", SATELLITE_TILES).apply { maxZoom = 14f }, 256))
            }
            val layer = RasterLayer(SATELLITE, SATELLITE)
            val background = layers.firstOrNull { it is BackgroundLayer }
            if (background != null) addLayerAbove(layer, background.id) else addLayerAt(layer, 0)
        }
        // Another year's photos come from another source.
        val year = aerialLayer(settings.aerialYear)
        if (aerialShown[this] != year) remove(AERIAL)
        if (getLayer(AERIAL) == null) {
            aerialShown[this] = year
            if (getSource(AERIAL) == null) {
                val tiles = TileSet("2.2.0", aerialTiles(year)).apply {
                    minZoom = AERIAL_ZOOM
                    maxZoom = 20f
                    setBounds(*NETHERLANDS)
                }
                addSource(RasterSource(AERIAL, tiles, 256))
            }
            addLayerAbove(RasterLayer(AERIAL, AERIAL), SATELLITE)
        }
        for (id in listOf(SATELLITE, AERIAL)) {
            getLayer(id)?.setProperties(PropertyFactory.rasterBrightnessMax(if (dark) 0.8f else 1f))
        }
    } else {
        remove(AERIAL)
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

    if (Overlay.Contours in settings.overlays) addContours(dark, below) else {
        remove(CONTOUR_LABELS)
        remove(CONTOURS)
    }
    removeWeather(settings.overlays)

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
                    val tileSet = TileSet("2.2.0", tiles).apply {
                        minZoom = overlay.minZoom
                        maxZoom = overlay.maxZoom
                        overlay.bounds?.let { setBounds(*it) }
                    }
                    addSource(RasterSource(id, tileSet, 256))
                }
                val layer = RasterLayer(id, id).withProperties(PropertyFactory.rasterOpacity(overlay.opacity))
                if (getLayer(below) != null) addLayerBelow(layer, below) else addLayer(layer)
            }
            getLayer(id)?.setProperties(
                *if (dark && overlay.invertInDark) arrayOf(
                    // Brightness the wrong way round turns dark lines light; turning the hue
                    // half way round gives them back their color.
                    PropertyFactory.rasterBrightnessMin(0.9f),
                    PropertyFactory.rasterBrightnessMax(0f),
                    PropertyFactory.rasterHueRotate(180f),
                ) else arrayOf(
                    PropertyFactory.rasterBrightnessMin(0f),
                    PropertyFactory.rasterBrightnessMax(if (dark) 0.85f else 1f),
                    PropertyFactory.rasterHueRotate(0f),
                )
            )
        } else {
            remove(id)
        }
    }
}

private const val CONTOURS = "${PREFIX}contours"
private const val CONTOUR_LABELS = "${PREFIX}contour-labels"
private const val RAIN = "${PREFIX}rain"

/**
 * Height lines over the land, every 10 to 200 meters as the zoom asks (see [ContourTiles]),
 * with every fifth one bolder and labelled. Like the terrain shading, they go under roads.
 */
private fun Style.addContours(dark: Boolean, below: String) {
    if (getLayer(CONTOURS) == null) {
        if (getSource(CONTOURS) == null) {
            addSource(VectorSource(CONTOURS, TileSet("3.0.0", ContourTiles.TILES).apply {
                minZoom = ContourTiles.MIN_ZOOM.toFloat()
                maxZoom = ContourTiles.MAX_ZOOM.toFloat()
            }))
        }
        val lines = LineLayer(CONTOURS, CONTOURS).apply {
            sourceLayer = ContourTiles.LAYER
            setProperties(
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineWidth(Expression.switchCase(Expression.get("major"), Expression.literal(1.1f), Expression.literal(0.6f))),
            )
        }
        val above = layers.firstOrNull {
            it is SymbolLayer || it.sourceLayer() in setOf("transportation", "building", "boundary")
        }?.id ?: below
        if (getLayer(above) != null) addLayerBelow(lines, above) else addLayer(lines)
        val font = layers.filterIsInstance<SymbolLayer>()
            .firstNotNullOfOrNull { (it.textFont.value as? Array<*>)?.filterIsInstance<String>()?.takeIf { f -> f.isNotEmpty() } }
            ?.toTypedArray() ?: arrayOf("Noto Sans Regular")
        val labels = SymbolLayer(CONTOUR_LABELS, CONTOURS).apply {
            sourceLayer = ContourTiles.LAYER
            setFilter(Expression.get("major"))
            setProperties(
                PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                PropertyFactory.textField(Expression.concat(Expression.toString(Expression.get("ele")), Expression.literal(" m"))),
                PropertyFactory.textFont(font),
                PropertyFactory.textSize(10f),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.symbolSpacing(300f),
            )
        }
        if (getLayer(below) != null) addLayerBelow(labels, below) else addLayer(labels)
    }
    val color = if (dark) "rgba(230,200,170,0.45)" else "rgba(140,90,40,0.5)"
    getLayer(CONTOURS)?.setProperties(PropertyFactory.lineColor(color))
    getLayer(CONTOUR_LABELS)?.setProperties(
        PropertyFactory.textColor(if (dark) "rgba(230,200,170,0.9)" else "rgba(120,75,30,0.9)"),
        PropertyFactory.textHaloColor(if (dark) "rgba(0,0,0,0.6)" else "rgba(255,255,255,0.8)"),
    )
}

/**
 * Pictures over time, [frames] by their time, each its own layer named [prefix] and the time;
 * only the one at [time] shows. With [preload], all of them load at once so stepping through
 * them is instant; otherwise each loads once it's first shown, and is kept. Frames no longer
 * among [frames] go. The pictures only go up to [maxZoom]; closer in, they're enlarged.
 */
fun Style.showFrames(prefix: String, frames: Map<Long, String>, time: Long?, maxZoom: Float, opacity: Float, preload: Boolean, below: String) {
    val ids = frames.keys.associateBy { "$prefix-$it" }
    for (layer in layers) if (layer.id.startsWith("$prefix-") && layer.id !in ids) remove(layer.id)
    for ((id, t) in ids) {
        if (getLayer(id) == null) {
            if (!preload && t != time) continue
            if (getSource(id) == null) addSource(RasterSource(id, TileSet("2.2.0", frames.getValue(t)).apply { this.maxZoom = maxZoom }, 256))
            val layer = RasterLayer(id, id).withProperties(
                PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
                PropertyFactory.rasterFadeDuration(0f),
            )
            if (getLayer(below) != null) addLayerBelow(layer, below) else addLayer(layer)
        }
        getLayer(id)?.setProperties(PropertyFactory.rasterOpacity(if (t == time) opacity else 0f))
    }
}

private fun Style.removeAll(prefix: String) {
    for (layer in layers) if (layer.id.startsWith(prefix)) remove(layer.id)
    for (source in sources) if (source.id.startsWith(prefix)) removeSource(source)
}

const val RAIN_FRAMES = RAIN
fun satelliteFrames(overlay: Overlay) = "${PREFIX}sat-${overlay.name.lowercase()}"
private const val FIELD = "${PREFIX}field-"
private const val WIND = "${PREFIX}wind"
private const val STATIONS = "${PREFIX}air-stations"
private const val SENSORS = "${PREFIX}air-sensors"

/** The forecast of [overlay] at [hour], as a picture over the forecast's area. */
fun Style.showField(overlay: Overlay, forecast: Forecast, hour: Int, below: String) {
    val spec = overlay.field ?: return
    val id = FIELD + overlay.name.lowercase()
    val picture = forecast.picture(spec, hour) ?: return
    val source = getSourceAs<ImageSource>(id)
    if (source == null) {
        addSource(ImageSource(id, forecast.grid.quad, picture))
        val layer = RasterLayer(id, id).withProperties(PropertyFactory.rasterFadeDuration(0f))
        if (getLayer(below) != null) addLayerBelow(layer, below) else addLayer(layer)
    } else {
        source.setCoordinates(forecast.grid.quad)
        source.setImage(picture)
    }
}

/** The wind at [hour] as arrows: pointing where it blows, colored and sized by its speed. */
fun Style.showWind(forecast: Forecast, hour: Int, below: String) {
    val speed = forecast.values["wind_speed_10m"]?.getOrNull(hour) ?: return
    val direction = forecast.values["wind_direction_10m"]?.getOrNull(hour) ?: return
    if (getImage(WIND) == null) addImage(WIND, windArrow(64), true)
    if (getSource(WIND) == null) addSource(GeoJsonSource(WIND))
    if (getLayer(WIND) == null) {
        val stops = WIND_SCALE.stops.map { (v, c) -> Expression.stop(v, Expression.color(c)) }.toTypedArray()
        val layer = SymbolLayer(WIND, WIND).withProperties(
            PropertyFactory.iconImage(WIND),
            PropertyFactory.iconRotate(Expression.get("to")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconColor(Expression.interpolate(Expression.linear(), Expression.get("speed"), *stops)),
            PropertyFactory.iconSize(Expression.interpolate(
                Expression.linear(), Expression.get("speed"),
                Expression.stop(0, 0.3f), Expression.stop(30, 0.5f), Expression.stop(80, 0.75f),
            )),
            PropertyFactory.iconHaloColor("rgba(0,0,0,0.5)"),
            PropertyFactory.iconHaloWidth(1f),
        )
        if (getLayer(below) != null) addLayerBelow(layer, below) else addLayer(layer)
    }
    getSourceAs<GeoJsonSource>(WIND)?.setGeoJson(FeatureCollection.fromFeatures(
        forecast.grid.points.indices.filter { !speed[it].isNaN() && !direction[it].isNaN() }.map { i ->
            val at = forecast.grid.points[i]
            Feature.fromGeometry(Point.fromLngLat(at.longitude, at.latitude)).apply {
                addNumberProperty("speed", speed[i])
                // Wind directions say where it comes from.
                addNumberProperty("to", (direction[i] + 180) % 360)
            }
        }
    ))
}

/** Measurements as dots in their color, with the value beside them closer in. */
fun Style.showReadings(sensors: Boolean, readings: List<Reading>, dark: Boolean, below: String) {
    val id = if (sensors) SENSORS else STATIONS
    val labels = "$id-labels"
    if (getSource(id) == null) addSource(GeoJsonSource(id))
    if (getLayer(id) == null) {
        val font = layers.filterIsInstance<SymbolLayer>()
            .firstNotNullOfOrNull { (it.textFont.value as? Array<*>)?.filterIsInstance<String>()?.takeIf { f -> f.isNotEmpty() } }
            ?.toTypedArray() ?: arrayOf("Noto Sans Regular")
        val dots = CircleLayer(id, id).withProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleRadius(if (sensors) 5f else 8f),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleOpacity(0.9f),
        )
        val text = SymbolLayer(labels, id).withProperties(
            PropertyFactory.textField(Expression.get("text")),
            PropertyFactory.textFont(font),
            PropertyFactory.textSize(11f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_LEFT),
            PropertyFactory.textOffset(arrayOf(0.9f, 0f)),
            PropertyFactory.textHaloWidth(1.5f),
        ).apply { minZoom = if (sensors) 12f else 9f }
        for (layer in listOf(dots, text)) if (getLayer(below) != null) addLayerBelow(layer, below) else addLayer(layer)
    }
    getLayer(id)?.setProperties(PropertyFactory.circleStrokeColor(if (dark) "rgba(0,0,0,0.7)" else "#ffffff"))
    getLayer(labels)?.setProperties(
        PropertyFactory.textColor(if (dark) "#ffffff" else "#202124"),
        PropertyFactory.textHaloColor(if (dark) "rgba(0,0,0,0.8)" else "rgba(255,255,255,0.9)"),
    )
    getSourceAs<GeoJsonSource>(id)?.setGeoJson(FeatureCollection.fromFeatures(readings.map {
        Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
            addStringProperty("color", String.format("#%06X", it.color and 0xFFFFFF))
            addStringProperty("text", it.text)
        }
    }))
}

/** Takes away the weather and air overlays that are off. */
private fun Style.removeWeather(on: Set<Overlay>) {
    if (Overlay.Rain !in on) removeAll("$RAIN-")
    for (overlay in Overlay.entries) {
        if (overlay in on) continue
        if (Satellite.layer(overlay) != null || Cams.layer(overlay) != null) removeAll(satelliteFrames(overlay) + "-")
        if (overlay.field != null && overlay != Overlay.Wind) removeAll(FIELD + overlay.name.lowercase())
    }
    if (Overlay.Wind !in on) removeAll(WIND)
    if (Overlay.AirStations !in on) removeAll(STATIONS)
    if (Overlay.AirSensors !in on) removeAll(SENSORS)
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
