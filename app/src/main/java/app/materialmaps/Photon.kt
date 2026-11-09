package app.materialmaps

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
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
    /**
     * How well known the place is, 0 to 1, where the data says: from how many languages its
     * name is in and how the map ranks it. Used to put notable places first among results.
     */
    val prominence: Double = 0.0,
    /** The map's own kind of place (an OpenMapTiles class like `shop`), for places tapped on it. */
    val poiClass: String? = null,
)

/**
 * Search and reverse geocoding with Photon (https://photon.komoot.io), which is built on
 * OpenStreetMap data and allows search-as-you-type.
 */
object Photon {
    private const val BASE = "https://photon.komoot.io"

    // Photon only knows a few languages and rejects the rest; "default" gives local names.
    private val language = Locale.getDefault().language.takeIf { it in setOf("en", "de", "fr") }

    suspend fun search(query: String, nearLat: Double?, nearLon: Double?, zoom: Double?): List<SearchHit> {
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
        return parse(httpGet(url)).mapIndexed { i, (place, context) -> SearchHit(place, context, i, fromPhoton = true) }
    }

    suspend fun reverse(lat: Double, lon: Double): Place? =
        parse(httpGet("$BASE/reverse?lat=$lat&lon=$lon")).firstOrNull()?.first

    fun formatCoordinates(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lon)

    /** Each place with the address and areas around it, for ranking. */
    private fun parse(json: String): List<Pair<Place, String>> {
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

            val place = Place(
                name = name,
                subtitle = subtitle,
                lat = coords.getDouble(1),
                lon = coords.getDouble(0),
                type = prop("osm_value")?.takeIf { it != "yes" },
                key = prop("osm_key"),
            )
            val context = listOf("street", "housenumber", "postcode", "city", "town", "village", "district", "county", "state", "country")
                .mapNotNull(::prop).joinToString(" ")
            place to context
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
