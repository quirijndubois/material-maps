package app.materialmaps

import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.net.URLEncoder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import kotlin.math.cos
import kotlin.math.hypot

/** One turn of a walk, ride or drive to or from a stop, as MOTIS gives it. */
data class TransitStep(
    /** Like LEFT, SLIGHTLY_RIGHT, CIRCLE_CLOCKWISE or STAIRS. */
    val direction: String,
    val street: String?,
    val exit: String?,
    val start: LatLng,
)

/** A ride or a walk within a public transport journey. Times are epoch millis. */
data class TransitLeg(
    /**
     * MOTIS mode, such as WALK, BIKE, CAR, BUS, TRAM, SUBWAY, FERRY or REGIONAL_RAIL; TAXI for
     * a drive asked for as one (MOTIS calls that CAR too).
     */
    val mode: String,
    /** For looking up the whole trip; rides only. */
    val tripId: String?,
    val line: String?,
    val headsign: String?,
    val agency: String?,
    /** Line colors as #RRGGBB when the operator publishes them. */
    val color: String?,
    val textColor: String?,
    val fromName: String,
    val fromStopId: String?,
    val fromTrack: String?,
    val toName: String,
    val toTrack: String?,
    val departure: Long,
    val scheduledDeparture: Long,
    val arrival: Long,
    val scheduledArrival: Long,
    val realTime: Boolean,
    val cancelled: Boolean,
    val stops: Int,
    /** Meters; walks only. */
    val distance: Double?,
    val points: List<LatLng>,
    /** Turn by turn; walks, rides and drives only. */
    val steps: List<TransitStep> = emptyList(),
) {
    val isWalk get() = mode == "WALK"
    /** On one's own way rather than on a line: walking, cycling, driving or by taxi. */
    val isStreet get() = mode in STREET_MODES
    val delayMinutes get() = ((departure - scheduledDeparture) / 60_000).toInt()
}

data class Itinerary(val legs: List<TransitLeg>, val departure: Long, val arrival: Long, val transfers: Int) {
    val rides get() = legs.filterNot { it.isStreet }
}

private val STREET_MODES = setOf("WALK", "BIKE", "RENTAL", "CAR", "TAXI")

/** Other ways than walking to get to the first stop, or on from the last one. */
enum class Access(val label: String) { Bike("Bike"), Car("Car"), Taxi("Taxi") }

/** How a journey may start and end besides walking, which is always allowed. */
data class TransitAccess(val start: Set<Access> = emptySet(), val end: Set<Access> = emptySet()) {
    val isDefault get() = start.isEmpty() && end.isEmpty()
}

data class TransitPlan(val itineraries: List<Itinerary>, val nextPage: String?)

data class Departure(
    val tripId: String?,
    val mode: String,
    val line: String,
    val headsign: String,
    val color: String?,
    val textColor: String?,
    val stopName: String,
    val stopId: String?,
    val track: String?,
    val time: Long,
    val scheduled: Long,
    val realTime: Boolean,
    val cancelled: Boolean,
) {
    val delayMinutes get() = ((time - scheduled) / 60_000).toInt()
}

/** One stop of a [Trip]. Times are epoch millis, live where the operator shares them. */
data class TripStop(
    val name: String,
    val stopId: String?,
    val lat: Double,
    val lon: Double,
    val track: String?,
    val arrival: Long?,
    val scheduledArrival: Long?,
    val departure: Long?,
    val scheduledDeparture: Long?,
    val cancelled: Boolean,
) {
    /** When the vehicle is here: departure, or arrival at the last stop. */
    val time get() = departure ?: arrival ?: 0L
    val scheduled get() = scheduledDeparture ?: scheduledArrival ?: time
    val delayMinutes get() = ((time - scheduled) / 60_000).toInt()
}

/** Where a vehicle is along its trip, going by the times. */
sealed interface TripPosition {
    data object NotStarted : TripPosition
    data class AtStop(val index: Int) : TripPosition
    /** Left stop [after] and is [fraction] of the way to the next one. */
    data class Between(val after: Int, val fraction: Double) : TripPosition
    data object Finished : TripPosition
}

/** A whole bus, train or tram run, from its first stop to its last. */
data class Trip(
    val mode: String,
    val line: String?,
    val headsign: String?,
    val agency: String?,
    val color: String?,
    val textColor: String?,
    val realTime: Boolean,
    val cancelled: Boolean,
    val stops: List<TripStop>,
    val points: List<LatLng>,
) {
    fun position(now: Long): TripPosition {
        if (stops.isEmpty() || now < stops.first().time) return TripPosition.NotStarted
        val i = stops.indexOfLast { (it.arrival ?: it.time) <= now }
        if (i >= stops.lastIndex) return TripPosition.Finished
        val leave = stops[i].time
        if (now < leave) return TripPosition.AtStop(i)
        val next = stops[i + 1].arrival ?: stops[i + 1].time
        val fraction = if (next > leave) (now - leave).toDouble() / (next - leave) else 1.0
        return TripPosition.Between(i, fraction.coerceIn(0.0, 1.0))
    }

    /** Where on the map [position] is: on the line between the stops, by distance. */
    fun locate(position: TripPosition): LatLng? = when (position) {
        TripPosition.NotStarted -> stops.firstOrNull()?.let { LatLng(it.lat, it.lon) }
        TripPosition.Finished -> stops.lastOrNull()?.let { LatLng(it.lat, it.lon) }
        is TripPosition.AtStop -> stops[position.index].let { LatLng(it.lat, it.lon) }
        is TripPosition.Between -> {
            val from = stopPointIndex[position.after]
            val to = stopPointIndex[position.after + 1]
            if (points.size < 2 || to <= from) {
                val a = stops[position.after]
                val b = stops[position.after + 1]
                LatLng(a.lat + (b.lat - a.lat) * position.fraction, a.lon + (b.lon - a.lon) * position.fraction)
            } else {
                val target = along[from] + (along[to] - along[from]) * position.fraction
                val k = (from + 1..to).first { along[it] >= target }
                val t = (target - along[k - 1]) / (along[k] - along[k - 1]).coerceAtLeast(1e-6)
                LatLng(
                    points[k - 1].latitude + (points[k].latitude - points[k - 1].latitude) * t,
                    points[k - 1].longitude + (points[k].longitude - points[k - 1].longitude) * t,
                )
            }
        }
    }

    /** Running distance along [points], in degrees; only compared with itself. */
    private val along by lazy {
        DoubleArray(points.size).also { a ->
            for (i in 1 until points.size) {
                a[i] = a[i - 1] + hypot(
                    points[i].latitude - points[i - 1].latitude,
                    (points[i].longitude - points[i - 1].longitude) * cos(Math.toRadians(points[i].latitude)),
                )
            }
        }
    }

    /** For each stop, the closest point of the line, searching onward from the previous stop. */
    private val stopPointIndex by lazy {
        var start = 0
        stops.map { stop ->
            val best = (start until points.size).minByOrNull {
                hypot(points[it].latitude - stop.lat, points[it].longitude - stop.lon)
            } ?: start
            start = best
            best
        }
    }
}

/**
 * Public transport journeys and departures from Transitous (https://transitous.org), a free
 * community-run MOTIS server. In the Netherlands it uses the OpenOV feed: NS, GVB, RET, HTM,
 * Arriva, Qbuzz and the rest, with live delays where the operator shares them.
 */
object Transitous {
    private const val BASE = "https://api.transitous.org/api/v1"

    /**
     * Journeys from one place to another, getting to and from the stops on foot or as [access]
     * allows. [page] is a [TransitPlan.nextPage] of the same request, for later (or earlier) ones.
     */
    suspend fun plan(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        time: Long?,
        arriveBy: Boolean,
        page: String? = null,
        access: TransitAccess = TransitAccess(),
    ): TransitPlan = coroutineScope {
        // Parking the car and being dropped off both come back as a drive, so a journey can
        // only say which it was when they're asked for separately: one request for each.
        fun drives(options: Set<Access>) = listOf(Access.Car, Access.Taxi).filter { it in options }.ifEmpty { listOf(null) }
        val variants = drives(access.start).flatMap { start -> drives(access.end).map { end -> start to end } }
        val pages = page?.split(PAGE_SEPARATOR)
        val plans = variants.mapIndexed { i, (startDrive, endDrive) ->
            val variantPage = pages?.getOrNull(i)
            if (pages != null && variantPage.isNullOrEmpty()) return@mapIndexed null
            async {
                runCatching { planOne(fromLat, fromLon, toLat, toLon, time, arriveBy, variantPage, access, startDrive, endDrive) }
            }
        }.map { it?.await() }
        val found = plans.mapNotNull { it?.getOrNull() }
        if (found.isEmpty()) {
            throw plans.firstNotNullOfOrNull { it?.exceptionOrNull() } ?: RouteException("Couldn't plan a public transport trip.")
        }
        // The same walk-only journey can come from several requests.
        val seen = HashSet<List<Any?>>()
        TransitPlan(
            itineraries = found.flatMap { it.itineraries }
                .filter { seen.add(listOf(it.departure, it.arrival) + it.legs.map { leg -> leg.mode to leg.tripId }) }
                .sortedBy { if (arriveBy) -it.departure else it.arrival },
            nextPage = plans.map { it?.getOrNull()?.nextPage.orEmpty() }
                .takeIf { cursors -> cursors.any { it.isNotEmpty() } }
                ?.joinToString(PAGE_SEPARATOR),
        )
    }

    private suspend fun planOne(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        time: Long?,
        arriveBy: Boolean,
        page: String?,
        access: TransitAccess,
        startDrive: Access?,
        endDrive: Access?,
    ): TransitPlan {
        val pre = listOfNotNull(
            "WALK",
            "BIKE".takeIf { Access.Bike in access.start },
            when (startDrive) { Access.Car -> "CAR_PARKING"; Access.Taxi -> "CAR_DROPOFF"; else -> null },
        )
        val post = listOfNotNull(
            "WALK",
            "BIKE".takeIf { Access.Bike in access.end },
            when (endDrive) { Access.Car -> "CAR"; Access.Taxi -> "CAR_DROPOFF"; else -> null },
        )
        val url = buildString {
            append("$BASE/plan?fromPlace=$fromLat,$fromLon&toPlace=$toLat,$toLon")
            append("&time=").append(Instant.ofEpochMilli(time ?: System.currentTimeMillis()))
            if (arriveBy) append("&arriveBy=true")
            if (page != null) append("&pageCursor=").append(URLEncoder.encode(page, "UTF-8"))
            // Cycling or driving reaches stations further away than the 15 minutes' walk MOTIS looks at.
            if (pre.size > 1) append("&preTransitModes=").append(pre.joinToString(",")).append("&maxPreTransitTime=$ACCESS_SECONDS")
            if (post.size > 1) append("&postTransitModes=").append(post.joinToString(",")).append("&maxPostTransitTime=$ACCESS_SECONDS")
            if (Access.Bike in access.start && Access.Bike in access.end) append("&directModes=WALK,BIKE")
        }
        val json = try {
            JSONObject(httpGet(url))
        } catch (e: HttpException) {
            throw RouteException("Couldn't plan a public transport trip.")
        }
        fun parse(it: JSONObject) = parseItinerary(it).asked(startDrive, endDrive)
        val transit = json.optJSONArray("itineraries").objects().map(::parse)
        // Walking all the way is offered separately when the destination is close.
        val direct = if (page == null) json.optJSONArray("direct").objects().map(::parse) else emptyList()
        return TransitPlan(
            itineraries = transit + direct,
            nextPage = json.optString(if (arriveBy) "previousPageCursor" else "nextPageCursor").ifBlank { null },
        )
    }

    /** Calls a drive before the first ride, or after the last, a taxi when that was asked for. */
    private fun Itinerary.asked(startDrive: Access?, endDrive: Access?): Itinerary {
        val first = legs.indexOfFirst { !it.isStreet }
        if (first < 0) return this
        val last = legs.indexOfLast { !it.isStreet }
        return copy(legs = legs.mapIndexed { i, leg ->
            val taxi = leg.mode == "CAR" && ((i < first && startDrive == Access.Taxi) || (i > last && endDrive == Access.Taxi))
            if (taxi) leg.copy(mode = "TAXI") else leg
        })
    }

    /** Upcoming departures from every stop within [radius] meters. */
    suspend fun departures(lat: Double, lon: Double, radius: Int, count: Int = 60): List<Departure> {
        val json = JSONObject(httpGet("$BASE/stoptimes?center=$lat,$lon&radius=$radius&n=$count"))
        return json.optJSONArray("stopTimes").objects().map { s ->
            val place = s.getJSONObject("place")
            val time = place.time("departure") ?: place.time("arrival") ?: 0L
            Departure(
                tripId = s.str("tripId"),
                mode = s.optString("mode"),
                line = s.str("displayName") ?: s.str("routeShortName") ?: s.str("tripShortName") ?: "",
                headsign = s.str("headsign") ?: "",
                color = s.str("routeColor")?.let { "#$it" },
                textColor = s.str("routeTextColor")?.let { "#$it" },
                stopName = place.optString("name"),
                stopId = place.str("stopId"),
                track = place.str("track"),
                time = time,
                scheduled = place.time("scheduledDeparture") ?: place.time("scheduledArrival") ?: time,
                realTime = s.optBoolean("realTime"),
                cancelled = s.optBoolean("cancelled") || s.optBoolean("tripCancelled"),
            )
        }
    }

    /** Every stop of one run of a line, with live times. */
    suspend fun trip(tripId: String): Trip {
        val json = JSONObject(httpGet("$BASE/trip?tripId=" + URLEncoder.encode(tripId, "UTF-8")))
        val leg = json.getJSONArray("legs").getJSONObject(0)
        val places = listOf(leg.getJSONObject("from")) +
            leg.optJSONArray("intermediateStops").objects() +
            listOf(leg.getJSONObject("to"))
        val geometry = leg.optJSONObject("legGeometry")
        return Trip(
            mode = leg.optString("mode"),
            line = leg.str("displayName") ?: leg.str("routeShortName") ?: leg.str("tripShortName"),
            headsign = leg.str("headsign"),
            agency = leg.str("agencyName"),
            color = leg.str("routeColor")?.let { "#$it" },
            textColor = leg.str("routeTextColor")?.let { "#$it" },
            realTime = leg.optBoolean("realTime"),
            cancelled = leg.optBoolean("cancelled"),
            stops = places.map { p ->
                TripStop(
                    name = p.optString("name"),
                    stopId = p.str("stopId"),
                    lat = p.getDouble("lat"),
                    lon = p.getDouble("lon"),
                    track = p.str("track"),
                    arrival = p.time("arrival"),
                    scheduledArrival = p.time("scheduledArrival"),
                    departure = p.time("departure"),
                    scheduledDeparture = p.time("scheduledDeparture"),
                    cancelled = p.optBoolean("cancelled"),
                )
            },
            points = geometry?.let { g ->
                decodePolyline(g.optString("points"), g.optString("precision", "7").toIntOrNull() ?: 7)
            }.orEmpty(),
        )
    }

    /** Places, addresses and stops matching [text], biased toward the given point. */
    suspend fun geocode(text: String, nearLat: Double?, nearLon: Double?, bias: Int = 5, count: Int = 12): List<SearchHit> {
        val url = buildString {
            append("$BASE/geocode?text=").append(URLEncoder.encode(text, "UTF-8"))
            if (nearLat != null && nearLon != null) append("&place=$nearLat,$nearLon&placeBias=$bias")
            append("&numResults=$count")
        }
        return JSONArray(httpGet(url)).objects().take(count).mapIndexedNotNull { i, m ->
            parseMatch(m)?.let { SearchHit(it, matchContext(m), i) }
        }
    }

    /** Street, postcode and every area a match lies in, for ranking; "station" for stations. */
    private fun matchContext(m: JSONObject): String = (
        listOfNotNull(m.str("street"), m.str("houseNumber"), m.str("zip")) +
            m.optJSONArray("areas").objects().mapNotNull { it.str("name") } +
            listOfNotNull("station".takeIf { m.modes().any { transitKind(it) in setOf(TransitKind.Train, TransitKind.Metro) } })
        ).joinToString(" ")

    suspend fun reverseGeocode(lat: Double, lon: Double): Place? =
        JSONArray(httpGet("$BASE/reverse-geocode?place=$lat,$lon")).objects().firstNotNullOfOrNull(::parseMatch)

    private fun parseMatch(m: JSONObject): Place? {
        val name = m.str("name") ?: return null
        val areas = m.optJSONArray("areas").objects()
        // The area MOTIS would show by default is the town; add the province for context.
        val town = areas.firstOrNull { it.optBoolean("default") }?.str("name")
            ?: areas.lastOrNull()?.str("name")
        val region = areas.firstOrNull { it.optDouble("adminLevel") == 4.0 }?.str("name")
        val street = listOfNotNull(m.str("street"), m.str("houseNumber")).joinToString(" ").ifBlank { null }
        val subtitle = listOfNotNull(
            street?.takeIf { it != name },
            m.str("zip"),
            town?.takeIf { it != name },
            region?.takeIf { it != name && it != town },
        ).joinToString(", ")
        // Categories look like "supermarket_14"; the number is a zoom level.
        val category = m.str("category")?.substringBeforeLast('_')
        val (key, type) = when (m.optString("type")) {
            "STOP" -> return stopPlace(name, m.getDouble("lat"), m.getDouble("lon"), m.modes().map(::transitKind).toSet(), town)
            "ADDRESS" -> "building" to null
            else -> null to category
        }
        return Place(name, subtitle, m.getDouble("lat"), m.getDouble("lon"), type, key)
    }

    private fun parseItinerary(it: JSONObject): Itinerary {
        val legs = it.getJSONArray("legs").objects().map { leg ->
            val from = leg.getJSONObject("from")
            val to = leg.getJSONObject("to")
            val geometry = leg.optJSONObject("legGeometry")
            TransitLeg(
                mode = leg.optString("mode"),
                tripId = leg.str("tripId"),
                line = leg.str("displayName") ?: leg.str("routeShortName"),
                headsign = leg.str("headsign"),
                agency = leg.str("agencyName"),
                color = leg.str("routeColor")?.let { "#$it" },
                textColor = leg.str("routeTextColor")?.let { "#$it" },
                fromName = from.optString("name"),
                fromStopId = from.str("stopId"),
                fromTrack = from.str("track"),
                toName = to.optString("name"),
                toTrack = to.str("track"),
                departure = leg.time("startTime") ?: 0L,
                scheduledDeparture = leg.time("scheduledStartTime") ?: leg.time("startTime") ?: 0L,
                arrival = leg.time("endTime") ?: 0L,
                scheduledArrival = leg.time("scheduledEndTime") ?: leg.time("endTime") ?: 0L,
                realTime = leg.optBoolean("realTime"),
                cancelled = leg.optBoolean("cancelled"),
                stops = leg.optJSONArray("intermediateStops")?.length() ?: 0,
                distance = leg.optDouble("distance").takeUnless { d -> d.isNaN() },
                points = geometry?.let { g ->
                    decodePolyline(g.optString("points"), g.optString("precision", "7").toIntOrNull() ?: 7)
                }.orEmpty(),
                steps = leg.optJSONArray("steps").objects().mapNotNull(::parseStep),
            )
        }
        return Itinerary(
            legs = legs,
            departure = it.time("startTime") ?: legs.firstOrNull()?.departure ?: 0L,
            arrival = it.time("endTime") ?: legs.lastOrNull()?.arrival ?: 0L,
            transfers = it.optInt("transfers"),
        )
    }

    private fun parseStep(step: JSONObject): TransitStep? {
        val polyline = step.optJSONObject("polyline") ?: return null
        val start = decodePolyline(polyline.optString("points"), polyline.optInt("precision", 7)).firstOrNull() ?: return null
        return TransitStep(
            direction = step.optString("relativeDirection"),
            street = step.str("streetName"),
            exit = step.str("exit"),
            start = start,
        )
    }

    private const val PAGE_SEPARATOR = "\n"
    /** The longest drive or ride to or from a stop that's looked for, in seconds. */
    private const val ACCESS_SECONDS = 1800

    private fun JSONObject.modes(): List<String> =
        optJSONArray("modes")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()

    private fun JSONObject.str(key: String): String? = optString(key).takeIf { has(key) && it.isNotBlank() }
    private fun JSONObject.time(key: String): Long? = str(key)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    private fun JSONArray?.objects(): List<JSONObject> = this?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
}

/** A stop as a [Place]; train stations get a wider departure radius than a single bus stop. */
fun stopPlace(name: String, lat: Double, lon: Double, kinds: Set<TransitKind>, town: String? = null) = Place(
    name = name,
    subtitle = listOfNotNull(
        kinds.sorted().joinToString(" · ") { it.label }.ifBlank { null },
        town?.takeIf { !name.startsWith(it) },
    ).joinToString(", "),
    lat = lat,
    lon = lon,
    type = if (TransitKind.Train in kinds || TransitKind.Metro in kinds) "station" else "stop",
    key = "public_transport",
)

/** OSM places that are stops or stations, and how far around them to look for departures. */
fun Place.departureRadius(): Int? = when {
    key == "railway" && type in setOf("station", "halt") -> 300
    key == "public_transport" && type == "station" -> 300
    key == "railway" && type in setOf("tram_stop", "subway_entrance", "stop", "platform") -> 120
    key == "public_transport" -> 120
    key == "highway" && type == "bus_stop" -> 80
    key == "amenity" && type in setOf("bus_station", "ferry_terminal") -> 200
    else -> null
}
