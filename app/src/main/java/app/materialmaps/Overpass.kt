package app.materialmaps

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.PedalBike
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CancellationException
import java.io.IOException
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Kinds of places to look for on the map, as OpenStreetMap tags them: [key]=[value] for
 * Overpass, and [subclasses] as the map's own points of interest call them.
 */
enum class Category(
    val label: String,
    val icon: ImageVector,
    val key: String,
    val value: String,
    val subclasses: Set<String> = setOf(value),
    val needsName: Boolean = true,
) {
    Restaurants("Restaurants", Icons.Outlined.Restaurant, "amenity", "restaurant"),
    Cafes("Cafés", Icons.Outlined.LocalCafe, "amenity", "cafe"),
    Groceries("Groceries", Icons.Outlined.LocalGroceryStore, "shop", "supermarket", setOf("supermarket", "convenience", "greengrocer")),
    Fuel("Fuel", Icons.Outlined.LocalGasStation, "amenity", "fuel", needsName = false),
    Charging("Charging", Icons.Outlined.EvStation, "amenity", "charging_station", needsName = false),
    Parking("Parking", Icons.Outlined.LocalParking, "amenity", "parking", needsName = false),
    Pharmacy("Pharmacy", Icons.Outlined.LocalPharmacy, "amenity", "pharmacy"),
    Bars("Bars", Icons.Outlined.LocalBar, "amenity", "bar", setOf("bar", "pub", "biergarten")),
    FastFood("Fast food", Icons.Outlined.Fastfood, "amenity", "fast_food"),
    Bakeries("Bakeries", Icons.Outlined.BakeryDining, "shop", "bakery"),
    Hotels("Hotels", Icons.Outlined.Hotel, "tourism", "hotel", setOf("hotel", "hostel", "guest_house", "motel")),
    Atms("ATMs", Icons.Outlined.LocalAtm, "amenity", "atm", needsName = false),
    Toilets("Toilets", Icons.Outlined.Wc, "amenity", "toilets", needsName = false),
    Hospitals("Hospitals", Icons.Outlined.LocalHospital, "amenity", "hospital"),
    BikeParking("Bike parking", Icons.Outlined.PedalBike, "amenity", "bicycle_parking", needsName = false);

    val kind get() = PlaceKind(label, key, value, subclasses, needsName)
}

/** Opening hours, phone and website of a place, from OpenStreetMap. */
data class PlaceDetails(
    val openingHours: String?,
    val phone: String?,
    val website: String?,
    /** Links to its Wikipedia article, like `nl:Domtoren`, or its Wikidata item. */
    val wikipedia: String? = null,
    val wikidata: String? = null,
) {
    val isEmpty get() = openingHours == null && phone == null && website == null && wikipedia == null && wikidata == null
}

/**
 * Raw OpenStreetMap data from the public Overpass API (https://overpass-api.de), for what the
 * geocoders don't return: everything of one kind in view, and a place's opening hours.
 */
object Overpass {
    // The main server is often at capacity; the others are public mirrors with the same API.
    private val SERVERS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )

    /** Runs [query] on the first server that answers. */
    private suspend fun run(query: String): JSONObject {
        var failure: Exception? = null
        for (server in SERVERS) {
            try {
                return JSONObject(httpGet("$server?data=" + URLEncoder.encode(query, "UTF-8")))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        throw failure ?: IOException("No Overpass server")
    }

    /** Places of [category] within [bounds], at most [limit]. */
    suspend fun find(category: PlaceKind, bounds: LatLngBounds, limit: Int = 80): List<Place> {
        val box = "${bounds.latitudeSouth},${bounds.longitudeWest},${bounds.latitudeNorth},${bounds.longitudeEast}"
        val name = if (category.needsName) "[\"name\"]" else ""
        val query = "[out:json][timeout:20];nwr[\"${category.key}\"=\"${category.value}\"]$name($box);out center tags $limit;"
        return places(run(query), category)
    }

    private fun places(answer: JSONObject, category: PlaceKind): List<Place> {
        val elements = answer.optJSONArray("elements") ?: return emptyList()
        return (0 until elements.length()).mapNotNull { i ->
            val e = elements.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: return@mapNotNull null
            val center = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.getDouble("lat") else center?.optDouble("lat") ?: return@mapNotNull null
            val lon = if (e.has("lon")) e.getDouble("lon") else center?.optDouble("lon") ?: return@mapNotNull null
            Place(
                name = tags.optString("name").ifBlank { tags.optString("brand").ifBlank { category.label.removeSuffix("s") } },
                subtitle = listOf(
                    listOf(tags.optString("addr:street"), tags.optString("addr:housenumber")).filter { it.isNotBlank() }.joinToString(" "),
                    tags.optString("addr:city"),
                ).filter { it.isNotBlank() }.joinToString(", "),
                lat = lat,
                lon = lon,
                type = category.value,
                key = category.key,
            )
        }
    }

    /** Places of [kind] within [radius] meters of the line through [path], at most [limit]. */
    suspend fun alongPath(kind: PlaceKind, path: List<LatLng>, radius: Int, limit: Int = 120): List<Place> {
        val line = path.joinToString(",") { String.format(Locale.US, "%.5f,%.5f", it.latitude, it.longitude) }
        val name = if (kind.needsName) "[\"name\"]" else ""
        val query = "[out:json][timeout:25];nwr[\"${kind.key}\"=\"${kind.value}\"]$name(around:$radius,$line);out center tags $limit;"
        return places(run(query), kind)
    }

    /** Details of the OpenStreetMap feature named like [place] closest to it, if there is one. */
    suspend fun details(place: Place): PlaceDetails? {
        val name = place.name.replace("\\", "\\\\").replace("\"", "\\\"")
        val query = "[out:json][timeout:10];nwr(around:60,${place.lat},${place.lon})[\"name\"=\"$name\"];out tags 1;"
        val tags = run(query).optJSONArray("elements")?.optJSONObject(0)?.optJSONObject("tags") ?: return null
        fun tag(vararg keys: String) = keys.firstNotNullOfOrNull { k -> tags.optString(k).takeIf { it.isNotBlank() } }
        return PlaceDetails(
            openingHours = tag("opening_hours"),
            phone = tag("phone", "contact:phone")?.substringBefore(';')?.trim(),
            website = tag("website", "contact:website", "url")?.substringBefore(';')?.trim(),
            wikipedia = tag("wikipedia"),
            wikidata = tag("wikidata"),
        ).takeUnless { it.isEmpty }
    }
}

/**
 * Place details from Nominatim (https://nominatim.openstreetmap.org), OpenStreetMap's own
 * search: it answers in a fraction of a second with the same tags Overpass has. Its usage
 * policy allows about one request a second, which looking up the place on screen stays under.
 */
object Nominatim {
    suspend fun details(place: Place): PlaceDetails? {
        // Look for the name in a small box around the place, and take the closest match.
        val d = 0.002
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&extratags=1&limit=5&bounded=1" +
            "&q=" + URLEncoder.encode(place.name, "UTF-8") +
            "&viewbox=${place.lon - d * 1.5},${place.lat + d},${place.lon + d * 1.5},${place.lat - d}"
        val results = org.json.JSONArray(httpGet(url))
        val match = (0 until results.length()).map { results.getJSONObject(it) }
            .minByOrNull { distanceKm(place.lat, place.lon, it.optDouble("lat"), it.optDouble("lon")) }
            ?: return null
        val tags = match.optJSONObject("extratags") ?: return null
        fun tag(vararg keys: String) = keys.firstNotNullOfOrNull { k -> tags.optString(k).takeIf { it.isNotBlank() } }
        return PlaceDetails(
            openingHours = tag("opening_hours"),
            phone = tag("phone", "contact:phone")?.substringBefore(';')?.trim(),
            website = tag("website", "contact:website", "url")?.substringBefore(';')?.trim(),
            wikipedia = tag("wikipedia"),
            wikidata = tag("wikidata"),
        ).takeUnless { it.isEmpty }
    }
}

/** Whether a place is open, and when that changes, as far as [OpeningHours] could tell. */
data class OpenState(val open: Boolean, val text: String)

/**
 * Reads the common forms of OpenStreetMap's opening_hours, like "Mo-Fr 09:00-18:00; Sa
 * 10:00-17:00; Su off" or "24/7". Anything fancier (holidays, months, week numbers) gives
 * null, and the raw text is shown instead.
 */
object OpeningHours {
    private val dayNames = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    /** Minutes from Monday 00:00 that it's open, per weekday, as [start, end) pairs. */
    private fun parse(text: String): List<List<IntRange>>? {
        val spec = text.trim()
        if (spec == "24/7") return List(7) { listOf(0 until 24 * 60) }
        val week = MutableList(7) { emptyList<IntRange>() }
        for (rule in spec.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            val match = Regex("""^((?:(?:Mo|Tu|We|Th|Fr|Sa|Su)(?:-(?:Mo|Tu|We|Th|Fr|Sa|Su))?,?)+)?\s*(.*)$""").find(rule) ?: return null
            val daysPart = match.groupValues[1]
            val timesPart = match.groupValues[2].trim()
            val days = if (daysPart.isEmpty()) (0..6).toList() else daysPart.split(',').filter { it.isNotEmpty() }.flatMap { d ->
                val ends = d.split('-').map { dayNames.indexOf(it) }
                if (ends.any { it < 0 }) return null
                if (ends.size == 1) listOf(ends[0])
                // Ranges can wrap round the week, like Sa-Mo.
                else buildList {
                    var day = ends[0]
                    add(day)
                    while (day != ends[1]) {
                        day = (day + 1) % 7
                        add(day)
                    }
                }
            }
            val ranges = when {
                timesPart == "off" || timesPart == "closed" -> emptyList()
                timesPart.isEmpty() || timesPart == "24/7" -> listOf(0 until 24 * 60)
                else -> timesPart.split(',').map { span ->
                    val (from, to) = span.trim().split('-').takeIf { it.size == 2 }?.map(::minutes) ?: return null
                    if (from == null || to == null) return null
                    // Past midnight, like 18:00-02:00, runs on into the next day.
                    from until (if (to <= from) to + 24 * 60 else to)
                }
            }
            // A later rule replaces earlier ones for the days it names.
            days.forEach { week[it] = ranges }
        }
        return week
    }

    private fun minutes(time: String): Int? {
        val parts = time.trim().split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    /** The hours for each day, Monday first, like "07:00–22:00" or "Closed"; null if unreadable. */
    fun week(text: String): List<String>? = parse(text)?.map { ranges ->
        when {
            ranges.isEmpty() -> "Closed"
            ranges.size == 1 && ranges[0].first == 0 && ranges[0].last + 1 >= 24 * 60 -> "Open 24 hours"
            else -> ranges.joinToString(", ") { r ->
                fun clock(m: Int) = LocalTime.of((m % 1440) / 60, m % 60).toString()
                "${clock(r.first)}–${clock(r.last + 1)}"
            }
        }
    }

    fun state(text: String, now: LocalDateTime = LocalDateTime.now()): OpenState? {
        val week = parse(text) ?: return null
        // Every open stretch in minutes from this Monday, also for next week, to look ahead.
        val spans = (0 until 14).flatMap { d -> week[d % 7].map { (it.first + d * 1440)..(it.last + 1 + d * 1440) } }
            .sortedBy { it.first }
        val today = now.dayOfWeek.value - 1
        val minute = today * 1440 + now.hour * 60 + now.minute
        val current = spans.firstOrNull { minute in it.first until it.last }
        fun clock(m: Int) = LocalTime.of((m % 1440) / 60, m % 60).toString()
        fun day(m: Int): String {
            val d = m / 1440 - today
            return when (d) {
                0 -> ""
                1 -> " tomorrow"
                else -> " " + DayOfWeek.of((m / 1440) % 7 + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
        }
        if (current != null) {
            // Back-to-back stretches (like up to midnight and on from it) count as one.
            var end = current.last
            while (true) {
                val next = spans.firstOrNull { it.first == end } ?: break
                end = next.last
            }
            return if (end - minute >= 7 * 1440) OpenState(true, "Open 24 hours")
            else OpenState(true, "Open · closes ${clock(end)}${day(end)}")
        }
        val next = spans.firstOrNull { it.first > minute } ?: return OpenState(false, "Closed")
        return OpenState(false, "Closed · opens ${clock(next.first)}${day(next.first)}")
    }
}
