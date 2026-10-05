package com.databelay.refwatch.data

import android.util.Log
import com.databelay.refwatch.common.AppJsonConfiguration
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.WearSyncConstants
import com.google.android.gms.wearable.DataClient // For sending data back
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PhoneDataListenerService : WearableListenerService() {

    private val TAG = "PhoneDataListenerSvc"
    private val serviceJob = SupervisorJob()
    // Use Dispatchers.IO for database operations by default for this scope
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject // Injected by Hilt
    lateinit var gameRepository: GameStorageMobile

    @Inject // Injected by Hilt (or use Wearable.getDataClient(applicationContext))
    lateinit var dataClient: DataClient

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        Log.d(TAG, "onDataChanged received ${dataEvents.count} events on PHONE.")

        val currentUserId = getCurrentUserId()
        if (currentUserId == null) {
            Log.e(TAG, "No signed-in user on phone. Cannot process game data from watch.")
            dataEvents.release()
            return
        }

        dataEvents.forEach { event ->
            Log.d(TAG, "Phone Event: type=${event.type}, path=${event.dataItem.uri.path}")
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                val path = dataItem.uri.path

                val isAdHocGame = path?.startsWith(WearSyncConstants.NEW_ADHOC_GAME_FROM_WATCH_PATH_PREFIX) == true
                val isGameUpdate = path?.startsWith(WearSyncConstants.PATH_GAME_UPDATE) == true

                if (isAdHocGame || isGameUpdate) {
                    val gameIdFromPath = path.substringAfterLast("/")
                    val typeForLog = if (isAdHocGame) "NEW AD-HOC" else "UPDATE"
                    Log.i(TAG, "Received GAME $typeForLog from watch (path ID: $gameIdFromPath)")

                    try {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val gameJsonString = dataMap.getString(WearSyncConstants.KEY_GAME_UPDATE)

                        if (gameJsonString != null) {
                            val gameFromWatch = AppJsonConfiguration.decodeFromString<Game>(gameJsonString)
                            Log.i(TAG, "Successfully parsed game $typeForLog: ${gameFromWatch.id}, home: ${gameFromWatch.homeTeamName}, status: ${gameFromWatch.status}")

                            serviceScope.launch {
                                val saveResult = gameRepository.addOrUpdateGame(currentUserId, gameFromWatch)
                                if (saveResult.isSuccess) {
                                    // The watch sees the saved game through its own Firestore listener.
                                    Log.i(TAG, "Game ${gameFromWatch.id} ($typeForLog) saved/updated on phone for user $currentUserId.")
                                } else {
                                    Log.e(TAG, "Failed to save game ${gameFromWatch.id} ($typeForLog) for user $currentUserId: ${saveResult.exceptionOrNull()?.message}")
                                }
                            }
                        } else {
                            Log.w(TAG, "Game $typeForLog JSON string is null for path: $path")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing game $typeForLog (path: $path)", e)
                    }
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                // Handle deleted data items if your app uses this
                Log.d(TAG, "DataItem deleted on phone: ${event.dataItem.uri.path}")
                // Example: If watch could delete games and phone should mirror
                // val path = event.dataItem.uri.path
                // if (path?.startsWith(WearSyncConstants.GAME_DELETE_FROM_WATCH_PATH_PREFIX) == true) {
                // val gameId = path.substringAfterLast("/")
                // serviceScope.launch {
                // gameRepository.deleteGame(gameId)
                // Log.i(TAG, "Game $gameId deleted on phone per watch instruction.")
                // sendFullGameListToWatch() // Send updated list
                // }
                // }
            }
        }
        dataEvents.release() // Important to release the buffer
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(TAG, "PhoneDataListenerService destroyed.")
    }
}
