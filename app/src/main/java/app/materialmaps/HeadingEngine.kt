package app.materialmaps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import org.maplibre.android.location.CompassEngine
import org.maplibre.android.location.CompassListener
import kotlin.math.abs

/**
 * Phone heading for the blue dot. MapLibre's own engine samples at 10 Hz and the location
 * component then animates every sample over 500 ms, which makes the arrow lag behind the
 * phone. This one samples at ~50 Hz and smooths lightly, so with the compass animation
 * turned off the arrow turns with the phone.
 */
class HeadingEngine(private val context: Context) : CompassEngine, SensorEventListener {
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
    private var reported = Float.NaN
    private var reportedAt = 0L

    override fun addCompassListener(listener: CompassListener) {
        if (listeners.isEmpty() && sensor != null) {
            sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        listeners.add(listener)
    }

    override fun removeCompassListener(listener: CompassListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) sensors.unregisterListener(this)
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
            heading = normalize(raw)
            hasHeading = true
        } else {
            // Light low-pass filter along the shortest way round, to take out sensor jitter.
            val delta = ((raw - heading + 540f) % 360f) - 180f
            heading = normalize(heading + delta * SMOOTHING)
        }
        // Every report makes the map redraw, so skip changes nobody can see.
        val now = event.timestamp
        val moved = reported.isNaN() || abs(((heading - reported + 540f) % 360f) - 180f) >= MIN_CHANGE
        if (!moved || now - reportedAt < MIN_INTERVAL_NANOS) return
        reported = heading
        reportedAt = now
        listeners.toList().forEach { it.onCompassChanged(heading) }
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display?.rotation ?: Surface.ROTATION_0
        else (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

    private fun normalize(degrees: Float) = (degrees % 360f + 360f) % 360f

    private companion object {
        const val SMOOTHING = 0.3f
        const val MIN_CHANGE = 0.5f
        const val MIN_INTERVAL_NANOS = 33_000_000L
    }
}
