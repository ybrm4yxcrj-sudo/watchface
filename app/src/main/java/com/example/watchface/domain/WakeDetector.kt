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

    fun startListening() {
        if (isRegistered || sensorManager == null) return
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        isRegistered = true
    }

    fun setDimState(isDim: Boolean) {
        if (!isRegistered || sensorManager == null) return
        if (isDim) {
            // Unregister light sensor in DIM to save battery, keep accelerometer for wake
            lightSensor?.let { sensorManager.unregisterListener(this, it) }
        } else {
            // Re-register light sensor in ACTIVE mode
            lightSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
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

                // 1. Motion magnitude wake (v1粗糙版)
                val mag = sqrt(x * x + y * y + z * z)
                val magDev = abs(mag - 9.81f)

                // 2. Pitch angle computation (v2精确抬腕判定)
                // pitch = atan2(-gx, sqrt(gy^2 + gz^2)) * 180 / PI
                val pitch = (atan2(-gravity[0].toDouble(), sqrt((gravity[1] * gravity[1] + gravity[2] * gravity[2]).toDouble())) * 180.0 / Math.PI).toFloat()

                onMotionTelemetry(pitch, magDev)

                val now = SystemClock.elapsedRealtime()
                if (now - lastWakeTime < Config.WAKE_COOLDOWN_MS) {
                    lastPitch = pitch
                    return
                }

                // Check wrist raise gesture:
                // Arms down (pitch < -40°), then turned towards face within 1.2s (-20° <= pitch <= 45°)
                if (pitch < -40f) {
                    wasLookingDown = true
                    lookDownTimestamp = now
                } else if (wasLookingDown && (now - lookDownTimestamp <= 1200L)) {
                    if (pitch in -20f..45f) {
                        wasLookingDown = false
                        lastWakeTime = now
                        onWakeRequest("抬腕手势")
                        return
                    }
                }

                // Fallback: Sudden motion threshold
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
