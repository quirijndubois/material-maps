package app.materialmaps

import app.materialmaps.ModelBuilder.Companion.GLASS
import app.materialmaps.ModelBuilder.Companion.HEADLIGHT
import app.materialmaps.ModelBuilder.Companion.TAILLIGHT
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Low-poly models of the [Vehicle]s, nose forward (+y). See [Model] for the units. */
object VehicleModels {
    private fun c(hex: Long) = hex.toInt()

    /** Head and tail lights on both sides of a body [halfWidth] wide, from [back] to [front]. */
    private fun ModelBuilder.lights(halfWidth: Float, back: Float, front: Float, z0: Float, z1: Float, size: Float = 0.3f) {
        for (s in listOf(-1f, 1f)) {
            val outer = s * (halfWidth - 0.08f)
            val inner = s * (halfWidth - 0.08f - size)
            block(minOf(outer, inner), maxOf(outer, inner), front - 0.03f, front + 0.02f, z0, z1, HEADLIGHT, emissive = true)
            block(minOf(outer, inner), maxOf(outer, inner), back - 0.02f, back + 0.03f, z0, z1, TAILLIGHT, emissive = true)
        }
    }

    private fun ModelBuilder.fourWheels(x: Float, front: Float, back: Float, r: Float, width: Float) {
        for (s in listOf(-1f, 1f)) {
            wheel(s * x, front, r, width)
            wheel(s * x, back, r, width)
        }
    }

    val taxi: Model by lazy {
        ModelBuilder().apply {
            val yellow = c(0xFFF7C21B)
            fourWheels(0.86f, 1.35f, -1.35f, 0.34f, 0.26f)
            block(-0.9f, 0.9f, -2.2f, 2.2f, 0.3f, 0.85f, yellow, insetSide = 0.06f, insetFront = 0.25f, insetBack = 0.1f)
            block(-0.78f, 0.78f, -1.0f, 0.75f, 0.85f, 1.38f, yellow, sides = GLASS, insetSide = 0.1f, insetFront = 0.38f, insetBack = 0.3f)
            // The sign on the roof, and the checkered band down the sides.
            block(-0.3f, 0.3f, -0.25f, 0.05f, 1.38f, 1.58f, c(0xFFFFFDF2), insetSide = 0.03f)
            for (s in listOf(-1f, 1f)) {
                for (i in 0 until 8) {
                    if (i % 2 == 1) continue
                    val y = -1.2f + i * 0.3f
                    block(minOf(s * 0.89f, s * 0.92f), maxOf(s * 0.89f, s * 0.92f), y, y + 0.3f, 0.55f, 0.66f, c(0xFF1A1A1A))
                }
            }
            lights(0.9f, -2.2f, 2.2f, 0.55f, 0.7f)
        }.build()
    }

    /** The little lime-green Mini with a black bonnet, and the padlock on the door. */
    val beanMini: Model by lazy {
        ModelBuilder().apply {
            val green = c(0xFFA4B83C)
            fourWheels(0.72f, 1.0f, -1.0f, 0.26f, 0.2f)
            block(-0.75f, 0.75f, -1.5f, 1.5f, 0.25f, 0.78f, green, insetSide = 0.04f, insetFront = 0.1f, insetBack = 0.05f)
            block(-0.7f, 0.7f, -1.05f, 0.55f, 0.78f, 1.3f, green, sides = GLASS, insetSide = 0.06f, insetFront = 0.16f, insetBack = 0.08f)
            block(-0.67f, 0.67f, 0.58f, 1.4f, 0.78f, 0.81f, c(0xFF1E1E1E))
            block(0.74f, 0.8f, 0.05f, 0.25f, 0.42f, 0.6f, c(0xFFB8B8B8))
            // A chrome grille and round-ish headlights.
            block(-0.35f, 0.35f, 1.49f, 1.53f, 0.38f, 0.62f, c(0xFFCFD8DC))
            lights(0.75f, -1.5f, 1.5f, 0.5f, 0.65f, size = 0.2f)
        }.build()
    }

    /** The stainless-steel time machine with gull-wing doors. */
    val delorean: Model by lazy {
        ModelBuilder().apply {
            val steel = c(0xFFB9C0C4)
            fourWheels(0.86f, 1.3f, -1.35f, 0.33f, 0.26f)
            // A wedge: low at the nose, high at the back.
            hexa(
                listOf(Vec(-0.95f, -2.1f, 0.28f), Vec(0.95f, -2.1f, 0.28f), Vec(0.95f, 2.1f, 0.28f), Vec(-0.95f, 2.1f, 0.28f)),
                listOf(Vec(-0.9f, -2.05f, 0.82f), Vec(0.9f, -2.05f, 0.82f), Vec(0.85f, 2.0f, 0.55f), Vec(-0.85f, 2.0f, 0.55f)),
                steel,
            )
            hexa(
                listOf(Vec(-0.72f, -0.95f, 0.74f), Vec(0.72f, -0.95f, 0.74f), Vec(0.72f, 0.55f, 0.65f), Vec(-0.72f, 0.55f, 0.65f)),
                listOf(Vec(-0.6f, -0.8f, 1.15f), Vec(0.6f, -0.8f, 1.15f), Vec(0.6f, -0.15f, 1.15f), Vec(-0.6f, -0.15f, 1.15f)),
                steel, sides = GLASS, front = GLASS, back = c(0xFF2A2A2A),
            )
            // Louvers over the rear window, and the glowing coil out back.
            for (i in 0 until 5) {
                val y = -1.95f + i * 0.18f
                block(-0.6f, 0.6f, y, y + 0.09f, 0.76f, 0.84f, c(0xFF262626))
            }
            block(-0.25f, 0.25f, -2.16f, -2.1f, 0.35f, 0.5f, c(0xFF80DEEA), emissive = true)
            lights(0.95f, -2.1f, 2.1f, 0.42f, 0.5f)
        }.build()
    }

    /** The white racing Beetle with red, white and blue stripes. */
    val herbie: Model by lazy {
        ModelBuilder().apply {
            val white = c(0xFFF4F3EE)
            fun stripes(center: Vec, normal: Vec): Int = when {
                normal.z < 0.45f -> white
                abs(center.x) < 0.1f -> c(0xFFD32F2F)
                abs(center.x) < 0.16f -> white
                abs(center.x) < 0.24f -> c(0xFF1E3F8F)
                else -> white
            }
            fourWheels(0.8f, 1.25f, -1.3f, 0.32f, 0.22f)
            ellipsoid(0f, 0f, 0.55f, 0.88f, 2.05f, 0.42f, segments = 16, rings = 8, color = ::stripes)
            ellipsoid(0f, -0.3f, 0.82f, 0.68f, 0.98f, 0.5f, segments = 16, rings = 8) { center, normal ->
                if (normal.z < 0.55f && center.z > 0.9f) GLASS else stripes(center, normal)
            }
            // The number on the bonnet.
            ellipsoid(0f, 1.35f, 0.9f, 0.22f, 0.22f, 0.03f, c(0xFFFAFAFA), segments = 10, rings = 3)
            block(-0.05f, 0.05f, 1.25f, 1.45f, 0.9f, 0.94f, c(0xFF1A1A1A))
            block(-0.75f, 0.75f, 2.0f, 2.1f, 0.3f, 0.4f, c(0xFFCFD8DC))
            block(-0.75f, 0.75f, -2.1f, -2.0f, 0.3f, 0.4f, c(0xFFCFD8DC))
            lights(0.8f, -1.95f, 1.95f, 0.5f, 0.62f, size = 0.22f)
        }.build()
    }

    /** The battered round freighter with its two front prongs and the cockpit off to the side. */
    val falcon: Model by lazy {
        ModelBuilder().apply {
            val hull = c(0xFFCBCBC4)
            val dark = c(0xFF8E8E88)
            lathe(0f, 0f, listOf(0f to 0.25f, 1.7f to 0.25f, 1.76f to 0.4f, 1.6f to 0.56f, 0.9f to 0.74f, 0f to 0.8f), 16) { center, _ ->
                // Panels in two shades, like the real thing's patchwork.
                if (((center.x * 2).toInt() + (center.y * 2).toInt()) % 3 == 0) dark else hull
            }
            // The two prongs: wide where they leave the hull, tapering on the outside to a
            // squared-off tip, and sloping down to it. A gap between them, straight inner edges.
            for (s in listOf(-1f, 1f)) {
                fun v(x: Float, y: Float, z: Float) = Vec(s * x, y, z)
                fun side(points: List<Vec>) = if (s < 0) points else listOf(points[1], points[0], points[3], points[2])
                // Root, from inside the hull to halfway.
                hexa(
                    side(listOf(v(0.28f, 0.8f, 0.3f), v(1.12f, 0.8f, 0.3f), v(0.82f, 1.95f, 0.3f), v(0.28f, 1.95f, 0.3f))),
                    side(listOf(v(0.32f, 0.8f, 0.6f), v(1.02f, 0.8f, 0.58f), v(0.76f, 1.95f, 0.52f), v(0.32f, 1.95f, 0.52f))),
                    dark, topColor = hull, sides = hull,
                )
                // Tip, narrower and lower, its top edge bevelled.
                hexa(
                    side(listOf(v(0.28f, 1.95f, 0.3f), v(0.82f, 1.95f, 0.3f), v(0.66f, 2.65f, 0.32f), v(0.3f, 2.65f, 0.32f))),
                    side(listOf(v(0.32f, 1.95f, 0.52f), v(0.76f, 1.95f, 0.52f), v(0.6f, 2.55f, 0.45f), v(0.34f, 2.55f, 0.45f))),
                    dark, topColor = hull, sides = hull,
                )
                // Greebles: pipes and plates along the top.
                block(minOf(s * 0.42f, s * 0.6f), maxOf(s * 0.42f, s * 0.6f), 1.05f, 1.75f, 0.54f, 0.62f, dark)
                block(minOf(s * 0.66f, s * 0.8f), maxOf(s * 0.66f, s * 0.8f), 1.0f, 1.3f, 0.54f, 0.6f, dark)
                block(minOf(s * 0.4f, s * 0.56f), maxOf(s * 0.4f, s * 0.56f), 2.05f, 2.4f, 0.47f, 0.53f, dark)
            }
            // Cockpit tube on the right, its window at the front.
            block(1.05f, 1.5f, 0.2f, 0.9f, 0.32f, 0.6f, hull)
            block(1.4f, 1.85f, 0.4f, 1.55f, 0.32f, 0.66f, hull, insetFront = 0.12f, insetSide = 0.04f)
            block(1.48f, 1.77f, 1.55f, 1.72f, 0.38f, 0.58f, GLASS, insetFront = 0.08f)
            // Dish, gun turret, and the blue engine glow along the back.
            ellipsoid(-0.65f, 0.35f, 0.88f, 0.38f, 0.38f, 0.08f, c(0xFFE0E0DA), segments = 10, rings = 3)
            block(-0.12f, 0.12f, -0.12f, 0.12f, 0.78f, 0.9f, dark)
            for (i in -5..5) {
                val a = Math.PI + i * 0.11
                val x = (1.74 * sin(a)).toFloat()
                val y = (1.74 * cos(a)).toFloat()
                block(x - 0.16f, x + 0.16f, y - 0.03f, y + 0.03f, 0.28f, 0.5f, c(0xFF7FE3FF), emissive = true)
            }
        }.build()
    }

    /** The red London double-decker. */
    val routemaster: Model by lazy {
        ModelBuilder().apply {
            val red = c(0xFFC8102E)
            for (s in listOf(-1f, 1f)) {
                wheel(s * 0.86f, 1.75f, 0.45f, 0.3f)
                wheel(s * 0.86f, -1.55f, 0.45f, 0.3f)
            }
            block(-1f, 1f, -2.6f, 2.6f, 0.3f, 1.75f, red)
            block(-1.01f, 1.01f, -2.61f, 2.61f, 1.75f, 1.86f, c(0xFFF2E6C8))
            block(-1f, 1f, -2.6f, 2.6f, 1.86f, 2.75f, red, insetSide = 0.06f, insetFront = 0.08f, insetBack = 0.08f)
            // Windows on both decks, all round.
            for (z in listOf(1.05f to 1.55f, 2.05f to 2.5f)) {
                for (s in listOf(-1f, 1f)) {
                    block(minOf(s * 1.0f, s * 1.025f), maxOf(s * 1.0f, s * 1.025f), -2.3f, 2.35f, z.first, z.second, GLASS)
                }
                block(-0.88f, 0.88f, 2.6f, 2.63f, z.first, z.second, GLASS)
                block(-0.88f, 0.88f, -2.63f, -2.6f, z.first, z.second, GLASS)
            }
            lights(1f, -2.6f, 2.6f, 0.5f, 0.68f, size = 0.26f)
        }.build()
    }

    /** The groovy green-and-blue van with flowers. */
    val mysteryVan: Model by lazy {
        ModelBuilder().apply {
            val green = c(0xFF5DAE49)
            val blue = c(0xFF7EC8DA)
            fourWheels(0.86f, 1.45f, -1.45f, 0.36f, 0.26f)
            block(-0.95f, 0.95f, -2.2f, 2.25f, 0.3f, 1.0f, green, insetFront = 0.05f)
            hexa(
                listOf(Vec(-0.95f, -2.2f, 1.0f), Vec(0.95f, -2.2f, 1.0f), Vec(0.95f, 2.2f, 1.0f), Vec(-0.95f, 2.2f, 1.0f)),
                listOf(Vec(-0.9f, -2.15f, 1.9f), Vec(0.9f, -2.15f, 1.9f), Vec(0.9f, 1.5f, 1.9f), Vec(-0.9f, 1.5f, 1.9f)),
                blue, front = GLASS,
            )
            // Orange flowers down the sides, a roof rack, and a spare tyre on the nose.
            for (s in listOf(-1f, 1f)) {
                for (y in listOf(-1.4f, -0.3f, 0.8f)) {
                    block(minOf(s * 0.95f, s * 0.985f), maxOf(s * 0.95f, s * 0.985f), y, y + 0.35f, 0.5f, 0.8f, c(0xFFF28C28))
                }
                block(minOf(s * 0.95f, s * 0.98f), maxOf(s * 0.95f, s * 0.98f), 0.3f, 1.4f, 1.25f, 1.65f, GLASS)
            }
            block(-0.7f, 0.7f, -1.6f, 0.9f, 1.9f, 1.98f, c(0xFF3E3E3E))
            block(-0.3f, 0.3f, 2.25f, 2.35f, 0.4f, 0.95f, ModelBuilder.TYRE)
            lights(0.95f, -2.2f, 2.25f, 0.55f, 0.72f)
        }.build()
    }

    /** A classic silver saucer with someone small and green under the dome. */
    val saucer: Model by lazy {
        ModelBuilder().apply {
            val metal = c(0xFFB8C2CC)
            lathe(0f, 0f, listOf(0f to 0.55f, 0.8f to 0.6f, 1.7f to 0.95f, 1.75f to 1.05f, 1.0f to 1.25f, 0f to 1.3f), 16) { _, _ -> metal }
            for (i in 0 until 12) {
                val a = 2 * Math.PI * i / 12
                val x = (1.74 * sin(a)).toFloat()
                val y = (1.74 * cos(a)).toFloat()
                val color = if (i == 0) c(0xFF76FF03) else if (i % 2 == 0) c(0xFFFFEB3B) else c(0xFFFF7043)
                block(x - 0.09f, x + 0.09f, y - 0.09f, y + 0.09f, 0.96f, 1.08f, color, emissive = true)
            }
            ellipsoid(0f, 0f, 1.45f, 0.26f, 0.26f, 0.3f, c(0xFF7CB342))
            block(-0.16f, -0.05f, 0.2f, 0.26f, 1.52f, 1.6f, c(0xFF111111))
            block(0.05f, 0.16f, 0.2f, 0.26f, 1.52f, 1.6f, c(0xFF111111))
            ellipsoid(0f, 0f, 1.28f, 0.72f, 0.72f, 0.6f, c(0x8890D5FF), segments = 12, rings = 6)
        }.build()
    }

    val rubberDuck: Model by lazy {
        ModelBuilder().apply {
            val yellow = c(0xFFFFD54F)
            ellipsoid(0f, -0.2f, 0.6f, 0.95f, 1.25f, 0.6f, yellow, segments = 12, rings = 6)
            for (s in listOf(-1f, 1f)) ellipsoid(s * 0.82f, -0.25f, 0.75f, 0.2f, 0.6f, 0.25f, c(0xFFF2B830), segments = 8, rings = 4)
            block(-0.22f, 0.22f, -1.6f, -1.25f, 0.75f, 1.15f, yellow, insetBack = 0.2f, insetSide = 0.06f)
            ellipsoid(0f, 0.8f, 1.38f, 0.55f, 0.55f, 0.55f, yellow, segments = 12, rings = 6)
            block(-0.26f, 0.26f, 1.25f, 1.85f, 1.18f, 1.36f, c(0xFFFF8F00), insetFront = 0.14f, insetSide = 0.08f)
            for (s in listOf(-1f, 1f)) block(minOf(s * 0.22f, s * 0.36f), maxOf(s * 0.22f, s * 0.36f), 1.2f, 1.32f, 1.48f, 1.6f, c(0xFF111111))
        }.build()
    }
}
