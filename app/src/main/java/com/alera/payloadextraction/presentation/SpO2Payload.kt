package com.alera.payloadextraction.presentation.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpO2Payload(
    @SerialName("event_type")
    val eventType: String = "spo2",

    @SerialName("measured_at")
    val measuredAt: String,

    @SerialName("spo2_percent")
    val spo2Percent: Double,

    val status: Int
) {
}