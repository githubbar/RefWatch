package com.databelay.refwatch.games // Or your package
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.databelay.refwatch.auth.AuthState
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString // For Kotlinx Serialization
import kotlinx.serialization.json.Json // For Kotlinx Serialization

import com.databelay.refwatch.common.Game // From common module
import com.databelay.refwatch.games.GameRepository // Hilt will inject this
import com.databelay.refwatch.auth.AuthViewModel // Your AuthViewModel
import com.databelay.refwatch.common.SimpleIcsEvent
import com.databelay.refwatch.common.WearSyncConstants
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.DataClient
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MobileGameViewModel @Inject constructor(
    application: Application, // Hilt provides this
    private val gameRepository: GameRepository, // Hilt injects this from RepositoryModule
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MobileGameViewModel"
    }
    private val firestore = FirebaseFirestore.getInstance()
    private val dataClient by lazy { Wearable.getDataClient(application) }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true } // Kotlinx Serialization
    private val _currentUserId = MutableStateFlow<String?>(null) // To store current user ID

    // Observe games based on the _currentUserId
    @OptIn(ExperimentalCoroutinesApi::class)
    val gamesList: StateFlow<List<Game>> = _currentUserId
        .flatMapLatest { userId ->
            if (userId != null) {
                Log.d("MobileVM", "User ID set: $userId. Fetching games.")
                gameRepository.getGamesFlow(userId)
            } else {
                Log.d("MobileVM", "No user ID. Emitting empty game list.")
                flowOf(emptyList())
            }
        }
        .catch { e ->
            Log.e("MobileVM", "Error in gamesList flow: ${e.message}", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val dataChangedListener = DataClient.OnDataChangedListener { /* ... as before ... */ }

    init {
        // No longer need to collect gamesList here to trigger sync based on authViewModel directly.
        // The Activity will call onUserChanged, which updates _currentUserId,
        // which then triggers gamesList collection.
        // Syncing to watch will now be tied to gamesList changes based on _currentUserId.

        viewModelScope.launch {
            gamesList.collect { games ->
                if (_currentUserId.value != null) { // Sync if there's a user
                    Log.d("MobileVM", "Games list updated for user ${_currentUserId.value} (${games.size} games). Syncing to watch.")
                    syncGamesToWatch(games)
                } else if (games.isEmpty() && _currentUserId.value == null) {
                    // If user becomes null (logged out) and gamesList is consequently empty,
                    // send empty list to clear watch.
                    Log.d("MobileVM", "User logged out and games list is empty. Syncing empty list to watch.")
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
        // ... (logging as before) ...
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Using Kotlinx Serialization
                val jsonString = json.encodeToString(games)
                // val jsonString = gson.toJson(games) // If using Gson

                val putDataMapReq = PutDataMapRequest.create(WearSyncConstants.GAMES_LIST_PATH)
                putDataMapReq.dataMap.putString(WearSyncConstants.GAME_SETTINGS_KEY, jsonString)
                putDataMapReq.setUrgent()

                val putDataReq = putDataMapReq.asPutDataRequest()
                dataClient.putDataItem(putDataReq).await()
                Log.i(TAG, "Successfully sent/updated ${games.size} games to watch via DataClient.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send game list to watch.", e)
            }
        }
    }

    private fun listenForUpdatesFromWatch() {
        val userId = _currentUserId.value // Use internal state
        if (userId == null) {
            Log.w("MobileVM", "Cannot save single game: User not logged in (currentUserId is null).")
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
                                val gameUpdateJson = dataMap.getString(WearSyncConstants.GAME_UPDATE_PAYLOAD_KEY)
                                if (gameUpdateJson != null) {
                                    try {
                                        // Watch could send the full Game object or just GameEvents
                                        // Option A: Watch sends the full updated Game object
                                        val updatedGameFromWatch = json.decodeFromString<Game>(gameUpdateJson)
                                        // val updatedGameFromWatch = gson.fromJson(gameUpdateJson, Game::class.java)

                                        Log.d(TAG, "Received full game update for $gameId from watch.")
                                        // Merge intelligently if needed, or overwrite if watch state is master for those fields
                                        gameRepository.addOrUpdateGame(userId, updatedGameFromWatch.copy(lastUpdated = System.currentTimeMillis()))
                                            .onSuccess { Log.i(TAG, "FS Updated game $gameId from watch.") }
                                            .onFailure { e -> Log.e(TAG, "FS FAILED update game $gameId from watch.", e) }

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
                                        Log.e(TAG, "Error deserializing game update from watch for game $gameId", e)
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

    fun addOrUpdateGame(game: Game) {
        val userId = _currentUserId.value // Use internal state
        if (userId == null) {
            Log.w("MobileVM", "Cannot save single game: User not logged in (currentUserId is null).")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val gameWithTimestamp = game.copy(lastUpdated = System.currentTimeMillis())
            gameRepository.addOrUpdateGame(userId, gameWithTimestamp).onFailure {
                Log.e(TAG, "Failed to add/update game: ${it.localizedMessage}")
                // Handle error (e.g., show a toast to UI)
            }
            // Firestore listener will pick up the change and trigger syncGamesToWatch
        }
    }

    // When importing ICS, use the new constructor in your Game class
    fun addOrUpdateGames(games: List<Game>) { // Assuming SimpleIcsEvent is your parser's output
        val userId = _currentUserId.value // Use internal state
        if (userId == null) {
            Log.w("MobileVM", "Cannot save single game: User not logged in (currentUserId is null).")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            games.forEach { game ->
                gameRepository.addOrUpdateGame(userId, game)
                    .onFailure { e -> Log.e(TAG, "Failed to save imported game ${game.summary}: ${e.message}")}
            }
            Log.i(TAG, "Finished importing and saving ${games.size} games from ICS.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataClient.removeListener(dataChangedListener)
        Log.d("MobileVM", "DataChangedListener removed.")
    }

    // New method to be called from Activity/Fragment
    fun onUserChanged(firebaseUser: FirebaseUser?) {
        val newUserId = firebaseUser?.uid
        if (_currentUserId.value != newUserId) {
            Log.d("MobileVM", "User changed. Old: ${_currentUserId.value}, New: $newUserId")
            _currentUserId.value = newUserId
            // If newUserId is null, gamesList will emit emptyList, and init's collect will send empty to watch
        }
    }}
