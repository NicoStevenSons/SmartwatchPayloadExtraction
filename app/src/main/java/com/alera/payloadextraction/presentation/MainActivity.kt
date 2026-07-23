/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.alera.payloadextraction.presentation

import java.time.OffsetDateTime
import android.content.Context
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.alera.payloadextraction.presentation.theme.PayloadExtractionTheme
import kotlinx.serialization.json.Json
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import androidx.compose.runtime.mutableStateOf
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.ValueKey
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var healthTrackingService: HealthTrackingService
    private var heartRateTracker: HealthTracker? = null
    private val heartRateState = mutableStateOf<Double?>(null)
    private val spo2State = mutableStateOf<Double?>(null)
    private val spo2StatusState = mutableStateOf<Int?>(null)
    private lateinit var spo2SensorManager: SpO2SensorManager
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

                runOnUiThread {
                    heartRateState.value = heartRate.toDouble()
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

                onMeasurementFinished = {
                    startHeartRateTracking()
                }
            )

        stopHeartRateTracking()

        spo2SensorManager.prepare()
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
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkBodySensorPermission()

        setContent {
            WearApp(
                heartRateBpm = heartRateState.value,
                spo2Percent = spo2State.value,
                spo2Status = spo2StatusState.value
            )
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

        Log.d(
            "AleraSensor",
            "Continuous heart rate supported: $heartRateSupported"
        )

        if (heartRateSupported) {
            prepareHeartRateTracker()
        }

        val spo2Supported =
            availableTrackers.contains(
                HealthTrackerType.SPO2_ON_DEMAND
            )

        Log.d(
            "AleraSensor",
            "SpO2 on-demand supported: $spo2Supported"
        )

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

    private fun stopHeartRateTracking() {
        heartRateTracker?.unsetEventListener()

        Log.d(
            "AleraSensor",
            "Heart-rate tracking stopped"
        )
    }

    override fun onDestroy() {
        stopHeartRateTracking()


        if (::spo2SensorManager.isInitialized) {
            spo2SensorManager.release()
        }


        if (::healthTrackingService.isInitialized) {
            healthTrackingService.disconnectService()
        }

        super.onDestroy()
    }
}


@Composable
fun WearApp(heartRateBpm: Double?, spo2Percent: Double?, spo2Status: Int?) {
    PayloadExtractionTheme {
        AppScaffold {
            PayloadScreen(
                heartRateBpm = heartRateBpm,
                spo2Percent = spo2Percent,
                spo2Status = spo2Status
            )
        }
    }
}

@Composable
fun PayloadScreen(heartRateBpm: Double?, spo2Percent: Double?, spo2Status: Int?) {
    val context = LocalContext.current

    val batteryPercent = remember {
        readBatteryPercentage(context)
    }

    val isConnected = remember {
        isNetworkConnected(context)
    }

    val payload = remember(batteryPercent, isConnected, heartRateBpm, spo2Percent) {
        SmartwatchPayload(
            measuredAt = OffsetDateTime.now().toString(),
            batteryPercent = batteryPercent,
            heartRateBpm = heartRateBpm,
            spo2Percent = spo2Percent,
            isConnected = isConnected
        )
    }

    val payloadJson = Json.encodeToString(payload)

    Log.d("AleraPayload",payloadJson)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
                                                TransformingLazyColumn( contentPadding = contentPadding,
                                                                        state = listState) {
                                                    item{
                                                        ListHeader(modifier = Modifier
                                                            .fillMaxWidth()
                                                            .transformedHeight(
                                                                this,
                                                                transformationSpec
                                                            ),
                                                            transformation =
                                                                SurfaceTransformation(transformationSpec)
                                                        ) {
                                                            Text("Alera Payload")

                                                        }
                                                    }

                                                    item{
                                                        Text(text = "Battery: $batteryPercent%")
                                                    }
                                                    item {
                                                        Text(text = heartRateBpm?.let{
                                                            "Heart Rate: ${it.toInt()}BPM"
                                                            } ?:"Heart Rate : Waiting"
                                                        )
                                                    }
                                                    item {
                                                        Text(text = spo2Percent?.let{
                                                            "Spo2: ${it.toInt()}%"
                                                        }?:"SpO₂ Status: ${spo2Status ?: "Waiting"}")
                                                    }
                                                   item {
                                                       Text(
                                                           text = if (isConnected) { "Connection: Online"
                                                                } else { "Connection: Offline"
                                                           }
                                                       )
                                                   }

                                                }

    }
}

fun readBatteryPercentage(context: Context): Int {
    val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE)
                as BatteryManager

    return batteryManager.getIntProperty(
        BatteryManager.BATTERY_PROPERTY_CAPACITY
    )
}

fun isNetworkConnected(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    val activeNetwork = connectivityManager.activeNetwork
        ?: return false

    val capabilities =
        connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return false

    return capabilities.hasCapability(
        NetworkCapabilities.NET_CAPABILITY_INTERNET
    )
}


@WearPreviewDevices
@Composable
fun DefaultPreview() {
    WearApp(
        heartRateBpm = 78.0,
        spo2Percent = 97.0,
        spo2Status = 2
    )
}