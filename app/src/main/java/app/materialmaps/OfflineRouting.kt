package app.materialmaps

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import btools.router.FormatJson
import btools.router.OsmNodeNamed
import btools.router.RoutingContext
import btools.router.RoutingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Directions on the phone, without a connection, with BRouter (see the brouter module) and the
 * routing data downloaded for the offline map regions: one file per 5° square of the world,
 * from brouter.de, rebuilt there from OpenStreetMap every day.
 */
object OfflineRouting {
    private const val SEGMENTS = "https://brouter.de/brouter/segments4/"
    private const val PROFILES = "https://brouter.de/brouter/profiles2/"
    /** The routing profiles, and the table of map tags they're written against. */
    private val PROFILE_FILES = listOf("lookups.dat", "car-vario.brf", "trekking.brf", "hiking-mountain.brf")
    /** BRouter isn't made for running two routes at once. */
    private val engine = Mutex()

    private fun root(context: Context) = File(context.filesDir, "brouter")
    fun segmentDir(context: Context) = File(root(context), "segments4")
    private fun profileDir(context: Context) = File(root(context), "profiles2")

    fun segmentUrl(name: String) = "$SEGMENTS$name.rd5"
    fun segmentFile(context: Context, name: String) = File(segmentDir(context), "$name.rd5")

    /** The routing data file with [lat]/[lon] in it, named by its south-west corner, like `E5_N50`. */
    fun segmentName(lat: Double, lon: Double): String {
        val x = (floor(lon / 5) * 5).toInt()
        val y = (floor(lat / 5) * 5).toInt()
        return (if (x < 0) "W${-x}" else "E$x") + "_" + (if (y < 0) "S${-y}" else "N$y")
    }

    /** The routing data files [bounds] needs. */
    fun segmentsIn(bounds: LatLngBounds): List<String> {
        val names = LinkedHashSet<String>()
        var lat = floor(bounds.latitudeSouth / 5) * 5
        while (lat < bounds.latitudeNorth) {
            var lon = floor(bounds.longitudeWest / 5) * 5
            while (lon < bounds.longitudeEast) {
                names += segmentName(lat + 0.1, lon + 0.1)
                lon += 5
            }
            lat += 5
        }
        return names.toList()
    }

    /** Whether the routing data for all of [points] is on the phone. */
    fun covers(context: Context, points: List<LatLng>): Boolean =
        points.all { segmentFile(context, segmentName(it.latitude, it.longitude)).exists() }

    /** Whether the phone has a working connection to the internet. */
    fun online(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Gets the latest profiles, to go with freshly downloaded routing data: both follow the
     * same table of map tags, which changes now and then. Without a connection, the ones
     * that came with the app do.
     */
    suspend fun updateProfiles(context: Context) {
        val dir = profileDir(context).apply { mkdirs() }
        for (name in PROFILE_FILES) {
            runCatching {
                val bytes = httpGetBytes(PROFILES + name)
                withContext(Dispatchers.IO) {
                    val part = File(dir, "$name.part")
                    part.writeBytes(bytes)
                    part.renameTo(File(dir, name))
                }
            }
        }
    }

    /** The profiles, from brouter.de where downloaded, else as they came with the app. */
    private fun profiles(context: Context): File {
        val dir = profileDir(context).apply { mkdirs() }
        for (name in PROFILE_FILES) {
            val file = File(dir, name)
            if (!file.exists()) context.assets.open("brouter/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        return dir
    }

    /**
     * A route from [from] via [stops] to [to], worked out on the phone. Only one: alternatives
     * would take as long again each. Instructions are in English.
     */
    suspend fun route(context: Context, from: LatLng, stops: List<LatLng>, to: LatLng, mode: TravelMode, settings: Settings): List<Route> =
        withContext(Dispatchers.Default) {
            val waypoints = listOf(from) + stops + to
            if (!covers(context, waypoints)) throw RouteException("This trip goes outside the offline maps on this phone.")
            val profile = when (mode) {
                TravelMode.Car -> "car-vario"
                TravelMode.Bike -> "trekking"
                else -> "hiking-mountain"
            }
            // Profile settings are numbers: 1 for yes, 0 for no.
            fun flag(on: Boolean) = if (on) "1" else "0"
            val options = HashMap<String, String>()
            // Turn instructions as OsmAnd takes them: the plainest set.
            options["turnInstructionMode"] = "3"
            when (mode) {
                TravelMode.Car -> {
                    options["avoid_motorways"] = flag(settings.avoidHighways)
                    options["avoid_toll"] = flag(settings.avoidTolls)
                }
                TravelMode.Bike -> options["allow_ferries"] = flag(!settings.avoidFerries)
                else -> Unit
            }
            val json = engine.withLock {
                val rc = RoutingContext().apply {
                    localFunction = File(profiles(context), "$profile.brf").path
                    keyValues = options
                    memoryclass = 128
                }
                val nodes = waypoints.mapIndexed { i, point ->
                    OsmNodeNamed().apply {
                        name = if (i == 0) "from" else if (i == waypoints.lastIndex) "to" else "via$i"
                        ilon = ((point.longitude + 180) * 1_000_000 + 0.5).toInt()
                        ilat = ((point.latitude + 90) * 1_000_000 + 0.5).toInt()
                    }
                }
                val routing = try {
                    RoutingEngine(null, null, segmentDir(context), nodes, rc).apply {
                        quite = true
                        doRun(60_000)
                    }
                } catch (e: RuntimeException) {
                    // A profile it can't read, or data it can't make sense of.
                    throw RouteException("Couldn't work out directions offline.")
                }
                val track = routing.foundTrack
                    ?: throw RouteException(routing.errorMessage?.let(::errorMessage) ?: "No route found.")
                FormatJson(rc).format(track)
            }
            listOf(parse(JSONObject(json), mode, stops))
        }

    private fun errorMessage(error: String): String = when {
        "not found" in error && ".rd5" in error -> "This trip goes outside the offline maps on this phone."
        "position not mapped" in error || "operation killed" in error -> "There's no road near the start or the destination."
        else -> "No route found."
    }

    private fun parse(json: JSONObject, mode: TravelMode, stops: List<LatLng>): Route {
        val feature = json.getJSONArray("features").getJSONObject(0)
        val properties = feature.getJSONObject("properties")
        val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coordinates.length()).map { i ->
            coordinates.getJSONArray(i).let { LatLng(it.getDouble(1), it.getDouble(0)) }
        }
        val times = properties.optJSONArray("times")?.let { t -> DoubleArray(t.length()) { t.getDouble(it) } }
        fun timeAt(i: Int) = times?.getOrNull(i.coerceIn(0, points.lastIndex)) ?: 0.0
        val along = DoubleArray(points.size).also { a ->
            for (i in 1 until points.size) a[i] = a[i - 1] + metersBetween(points[i - 1], points[i])
        }

        // The turns, between setting off and arriving.
        class Turn(val index: Int, val type: String, val modifier: String?, val instruction: String)
        val turns = ArrayList<Turn>()
        turns += Turn(0, "depart", null, "Head off")
        val hints = properties.optJSONArray("voicehints")
        if (hints != null) for (h in 0 until hints.length()) {
            val hint = hints.getJSONArray(h)
            val index = hint.getInt(0).coerceIn(0, points.lastIndex)
            val exit = abs(hint.optInt(2))
            val turn = when (hint.getInt(1)) {
                1 -> Turn(index, "continue", "straight", "Continue straight")
                2 -> Turn(index, "turn", "left", "Turn left")
                3 -> Turn(index, "turn", "slight left", "Turn slightly left")
                4 -> Turn(index, "turn", "sharp left", "Turn sharply left")
                5 -> Turn(index, "turn", "right", "Turn right")
                6 -> Turn(index, "turn", "slight right", "Turn slightly right")
                7 -> Turn(index, "turn", "sharp right", "Turn sharply right")
                8 -> Turn(index, "fork", "slight left", "Keep left")
                9 -> Turn(index, "fork", "slight right", "Keep right")
                10, 11, 15 -> Turn(index, "turn", "uturn", "Make a U-turn")
                13, 14 -> Turn(
                    index, "roundabout", "straight",
                    if (exit > 0) "At the roundabout, take the ${ordinal(exit)} exit" else "Go round the roundabout",
                )
                17 -> Turn(index, "off ramp", "slight left", "Take the exit on the left")
                18 -> Turn(index, "off ramp", "slight right", "Take the exit on the right")
                else -> null
            } ?: continue
            if (index > turns.last().index) turns += turn
        }
        turns += Turn(points.lastIndex, "arrive", null, "You have arrived")

        val maneuvers = turns.mapIndexed { i, turn ->
            val next = turns.getOrNull(i + 1)?.index ?: turn.index
            Maneuver(
                type = turn.type,
                modifier = turn.modifier,
                instruction = turn.instruction,
                distance = along[next] - along[turn.index],
                duration = timeAt(next) - timeAt(turn.index),
                beginIndex = turn.index,
            )
        }

        // Said ahead of each turn, and again at it: further ahead the faster one goes.
        val ahead = when (mode) {
            TravelMode.Car -> listOf(1000.0, 300.0)
            TravelMode.Bike -> listOf(200.0)
            else -> listOf(100.0)
        }
        val atTurn = if (mode == TravelMode.Car) 60.0 else 20.0
        val announcements = ArrayList<Announcement>()
        for ((i, turn) in turns.withIndex()) {
            if (i == 0) continue
            val at = along[turn.index]
            val gap = at - along[turns[i - 1].index]
            val action = if (turn.type == "arrive") "you'll arrive" else turn.instruction.replaceFirstChar { it.lowercase() }
            for (before in ahead) {
                if (gap > before * 1.5) announcements += Announcement(at - before, "In ${spokenDistance(before)}, $action")
            }
            announcements += Announcement((at - atTurn).coerceAtLeast(along[turns[i - 1].index]), turn.instruction)
        }

        // Each stop starts a leg: the closest point to it, onward from the last.
        val legStarts = mutableListOf(0)
        for (stop in stops) {
            val from = legStarts.last()
            legStarts += (from until points.size).minByOrNull { metersBetween(points[it], stop) } ?: from
        }

        return Route(
            points = points,
            distance = properties.optString("track-length").toDoubleOrNull() ?: along.last(),
            duration = properties.optString("total-time").toDoubleOrNull() ?: timeAt(points.lastIndex),
            maneuvers = maneuvers,
            announcements = announcements.sortedBy { it.at },
            legStarts = legStarts,
        )
    }

    private fun ordinal(n: Int) = when (n) {
        1 -> "first"
        2 -> "second"
        3 -> "third"
        4 -> "fourth"
        5 -> "fifth"
        6 -> "sixth"
        else -> "${n}th"
    }

    /** A distance as it's said, like "300 meters" or "1 kilometer". */
    private fun spokenDistance(meters: Double): String = if (distanceUnits == Units.Imperial) {
        val feet = meters * 3.28084
        if (feet < 1000) "${(feet / 100).roundToInt() * 100} feet" else "${"%.1f".format(meters / 1609.34).removeSuffix(".0")} miles"
    } else {
        if (meters < 1000) "${meters.roundToInt()} meters" else if (meters < 1500) "1 kilometer" else "${"%.1f".format(meters / 1000).removeSuffix(".0")} kilometers"
    }
}

/**
 * Directions online where there's a connection, else (or when the service can't be reached)
 * on the phone, if the routing data for the trip is downloaded.
 */
object Directions {
    suspend fun route(context: Context, from: LatLng, stops: List<LatLng>, to: LatLng, mode: TravelMode, settings: Settings): List<Route> {
        val points = listOf(from) + stops + to
        val offline = OfflineRouting.covers(context, points)
        if (offline && !OfflineRouting.online(context)) return OfflineRouting.route(context, from, stops, to, mode, settings)
        return try {
            Valhalla.route(from, stops, to, mode, settings)
        } catch (e: IOException) {
            if (e is HttpException || !offline) throw e
            OfflineRouting.route(context, from, stops, to, mode, settings)
        }
    }
}
