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

    suspend fun sendPayloadToNode(
        nodeId: String,
        path: String,
        json: String
    ): Boolean {
        return try {
            messageClient.sendMessage(
                nodeId,
                path,
                json.toByteArray(Charsets.UTF_8)
            ).await()

            Log.d(
                "AleraTransfer",
                "Sent payload to node $nodeId: $path"
            )

            true
        } catch (exception: Exception) {
            Log.e(
                "AleraTransfer",
                "Payload send to node failed",
                exception
            )

            false
        }
    }
}