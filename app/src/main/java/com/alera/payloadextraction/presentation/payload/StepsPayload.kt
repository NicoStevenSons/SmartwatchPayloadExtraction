package com.alera.payloadextraction.presentation.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StepsPayload(
    @SerialName("event_type")
    val eventType: String = "steps",

    @SerialName("recorded_at")
    val recordedAt: String,

    @SerialName("start_time")
    val startTime: String,

    @SerialName("end_time")
    val endTime: String,

    @SerialName("step_count")
    val stepCount: Long,

    @SerialName("source")
    val source: String = "samsung_health"
)