package app.materialmaps

import android.net.Uri

/** What a link another app opened with points at. */
sealed interface MapLink {
    data class At(val lat: Double, val lon: Double, val label: String?, val zoom: Double?) : MapLink
    data class Search(val query: String) : MapLink

    companion object {
        private val latLon = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""")
        // geo:0,0?q=52.1,5.1(Label)
        private val latLonLabel = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*(?:\((.*)\))?\s*$""")
        // .../maps/place/Name/@52.1,5.1,17z or .../maps/@52.1,5.1,17z
        private val googleAt = Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)(?:,(\d+(?:\.\d+)?)z)?""")
        // openstreetmap.org/#map=17/52.1/5.1
        private val osmMap = Regex("""map=(\d+(?:\.\d+)?)/(-?\d+(?:\.\d+)?)/(-?\d+(?:\.\d+)?)""")

        fun parse(uri: Uri): MapLink? = when (uri.scheme) {
            "geo" -> parseGeo(uri)
            "http", "https" -> parseWeb(uri)
            else -> null
        }

        /** geo:lat,lon, geo:lat,lon?z=17, geo:0,0?q=lat,lon(label) or geo:0,0?q=an address. */
        private fun parseGeo(uri: Uri): MapLink? {
            // geo: URIs are opaque, so Uri won't split off the query for us.
            val rest = uri.schemeSpecificPart.orEmpty()
            val position = rest.substringBefore('?')
            val params = rest.substringAfter('?', "").split('&').associate {
                Uri.decode(it.substringBefore('=')) to Uri.decode(it.substringAfter('=', "")).replace('+', ' ')
            }
            val zoom = params["z"]?.toDoubleOrNull()
            params["q"]?.takeIf { it.isNotBlank() }?.let { q ->
                latLonLabel.find(q)?.let { m ->
                    return At(m.groupValues[1].toDouble(), m.groupValues[2].toDouble(), m.groupValues[3].ifBlank { null }, zoom)
                }
                return Search(q)
            }
            val m = latLon.find(position) ?: return null
            val lat = m.groupValues[1].toDouble()
            val lon = m.groupValues[2].toDouble()
            return if (lat == 0.0 && lon == 0.0) null else At(lat, lon, null, zoom)
        }

        private fun parseWeb(uri: Uri): MapLink? {
            val host = uri.host.orEmpty()
            if (host.endsWith("openstreetmap.org") || host == "osm.org") {
                val lat = uri.getQueryParameter("mlat")?.toDoubleOrNull()
                val lon = uri.getQueryParameter("mlon")?.toDoubleOrNull()
                val map = osmMap.find(uri.fragment.orEmpty()) ?: osmMap.find(uri.toString())
                if (lat != null && lon != null) return At(lat, lon, null, map?.groupValues?.get(1)?.toDoubleOrNull())
                if (map != null) return At(map.groupValues[2].toDouble(), map.groupValues[3].toDouble(), null, map.groupValues[1].toDouble())
                uri.getQueryParameter("query")?.takeIf { it.isNotBlank() }?.let { return Search(it) }
                return null
            }
            // Google Maps
            val q = uri.getQueryParameter("q") ?: uri.getQueryParameter("query") ?: uri.getQueryParameter("destination")
            if (!q.isNullOrBlank()) {
                latLonLabel.find(q)?.let { m -> return At(m.groupValues[1].toDouble(), m.groupValues[2].toDouble(), null, null) }
                return Search(q)
            }
            val segments = uri.pathSegments
            val place = segments.indexOf("place").takeIf { it >= 0 }?.let { segments.getOrNull(it + 1) }?.replace('+', ' ')
            googleAt.find(uri.path.orEmpty())?.let { m ->
                return At(m.groupValues[1].toDouble(), m.groupValues[2].toDouble(), place, m.groupValues[3].toDoubleOrNull())
            }
            if (place != null) return Search(place)
            segments.indexOf("search").takeIf { it >= 0 }?.let { segments.getOrNull(it + 1) }?.let { return Search(it.replace('+', ' ')) }
            return null
        }
    }
}
