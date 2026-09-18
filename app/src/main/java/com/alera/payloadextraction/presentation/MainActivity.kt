/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.alera.payloadextraction.presentation

import android.content.Context
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
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
import java.time.OffsetDateTime
import com.alera.payloadextraction.presentation.payload.HeartRatePayload
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.alera.payloadextraction.presentation.sensor.SpO2SensorManager
import androidx.lifecycle.lifecycleScope
import com.alera.payloadextraction.presentation.transfer.WearPayloadSender
import kotlinx.coroutines.launch
import com.alera.payloadextraction.presentation.status.DeviceStatusReader

class MainActivity : ComponentActivity() {

    private lateinit var wearPayloadSender: WearPayloadSender
    private lateinit var healthTrackingService: HealthTrackingService
    private var heartRateTracker: HealthTracker? = null
    private val heartRateState = mutableStateOf<Double?>(null)
    private val spo2State = mutableStateOf<Double?>(null)
    private val spo2StatusState = mutableStateOf<Int?>(null)
    private lateinit var spo2SensorManager: SpO2SensorManager
    private val spo2MeasuredAtState = mutableStateOf<String?>(null)
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
                        heartRateState.value =
                            heartRate.toDouble()
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
            WearApp(
                heartRateBpm = heartRateState.value,
                spo2Percent = spo2State.value,
                spo2Status = spo2StatusState.value

            )
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

    @Composable
    fun WearApp(
        heartRateBpm: Double?,
        spo2Percent: Double?,
        spo2Status: Int?
    ) {
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
    fun PayloadScreen(
        heartRateBpm: Double?,
        spo2Percent: Double?,
        spo2Status: Int?
    ) {
        val context = LocalContext.current

        val batteryPercent = remember {
            readBatteryPercentage(context)
        }

        val isConnected = remember {
            isNetworkConnected(context)
        }

        val listState =
            rememberTransformingLazyColumnState()

        val background = Color(0xFF08080A)
        val cardColor = Color(0xFF15151A)
        val softCardColor = Color(0xFF1C1920)
        val primaryPink = Color(0xFFFF82BE)
        val secondaryPurple = Color(0xFFB8A0FF)
        val textPrimary = Color(0xFFF8F6FA)
        val textSecondary = Color(0xFFAAA5AF)
        val success = Color(0xFF82D8A0)
        val danger = Color(0xFFFF8D9B)

        ScreenScaffold(
            scrollState = listState
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .background(background)
            ) {
                TransformingLazyColumn(
                    contentPadding = contentPadding,
                    state = listState
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 18.dp,
                                    vertical = 8.dp
                                ),
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ALERA",
                                color = primaryPink,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )

                            Spacer(
                                modifier = Modifier.height(4.dp)
                            )

                            Text(
                                text = "Health at a glance",
                                color = textSecondary,
                                fontSize = 11.sp
                            )

                            Spacer(
                                modifier = Modifier.height(10.dp)
                            )

                            StatusPill(
                                label =
                                    if (isConnected) {
                                        "ONLINE"
                                    } else {
                                        "OFFLINE"
                                    },
                                dotColor =
                                    if (isConnected) {
                                        success
                                    } else {
                                        danger
                                    },
                                textColor = textPrimary,
                                cardColor = cardColor
                            )
                        }
                    }

                    item {
                        MetricCard(
                            eyebrow = "HEART RATE",
                            value =
                                heartRateBpm?.let {
                                    it.toInt().toString()
                                } ?: "--",
                            unit = "BPM",
                            accent = primaryPink,
                            cardColor = cardColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }

                    item {
                        MetricCard(
                            eyebrow = "BLOOD OXYGEN",
                            value =
                                spo2Percent?.let {
                                    it.toInt().toString()
                                } ?: "--",
                            unit = "%",
                            accent = secondaryPurple,
                            cardColor = softCardColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            footer =
                                if (spo2Percent == null) {
                                    "Status: ${spo2Status ?: "Waiting"}"
                                } else {
                                    "Latest SpO₂ reading"
                                }
                        )
                    }

                    item {
                        CompactStatusCard(
                            label = "WATCH BATTERY",
                            value = "$batteryPercent%",
                            accent = primaryPink,
                            cardColor = cardColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }

                    item {
                        CompactStatusCard(
                            label = "NETWORK",
                            value =
                                if (isConnected) {
                                    "Connected"
                                } else {
                                    "Offline"
                                },
                            accent =
                                if (isConnected) {
                                    success
                                } else {
                                    danger
                                },
                            cardColor = cardColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }

                    item {
                        Text(
                            text = "Alera is monitoring your watch",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 22.dp,
                                    vertical = 12.dp
                                ),
                            color = textSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusPill(
        label: String,
        dotColor: Color,
        textColor: Color,
        cardColor: Color
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = cardColor,
                    shape = RoundedCornerShape(50.dp)
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 7.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = dotColor,
                        shape = CircleShape
                    )
            )

            Text(
                text = "  $label",
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }
    }

    @Composable
    private fun MetricCard(
        eyebrow: String,
        value: String,
        unit: String,
        accent: Color,
        cardColor: Color,
        textPrimary: Color,
        textSecondary: Color,
        footer: String? = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 5.dp
                )
                .background(
                    color = cardColor,
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    color = accent.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(
                    horizontal = 18.dp,
                    vertical = 16.dp
                )
        ) {
            Text(
                text = eyebrow,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    color = textPrimary,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 40.sp
                )

                Text(
                    text = " $unit",
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            footer?.let {
                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Text(
                    text = it,
                    color = textSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }

    @Composable
    private fun CompactStatusCard(
        label: String,
        value: String,
        accent: Color,
        cardColor: Color,
        textPrimary: Color,
        textSecondary: Color
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 4.dp
                )
                .background(
                    color = cardColor,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(
                    horizontal = 16.dp,
                    vertical = 13.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = accent,
                        shape = CircleShape
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = label,
                    color = textSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.7.sp
                )

                Text(
                    text = value,
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
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
            spo2Status = 2,
        )
    }
}