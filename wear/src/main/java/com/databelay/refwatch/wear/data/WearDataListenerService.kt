package com.databelay.refwatch.wear.data

import android.util.Log
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.WearSyncConstants
import com.google.android.gms.wearable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer // For List<Game>
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.StandardCharsets
@AndroidEntryPoint // <<<< ADD THIS
class WearDataListenerService : WearableListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val TAG = "WearDataListener"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        classDiscriminator = "eventType" // If needed for GameEvent
    }
    @Inject // <<<< ADD THIS for field injection
    lateinit var gameStorage: GameStorageWear

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        Log.d(TAG, "Message received! Path: ${messageEvent.path}")
        val message = String(messageEvent.data, StandardCharsets.UTF_8)
        Log.d(TAG, "Received message: $message")

    }


    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged received ${dataEvents.count} events.")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path == WearSyncConstants.GAMES_LIST_PATH) {
                    Log.i(TAG, "Game list data item changed from phone.")
                    try {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val gamesJsonString = dataMap.getString(WearSyncConstants.GAME_SETTINGS_KEY)
                        if (gamesJsonString != null) {
                            Log.d(TAG, "Received games JSON string (length: ${gamesJsonString.length})")
                            // Deserialize List<Game>
                            val gameList = json.decodeFromString(ListSerializer(Game.serializer()), gamesJsonString)
                            Log.i(TAG, "Successfully deserialized ${gameList.size} games.")

                            serviceScope.launch {
                                gameStorage.saveGamesList(gameList)
                                // TODO: Optionally, notify active UI/ViewModel to refresh
                                // This could be via a LocalBroadcastManager, a SharedFlow in a singleton,
                                // or the ViewModel re-fetching on next observation.
                            }
                        } else {
                            Log.w(TAG, "Games JSON string is null in DataItem.")
                            // This might mean the phone sent an empty list to clear data
                            // or an error occurred. If phone sends empty list to clear,
                            // we should clear local storage.
                            // Let's assume phone sends empty JSON array "[]" for empty list.
                            // If it's truly null, maybe it's a deletion of the DataItem path.
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing game list DataItem", e)
                    }
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                if (event.dataItem.uri.path == WearSyncConstants.GAMES_LIST_PATH) {
                    Log.i(TAG, "Game list DataItem deleted by phone. Clearing local storage.")
                    serviceScope.launch {
                        gameStorage.clearGamesList()
                        // TODO: Notify UI/ViewModel
                    }
                }
            }
        }
        dataEvents.release() // Crucial: Release the buffer
    }

    // In WearDataListenerService.kt
    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "WearDataListenerService CREATED") // Use a prominent tag/level
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // Cancel coroutines when service is destroyed
        Log.d(TAG, "WearDataListenerService destroyed.")
    }
}