package com.databelay.refwatch

import android.util.Log
import android.widget.Toast
import com.databelay.refwatch.common.WearSyncConstants
import com.google.android.gms.wearable.DataClient // Import the interface
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient   // Import the interface
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
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
    private val messageClient: MessageClient, // Injected by Hilt
    private val dataClient: DataClient, // Injected by Hilt
    private val nodeClient: NodeClient, // Injected by Hilt

    // Optionally inject a scope, or create one internally
    // @ApplicationCoroutineScope private val coroutineScope: CoroutineScope // If you define a custom scope
) {
    // If not injecting a scope, create one internally:
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "PhonePinger"


    fun sendPing() {
        coroutineScope.launch {
            try {
                Log.d(TAG, "Attempting to send a ping...")
                // 1. Get the NodeClient

                // 2. Get all connected nodes (watches)
                nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                    if (nodes.isEmpty()) {
                        Log.w(TAG, "No connected Wear OS nodes found.")
                        return@addOnSuccessListener
                    }
                    // 3. Send the message to each connected node
                    nodes.forEach { node ->
                        val payload = "Ping from Phone @ ${System.currentTimeMillis()}".toByteArray()

                        messageClient.sendMessage(node.id, WearSyncConstants.GAMES_LIST_PATH, payload)
                            .addOnSuccessListener {
                                Log.d(TAG, "Ping sent successfully to ${node.displayName}")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to send ping to ${node.displayName}", e)
                            }
                    }
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get connected nodes", e)
                }
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