package app.materialmaps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Choreographer
import android.view.Surface
import android.view.WindowManager
import org.maplibre.android.location.CompassEngine
import org.maplibre.android.location.CompassListener
import kotlin.math.abs
import kotlin.math.exp

/**
 * Phone heading for the blue dot and the map turning with the phone. MapLibre's own engine
 * samples at 10 Hz and the location component then animates every sample over 500 ms, which
 * makes the arrow lag behind the phone. This one samples at ~50 Hz, and with the compass
 * animation turned off, glides towards the latest reading once every display frame, so the
 * map turns smoothly with the phone instead of in sensor-sized steps.
 */
class HeadingEngine(private val context: Context) : CompassEngine, SensorEventListener, Choreographer.FrameCallback {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensors.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val listeners = mutableListOf<CompassListener>()
    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)
    private var heading = 0f
    private var hasHeading = false
    private var accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    /** The filtered sensor heading, and the one the reported heading glides towards. */
    private var filtered = 0f
    private var target = 0f
    private var lastFrameNanos = 0L
    private var frameScheduled = false

    override fun addCompassListener(listener: CompassListener) {
        if (listeners.isEmpty() && sensor != null) {
            sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        listeners.add(listener)
    }

    override fun removeCompassListener(listener: CompassListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            sensors.unregisterListener(this)
            Choreographer.getInstance().removeFrameCallback(this)
            frameScheduled = false
        }
    }

    override fun getLastHeading() = heading

    override fun getLastAccuracySensorStatus() = accuracy

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        this.accuracy = accuracy
        listeners.toList().forEach { it.onCompassAccuracyChange(accuracy) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val (x, y) = when (displayRotation()) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotation, x, y, remapped)
        SensorManager.getOrientation(remapped, orientation)
        val raw = Math.toDegrees(orientation[0].toDouble()).toFloat()

        if (!hasHeading) {
            filtered = normalize(raw)
            target = filtered
            heading = target
            hasHeading = true
            listeners.toList().forEach { it.onCompassChanged(heading) }
            return
        }
        // Light low-pass filter along the shortest way round, to take out sensor jitter. Changes
        // too small to see are left out, so a phone lying still doesn't keep the map redrawing.
        filtered = normalize(filtered + turn(filtered, raw) * SMOOTHING)
        if (abs(turn(target, filtered)) < MIN_CHANGE) return
        target = filtered
        if (!frameScheduled) {
            frameScheduled = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameScheduled = false
        if (listeners.isEmpty()) return
        // Ease towards the target at the same pace whatever the display's refresh rate.
        val dt = if (lastFrameNanos == 0L) FRAME_NANOS else (frameTimeNanos - lastFrameNanos).coerceIn(1L, FRAME_NANOS * 4)
        lastFrameNanos = frameTimeNanos
        val remaining = turn(heading, target)
        heading = if (abs(remaining) < SETTLED) target
        else normalize(heading + remaining * (1f - exp(-dt / EASE_NANOS)))
        listeners.toList().forEach { it.onCompassChanged(heading) }
        if (heading != target) {
            frameScheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** The shortest turn from [from] to [to], in degrees within ±180. */
    private fun turn(from: Float, to: Float) = ((to - from + 540f) % 360f) - 180f

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display?.rotation ?: Surface.ROTATION_0
        else (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

    private fun normalize(degrees: Float) = (degrees % 360f + 360f) % 360f

    private companion object {
        const val SMOOTHING = 0.35f
        const val MIN_CHANGE = 0.3f
        /** How quickly the shown heading catches up: about 63% of the way in this time. */
        const val EASE_NANOS = 70_000_000f
        const val FRAME_NANOS = 16_666_667L
        const val SETTLED = 0.05f
    }
}
