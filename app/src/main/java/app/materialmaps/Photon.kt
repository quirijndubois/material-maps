package app.materialmaps

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

data class Place(
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val type: String? = null,
    /** OSM key such as `amenity` or `shop`; [type] is its value. */
    val key: String? = null,
)

/**
 * Place search for the search bar and dropped pins. Transitous (MOTIS, built on OSM) answers in
 * ~150 ms and also knows transit stops; Photon is the fallback when it can't be reached.
 */
object Geocoder {
    private const val NEARBY_KM = 25.0
    private val TOWN_TYPES = setOf("place", "town", "city", "village", "state", "place_capital", "suburb")
    private val coordinates = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*[, ]\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")

    private val cache = object : LinkedHashMap<String, List<Place>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Place>>) = size > 64
    }

    private fun cacheKey(query: String, nearLat: Double?, nearLon: Double?): String {
        // Round the bias point to roughly a kilometre so small pans still hit the cache.
        fun round(v: Double?) = v?.let { (it * 100).roundToInt() }
        return "${query.trim().lowercase()}|${round(nearLat)}|${round(nearLon)}"
    }

    fun cached(query: String, nearLat: Double?, nearLon: Double?): List<Place>? =
        synchronized(cache) { cache[cacheKey(query, nearLat, nearLon)] }

    suspend fun search(query: String, nearLat: Double?, nearLon: Double?, zoom: Double?): List<Place> {
        coordinates.matchEntire(query)?.let { m ->
            val lat = m.groupValues[1].toDouble()
            val lon = m.groupValues[2].toDouble()
            if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                return listOf(Place("Coordinates", Photon.formatCoordinates(lat, lon), lat, lon))
            }
        }
        val places = try {
            searchTransitous(query.trim(), nearLat, nearLon)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Photon.search(query, nearLat, nearLon, zoom)
        }.withoutDuplicates()
        synchronized(cache) { cache[cacheKey(query, nearLat, nearLon)] = places }
        return places
    }

    /**
     * Two searches at once: a strongly local one, so "albert" finds every Albert Heijn around,
     * and a broad one, so "utrecht" still finds the city from Amsterdam.
     */
    private suspend fun searchTransitous(query: String, nearLat: Double?, nearLon: Double?): List<Place> {
        if (nearLat == null || nearLon == null) return Transitous.geocode(query, null, null)
        return coroutineScope {
            val nearby = async { Transitous.geocode(query, nearLat, nearLon, bias = 20, count = 30) }
            val broad = async { Transitous.geocode(query, nearLat, nearLon) }
            val local = nearby.await()
                .map { it to distanceKm(nearLat, nearLon, it.lat, it.lon) }
                .filter { (_, km) -> km < NEARBY_KM }
                .sortedBy { (_, km) -> km }
                .map { (place, _) -> place }
            val wide = broad.await()
            // A town or city named exactly like the query beats nearby lookalikes, unless
            // something nearby has that exact name too.
            val exactNearby = local.any { it.name.equals(query, ignoreCase = true) }
            val town = wide.firstOrNull {
                it.name.equals(query, ignoreCase = true) && it.type in TOWN_TYPES
            }?.takeUnless { exactNearby }
            listOfNotNull(town) + local + wide
        }.withoutDuplicates()
    }

    suspend fun reverse(lat: Double, lon: Double): Place? = try {
        Transitous.reverseGeocode(lat, lon)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Photon.reverse(lat, lon)
    }
}

/**
 * Search and reverse geocoding with Photon (https://photon.komoot.io), which is built on
 * OpenStreetMap data and allows search-as-you-type.
 */
object Photon {
    private const val BASE = "https://photon.komoot.io"

    // Photon only knows a few languages and rejects the rest; "default" gives local names.
    private val language = Locale.getDefault().language.takeIf { it in setOf("en", "de", "fr") }

    suspend fun search(query: String, nearLat: Double?, nearLon: Double?, zoom: Double?): List<Place> {
        val url = buildString {
            append("$BASE/api/?limit=12&q=")
            append(URLEncoder.encode(query.trim(), "UTF-8"))
            if (nearLat != null && nearLon != null) {
                append("&lat=$nearLat&lon=$nearLon")
                // Zoomed out, the bias should cover a wider area.
                if (zoom != null) append("&zoom=${zoom.roundToInt().coerceIn(4, 18)}")
            }
            if (language != null) append("&lang=$language")
        }
        return parse(httpGet(url))
    }

    suspend fun reverse(lat: Double, lon: Double): Place? =
        parse(httpGet("$BASE/reverse?lat=$lat&lon=$lon")).firstOrNull()

    fun formatCoordinates(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lon)

    private fun parse(json: String): List<Place> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val feature = features.getJSONObject(i)
            val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
                ?: return@mapNotNull null
            val props = feature.optJSONObject("properties") ?: JSONObject()
            fun prop(key: String): String? = props.optString(key).takeIf { it.isNotBlank() }

            val street = listOfNotNull(prop("street"), prop("housenumber"))
                .joinToString(" ").ifBlank { null }
            val city = prop("city") ?: prop("town") ?: prop("village") ?: prop("district")
            val name = prop("name") ?: street ?: city ?: return@mapNotNull null
            val locality = listOfNotNull(prop("postcode"), city).joinToString(" ").ifBlank { null }
            val subtitle = listOfNotNull(
                street?.takeIf { it != name },
                locality?.takeIf { it != name },
                prop("country")?.takeIf { it != name },
            ).joinToString(", ")

            Place(
                name = name,
                subtitle = subtitle,
                lat = coords.getDouble(1),
                lon = coords.getDouble(0),
                type = prop("osm_value")?.takeIf { it != "yes" },
                key = prop("osm_key"),
            )
        }
    }
}

/** The last few places picked from search, newest first. */
object RecentPlaces {
    private const val PREFS = "recent-places"
    private const val KEY = "places"
    private const val MAX = 8

    fun load(context: Context): List<Place> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Place(
                name = o.getString("name"),
                subtitle = o.optString("subtitle"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                type = o.optString("type").ifBlank { null },
                key = o.optString("key").ifBlank { null },
            )
        }
    }.getOrDefault(emptyList())

    fun add(context: Context, place: Place): List<Place> {
        val places = (listOf(place) + load(context).filterNot { it.isSameAs(place) }).take(MAX)
        val array = JSONArray()
        places.forEach { p ->
            array.put(
                JSONObject()
                    .put("name", p.name)
                    .put("subtitle", p.subtitle)
                    .put("lat", p.lat)
                    .put("lon", p.lon)
                    .put("type", p.type ?: "")
                    .put("key", p.key ?: "")
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
        return places
    }

    fun clear(context: Context) = prefs(context).edit().remove(KEY).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val result = FloatArray(1)
    android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, result)
    return result[0] / 1000.0
}

/**
 * Drops repeats of the same name and address close together (one building often has several
 * entrances), but keeps branches of a chain, like every Albert Heijn, that are further apart.
 */
private fun List<Place>.withoutDuplicates(): List<Place> {
    val kept = mutableListOf<Place>()
    forEach { p ->
        val repeat = kept.any {
            it.name == p.name && it.subtitle == p.subtitle && distanceKm(it.lat, it.lon, p.lat, p.lon) < 0.3
        }
        if (!repeat) kept += p
    }
    return kept
}
