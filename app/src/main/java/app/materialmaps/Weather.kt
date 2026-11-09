package app.materialmaps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLngQuad
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The last few hours, in ten-minute steps, for the radar and satellite overlays to share one
 * slider: rain from RainViewer, clouds and lightning from EUMETSAT.
 */
object RecentTimes {
    const val STEP = 600L
    /** Three hours back. RainViewer only has two; earlier, the rain is left out. */
    private const val STEPS = 18

    /** Oldest first, in epoch seconds, ending at the latest whole ten minutes. */
    fun now(): List<Long> {
        val end = System.currentTimeMillis() / 1000 / STEP * STEP
        return (STEPS downTo 0).map { end - it * STEP }
    }
}

/**
 * Pictures from Meteosat Third Generation on EUMETSAT's public map server, which has every one
 * since the satellite started: clouds in infrared (so also at night), the earth as it looks
 * (in daylight; at night clouds in infrared over city lights), and the Lightning Imager's
 * flash areas. The server picks the picture nearest to the
 * time asked for, so the last steps repeat the latest one until a newer one is out.
 */
object Satellite {
    const val HOST = "view.eumetsat.int"
    private const val INFRARED = "mtg_fd:ir105_hrfi"

    fun layer(overlay: Overlay): String? = when (overlay) {
        Overlay.Clouds -> INFRARED
        Overlay.LiveSatellite -> "mtg_fd:rgb_geocolour"
        Overlay.Lightning -> "mtg_fd:li_afa"
        else -> null
    }

    fun tiles(layer: String, time: Long): String =
        "https://$HOST/geoserver/ows?service=WMS&version=1.3.0&request=GetMap&layers=$layer&styles=" +
            "&crs=EPSG:3857&bbox={bbox-epsg-3857}&width=256&height=256&format=image/png&transparent=true" +
            "&time=" + DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(time))

    /**
     * The infrared pictures are grey all over: warm ground dark, cold cloud tops bright. Here
     * the ground becomes see-through and the clouds white, so they can go over the map.
     */
    object CloudTiles : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            val url = chain.request().url
            if (url.host != HOST || url.queryParameter("layers") != INFRARED || response.code != 200) return response
            val body = response.body ?: return response
            val bytes = body.bytes()
            val png = runCatching { clouds(bytes) }.getOrNull()
                ?: return response.newBuilder().body(bytes.toResponseBody(body.contentType())).build()
            return response.newBuilder()
                .header("Content-Type", "image/png")
                .body(png.toResponseBody("image/png".toMediaType()))
                .build()
        }

        private fun clouds(image: ByteArray): ByteArray? {
            val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size) ?: return null
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            bitmap.recycle()
            for (i in pixels.indices) {
                val p = pixels[i]
                if (Color.alpha(p) == 0) continue
                val brightness = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
                // Ground is darker than about 90, the highest cloud tops about 190.
                val alpha = ((brightness - 90) * 255 / 90).coerceIn(0, 230)
                pixels[i] = Color.argb(alpha, 255, 255, 255)
            }
            val out = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
            return ByteArrayOutputStream().use { stream ->
                out.compress(Bitmap.CompressFormat.PNG, 100, stream)
                out.recycle()
                stream.toByteArray()
            }
        }
    }
}

/** A color scale: colors at values, blended in between. [auto] scales fit what's on screen. */
class Scale(val unit: String, val stops: List<Pair<Float, Int>>) {
    fun color(v: Float): Int {
        if (v.isNaN()) return Color.TRANSPARENT
        if (v <= stops.first().first) return stops.first().second
        for (i in 1 until stops.size) {
            val (b, cb) = stops[i]
            if (v <= b) {
                val (a, ca) = stops[i - 1]
                return blend(ca, cb, (v - a) / (b - a))
            }
        }
        return stops.last().second
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun mix(x: Int, y: Int) = (x + (y - x) * t).roundToInt()
        return Color.argb(mix(Color.alpha(a), Color.alpha(b)), mix(Color.red(a), Color.red(b)), mix(Color.green(a), Color.green(b)), mix(Color.blue(a), Color.blue(b)))
    }
}

/**
 * What a forecast overlay shows: [variable] from Open-Meteo's weather or air quality model,
 * drawn in [scale]. Without a scale, it's fitted to the values loaded (for CO₂ and methane,
 * where what matters is where there's more or less).
 */
class FieldSpec(val air: Boolean, val variable: String, val label: String, val scale: Scale?, val unit: String = scale?.unit.orEmpty())

private const val EAQI_GOOD = 0xFF50F0E6.toInt()
private const val EAQI_FAIR = 0xFF50CCAA.toInt()
private const val EAQI_MODERATE = 0xFFF0E641.toInt()
private const val EAQI_POOR = 0xFFFF5050.toInt()
private const val EAQI_VERY_POOR = 0xFF960032.toInt()
private const val EAQI_EXTREMELY_POOR = 0xFF7D2181.toInt()

/** The European Air Quality Index colors, at the band edges for a pollutant. */
private fun eaqi(unit: String, vararg edges: Float) = Scale(unit, edges.toList().zip(listOf(EAQI_GOOD, EAQI_FAIR, EAQI_MODERATE, EAQI_POOR, EAQI_VERY_POOR, EAQI_EXTREMELY_POOR)))

/** Particulate matter in µg/m³, by the European index bands, for the sensor dots too. */
val PM25_SCALE = eaqi("µg/m³", 0f, 10f, 20f, 25f, 50f, 75f)

/** Dark blue through green to yellow, for scales fitted to the values. */
private val FITTED = listOf(0xFF440154, 0xFF3B528B, 0xFF21918C, 0xFF5EC962, 0xFFFDE725).map { it.toInt() }

val Overlay.field: FieldSpec?
    get() = when (this) {
        Overlay.Wind -> FieldSpec(false, "wind_speed_10m", "Wind", WIND_SCALE)
        Overlay.CloudForecast -> FieldSpec(false, "cloud_cover", "Cloud cover", Scale("%", listOf(0f to 0x00607D8B, 100f to 0xD0607D8B.toInt())))
        Overlay.Storms -> FieldSpec(
            false, "cape", "Storm energy (CAPE)",
            Scale("J/kg", listOf(100f to 0x00FFEB3B, 400f to 0x99FFEB3B.toInt(), 1000f to 0xB3FF9800.toInt(), 2000f to 0xC0F44336.toInt(), 3500f to 0xCC9C27B0.toInt())),
        )
        else -> null
    }

/** The hours the forecast slider steps through: half a day back to three days ahead. */
object ForecastTimes {
    fun now(): List<Long> {
        val hour = System.currentTimeMillis() / 1000 / 3600 * 3600
        return (-12..72).map { hour + it * 3600L }
    }
}

/**
 * Air quality, CO₂ and methane forecasts from the Copernicus Atmosphere Monitoring Service
 * (CAMS), drawn by ECMWF's public chart server at the models' own detail: hourly for Europe,
 * every three hours worldwide. It only has pictures for exactly those times.
 */
object Cams {
    const val HOST = "eccharts.ecmwf.int"

    class Layer(val name: String, val hours: Int)

    fun layer(overlay: Overlay): Layer? = when (overlay) {
        Overlay.Pm25 -> Layer("composition_europe_pm2p5_forecast_surface", 1)
        Overlay.Pm10 -> Layer("composition_europe_pm10_forecast_surface", 1)
        Overlay.No2 -> Layer("composition_europe_no2_forecast_surface", 1)
        Overlay.Ozone -> Layer("composition_europe_o3_forecast_surface", 1)
        Overlay.Dust -> Layer("composition_europe_dust_forecast_surface", 1)
        Overlay.Uv -> Layer("composition_uvindex", 3)
        Overlay.Co2 -> Layer("composition_co2_surface", 3)
        Overlay.Methane -> Layer("composition_ch4_surface", 3)
        else -> null
    }

    /** The picture time for [time]: the last one the layer has. */
    fun frameTime(layer: Layer, time: Long) = time / (layer.hours * 3600L) * (layer.hours * 3600L)

    fun tiles(layer: Layer, time: Long): String =
        "https://$HOST/wms/?token=public&service=WMS&version=1.3.0&request=GetMap&layers=${layer.name}&styles=" +
            "&crs=EPSG:3857&bbox={bbox-epsg-3857}&width=256&height=256&format=image/png&transparent=true" +
            "&time=" + DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(time))
}

/** Wind speed in km/h, for the arrows. */
val WIND_SCALE = Scale(
    "km/h",
    listOf(0f to 0xFF81D4FA.toInt(), 15f to 0xFF4CAF50.toInt(), 30f to 0xFFFFEB3B.toInt(), 50f to 0xFFFF9800.toInt(), 75f to 0xFFF44336.toInt(), 100f to 0xFF9C27B0.toInt()),
)

/** The points a forecast was asked for: [cols] by [rows], evenly spaced on the map. */
class Grid(val cols: Int, val rows: Int, val west: Double, val east: Double, val north: Double, val south: Double) {
    private val top = mercatorY(north)
    private val bottom = mercatorY(south)

    /** Row by row from the north-west. */
    val points: List<LatLng> = (0 until rows).flatMap { r ->
        val lat = latitude(top + (bottom - top) * r / (rows - 1))
        (0 until cols).map { c -> LatLng(lat, west + (east - west) * c / (cols - 1)) }
    }

    val quad get() = LatLngQuad(LatLng(north, west), LatLng(north, east), LatLng(south, east), LatLng(south, west))

    /** How much wider than tall it is on the map. */
    val aspect get() = (Math.toRadians(east - west) / (top - bottom)).coerceIn(0.2, 5.0)

    companion object {
        /** About this many points, spread to fit the area's shape. */
        private const val POINTS = 300

        fun over(area: LatLngBounds): Grid {
            val width = Math.toRadians(area.longitudeEast - area.longitudeWest)
            val height = mercatorY(area.latitudeNorth) - mercatorY(area.latitudeSouth)
            val aspect = (width / height).coerceIn(0.2, 5.0)
            val cols = sqrt(POINTS * aspect).roundToInt().coerceIn(4, 40)
            val rows = (POINTS / cols).coerceIn(4, 40)
            return Grid(cols, rows, area.longitudeWest, area.longitudeEast, area.latitudeNorth, area.latitudeSouth)
        }

        private fun mercatorY(lat: Double) = ln(tan(PI / 4 + Math.toRadians(lat.coerceIn(-85.0, 85.0)) / 2))
        private fun latitude(y: Double) = Math.toDegrees(2 * atan(exp(y)) - PI / 2)
    }
}

/** Hourly values per variable, per hour, per [Grid] point; NaN where the model has none. */
class Forecast(val grid: Grid, val times: List<Long>, val values: Map<String, Array<FloatArray>>) {
    /** For scales fitted to the values: the lowest and highest of [variable] at any hour. */
    fun range(variable: String): Pair<Float, Float>? {
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (hour in values[variable] ?: return null) for (v in hour) if (!v.isNaN()) {
            if (v < lo) lo = v
            if (v > hi) hi = v
        }
        return if (lo <= hi) lo to hi else null
    }

    fun scale(spec: FieldSpec): Scale? = spec.scale ?: range(spec.variable)?.let { (lo, hi) ->
        val span = (hi - lo).takeIf { it > 0 } ?: 1f
        Scale(spec.unit, FITTED.mapIndexed { i, c -> lo + span * i / (FITTED.size - 1) to c })
    }

    /** [variable] at [hour] as a picture to lay over [Grid.quad], smoothly blended between points. */
    fun picture(spec: FieldSpec, hour: Int): Bitmap? {
        val scale = scale(spec) ?: return null
        val grid = values[spec.variable]?.getOrNull(hour) ?: return null
        val cols = this.grid.cols
        val rows = this.grid.rows
        val w = sqrt(256.0 * 256 * this.grid.aspect).roundToInt().coerceIn(64, 512)
        val h = (256 * 256 / w).coerceIn(64, 512)
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val gy = y.toFloat() / (h - 1) * (rows - 1)
            val r0 = floor(gy).toInt().coerceAtMost(rows - 2)
            val ty = gy - r0
            for (x in 0 until w) {
                val gx = x.toFloat() / (w - 1) * (cols - 1)
                val c0 = floor(gx).toInt().coerceAtMost(cols - 2)
                val tx = gx - c0
                val a = grid[r0 * cols + c0]
                val b = grid[r0 * cols + c0 + 1]
                val c = grid[(r0 + 1) * cols + c0]
                val d = grid[(r0 + 1) * cols + c0 + 1]
                val v = if (a.isNaN() || b.isNaN() || c.isNaN() || d.isNaN()) {
                    // At the edge of what the model covers: the nearest point, if it has a value.
                    grid[(r0 + ty.roundToInt()) * cols + c0 + tx.roundToInt()]
                } else {
                    (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
                }
                pixels[y * w + x] = scale.color(v)
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}

/**
 * Weather forecasts from Open-Meteo (https://open-meteo.com), free for apps like this one,
 * from the national weather services' models. Asked for a grid of points over the area, for
 * the [ForecastTimes].
 */
object OpenMeteo {
    private const val WEATHER = "https://api.open-meteo.com/v1/forecast"
    private const val PAST_HOURS = 12L
    private const val AHEAD_HOURS = 72L

    suspend fun forecast(overlays: Set<Overlay>, area: LatLngBounds): Forecast? = coroutineScope {
        val specs = overlays.mapNotNull { it.field }
        if (specs.isEmpty()) return@coroutineScope null
        val grid = Grid.over(area)
        val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneOffset.UTC)
        val window = "&start_hour=${format.format(now.minus(PAST_HOURS, ChronoUnit.HOURS))}" +
            "&end_hour=${format.format(now.plus(AHEAD_HOURS, ChronoUnit.HOURS))}&timeformat=unixtime"
        val coords = "latitude=" + grid.points.joinToString(",") { "%.4f".format(java.util.Locale.US, it.latitude) } +
            "&longitude=" + grid.points.joinToString(",") { "%.4f".format(java.util.Locale.US, it.longitude) }
        val variables = specs.map { it.variable }.toMutableSet()
        if (Overlay.Wind in overlays) variables += "wind_direction_10m"
        val requests = listOf(async { httpGet("$WEATHER?$coords&hourly=${variables.joinToString(",")}$window") })
        val values = HashMap<String, Array<FloatArray>>()
        var times: List<Long>? = null
        for (answer in requests.awaitAll()) withContext(Dispatchers.Default) {
            val locations = JSONArray(answer)
            for (p in 0 until locations.length()) {
                val hourly = locations.getJSONObject(p).getJSONObject("hourly")
                val t = hourly.getJSONArray("time")
                if (times == null) times = (0 until t.length()).map { t.getLong(it) }
                for (key in hourly.keys()) {
                    if (key == "time") continue
                    val series = hourly.getJSONArray(key)
                    val table = values.getOrPut(key) { Array(series.length()) { FloatArray(grid.points.size) { Float.NaN } } }
                    for (hour in 0 until minOf(series.length(), table.size)) {
                        table[hour][p] = if (series.isNull(hour)) Float.NaN else series.getDouble(hour).toFloat()
                    }
                }
            }
        }
        times?.let { Forecast(grid, it, values) }
    }
}

/** A measurement at a place, as a colored dot with its value. */
class Reading(val lat: Double, val lon: Double, val text: String, val color: Int)

/**
 * The Dutch air quality network (https://www.luchtmeetnet.nl): the latest Luchtkwaliteitsindex,
 * 1 (good) to 11 (very bad), at each station. Where the stations are rarely changes, so that's
 * kept on the phone.
 */
object Luchtmeetnet {
    private const val API = "https://api.luchtmeetnet.nl/open_api"
    private val fetches = Semaphore(6)

    /** The index's colors, from good to very bad. */
    private val COLORS = listOf(
        0xFF0020C5, 0xFF006DFE, 0xFF00ABFE, 0xFFFEFE00, 0xFFFFD100, 0xFFFFA200,
        0xFFFF5900, 0xFFE60000, 0xFFAF0000, 0xFF7D0000, 0xFF962C82,
    ).map { it.toInt() }

    suspend fun latest(context: Context): List<Reading> = coroutineScope {
        val end = Instant.now().truncatedTo(ChronoUnit.HOURS)
        val start = end.minus(3, ChronoUnit.HOURS)
        val newest = LinkedHashMap<String, Int>()
        var page = 1
        var last = 1
        while (page <= last && page <= 20) {
            val json = JSONObject(httpGet("$API/lki?start=$start&end=$end&order_by=timestamp_measured&order_direction=desc&station_number=&page=$page"))
            last = json.optJSONObject("pagination")?.optInt("last_page", 1) ?: 1
            val data = json.optJSONArray("data") ?: break
            for (i in 0 until data.length()) {
                val d = data.getJSONObject(i)
                // Newest first, so the first value for each station is its latest.
                newest.putIfAbsent(d.getString("station_number"), d.getInt("value"))
            }
            page++
        }
        val places = stations(context, newest.keys)
        newest.mapNotNull { (number, index) ->
            val at = places[number] ?: return@mapNotNull null
            Reading(at.latitude, at.longitude, "$index", COLORS[(index - 1).coerceIn(0, COLORS.size - 1)])
        }
    }

    /** Where each of [numbers] is, from the phone where known, else asked for and kept. */
    private suspend fun stations(context: Context, numbers: Collection<String>): Map<String, LatLng> = coroutineScope {
        val file = File(context.filesDir, "luchtmeetnet-stations.json")
        val known = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
        val missing = numbers.filter { !known.has(it) }
        val found = missing.map { number ->
            async {
                fetches.withPermit {
                    runCatching {
                        val c = JSONObject(httpGet("$API/stations/$number")).getJSONObject("data").getJSONObject("geometry").getJSONArray("coordinates")
                        number to JSONArray().put(c.getDouble(0)).put(c.getDouble(1))
                    }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull()
        if (found.isNotEmpty()) {
            for ((number, at) in found) known.put(number, at)
            runCatching { file.writeText(known.toString()) }
        }
        numbers.mapNotNull { n -> known.optJSONArray(n)?.let { n to LatLng(it.getDouble(1), it.getDouble(0)) } }.toMap()
    }
}

/**
 * Sensor.Community (https://sensor.community): particulate sensors run by people at home, the
 * world over, averaged over the last five minutes. It comes as one file of every sensor.
 */
object SensorCommunity {
    private const val URL = "https://data.sensor.community/static/v2/data.dust.min.json"

    /** Every sensor's PM2.5 in µg/m³: latitudes, longitudes and values. */
    class Sensors(val lat: DoubleArray, val lon: DoubleArray, val pm25: FloatArray)

    suspend fun all(): Sensors {
        val bytes = httpGetBytes(URL)
        return withContext(Dispatchers.Default) {
            val lat = ArrayList<Double>()
            val lon = ArrayList<Double>()
            val pm = ArrayList<Float>()
            val seen = HashSet<Long>()
            JsonReader(InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    var la = Double.NaN
                    var lo = Double.NaN
                    var value = Float.NaN
                    var id = -1L
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "location" -> {
                                reader.beginObject()
                                while (reader.hasNext()) when (reader.nextName()) {
                                    "latitude" -> la = reader.nextString().toDoubleOrNull() ?: Double.NaN
                                    "longitude" -> lo = reader.nextString().toDoubleOrNull() ?: Double.NaN
                                    "id" -> id = reader.nextLong()
                                    else -> reader.skipValue()
                                }
                                reader.endObject()
                            }
                            "sensordatavalues" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    var type = ""
                                    var v = Float.NaN
                                    reader.beginObject()
                                    while (reader.hasNext()) when (reader.nextName()) {
                                        "value_type" -> type = reader.nextString()
                                        "value" -> v = reader.nextString().toFloatOrNull() ?: Float.NaN
                                        else -> reader.skipValue()
                                    }
                                    reader.endObject()
                                    if (type == "P2") value = v
                                }
                                reader.endArray()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    // Broken sensors report nonsense; one per spot is plenty.
                    if (!la.isNaN() && !lo.isNaN() && value in 0f..500f && seen.add(id)) {
                        lat += la
                        lon += lo
                        pm += value
                    }
                }
                reader.endArray()
            }
            Sensors(lat.toDoubleArray(), lon.toDoubleArray(), pm.toFloatArray())
        }
    }

    fun readings(sensors: Sensors, area: LatLngBounds): List<Reading> =
        sensors.lat.indices.filter { area.contains(LatLng(sensors.lat[it], sensors.lon[it])) }.map {
            val v = sensors.pm25[it]
            Reading(sensors.lat[it], sensors.lon[it], v.roundToInt().toString(), PM25_SCALE.color(v))
        }
}

/** An arrow pointing up, white, for the map to tint by wind speed. */
fun windArrow(size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val s = size.toFloat()
    val path = Path().apply {
        moveTo(s * 0.5f, s * 0.08f)
        lineTo(s * 0.8f, s * 0.45f)
        lineTo(s * 0.6f, s * 0.45f)
        lineTo(s * 0.6f, s * 0.92f)
        lineTo(s * 0.4f, s * 0.92f)
        lineTo(s * 0.4f, s * 0.45f)
        lineTo(s * 0.2f, s * 0.45f)
        close()
    }
    Canvas(bitmap).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    return bitmap
}
