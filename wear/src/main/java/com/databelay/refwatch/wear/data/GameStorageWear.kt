package com.databelay.refwatch.wear.data // Example package for Wear OS data layer

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.databelay.refwatch.common.AppJsonConfiguration // Assuming your common Json object
import com.databelay.refwatch.common.Game // Your common Game class
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString // Ensure this is the one from AppJsonConfiguration if it's custom
import javax.inject.Inject
import javax.inject.Singleton

// ---- Put DataFetchStatus enum here or import it ----
enum class DataFetchStatus {
    INITIAL,
    FETCHING,
    SUCCESS,
    LOADED_FROM_CACHE,
    NO_DATA_AVAILABLE,
    ERROR_PHONE_UNREACHABLE,
    ERROR_PARSING,
    ERROR_UNKNOWN
}

@Singleton // Hilt will create only one instance of this class for the entire app
class GameStorageWear @Inject constructor(
    @ApplicationContext private val context: Context // Hilt provides the application context
) {
    private val TAG = "GameStorageWear"
    private val PREFS_NAME = "RefWatchWearPrefs"
    private val KEY_GAMES_LIST = "syncedGamesList"
    private val KEY_DATA_FETCH_STATUS = "dataFetchStatus" // For persisting status

    // Use your common JSON configuration
    private val json = AppJsonConfiguration // Assuming AppJsonConfiguration is your Json instance

    private val _gamesListFlow = MutableStateFlow<List<Game>>(emptyList())
    val gamesListFlow: StateFlow<List<Game>> = _gamesListFlow.asStateFlow()

    private val _dataFetchStatusFlow = MutableStateFlow(DataFetchStatus.INITIAL)
    val dataFetchStatusFlow: StateFlow<DataFetchStatus> = _dataFetchStatusFlow.asStateFlow()

    init {
        // Load the initial list and status from SharedPreferences
        loadGamesListAndStatus()
    }

    /**
     * Called by WearDataListenerService when new game data is received from the phone.
     */
    fun saveGamesListFromPhone(games: List<Game>) {
        try {
            val jsonString = json.encodeToString(games) // Using your AppJsonConfiguration
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit {
                    putString(KEY_GAMES_LIST, jsonString)
                    // When saving from phone, it's either SUCCESS or NO_DATA_AVAILABLE
                    val newStatus = if (games.isEmpty()) DataFetchStatus.NO_DATA_AVAILABLE else DataFetchStatus.SUCCESS
                    putString(KEY_DATA_FETCH_STATUS, newStatus.name)
                    _dataFetchStatusFlow.value = newStatus
                }
            _gamesListFlow.value = games
            Log.i(TAG, "Saved ${games.size} games from phone. Status: ${_dataFetchStatusFlow.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save games list from phone", e)
            // If saving fails after successful phone communication, it's an unknown error here
            // ERROR_PARSING should be set by the listener if it fails to decode data from phone
            updateDataFetchStatus(DataFetchStatus.ERROR_UNKNOWN)
        }
    }

    private fun loadGamesListAndStatus() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_GAMES_LIST, null)
            val loadedGames = if (jsonString != null) {
                json.decodeFromString<List<Game>>(jsonString)
            } else {
                emptyList()
            }
            _gamesListFlow.value = loadedGames

            val persistedStatusString = prefs.getString(KEY_DATA_FETCH_STATUS, DataFetchStatus.INITIAL.name)
            val loadedStatus = try {
                DataFetchStatus.valueOf(persistedStatusString ?: DataFetchStatus.INITIAL.name)
            } catch (e: IllegalArgumentException) {
                DataFetchStatus.INITIAL
            }

            // Refine initial status based on loaded data
            if (loadedStatus == DataFetchStatus.INITIAL && loadedGames.isNotEmpty()) {
                _dataFetchStatusFlow.value = DataFetchStatus.LOADED_FROM_CACHE
            } else if (loadedStatus == DataFetchStatus.SUCCESS && loadedGames.isEmpty()){
                // If it was SUCCESS but now we load and it's empty, it could be NO_DATA_AVAILABLE
                _dataFetchStatusFlow.value = DataFetchStatus.NO_DATA_AVAILABLE
            }
            else {
                _dataFetchStatusFlow.value = loadedStatus
            }
            Log.i(TAG, "Loaded ${loadedGames.size} games from SharedPreferences. Initial Status: ${_dataFetchStatusFlow.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load games list or status", e)
            _gamesListFlow.value = emptyList()
            updateDataFetchStatus(DataFetchStatus.ERROR_UNKNOWN) // Or a more specific load error
        }
    }

    /**
     * Called by WearDataListenerService when the game list data item is deleted by the phone.
     */
    fun clearGamesListFromPhone() {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit {
                    remove(KEY_GAMES_LIST)
                    // When cleared by phone, it implies no data is currently available from the source
                    putString(KEY_DATA_FETCH_STATUS, DataFetchStatus.NO_DATA_AVAILABLE.name)
                    _dataFetchStatusFlow.value = DataFetchStatus.NO_DATA_AVAILABLE
                }
            _gamesListFlow.value = emptyList()
            Log.i(TAG, "Games list cleared on instruction from phone. Status: NO_DATA_AVAILABLE")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear games list.", e)
            updateDataFetchStatus(DataFetchStatus.ERROR_UNKNOWN)
        }
    }

    /**
     * Updates the data fetch status. Can be called from WearDataListenerService
     * if parsing fails, or from a ViewModel if connectivity checks fail.
     */
    fun updateDataFetchStatus(newStatus: DataFetchStatus) {
        // Avoid overwriting a more specific error with a general one if not intended
        if (_dataFetchStatusFlow.value == DataFetchStatus.ERROR_PHONE_UNREACHABLE && newStatus == DataFetchStatus.ERROR_UNKNOWN) {
            // Keep the more specific error
            return
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_DATA_FETCH_STATUS, newStatus.name)
            }
        _dataFetchStatusFlow.value = newStatus
        Log.i(TAG, "DataFetchStatus updated to: $newStatus")
    }

    // This can be useful if other parts of the app need a non-flow, immediate snapshot
    fun getGames(): List<Game> {
        return _gamesListFlow.value
    }

    // Original saveGamesList and loadGamesList renamed or adapted
    // The public methods `saveGamesListFromPhone` and `clearGamesListFromPhone`
    // are now the primary entry points from the Data Layer events.
    // `updateDataFetchStatus` is the entry point for other status changes.
}

