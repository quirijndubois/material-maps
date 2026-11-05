package app.materialmaps

import android.app.PendingIntent
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.engine.LocationEngineRequest
import kotlin.math.abs

/**
 * Sits between the phone's location and the map. While navigating it moves the arrow the way a
 * car following the route would move, rather than jumping from GPS fix to GPS fix:
 * - the arrow stays on the route's driving line (corners rounded into arcs, see
 *   [Route.drivingLine]) and always faces along it, so it turns through a corner at the pace
 *   the car drives it;
 * - each fix is matched to the stretch of route just around where the user already was, so a
 *   fix next to a corner isn't taken for the street being turned into;
 * - between fixes [frame] moves the arrow on every screen frame at the current speed, nudged
 *   towards where the fixes say the user is now. It never goes backwards or jumps, unless a
 *   fix is far off what it shows.
 * Off the route it passes fixes on with the GPS course as direction (or the last direction
 * when standing still, as GPS bearings at walking pace or at a red light are noise). The
 * phone's own compass is never used for this.
 */
class RouteHeadingEngine(private val engine: LocationEngine) : LocationEngine {
    /** The route being navigated, or null to pass locations through untouched. */
    @Volatile
    var route: Route? = null
        set(value) {
            field = value
            following = false
            last = null
        }

    /** Whether the arrow is following the route, moved by [frame]; otherwise fixes go to the map as they are. */
    @Volatile
    var following = false
        private set

    // Meters along the driving line: the last fix, and the arrow.
    private var fixAt = 0.0
    private var fixTime = 0L
    private var speed = 0.0
    private var shown = 0.0
    private var lastFrame = 0L
    private var lastFix: Location? = null
    private var last: Float? = null
    private val wrapped = HashMap<LocationEngineCallback<LocationEngineResult>, LocationEngineCallback<LocationEngineResult>>()

    /** Takes in a fix; gives what the rest of the app should take as the user's location. */
    @Synchronized
    private fun adjust(location: Location): Location {
        val line = route?.drivingLine ?: return location
        val now = SystemClock.elapsedRealtime()
        val around = if (following) shown else null
        val (at, off) = around
            ?.let { line.locate(location.latitude, location.longitude, it - BEHIND, it + AHEAD + speed * 3) }
            ?.takeIf { it.second < ON_ROUTE }
            ?: line.locate(location.latitude, location.longitude, 0.0, line.length)
        val moving = location.hasBearing() && location.hasSpeed() && location.speed > MIN_SPEED
        val wrongWay = moving && angleBetween(location.bearing, line.directionAt(at).toFloat()) > 100
        if (off > ON_ROUTE || wrongWay) {
            following = false
            val heading = if (moving) location.bearing else last ?: line.directionAt(at).toFloat()
            last = heading
            return Location(location).apply { bearing = heading }
        }

        // Speed along the route: the phone's if it has one, else worked out from progress.
        val seconds = (now - fixTime) / 1000.0
        val measured = when {
            location.hasSpeed() -> location.speed.toDouble()
            following && seconds in 0.2..5.0 -> ((at - fixAt) / seconds).coerceAtLeast(0.0)
            else -> speed
        }
        speed += (measured - speed) * 0.5
        if (!following) {
            shown = at
            lastFrame = now
            following = true
        }
        fixAt = at
        fixTime = now
        lastFix = location
        val point = line.pointAt(at)
        return Location(location).apply {
            latitude = point.latitude
            longitude = point.longitude
            bearing = line.directionAt(at).toFloat()
        }
    }

    /**
     * Where the arrow should go over the next [millis], while following the route: a few points
     * along the driving line, the last one timed for when that's up. The map glides the arrow
     * through them, so calling this again every [millis] keeps it moving without a break.
     */
    @Synchronized
    fun ahead(millis: Long): List<Location>? {
        val line = route?.drivingLine ?: return null
        val fix = lastFix ?: return null
        if (!following) return null
        val now = SystemClock.elapsedRealtime()
        val wallClock = System.currentTimeMillis()
        // After a pause (app in the background) start from now rather than catching up.
        if (lastFrame < now - 1000) lastFrame = now
        val until = now + millis
        val frames = mutableListOf<Location>()
        while (lastFrame < until) {
            val dt = minOf(STEP_MILLIS, until - lastFrame)
            lastFrame += dt
            // Where the user should be by then, going on at the same speed for a while after the fix.
            val since = ((lastFrame - fixTime) / 1000.0).coerceIn(0.0, DEAD_RECKONING)
            val estimate = fixAt + speed * since
            val behind = estimate - shown
            shown = if (behind < -BACK_UP || behind > JUMP) {
                estimate
            } else {
                // Close the gap over a second or two, never by reversing.
                val pace = (speed + behind * CATCH_UP).coerceIn(0.0, speed * 1.5 + 3)
                shown + pace * dt / 1000.0
            }.coerceIn(0.0, line.length)
            val point = line.pointAt(shown)
            val heading = line.directionAt(shown, TANGENT).toFloat()
            last = heading
            frames += Location(fix).apply {
                latitude = point.latitude
                longitude = point.longitude
                bearing = heading
                this.speed = this@RouteHeadingEngine.speed.toFloat()
                time = wallClock + (lastFrame - now)
                elapsedRealtimeNanos = lastFrame * 1_000_000
            }
        }
        return frames.ifEmpty { null }
    }

    private fun LocationEngineResult.adjusted() = LocationEngineResult.create(locations.map(::adjust))

    private fun LocationEngineCallback<LocationEngineResult>.adjusting() =
        object : LocationEngineCallback<LocationEngineResult> {
            override fun onSuccess(result: LocationEngineResult) = this@adjusting.onSuccess(result.adjusted())
            override fun onFailure(exception: Exception) = this@adjusting.onFailure(exception)
        }

    override fun getLastLocation(callback: LocationEngineCallback<LocationEngineResult>) =
        engine.getLastLocation(callback.adjusting())

    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        callback: LocationEngineCallback<LocationEngineResult>,
        looper: Looper?,
    ) {
        val adjusting = callback.adjusting()
        synchronized(wrapped) { wrapped.put(callback, adjusting) }?.let { engine.removeLocationUpdates(it) }
        engine.requestLocationUpdates(request, adjusting, looper)
    }

    override fun removeLocationUpdates(callback: LocationEngineCallback<LocationEngineResult>) {
        synchronized(wrapped) { wrapped.remove(callback) }?.let { engine.removeLocationUpdates(it) }
    }

    // Only used for background updates, which this app doesn't ask for.
    override fun requestLocationUpdates(request: LocationEngineRequest, pendingIntent: PendingIntent?) =
        engine.requestLocationUpdates(request, pendingIntent)

    override fun removeLocationUpdates(pendingIntent: PendingIntent?) = engine.removeLocationUpdates(pendingIntent)

    private companion object {
        /** Meters from the route line within which the user counts as on it. */
        const val ON_ROUTE = 30.0
        /** Meters per second; below this a GPS course is too noisy to use. */
        const val MIN_SPEED = 2f
        /** Meters back and forward along the route to look for the next fix. */
        const val BEHIND = 40.0
        const val AHEAD = 80.0
        /** Meters the arrow may be ahead of the fixes before it's put back, or behind before it jumps. */
        const val BACK_UP = 30.0
        const val JUMP = 150.0
        /** Seconds to keep going at the last speed when fixes stop coming. */
        const val DEAD_RECKONING = 3.0
        /** Milliseconds between the points [ahead] hands out. */
        const val STEP_MILLIS = 40L
        /** Per second: how fast the arrow closes the gap to where the fixes say the user is. */
        const val CATCH_UP = 0.8
        /** Meters either side of the arrow its direction is taken over: just the road's tangent. */
        const val TANGENT = 1.0

        fun angleBetween(a: Float, b: Float) = abs(((a - b + 540f) % 360f) - 180f)
    }
}
