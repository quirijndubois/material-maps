package app.materialmaps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * What the overlays are loading, for the indicator on the map: map tiles as MapLibre asks for
 * them (seen in [Tiles]), and the app's own lookups (in [track]). Each overlay counts what it
 * started and finished since it was last done, which is its progress.
 */
object Loading {
    class Progress(val started: Int, val finished: Int)

    private val _state = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val state: StateFlow<Map<String, Progress>> = _state.asStateFlow()

    private fun start(label: String) = _state.update { all ->
        val p = all[label]
        all + (label to Progress((p?.started ?: 0) + 1, p?.finished ?: 0))
    }

    private fun finish(label: String) = _state.update { all ->
        val p = all[label] ?: return@update all
        // Once all it started is done, it's no longer loading.
        if (p.finished + 1 >= p.started) all - label else all + (label to Progress(p.started, p.finished + 1))
    }

    /**
     * Forgets what's loading for overlays no longer shown ([shown] says by label): what they
     * still had going is cancelled, and mustn't hold up the indicator.
     */
    fun keepOnly(shown: (String) -> Boolean) = _state.update { all -> all.filterKeys(shown) }

    /** Runs [block] as loading for [label]. */
    suspend fun <T> track(label: String, block: suspend () -> T): T {
        start(label)
        try {
            return block()
        } finally {
            finish(label)
        }
    }

    /** Counts the overlays' tile requests. The base map's own are left out. */
    object Tiles : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val label = labelFor(chain.request().url) ?: return chain.proceed(chain.request())
            start(label)
            try {
                return chain.proceed(chain.request())
            } finally {
                finish(label)
            }
        }
    }

    private fun labelFor(url: HttpUrl): String? {
        val path = url.encodedPath
        return when {
            url.host.endsWith("rainviewer.com") -> Overlay.Rain.label
            url.host == Satellite.HOST -> Overlay.entries.firstOrNull { Satellite.layer(it) == url.queryParameter("layers") }?.label
            url.host == Cams.HOST -> Overlay.entries.firstOrNull { Cams.layer(it)?.name == url.queryParameter("layers") }?.label
            url.host == "tile.waymarkedtrails.org" -> Overlay.entries.firstOrNull { it.tiles?.contains("/${url.pathSegments.firstOrNull()}/") == true }?.label
            url.host.endsWith("openrailwaymap.org") -> Overlay.Railways.label
            url.host.endsWith("opensnowmap.org") -> Overlay.Slopes.label
            url.host.endsWith("openseamap.org") -> Overlay.SeaMarks.label
            url.host.endsWith("versatiles.org") -> "Metro and trams"
            url.host == AERIAL_HOST && "luchtfoto" in path -> "Aerial photos"
            url.host == AERIAL_HOST && "/ahn/" in path -> Overlay.Elevation.label
            url.host == AERIAL_HOST && "kadaster" in path -> Overlay.Parcels.label
            url.host == "tiles.maps.eox.at" -> "Satellite"
            url.host == ContourTiles.HOST -> Overlay.Contours.label
            "elevation-tiles-prod" in path -> Overlay.Terrain.label
            else -> null
        }
    }
}
