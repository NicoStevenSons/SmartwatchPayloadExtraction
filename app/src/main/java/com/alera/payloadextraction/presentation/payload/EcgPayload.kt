package com.alera.payloadextraction.presentation.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EcgPayload(
    @SerialName("event_type")
    val eventType: String = "ecg",

    @SerialName("measured_at")
    val measuredAt: String,

    @SerialName("sample_rate_hz")
    val sampleRateHz: Int?,

    val samples: List<EcgSamplePayload>,

    val status: Int,

    val source: String = "samsung_health_sensor_sdk"
)

@Serializable
data class EcgSamplePayload(
    @SerialName("timestamp_offset_ms")
    val timestampOffsetMs: Long,

    val value: Double
)