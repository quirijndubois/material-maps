package app.materialmaps

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Two-finger gestures for the map, in place of MapLibre's own. MapLibre starts a pinch as soon
 * as the gap between the fingers changes a pixel or two, and a pinch rules out tilting, so two
 * fingers pushed up together mostly zoomed instead of tilting. This looks at how both fingers
 * move before picking what the user means:
 *
 * - Fingers side by side moving up or down together: tilt, and only tilt.
 * - Anything else: the map sticks to the fingers. It moves and zooms with them right away, and
 *   turns with them once they've clearly twisted, so a pinch doesn't turn the map by accident.
 *
 * One finger is still MapLibre's: panning, flinging, double tap and the double-tap drag zoom.
 * A quick tap with two fingers zooms out.
 */
@SuppressLint("ClickableViewAccessibility")
class MapGestures(
    private val view: View,
    private val map: MapLibreMap,
    /** The user started moving the map with two fingers. */
    private val onStart: () -> Unit,
    /** The user moved the map away from the followed location. */
    private val onStopTracking: () -> Unit,
    /** The user turned the map while it followed the heading; keep following, but north is theirs now. */
    private val onFreeBearing: () -> Unit,
) : View.OnTouchListener {
    private enum class Mode { Undecided, Tilt, Transform, Pan }

    private val density = view.resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    /** How far the fingers move before we decide what they're doing. */
    private val slop = dp(10f)

    private var active = false
    private var mode = Mode.Undecided
    private var id1 = -1
    private var id2 = -1
    private val start1 = PointF()
    private val start2 = PointF()
    private val last1 = PointF()
    private val last2 = PointF()
    private var downAt = 0L
    private var moved = false
    private var rotating = false

    // Zoom speed when the fingers come off, to let a quick pinch carry on a little.
    private var zoomVelocity = 0.0
    private var lastMoveAt = 0L
    private var liftAt = 0L
    private var liftVelocity = 0.0
    private val liftFocus = PointF()

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = false
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!active) begin(e)
                // A third finger is ignored; a new second finger (after one was lifted) takes over.
                if (e.pointerCount == 2 || mode == Mode.Pan) pair(e, lifted = -1)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!active) return false
                move(e)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (!active) return false
                pointerUp(e)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!active) return false
                active = false
                fling()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!active) return false
                active = false
                return true
            }
        }
        return active
    }

    private fun begin(e: MotionEvent) {
        active = true
        // MapLibre has the first finger and may be panning with it: end that cleanly.
        val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
        view.onTouchEvent(cancel)
        cancel.recycle()
        map.cancelTransitions()
        mode = Mode.Undecided
        downAt = e.eventTime
        moved = false
        rotating = false
        zoomVelocity = 0.0
        lastMoveAt = 0L
        liftAt = 0L
    }

    /** Starts tracking the first two fingers still down, other than [lifted]. */
    private fun pair(e: MotionEvent, lifted: Int) {
        val ids = (0 until e.pointerCount).filter { it != lifted }.map { e.getPointerId(it) }
        id1 = ids.getOrElse(0) { -1 }
        id2 = ids.getOrElse(1) { -1 }
        position(e, id1, start1)
        position(e, id2, start2)
        last1.set(start1)
        last2.set(start2)
        if (id2 == -1) {
            mode = Mode.Pan
        } else if (mode == Mode.Pan) {
            // A second finger again: look afresh at what the two are doing.
            mode = Mode.Undecided
            rotating = false
        }
    }

    private fun position(e: MotionEvent, id: Int, out: PointF) {
        val index = if (id == -1) -1 else e.findPointerIndex(id)
        if (index >= 0) out.set(e.getX(index), e.getY(index))
    }

    private fun pointerUp(e: MotionEvent) {
        val index = e.actionIndex
        val id = e.getPointerId(index)
        if (id != id1 && id != id2) return
        if (mode == Mode.Undecided && !moved && e.pointerCount == 2 && e.eventTime - downAt < TAP_TIMEOUT) {
            // A two-finger tap.
            onStart()
            map.animateCamera(CameraUpdateFactory.zoomOut(), 300)
        }
        if (mode == Mode.Transform && e.pointerCount == 2) {
            liftAt = e.eventTime
            liftVelocity = if (e.eventTime - lastMoveAt < 50) zoomVelocity else 0.0
            liftFocus.set((last1.x + last2.x) / 2, (last1.y + last2.y) / 2)
        }
        pair(e, lifted = index)
    }

    private fun move(e: MotionEvent) {
        val p1 = PointF()
        val p2 = PointF()
        position(e, id1, p1)
        if (mode == Mode.Pan) {
            pan(p1.x - last1.x, p1.y - last1.y, p1.x - start1.x, p1.y - start1.y)
            last1.set(p1)
            return
        }
        position(e, id2, p2)
        if (mode == Mode.Undecided) {
            mode = decide(p1, p2)
            if (mode == Mode.Undecided) return
            moved = true
            onStart()
        }
        if (mode == Mode.Tilt) tilt(p1, p2) else transform(p1, p2, e.eventTime)
        last1.set(p1)
        last2.set(p2)
    }

    /**
     * What the fingers are doing, judged from how far they've come since they went down. Until
     * they've moved a little, or while it could still go either way, it stays undecided and the
     * map holds still.
     */
    private fun decide(p1: PointF, p2: PointF): Mode {
        val d1x = p1.x - start1.x
        val d1y = p1.y - start1.y
        val d2x = p2.x - start2.x
        val d2y = p2.y - start2.y
        val centerX = (d1x + d2x) / 2
        val centerY = (d1y + d2y) / 2
        val travel = hypot(centerX, centerY)
        val span0 = distance(start1, start2)
        val zoom = abs(distance(p1, p2) - span0).toFloat()
        // How far the fingers have travelled around each other, comparable to the others.
        val twist = (abs(angleBetween(angle(start1, start2), angle(p1, p2))) * span0 / 2).toFloat()
        if (max(travel, max(zoom, twist)) < slop) return Mode.Undecided

        // Tilting is two fingers side by side going up or down together.
        val sideBySide = abs(start2.y - start1.y) < abs(start2.x - start1.x) * SIDE_BY_SIDE
        val upOrDown = abs(centerY) > abs(centerX) && zoom < abs(centerY)
        if (sideBySide && upOrDown) {
            val together = sign(d1y) == sign(d2y) &&
                min(abs(d1y), abs(d2y)) > max(abs(d1y), abs(d2y)) * 0.4f
            if (together) return Mode.Tilt
            // One finger is lagging, or only one moves: wait for the other to join before
            // calling it a twist around the still finger.
            if (travel < slop * 3) return Mode.Undecided
        }
        return Mode.Transform
    }

    private fun tilt(p1: PointF, p2: PointF) {
        val dy = ((p1.y - last1.y) + (p2.y - last2.y)) / 2
        val current = map.cameraPosition.tilt
        val tilt = (current - dy / density * TILT_PER_DP).coerceIn(0.0, map.maxPitch)
        if (tilt != current) map.moveCamera(CameraUpdateFactory.tiltTo(tilt))
    }

    private fun transform(p1: PointF, p2: PointF, time: Long) {
        val span = distance(p1, p2)
        val lastSpan = distance(last1, last2)
        val zoomBy = if (lastSpan > 0 && span > 0) ln(span / lastSpan) / ln(2.0) else 0.0

        var turnBy = 0.0
        if (rotating) {
            turnBy = -angleBetween(angle(last1, last2), angle(p1, p2)) * 180 / PI
        } else {
            // Turning starts once the fingers clearly twist; the more they've pinched, the
            // more twist that takes, as pinching tends to twist the hand a little.
            val pinched = abs(ln(span / distance(start1, start2)) / ln(2.0))
            val needed = ROTATE_THRESHOLD + ROTATE_THRESHOLD_PINCHING * min(1.0, pinched)
            val twisted = abs(angleBetween(angle(start1, start2), angle(p1, p2)))
            if (twisted * 180 / PI > needed) {
                // From here on the map turns with the fingers, without first catching up.
                rotating = true
                val component = map.locationComponent.takeIf { it.isLocationComponentActivated }
                if (component != null && component.cameraMode in HEADING_MODES) onFreeBearing()
            }
        }

        val dt = (time - lastMoveAt).coerceAtLeast(1)
        if (lastMoveAt != 0L && dt < 100) {
            zoomVelocity = zoomVelocity * 0.5 + zoomBy / dt * 1000 * 0.5
        }
        lastMoveAt = time

        val center = PointF((p1.x + p2.x) / 2, (p1.y + p2.y) / 2)
        val lastCenter = PointF((last1.x + last2.x) / 2, (last1.y + last2.y) / 2)
        val startCenter = PointF((start1.x + start2.x) / 2, (start1.y + start2.y) / 2)
        val camera = map.cameraPosition

        if (tracking()) {
            // Following the user: zoom and turn around them, and let go of them only when the
            // fingers clearly drag the map away rather than drift while pinching.
            val dragged = distance(center, startCenter)
            val pinchedPx = abs(span - distance(start1, start2))
            if (dragged > dp(DRAG_AWAY_DP) && dragged > pinchedPx) {
                onStopTracking()
            } else {
                map.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder(camera).zoom(camera.zoom + zoomBy).bearing(camera.bearing + turnBy).build()
                ))
                return
            }
        }

        // Keep the spot that was between the fingers between the fingers.
        val anchor = map.projection.fromScreenLocation(lastCenter)
        map.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder(camera).zoom(camera.zoom + zoomBy).bearing(camera.bearing + turnBy).build()
        ))
        val now = map.projection.toScreenLocation(anchor)
        val dx = center.x - now.x
        val dy = center.y - now.y
        if (dx.isFinite() && dy.isFinite() && (dx != 0f || dy != 0f)) map.scrollBy(dx, dy)
    }

    /** One finger left after a two-finger gesture: keep moving the map with it. */
    private fun pan(dx: Float, dy: Float, totalX: Float, totalY: Float) {
        if (tracking()) {
            if (hypot(totalX, totalY) < slop * 2) return
            onStopTracking()
        }
        if (dx != 0f || dy != 0f) map.scrollBy(dx, dy)
    }

    /** Lets a quick pinch carry on a little once the fingers come off together. */
    private fun fling() {
        val now = SystemClock.uptimeMillis()
        if (liftAt == 0L || now - liftAt > 120 || abs(liftVelocity) < 1.0) return
        val zoomBy = (liftVelocity * 0.12).coerceIn(-1.0, 1.0)
        val update = if (tracking()) CameraUpdateFactory.zoomBy(zoomBy)
        else CameraUpdateFactory.zoomBy(zoomBy, android.graphics.Point(liftFocus.x.toInt(), liftFocus.y.toInt()))
        map.animateCamera(update, 250)
    }

    private fun tracking(): Boolean {
        val component = map.locationComponent.takeIf { it.isLocationComponentActivated } ?: return false
        return component.cameraMode != CameraMode.NONE
    }

    private companion object {
        const val TAP_TIMEOUT = 300L
        /** Fingers count as side by side when the line between them is within ~50° of level. */
        const val SIDE_BY_SIDE = 1.2f
        const val TILT_PER_DP = 0.3
        /** Degrees of twist before the map turns, plus up to this much more while pinching. */
        const val ROTATE_THRESHOLD = 12.0
        const val ROTATE_THRESHOLD_PINCHING = 12.0
        const val DRAG_AWAY_DP = 48f
        val HEADING_MODES = setOf(
            CameraMode.TRACKING_COMPASS, CameraMode.TRACKING_GPS, CameraMode.TRACKING_GPS_NORTH,
        )

        fun distance(a: PointF, b: PointF) = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        fun angle(a: PointF, b: PointF) = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())

        /** The signed turn from [from] to [to], within ±π. */
        fun angleBetween(from: Double, to: Double): Double {
            var d = to - from
            while (d > PI) d -= 2 * PI
            while (d < -PI) d += 2 * PI
            return d
        }
    }
}
