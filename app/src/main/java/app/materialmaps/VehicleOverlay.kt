package app.materialmaps

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.exp

/**
 * Draws the chosen [Vehicle] in 3D where the user is, over the map, turned to where they're
 * heading and seen at the map's tilt, so it looks like it's on the road. MapLibre can't draw
 * models for the location, so this view follows it every frame instead.
 *
 * While following, the camera is centered on MapLibre's own animated position, so the vehicle
 * sits exactly there. Otherwise it glides between GPS fixes the same way MapLibre does.
 */
class VehicleOverlay(context: Context) : View(context), Choreographer.FrameCallback {
    var map: MapLibreMap? = null
    var model: Model? = null
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density

    // Gliding from one fix to the next.
    private var fromLat = Double.NaN
    private var fromLon = 0.0
    private var toLat = Double.NaN
    private var toLon = 0.0
    private var glideStart = 0L
    private var glideLength = 1000L
    private var lastFixAt = 0L
    private var lastFixTime = Long.MIN_VALUE

    private var heading = Float.NaN
    private var lastFrame = 0L
    private var yaw = 0f
    private var tilt = 0f
    private var zoom = 0.0
    private var visible = false

    init {
        // The shadow's blur needs a software canvas; the view is small, so that's cheap.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        Choreographer.getInstance().postFrameCallback(this)
        update()
    }

    private fun update() {
        val m = map
        val component = m?.locationComponent?.takeIf { it.isLocationComponentActivated && it.isLocationComponentEnabled }
        val fix = component?.lastKnownLocation
        if (m == null || component == null || fix == null || model == null) {
            if (visible) {
                visible = false
                invalidate()
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16L else (now - lastFrame).coerceIn(1L, 100L)
        lastFrame = now

        if (fix.time != lastFixTime || fix.latitude != toLat || fix.longitude != toLon) {
            // A new fix: glide there over about the time since the last one.
            val (lat, lon) = position(now)
            fromLat = if (lat.isNaN()) fix.latitude else lat
            fromLon = if (lat.isNaN()) fix.longitude else lon
            toLat = fix.latitude
            toLon = fix.longitude
            glideLength = if (lastFixAt == 0L) 1L else (now - lastFixAt).coerceIn(300L, 1500L)
            glideStart = now
            lastFixAt = now
            lastFixTime = fix.time
        }

        val camera = m.cameraPosition
        val tracking = component.cameraMode != CameraMode.NONE
        val at = if (tracking && camera.target != null) camera.target!! else position(now).let { LatLng(it.first, it.second) }
        val point = m.projection.toScreenLocation(at)

        // Heading: the camera's own while it turns with the route; otherwise the latest fix's, eased.
        val target = when {
            component.cameraMode == CameraMode.TRACKING_GPS -> camera.bearing.toFloat()
            fix.hasBearing() -> fix.bearing
            else -> if (heading.isNaN()) camera.bearing.toFloat() else heading
        }
        heading = if (heading.isNaN() || component.cameraMode == CameraMode.TRACKING_GPS) target
        else heading + turn(heading, target) * (1f - exp(-dt / 250f))

        translationX = point.x - width / 2f
        translationY = point.y - height / 2f
        yaw = heading - camera.bearing.toFloat()
        tilt = camera.tilt.toFloat()
        zoom = camera.zoom
        visible = true
        invalidate()
    }

    private fun position(now: Long): Pair<Double, Double> {
        if (toLat.isNaN()) return Double.NaN to 0.0
        val t = ((now - glideStart).toDouble() / glideLength).coerceIn(0.0, 1.0)
        return (fromLat + (toLat - fromLat) * t) to (fromLon + (toLon - fromLon) * t)
    }

    override fun onDraw(canvas: Canvas) {
        if (!visible) return
        val model = model ?: return
        // Fixed on screen from afar; closer in it grows with the map, only slower, so it still
        // looks like it's on the road without covering it.
        val grow = Math.pow(2.0, (zoom - GROW_FROM_ZOOM).coerceAtLeast(0.0) * 0.5).toFloat().coerceAtMost(MAX_GROWTH)
        model.draw(canvas, width / 2f, height / 2f, SCALE_DP * density * grow, yaw, tilt, blur = 3f * density * grow)
    }

    private fun turn(from: Float, to: Float) = ((to - from) % 360f + 540f) % 360f - 180f

    companion object {
        /** On-screen size of one model unit: a car comes out about 50 dp long. */
        const val SCALE_DP = 11.5f
        /** Up to this zoom the vehicle keeps its size on screen; past it, it grows with the map. */
        const val GROW_FROM_ZOOM = 15.0
        const val MAX_GROWTH = 2.2f
        /** The view's size; room for the biggest model at full size and tilt. */
        const val SIZE_DP = 340
    }
}
