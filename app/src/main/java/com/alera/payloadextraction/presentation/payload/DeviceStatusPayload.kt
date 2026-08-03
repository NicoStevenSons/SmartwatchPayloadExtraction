package com.alera.payloadextraction.presentation.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceStatusPayload(
    @SerialName("event_type")
    val eventType: String = "device_status",

    @SerialName("battery_percent")
    val batteryPercent: Int,

    @SerialName("device_name")
    val deviceName: String,

    @SerialName("device_model")
    val deviceModel: String,

    @SerialName("connected_to_phone")
    val connectedToPhone: Boolean,

    @SerialName("connected_phone_name")
    val connectedPhoneName: String?,

    @SerialName("measured_at")
    val measuredAt: String
)