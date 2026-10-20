package com.alera.payloadextraction.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AppScaffold
import com.alera.payloadextraction.presentation.payload.HeartRatePayload
import com.alera.payloadextraction.presentation.sensor.SpO2SensorManager
import com.alera.payloadextraction.presentation.status.DeviceStatusReader
import com.alera.payloadextraction.presentation.theme.PayloadExtractionTheme
import com.alera.payloadextraction.presentation.transfer.WearPayloadSender
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import java.time.OffsetDateTime
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    private lateinit var wearPayloadSender: WearPayloadSender
    private lateinit var healthTrackingService: HealthTrackingService
    private var heartRateTracker: HealthTracker? = null
    private val heartRateState = mutableStateOf<Double?>(null)
    private val spo2State = mutableStateOf<Double?>(null)
    private val spo2StatusState = mutableStateOf<Int?>(null)
    private lateinit var spo2SensorManager: SpO2SensorManager
    private val spo2MeasuredAtState = mutableStateOf<String?>(null)
    private val isWornState = mutableStateOf<Boolean?>(null)
    private val batteryPercentState = mutableStateOf<Int?>(null)
    private val phoneConnectedState = mutableStateOf<Boolean?>(null)
    private lateinit var deviceStatusReader:  DeviceStatusReader
    private val bodySensorPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                Log.d(
                    "AleraSensor",
                    "Body sensor permission granted"
                )

                connectToHealthPlatform()
            } else {
                Log.e(
                    "AleraSensor",
                    "Body sensor permission denied"
                )
            }
        }

    private fun checkBodySensorPermission() {
        val permissionGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BODY_SENSORS
            ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            Log.d(
                "AleraSensor",
                "Body sensor permission already granted"
            )

            connectToHealthPlatform()
        } else {
            Log.d(
                "AleraSensor",
                "Requesting body sensor permission"
            )

            bodySensorPermissionLauncher.launch(
                Manifest.permission.BODY_SENSORS
            )
        }
    }
    private val heartRateListener =
        object : HealthTracker.TrackerEventListener {

            override fun onDataReceived(
                dataPoints: List<DataPoint>
            ) {
                val latestDataPoint =
                    dataPoints.lastOrNull() ?: return

                val heartRate =
                    latestDataPoint.getValue(
                        ValueKey.HeartRateSet.HEART_RATE
                    )

                val heartRateStatus =
                    latestDataPoint.getValue(
                        ValueKey.HeartRateSet.HEART_RATE_STATUS
                    )


                Log.d(
                    "AleraSensor",
                    "Heart rate: $heartRate BPM, status: $heartRateStatus"
                )

                    val heartRatePayload =
                        HeartRatePayload(
                            measuredAt =
                                OffsetDateTime.now().toString(),
                            heartRateBpm =
                                heartRate.toDouble(),
                            status =
                                heartRateStatus
                        )

                val json = Json {
                    encodeDefaults = true
                }

                val heartRateJson = json.encodeToString(heartRatePayload)

                    Log.d(
                        "AleraHeartRatePayload",
                        heartRateJson
                    )

                lifecycleScope.launch {
                    wearPayloadSender.sendPayload(
                        path = "/alera/heart-rate",
                        json = heartRateJson
                    )
                }

                    runOnUiThread {
                        // Avoid redrawing for sensor samples that display the same BPM.
                        val displayBpm = heartRate.toInt().toDouble()
                        if (heartRateState.value != displayBpm) {
                            heartRateState.value = displayBpm
                        }
                    }

            }

            override fun onFlushCompleted() {
                Log.d(
                    "AleraSensor",
                    "Heart-rate flush completed"
                )
            }

            override fun onError(
                error: HealthTracker.TrackerError
            ) {
                Log.e(
                    "AleraSensor",
                    "Heart-rate tracking error: $error"
                )
            }
        }

    private val connectionListener =
        object : ConnectionListener {

            override fun onConnectionSuccess() {
                Log.d(
                    "AleraSensor",
                    "Connected to Samsung Health Platform"
                )

                checkSupportedTrackers()
            }

            override fun onConnectionEnded() {
                Log.d(
                    "AleraSensor",
                    "Samsung Health Platform connection ended"
                )
            }

            override fun onConnectionFailed(
                exception: HealthTrackerException
            ) {
                Log.e(
                    "AleraSensor",
                    "Connection failed: ${exception.errorCode}",
                    exception
                )

                if (exception.hasResolution()) {
                    exception.resolve(this@MainActivity)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceStatusReader =
            DeviceStatusReader(this)

        wearPayloadSender =
            WearPayloadSender(applicationContext)

        checkBodySensorPermission()
        deviceStatusHandler.post(deviceStatusRunnable)

        setContent {
            PayloadExtractionTheme {
                AppScaffold {
                    WatchDashboard(
                        heartRateBpm = heartRateState.value,
                        spo2Percent = spo2State.value,
                        spo2Status = spo2StatusState.value,
                        isWorn = isWornState.value,
                        batteryPercent = batteryPercentState.value,
                        phoneConnected = phoneConnectedState.value
                    )
                }
            }
        }
    }

    private fun sendDeviceStatus() {
        lifecycleScope.launch {
            try {
                val status =
                    deviceStatusReader.readStatus()

                val json = Json {
                    encodeDefaults = true
                }

                val statusJson =
                    json.encodeToString(status)

                runOnUiThread {
                    isWornState.value = status.isWorn
                    batteryPercentState.value = status.batteryPercent
                    phoneConnectedState.value = status.connectedToPhone
                }

                wearPayloadSender.sendPayload(
                    "/alera/device-status",
                    statusJson
                )

                Log.d(
                    "AleraDeviceStatus",
                    statusJson
                )
            } catch (exception: Exception) {
                Log.e(
                    "AleraDeviceStatus",
                    "Failed to send device status",
                    exception
                )
            }
        }
    }

    private fun connectToHealthPlatform() {
        healthTrackingService =
            HealthTrackingService(
                connectionListener,
                this
            )

        Log.d(
            "AleraSensor",
            "Connecting to Samsung Health Platform..."
        )

        healthTrackingService.connectService()
    }

    private fun checkSupportedTrackers() {
        val availableTrackers =
            healthTrackingService
                .trackingCapability
                .supportHealthTrackerTypes

        Log.d(
            "AleraSensor",
            "Available trackers: $availableTrackers"
        )

        val heartRateSupported =
            availableTrackers.contains(
                HealthTrackerType.HEART_RATE_CONTINUOUS
            )

        val spo2Supported =
            availableTrackers.contains(
                HealthTrackerType.SPO2_ON_DEMAND
            )

        Log.d(
            "AleraSensor",
            "Continuous heart rate supported: $heartRateSupported"
        )

        Log.d(
            "AleraSensor",
            "SpO2 on-demand supported: $spo2Supported"
        )

        if (heartRateSupported) {
            prepareHeartRateTracker()
        }

        if (spo2Supported) {
            prepareSpO2Tracker()
        }
    }

    private fun prepareHeartRateTracker() {
        heartRateTracker =
            healthTrackingService.getHealthTracker(
                HealthTrackerType.HEART_RATE_CONTINUOUS
            )

        Log.d(
            "AleraSensor",
            "Heart-rate tracker prepared"
        )

        startHeartRateTracking()
    }

    private fun startHeartRateTracking() {
        val tracker = heartRateTracker

        if (tracker == null) {
            Log.e(
                "AleraSensor",
                "Cannot start heart rate: tracker is null"
            )
            return
        }

        tracker.setEventListener(
            heartRateListener
        )

        Log.d(
            "AleraSensor",
            "Heart-rate tracking started"
        )
    }

    private fun stopHeartRateTracking() {
        heartRateTracker?.unsetEventListener()

        Log.d(
            "AleraSensor",
            "Heart-rate tracking stopped"
        )
    }

    private fun prepareSpO2Tracker() {
        spo2SensorManager =
            SpO2SensorManager(
                healthTrackingService = healthTrackingService,

                onSpO2Changed = { value ->
                    runOnUiThread {
                        spo2State.value = value
                    }
                },

                onStatusChanged = { status ->
                    runOnUiThread {
                        spo2StatusState.value = status
                    }
                },

                onMeasuredAtChanged = { measuredAt ->
                    runOnUiThread {
                        spo2MeasuredAtState.value = measuredAt
                    }
                },

                onMeasurementFinished = {
                    runOnUiThread {
                        startHeartRateTracking()
                    }
                },
                onPayloadReady = { spo2Json ->
                    lifecycleScope.launch {
                        val sent = wearPayloadSender.sendPayload(
                            "/alera/spo2",
                            spo2Json
                        )

                        Log.d(
                            "AleraTransfer",
                            "SpO2 payload sent: $sent"
                        )
                    }
                }
            )

        stopHeartRateTracking()

        spo2SensorManager.prepare()
    }

    override fun onDestroy() {
        stopHeartRateTracking()

        if (::spo2SensorManager.isInitialized) {
            spo2SensorManager.release()
        }

        if (::healthTrackingService.isInitialized) {
            healthTrackingService.disconnectService()
        }

        deviceStatusHandler.removeCallbacks(
            deviceStatusRunnable
        )

        super.onDestroy()
    }

    private val deviceStatusHandler =
        Handler(Looper.getMainLooper())

    private val deviceStatusRunnable =
        object : Runnable {
            override fun run() {
                sendDeviceStatus()

                deviceStatusHandler.postDelayed(
                    this,
                    60_000L
                )
            }
        }

}
