package com.databelay.refwatch.wear.data // Example package for Wear OS data layer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.databelay.refwatch.common.Game // Your common Game class
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.core.content.edit

@Singleton // Hilt will create only one instance of this class for the entire app
class GameStorageWear @Inject constructor(
    @ApplicationContext private val context: Context // Hilt provides the application context
) {
    private val TAG = "GameStorageWear"
    private val PREFS_NAME = "RefWatchWearPrefs"
    private val KEY_GAMES_LIST = "syncedGamesList"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _gamesListFlow = MutableStateFlow<List<Game>>(emptyList())
    val gamesListFlow: StateFlow<List<Game>> = _gamesListFlow.asStateFlow()

    init {
        // Load the initial list from SharedPreferences when the singleton is first created
        loadGamesList()
    }

    fun saveGamesList(games: List<Game>) {
        try {
            val jsonString = json.encodeToString(games)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit {
                    putString(KEY_GAMES_LIST, jsonString)
                }
            // Update the flow to notify observers
            _gamesListFlow.value = games
            Log.i(TAG, "Saved and updated ${games.size} games.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save games list", e)
        }
    }

    private fun loadGamesList() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_GAMES_LIST, null)
            val loadedGames = if (jsonString != null) {
                json.decodeFromString<List<Game>>(jsonString)
            } else {
                emptyList()
            }
            // Update the flow with the loaded data
            _gamesListFlow.value = loadedGames
            Log.i(TAG, "Loaded ${loadedGames.size} games from SharedPreferences.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load games list", e)
            _gamesListFlow.value = emptyList()
        }
    }

    /**
     * Clears the list of games from both memory (StateFlow) and persistence (SharedPreferences).
     */
    fun clearGamesList() {
        try {
            // Clear from SharedPreferences
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit {
                    remove(KEY_GAMES_LIST)
                }

            // Update the flow to notify observers that the list is now empty
            _gamesListFlow.value = emptyList()
            Log.i(TAG, "Games list cleared from storage and memory.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear games list.", e)
        }
    }

    // This can be useful if other parts of the app need a non-flow, immediate snapshot
    fun getGames(): List<Game> {
        return _gamesListFlow.value
    }
}