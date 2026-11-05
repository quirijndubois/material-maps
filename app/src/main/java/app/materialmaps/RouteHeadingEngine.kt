package app.materialmaps

import android.app.PendingIntent
import android.location.Location
import android.os.Looper
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.engine.LocationEngineRequest
import kotlin.math.abs

/**
 * Sits between the phone's location and the map. While navigating it replaces each fix's
 * bearing, which turns the arrow and the map, with where the user is actually going:
 * along the route when they're on it, the GPS course when they've left it, and the last
 * direction when standing still (GPS bearings at walking pace or at a red light are noise).
 * The phone's own compass is never used for this.
 */
class RouteHeadingEngine(private val engine: LocationEngine) : LocationEngine {
    /** The route being navigated, or null to pass locations through untouched. */
    @Volatile
    var route: Route? = null
        set(value) {
            field = value
            if (value == null) last = null
        }

    private var last: Float? = null
    private val wrapped = HashMap<LocationEngineCallback<LocationEngineResult>, LocationEngineCallback<LocationEngineResult>>()

    private fun adjust(location: Location): Location {
        val route = route ?: return location
        val p = route.progress(location.latitude, location.longitude)
        val moving = location.hasBearing() && location.hasSpeed() && location.speed > MIN_SPEED
        val course = location.bearing
        val heading = when {
            // Driving the wrong way down the route (before rerouting kicks in) shouldn't point backwards.
            p.offRoute < ON_ROUTE && moving && angleBetween(course, p.heading.toFloat()) > 100 -> course
            p.offRoute < ON_ROUTE -> p.heading.toFloat()
            moving -> course
            else -> last ?: p.heading.toFloat()
        }
        last = heading
        return Location(location).apply { bearing = heading }
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

        fun angleBetween(a: Float, b: Float) = abs(((a - b + 540f) % 360f) - 180f)
    }
}
