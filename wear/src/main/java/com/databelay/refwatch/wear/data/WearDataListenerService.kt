package com.databelay.refwatch.wear.data

import android.util.Log
import com.databelay.refwatch.common.AppJsonConfiguration
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.WearSyncConstants
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WearDataListenerService : WearableListenerService() {
    private val TAG = "WearDataListenerSvc"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    @Inject
    lateinit var gameStorage: GameStorageWear // Injected by Hilt

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        Log.d(TAG, "onDataChanged received ${dataEvents.count} events.")

        dataEvents.forEach { event ->
            Log.d(TAG, "Event: type=${event.type}, path=${event.dataItem.uri.path}")
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path == WearSyncConstants.GAMES_LIST_PATH) {
                    Log.i(TAG, "Games list DataItem changed from phone.")
                    try {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val gamesJsonString = dataMap.getString(WearSyncConstants.GAME_SETTINGS_KEY) // Ensure this key is correct
                        if (gamesJsonString != null) {
                            Log.d(TAG, "Received games JSON (length: ${gamesJsonString.length})")
                            // Before parsing, set status to FETCHING (or it's implicit that phone sent data)
                            // gameStorage.updateDataFetchStatus(DataFetchStatus.FETCHING) // Optional
                            val gameList = AppJsonConfiguration.decodeFromString<List<Game>>(gamesJsonString)
                            serviceScope.launch {
                                gameStorage.saveGamesListFromPhone(gameList) // This sets SUCCESS or NO_DATA_AVAILABLE
                            }
                        } else {
                            Log.w(TAG, "Games JSON string is null in DataItem. Phone sent empty data.")
                            serviceScope.launch {
                                gameStorage.saveGamesListFromPhone(emptyList()) // Results in NO_DATA_AVAILABLE
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing game list from DataItem", e)
                        serviceScope.launch {
                            gameStorage.updateDataFetchStatus(DataFetchStatus.ERROR_PARSING)
                        }
                    }
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                if (event.dataItem.uri.path == WearSyncConstants.GAMES_LIST_PATH) {
                    Log.i(TAG, "Games list DataItem deleted by phone.")
                    serviceScope.launch {
                        gameStorage.clearGamesListFromPhone() // This sets NO_DATA_AVAILABLE
                    }
                }
            }
        }
        dataEvents.release()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        Log.d(TAG, "Message received: ${messageEvent.path}")
        // Handle other messages if necessary, e.g., a specific "request_games_ack"
        // or a direct "phone_unreachable" message from your phone app if it implements that.
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        super.onCapabilityChanged(capabilityInfo)
        Log.d(TAG, "Capability changed: ${capabilityInfo.name}, Nodes: ${capabilityInfo.nodes.size}")
        // This is a good place to check for phone connectivity.
        // Assuming your phone app declares a capability like "refwatch_phone_app_capability"
        val phoneConnected = capabilityInfo.nodes.any { it.isNearby } // A simple check

        // You could directly call gameStorage here, but it's often better
        // for a ViewModel to observe this or for GameStorageWear to handle NodeClient itself.
        // For simplicity here if you want the service to directly influence it:
        if (capabilityInfo.name == WearSyncConstants.PHONE_APP_CAPABILITY) { // Define this constant
            Log.i(TAG, "Phone capability changed. Connected: $phoneConnected")
            serviceScope.launch {
                if (!phoneConnected && gameStorage.gamesListFlow.value.isEmpty()) {
                    // Only set to unreachable if we have no games and the phone node disappeared
                    // Avoid overriding PARSING_ERROR or other specific errors with this.
                    if (gameStorage.dataFetchStatusFlow.value != DataFetchStatus.ERROR_PARSING &&
                        gameStorage.dataFetchStatusFlow.value != DataFetchStatus.ERROR_UNKNOWN) {
                        gameStorage.updateDataFetchStatus(DataFetchStatus.ERROR_PHONE_UNREACHABLE)
                    }
                } else if (phoneConnected && gameStorage.dataFetchStatusFlow.value == DataFetchStatus.ERROR_PHONE_UNREACHABLE) {
                    // Phone reconnected, set status to initial to allow a refresh or indicate it can load
                    gameStorage.updateDataFetchStatus(DataFetchStatus.INITIAL)
                }
                // If phone is connected and status was SUCCESS/LOADED_FROM_CACHE, do nothing here.
                // A new data sync will update status via onDataChanged.
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(TAG, "Service destroyed.")
    }
}
