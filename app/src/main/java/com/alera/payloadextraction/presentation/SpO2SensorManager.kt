package com.alera.payloadextraction.presentation

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alera.payloadextraction.presentation.payload.SpO2Payload
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json

class SpO2SensorManager(
    private val healthTrackingService: HealthTrackingService,
    private val onSpO2Changed: (Double?) -> Unit,
    private val onStatusChanged: (Int?) -> Unit,
    private val onMeasuredAtChanged: (String) -> Unit,
    private val onMeasurementFinished: () -> Unit
) {
    private var spo2Tracker: HealthTracker? = null

    private var isMeasuring = false
    private var schedulerRunning = false

    private var intervalMinutes: Long = 1

    private val handler =
        Handler(Looper.getMainLooper())

    private val scheduledMeasurement =
        object : Runnable {
            override fun run() {
                if (!isMeasuring) {
                    startMeasurement()
                }
            }
        }

    private val spo2Listener =
        object : HealthTracker.TrackerEventListener {

            override fun onDataReceived(
                dataPoints: List<DataPoint>
            ) {
                val latestDataPoint =
                    dataPoints.lastOrNull() ?: return

                val spo2 =
                    latestDataPoint.getValue(
                        ValueKey.SpO2Set.SPO2
                    )

                val status =
                    latestDataPoint.getValue(
                        ValueKey.SpO2Set.STATUS
                    )

                Log.d(
                    "AleraSensor",
                    "SpO2: $spo2%, status: $status"
                )

                onStatusChanged(status)

                    val measuredAt =
                        OffsetDateTime.now().toString()

                    val spo2Payload =
                        SpO2Payload(
                            measuredAt = measuredAt,
                            spo2Percent = spo2.toDouble(),
                            status = status
                        )

                    val spo2Json =
                        Json.encodeToString(spo2Payload)

                    Log.d(
                        "AleraSpO2Payload",
                        spo2Json
                    )

                    onSpO2Changed(
                        spo2.toDouble()
                    )

                    onMeasuredAtChanged(
                        measuredAt
                    )

                    stopMeasurement()
                 if (
                     status == 2 ||
                     status == -4 ||
                     status == -5 ||
                     status == -6
                ) {
                    stopMeasurement()
                }
            }

            override fun onFlushCompleted() {
                Log.d(
                    "AleraSensor",
                    "SpO2 flush completed"
                )
            }

            override fun onError(
                error: HealthTracker.TrackerError
            ) {
                Log.e(
                    "AleraSensor",
                    "SpO2 tracking error: $error"
                )

                stopMeasurement()
            }
        }

    fun prepare() {
        spo2Tracker =
            healthTrackingService.getHealthTracker(
                HealthTrackerType.SPO2_ON_DEMAND
            )

        Log.d(
            "AleraSensor",
            "SpO2 tracker prepared"
        )

        startSchedule()
    }

    private fun startSchedule() {
        if (schedulerRunning) {
            return
        }

        schedulerRunning = true

        startMeasurement()
    }

    private fun startMeasurement() {
        if (isMeasuring) {
            return
        }

        val tracker = spo2Tracker

        if (tracker == null) {
            Log.e(
                "AleraSensor",
                "Cannot start SpO2: tracker is null"
            )
            return
        }

        isMeasuring = true

        onSpO2Changed(null)
        onStatusChanged(0)

        tracker.setEventListener(
            spo2Listener
        )

        Log.d(
            "AleraSensor",
            "SpO2 measurement started"
        )
    }

    private fun stopMeasurement() {
        if (!isMeasuring) {
            return
        }

        spo2Tracker?.unsetEventListener()
        isMeasuring = false

        Log.d(
            "AleraSensor",
            "SpO2 measurement stopped"
        )

        onMeasurementFinished()

        scheduleNextMeasurement()
    }

    private fun scheduleNextMeasurement() {
        if (!schedulerRunning) {
            return
        }

        val delayMillis =
            intervalMinutes * 60 * 1000L

        handler.removeCallbacks(
            scheduledMeasurement
        )

        handler.postDelayed(
            scheduledMeasurement,
            delayMillis
        )

        Log.d(
            "AleraSensor",
            "Next SpO2 measurement in $intervalMinutes minutes"
        )
    }

    fun updateInterval(minutes: Long) {
        if (minutes < 1) {
            return
        }

        intervalMinutes = minutes

        Log.d(
            "AleraSensor",
            "SpO2 interval changed to $minutes minutes"
        )

        if (schedulerRunning && !isMeasuring) {
            scheduleNextMeasurement()
        }
    }

    fun release() {
        schedulerRunning = false

        handler.removeCallbacks(
            scheduledMeasurement
        )

        spo2Tracker?.unsetEventListener()
        spo2Tracker = null
        isMeasuring = false

        Log.d(
            "AleraSensor",
            "SpO2 manager released"
        )
    }
}