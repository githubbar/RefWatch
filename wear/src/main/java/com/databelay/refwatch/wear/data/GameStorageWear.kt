package com.databelay.refwatch.wear.data // Example package for Wear OS data layer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.databelay.refwatch.common.Game // Your common Game class
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer // For List<Game>

object GameStorageWear {
    private const val PREFS_NAME = "RefWatchWearPrefs"
    private const val KEY_GAMES_LIST = "syncedGamesList"
    private const val TAG = "GameStorageWear"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        classDiscriminator = "eventType" // If GameEvent needs it
    }

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveGamesList(context: Context, list: List<Game>) {
        try {
            val jsonString = json.encodeToString(ListSerializer(Game.serializer()), list)
            getPreferences(context).edit().putString(KEY_GAMES_LIST, jsonString).apply()
            Log.i(TAG, "Saved ${list.size} games to SharedPreferences on watch.")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving games list to SharedPreferences on watch", e)
        }
    }

    fun loadGamesList(context: Context): List<Game> {
        val jsonString = getPreferences(context).getString(KEY_GAMES_LIST, null)
        return if (jsonString != null) {
            try {
                json.decodeFromString(ListSerializer(Game.serializer()), jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading games list from SharedPreferences on watch", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun clearGamesList(context: Context) {
        getPreferences(context).edit().remove(KEY_GAMES_LIST).apply()
        Log.i(TAG, "Cleared games list from SharedPreferences on watch.")
    }
}