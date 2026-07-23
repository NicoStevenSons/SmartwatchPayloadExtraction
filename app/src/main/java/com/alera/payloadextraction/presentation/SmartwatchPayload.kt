package com.alera.payloadextraction.presentation
import kotlinx.serialization.Serializable

@Serializable
data class SmartwatchPayload(
    val measuredAt: String,
    val batteryPercent: Int,
    val heartRateBpm: Double?,
    val spo2Percent: Double?,
    val isConnected: Boolean?
) {

}