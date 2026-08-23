package com.databelay.refwatch.data // Or your package

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.databelay.refwatch.common.AppJsonConfiguration
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GameStatus
import com.databelay.refwatch.common.IMobileGameViewModel
import com.databelay.refwatch.common.WearSyncConstants
import com.databelay.refwatch.di.UserIdFlow
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import androidx.compose.material3.TooltipState


@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MobileGameViewModel @Inject constructor(
    // Hilt injects the following:
    application: Application,
    private val gameRepository: GameStorageMobile,
    @UserIdFlow private val userIdFlow: Flow<String?>,
//    val onboardingViewModel: OnboardingViewModel // <-- ADD THIS LINE
) : AndroidViewModel(application), IMobileGameViewModel {

    companion object {
        private const val TAG = "MobileGameViewModel"
        private const val WATCH_GAME_PAYLOAD_KEY = WearSyncConstants.KEY_GAME_UPDATE
        private const val SYNC_TO_WATCH_DELAY_MS = 3000L // 3 seconds, adjust as needed
    }
    // --- Onboarding Tooltip State ---
    @OptIn(ExperimentalMaterial3Api::class)

    // --- Tab State Management ---
    private val _selectedTab = MutableStateFlow(GameStatus.SCHEDULED) // Default to SCHEDULED
    val selectedTab: StateFlow<GameStatus> = _selectedTab.asStateFlow()
    private val _scrollToTopGamesListEvent =
        MutableSharedFlow<Unit>(replay = 0) // Simpler: just Unit event
    val scrollToTopGamesListEvent: SharedFlow<Unit> = _scrollToTopGamesListEvent.asSharedFlow()

    // Job to manage the delayed task of syncing to the watch
    private var syncToWatchJob: Job? = null

    // You'll need a way to know if the watch is connected.
    // This could be a Flow from a service that monitors wearable capabilities.
    // For this example, let's assume you have a way to call a function when connection status changes.
    // Replace this with your actual watch connectivity detection mechanism.
    private val _watchConnectedState = MutableStateFlow(true) // Example state
    val watchConnectedState: StateFlow<Boolean> = _watchConnectedState.asStateFlow()

    private val dataClient by lazy { Wearable.getDataClient(application) }

    private val _currentUserId = MutableStateFlow<String?>(null) // To store current user ID

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }
    // gamesList now directly uses the injected userIdFlow via flatMapLatest
    // OR it can use the internally collected _currentUserId.
    // Using _currentUserId which is collected from userIdFlow is fine.
    override val gamesList: StateFlow<List<Game>> = _currentUserId
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


    // Listener for data changes from the watch
    private val dataChangedListener = DataClient.OnDataChangedListener { dataEvents: DataEventBuffer ->
        Log.d(TAG, "onDataChanged triggered from watch. Events: ${dataEvents.count}")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                val path = dataItem.uri.path
                Log.d(TAG, "DataItem changed: $path")
                if (path != null && path.startsWith(WearSyncConstants.PATH_GAME_UPDATE)) {
                    val gameId = path.substringAfterLast('/') // Extracts gameId from path
                    if (gameId.isNotBlank()) {
                        processGameDataFromWatch(DataMapItem.fromDataItem(dataItem), gameId)
                    } else {
                        Log.w(TAG, "Received game update from watch with blank gameId in path: $path")
                    }
                } else if (path == WearSyncConstants.PATH_GAMES_LIST) {
                    Log.d(TAG, "Ignoring change to GAMES_LIST_PATH as this VM is the sender.")
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                Log.d(TAG, "DataItem deleted: ${event.dataItem.uri.path}")
                // Handle if necessary
            }
        }
        dataEvents.release() // IMPORTANT: Release the buffer!
    }

    init {
        Log.d(TAG, "MobileGameViewModel initializing...")
        try {
            dataClient.addListener(dataChangedListener)
            Log.d("MobileVM", "DataChangedListener added for watch updates.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add DataClient listener. Wearable API might be unavailable.", e)
            if (e is com.google.android.gms.common.api.ApiException && e.statusCode == 17) {
                _watchConnectedState.value = false
            }
        }

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

        // Collect gamesList to sync to watch (this part syncs whenever gamesList changes AFTER the initial delay)
        viewModelScope.launch {
            gamesList
                .collectLatest { currentGamesList ->
                    val userId = _currentUserId.value
                    if (userId != null && _watchConnectedState.value) { // Only sync if watch is considered connected
                        Log.d(TAG, "Games list changed for user $userId (${currentGamesList.size} games). Syncing to watch.")
                        syncGamesToWatchInternal(currentGamesList) // Changed to internal to avoid immediate call
                    } else if (userId == null && currentGamesList.isEmpty() && _watchConnectedState.value) {
                        Log.d(TAG, "User logged out, games list is empty. Syncing empty list to clear watch.")
                        syncGamesToWatchInternal(emptyList())
                    } else if (!_watchConnectedState.value) {
                        Log.d(TAG, "Games list changed, but watch is not connected. Sync deferred.")
                    }
                }
        }

        // Example: Reacting to watch connection changes
        // Replace this with your actual connection status observation logic
        viewModelScope.launch {
            watchConnectedState.collectLatest { isConnected ->
                if (isConnected) {
                    Log.i(TAG, "Watch connection established. Scheduling full sync TO watch after a delay.")
                    scheduleFullSyncToWatchWithDelay()
                } else {
                    Log.i(TAG, "Watch disconnected. Cancelling any pending sync TO watch.")
                    syncToWatchJob?.cancel()
                }
            }
        }
    }

    // Call this method from your service or mechanism that detects watch connection status
    fun onWatchConnectionChanged(isConnected: Boolean) {
        _watchConnectedState.value = isConnected
    }

    private fun scheduleFullSyncToWatchWithDelay() {
        syncToWatchJob?.cancel() // Cancel any existing delayed sync
        syncToWatchJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Waiting ${SYNC_TO_WATCH_DELAY_MS}ms before syncing all games to watch...")
            delay(SYNC_TO_WATCH_DELAY_MS)

            // Check connection again after delay, in case it disconnected during the delay
            if (!_watchConnectedState.value) {
                Log.i(TAG, "Watch disconnected during delay. Aborting sync TO watch.")
                return@launch
            }

            val userId = _currentUserId.value
            if (userId != null) {
                // Fetch the most current list of games to send.
                // gamesList.value might be stale if it hasn't recomposed/recollected yet.
                // It's safer to query the repository or use a first() on the flow if appropriate.
                // For simplicity, using gamesList.value which is updated by its own collector.
                val gamesToSend = gamesList.value
                Log.i(TAG, "Delay finished. Syncing ${gamesToSend.size} games to watch for user $userId.")
                syncGamesToWatchInternal(gamesToSend)
            } else {
                Log.i(TAG, "Delay finished, but user is null. Syncing empty list to watch.")
                syncGamesToWatchInternal(emptyList())
            }
        }
    }
    private fun processGameDataFromWatch(dataMapItem: DataMapItem, pathGameId: String?) {
        val userId = _currentUserId.value
        if (userId == null) {
            Log.w(TAG, "processGameDataFromWatch: Cannot process. _currentUserId is null. Path: ${dataMapItem.uri.path}")
            return
        }

        try {
            val dataMap = dataMapItem.dataMap
            // Use the standardized key.
            var gameJson = dataMap.getString(WearSyncConstants.KEY_GAME_UPDATE)
            if (gameJson != null) {
                val gameFromWatch = AppJsonConfiguration.decodeFromString<Game>(gameJson)
                Log.i(TAG, "processGameDataFromWatch: Received game ${gameFromWatch.id} from watch (pathGameId: $pathGameId). User: $userId. Saving to Firebase.")
//                Log.v(TAG, "processGameDataFromWatch: Received JSON: $gameJson")
//                Log.v(TAG, "processGameDataFromWatch: Deserialized events: ${gameFromWatch.events.joinToString { it.displayString }}")

                if (pathGameId != null && gameFromWatch.id != pathGameId) {
                    Log.e(
                        TAG,
                        "processGameDataFromWatch: ID MISMATCH! Path gameId '$pathGameId' vs payload gameId '${gameFromWatch.id}'. Using payload ID for saving."
                    )
                }
                // The game object (gameFromWatch) is the source of truth for content.
                // The addOrUpdateGame function should handle if it's new or an update.
                addOrUpdateGame(gameFromWatch) // This function now handles both new and updates
            } else {
                Log.w(TAG, "processGameDataFromWatch: Game JSON payload was null in DataMap for path ${dataMapItem.uri.path} using key ${WearSyncConstants.KEY_GAME_UPDATE}.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "processGameDataFromWatch: Error processing DataItem from watch. Path: ${dataMapItem.uri.path}", e)
        }
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

    private fun syncGamesToWatchInternal(games: List<Game>) {
        val userIdForSync = _currentUserId.value // Use the ID for whom these games are relevant

        if (userIdForSync == null && games.isNotEmpty()) {
            Log.w(TAG, "syncGamesToWatch: Attempting to sync non-empty games list but _currentUserId is null. This is unusual. Skipping sync.")
            return
        }
        // If userIdForSync is null and games is empty, it means user logged out, send empty list.
        // If userIdForSync is not null, send the games (even if empty for that user).

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // In phone's MobileGameViewModel, before sending
                val nodes = Wearable.getNodeClient(getApplication<Application>()).connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.e(TAG, "PHONE: No connected Wear OS nodes found. Data will be queued by DataClient but may not send immediately.")
                } else {
                    Log.i(TAG, "PHONE: Connected nodes: ${nodes.joinToString { it.displayName }}")
                }

                val jsonString = AppJsonConfiguration.encodeToString(games)
                Log.d(TAG, "syncGamesToWatch: Sending to watch. Path: ${WearSyncConstants.PATH_GAMES_LIST}, User: $userIdForSync, Games: ${games.size}")
                // ... (rest of PutDataMapRequest logic) ...

                val putDataMapReq = PutDataMapRequest.create(WearSyncConstants.PATH_GAMES_LIST)
                getCurrentUserId()?.let { putDataMapReq.dataMap.putString(WearSyncConstants.KEY_USER_ID, it) } // Add the user ID
                putDataMapReq.dataMap.putString(WearSyncConstants.KEY_GAMES_JSON, jsonString)
                putDataMapReq.dataMap.putLong("syncTimestamp", System.currentTimeMillis())
                putDataMapReq.setUrgent()
                val putDataReq = putDataMapReq.asPutDataRequest()
                dataClient.putDataItem(putDataReq).await()
                Log.i(TAG, "syncGamesToWatch: Games list for user $userIdForSync (${games.size}) sent successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "syncGamesToWatch: Failed for user $userIdForSync.", e)
                // If Wearable API is not available on this device (ApiException 17), 
                // mark watch as disconnected to prevent further attempts.
                if (e is com.google.android.gms.common.api.ApiException && e.statusCode == 17) {
                    _watchConnectedState.value = false
                }
            }
        }
    }

    fun selectTab(tab: GameStatus) {
        if (tab == GameStatus.SCHEDULED || tab == GameStatus.COMPLETED) { // Ensure valid tabs
            _selectedTab.value = tab
            Log.d(TAG, "Selected tab changed to: $tab")
        } else {
            Log.w(TAG, "Attempted to select invalid tab: $tab")
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
                val updatedGameFromWatch = AppJsonConfiguration.decodeFromString<Game>(updatedGameStateJson)

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
        try {
            dataClient.addListener { dataEvents ->
                viewModelScope.launch(Dispatchers.IO) {
                    dataEvents.forEach { event ->
                        if (event.type == DataEvent.TYPE_CHANGED) {
                            val dataItem = event.dataItem
                            val itemUri: Uri = dataItem.uri // dataItem.uri IS an android.net.Uri
                            val path = itemUri.path // path is a String?
                            if (path?.startsWith(WearSyncConstants.PATH_GAME_UPDATE) == true) {
                                val gameId = itemUri.lastPathSegment
                                if (gameId != null) {
                                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                                    val gameUpdateJson =
                                        dataMap.getString(WearSyncConstants.KEY_GAME_UPDATE)
                                    if (gameUpdateJson != null) {
                                        try {
                                            // Watch could send the full Game object or just GameEvents
                                            // Option A: Watch sends the full updated Game object
                                            val updatedGameFromWatch =
                                                AppJsonConfiguration.decodeFromString<Game>(gameUpdateJson)
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register DataClient listener in listenForUpdatesFromWatch.", e)
        }
    }

    // Add this convenience function
    fun addOrUpdateGame(game: Game) {
        addOrUpdateGames(listOf(game))
    }

    // Simplified: Call this function from anywhere a new game is added
    // and you want the list on mobile to scroll to the top.
    private fun triggerScrollToTopGamesList() {
        viewModelScope.launch {
            // Emit the event. The list should ideally have updated or be updating shortly.
            _scrollToTopGamesListEvent.emit(Unit)
            Log.d(TAG, "ScrollToTopGamesList event emitted.")
        }
    }

    // When importing ICS, use the new constructor in your Game class
    fun addOrUpdateGames(games: List<Game>) { // Assuming this is called when games are added/imported
        val userId = _currentUserId.value
        if (userId == null) {
            Log.w(TAG, "Cannot save games: User not logged in.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            var newItemsWereAdded = false // Simple flag
            games.forEach { game ->
                // Basic check: if the game isn't already in the current list by ID, assume it's new for scroll purposes.
                // This is a simplification; for updates, you might not want to scroll.
                // For this example, let's assume any game passed here for "addOrUpdate"
                // when it's part of an "add new" flow should trigger a scroll.
                // A more robust check might involve comparing with the list *before* this operation.
                val gameWithTimestamp = game.copy(lastUpdated = System.currentTimeMillis())
                val result = gameRepository.addOrUpdateGame(userId, gameWithTimestamp)
                if (result.isSuccess) {
                    newItemsWereAdded = true
                } else {
                    Log.e(TAG, "Failed to save game ${game.id}: ${result.exceptionOrNull()?.message}")
                }
            }

            if (newItemsWereAdded) {
                // The gamesList StateFlow will update due to Firestore listener.
                // After data is saved, trigger the scroll.
                triggerScrollToTopGamesList()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            dataClient.removeListener(dataChangedListener)
            Log.d("MobileVM", "DataChangedListener removed.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove DataClient listener.", e)
        }
    }

}
