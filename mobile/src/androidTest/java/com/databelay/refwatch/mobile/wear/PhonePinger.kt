package com.databelay.refwatch.mobile.wear // << ADJUST THIS PACKAGE TO YOUR PHONE APP'S STRUCTURE

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class PhonePinger(private val context: Context) {

    // Get the application context to avoid memory leaks with Activity context
    private val applicationContext = context.applicationContext
    private val dataClient by lazy { Wearable.getDataClient(applicationContext) }

    // Use a custom scope that can be cancelled if PhonePinger were part of a lifecycle component
    // For a simple utility, a global scope might be okay for one-off calls, but be mindful.
    // For now, let's keep it simple and assume it's called from a ViewModel's scope or similar.
    // If called directly from an Activity, pass its lifecycleScope or create one that's managed.

    companion object {
        private const val TAG = "PhonePinger"
        const val MINIMAL_PING_PATH = "/minimal_test_ping" // Must match what watch service expects
        const val PING_MESSAGE_KEY = "ping_message"
    }

    fun sendPing(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) { // Allow passing a scope
        scope.launch { // Launch within the provided or default scope
            Log.d(TAG, "Attempting to send minimal ping to path: $MINIMAL_PING_PATH")
            try {
                // Check for connected nodes (optional, but good for debugging)
                val nodes = Wearable.getNodeClient(applicationContext).connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No Wear OS nodes connected. Ping might be queued or fail to deliver immediately.")
                } else {
                    nodes.forEach { node ->
                        Log.i(TAG, "Connected Node: ${node.displayName} (${node.id}) - isNearby: ${node.isNearby}")
                    }
                }

                val putDataMapReq = PutDataMapRequest.create(MINIMAL_PING_PATH)
                val message = "Hello Minimal Wear Service from PhonePinger @ ${System.currentTimeMillis()}"

                putDataMapReq.dataMap.putString(PING_MESSAGE_KEY, message)
                putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis()) // To ensure it's seen as new data
                putDataMapReq.setUrgent() // Attempt to send more quickly

                val putDataReq = putDataMapReq.asPutDataRequest()

                dataClient.putDataItem(putDataReq).await() // Suspend until task completes

                Log.i(TAG, "Minimal ping SENT successfully from PhonePinger to path: $MINIMAL_PING_PATH with message: '$message'")
                withContext(Dispatchers.Main) {
                    // Toast.makeText(applicationContext, "Ping sent!", Toast.LENGTH_SHORT).show() // Example
                }

            } catch (e: Exception) {
                Log.e(TAG, "Minimal ping send FAILED from PhonePinger to path: $MINIMAL_PING_PATH", e)
                withContext(Dispatchers.Main) {
                    // Toast.makeText(applicationContext, "Ping failed: ${e.message}", Toast.LENGTH_LONG).show() // Example
                }
            }
        }
    }
}