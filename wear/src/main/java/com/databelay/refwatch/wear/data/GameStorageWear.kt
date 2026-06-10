package com.databelay.refwatch.wear.data

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.size
import androidx.core.content.edit
// import androidx.core.content.edit // Not strictly needed if using withContext for prefs
import com.databelay.refwatch.common.AppJsonConfiguration
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GameEvent
import com.databelay.refwatch.common.jsonObjectToMap
import com.databelay.refwatch.common.mapToJsonObject
import com.databelay.refwatch.common.parseGameEventsFromDocument
import com.databelay.refwatch.common.toFirestoreMap
import com.databelay.refwatch.wear.auth.WatchAuthManager
import com.databelay.refwatch.wear.util.ConnectivityObserver // Import ConnectivityObserver
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
// import com.google.firebase.firestore.ktx.toObject // Removed
// import com.google.firebase.firestore.ktx.toObjects // Removed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
// import kotlinx.serialization.json.Json // No longer needed directly if AppJsonConfiguration is Json
import javax.inject.Inject
import javax.inject.Singleton

// Re-defining or importing DataFetchStatus. Assuming it's the same as before.
enum class DataFetchStatus {
    INITIAL,
    FETCHING,
    SUCCESS,
    NO_USER_AUTHENTICATED,
    LOADED_FROM_CACHE,
    NO_DATA_AVAILABLE,
    ERROR_NETWORK,
    ERROR_FIREBASE_OPERATION,
    ERROR_UNKNOWN
}

@Singleton
class GameStorageWear @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchAuthManager: WatchAuthManager,
    private val firestore: FirebaseFirestore,
    private val connectivityObserver: ConnectivityObserver // Inject ConnectivityObserver
) {
    private val tag = "GameStorageWear"
    private val prefsName = "RefWatchWearPrefs"
    private val gamesCacheKeyPrefix = "games_cache_for_user_"
    private val pendingSyncGamesKeyPrefix = "pending_sync_games_for_user_"

    private val storageScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _gamesListFlow = MutableStateFlow<List<Game>>(emptyList())
    val gamesListFlow: StateFlow<List<Game>> = _gamesListFlow.asStateFlow()

    private val _dataFetchStatusFlow = MutableStateFlow(DataFetchStatus.INITIAL)
    val dataFetchStatusFlow: StateFlow<DataFetchStatus> = _dataFetchStatusFlow.asStateFlow()

    private val _networkStatusFlow = MutableStateFlow(ConnectivityObserver.Status.UNINITIALIZED)
    val networkStatusFlow: StateFlow<ConnectivityObserver.Status> = _networkStatusFlow.asStateFlow()

    private var currentUserId: String? = null
    private var firestoreListenerRegistration: ListenerRegistration? = null

    init {
        Log.d(tag, "Initializing GameStorageWear.")

        // Observe network status
        connectivityObserver.observe()
            .onEach { status ->
                Log.i(tag, "Network status changed: $status")
                _networkStatusFlow.value = status
            }
            .launchIn(storageScope) // Observe in storageScope

        // Combine User ID and Network Status to react to changes
        watchAuthManager.currentWatchUserId
            .combine(_networkStatusFlow) { userId, networkStatus ->
                Pair(userId, networkStatus)
            }
            .distinctUntilChanged() // Only react if userId or networkStatus actually changes
            .onEach { (userId, networkStatus) ->
                Log.d(tag, "User ID or Network status change detected. User: $userId, Network: $networkStatus")
                if (currentUserId != userId) {
                    val oldUserId = currentUserId
                    currentUserId = userId
                    onUserChanged(newUserId = userId, oldUserId = oldUserId, isOnline = networkStatus == ConnectivityObserver.Status.AVAILABLE)
                } else if (userId != null && networkStatus == ConnectivityObserver.Status.AVAILABLE) {
                    // User is the same, but network might have come online
                    Log.i(tag, "Network became available for user $userId. Triggering pending sync.")
                    syncPendingGames(userId)
                    // Re-attach listener if it was detached due to prior network unavailability
                    if (firestoreListenerRegistration == null) {
                         Log.i(tag, "Network reconnected, re-attaching Firestore listener for user $userId.")
                         attachFirestoreListener(userId)
                    }
                } else if (userId != null) { // Simplified condition: user same, network not available
                    Log.w(tag, "Network became unavailable for user $userId. Detaching Firestore listener.")
                    detachFirestoreListener() // Detach listener when offline to prevent errors/retries
                     _dataFetchStatusFlow.value = DataFetchStatus.ERROR_NETWORK // Reflect that we are offline
                }
            }
            .launchIn(storageScope) // Observe in storageScope for long-lived operations
    }

    private fun onUserChanged(newUserId: String?, oldUserId: String?, isOnline: Boolean) {
        storageScope.launch { // Ensure this runs on the IO dispatcher
            detachFirestoreListener()
            if (oldUserId != null) {
                // Optionally clear cache for old user
            }

            if (newUserId.isNullOrBlank()) {
                _gamesListFlow.value = emptyList()
                _dataFetchStatusFlow.value = DataFetchStatus.NO_USER_AUTHENTICATED
                Log.i(tag, "No user authenticated. Cleared game list and detached listener.")
            } else {
                _dataFetchStatusFlow.value = DataFetchStatus.FETCHING
                loadGamesFromCache(newUserId) // Load from cache first
                if (isOnline) {
                    attachFirestoreListener(newUserId)
                    syncPendingGames(newUserId)
                } else {
                    Log.w(tag, "User changed to $newUserId, but device is offline. Will not attach listener or sync yet.")
                    _dataFetchStatusFlow.value = DataFetchStatus.LOADED_FROM_CACHE // Or ERROR_NETWORK
                }
            }
        }
    }

    private fun attachFirestoreListener(userId: String) {
        // ... (existing setup and error handling for listener) ...
        Log.d(tag, "Attempting to attach Firestore listener for user: $userId (Wear)")


        val gamesCollection = firestore.collection("users").document(userId).collection("games")
        firestoreListenerRegistration = gamesCollection.addSnapshotListener { snapshots, e ->
            if (snapshots == null) {
                Log.w(tag, "Firestore snapshots were null for user $userId. Not updating game list.")
                _dataFetchStatusFlow.value = DataFetchStatus.NO_DATA_AVAILABLE 
                return@addSnapshotListener
            }

            Log.d(tag, "Firestore listener (Wear) received ${snapshots.size()} documents for user $userId.")

            storageScope.launch {
                val gamesFromFirestore = snapshots.documents.mapNotNull { doc ->
                    try {
                        val gameBase = doc.toObject(Game::class.java) 
                        if (gameBase == null) return@mapNotNull null
                        
                        val parsedEvents = parseGameEventsFromDocument(doc)
                        gameBase.copy(id = doc.id, events = parsedEvents)
                    } catch (docEx: Exception) {
                        Log.e(tag, "Listener (Wear): Error processing document ${doc.id}", docEx)
                        null
                    }
                }

                if (_gamesListFlow.value != gamesFromFirestore) {
                    _gamesListFlow.value = gamesFromFirestore
                    saveGamesToCache(gamesFromFirestore, userId)
                }
                
                _dataFetchStatusFlow.value = if (gamesFromFirestore.isEmpty()) DataFetchStatus.NO_DATA_AVAILABLE else DataFetchStatus.SUCCESS
            }
        }
        Log.i(tag, "Firestore listener attached (Wear) for user: $userId")
    }

    private fun detachFirestoreListener() {
        if (firestoreListenerRegistration != null) {
            firestoreListenerRegistration?.remove()
            firestoreListenerRegistration = null
            Log.d(tag, "Firestore listener detached.")
        }
    }

    private suspend fun loadGamesFromCache(userId: String) {
        if (userId.isBlank()) return
        withContext(Dispatchers.IO) { // Ensure runs on IO
            try {
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                val gamesKey = "$gamesCacheKeyPrefix$userId"
                val jsonString = prefs.getString(gamesKey, null)
                val cachedGames = if (jsonString != null) {
                    AppJsonConfiguration.decodeFromString<List<Game>>(jsonString)
                } else {
                    emptyList()
                }
                _gamesListFlow.value = cachedGames
                if (_dataFetchStatusFlow.value == DataFetchStatus.FETCHING || _dataFetchStatusFlow.value == DataFetchStatus.INITIAL || _networkStatusFlow.value != ConnectivityObserver.Status.AVAILABLE) {
                     _dataFetchStatusFlow.value = if (cachedGames.isNotEmpty()) DataFetchStatus.LOADED_FROM_CACHE else DataFetchStatus.NO_DATA_AVAILABLE
                }
                Log.i(tag, "Loaded ${cachedGames.size} games from cache for user $userId. Status: ${_dataFetchStatusFlow.value}")
            } catch (e: Exception) {
                Log.e(tag, "Failed to load games from cache for user $userId", e)
                _gamesListFlow.value = emptyList()
                _dataFetchStatusFlow.value = DataFetchStatus.ERROR_UNKNOWN
            }
        }
    }

    private suspend fun saveGamesToCache(games: List<Game>, userId: String) {
        if (userId.isBlank()) return
        withContext(Dispatchers.IO) { // Ensure runs on IO
            try {
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                val gamesKey = "$gamesCacheKeyPrefix$userId"
                val jsonString = AppJsonConfiguration.encodeToString(games)
                // Use androidx.core.content.edit for SharedPreferences
                prefs.edit(commit = true) { putString(gamesKey, jsonString) }
                Log.d(tag, "Saved ${games.size} games to cache for user $userId.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to save games to cache for user $userId", e)
            }
        }
    }

    suspend fun addOrUpdateGame(game: Game): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = currentUserId
        Log.d(tag, "addOrUpdateGame (Wear): User: $userId, Game ID: ${game.id}, Events in Game object: ${game.events.size}")
        if (userId.isNullOrBlank()) {
            Log.w(tag, "No authenticated user. Cannot save/update game.")
            _dataFetchStatusFlow.value = DataFetchStatus.NO_USER_AUTHENTICATED
            return@withContext Result.failure(IllegalArgumentException("User ID is null or blank"))
        }

        val gameWithTimestamp = game.copy(lastUpdated = System.currentTimeMillis()) // Use 'lastUpdated'

        if (_networkStatusFlow.value != ConnectivityObserver.Status.AVAILABLE) {
            Log.w(tag, "Network unavailable. Saving game ${gameWithTimestamp.id} as pending sync for user $userId.")
            saveGameToPendingSync(gameWithTimestamp, userId)
            _dataFetchStatusFlow.value = DataFetchStatus.ERROR_NETWORK // Reflect that data is local due to network
            return@withContext Result.failure(IllegalStateException("Network unavailable"))
        }

        if (game.id.isBlank()) {
            Log.e(tag, "addOrUpdateGame (Wear): game.id is blank for user $userId.")
            return@withContext Result.failure(IllegalArgumentException("Game ID cannot be blank for addOrUpdateGame"))
        }
        try {
            val gameDocumentRef = firestore.collection("users")
                .document(userId!!) // userId is checked not to be blank above
                .collection("games")
                .document(game.id)

            // Use the extension function
            val gameDataForFirestore = game.toFirestoreMap() // Pass AppJsonConfiguration if needed

            Log.d(tag, "addOrUpdateGame (Wear): Saving game ${game.id} for user $userId with ${(gameDataForFirestore["events"] as? List<*>)?.size ?: 0} events.")
            Log.v(tag, "addOrUpdateGame (Wear): Data being sent to Firestore: $gameDataForFirestore")

            gameDocumentRef.set(gameDataForFirestore).await()
            Log.d(tag, "Game ${game.id} saved/updated successfully to Firestore for user $userId.")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(tag, "Error saving/updating game ${game.id} for user $userId to Firestore", e)
            Result.failure(e)
        }
    }

    private suspend fun saveGameToPendingSync(game: Game, userId: String) {
        withContext(Dispatchers.IO) { // Ensure runs on IO
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val pendingKey = "$pendingSyncGamesKeyPrefix$userId"
            val currentPendingJson = prefs.getString(pendingKey, "[]") // Default to "[]"
            // Ensure non-null for decodeFromString, though "[]" default from getString should handle it.
            val currentPendingGames = AppJsonConfiguration.decodeFromString<MutableList<Game>>(currentPendingJson ?: "[]")
            currentPendingGames.removeAll { it.id == game.id }
            currentPendingGames.add(game)
            // related: for all fields Firestore                W  (26.0.0) [CustomClassMapper]: No setter/field for formattedGameDateTime found on class com.databelay.refwatch.common.Game (fields/setters are case sensitive!)
            prefs.edit(commit = true) { putString(pendingKey, AppJsonConfiguration.encodeToString(currentPendingGames)) }
            Log.i(tag, "Game ${game.id} saved to pending sync for user $userId.")
            updateLocalGameInFlowAndCache(game, userId)
        }
    }

    private suspend fun updateLocalGameInFlowAndCache(updatedGame: Game, userId: String) {
        val currentGames = _gamesListFlow.value.toMutableList()
        val index = currentGames.indexOfFirst { it.id == updatedGame.id }
        if (index != -1) {
            currentGames[index] = updatedGame
        } else {
            currentGames.add(updatedGame)
        }
        _gamesListFlow.value = currentGames.toList() // Ensure it's immutable for the flow
        saveGamesToCache(currentGames.toList(), userId)
    }

    suspend fun syncPendingGames(userId: String) {
        if (userId.isBlank()) return
        if (_networkStatusFlow.value != ConnectivityObserver.Status.AVAILABLE) {
            Log.w(tag, "Network unavailable. Cannot sync pending games for user $userId.")
            _dataFetchStatusFlow.value = DataFetchStatus.ERROR_NETWORK
            return
        }

        withContext(Dispatchers.IO) { // Ensure runs on IO
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val pendingKey = "$pendingSyncGamesKeyPrefix$userId"
            
            val pendingGamesJson = prefs.getString(pendingKey, null)
            if (pendingGamesJson == null) {
                Log.d(tag, "No pending games to sync for user $userId.")
                return@withContext
            }
            val pendingGames = AppJsonConfiguration.decodeFromString<MutableList<Game>>(pendingGamesJson) // pendingGamesJson is non-null here

            if (pendingGames.isEmpty()) {
                Log.d(tag, "Pending games list is empty for user $userId.")
                return@withContext
            }

            Log.i(tag, "Starting sync of ${pendingGames.size} pending games for user $userId.")
            val successfullySyncedGamesIds = mutableListOf<String>()

            for (pendingGame in pendingGames) {
                try {
                    val firestoreDocRef = firestore.collection("users").document(userId)
                        .collection("games").document(pendingGame.id)
                    val firestoreDoc = firestoreDocRef.get().await()
                    if (firestoreDoc.exists()) {
                        val firestoreGame = firestoreDoc.toObject(Game::class.java) // Changed to use Game::class.java
                        // Use 'lastUpdated' from Game.kt
                        if (firestoreGame != null && firestoreGame.lastUpdated > pendingGame.lastUpdated) {
                            Log.d(tag, "Firestore version of game ${pendingGame.id} is newer. Skipping sync.")
                            successfullySyncedGamesIds.add(pendingGame.id)
                            continue
                        }
                    }
                    firestoreDocRef.set(pendingGame).await()
                    Log.i(tag, "Successfully synced pending game ${pendingGame.id} to Firestore for user $userId.")
                    successfullySyncedGamesIds.add(pendingGame.id)
                } catch (e: Exception) {
                    Log.e(tag, "Error syncing pending game ${pendingGame.id} for user $userId. Will retry later.", e)
                }
            }

            if (successfullySyncedGamesIds.isNotEmpty()) {
                val updatedPendingGames = pendingGames.filterNot { successfullySyncedGamesIds.contains(it.id) }
                prefs.edit(commit = true) { putString(pendingKey, AppJsonConfiguration.encodeToString(updatedPendingGames)) }
                Log.i(tag, "Removed ${successfullySyncedGamesIds.size} games from pending sync list for user $userId.")
            }
        }
    }

    suspend fun deleteGame(gameId: String) {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            Log.w(tag, "No authenticated user. Cannot delete game.")
            _dataFetchStatusFlow.value = DataFetchStatus.NO_USER_AUTHENTICATED
            return
        }

        // TODO: Implement offline deletion strategy (e.g., mark as pending delete)
        if (_networkStatusFlow.value != ConnectivityObserver.Status.AVAILABLE) {
            Log.w(tag, "Network unavailable. Deletion of game $gameId for user $userId will be queued or handled offline.")
            // For now, just log and perhaps update status.
            _dataFetchStatusFlow.value = DataFetchStatus.ERROR_NETWORK
            // Add to a "pending delete" list in SharedPreferences if you want to sync deletions
            return
        }

        try {
            Log.d(tag, "Attempting to delete game $gameId from Firestore for user $userId.")
            firestore.collection("users").document(userId)
                .collection("games").document(gameId)
                .delete()
                .await()
            Log.i(tag, "Game $gameId successfully deleted from Firestore for user $userId.")
            // Listener will update cache and flow.
        } catch (e: Exception) {
            Log.e(tag, "Error deleting game $gameId from Firestore for user $userId.", e)
            _dataFetchStatusFlow.value = DataFetchStatus.ERROR_FIREBASE_OPERATION
            // Potentially add to a "pending delete" list here as well if the online attempt fails
        }
    }
    
    private suspend fun removeLocalGameFromFlowAndCache(gameId: String, userId: String) {
        val currentGames = _gamesListFlow.value.toMutableList()
        currentGames.removeAll { it.id == gameId }
        _gamesListFlow.value = currentGames.toList()
        saveGamesToCache(currentGames.toList(), userId)
    }

    fun cleanup() {
        Log.d(tag, "Cleaning up GameStorageWear.")
        detachFirestoreListener()
        storageScope.cancel()
    }
}
