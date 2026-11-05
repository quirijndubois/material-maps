package app.materialmaps

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/** A named list of saved places. [FAVOURITES] always exists and can't be deleted. */
data class PlaceList(val id: String, val name: String, val places: List<Place>) {
    val isFavourites get() = id == FAVOURITES

    companion object {
        const val FAVOURITES = "favourites"
    }
}

/** Saved places and lists, kept on the device only. */
object SavedPlaces {
    private const val PREFS = "saved-places"
    private const val KEY = "lists"

    fun load(context: Context): List<PlaceList> {
        val lists = runCatching {
            val array = JSONArray(prefs(context).getString(KEY, "[]"))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val places = o.getJSONArray("places")
                PlaceList(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    places = (0 until places.length()).map { places.getJSONObject(it).toPlace() },
                )
            }
        }.getOrDefault(emptyList())
        return if (lists.any { it.isFavourites }) lists
        else listOf(PlaceList(PlaceList.FAVOURITES, "Favourites", emptyList())) + lists
    }

    fun create(context: Context, name: String, firstPlace: Place?): List<PlaceList> =
        save(context, load(context) + PlaceList(UUID.randomUUID().toString(), name.trim(), listOfNotNull(firstPlace)))

    fun rename(context: Context, id: String, name: String): List<PlaceList> =
        save(context, load(context).map { if (it.id == id) it.copy(name = name.trim()) else it })

    fun delete(context: Context, id: String): List<PlaceList> =
        save(context, load(context).filterNot { it.id == id && !it.isFavourites })

    /** Adds [place] to the list, or takes it off if it's already there. */
    fun toggle(context: Context, id: String, place: Place): List<PlaceList> =
        save(context, load(context).map { list ->
            if (list.id != id) list
            else if (list.places.any { it.isSameAs(place) }) list.copy(places = list.places.filterNot { it.isSameAs(place) })
            else list.copy(places = list.places + place)
        })

    fun remove(context: Context, id: String, place: Place): List<PlaceList> =
        save(context, load(context).map { list ->
            if (list.id == id) list.copy(places = list.places.filterNot { it.isSameAs(place) }) else list
        })

    /** All lists as GeoJSON: one point per place, with the lists it's on. */
    fun export(lists: List<PlaceList>): String {
        val features = JSONArray()
        lists.flatMap { it.places }.distinctBy { Triple(it.name, it.lat, it.lon) }.forEach { place ->
            val on = JSONArray().apply { lists.containing(place).forEach { put(it.name) } }
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(place.lon).put(place.lat)))
                    .put(
                        "properties",
                        JSONObject()
                            .put("name", place.name)
                            .put("subtitle", place.subtitle)
                            .put("type", place.type ?: "")
                            .put("key", place.key ?: "")
                            .put("lists", on),
                    )
            )
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString(2)
    }

    /**
     * Adds the places in a GeoJSON file to the lists named in it, making lists that don't
     * exist yet. Points without lists go to Favourites. Returns how many places were added.
     */
    fun import(context: Context, geoJson: String): Pair<List<PlaceList>, Int> {
        var lists = load(context)
        var added = 0
        val features = JSONObject(geoJson).optJSONArray("features") ?: JSONArray()
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val coordinates = feature.optJSONObject("geometry")?.takeIf { it.optString("type") == "Point" }
                ?.optJSONArray("coordinates") ?: continue
            val props = feature.optJSONObject("properties") ?: JSONObject()
            val place = Place(
                name = props.optString("name").ifBlank { props.optString("title").ifBlank { "Saved place" } },
                subtitle = props.optString("subtitle").ifBlank { props.optString("address") },
                lat = coordinates.getDouble(1),
                lon = coordinates.getDouble(0),
                type = props.optString("type").ifBlank { null },
                key = props.optString("key").ifBlank { null },
            )
            val names = props.optJSONArray("lists")?.let { a -> (0 until a.length()).map { a.getString(it) } }
                ?.ifEmpty { null } ?: listOf("Favourites")
            for (name in names) {
                val list = lists.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: PlaceList(UUID.randomUUID().toString(), name, emptyList()).also { lists = lists + it }
                if (list.places.none { it.isSameAs(place) }) {
                    lists = lists.map { if (it.id == list.id) it.copy(places = it.places + place) else it }
                    added++
                }
            }
        }
        return save(context, lists) to added
    }

    private fun save(context: Context, lists: List<PlaceList>): List<PlaceList> {
        val array = JSONArray()
        lists.forEach { list ->
            array.put(
                JSONObject()
                    .put("id", list.id)
                    .put("name", list.name)
                    .put("places", JSONArray().apply { list.places.forEach { put(it.toJson()) } })
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
        return lists
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

internal fun Place.toJson(): JSONObject = JSONObject()
    .put("name", name)
    .put("subtitle", subtitle)
    .put("lat", lat)
    .put("lon", lon)
    .put("type", type ?: "")
    .put("key", key ?: "")

internal fun JSONObject.toPlace() = Place(
    name = getString("name"),
    subtitle = optString("subtitle"),
    lat = getDouble("lat"),
    lon = getDouble("lon"),
    type = optString("type").ifBlank { null },
    key = optString("key").ifBlank { null },
)

/** Home and Work: one place each, for directions in one tap. */
enum class Shortcut(val label: String) { Home("Home"), Work("Work") }

object Shortcuts {
    private const val PREFS = "shortcuts"

    fun load(context: Context): Map<Shortcut, Place> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Shortcut.entries.mapNotNull { s ->
            prefs.getString(s.name, null)?.let { json -> runCatching { s to JSONObject(json).toPlace() }.getOrNull() }
        }.toMap()
    }

    fun set(context: Context, shortcut: Shortcut, place: Place?): Map<Shortcut, Place> {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (place == null) remove(shortcut.name) else putString(shortcut.name, place.toJson().toString())
        }.apply()
        return load(context)
    }
}

/** The same place, even if found again by another search with slightly different coordinates. */
fun Place.isSameAs(other: Place) =
    name == other.name && abs(lat - other.lat) < 1e-4 && abs(lon - other.lon) < 1e-4

fun List<PlaceList>.containing(place: Place) = filter { list -> list.places.any { it.isSameAs(place) } }
