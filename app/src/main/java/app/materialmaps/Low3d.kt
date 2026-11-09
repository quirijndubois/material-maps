package app.materialmaps

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A tiny renderer for low-poly models, drawn straight onto an Android [Canvas]: flat-shaded
 * faces, painted back to front, with a soft shadow on the ground. Plenty for a vehicle of a
 * few hundred faces, every frame.
 *
 * Models are in their own units: x to the right, y forward (the nose), z up, standing on z = 0
 * around the origin. A car is about 4.4 long.
 */
class Vec(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y, z - o.z)
    operator fun times(f: Float) = Vec(x * f, y * f, z * f)
    infix fun dot(o: Vec) = x * o.x + y * o.y + z * o.z
    fun normalized(): Vec {
        val l = sqrt(this dot this)
        return if (l == 0f) this else Vec(x / l, y / l, z / l)
    }
}

/** One flat face; [emissive] ones (lights, engine glow) aren't shaded. */
class Face(val points: List<Vec>, val color: Int, val normal: Vec, val emissive: Boolean)

class Model(val faces: List<Face>) {
    private val groundPoints = faces.flatMap { it.points }.distinctBy { Triple(it.x, it.y, it.z) }

    // Reused between frames.
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 0, 0, 0) }
    private var shadowBlur = -1f

    /**
     * Draws the model with its ground center at ([cx], [cy]), [scale] pixels to a unit, turned
     * [yaw] degrees clockwise from nose-up and seen from [tilt] degrees off straight down, as the
     * map's own tilt. [blur] softens the shadow; that needs a software canvas.
     */
    fun draw(canvas: Canvas, cx: Float, cy: Float, scale: Float, yaw: Float, tilt: Float, blur: Float = 0f) {
        val a = Math.toRadians(yaw.toDouble())
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        val t = Math.toRadians(tilt.toDouble())
        val ct = cos(t).toFloat()
        val st = sin(t).toFloat()
        // Turned to face where it's going; still in map space, z up.
        fun turnX(v: Vec) = v.x * ca + v.y * sa
        fun turnY(v: Vec) = -v.x * sa + v.y * ca
        fun screenX(x: Float) = cx + x * scale
        fun screenY(y: Float, z: Float) = cy - (y * ct + z * st) * scale

        // The shadow: everything flattened onto the ground, nudged away from the light.
        if (blur != shadowBlur) {
            shadowPaint.maskFilter = if (blur > 0f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
            shadowBlur = blur
        }
        val flat = groundPoints.map { floatArrayOf(screenX(turnX(it) + 0.15f), screenY(turnY(it) - 0.2f, 0f)) }
        val hull = convexHull(flat)
        if (hull.size >= 3) {
            path.reset()
            path.moveTo(hull[0][0], hull[0][1])
            for (i in 1 until hull.size) path.lineTo(hull[i][0], hull[i][1])
            path.close()
            canvas.drawPath(path, shadowPaint)
        }

        class Drawn(val face: Face, val depth: Float, val light: Float)
        val drawn = ArrayList<Drawn>(faces.size)
        for (face in faces) {
            val n = face.normal
            val nx = turnX(n)
            val ny = turnY(n)
            // Faces turned away from the viewer are hidden behind the others.
            if (n.z * ct - ny * st <= 0.01f) continue
            var depth = 0f
            for (p in face.points) depth += p.z * ct - turnY(p) * st
            val light = 0.55f + 0.55f * max(0f, nx * LIGHT.x + ny * LIGHT.y + n.z * LIGHT.z)
            drawn += Drawn(face, depth / face.points.size, light)
        }
        drawn.sortBy { it.depth }
        paint.strokeWidth = max(0.6f, scale * 0.015f)
        for (d in drawn) {
            path.reset()
            d.face.points.forEachIndexed { i, p ->
                val x = screenX(turnX(p))
                val y = screenY(turnY(p), p.z)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            paint.color = if (d.face.emissive) d.face.color else lit(d.face.color, d.light)
            canvas.drawPath(path, paint)
        }
    }

    private fun lit(color: Int, f: Float) = Color.argb(
        Color.alpha(color),
        min(255f, Color.red(color) * f).toInt(),
        min(255f, Color.green(color) * f).toInt(),
        min(255f, Color.blue(color) * f).toInt(),
    )

    private companion object {
        /** Light from the upper left, the way map apps light their buildings. */
        val LIGHT = Vec(-0.4f, 0.35f, 0.85f).normalized()

        fun convexHull(points: List<FloatArray>): List<FloatArray> {
            val sorted = points.sortedWith(compareBy({ it[0] }, { it[1] }))
            if (sorted.size < 3) return sorted
            fun cross(o: FloatArray, a: FloatArray, b: FloatArray) = (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
            val lower = ArrayList<FloatArray>()
            for (p in sorted) {
                while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) lower.removeAt(lower.size - 1)
                lower += p
            }
            val upper = ArrayList<FloatArray>()
            for (p in sorted.asReversed()) {
                while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) upper.removeAt(upper.size - 1)
                upper += p
            }
            return lower.dropLast(1) + upper.dropLast(1)
        }
    }
}

/** Builds a [Model] out of simple parts. Each part should be convex, so its normals point out. */
class ModelBuilder {
    private val faces = mutableListOf<Face>()

    /** Faces over [points], each a list of indices and its color; normals point away from the part's middle. */
    fun part(points: List<Vec>, polygons: List<Pair<IntArray, Int>>, emissive: Boolean = false) {
        val middle = points.reduce { a, b -> a + b } * (1f / points.size)
        for ((indices, color) in polygons) {
            val pts = indices.map { points[it] }.fold(mutableListOf<Vec>()) { list, p ->
                if (list.isEmpty() || !same(list.last(), p)) list += p
                list
            }
            if (pts.size > 2 && same(pts.first(), pts.last())) pts.removeAt(pts.size - 1)
            if (pts.size < 3) continue
            var n = newell(pts)
            if (n dot n == 0f) continue
            val center = pts.reduce { a, b -> a + b } * (1f / pts.size)
            if (n dot (center - middle) < 0) n = n * -1f
            faces += Face(pts, color, n.normalized(), emissive)
        }
    }

    /**
     * A box from [x0]..[x1], [y0]..[y1], [z0]..[z1], its top pulled in at the sides, front and
     * back for sloping windows and bonnets.
     */
    fun block(
        x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float,
        color: Int,
        top: Int = color, sides: Int = color, front: Int = sides, back: Int = sides,
        insetSide: Float = 0f, insetFront: Float = 0f, insetBack: Float = 0f,
        emissive: Boolean = false,
    ) {
        val bottom = listOf(Vec(x0, y0, z0), Vec(x1, y0, z0), Vec(x1, y1, z0), Vec(x0, y1, z0))
        val upper = listOf(
            Vec(x0 + insetSide, y0 + insetBack, z1), Vec(x1 - insetSide, y0 + insetBack, z1),
            Vec(x1 - insetSide, y1 - insetFront, z1), Vec(x0 + insetSide, y1 - insetFront, z1),
        )
        hexa(bottom, upper, color, top, sides, front, back, emissive)
    }

    /** Any six-sided solid: four corners at the bottom and four at the top, back left first, going round. */
    fun hexa(
        bottom: List<Vec>, top: List<Vec>, color: Int,
        topColor: Int = color, sides: Int = color, front: Int = sides, back: Int = sides,
        emissive: Boolean = false,
    ) {
        part(
            bottom + top,
            listOf(
                intArrayOf(0, 1, 2, 3) to color,
                intArrayOf(4, 5, 6, 7) to topColor,
                intArrayOf(0, 1, 5, 4) to back,
                intArrayOf(1, 2, 6, 5) to sides,
                intArrayOf(2, 3, 7, 6) to front,
                intArrayOf(3, 0, 4, 7) to sides,
            ),
            emissive,
        )
    }

    /** A wheel at ([x], [y]) on the ground, its axle across. */
    fun wheel(x: Float, y: Float, r: Float, width: Float, tyre: Int = TYRE, hub: Int = HUB, segments: Int = 8) {
        val points = mutableListOf<Vec>()
        for (side in listOf(x - width / 2, x + width / 2)) {
            for (i in 0 until segments) {
                val a = 2 * Math.PI * i / segments
                points += Vec(side, y + r * cos(a).toFloat(), r + r * sin(a).toFloat())
            }
        }
        val polygons = mutableListOf<Pair<IntArray, Int>>()
        polygons += IntArray(segments) { it } to hub
        polygons += IntArray(segments) { segments + it } to hub
        for (i in 0 until segments) {
            val j = (i + 1) % segments
            polygons += intArrayOf(i, j, segments + j, segments + i) to tyre
        }
        part(points, polygons)
    }

    /** A solid of revolution around a vertical axis at ([cx], [cy]): [profile] is (radius, height) from bottom to top. */
    fun lathe(cx: Float, cy: Float, profile: List<Pair<Float, Float>>, segments: Int, color: (center: Vec, normal: Vec) -> Int) {
        surface(profile.size, segments) { ring, i ->
            val (r, z) = profile[ring]
            val a = 2 * Math.PI * i / segments
            Vec(cx + r * sin(a).toFloat(), cy + r * cos(a).toFloat(), z)
        }.let { (points, quads) -> colored(points, quads, color) }
    }

    fun ellipsoid(
        cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float,
        segments: Int = 10, rings: Int = 6,
        color: (center: Vec, normal: Vec) -> Int,
    ) {
        surface(rings + 1, segments) { ring, i ->
            val phi = Math.PI * ring / rings - Math.PI / 2
            val a = 2 * Math.PI * i / segments
            Vec(
                cx + rx * (cos(phi) * sin(a)).toFloat(),
                cy + ry * (cos(phi) * cos(a)).toFloat(),
                cz + rz * sin(phi).toFloat(),
            )
        }.let { (points, quads) -> colored(points, quads, color) }
    }

    fun ellipsoid(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, color: Int, segments: Int = 10, rings: Int = 6) =
        ellipsoid(cx, cy, cz, rx, ry, rz, segments, rings) { _, _ -> color }

    private fun surface(rings: Int, segments: Int, at: (ring: Int, i: Int) -> Vec): Pair<List<Vec>, List<IntArray>> {
        val points = ArrayList<Vec>()
        for (ring in 0 until rings) for (i in 0 until segments) points += at(ring, i)
        val quads = ArrayList<IntArray>()
        // The caps at both ends, then the bands between rings.
        quads += IntArray(segments) { it }
        quads += IntArray(segments) { (rings - 1) * segments + it }
        for (ring in 0 until rings - 1) for (i in 0 until segments) {
            val j = (i + 1) % segments
            quads += intArrayOf(ring * segments + i, ring * segments + j, (ring + 1) * segments + j, (ring + 1) * segments + i)
        }
        return points to quads
    }

    private fun colored(points: List<Vec>, quads: List<IntArray>, color: (Vec, Vec) -> Int) {
        val middle = points.reduce { a, b -> a + b } * (1f / points.size)
        part(points, quads.map { q ->
            val pts = q.map { points[it] }
            val center = pts.reduce { a, b -> a + b } * (1f / pts.size)
            var n = newell(pts)
            if (n dot (center - middle) < 0) n = n * -1f
            q to color(center, n.normalized())
        })
    }

    fun build() = Model(faces.toList())

    private fun same(a: Vec, b: Vec) = a.x == b.x && a.y == b.y && a.z == b.z

    private fun newell(pts: List<Vec>): Vec {
        var x = 0f
        var y = 0f
        var z = 0f
        for (i in pts.indices) {
            val c = pts[i]
            val n = pts[(i + 1) % pts.size]
            x += (c.y - n.y) * (c.z + n.z)
            y += (c.z - n.z) * (c.x + n.x)
            z += (c.x - n.x) * (c.y + n.y)
        }
        return Vec(x, y, z)
    }

    companion object {
        const val TYRE = 0xFF1C1C1C.toInt()
        const val HUB = 0xFF9E9E9E.toInt()
        const val GLASS = 0xFF34485F.toInt()
        const val HEADLIGHT = 0xFFFFF6C8.toInt()
        const val TAILLIGHT = 0xFFE53935.toInt()
    }
}
