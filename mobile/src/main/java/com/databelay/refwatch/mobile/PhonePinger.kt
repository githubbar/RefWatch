package com.databelay.refwatch.mobile

import android.util.Log
import com.google.android.gms.wearable.DataClient // Import the interface
import com.google.android.gms.wearable.NodeClient   // Import the interface
import com.google.android.gms.wearable.PutDataMapRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Define a custom CoroutineScope for injection if desired
// Or inject Dispatchers.IO directly if you prefer to create scope inside the class.
@Singleton // PhonePinger can be a singleton if its state is just context/clients
class PhonePinger @Inject constructor(
    private val dataClient: DataClient, // Injected by Hilt
    private val nodeClient: NodeClient, // Injected by Hilt
    // Optionally inject a scope, or create one internally
    // @ApplicationCoroutineScope private val coroutineScope: CoroutineScope // If you define a custom scope
) {
    // If not injecting a scope, create one internally:
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "PhonePinger"
        const val MINIMAL_PING_PATH = "/simple_ping_test"
        const val PING_MESSAGE_KEY = "ping_message"
    }

    fun sendPing() {
        coroutineScope.launch {
            Log.d(TAG, "Attempting to send minimal ping to path: $MINIMAL_PING_PATH")
            try {
                val nodes = nodeClient.connectedNodes.await() // Use injected nodeClient
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No Wear OS nodes connected.")
                } else {
                    nodes.forEach { node ->
                        Log.i(TAG, "Connected Node: ${node.displayName} (${node.id}) - isNearby: ${node.isNearby}")
                    }
                }

                val putDataMapReq = PutDataMapRequest.create(MINIMAL_PING_PATH)
                val message = "Hello Minimal Wear Service from Hilt PhonePinger @ ${System.currentTimeMillis()}"

                putDataMapReq.dataMap.putString(PING_MESSAGE_KEY, message)
                putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())
                putDataMapReq.setUrgent()

                val putDataReq = putDataMapReq.asPutDataRequest()

                dataClient.putDataItem(putDataReq).await() // Use injected dataClient

                Log.i(TAG, "Minimal ping SENT successfully: '$message'")
                // No Toast here, PhonePinger shouldn't know about UI elements like Context for Toasts
                // The ViewModel or Activity should handle UI feedback.

            } catch (e: Exception) {
                Log.e(TAG, "Minimal ping send FAILED", e)
            }
        }
    }

    // Optional: Method to cancel the scope if PhonePinger is not a @Singleton
    // and has a shorter lifecycle tied to something else.
    // fun cancelScope() {
    //     coroutineScope.cancel()
    // }
}