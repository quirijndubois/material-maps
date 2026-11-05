package app.materialmaps

import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** The opening of a Wikipedia article about a place, with its picture. */
data class WikiSummary(val title: String, val extract: String, val imageUrl: String?, val url: String)

/**
 * Short descriptions of sights and landmarks from Wikipedia, in the phone's language where
 * there's an article in it, else in English. Text under CC BY-SA, credited on the card.
 */
object Wikipedia {
    private val languages get() = listOf(Locale.getDefault().language, "en").distinct()

    /** From OpenStreetMap's links to the article: `wikipedia=nl:Domtoren` or `wikidata=Q123`. */
    suspend fun forTags(wikipedia: String?, wikidata: String?): WikiSummary? {
        if (wikidata != null) {
            val sites = languages.joinToString("|") { "${it}wiki" }
            val json = JSONObject(
                httpGet(
                    "https://www.wikidata.org/w/api.php?action=wbgetentities&props=sitelinks&format=json" +
                        "&ids=" + URLEncoder.encode(wikidata, "UTF-8") + "&sitefilter=" + URLEncoder.encode(sites, "UTF-8")
                )
            )
            val links = json.optJSONObject("entities")?.optJSONObject(wikidata)?.optJSONObject("sitelinks")
            for (lang in languages) {
                val title = links?.optJSONObject("${lang}wiki")?.optString("title")?.takeIf { it.isNotBlank() } ?: continue
                summary(lang, title)?.let { return it }
            }
        }
        if (wikipedia != null && ':' in wikipedia) {
            return summary(wikipedia.substringBefore(':'), wikipedia.substringAfter(':'))
        }
        return null
    }

    /**
     * For places OpenStreetMap doesn't link: an article about something within 150 m whose
     * title is the place's name, ignoring a qualifier like "Domtoren (Utrecht)".
     */
    suspend fun nearby(place: Place): WikiSummary? {
        val name = place.name.lowercase()
        if (name.length < 4) return null
        for (lang in languages) {
            val json = JSONObject(
                httpGet(
                    "https://$lang.wikipedia.org/w/api.php?action=query&list=geosearch&format=json" +
                        "&gscoord=${place.lat}%7C${place.lon}&gsradius=150&gslimit=20"
                )
            )
            val results = json.optJSONObject("query")?.optJSONArray("geosearch") ?: continue
            for (i in 0 until results.length()) {
                val title = results.getJSONObject(i).optString("title")
                val bare = title.substringBefore(" (").lowercase()
                if (bare == name) return summary(lang, title)
            }
        }
        return null
    }

    private suspend fun summary(lang: String, title: String): WikiSummary? {
        val path = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
        val json = JSONObject(httpGet("https://$lang.wikipedia.org/api/rest_v1/page/summary/$path"))
        if (json.optString("type") == "disambiguation") return null
        val extract = json.optString("extract").takeIf { it.isNotBlank() } ?: return null
        return WikiSummary(
            title = json.optString("title", title),
            extract = extract,
            imageUrl = json.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() },
            url = json.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page")
                ?: "https://$lang.wikipedia.org/wiki/$path",
        )
    }
}
