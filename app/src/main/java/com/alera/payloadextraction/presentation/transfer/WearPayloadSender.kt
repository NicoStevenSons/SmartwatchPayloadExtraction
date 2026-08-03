package com.alera.payloadextraction.presentation.transfer

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

class WearPayloadSender(
    context: Context
) {
    private val nodeClient =
        Wearable.getNodeClient(context)

    private val messageClient =
        Wearable.getMessageClient(context)

    suspend fun sendPayload(
        path: String,
        json: String
    ): Boolean {
        return try {
            val nodes =
                nodeClient.connectedNodes.await()

            if (nodes.isEmpty()) {
                Log.w(
                    "AleraTransfer",
                    "No connected phone found"
                )

                return false
            }

            nodes.forEach { node ->
                messageClient.sendMessage(
                    node.id,
                    path,
                    json.toByteArray(
                        Charsets.UTF_8
                    )
                ).await()
            }

            Log.d(
                "AleraTransfer",
                "Sent payload to phone: $path"
            )

            true
        } catch (exception: Exception) {
            Log.e(
                "AleraTransfer",
                "Payload send failed",
                exception
            )

            false
        }
    }
}