package app.materialmaps

import android.graphics.BitmapFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Contour lines as vector tiles, made on the phone: no server has them for free. MapLibre asks
 * for them at [TILES], whose host doesn't exist; requests for it are answered here instead,
 * from the same elevation tiles the terrain shading uses. Each tile is traced with marching
 * squares on the elevation grid, plus the first row and column of its neighbours, so the lines
 * run on without a gap from one tile into the next.
 */
object ContourTiles : Interceptor {
    const val HOST = "contours.materialmaps.invalid"
    const val TILES = "https://$HOST/{z}/{x}/{y}.pbf"
    const val LAYER = "contours"
    const val MIN_ZOOM = 9
    /** Closer in, the elevation data has nothing finer, and the tiles are enlarged. */
    const val MAX_ZOOM = 14
    private const val DEM = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/%d/%d/%d.png"
    private const val SIZE = 256
    private const val EXTENT = 4096

    /** Meters between lines at each zoom, and every how many lines one is bolder. */
    private fun interval(z: Int) = when {
        z <= 9 -> 200
        z == 10 -> 100
        z == 11 -> 50
        z == 12 -> 20
        else -> 10
    }
    private const val MAJOR_EVERY = 5

    /**
     * Below this the elevation data is the sea floor, whose lines would only clutter the water.
     * The lowest polder in the Netherlands is at 7 meters below sea level.
     */
    private const val LOWEST = -10

    private val client = OkHttpClient()
    private val dems = object : LinkedHashMap<Triple<Int, Int, Int>, FloatArray>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<Int, Int, Int>, FloatArray>) = size > 24
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != HOST) return chain.proceed(request)
        val parts = request.url.pathSegments
        val z = parts.getOrNull(0)?.toIntOrNull()
        val x = parts.getOrNull(1)?.toIntOrNull()
        val y = parts.getOrNull(2)?.substringBefore('.')?.toIntOrNull()
        val body = if (z == null || x == null || y == null) ByteArray(0) else tile(z, x, y)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/x-protobuf".toMediaType()))
            .build()
    }

    private fun tile(z: Int, x: Int, y: Int): ByteArray {
        val n = 1 shl z
        val here = dem(z, x, y) ?: throw IOException("No elevation for $z/$x/$y")
        // The neighbours only add a seam; without them (at the edge of the world, or offline)
        // the tile still has all its own lines.
        val right = dem(z, (x + 1) % n, y)
        val below = if (y + 1 < n) dem(z, x, y + 1) else null
        val corner = if (y + 1 < n) dem(z, (x + 1) % n, y + 1) else null
        val w = if (right != null) SIZE + 1 else SIZE
        val h = if (below != null) SIZE + 1 else SIZE
        val grid = FloatArray(w * h)
        for (j in 0 until h) for (i in 0 until w) {
            grid[j * w + i] = when {
                i < SIZE && j < SIZE -> here[j * SIZE + i]
                i == SIZE && j < SIZE -> right!![j * SIZE]
                i < SIZE -> below!![i]
                else -> corner?.get(0) ?: below!![SIZE - 1]
            }
        }
        val step = interval(z)
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (v in grid) {
            if (v < lo) lo = v
            if (v > hi) hi = v
        }
        val lines = ArrayList<Pair<Int, List<IntArray>>>()
        var level = maxOf(ceil(lo / step).toInt(), ceil(LOWEST.toFloat() / step).toInt()) * step
        while (level <= floor(hi)) {
            for (line in trace(grid, w, h, level.toFloat())) lines += level to line
            level += step
        }
        return encode(lines, step * MAJOR_EVERY)
    }

    /** Elevation in meters at each pixel of a Terrarium tile, or null if it can't be had. */
    private fun dem(z: Int, x: Int, y: Int): FloatArray? {
        val key = Triple(z, x, y)
        synchronized(dems) { dems[key] }?.let { return it }
        val bytes = runCatching {
            client.newCall(Request.Builder().url(DEM.format(z, x, y)).header("User-Agent", USER_AGENT).build()).execute().use {
                if (it.isSuccessful) it.body?.bytes() else null
            }
        }.getOrNull() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (bitmap.width != SIZE || bitmap.height != SIZE) return null
        val pixels = IntArray(SIZE * SIZE)
        bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        bitmap.recycle()
        val meters = FloatArray(pixels.size) { i ->
            val p = pixels[i]
            ((p shr 16 and 0xff) * 256f + (p shr 8 and 0xff) + (p and 0xff) / 256f) - 32768f
        }
        synchronized(dems) { dems[key] = meters }
        return meters
    }

    /**
     * The lines at [level] through [grid], as points in tile units. Every point lies on an edge
     * between two grid points, which is how the pieces from neighbouring cells find each other.
     */
    private fun trace(grid: FloatArray, w: Int, h: Int, level: Float): List<List<IntArray>> {
        // Edges: across from (i, j) to (i + 1, j) are j * w + i; down from (i, j) are w * h + j * w + i.
        val points = HashMap<Int, IntArray>()
        val links = HashMap<Int, IntArray>()
        fun point(edge: Int): Int {
            if (edge !in points) {
                val down = edge >= w * h
                val k = if (down) edge - w * h else edge
                val i = k % w
                val j = k / w
                val a = grid[k]
                val b = grid[if (down) k + w else k + 1]
                val t = ((level - a) / (b - a)).coerceIn(0f, 1f)
                val gx = i + if (down) 0f else t
                val gy = j + if (down) t else 0f
                // Grid points are pixel centers.
                points[edge] = intArrayOf(((gx + 0.5f) / SIZE * EXTENT).roundToInt(), ((gy + 0.5f) / SIZE * EXTENT).roundToInt())
            }
            return edge
        }
        fun link(a: Int, b: Int) {
            for ((from, to) in listOf(a to b, b to a)) {
                val l = links[from]
                if (l == null) links[from] = intArrayOf(to, -1) else l[1] = to
            }
        }
        for (j in 0 until h - 1) for (i in 0 until w - 1) {
            val k = j * w + i
            val tl = grid[k] >= level
            val tr = grid[k + 1] >= level
            val br = grid[k + w + 1] >= level
            val bl = grid[k + w] >= level
            val case = (if (tl) 8 else 0) or (if (tr) 4 else 0) or (if (br) 2 else 0) or (if (bl) 1 else 0)
            if (case == 0 || case == 15) continue
            val top = k
            val bottom = k + w
            val left = w * h + k
            val right = w * h + k + 1
            when (case) {
                1, 14 -> link(point(left), point(bottom))
                2, 13 -> link(point(bottom), point(right))
                3, 12 -> link(point(left), point(right))
                4, 11 -> link(point(top), point(right))
                6, 9 -> link(point(top), point(bottom))
                7, 8 -> link(point(left), point(top))
                5, 10 -> {
                    // A saddle: the middle decides which corners are joined.
                    val middle = (grid[k] + grid[k + 1] + grid[k + w] + grid[k + w + 1]) / 4 >= level
                    if ((case == 5) == middle) {
                        link(point(left), point(top))
                        link(point(bottom), point(right))
                    } else {
                        link(point(left), point(bottom))
                        link(point(top), point(right))
                    }
                }
            }
        }
        // Walk the links into lines: first from the loose ends, then round the closed loops.
        val seen = HashSet<Int>()
        val lines = ArrayList<List<IntArray>>()
        fun walk(start: Int) {
            val line = ArrayList<IntArray>()
            var previous = -1
            var current = start
            while (current != -1 && seen.add(current)) {
                line += points.getValue(current)
                val l = links.getValue(current)
                val next = if (l[0] != previous) l[0] else l[1]
                previous = current
                current = next
            }
            if (current == start) line += points.getValue(start)
            if (line.size >= 2) lines += line
        }
        for ((edge, l) in links) if (l[1] == -1 && edge !in seen) walk(edge)
        for (edge in links.keys) if (edge !in seen) walk(edge)
        return lines
    }

    /** The lines as a Mapbox Vector Tile with one layer, [LAYER]: `ele` in meters, and `major`. */
    private fun encode(lines: List<Pair<Int, List<IntArray>>>, major: Int): ByteArray {
        val values = ArrayList<Any>()
        val valueIndex = HashMap<Any, Int>()
        fun value(v: Any) = valueIndex.getOrPut(v) { values += v; values.size - 1 }
        val layer = Pb()
        layer.uint(15, 2)
        layer.string(1, LAYER)
        for ((level, line) in lines) {
            // Moves from point to point, leaving out those that round to the same spot.
            val deltas = ArrayList<Int>()
            var cx = 0
            var cy = 0
            for ((index, p) in line.withIndex()) {
                if (index > 0 && p[0] == cx && p[1] == cy) continue
                deltas += zigzag(p[0] - cx)
                deltas += zigzag(p[1] - cy)
                cx = p[0]
                cy = p[1]
            }
            val count = deltas.size / 2
            if (count < 2) continue
            val geometry = IntArray(deltas.size + 2)
            geometry[0] = command(1, 1)
            geometry[1] = deltas[0]
            geometry[2] = deltas[1]
            geometry[3] = command(2, count - 1)
            for (i in 2 until deltas.size) geometry[i + 2] = deltas[i]
            val feature = Pb()
            feature.packed(2, intArrayOf(0, value(level.toLong()), 1, value(level % major == 0)))
            feature.uint(3, 2)
            feature.packed(4, geometry)
            layer.message(2, feature)
        }
        layer.string(3, "ele")
        layer.string(3, "major")
        for (v in values) {
            val pb = Pb()
            when (v) {
                is Long -> pb.uint(6, (v shl 1) xor (v shr 63))
                is Boolean -> pb.uint(7, if (v) 1 else 0)
            }
            layer.message(4, pb)
        }
        layer.uint(5, EXTENT.toLong())
        return Pb().apply { message(3, layer) }.bytes()
    }

    private fun command(id: Int, count: Int) = (id and 7) or (count shl 3)
    private fun zigzag(n: Int) = (n shl 1) xor (n shr 31)

    /** Just enough of a protobuf writer for vector tiles. */
    private class Pb {
        private val out = ByteArrayOutputStream()
        fun bytes(): ByteArray = out.toByteArray()
        private fun varint(value: Long) {
            var v = value
            while (v and 0x7fL.inv() != 0L) {
                out.write(((v and 0x7f) or 0x80).toInt())
                v = v ushr 7
            }
            out.write(v.toInt())
        }
        private fun key(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())
        fun uint(field: Int, value: Long) {
            key(field, 0)
            varint(value)
        }
        fun bytes(field: Int, data: ByteArray) {
            key(field, 2)
            varint(data.size.toLong())
            out.write(data)
        }
        fun string(field: Int, s: String) = bytes(field, s.toByteArray())
        fun message(field: Int, m: Pb) = bytes(field, m.bytes())
        fun packed(field: Int, values: IntArray) {
            val inner = Pb()
            for (v in values) inner.varint(v.toLong() and 0xffffffffL)
            bytes(field, inner.bytes())
        }
    }
}
