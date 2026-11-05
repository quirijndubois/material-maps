package app.materialmaps

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt

/** A search result, with the words around it (street, town, province) that ranking looks at. */
class SearchHit(
    val place: Place,
    val context: String,
    /** Position in the answer of the service it came from. */
    val rank: Int,
    val fromPhoton: Boolean = false,
)

/**
 * Place search for the search bar and dropped pins. Transitous (MOTIS, built on OSM) answers in
 * ~150 ms and also knows transit stops, but has no idea what's famous: "Colosseum" finds car
 * parks named after it before the one in Rome. Photon does know (it ranks by Wikipedia
 * importance) but takes a second or two, so it's asked at the same time and its answers are
 * merged in when they come. Both are then ranked here, by how well the words match, how
 * important a place is and how far away it is.
 */
object Geocoder {
    /** How long to wait for Photon once Transitous has answered. */
    private const val PHOTON_WAIT = 2_500L
    private const val MAX_RESULTS = 25
    private val coordinates = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*[, ]\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")

    // Photon calls aren't tied to the search that made them: a blocking read can't be
    // interrupted, and a search shouldn't wait for one it has given up on.
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    /**
     * Places matching [query], best first. [early] gets the quick answer from Transitous alone
     * while Photon is still on its way; the return value has both.
     */
    suspend fun search(
        query: String,
        nearLat: Double?,
        nearLon: Double?,
        zoom: Double?,
        early: (List<Place>) -> Unit = {},
    ): List<Place> {
        coordinates.matchEntire(query)?.let { m ->
            val lat = m.groupValues[1].toDouble()
            val lon = m.groupValues[2].toDouble()
            if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                return listOf(Place("Coordinates", Photon.formatCoordinates(lat, lon), lat, lon))
            }
        }
        val q = query.trim()
        fun rank(hits: List<SearchHit>) = SearchRanking.rank(q, hits, nearLat, nearLon, zoom).take(MAX_RESULTS)

        // Too short to say anything Photon could add.
        val photon = if (q.length < 3) null else background.async { Photon.search(q, nearLat, nearLon, zoom) }
        val fast = try {
            searchTransitous(q, nearLat, nearLon)
        } catch (e: CancellationException) {
            photon?.cancel()
            throw e
        } catch (e: Exception) {
            null
        }
        if (fast != null && photon != null) early(rank(fast))
        val slow = try {
            if (fast == null) photon?.await() else withTimeoutOrNull(PHOTON_WAIT) { photon?.await() }
        } catch (e: CancellationException) {
            photon?.cancel()
            throw e
        } catch (e: Exception) {
            null
        }
        photon?.cancel()
        if (fast == null && slow == null) throw IOException("Search failed")
        val places = rank(fast.orEmpty() + slow.orEmpty())
        synchronized(cache) { cache[cacheKey(query, nearLat, nearLon)] = places }
        return places
    }

    /**
     * Two searches at once: a strongly local one, so "albert" finds every Albert Heijn around,
     * and a broad one, so "utrecht" still finds the city from Amsterdam.
     */
    private suspend fun searchTransitous(query: String, nearLat: Double?, nearLon: Double?): List<SearchHit> {
        if (nearLat == null || nearLon == null) return Transitous.geocode(query, null, null)
        return coroutineScope {
            val nearby = async { Transitous.geocode(query, nearLat, nearLon, bias = 20, count = 30) }
            val broad = async { Transitous.geocode(query, nearLat, nearLon) }
            nearby.await() + broad.await()
        }
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
 * Orders search results from both services as one list. Each gets a score from three things:
 * - the words: every word typed should be found in the name, or in the address or town around
 *   it ("Mozartstraat 22 Haarlem"); the last word may still be half typed, and small typos
 *   are forgiven. Words left over count against a result.
 * - importance: cities, landmarks and stations over shops, and whatever Photon puts first
 *   when it's a landmark, since Photon knows what's famous.
 * - distance, which counts less the more important a place is, and much less when the query
 *   itself names the town.
 */
internal object SearchRanking {
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val marks = Regex("\\p{M}+")

    private val towns = mapOf(
        "country" to 0.6, "city" to 0.5, "place" to 0.5, "place_capital" to 0.5, "town" to 0.4,
        "state" to 0.3, "municipality" to 0.3, "village" to 0.2, "suburb" to 0.15,
    )
    private val landmarks = setOf(
        "monument", "memorial", "attraction", "museum", "castle", "tower", "tower_freestanding", "viewpoint",
        "zoo", "theme_park", "stadium", "cathedral", "palace", "park", "square", "station", "train_station",
        "aerodrome", "airport", "university", "bridge", "theatre", "arts_centre",
    )

    fun rank(query: String, hits: List<SearchHit>, nearLat: Double?, nearLon: Double?, zoom: Double?): List<Place> {
        val typed = words(query)
        if (typed.isEmpty()) return hits.map { it.place }
        // Zoomed out to a whole country, where the map happens to be centered says little.
        val zoomFactor = zoom?.let { ((it - 3) / 9).coerceIn(0.3, 1.0) } ?: 1.0
        val ranked = hits
            .map { hit -> hit to score(typed, hit, nearLat, nearLon, zoomFactor) }
            .sortedByDescending { it.second }
            .map { it.first.place }
        // One building often comes back several times (entrances, or once from each service),
        // but branches of a chain further apart are all worth showing.
        val kept = mutableListOf<Place>()
        ranked.forEach { p ->
            val key = words(p.name).joinToString(" ")
            val repeat = kept.any { words(it.name).joinToString(" ") == key && distanceKm(it.lat, it.lon, p.lat, p.lon) < 0.3 }
            if (!repeat) kept += p
        }
        return kept
    }

    private fun score(typed: List<String>, hit: SearchHit, nearLat: Double?, nearLon: Double?, zoomFactor: Double): Double {
        val place = hit.place
        var (text, located) = textScore(typed, words(place.name), words(hit.context))
        // Photon also matches names in other languages ("Tour Eiffel" for "eiffel tower") and
        // alternative names, which aren't in what it sends back; trust its order a little.
        if (hit.fromPhoton) text = maxOf(text, 0.9 - 0.08 * hit.rank)

        val baseImportance = importance(place)
        var importance = baseImportance
        var distanceWeight = 0.15 * (1 - 1.4 * min(importance, 0.5))
        if (located) distanceWeight *= 0.3
        // A city or landmark that's only half matched shouldn't push past a good match.
        if (text < 1) importance /= 2
        if (hit.fromPhoton && hit.rank == 0 && (place.type in landmarks || baseImportance >= 0.4)) {
            importance += 0.35
            distanceWeight /= 2
        }
        val km = if (nearLat != null && nearLon != null) distanceKm(nearLat, nearLon, place.lat, place.lon) else 0.0
        return text + importance - distanceWeight * zoomFactor * ln(1 + km / 2)
    }

    /**
     * How well [typed] matches a name and what's around it: about 1 when every word is found,
     * 1.3 for the exact name, less for prefixes, typos and leftovers. Also whether a word was
     * found only around the name, like the town in "Mozartstraat 22 Haarlem".
     */
    private fun textScore(typed: List<String>, name: List<String>, context: List<String>): Pair<Double, Boolean> {
        if (name.isEmpty()) return 0.0 to false
        val typedJoined = typed.joinToString("")
        val nameJoined = name.joinToString("")
        // Ignoring spaces, so "eiffeltower" is "Eiffel Tower".
        if (typedJoined == nameJoined) return 1.3 to false

        var total = 0.0
        var missing = 0
        var located = false
        typed.forEachIndexed { i, word ->
            val typing = i == typed.lastIndex
            val inName = name.maxOf { wordMatch(word, it, typing) }
            val inContext = context.maxOfOrNull {
                val m = wordMatch(word, it, typing)
                // A half-typed town shouldn't count as much: "haarlem" isn't "Haarlemmermeer".
                if (m == 1.0) 0.95 else m * 0.7
            } ?: 0.0
            if (inContext > inName) located = true
            val best = maxOf(inName, inContext)
            total += best
            if (best == 0.0) missing++
        }
        // How much of the name the query accounts for: "Leidseplein" is a better answer to
        // "leidseplein" than "Fietsenstalling Leidseplein" is.
        val covered = name.count { w -> typed.withIndex().any { (i, t) -> wordMatch(t, w, i == typed.lastIndex) > 0 } }
        var score = total / typed.size * (0.7 + 0.3 * covered / name.size)
        // Typing the start of a name, spaces or not.
        if (typedJoined.length >= 3 && nameJoined.startsWith(typedJoined)) {
            score = maxOf(score, 0.85 + 0.15 * typedJoined.length / nameJoined.length)
        }
        return score - 0.8 * missing / typed.size to located
    }

    private fun wordMatch(typed: String, word: String, typing: Boolean): Double = when {
        typed == word -> 1.0
        typing && word.startsWith(typed) -> 0.9
        typed.length >= 4 && word.startsWith(typed) -> 0.75
        typed.length >= 5 && word.length >= 5 -> when (editDistance(typed, word, 2)) {
            1 -> 0.6
            2 -> if (typed.length >= 8) 0.5 else 0.0
            else -> 0.0
        }
        else -> 0.0
    }

    private fun importance(place: Place): Double = when {
        place.key == "public_transport" -> if (place.type == "station") 0.15 else 0.05
        place.key == null || place.key == "place" -> towns[place.type] ?: if (place.type in landmarks) 0.2 else 0.0
        place.type in landmarks -> 0.2
        else -> 0.0
    }

    /** Lowercase words without accents, so "Bijlmer ArenA" and "bijlmer arena" match. */
    fun words(text: String): List<String> =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(marks, "")
            .split(separators)
            .filter { it.isNotEmpty() }

    /** Levenshtein distance, or [max] + 1 once it's clearly more than [max]. */
    private fun editDistance(a: String, b: String, max: Int): Int {
        if (abs(a.length - b.length) > max) return max + 1
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                rowMin = min(rowMin, current[j])
            }
            if (rowMin > max) return max + 1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
