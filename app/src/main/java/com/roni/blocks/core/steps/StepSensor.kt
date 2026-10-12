package com.roni.blocks.core.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The phone's step counter (2.5.0). Android's hardware counter runs on a low-power chip whether the
 * app is alive or not, so the app only *reads* it at checkpoints: the difference since the last
 * reading belongs to the newer reading's day ([StepBook] does the arithmetic).
 *
 * Talks to: [SensorManager].
 */
interface StepSensor {
    /**
     * Steps since the phone booted, or null after two seconds, with no sensor, or without the
     * "Physical activity" permission.
     */
    suspend fun read(): Long?

    /** The counter while Home is on screen, for the number in the top bar. Empty without a sensor. */
    fun live(): Flow<Long>
}

@Singleton
class AndroidStepSensor @Inject constructor(
    @ApplicationContext private val context: Context,
) : StepSensor {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override suspend fun read(): Long? {
        if (!hasPermission()) return null
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
                        manager.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0].toLong())
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { manager.unregisterListener(listener) }
            }
        }
    }

    override fun live(): Flow<Long> = callbackFlow {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null || !hasPermission()) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) trySend(event.values[0].toLong())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager.unregisterListener(listener) }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
}
