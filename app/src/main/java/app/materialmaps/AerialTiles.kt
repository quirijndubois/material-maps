package app.materialmaps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.ByteArrayOutputStream

const val AERIAL_HOST = "service.pdok.nl"

/**
 * PDOK's aerial photos stop a few hundred metres past the Dutch border and are plain white
 * beyond it. MapLibre can't clip a raster, so its tiles pass through here: the white that
 * reaches the tile's edge becomes see-through, and the satellite photos underneath show.
 */
object AerialTiles : Interceptor {
    /** Hands MapLibre a client with this in it. Call before the first map is made. */
    fun install() {
        // MapLibre's own client allows 20 requests per host; tiles come in bursts.
        val dispatcher = Dispatcher().apply { maxRequestsPerHost = 20 }
        val client = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor(Loading.Tiles)
            .addInterceptor(ContourTiles)
            .addInterceptor(TileUserAgent)
            .addInterceptor(Satellite.CloudTiles)
            .addInterceptor(this)
            .build()
        HttpRequestUtil.setOkHttpClient(client)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        // The same host also serves elevation and parcels, which are left as they are.
        val url = chain.request().url
        if (url.host != AERIAL_HOST || "luchtfotorgb" !in url.encodedPath || response.code != 200) return response
        val body = response.body ?: return response
        val bytes = body.bytes()
        val png = runCatching { clearBeyondBorder(bytes) }.getOrNull()
            ?: return response.newBuilder().body(bytes.toResponseBody(body.contentType())).build()
        return response.newBuilder()
            .header("Content-Type", "image/png")
            .body(png.toResponseBody("image/png".toMediaType()))
            .build()
    }

    /**
     * The tile as a PNG with its no-data white cleared, or null if it has none. That white is
     * pure 255 and always runs to the tile's edge, so it's filled from pure white edge pixels
     * through near-white ones (the JPEG blurs its border). A bright roof that happens to touch
     * the edge is left alone unless it's a sizable part of the tile.
     */
    private fun clearBeyondBorder(jpeg: ByteArray): ByteArray? {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.recycle()

        fun white(p: Int) = p and 0xFFFFFF == 0xFFFFFF
        fun nearWhite(p: Int) = minOf(Color.red(p), Color.green(p), Color.blue(p)) >= 240

        val cleared = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var top = 0
        fun seed(i: Int) {
            if (!cleared[i] && white(pixels[i])) { cleared[i] = true; stack[top++] = i }
        }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        if (top == 0) return null

        var count = 0
        while (top > 0) {
            val i = stack[--top]
            count++
            val x = i % w
            val y = i / w
            for (n in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1)) {
                if (n >= 0 && !cleared[n] && nearWhite(pixels[n])) { cleared[n] = true; stack[top++] = n }
            }
        }
        if (count < w * h / 100) return null

        for (i in pixels.indices) if (cleared[i]) pixels[i] = Color.TRANSPARENT
        val out = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { stream ->
            out.compress(Bitmap.CompressFormat.PNG, 100, stream)
            out.recycle()
            stream.toByteArray()
        }
    }
}

/** OpenRailwayMap only serves apps that say who they are. */
private object TileUserAgent : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.host.endsWith("openrailwaymap.org")) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("User-Agent", USER_AGENT).build())
    }
}
