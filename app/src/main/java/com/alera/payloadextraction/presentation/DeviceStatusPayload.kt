package com.alera.payloadextraction.presentation.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceStatusPayload (
    @SerialName("event_type")
    val eventType: String = "device_status",

    @SerialName("recorded_at")
    val recordedAt: String,

    @SerialName("battery_percent")
    val batteryPercent: Int,

    @SerialName("is_charging")
    val isCharging: Boolean,

    @SerialName("is_connected")
    val isConnected: Boolean,

    @SerialName("connection_type")
    val connectionType: String? = null
){
}