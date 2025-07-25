package com.databelay.refwatch.games // Or your package

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.databelay.refwatch.PhonePinger
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.WearSyncConstants
import com.databelay.refwatch.di.UserIdFlow
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.DataEventBuffer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MobileGameViewModel @Inject constructor(
    // Hilt injects the following:
    val phonePinger: PhonePinger,
    application: Application,
    private val gameRepository: GameRepository,
    @UserIdFlow private val userIdFlow: Flow<String?>
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MobileGameViewModel"
    }

    private val dataClient by lazy { Wearable.getDataClient(application) }
    private val json =
        Json { ignoreUnknownKeys = true; encodeDefaults = true } // Kotlinx Serialization
    private val _currentUserId = MutableStateFlow<String?>(null) // To store current user ID

    // gamesList now directly uses the injected userIdFlow via flatMapLatest
    // OR it can use the internally collected _currentUserId.
    // Using _currentUserId which is collected from userIdFlow is fine.
    val gamesList: StateFlow<List<Game>> = _currentUserId
        .flatMapLatest { userId ->
            if (userId != null) {
                Log.d(TAG, "gamesList: User ID is $userId. Fetching games.")
                gameRepository.getGamesFlow(userId)
            } else {
                Log.d(TAG, "gamesList: User ID is null. Emitting empty game list.")
                flowOf(emptyList())
            }
        }
        .catch { e -> Log.e(TAG, "Error in gamesList flow: ${e.message}", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
/*
    // --- THIS IS THE CRUCIAL CHANGE ---
    // 1. We introduce a private, mutable StateFlow to act as the ViewModel's
    //    internal source of truth for the UI.
    private val _gamesList = MutableStateFlow<List<Game>>(emptyList())

    // 2. The public gamesList simply exposes the private one as a read-only StateFlow.
    val gamesList: StateFlow<List<Game>> = _gamesList.asStateFlow()
    // --- END OF CHANGE ---
*/

    // Listener for data changes from the watch
    private val dataChangedListener = DataClient.OnDataChangedListener { dataEvents: DataEventBuffer ->


        Log.d(TAG, "onDataChanged triggered from watch. Events: ${dataEvents.count}")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                val path = dataItem.uri.path
                Log.d(TAG, "DataItem changed: $path")
                if (path == WearSyncConstants.NEW_AD_HOC_GAME_PATH) {
                    try {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val newGameJson = dataMap.getString(WearSyncConstants.NEW_GAME_PAYLOAD_KEY)
                        if (newGameJson != null) {
                            val newGame = json.decodeFromString<Game>(newGameJson)
                            Log.i(TAG, "Received new ad-hoc game ${newGame.id} from watch. Saving to Firebase.")
                            // Call the existing method to save it to Firestore
                            addOrUpdateGame(newGame)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing new ad-hoc game from watch.", e)
                    }
                }
                else if (path != null && path.startsWith(WearSyncConstants.GAME_UPDATE_FROM_WATCH_PATH_PREFIX)) {
                    val gameId = path.substringAfterLast('/') // Extracts gameId from path
                    try {
                        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                        val updatedGameStateJson = dataMap.getString(WearSyncConstants.GAME_UPDATE_PAYLOAD_KEY)

                        if (updatedGameStateJson != null && gameId.isNotBlank()) {
                            Log.i(TAG, "Received game update from watch for gameId: $gameId. JSON: $updatedGameStateJson")
                            processGameStateUpdateFromWatch(gameId, updatedGameStateJson)
                        } else {
                            Log.w(TAG, "Received game update from watch with null JSON or blank gameId. Path: $path")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing game update DataItem from watch for path $path", e)
                    }
                } else if (path == WearSyncConstants.GAMES_LIST_PATH) {
                    // This ViewModel is the source of GAMES_LIST_PATH, so it typically wouldn't process changes
                    // to it from other sources unless you have a multi-master sync (which is complex).
                    // For now, we assume phone is master for the full list.
                    Log.d(TAG, "Ignoring change to GAMES_LIST_PATH as this VM is the sender.")
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                Log.d(TAG, "DataItem deleted: ${event.dataItem.uri.path}")
                // Handle if necessary, e.g., if watch can delete a game state item
            }
        }
        dataEvents.release() // IMPORTANT: Release the buffer!
    }

    init {
        Log.d(TAG, "MobileGameViewModel initializing...")

        viewModelScope.launch {
            // Collect the injected userIdFlow to update the internal _currentUserId
            userIdFlow.collect { newUserId ->
                Log.d(TAG, "userIdFlow collected in MobileGameViewModel. New userId: $newUserId")
                if (_currentUserId.value != newUserId) {
                    _currentUserId.value = newUserId
                    Log.i(TAG, "Internal _currentUserId updated to: $newUserId.")
                }
            }
        }

        viewModelScope.launch {
            gamesList.collect { games ->
                if (_currentUserId.value != null) { // Sync if there's a user
                    Log.d(
                        "MobileVM",
                        "Games list updated for user ${_currentUserId.value} (${games.size} games). Syncing to watch."
                    )
                    syncGamesToWatch(games)
                } else if (games.isEmpty() && _currentUserId.value == null) {
                    // If user becomes null (logged out) and gamesList is consequently empty,
                    // send empty list to clear watch.
                    Log.d(
                        "MobileVM",
                        "User logged out and games list is empty. Syncing empty list to watch."
                    )
                    syncGamesToWatch(emptyList())
                }
            }
        }
        dataClient.addListener(dataChangedListener)
        Log.d("MobileVM", "DataChangedListener added for watch updates.")
    }


    fun deleteGame(game: Game) {
        val userId = _currentUserId.value // Use internal state
        if (userId == null) {
            Log.w("MobileVM", "Cannot delete game ${game.id}: User not logged in.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            gameRepository.deleteGame(userId, game.id).onFailure {
                Log.e(TAG, "Failed to delete game: ${it.localizedMessage}")
            }
        }

    }

    private fun syncGamesToWatch(games: List<Game>) {
        val userIdForSync = _currentUserId.value // Use the ID for whom these games are relevant

        if (userIdForSync == null && games.isNotEmpty()) {
            Log.w(TAG, "syncGamesToWatch: Attempting to sync non-empty games list but _currentUserId is null. This is unusual. Skipping sync.")
            return
        }
        // If userIdForSync is null and games is empty, it means user logged out, send empty list.
        // If userIdForSync is not null, send the games (even if empty for that user).

        viewModelScope.launch(Dispatchers.IO) {
            // In phone's MobileGameViewModel, before sending
            val nodes = Wearable.getNodeClient(getApplication<Application>()).connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.e(TAG, "PHONE: No connected Wear OS nodes found. Data will be queued by DataClient but may not send immediately.")
            } else {
                Log.i(TAG, "PHONE: Connected nodes: ${nodes.joinToString { it.displayName }}")
            }
            try {
                val jsonString = json.encodeToString(games)
                Log.d(TAG, "syncGamesToWatch: Sending to watch. Path: ${WearSyncConstants.GAMES_LIST_PATH}, User: $userIdForSync, Games: ${games.size}")
                // ... (rest of PutDataMapRequest logic) ...
                val putDataMapReq = PutDataMapRequest.create(WearSyncConstants.GAMES_LIST_PATH)
                putDataMapReq.dataMap.putString(WearSyncConstants.GAME_SETTINGS_KEY, jsonString)
                putDataMapReq.dataMap.putLong("syncTimestamp", System.currentTimeMillis())
                putDataMapReq.setUrgent()
                val putDataReq = putDataMapReq.asPutDataRequest()
                dataClient.putDataItem(putDataReq).await()
                Log.i(TAG, "syncGamesToWatch: Games list for user $userIdForSync (${games.size}) sent successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "syncGamesToWatch: Failed for user $userIdForSync.", e)
            }
        }
    }

    private fun processGameStateUpdateFromWatch(gameIdFromPath: String, updatedGameStateJson: String) {

        val userId = _currentUserId.value
        if (userId == null) {
            Log.w(TAG, "processGameStateUpdateFromWatch: Cannot process. _currentUserId is null.")
            return
        }
        Log.d(TAG, "processGameStateUpdateFromWatch: Processing update for gameId (from path): $gameIdFromPath, User: $userId")
        Log.v(TAG, "processGameStateUpdateFromWatch: Received JSON: $updatedGameStateJson")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedGameFromWatch = json.decodeFromString<Game>(updatedGameStateJson)

                Log.i(TAG, "processGameStateUpdateFromWatch: Successfully deserialized game from watch. Parsed Game ID: ${updatedGameFromWatch.id}, Events count: ${updatedGameFromWatch.events.size}")
                Log.v(TAG, "processGameStateUpdateFromWatch: Deserialized events: ${updatedGameFromWatch.events.joinToString { it.displayString }}") // Assuming displayString or similar exists for logging

                if (updatedGameFromWatch.id != gameIdFromPath) {
                    Log.e(TAG, "processGameStateUpdateFromWatch: CRITICAL ID MISMATCH: Watch update path gameId '$gameIdFromPath' vs payload gameId '${updatedGameFromWatch.id}'. Using payload ID for saving.")
                    // Potentially log this as a more severe issue or analytics event.
                }
                // Always use the ID from the payload as the source of truth for the game object itself.
                // The path ID is for routing.
                val gameToSaveToFirebase = updatedGameFromWatch.copy(
                    id = updatedGameFromWatch.id, // Ensure we use the ID from the deserialized object
                    lastUpdated = System.currentTimeMillis()
                )

                Log.i(TAG, "processGameStateUpdateFromWatch: Attempting to save game to Firebase. Game ID: ${gameToSaveToFirebase.id}, User ID: $userId, Events count: ${gameToSaveToFirebase.events.size}")
                val result = gameRepository.addOrUpdateGame(userId, gameToSaveToFirebase)

                if (result.isSuccess) {

                    Log.i(TAG, "processGameStateUpdateFromWatch: Successfully saved game state update from watch for game ${gameToSaveToFirebase.id}")

                    // The gamesList flow will automatically update from Firestore, triggering a re-sync
                    // of the full (now updated) list back to the watch, ensuring consistency.
                /*    // --- OPTIMISTIC UI UPDATE ---
                    // Now that the save is successful, we manually update our local list
                    // so the UI updates instantly, without waiting for the Firestore listener.
                    val currentGames = _gamesList.value.toMutableList()
                    val index = currentGames.indexOfFirst { it.id == updatedGameFromWatch.id }
                    if (index != -1) {
                        currentGames[index] = updatedGameFromWatch
                    } else {
                        currentGames.add(0, updatedGameFromWatch)
                    }
                    _gamesList.value = currentGames
                    Log.d(TAG, "Local _gamesList state updated optimistically.")

                    // Emit the newly modified list. Your derived flows (upcomingGames, completedGames)
                    // will automatically recalculate, and the UI will recompose instantly.
                    _gamesList.value = currentGames
                    Log.d(TAG, "Local UI state updated optimistically for game ${updatedGameFromWatch.id}.")*/
                } else {
                    Log.e(TAG, "Failed to save game state update from watch for game ${gameToSaveToFirebase.id}", result.exceptionOrNull())
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error deserializing or processing game state update from watch. GameId from path: $gameIdFromPath. JSON: $updatedGameStateJson", e)
            }
        }
    }

    private fun listenForUpdatesFromWatch() {
        val userId = _currentUserId.value // Use internal state
        if (userId == null) {
            Log.w(
                "MobileVM",
                "Cannot save single game: User not logged in (currentUserId is null)."
            )
            return
        }
        dataClient.addListener { dataEvents ->
            viewModelScope.launch(Dispatchers.IO) {
                dataEvents.forEach { event ->
                    if (event.type == DataEvent.TYPE_CHANGED) {
                        val dataItem = event.dataItem
                        val itemUri: Uri = dataItem.uri // dataItem.uri IS an android.net.Uri
                        val path = itemUri.path // path is a String?
                        if (path?.startsWith(WearSyncConstants.GAME_UPDATE_FROM_WATCH_PATH_PREFIX) == true) {
                            val gameId = itemUri.lastPathSegment
                            if (gameId != null) {
                                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                                val gameUpdateJson =
                                    dataMap.getString(WearSyncConstants.GAME_UPDATE_PAYLOAD_KEY)
                                if (gameUpdateJson != null) {
                                    try {
                                        // Watch could send the full Game object or just GameEvents
                                        // Option A: Watch sends the full updated Game object
                                        val updatedGameFromWatch =
                                            json.decodeFromString<Game>(gameUpdateJson)
                                        // val updatedGameFromWatch = gson.fromJson(gameUpdateJson, Game::class.java)

                                        Log.d(
                                            TAG,
                                            "Received full game update for $gameId from watch."
                                        )
                                        // Merge intelligently if needed, or overwrite if watch state is master for those fields
                                        gameRepository.addOrUpdateGame(
                                            userId,
                                            updatedGameFromWatch.copy(lastUpdated = System.currentTimeMillis())
                                        )
                                            .onSuccess {
                                                Log.i(
                                                    TAG,
                                                    "FS Updated game $gameId from watch."
                                                )
                                            }
                                            .onFailure { e ->
                                                Log.e(
                                                    TAG,
                                                    "FS FAILED update game $gameId from watch.",
                                                    e
                                                )
                                            }

                                        // Option B: Watch sends a new GameEventLog
                                        /*
                                        val newEventFromWatch = json.decodeFromString<GameEvent>(gameUpdateJson) // Assuming GameEvent is serializable
                                        Log.d(TAG, "Received event for game $gameId from watch: ${newEventFromWatch.displayString}")
                                        (authViewModel.authState.value as? AuthState.Authenticated)?.user?.uid?.let { userId ->
                                            val currentGame = gameRepository.getGameById(userId, gameId) // Fetch current game
                                            if (currentGame != null) {
                                                val updatedEvents = currentGame.events.toMutableList().apply { add(newEventFromWatch) }
                                                // Update score based on event if it's a goal
                                                var updatedHomeScore = currentGame.homeScore
                                                var updatedAwayScore = currentGame.awayScore
                                                if (newEventFromWatch is GameEvent.GoalScoredEvent) {
                                                    updatedHomeScore = newEventFromWatch.homeScoreAtTime
                                                    updatedAwayScore = newEventFromWatch.awayScoreAtTime
                                                }
                                                val gameToSave = currentGame.copy(
                                                    events = updatedEvents,
                                                    homeScore = updatedHomeScore,
                                                    awayScore = updatedAwayScore,
                                                    lastUpdated = System.currentTimeMillis()
                                                    // Potentially update currentPhase, displayedTimeMillis, etc. from the event or a richer payload
                                                )
                                                gameRepository.addOrUpdateGame(userId, gameToSave)
                                                    .onSuccess { Log.i(TAG, "FS Added event to game $gameId from watch.") }
                                                    .onFailure { e -> Log.e(TAG, "FS FAILED to add event to game $gameId from watch.", e) }
                                            } else {
                                                Log.w(TAG, "Game $gameId not found in Firestore to add event from watch.")
                                            }
                                        }
                                        */

                                    } catch (e: Exception) {
                                        Log.e(
                                            TAG,
                                            "Error deserializing game update from watch for game $gameId",
                                            e
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Log.d(TAG, "Registered DataClient listener for updates from watch.")
    }

    // Add this convenience function
    fun addOrUpdateGame(game: Game) {
        addOrUpdateGames(listOf(game))
    }

    // When importing ICS, use the new constructor in your Game class
    fun addOrUpdateGames(games: List<Game>) {
        val userId = _currentUserId.value
        if (userId == null) {
            Log.w(TAG, "Cannot save games: User not logged in.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            games.forEach { game ->
                val gameWithTimestamp = game.copy(lastUpdated = System.currentTimeMillis())
                gameRepository.addOrUpdateGame(userId, gameWithTimestamp).onFailure { e ->
                    Log.e(TAG, "Failed to save game ${game.id}: ${e.message}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataClient.removeListener(dataChangedListener)
        Log.d("MobileVM", "DataChangedListener removed.")
    }
}
