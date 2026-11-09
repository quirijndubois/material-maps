package app.materialmaps

import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class TravelMode(val costing: String, val label: String) {
    Car("auto", "Drive"),
    Bike("bicycle", "Bike"),
    Walk("pedestrian", "Walk"),
    Transit("", "Transit"),
}

/**
 * One turn instruction, in OSRM terms: [type] is like `turn`, `fork` or `roundabout`, and
 * [modifier] like `left` or `slight right`. [beginIndex] points into [Route.points].
 */
data class Maneuver(
    val type: String,
    val modifier: String?,
    val instruction: String,
    val distance: Double,
    val duration: Double,
    val beginIndex: Int,
    /** The lanes at this maneuver, left to right; empty when the map doesn't say. */
    val lanes: List<Lane> = emptyList(),
)

/** One lane at a junction: where it goes, and whether it's a lane to be in for the route. */
data class Lane(val indications: List<String>, val valid: Boolean, val validIndication: String?) {
    /** The direction to draw: the one the route uses if this is a lane for it. */
    val shown get() = validIndication ?: indications.firstOrNull() ?: "straight"
}

/** Something to say [at] meters along the route, such as "In 300 meters, turn left". */
data class Announcement(val at: Double, val text: String)

/** Speed limits along a route, in km/h, by segment of [Route.points]; null where unknown. */
class SpeedLimits(private val bySegment: IntArray) {
    fun at(segment: Int): Int? = bySegment.getOrNull(segment)?.takeIf { it > 0 }
}

class RouteException(message: String) : Exception(message)

data class RouteProgress(
    val next: Maneuver?,
    /** Meters along the route. */
    val traveled: Double,
    /** Index into [Route.points] of the segment the user is on. */
    val segment: Int,
    val distanceToNext: Double,
    val remainingDistance: Double,
    val remainingTime: Double,
    val offRoute: Double,
    /** Compass direction of the route just ahead, in degrees. */
    val heading: Double,
    /** The closest point on the route. */
    val snapped: LatLng,
) {
    val arrived get() = remainingDistance < 25
}

class Route(
    val points: List<LatLng>,
    /** Meters. */
    val distance: Double,
    /** Seconds. */
    val duration: Double,
    val maneuvers: List<Maneuver>,
    val announcements: List<Announcement> = emptyList(),
    /** [points] as Valhalla sent it, for looking up more about the roads. */
    val shape: String = "",
    /** Where each leg starts in [points]: one leg to each stop, then one to the destination. */
    val legStarts: List<Int> = listOf(0),
) {
    /** The points of each leg, each ending where the next starts, from point [from] on. */
    fun legs(from: Int = 0): List<Pair<Int, List<LatLng>>> = legStarts.indices.mapNotNull { leg ->
        val start = maxOf(legStarts[leg], from)
        val end = legStarts.getOrNull(leg + 1) ?: points.lastIndex
        if (end - start < 1) null else leg to points.subList(start, end + 1)
    }

    /** The leg point [index] is on. */
    fun legAt(index: Int): Int = legStarts.indexOfLast { it <= index }.coerceAtLeast(0)

    /** Distance along the route to each point, in meters. */
    private val along = DoubleArray(points.size).also { a ->
        for (i in 1 until points.size) a[i] = a[i - 1] + metersBetween(points[i - 1], points[i])
    }

    /** The closest spot on segments [segments] to a point: which segment, how far along it (0..1), and meters away. */
    private data class Nearest(val segment: Int, val t: Double, val distance: Double)

    private fun nearest(lat: Double, lon: Double, segments: IntRange): Nearest {
        val cosLat = cos(Math.toRadians(lat))
        var best = Nearest(segments.first.coerceAtLeast(0), 0.0, Double.MAX_VALUE)
        for (i in segments) {
            // Flat local coordinates in meters around the current position; fine at these distances.
            val ax = (points[i].longitude - lon) * cosLat * METERS_PER_DEGREE
            val ay = (points[i].latitude - lat) * METERS_PER_DEGREE
            val dx = (points[i + 1].longitude - lon) * cosLat * METERS_PER_DEGREE - ax
            val dy = (points[i + 1].latitude - lat) * METERS_PER_DEGREE - ay
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else ((-ax * dx - ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val d = hypot(ax + t * dx, ay + t * dy)
            if (d < best.distance) best = Nearest(i, t, d)
        }
        return best
    }

    /** Where [lat]/[lon] is along this route, snapped to the closest segment. */
    fun progress(lat: Double, lon: Double): RouteProgress {
        val (bestSegment, bestT, best) = nearest(lat, lon, 0 until points.size - 1)
        val bestAlong = alongAt(bestSegment, bestT)
        val total = along.lastOrNull() ?: 0.0
        val next = maneuvers.firstOrNull { it.beginIndex > bestSegment } ?: maneuvers.lastOrNull()
        val nextAt = next?.let { along[it.beginIndex.coerceIn(0, points.lastIndex)] } ?: total
        val remaining = (total - bestAlong).coerceAtLeast(0.0)
        return RouteProgress(
            next = next,
            traveled = bestAlong,
            segment = bestSegment,
            distanceToNext = (nextAt - bestAlong).coerceAtLeast(0.0),
            remainingDistance = remaining,
            remainingTime = if (total > 0) duration * remaining / total else 0.0,
            offRoute = best,
            heading = directionAt(bestAlong),
            snapped = points.getOrNull(bestSegment + 1)?.let { points[bestSegment].interpolate(it, bestT) }
                ?: points.firstOrNull() ?: LatLng(lat, lon),
        )
    }

    private fun alongAt(segment: Int, t: Double) =
        if (points.size < 2) 0.0 else along[segment] + t * (along[segment + 1] - along[segment])

    /**
     * Meters along the route and meters off it for [lat]/[lon], looking only at the stretch
     * between [from] and [to] meters along. Following a car with this, a fix next to a corner
     * can't be taken for the street it's about to turn into, or for a road it already passed.
     */
    fun locate(lat: Double, lon: Double, from: Double, to: Double): Pair<Double, Double> {
        if (points.size < 2) return 0.0 to Double.MAX_VALUE
        val first = (segmentAt(from) - 1).coerceAtLeast(0)
        val last = (segmentAt(to) + 1).coerceAtMost(points.size - 2)
        val n = nearest(lat, lon, first..last)
        return alongAt(n.segment, n.t) to n.distance
    }

    val length get() = along.lastOrNull() ?: 0.0

    /** This route as a car drives it, with its corners rounded into arcs; for the arrow to follow. */
    val drivingLine: Route by lazy { Route(roundCorners(points, CORNER_RADIUS), distance, duration, emptyList()) }

    /** The segment [meters] along the route falls in. */
    private fun segmentAt(meters: Double): Int {
        val i = along.binarySearch(meters.coerceIn(0.0, length))
        return (if (i >= 0) i else -i - 2).coerceIn(0, (points.size - 2).coerceAtLeast(0))
    }

    /** The spot [meters] along the route. */
    fun pointAt(meters: Double): LatLng {
        if (points.size < 2) return points.firstOrNull() ?: LatLng(0.0, 0.0)
        val i = segmentAt(meters)
        val t = ((meters - along[i]) / (along[i + 1] - along[i]).coerceAtLeast(1e-6)).coerceIn(0.0, 1.0)
        return points[i].interpolate(points[i + 1], t)
    }

    /**
     * Which way the road goes [meters] along the route, over [HEADING_SPAN] meters either side:
     * a corner turns the arrow within those few meters, while driving through it.
     */
    fun directionAt(meters: Double, span: Double = HEADING_SPAN): Double {
        if (points.size < 2) return 0.0
        val from = (meters - span).coerceIn(0.0, length)
        val to = (meters + span).coerceIn(0.0, length)
        if (to - from < 1) return bearingBetween(points[points.size - 2], points.last())
        return bearingBetween(pointAt(from), pointAt(to))
    }

    /** The part of the route from [meters] along it to the end. */
    fun remainingFrom(meters: Double): List<LatLng> {
        if (points.size < 2 || meters <= 0) return points
        val i = (1 until points.size).firstOrNull { along[it] > meters } ?: return listOf(points.last())
        val t = (meters - along[i - 1]) / (along[i] - along[i - 1]).coerceAtLeast(1e-6)
        return listOf(points[i - 1].interpolate(points[i], t)) + points.subList(i, points.size)
    }
}

/**
 * Turn-by-turn routing with Valhalla on the FOSSGIS server (https://valhalla1.openstreetmap.de),
 * built on OpenStreetMap data. Free, no API key; instructions come in the phone's language.
 * Answers come in OSRM's format, which adds lanes and timed voice announcements.
 */
object Valhalla {
    private const val BASE = "https://valhalla1.openstreetmap.de"

    /** The best route first, then up to two alternatives (none when there are [stops] on the way). */
    suspend fun route(
        from: LatLng,
        stops: List<LatLng>,
        to: LatLng,
        mode: TravelMode,
        settings: Settings,
    ): List<Route> {
        // 0 means avoid as far as possible, 0.5 is Valhalla's default.
        fun use(avoid: Boolean) = if (avoid) 0.0 else 0.5
        val costing = JSONObject().put("use_ferry", use(settings.avoidFerries))
        if (mode == TravelMode.Car) {
            costing.put("use_highways", use(settings.avoidHighways)).put("use_tolls", use(settings.avoidTolls))
        }
        val locations = JSONArray()
        (listOf(from) + stops + to).forEach { locations.put(JSONObject().put("lat", it.latitude).put("lon", it.longitude)) }
        val request = JSONObject()
            .put("locations", locations)
            .put("costing", mode.costing)
            .put("costing_options", JSONObject().put(mode.costing, costing))
            .put("alternates", if (stops.isEmpty()) 2 else 0)
            .put("format", "osrm")
            .put("voice_instructions", true)
            .put(
                "directions_options",
                JSONObject()
                    .put("units", if (settings.units == Units.Imperial) "miles" else "kilometers")
                    .put("language", Locale.getDefault().toLanguageTag()),
            )
        val json = try {
            JSONObject(httpGet("$BASE/route?json=" + URLEncoder.encode(request.toString(), "UTF-8")))
        } catch (e: HttpException) {
            throw RouteException(errorMessage(e.body))
        }
        return json.optJSONArray("routes").orEmpty().map(::parseRoute)
    }

    /** Speed limits along [route], matched from its shape; driving only. */
    suspend fun speedLimits(route: Route): SpeedLimits {
        val request = JSONObject()
            .put("encoded_polyline", route.shape)
            .put("shape_match", "edge_walk")
            .put("costing", "auto")
            .put(
                "filters",
                JSONObject()
                    .put("attributes", JSONArray().put("edge.speed_limit").put("edge.begin_shape_index").put("edge.end_shape_index"))
                    .put("action", "include"),
            )
        val json = JSONObject(httpGet("$BASE/trace_attributes?json=" + URLEncoder.encode(request.toString(), "UTF-8")))
        val limits = IntArray((route.points.size - 1).coerceAtLeast(0))
        json.optJSONArray("edges").orEmpty().map { edge ->
            val limit = edge.optInt("speed_limit", 0)
            val begin = edge.optInt("begin_shape_index", 0).coerceIn(0, limits.size)
            val end = edge.optInt("end_shape_index", 0).coerceIn(begin, limits.size)
            for (i in begin until end) limits[i] = limit
        }
        return SpeedLimits(limits)
    }

    private fun errorMessage(body: String): String {
        val error = runCatching { JSONObject(body) }.getOrNull()
        return when (error?.optInt("error_code")) {
            171 -> "There's no road near the start or the destination."
            154, 155 -> "That's too far for this way of travelling."
            442, 443 -> "No route found."
            else -> error?.optString("error")?.takeIf { it.isNotBlank() } ?: "Couldn't get directions."
        }
    }

    private fun parseRoute(route: JSONObject): Route {
        val shape = route.getString("geometry")
        val points = decodePolyline(shape)
        val maneuvers = ArrayList<Maneuver>()
        val announcements = ArrayList<Announcement>()
        var stepStart = 0.0
        var searchFrom = 0
        val legStarts = ArrayList<Int>()
        route.getJSONArray("legs").map { leg ->
            legStarts += searchFrom
            leg.getJSONArray("steps").map { step ->
                val m = step.getJSONObject("maneuver")
                val location = m.getJSONArray("location")
                val at = LatLng(location.getDouble(1), location.getDouble(0))
                // Steps come in order and start on a point of the line, so look onward from the
                // last one; the closest point is only a fallback, as a route can pass by twice.
                val index = (searchFrom until points.size).firstOrNull { metersBetween(points[it], at) < 1.0 }
                    ?: (searchFrom until points.size).minByOrNull { metersBetween(points[it], at) }
                    ?: searchFrom
                searchFrom = index
                val distance = step.optDouble("distance", 0.0)
                val type = m.optString("type")
                maneuvers += Maneuver(
                    type = type,
                    modifier = m.optString("modifier").ifBlank { null },
                    instruction = m.optString("instruction"),
                    distance = distance,
                    duration = step.optDouble("duration", 0.0),
                    beginIndex = index,
                    lanes = step.optJSONArray("intersections")?.optJSONObject(0)?.optJSONArray("lanes").orEmpty().map { lane ->
                        Lane(
                            indications = lane.optJSONArray("indications").strings(),
                            valid = lane.optBoolean("valid"),
                            validIndication = lane.optString("valid_indication").ifBlank { null },
                        )
                    },
                )
                // Each announcement says how far before the end of its step to speak it.
                step.optJSONArray("voiceInstructions").orEmpty().map { v ->
                    announcements += Announcement(
                        at = stepStart + distance - v.optDouble("distanceAlongGeometry", 0.0),
                        text = v.optString("announcement"),
                    )
                }
                stepStart += distance
            }
        }
        return Route(
            points = points,
            distance = route.optDouble("distance", 0.0),
            duration = route.optDouble("duration", 0.0),
            maneuvers = maneuvers,
            announcements = announcements.filter { it.text.isNotBlank() }.sortedBy { it.at },
            shape = shape,
            legStarts = legStarts.ifEmpty { listOf(0) },
        )
    }

    private fun JSONArray?.orEmpty() = this ?: JSONArray()
    private fun JSONArray?.strings() = this?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    private fun <T> JSONArray.map(transform: (JSONObject) -> T) = (0 until length()).map { transform(getJSONObject(it)) }
}

/** Decodes an encoded polyline; Valhalla uses six digits of precision, MOTIS seven. */
internal fun decodePolyline(encoded: String, precision: Int = 6): List<LatLng> {
    val factor = Math.pow(10.0, precision.toDouble())
    val points = ArrayList<LatLng>()
    var index = 0
    var lat = 0L
    var lon = 0L
    fun next(): Long {
        var result = 0L
        var shift = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
        } while (b >= 0x20)
        return if ((result and 1L) != 0L) (result shr 1).inv() else result shr 1
    }
    while (index < encoded.length) {
        lat += next()
        lon += next()
        points.add(LatLng(lat / factor, lon / factor))
    }
    return points
}

private const val EARTH_RADIUS = 6_371_000.0
private const val METERS_PER_DEGREE = EARTH_RADIUS * Math.PI / 180

internal fun metersBetween(a: LatLng, b: LatLng): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS * atan2(sqrt(h), sqrt(1 - h))
}

/** Follows the units setting. */
@Volatile
var distanceUnits = Units.Metric

fun formatDistance(meters: Double): String = if (distanceUnits == Units.Imperial) {
    val miles = meters / 1609.344
    when {
        miles < 0.1 -> "${((meters * 3.28084 / 50).roundToInt() * 50).coerceAtLeast(10)} ft"
        miles < 10 -> String.format(Locale.getDefault(), "%.1f mi", miles)
        else -> "${miles.roundToInt()} mi"
    }
} else when {
    meters < 1000 -> "${((meters / 10).roundToInt() * 10)} m"
    meters < 10_000 -> String.format(Locale.getDefault(), "%.1f km", meters / 1000)
    else -> "${(meters / 1000).roundToInt()} km"
}

fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt().coerceAtLeast(1)
    return when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

private const val HEADING_SPAN = 4.0
/** Meters; about how tight a car takes a corner in town. */
private const val CORNER_RADIUS = 10.0

/**
 * [points] with every corner replaced by an arc of [radius] meters (smaller where the
 * segments are too short for it), sampled about every meter: the line a car actually drives.
 */
private fun roundCorners(points: List<LatLng>, radius: Double): List<LatLng> {
    if (points.size < 3) return points
    val result = mutableListOf(points.first())
    for (i in 1 until points.lastIndex) {
        val b = points[i]
        val cosLat = cos(Math.toRadians(b.latitude))
        // Flat meters around the corner.
        fun toXY(p: LatLng) = doubleArrayOf((p.longitude - b.longitude) * cosLat * METERS_PER_DEGREE, (p.latitude - b.latitude) * METERS_PER_DEGREE)
        fun toLatLng(x: Double, y: Double) = LatLng(b.latitude + y / METERS_PER_DEGREE, b.longitude + x / (cosLat * METERS_PER_DEGREE))
        val a = toXY(points[i - 1])
        val c = toXY(points[i + 1])
        val lengthA = hypot(a[0], a[1])
        val lengthC = hypot(c[0], c[1])
        if (lengthA < 0.1 || lengthC < 0.1) {
            result += b
            continue
        }
        val ux = a[0] / lengthA
        val uy = a[1] / lengthA
        val wx = c[0] / lengthC
        val wy = c[1] / lengthC
        // How far the road turns here; nearly straight, or nearly turning back, stays as it is.
        val turn = Math.PI - acos((ux * wx + uy * wy).coerceIn(-1.0, 1.0))
        if (turn < Math.toRadians(2.0) || turn > Math.toRadians(170.0)) {
            result += b
            continue
        }
        val tanHalf = tan(turn / 2)
        // Where the arc meets each segment; at most a bit under half of it, so arcs don't overlap.
        val reach = minOf(radius * tanHalf, 0.45 * lengthA, 0.45 * lengthC)
        val r = reach / tanHalf
        val bx = ux + wx
        val by = uy + wy
        val bLength = hypot(bx, by)
        val toCenter = r / cos(turn / 2)
        val ox = bx / bLength * toCenter
        val oy = by / bLength * toCenter
        val start = atan2(uy * reach - oy, ux * reach - ox)
        val end = atan2(wy * reach - oy, wx * reach - ox)
        val sweep = ((end - start + 3 * Math.PI) % (2 * Math.PI)) - Math.PI
        val steps = ceil(abs(sweep) * r).toInt().coerceAtLeast(2)
        for (k in 0..steps) {
            val angle = start + sweep * k / steps
            result += toLatLng(ox + r * cos(angle), oy + r * sin(angle))
        }
    }
    result += points.last()
    return result
}

private fun LatLng.interpolate(other: LatLng, t: Double) =
    LatLng(latitude + (other.latitude - latitude) * t, longitude + (other.longitude - longitude) * t)

/** Initial compass bearing from [a] to [b], 0..360 degrees. */
fun bearingBetween(a: LatLng, b: LatLng): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}
