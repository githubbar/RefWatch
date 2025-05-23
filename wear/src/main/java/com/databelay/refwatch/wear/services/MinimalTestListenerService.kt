package com.databelay.refwatch.wear.services

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class MinimalTestListenerService : WearableListenerService() {
    private val TAG = "MinimalTestListener"
    companion object {
        // Match the path and key from PhonePinger
        const val PING_PATH = "/simple_ping_test"
        const val PING_MESSAGE_KEY = "ping_message"
    }

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "!!!!!!!!!!!!!! MinimalTestListenerService CREATED !!!!!!!!!!!!!!")
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        Log.e(TAG, "!!!!!!!!!!!!!! MinimalTestListenerService onDataChanged - Count: ${dataEvents.count} !!!!!!!!!!!!!!")
        dataEvents.forEach { event ->
            Log.e(TAG, "Event URI: ${event.dataItem.uri}, Type: ${event.type}")
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path == PING_PATH) { // Use the constant
                    try {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val message = dataMap.getString(PING_MESSAGE_KEY) // Use the constant
                        Log.e(TAG, "!!!!!!!!!!!!!! PING RECEIVED: '$message' !!!!!!!!!!!!!!")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing ping DataItem", e)
                    }
                }
            }
        }
        dataEvents.release()
    }
    // ... onDestroy ...
}