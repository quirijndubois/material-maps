package app.materialmaps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpException(val code: Int, val body: String) : IOException("HTTP $code")

internal suspend fun httpGet(url: String, readTimeout: Int = 15_000): String = String(httpGetBytes(url, readTimeout), Charsets.UTF_8)

/**
 * The body at [url]. Cancelling closes the connection at once, so a slow server doesn't keep
 * a lookup going (and showing as loading) after it's no longer wanted.
 */
internal suspend fun httpGetBytes(url: String, readTimeout: Int = 15_000): ByteArray = suspendCancellableCoroutine { continuation ->
    val connection = URL(url).openConnection() as HttpURLConnection
    continuation.invokeOnCancellation { runCatching { connection.disconnect() } }
    Dispatchers.IO.asExecutor().execute {
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = readTimeout
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw HttpException(code, body)
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            if (continuation.isActive) continuation.resume(bytes)
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resumeWithException(e)
        } finally {
            connection.disconnect()
        }
    }
}
