package app.materialmaps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Every shop, café and the like near a spot, read straight from the map's street-level vector
 * tiles (OpenFreeMap, OpenMapTiles schema). Those always have every point of interest, whatever
 * the map happens to show, and come from a fast CDN, unlike the shared Overpass servers.
 */
object PoiTiles {
    /** The zoom at which the tiles have every point of interest. */
    private const val ZOOM = 14
    private const val TILEJSON = "https://tiles.openfreemap.org/planet"

    /** [names] counts the languages the name is in. */
    private class Poi(val name: String?, val subclass: String, val lat: Double, val lon: Double, val names: Int)

    private var template: String? = null
    private val templateLock = Mutex()
    private val cache = object : LinkedHashMap<Long, List<Poi>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<Poi>>) = size > 48
    }
    private val fetches = Semaphore(8)

    /** Places of [kind] within [radius] meters of [center], closest first. */
    suspend fun near(kind: PlaceKind, center: LatLng, radius: Double): List<Place> {
        val dLat = radius / 111_320.0
        val dLon = radius / (111_320.0 * cos(Math.toRadians(center.latitude)))
        val tiles = tilesIn(center.latitude - dLat, center.longitude - dLon, center.latitude + dLat, center.longitude + dLon)
        return places(kind, load(tiles)) { metersBetween(center, LatLng(it.lat, it.lon)) <= radius }
            .sortedBy { metersBetween(center, LatLng(it.lat, it.lon)) }
    }

    /**
     * Places of [kind] within [radius] meters of [path]. The path is cut into stretches, and
     * tiles are read from all of them in turn, so what's found comes from the whole way from
     * the start, not just the first few kilometers: [onFound] gets everything so far after
     * each batch. A long route has more tiles than are worth reading; then the stretches each
     * get their share, nearest the route first.
     */
    suspend fun along(kind: PlaceKind, path: List<LatLng>, radius: Double, onFound: suspend (List<Place>) -> Unit) {
        val ordered = LinkedHashSet<Long>()
        for (p in path) {
            val dLat = radius / 111_320.0
            val dLon = radius / (111_320.0 * cos(Math.toRadians(p.latitude)))
            // The tile the route is in first, then any neighbours it passes close to.
            val (x, y) = tileOf(p.latitude, p.longitude)
            ordered += (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
            ordered += tilesIn(p.latitude - dLat, p.longitude - dLon, p.latitude + dLat, p.longitude + dLon)
        }
        val tiles = ordered.toList()
        val stretches = tiles.chunked(((tiles.size + STRETCHES - 1) / STRETCHES).coerceAtLeast(1))
        val share = (MAX_ALONG_TILES / stretches.size).coerceAtLeast(1)
        // Round and round the stretches: the first tile of each, then the second of each, ...
        val interleaved = (0 until share).flatMap { i -> stretches.mapNotNull { it.getOrNull(i) } }
        val found = mutableListOf<Place>()
        for (batch in interleaved.chunked(8)) {
            found += places(kind, load(batch)) { true }
            onFound(found.toList())
        }
    }

    private fun places(kind: PlaceKind, pois: List<Poi>, keep: (Poi) -> Boolean): List<Place> =
        pois.asSequence()
            .filter { it.subclass in kind.subclasses && (it.name != null || !kind.needsName) && keep(it) }
            .map {
                Place(name = it.name ?: kind.label, subtitle = "", lat = it.lat, lon = it.lon, type = it.subclass, key = kind.key, prominence = prominence(it))
            }
            // Neighbouring tiles share a margin, so the same place can come twice.
            .distinctBy { Triple(it.name, (it.lat * 1e4).toLong(), (it.lon * 1e4).toLong()) }
            .toList()

    /**
     * How well known a place seems. A name in many languages is the best sign there is in
     * these tiles: the Rijksmuseum has over fifty, a typical museum five, the corner shop one.
     */
    private fun prominence(poi: Poi): Double = (ln(1.0 + (poi.names - 1).coerceAtLeast(0)) / ln(40.0)).coerceIn(0.0, 1.0)

    private suspend fun load(tiles: Collection<Long>): List<Poi> = coroutineScope {
        tiles.map { tile -> async { fetches.withPermit { tile(tile) } } }.awaitAll().flatten()
    }

    private suspend fun tile(id: Long): List<Poi> {
        synchronized(cache) { cache[id] }?.let { return it }
        val x = (id shr 32).toInt()
        val y = id.toInt()
        val url = urlTemplate().replace("{z}", "$ZOOM").replace("{x}", "$x").replace("{y}", "$y")
        val bytes = httpGetBytes(url)
        val pois = withContext(Dispatchers.Default) { decode(unzip(bytes), x, y) }
        synchronized(cache) { cache[id] = pois }
        return pois
    }

    private suspend fun urlTemplate(): String = templateLock.withLock {
        template ?: JSONObject(httpGet(TILEJSON)).getJSONArray("tiles").getString(0).also { template = it }
    }

    private fun tilesIn(south: Double, west: Double, north: Double, east: Double): List<Long> {
        val (x0, y0) = tileOf(north, west)
        val (x1, y1) = tileOf(south, east)
        return (x0..x1).flatMap { x -> (y0..y1).map { y -> (x.toLong() shl 32) or (y.toLong() and 0xffffffffL) } }
    }

    private fun tileOf(lat: Double, lon: Double): Pair<Int, Int> {
        val n = 1 shl ZOOM
        val x = floor((lon + 180) / 360 * n).toInt().coerceIn(0, n - 1)
        val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        val y = floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n).toInt().coerceIn(0, n - 1)
        return x to y
    }

    private fun unzip(bytes: ByteArray): ByteArray =
        if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) GZIPInputStream(ByteArrayInputStream(bytes)).readBytes()
        else bytes

    /** The "poi" layer of a Mapbox Vector Tile: names, kinds and where they are. */
    private fun decode(bytes: ByteArray, tileX: Int, tileY: Int): List<Poi> {
        val tile = Proto(bytes, 0, bytes.size)
        while (tile.more()) {
            val (field, wire) = tile.key()
            if (field != 3 || wire != 2) {
                tile.skip(wire)
                continue
            }
            val layer = tile.message()
            var name = ""
            var extent = 4096
            val keys = ArrayList<String>()
            val values = ArrayList<Any?>()
            val features = ArrayList<Proto>()
            while (layer.more()) {
                val (f, w) = layer.key()
                when {
                    f == 1 && w == 2 -> name = layer.string()
                    f == 2 && w == 2 -> features += layer.message()
                    f == 3 && w == 2 -> keys += layer.string()
                    f == 4 && w == 2 -> {
                        val value = layer.message()
                        var v: Any? = null
                        while (value.more()) {
                            val (vf, vw) = value.key()
                            v = when {
                                vf == 1 && vw == 2 -> value.string()
                                (vf == 4 || vf == 5) && vw == 0 -> value.varint()
                                vf == 6 && vw == 0 -> value.varint().let { (it ushr 1) xor -(it and 1) }
                                else -> {
                                    value.skip(vw)
                                    v
                                }
                            }
                        }
                        values += v
                    }
                    f == 5 && w == 0 -> extent = layer.varint().toInt()
                    else -> layer.skip(w)
                }
            }
            if (name != "poi") continue
            val localName = "name:${Locale.getDefault().language}"
            val n = (1 shl ZOOM).toDouble()
            return features.mapNotNull { feature ->
                var tags: IntArray? = null
                var geometry: IntArray? = null
                while (feature.more()) {
                    val (f, w) = feature.key()
                    when {
                        f == 2 && w == 2 -> tags = feature.packed()
                        f == 4 && w == 2 -> geometry = feature.packed()
                        else -> feature.skip(w)
                    }
                }
                val t = tags ?: return@mapNotNull null
                val g = geometry ?: return@mapNotNull null
                if (g.size < 3 || g[0] and 7 != 1) return@mapNotNull null
                var subclass: String? = null
                var poiName: String? = null
                var localized: String? = null
                var names = 0
                for (i in 0 until t.size - 1 step 2) {
                    val key = keys.getOrNull(t[i]) ?: continue
                    val value = values.getOrNull(t[i + 1]) ?: continue
                    when {
                        key == "subclass" -> subclass = value as? String
                        key == "name" -> poiName = value as? String
                        key == localName -> localized = value as? String
                    }
                    if (key.startsWith("name:")) names++
                }
                val px = zigzag(g[1]) / extent.toDouble()
                val py = zigzag(g[2]) / extent.toDouble()
                if (px !in 0.0..1.0 || py !in 0.0..1.0) return@mapNotNull null
                val lon = (tileX + px) / n * 360 - 180
                val lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * (tileY + py) / n))))
                Poi((poiName ?: localized)?.takeIf { it.isNotBlank() }, subclass ?: return@mapNotNull null, lat, lon, names)
            }
        }
        return emptyList()
    }

    private fun zigzag(n: Int) = (n ushr 1) xor -(n and 1)

    /** Just enough of a protobuf reader for vector tiles. */
    private class Proto(val bytes: ByteArray, var pos: Int, val end: Int) {
        fun more() = pos < end
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = bytes[pos++].toInt() and 0xff
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b < 0x80) return result
                shift += 7
            }
        }
        fun key(): Pair<Int, Int> = varint().toInt().let { (it ushr 3) to (it and 7) }
        fun message(): Proto {
            val length = varint().toInt()
            return Proto(bytes, pos, pos + length).also { pos += length }
        }
        fun string(): String {
            val length = varint().toInt()
            return String(bytes, pos, length, Charsets.UTF_8).also { pos += length }
        }
        fun packed(): IntArray {
            val m = message()
            val out = ArrayList<Int>()
            while (m.more()) out += m.varint().toInt()
            return out.toIntArray()
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> pos += varint().toInt()
                5 -> pos += 4
            }
        }
    }

    /**
     * Along a route, look at no more tiles than this. Out in the country a tile is a few dozen
     * kilobytes, in a city centre half a megabyte.
     */
    private const val MAX_ALONG_TILES = 150
    /** How many stretches a route is cut into, to spread the search over all of it. */
    private const val STRETCHES = 12
}
