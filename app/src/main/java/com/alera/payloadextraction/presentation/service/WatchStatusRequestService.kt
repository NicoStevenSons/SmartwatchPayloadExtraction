package com.alera.payloadextraction.presentation.service

import android.util.Log
import com.alera.payloadextraction.presentation.status.DeviceStatusReader
import com.alera.payloadextraction.presentation.transfer.WearPayloadSender
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class WatchStatusRequestService : WearableListenerService() {

    companion object {
        private const val TAG = "AleraWatchStatus"

        private const val STATUS_REQUEST_PATH =
            "/alera/device-status/request"

        private const val STATUS_RESPONSE_PATH =
            "/alera/device-status"
    }

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private lateinit var deviceStatusReader:
            DeviceStatusReader

    private lateinit var wearPayloadSender:
            WearPayloadSender

    override fun onCreate() {
        super.onCreate()

        deviceStatusReader =
            DeviceStatusReader(applicationContext)

        wearPayloadSender =
            WearPayloadSender(applicationContext)
    }

    override fun onMessageReceived(
        messageEvent: MessageEvent
    ) {
        super.onMessageReceived(messageEvent)

        if (
            messageEvent.path !=
            STATUS_REQUEST_PATH
        ) {
            return
        }

        Log.d(
            TAG,
            "Device status requested by phone"
        )

        serviceScope.launch {
            try {
                val status =
                    deviceStatusReader.readStatus()

                val json =
                    Json {
                        encodeDefaults = true
                    }

                val statusJson =
                    json.encodeToString(status)

                val sent =
                    wearPayloadSender.sendPayloadToNode(
                        nodeId = messageEvent.sourceNodeId,
                        path = STATUS_RESPONSE_PATH,
                        json = statusJson
                    )

                Log.d(
                    TAG,
                    "Device status response sent: " +
                            "$sent | $statusJson"
                )
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Failed to respond with device status",
                    exception
                )
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()

        super.onDestroy()
    }
}