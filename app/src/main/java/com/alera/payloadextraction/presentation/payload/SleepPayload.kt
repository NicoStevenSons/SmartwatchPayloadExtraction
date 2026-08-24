package com.alera.payloadextraction.presentation.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SleepPayload(
    @SerialName("event_type")
    val eventType: String = "sleep_session",

    @SerialName("recorded_at")
    val recordedAt: String,

    @SerialName("sleep_start")
    val sleepStart: String,

    @SerialName("sleep_end")
    val sleepEnd: String,

    @SerialName("duration_minutes")
    val durationMinutes: Long,

    @SerialName("sleep_score")
    val sleepScore: Int? = null,

    @SerialName("stages")
    val stages: List<SleepStagePayload> = emptyList(),

    @SerialName("source")
    val source: String = "samsung_health"
)

@Serializable
data class SleepStagePayload(
    @SerialName("stage_type")
    val stageType: String,

    @SerialName("start_time")
    val startTime: String,

    @SerialName("end_time")
    val endTime: String,

    @SerialName("duration_minutes")
    val durationMinutes: Long
)