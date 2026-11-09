package app.materialmaps

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.storage.FileSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * The map's tiles as kept on the phone by MapLibre, for the downloaded regions (and whatever
 * was seen lately): read straight from its database, for looking things up without a
 * connection.
 */
object OfflineTiles {
    @Volatile
    private var path: File? = null

    fun init(context: Context) {
        path = File(FileSource.getResourcesCachePath(context), "mbgl-offline.db")
    }

    /** Tile [z]/[x]/[y] of the map's own data, unpacked, or null if it isn't on the phone. */
    fun read(z: Int, x: Int, y: Int): ByteArray? = read(z, (x..x), (y..y))[x.toLong() shl 32 or (y.toLong() and 0xffffffffL)]

    /** Every tile at [z] in the given columns and rows that's on the phone, by x and y. */
    fun read(z: Int, xs: IntRange, ys: IntRange): Map<Long, ByteArray> {
        val file = path?.takeIf { it.exists() } ?: return emptyMap()
        val out = HashMap<Long, ByteArray>()
        runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT x, y, data, compressed FROM tiles WHERE z = ? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? " +
                        "AND url_template LIKE '%openfreemap.org/planet%' AND data IS NOT NULL",
                    arrayOf("$z", "${xs.first}", "${xs.last}", "${ys.first}", "${ys.last}"),
                ).use { c ->
                    while (c.moveToNext()) {
                        val key = c.getLong(0) shl 32 or (c.getLong(1) and 0xffffffffL)
                        if (key in out) continue
                        var data = c.getBlob(2)
                        // MapLibre keeps them deflated; the tiles themselves may be gzipped too.
                        if (c.getInt(3) != 0) data = inflate(data)
                        out[key] = gunzip(data)
                    }
                }
            }
        }
        return out
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 3)
        val buffer = ByteArray(64 * 1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) GZIPInputStream(ByteArrayInputStream(bytes)).readBytes()
        else bytes

    fun tileOf(lat: Double, lon: Double, z: Int): Pair<Int, Int> {
        val n = 1 shl z
        val x = floor((lon + 180) / 360 * n).toInt().coerceIn(0, n - 1)
        val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        val y = floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n).toInt().coerceIn(0, n - 1)
        return x to y
    }
}

/**
 * Search without a connection, in the map data on the phone: places, streets and towns by
 * name around where the map is, ranked like online results. Only as far as the downloaded
 * regions go, and without house numbers.
 */
object OfflineSearch {
    /** Street-level tiles around the map, about 6 km each way: every place and street. */
    private const val NEAR_ZOOM = 14
    private const val NEAR_TILES = 4
    /** Region-level tiles further out, for towns and villages. */
    private const val FAR_ZOOM = 10
    private const val FAR_TILES = 3

    private class Named(val name: String, val kind: String?, val subclass: String?, val lat: Double, val lon: Double, val layer: String)

    suspend fun search(query: String, nearLat: Double, nearLon: Double, zoom: Double?): List<Place> = withContext(Dispatchers.Default) {
        val q = SearchRanking.words(query)
        if (q.isEmpty()) return@withContext emptyList()
        val found = ArrayList<Named>()
        for ((z, reach, layers) in listOf(
            Triple(NEAR_ZOOM, NEAR_TILES, setOf("poi", "transportation_name", "place")),
            Triple(FAR_ZOOM, FAR_TILES, setOf("place")),
        )) {
            val (cx, cy) = OfflineTiles.tileOf(nearLat, nearLon, z)
            val tiles = OfflineTiles.read(z, (cx - reach)..(cx + reach), (cy - reach)..(cy + reach))
            for ((key, bytes) in tiles) {
                val x = (key shr 32).toInt()
                val y = key.toInt()
                runCatching { decode(bytes, x, y, z, layers) }.getOrNull()?.forEach { named ->
                    // Only what has every word but the last, which may be half typed.
                    val words = SearchRanking.words(named.name)
                    if (q.dropLast(1).all { w -> words.any { it == w } } && words.any { it.startsWith(q.last()) || q.last().startsWith(it) }) {
                        found += named
                    }
                }
            }
        }
        // A street comes as many pieces: one will do, the nearest.
        val hits = found
            .sortedBy { metersBetween(org.maplibre.android.geometry.LatLng(it.lat, it.lon), org.maplibre.android.geometry.LatLng(nearLat, nearLon)) }
            .distinctBy { if (it.layer == "transportation_name") it.name else "${it.name}|${(it.lat * 1e3).toLong()}|${(it.lon * 1e3).toLong()}" }
            .mapIndexed { i, n ->
                val (key, type, subtitle) = when (n.layer) {
                    "transportation_name" -> Triple("highway", "residential", "Street")
                    "place" -> Triple("place", n.kind ?: "town", n.kind?.replaceFirstChar { it.uppercase() } ?: "Place")
                    else -> Triple(n.kind ?: "amenity", n.subclass ?: n.kind, (n.subclass ?: n.kind ?: "").replace('_', ' ').replaceFirstChar { it.uppercase() })
                }
                SearchHit(Place(n.name, subtitle, n.lat, n.lon, type = type, key = key), context = "", rank = i)
            }
        SearchRanking.rank(query.trim(), hits, nearLat, nearLon, zoom).take(25)
    }

    /** The named features of [layers] in a vector tile, each at its first point. */
    private fun decode(bytes: ByteArray, tileX: Int, tileY: Int, z: Int, layers: Set<String>): List<Named> {
        val out = ArrayList<Named>()
        val localName = "name:${Locale.getDefault().language}"
        val n = (1 shl z).toDouble()
        val tile = Pb(bytes, 0, bytes.size)
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
            val features = ArrayList<Pb>()
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
            if (name !in layers) continue
            for (feature in features) {
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
                val t = tags ?: continue
                val g = geometry ?: continue
                if (g.size < 3 || g[0] and 7 != 1) continue
                var featureName: String? = null
                var localized: String? = null
                var kind: String? = null
                var subclass: String? = null
                for (i in 0 until t.size - 1 step 2) {
                    val key = keys.getOrNull(t[i]) ?: continue
                    val value = values.getOrNull(t[i + 1]) as? String ?: continue
                    when (key) {
                        "name" -> featureName = value
                        localName -> localized = value
                        "class" -> kind = value
                        "subclass" -> subclass = value
                    }
                }
                val label = (localized ?: featureName)?.takeIf { it.isNotBlank() } ?: continue
                val px = zigzag(g[1]) / extent.toDouble()
                val py = zigzag(g[2]) / extent.toDouble()
                if (px !in 0.0..1.0 || py !in 0.0..1.0) continue
                val lon = (tileX + px) / n * 360 - 180
                val lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * (tileY + py) / n))))
                out += Named(label, kind, subclass, lat, lon, name)
            }
        }
        return out
    }

    private fun zigzag(n: Int) = (n ushr 1) xor -(n and 1)

    /** Just enough of a protobuf reader for vector tiles. */
    private class Pb(val bytes: ByteArray, var pos: Int, val end: Int) {
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
        fun message(): Pb {
            val length = varint().toInt()
            return Pb(bytes, pos, pos + length).also { pos += length }
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
}
