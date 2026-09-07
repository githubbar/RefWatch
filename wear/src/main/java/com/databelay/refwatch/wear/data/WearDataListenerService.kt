package com.databelay.refwatch.wear.data

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.databelay.refwatch.common.WearSyncConstants
import com.databelay.refwatch.wear.auth.WatchAuthManager
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * WearDataListenerService:
 * Although data is obtained directly from firestore we still need to keep this service to get auth token from phone
 *
 */
@AndroidEntryPoint
class WearDataListenerService : WearableListenerService() {
    private val tag = "WearDataListenerSvc"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    @Inject
    lateinit var watchAuthManager: WatchAuthManager

    @Inject
    lateinit var prefs: SharedPreferences

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(tag, "onDataChanged - events: ${dataEvents.count}")

        var newReceivedPhoneUserId: String? = null
        var phoneUserIdChanged = false

        dataEvents.forEach { event ->
            Log.d(tag, "Event: type=${event.type}, path=${event.dataItem.uri.path}")
            val dataItem = event.dataItem

            when (dataItem.uri.path) {
                WearSyncConstants.PATH_PHONE_USER_ID -> {
                    when (event.type) {
                        DataEvent.TYPE_CHANGED -> {
                            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                            val receivedUserId = dataMap.getString(WearSyncConstants.KEY_USER_ID)
                            val receivedToken = dataMap.getString(WearSyncConstants.KEY_CUSTOM_AUTH_TOKEN)
                            Log.i(tag, "Phone User ID DataItem CHANGED. New User ID: $receivedUserId")
                            Log.i(tag, "Custom Auth Token DataItem CHANGED. Received Token: $receivedToken")
                            if (!receivedToken.isNullOrBlank()) {
                                serviceScope.launch {
                                    watchAuthManager.signInWithCustomToken(receivedToken)
                                }
                            } else {
                                Log.w(tag, "Received a blank or null custom auth token.")
                            }
                            newReceivedPhoneUserId = receivedUserId
                            if (watchAuthManager.currentPhoneUserId.value != receivedUserId) {
                                phoneUserIdChanged = true
                            }
                        }
                        DataEvent.TYPE_DELETED -> {
                            Log.i(tag, "Phone User ID DataItem DELETED. User logged out on phone.")
                            newReceivedPhoneUserId = null
                            if (watchAuthManager.currentPhoneUserId.value != null) {
                                phoneUserIdChanged = true
                            }
                        }
                    }
                }

                // Settings the phone owns. Cached in the watch's own prefs so the value
                // survives restarts and is available while the phone is out of range.
                WearSyncConstants.PATH_SETTINGS -> {
                    if (event.type == DataEvent.TYPE_CHANGED) {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val logGoalScorer =
                            dataMap.getBoolean(WearSyncConstants.KEY_LOG_GOAL_SCORER, false)
                        val collectPositionInfo =
                            dataMap.getBoolean(WearSyncConstants.KEY_COLLECT_POSITION_INFO, false)
                        Log.i(
                            tag,
                            "Settings received from phone. logGoalScorer=$logGoalScorer, " +
                                "collectPositionInfo=$collectPositionInfo"
                        )
                        prefs.edit {
                            putBoolean(WearSyncConstants.KEY_LOG_GOAL_SCORER, logGoalScorer)
                            putBoolean(
                                WearSyncConstants.KEY_COLLECT_POSITION_INFO,
                                collectPositionInfo
                            )
                        }
                    }
                }
            }
        }
        dataEvents.release()

        if (phoneUserIdChanged) {
            serviceScope.launch {
                Log.d(tag, "Updating global Phone User ID to: $newReceivedPhoneUserId")
                watchAuthManager.updateCurrentPhoneUserId(newReceivedPhoneUserId)
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        super.onCapabilityChanged(capabilityInfo)
        Log.d(tag, "onCapabilityChanged received: ${capabilityInfo.name}, Nodes: ${capabilityInfo.nodes.size}")

        if (capabilityInfo.name == WearSyncConstants.PHONE_APP_CAPABILITY) {
            val isPhoneConnected = capabilityInfo.nodes.any { it.isNearby }
            Log.i(tag, "Phone app capability changed. Connected: $isPhoneConnected")

            // Call to gameStorage.updatePhoneConnectivityStatus(isPhoneConnected) removed.
            // GameStorageWear relies on its own ConnectivityObserver for general network status
            // relevant to Firebase operations.
            // If you have other actions that depend *specifically* on the phone companion being connected,
            // you might dispatch an event or update a different shared state here.
            // For now, we assume GameStorageWear doesn't need this specific trigger.
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(tag, "Service destroyed.")
    }
}
