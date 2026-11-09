package app.materialmaps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.tan

/**
 * A part of the world to download: a continent, country, or a province or state, as Geofabrik
 * divides up OpenStreetMap (https://download.geofabrik.de). [area] is how much of the map it
 * covers, as a share of the whole world map.
 */
class Region(
    val id: String,
    val name: String,
    val parent: String?,
    val outline: MultiPolygon,
    val bounds: LatLngBounds,
    val area: Double,
) {
    /** Roughly how many map tiles it takes, at every zoom up to the most detailed. */
    val tiles: Long get() = (area * TILES_AT_MAX_ZOOM * 4 / 3).toLong()

    /** Roughly how big the map is: an average tile, over land and sea, is about 10 kB. */
    val mapBytes: Long get() = tiles * 10_000

    /** Whether it's small enough to download in one go. */
    val downloadable: Boolean get() = tiles <= MAX_TILES

    companion object {
        private const val TILES_AT_MAX_ZOOM = 268_435_456.0 // 4^14
        /** Larger than a big country: pick a part of it instead. */
        const val MAX_TILES = 2_500_000L
    }
}

/** The regions there are to download, by Geofabrik, kept on the phone for a month at a time. */
object RegionCatalog {
    private const val INDEX = "https://download.geofabrik.de/index-v1.json"
    private const val MAX_AGE = 30L * 24 * 3600 * 1000
    private val lock = Mutex()
    private var regions: List<Region>? = null

    suspend fun all(context: Context): List<Region> = lock.withLock {
        regions ?: withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "regions/geofabrik.json")
            val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE
            val json = if (fresh) file.readText() else runCatching {
                httpGet(INDEX, readTimeout = 60_000).also {
                    file.parentFile?.mkdirs()
                    file.writeText(it)
                }
            }.getOrElse { if (file.exists()) file.readText() else throw it }
            withContext(Dispatchers.Default) { parse(json) }
        }.also { regions = it }
    }

    private fun parse(json: String): List<Region> {
        val features = JSONObject(json).getJSONArray("features")
        return (0 until features.length()).mapNotNull { i ->
            val feature = features.getJSONObject(i)
            val properties = feature.getJSONObject("properties")
            val geometry = feature.optJSONObject("geometry") ?: return@mapNotNull null
            val polygons = when (geometry.optString("type")) {
                "MultiPolygon" -> geometry.getJSONArray("coordinates").let { a -> (0 until a.length()).map { a.getJSONArray(it) } }
                "Polygon" -> listOf(geometry.getJSONArray("coordinates"))
                else -> return@mapNotNull null
            }
            var west = 180.0
            var east = -180.0
            var south = 90.0
            var north = -90.0
            var area = 0.0
            val outline = polygons.map { rings ->
                (0 until rings.length()).map { r ->
                    val ring = rings.getJSONArray(r)
                    val points = (0 until ring.length()).map { p ->
                        val c = ring.getJSONArray(p)
                        val lon = c.getDouble(0)
                        val lat = c.getDouble(1)
                        if (lon < west) west = lon
                        if (lon > east) east = lon
                        if (lat < south) south = lat
                        if (lat > north) north = lat
                        Point.fromLngLat(lon, lat)
                    }
                    // Holes take their area away again.
                    area += (if (r == 0) 1 else -1) * abs(mercatorArea(points))
                    points
                }
            }
            Region(
                id = properties.getString("id"),
                name = properties.getString("name"),
                parent = properties.optString("parent").ifBlank { null },
                outline = MultiPolygon.fromLngLats(outline),
                bounds = LatLngBounds.from(north.coerceAtMost(85.0), east, south.coerceAtLeast(-85.0), west),
                area = area,
            )
        }
    }

    /** A ring's area on the world map, as a share of all of it. */
    private fun mercatorArea(ring: List<Point>): Double {
        fun x(p: Point) = (p.longitude() + 180) / 360
        fun y(p: Point): Double {
            val lat = Math.toRadians(p.latitude().coerceIn(-85.0, 85.0))
            return (1 - ln(tan(PI / 4 + lat / 2)) / PI) / 2
        }
        var sum = 0.0
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            sum += x(a) * y(b) - x(b) * y(a)
        }
        return sum / 2
    }

    /** Folds accents and case, so "cote" finds Côte d'Ivoire. */
    fun searchable(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
}

/**
 * A region on the phone: its map (in MapLibre's offline database) and its routing data.
 * [mapEstimate] is roughly how big the map was expected to be, in bytes.
 */
data class SavedRegion(val id: String, val name: String, val mapRegion: Long?, val segments: List<String>, val mapEstimate: Long = 0)

/**
 * The regions downloaded for use without a connection, and their downloads: the map through
 * [OfflineMaps], the routing data for directions (see [OfflineRouting]) here. Routing data
 * comes in squares of 5° that regions can share; it's only deleted with the last one using it.
 */
object OfflineRegions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _saved = MutableStateFlow<List<SavedRegion>>(emptyList())
    val saved: StateFlow<List<SavedRegion>> = _saved.asStateFlow()

    /** A routing data download: bytes so far of [total] (0 while unknown), or what went wrong. */
    data class Transfer(val done: Long, val total: Long, val error: String? = null)

    private val _transfers = MutableStateFlow<Map<String, Transfer>>(emptyMap())
    val transfers: StateFlow<Map<String, Transfer>> = _transfers.asStateFlow()
    private val jobs = HashMap<String, Job>()
    private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "regions/saved.json")

    /** Reads what's on the phone, and carries on with downloads cut short last time. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val list = runCatching {
            val a = JSONArray(file(context).readText())
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                SavedRegion(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    mapRegion = if (o.has("map")) o.getLong("map") else null,
                    segments = o.getJSONArray("segments").let { s -> (0 until s.length()).map { s.getString(it) } },
                    mapEstimate = o.optLong("mapEstimate"),
                )
            }
        }.getOrDefault(emptyList())
        _saved.value = list
        list.flatMap { it.segments }.distinct().forEach { fetch(context.applicationContext, it) }
    }

    private fun store(context: Context) {
        val a = JSONArray()
        for (r in _saved.value) {
            a.put(
                JSONObject().put("id", r.id).put("name", r.name).put("segments", JSONArray(r.segments)).put("mapEstimate", r.mapEstimate)
                    .apply { r.mapRegion?.let { put("map", it) } }
            )
        }
        runCatching {
            file(context).parentFile?.mkdirs()
            file(context).writeText(a.toString())
        }
    }

    /** The routing data [region] needs that isn't on the phone yet, with each file's size. */
    suspend fun missingSegments(context: Context, region: Region): Map<String, Long> =
        OfflineRouting.segmentsIn(region.bounds)
            .filterNot { OfflineRouting.segmentFile(context, it).exists() }
            .associateWith { name -> runCatching { contentLength(OfflineRouting.segmentUrl(name)) }.getOrDefault(0L) }

    /** Downloads [region]: its map in [styleUrl] through [maps], and its routing data. */
    fun download(context: Context, region: Region, maps: OfflineMaps, styleUrl: String, pixelRatio: Float) {
        if (_saved.value.any { it.id == region.id }) return
        val segments = OfflineRouting.segmentsIn(region.bounds)
        _saved.update { it + SavedRegion(region.id, region.name, null, segments, region.mapBytes) }
        store(context)
        maps.downloadOutline(region.name, styleUrl, region.outline, pixelRatio) { mapId ->
            _saved.update { list -> list.map { if (it.id == region.id) it.copy(mapRegion = mapId) else it } }
            store(context)
        }
        segments.forEach { fetch(context.applicationContext, it) }
    }

    /** Deletes [region]'s map, and the routing data no other region on the phone uses. */
    fun delete(context: Context, region: SavedRegion, maps: OfflineMaps) {
        region.mapRegion?.let { maps.delete(it) }
        _saved.update { list -> list.filterNot { it.id == region.id } }
        store(context)
        val stillUsed = _saved.value.flatMap { it.segments }.toSet()
        for (name in region.segments - stillUsed) {
            synchronized(jobs) { jobs.remove(name) }?.cancel()
            _transfers.update { it - name }
            OfflineRouting.segmentFile(context, name).delete()
            File(OfflineRouting.segmentDir(context), "$name.rd5.part").delete()
        }
        DownloadService.update(context)
    }

    /** Downloads routing data file [name], carrying on from where an earlier try stopped. */
    private fun fetch(context: Context, name: String) {
        val target = OfflineRouting.segmentFile(context, name)
        if (target.exists()) return
        synchronized(jobs) {
            if (jobs[name]?.isActive == true) return
            _transfers.update { it + (name to Transfer(0, 0)) }
            jobs[name] = scope.launch {
                try {
                    var shown = 0L
                    download(OfflineRouting.segmentUrl(name), target) { done, total ->
                        _transfers.update { it + (name to Transfer(done, total)) }
                        // The notification at most once a second.
                        val now = System.currentTimeMillis()
                        if (now - shown > 1000) {
                            shown = now
                            DownloadService.update(context)
                        }
                    }
                    _transfers.update { it - name }
                    // Fresh data goes with the latest profiles.
                    OfflineRouting.updateProfiles(context)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _transfers.update { it + (name to (it[name] ?: Transfer(0, 0)).copy(error = "Download stopped; it'll carry on later")) }
                } finally {
                    synchronized(jobs) { jobs.remove(name) }
                    DownloadService.update(context)
                }
            }
        }
        DownloadService.update(context)
    }

    /** Tries the downloads that stopped again, for when the connection is back. */
    fun retry(context: Context) {
        _saved.value.flatMap { it.segments }.distinct().forEach { fetch(context.applicationContext, it) }
    }

    val downloading get() = synchronized(jobs) { jobs.isNotEmpty() }

    private suspend fun contentLength(url: String): Long = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.contentLengthLong.coerceAtLeast(0)
        } finally {
            connection.disconnect()
        }
    }

    /** Streams [url] to [target] through a part file, resuming it if there is one. */
    private suspend fun download(url: String, target: File, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        val have = if (part.exists()) part.length() else 0L
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (have > 0) connection.setRequestProperty("Range", "bytes=$have-")
            val code = connection.responseCode
            if (code !in 200..299) throw HttpException(code, "")
            // A server that ignores the range sends it all again.
            val resumed = code == 206
            val start = if (resumed) have else 0L
            val total = start + connection.contentLengthLong.coerceAtLeast(0)
            RandomAccessFile(part, "rw").use { out ->
                out.setLength(start)
                out.seek(start)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var done = start
                    var reported = 0L
                    while (isActive) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (done - reported > 512 * 1024) {
                            reported = done
                            onProgress(done, total)
                        }
                    }
                }
            }
            if (!isActive) return@withContext
            if (!part.renameTo(target)) throw java.io.IOException("Couldn't save $target")
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Keeps downloads of routing data going with the app in the background, which Android only
 * allows a foreground service, with a notification. It shows how far along they are.
 */
class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(this), type) }
            .onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 2
        private var running = false

        /** Starts, updates or stops the service, by what's downloading now. */
        fun update(context: Context) {
            val app = context.applicationContext
            ContextCompat.getMainExecutor(app).execute {
                if (OfflineRegions.downloading) {
                    if (!running) {
                        running = true
                        runCatching { ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java)) }
                    } else {
                        app.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(app))
                    }
                } else if (running) {
                    running = false
                    app.stopService(Intent(app, DownloadService::class.java))
                }
            }
        }

        private fun notification(context: Context): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Offline maps being downloaded"
                        setShowBadge(false)
                    }
                )
            }
            val transfers = OfflineRegions.transfers.value.values
            val total = transfers.sumOf { it.total }
            val done = transfers.sumOf { it.done }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading offline maps")
                .setContentText(if (total > 0) "${done * 100 / total}%" else null)
                .setProgress(100, if (total > 0) (done * 100 / total).toInt() else 0, total == 0L)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build()
        }
    }
}
