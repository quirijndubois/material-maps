package app.materialmaps

import android.content.Context
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A dark version of OpenFreeMap's detailed "liberty" style, in the app's Material You colors.
 * Their own dark style leaves out POIs, 3D buildings and most landuse, so instead we restyle
 * liberty: land takes the theme's neutral surface tone, roads are lighter surface tones with
 * the important ones tinted by the theme's tertiary color, and water and greenery keep their
 * hue at low, Material-like chroma. Labels become light text on dark halos.
 *
 * The light style is cached as downloaded; the recoloring runs on every start because it
 * depends on the wallpaper colors.
 */
object DarkStyle {
    private const val CACHE_FILE = "liberty-style.json"

    /** Theme colors (ARGB) the map is built from. */
    class Palette(surface: Int, accent: Int) {
        internal val neutral: Hsl = surface.toHsl()
        internal val accent: Hsl = accent.toHsl()
    }

    /** The cached light style, recolored, for an instant start. */
    suspend fun cached(context: Context, palette: Palette): String? = withContext(Dispatchers.IO) {
        File(context.cacheDir, CACHE_FILE).takeIf { it.exists() }?.readText()
    }?.let { withContext(Dispatchers.Default) { runCatching { darken(it, palette) }.getOrNull() } }

    /** Downloads the light style (falling back to the cache) and recolors it. */
    suspend fun load(context: Context, lightStyleUrl: String, palette: Palette): String? {
        val cache = File(context.cacheDir, CACHE_FILE)
        val light = try {
            httpGet(lightStyleUrl).also { json -> withContext(Dispatchers.IO) { cache.writeText(json) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.IO) { cache.takeIf { it.exists() }?.readText() }
        } ?: return null
        return withContext(Dispatchers.Default) { runCatching { darken(light, palette) }.getOrNull() }
    }

    fun darken(styleJson: String, palette: Palette): String {
        val style = JSONObject(styleJson)
        val layers = style.getJSONArray("layers")
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            val id = layer.optString("id")
            val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
            when (layer.optString("type")) {
                // The low-zoom shaded relief is a light image; keep it from glowing.
                "raster" -> {
                    paint.put("raster-brightness-max", 0.2)
                    paint.put("raster-saturation", -0.8)
                }
                // Pattern fills are light sprites we can't recolor; just tone them down.
                "fill" -> if (paint.has("fill-pattern")) paint.put("fill-opacity", 0.12)
            }
            for (key in paint.keys().asSequence().toList()) {
                if (!key.endsWith("color")) continue
                val transform: (Hsl) -> Hsl = when {
                    key == "text-color" || key == "icon-color" -> { c -> label(c, palette) }
                    key == "text-halo-color" || key == "icon-halo-color" -> { c -> halo(c, palette) }
                    key == "fill-extrusion-color" -> { c -> palette.neutral.tone(0.17, c.a) }
                    id == "building" -> { c -> palette.neutral.tone(if (key == "fill-outline-color") 0.18 else 0.15, c.a) }
                    key == "line-color" -> { c -> line(c, palette) }
                    else -> { c -> area(c, palette) }
                }
                paint.put(key, recolor(paint.get(key), transform))
            }
        }
        return style.toString()
    }

    /** Recolors every color string in a value, including the ones inside expressions. */
    private fun recolor(value: Any, transform: (Hsl) -> Hsl): Any = when (value) {
        is String -> parse(value)?.let { format(transform(it)) } ?: value
        is JSONArray -> JSONArray().also { out -> for (i in 0 until value.length()) out.put(recolor(value.get(i), transform)) }
        is JSONObject -> JSONObject().also { out -> value.keys().forEach { out.put(it, recolor(value.get(it), transform)) } }
        else -> value
    }

    /**
     * Light-style lightness to dark tone. Keeps the original order (water below land below
     * roads) but squeezes everything into Material's dark surface range; land lands near
     * surfaceContainer, white roads near surfaceContainerHighest.
     */
    private fun tone(l: Double): Double {
        val points = listOf(0.0 to 0.05, 0.70 to 0.085, 0.85 to 0.105, 0.95 to 0.125, 1.0 to 0.23)
        val upper = points.indexOfFirst { it.first >= l }.coerceAtLeast(1)
        val (x0, y0) = points[upper - 1]
        val (x1, y1) = points[upper]
        return y0 + (y1 - y0) * ((l - x0) / (x1 - x0)).coerceIn(0.0, 1.0)
    }

    /** Land, landuse, water: neutral unless clearly colored, then the same hue, muted. */
    private fun area(c: Hsl, p: Palette): Hsl =
        if (c.chroma < 0.08) p.neutral.tone(tone(c.l), c.a)
        else Hsl(c.h, (c.chroma * 1.2).coerceIn(0.18, 0.3), tone(c.l) + 0.02, c.a)

    /**
     * Important roads are orange/yellow in liberty: a darker casing under a lighter fill. The
     * casing becomes a dark outline and the fill a light surface tone with a hint of the theme
     * accent, so main roads stand out without turning into bright bands.
     */
    private fun line(c: Hsl, p: Palette): Hsl = when {
        c.chroma > 0.15 && c.h in 15.0..70.0 && c.l < 0.75 -> p.neutral.tone(0.10, c.a)
        c.chroma > 0.15 && c.h in 15.0..70.0 ->
            Hsl(p.accent.h, min(p.accent.s, 0.18), 0.25 + 0.25 * (1 - c.l), c.a)
        else -> area(c, p)
    }

    private fun label(c: Hsl, p: Palette): Hsl =
        if (c.chroma < 0.15) p.neutral.tone(0.93 - 0.5 * c.l, c.a)
        else Hsl(c.h, min(c.s, 0.4), 0.78, c.a)

    private fun halo(c: Hsl, p: Palette) = p.neutral.tone(0.09, c.a * 0.9)

    data class Hsl(val h: Double, val s: Double, val l: Double, val a: Double) {
        /** How colorful the color is regardless of lightness; HSL saturation overstates pale colors. */
        val chroma get() = s * (1 - abs(2 * l - 1))

        /** This hue at another lightness, with the slight tint Material gives its neutrals. */
        fun tone(l: Double, a: Double) = Hsl(h, min(s, 0.12), l, a)
    }

    private fun Int.toHsl(): Hsl {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(this, hsl)
        return Hsl(hsl[0].toDouble(), hsl[1].toDouble(), hsl[2].toDouble(), 1.0)
    }

    private val hex = Regex("^#([0-9a-fA-F]{3,8})$")
    private val functional = Regex("""^(rgba?|hsla?)\(([^)]*)\)$""")

    private fun parse(text: String): Hsl? {
        val value = text.trim()
        hex.matchEntire(value)?.let { m ->
            val digits = m.groupValues[1]
            val full = when (digits.length) {
                3, 4 -> digits.map { "$it$it" }.joinToString("")
                6, 8 -> digits
                else -> return null
            }
            val r = full.substring(0, 2).toInt(16)
            val g = full.substring(2, 4).toInt(16)
            val b = full.substring(4, 6).toInt(16)
            val a = if (full.length == 8) full.substring(6, 8).toInt(16) / 255.0 else 1.0
            return rgbToHsl(r / 255.0, g / 255.0, b / 255.0, a)
        }
        functional.matchEntire(value)?.let { m ->
            val parts = m.groupValues[2].split(',').map { it.trim().removeSuffix("%").toDoubleOrNull() ?: return null }
            if (parts.size < 3) return null
            val a = parts.getOrElse(3) { 1.0 }
            return if (m.groupValues[1].startsWith("rgb")) {
                rgbToHsl(parts[0] / 255, parts[1] / 255, parts[2] / 255, a)
            } else {
                Hsl(parts[0], parts[1] / 100, parts[2] / 100, a)
            }
        }
        return null
    }

    private fun format(c: Hsl) = String.format(
        Locale.US, "hsla(%d,%d%%,%.1f%%,%.3f)",
        c.h.roundToInt(), (c.s.coerceIn(0.0, 1.0) * 100).roundToInt(), c.l.coerceIn(0.0, 1.0) * 100, c.a,
    )

    private fun rgbToHsl(r: Double, g: Double, b: Double, a: Double): Hsl {
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val l = (high + low) / 2
        if (high == low) return Hsl(0.0, 0.0, l, a)
        val d = high - low
        val s = d / (1 - abs(2 * l - 1))
        val h = when (high) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
        return Hsl((h + 360) % 360, s, l, a)
    }
}
