package com.example.watchface.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.example.watchface.Config
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class WakeDetector(
    context: Context,
    private val onWakeRequest: (source: String) -> Unit,
    private val onLuxChanged: (smoothedLux: Float, rawLux: Float) -> Unit,
    private val onMotionTelemetry: (pitch: Float, magDev: Float) -> Unit = { _, _ -> }
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private var isRegistered = false
    private var lastWakeTime = 0L

    // Light sensor smoothing window
    private val luxWindow = ArrayDeque<Float>()
    private val luxWindowSize = 30
    var currentSmoothedLux = 100f
        private set

    // Accelerometer filtering
    private val gravity = FloatArray(3) { 0f }
    private var isGravityInitialized = false
    private val alpha = 0.8f

    // Pitch gesture tracking
    private var lastPitch = 0f
    private var wasLookingDown = false
    private var lookDownTimestamp = 0L

    private var lastProcessTimestamp = 0L

    fun startListening() {
        if (isRegistered || sensorManager == null) return
        // Use SENSOR_DELAY_NORMAL (approx 200ms / 5Hz) with 200ms batching latency to allow hardware FIFO batching
        // This significantly reduces CPU wake-ups in standby mode
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, 200_000)
        }
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, 500_000)
        }
        isRegistered = true
    }

    fun setDimState(isDim: Boolean) {
        if (!isRegistered || sensorManager == null) return
        if (isDim) {
            // Unregister light sensor completely in DIM mode to minimize battery draw
            lightSensor?.let { sensorManager.unregisterListener(this, it) }
        } else {
            // Re-register light sensor in ACTIVE mode with batching
            lightSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, 500_000)
            }
        }
    }

    fun stopListening() {
        if (!isRegistered || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val rawLux = event.values[0]
                luxWindow.addLast(rawLux)
                if (luxWindow.size > luxWindowSize) {
                    luxWindow.removeFirst()
                }
                currentSmoothedLux = if (luxWindow.isNotEmpty()) luxWindow.average().toFloat() else rawLux
                onLuxChanged(currentSmoothedLux, rawLux)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val now = SystemClock.elapsedRealtime()
                // Throttle processing if we are in cooldown window to save CPU cycles
                if (now - lastWakeTime < Config.WAKE_COOLDOWN_MS) {
                    return
                }

                // Minimum 100ms interval between sensor evaluations (10Hz max compute rate)
                if (now - lastProcessTimestamp < 100L) {
                    return
                }
                lastProcessTimestamp = now

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Low-pass filter for gravity component
                if (!isGravityInitialized) {
                    gravity[0] = x
                    gravity[1] = y
                    gravity[2] = z
                    isGravityInitialized = true
                } else {
                    gravity[0] = alpha * gravity[0] + (1 - alpha) * x
                    gravity[1] = alpha * gravity[1] + (1 - alpha) * y
                    gravity[2] = alpha * gravity[2] + (1 - alpha) * z
                }

                // Magnitude deviation from gravity (9.81 m/s^2)
                val mag = sqrt(x * x + y * y + z * z)
                val magDev = abs(mag - 9.81f)

                // Pitch angle computation for wrist-raise
                val pitch = (atan2(-gravity[0].toDouble(), sqrt((gravity[1] * gravity[1] + gravity[2] * gravity[2]).toDouble())) * 180.0 / Math.PI).toFloat()

                onMotionTelemetry(pitch, magDev)

                // Precise & Low-Power Wrist Raise Logic:
                // Must start from a lower resting angle (pitch < -45°), then explicitly rotate to reading angle (-15° <= pitch <= 35°) within 1.0 second
                if (pitch < -45f) {
                    wasLookingDown = true
                    lookDownTimestamp = now
                } else if (wasLookingDown && (now - lookDownTimestamp in 150L..1000L)) {
                    if (pitch in -15f..35f) {
                        wasLookingDown = false
                        lastWakeTime = now
                        onWakeRequest("抬腕手势")
                        return
                    }
                } else if (now - lookDownTimestamp > 1000L) {
                    wasLookingDown = false
                }

                // Filtered fallback: only trigger on strong deliberate shake / movement, not casual arm swinging
                if (magDev > Config.MOTION_THRESHOLD) {
                    lastWakeTime = now
                    onWakeRequest("动态感应")
                }

                lastPitch = pitch
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
