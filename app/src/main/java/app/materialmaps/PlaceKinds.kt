package app.materialmaps

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Attractions
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Train
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale
import kotlin.math.sqrt

/**
 * A kind of place, as OpenStreetMap tags it ([key]=[value]): what "supermarket" or "dentist"
 * means, so a search for one can show every one nearby. [subclasses] are the values the map's
 * own points of interest use for it.
 */
data class PlaceKind(
    val label: String,
    val key: String,
    val value: String,
    val subclasses: Set<String> = setOf(value),
    /** Leave out places without a name, for kinds where those are mostly noise. */
    val needsName: Boolean = false,
) {
    val icon: ImageVector
        get() = Category.entries.firstOrNull { it.value == value }?.icon ?: when (key) {
            "shop" -> Icons.Outlined.Storefront
            "tourism" -> Icons.Outlined.Attractions
            "leisure" -> Icons.Outlined.Park
            "healthcare", "emergency" -> Icons.Outlined.LocalHospital
            "sport" -> Icons.Outlined.SportsSoccer
            "craft" -> Icons.Outlined.Handyman
            "office" -> Icons.Outlined.Business
            "public_transport", "railway" -> Icons.Outlined.Train
            "aeroway" -> Icons.Outlined.Flight
            "historic" -> Icons.Outlined.Museum
            "natural" -> Icons.Outlined.Landscape
            else -> Icons.Outlined.Place
        }
}

/**
 * Understands searches for kinds of places, in several languages and with typos:
 * "supermarkets", "supermarkt", "supermarked" and "grocery store" all mean shop=supermarket.
 * The vocabulary is the iD editor's: names and search terms for each kind, built into
 * assets/place_kinds.json by scripts/place_kinds.py.
 */
object PlaceKinds {
    /** [phrases] are a kind's names and terms, with how much a match on each counts. */
    private class Entry(val key: String, val value: String, val names: Map<String, String>, val phrases: List<Pair<String, Double>>)

    @Volatile
    private var entries: List<Entry>? = null

    /** How many kinds share a phrase: "shop" says less than "supermarket". */
    private var shared: Map<String, Int> = emptyMap()

    suspend fun load(context: Context) {
        if (entries != null) return
        withContext(Dispatchers.IO) {
            val json = JSONArray(context.assets.open("place_kinds.json").bufferedReader().use { it.readText() })
            val list = ArrayList<Entry>(json.length())
            for (i in 0 until json.length()) {
                val o = json.getJSONObject(i)
                val names = o.getJSONObject("n").let { n -> n.keys().asSequence().associateWith { n.getString(it) } }
                val phrases = mutableListOf<Pair<String, Double>>()
                // Names in the user's own language and English say more than names elsewhere:
                // in Dutch, a "café" is a pub.
                val own = setOf("en", Locale.getDefault().language)
                names.forEach { (lang, name) -> phrases += normalize(name) to if (lang in own) 1.0 else 0.97 }
                o.optJSONObject("t")?.let { t ->
                    t.keys().forEach { lang ->
                        val terms = t.getJSONArray(lang)
                        for (j in 0 until terms.length()) phrases += normalize(terms.getString(j)) to TERM
                    }
                }
                val kept = phrases.filter { it.first.length >= 2 }.sortedByDescending { it.second }.distinctBy { it.first }
                list += Entry(o.getString("k"), o.getString("v"), names, kept)
            }
            shared = list.flatMap { e -> e.phrases.map { it.first }.distinct() }.groupingBy { it }.eachCount()
            entries = list
        }
    }

    /** Kinds of places [query] could mean, best first, with how sure that is (0 to 1). */
    fun match(query: String): List<Pair<PlaceKind, Double>> {
        val list = entries ?: return emptyList()
        val words = normalize(query).split(' ').filter { it.isNotEmpty() && it !in FILLER }
        if (words.isEmpty() || words.joinToString(" ").length < 2) return emptyList()
        // The whole query, and for longer ones every word and pair of words in it.
        val pieces = mutableListOf(words.joinToString(" ") to 1.0)
        if (words.size > 1) {
            words.forEach { pieces += it to 0.85 }
            words.zipWithNext().forEach { (a, b) -> pieces += "$a $b" to 0.9 }
        }
        val language = Locale.getDefault().language
        return list.mapNotNull { entry ->
            var best = 0.0
            for ((typed, weight) in pieces) for (variant in variants(typed)) for ((phrase, phraseWeight) in entry.phrases) {
                val s = similarity(variant, phrase)
                // A word out of a longer search only counts as itself: "Albert Heijn" is no "alder".
                if (s == 0.0 || (weight < 1.0 && s < 1.0)) continue
                // Terms many kinds share ("shop", "store") say little.
                val spread = if (phraseWeight == TERM) sqrt((shared[phrase] ?: 1).toDouble()) else 1.0
                val score = s * weight * phraseWeight / spread
                if (score > best) best = score
            }
            if (best < 0.5) return@mapNotNull null
            val label = entry.names[language] ?: entry.names["en"] ?: entry.names.values.first()
            PlaceKind(label, entry.key, entry.value) to best
        }
            .sortedByDescending { it.second }
            .distinctBy { it.first.label }
            .let { all ->
                // Only the clear favourite, and any about as likely.
                val top = all.firstOrNull()?.second ?: return@let all
                all.filter { it.second >= 0.7 && it.second >= top - 0.1 }
            }
            .take(3)
    }

    /** Sure enough that pressing search should show these places rather than names. */
    const val SURE = 0.85
    private const val TERM = 0.92

    /** The typed word and the singulars it could be the plural of, in a few languages. */
    private fun variants(typed: String): List<String> = listOfNotNull(
        typed,
        typed.removeSuffix("s").takeIf { it != typed && it.length >= 3 },
        typed.removeSuffix("es").takeIf { it != typed && it.length >= 3 },
        typed.removeSuffix("en").takeIf { it != typed && it.length >= 3 },
        typed.removeSuffix("n").takeIf { it != typed && it.length >= 3 },
        typed.removeSuffix("e").takeIf { it != typed && it.length >= 3 },
    ).distinct()

    private fun similarity(typed: String, phrase: String): Double {
        if (typed == phrase) return 1.0
        if (typed.length < 3 || phrase.length < 3) return 0.0
        // A typo or two, more for longer words.
        val allowed = when {
            phrase.length >= 9 -> 2
            phrase.length >= 5 -> 1
            else -> 0
        }
        if (allowed > 0) {
            val d = editDistance(typed, phrase, allowed)
            if (d <= allowed) return 0.9 - 0.05 * d
        }
        // Still typing it.
        if (typed.length >= 4 && phrase.startsWith(typed)) return 0.55 + 0.3 * typed.length / phrase.length
        return 0.0
    }

    private fun editDistance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                rowMin = minOf(rowMin, current[j])
            }
            if (rowMin > max) return max + 1
            previous = current
        }
        return previous[b.length]
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    /** Words that say where or which, not what: "supermarket near me", "dichtstbijzijnde apotheek". */
    private val FILLER = setOf(
        "near", "nearby", "me", "around", "closest", "nearest", "here", "find", "a", "an", "the", "in", "my", "area", "local",
        "dichtbij", "buurt", "de", "het", "een", "bij", "mij", "dichtstbijzijnde", "zoek", "hier", "omgeving",
        "nahe", "nahe", "der", "die", "das", "nachste", "naechste", "umgebung",
        "pres", "proche", "plus", "le", "la", "les", "moi",
        "cerca", "mas", "el", "los", "las",
        "vicino", "piu", "il",
    )
}
