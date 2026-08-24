package com.alera.payloadextraction.presentation.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HeartRatePayload (
    @SerialName("event_type")
    val eventType: String = "heart_rate",

    @SerialName("measured_at")
    val measuredAt: String,

    @SerialName("heart_rate_bpm")
    val heartRateBpm: Double,

    val status: Int
){
}