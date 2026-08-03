package com.alera.payloadextraction.presentation.status

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.alera.payloadextraction.presentation.payload.DeviceStatusPayload
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.time.OffsetDateTime

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

        val connectedNodes =
            nodeClient.connectedNodes.await()

        val connectedNode =
            connectedNodes.firstOrNull()

        return DeviceStatusPayload(
            batteryPercent = batteryPercent,
            deviceName =
                "${Build.MANUFACTURER} ${Build.MODEL}",
            deviceModel = Build.MODEL,
            connectedToPhone =
                connectedNode != null,
            connectedPhoneName =
                connectedNode?.displayName,
            measuredAt =
                OffsetDateTime.now().toString()
        )
    }
}