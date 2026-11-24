package com.example.gamepad.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Hardware tilt steering sensor for Racing Gyro mode.
 * Emits normalized steering axis (-1f left .. +1f right).
 */
class TiltSteeringSensor(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    fun steeringFlow(): Flow<Float> = callbackFlow {
        val manager = sensorManager
        val accel = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || accel == null) {
            trySend(0f)
            awaitClose {}
            return@callbackFlow
        }

        var smoothed = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // In landscape mode, tilting phone left/right alters Y gravity relative to X/Z
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val norm = sqrt(x * x + y * y + z * z).coerceAtLeast(0.1f)
                val angleRad = atan2(y / norm, sqrt((x * x + z * z)) / norm)
                // Full steering lock at ~38 degrees (~0.66 rad)
                val rawSteer = (angleRad / 0.66f).coerceIn(-1f, 1f)
                smoothed = smoothed * 0.45f + rawSteer * 0.55f
                trySend(smoothed)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        manager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        awaitClose {
            manager.unregisterListener(listener)
        }
    }
}
