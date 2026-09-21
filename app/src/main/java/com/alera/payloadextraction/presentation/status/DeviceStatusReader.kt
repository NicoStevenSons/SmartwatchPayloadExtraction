package com.alera.payloadextraction.presentation.status

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.alera.payloadextraction.presentation.payload.DeviceStatusPayload
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.time.OffsetDateTime

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class DeviceStatusReader(
    private val context: Context
) {
    private val nodeClient =
        Wearable.getNodeClient(context)

    suspend fun readStatus(): DeviceStatusPayload {
        val batteryManager =
            context.getSystemService(
                Context.BATTERY_SERVICE
            ) as BatteryManager

        val batteryPercent =
            batteryManager.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CAPACITY
            )

        val isCharging =
            readChargingStatus()

        val isWorn =
            readWornStatus()

        val connectedNodes =
            nodeClient.connectedNodes.await()

        val connectedNode =
            connectedNodes.firstOrNull()

        return DeviceStatusPayload(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            isWorn = isWorn,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            deviceModel = Build.MODEL,
            connectedToPhone = connectedNode != null,
            connectedPhoneName = connectedNode?.displayName,
            measuredAt = OffsetDateTime.now().toString()
        )
    }

    private fun readChargingStatus(): Boolean {
        val batteryIntent =
            context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return false

        val status =
            batteryIntent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                -1
            )

        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    private suspend fun readWornStatus(): Boolean? {
        val sensorManager =
            context.getSystemService(
                Context.SENSOR_SERVICE
            ) as SensorManager

        val offBodySensor =
            sensorManager.getDefaultSensor(
                Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT
            ) ?: return null

        return withTimeoutOrNull(2_000L) {
            suspendCancellableCoroutine { continuation ->

                val listener =
                    object : SensorEventListener {

                        override fun onSensorChanged(
                            event: SensorEvent
                        ) {
                            val value =
                                event.values.firstOrNull()
                                    ?: return

                            val isWorn =
                                value >= 0.5f

                            sensorManager.unregisterListener(
                                this
                            )

                            if (
                                continuation.isActive
                            ) {
                                continuation.resume(
                                    isWorn
                                )
                            }
                        }

                        override fun onAccuracyChanged(
                            sensor: Sensor?,
                            accuracy: Int
                        ) {
                            // Nothing needed.
                        }
                    }

                continuation.invokeOnCancellation {
                    sensorManager.unregisterListener(
                        listener
                    )
                }

                sensorManager.registerListener(
                    listener,
                    offBodySensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        }
    }

}