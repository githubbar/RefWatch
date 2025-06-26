package com.databelay.refwatch.games

import android.util.Log
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GameEvent
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

class GameRepository(private val firestore: FirebaseFirestore) {

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val GAMES_COLLECTION = "games"
        private const val TAG = "GameRepository"
    }

    // Create these instances once for efficiency
    private val gson = Gson()
    private val ktxJson = Json {
        ignoreUnknownKeys = true
        // This is needed if your common GameEvent is still in the old format
        // without the class discriminator property in the JSON itself.
        // If you are using the 'type' field as we discussed, this is still good practice.
        classDiscriminator = "eventType" // Or whatever you configured
    }
    fun getGamesFlow(userId: String): Flow<List<Game>> = callbackFlow {
        // ... (same as before, but now using your new Game class)
        // Ensure your Game class has a no-arg constructor or all fields have defaults
        // for toObject<Game>() to work seamlessly. Your provided model has this.

        val gamesCollectionRef = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(GAMES_COLLECTION)
            .orderBy(
                "gameDateTimeEpochMillis",
                Query.Direction.ASCENDING
            ) // Or another relevant field

        val listenerRegistration = gamesCollectionRef.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed for user $userId.", e)
                close(e)
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val games = snapshots.documents.mapNotNull { document ->
                    try {
                        // 1. Convert to Game object, this will have an empty `events` list because of @Exclude
                        val gameBase = document.toObject<Game>()

                        if (gameBase != null) {
                            // 2. Manually parse the events from the document data
                            val parsedEvents = parseGameEventsFromDocument(document)

                            // 3. Return a new Game object with the manually parsed events
                            gameBase.copy(
                                id = document.id, // Ensure Firestore document ID is used
                                events = parsedEvents
                            )
                        } else {
                            null
                        }
                    } catch (ex: Exception) {
                        Log.e(
                            "GameRepository",
                            "Error converting document ${document.id} to Game for user $userId",
                            ex
                        )
                        null
                    }
                }
                Log.d(TAG, "Fetched ${games.size} games for user $userId")
                trySend(games)
            } else {
                trySend(emptyList())
            }
        }
        awaitClose {
            Log.d(TAG, "Closing games flow listener for user $userId")
            listenerRegistration.remove()
        }
    }

    private fun parseGameEventsFromDocument(document: DocumentSnapshot): List<GameEvent> {
        val eventMaps = document.get("events") as? List<Map<String, Any>> ?: return emptyList()

        return eventMaps.mapNotNull { eventMap ->
            try {
                // 1. Use Gson to convert the Firestore Map into a standard JSON String.
                val jsonString = gson.toJson(eventMap)

                // 2. Use kotlinx.serialization to decode the JSON String into a GameEvent.
                //    It will automatically use the correct subclass because GameEvent is sealed
                //    and we added the 'type' (or 'eventType') property to each subclass.
                ktxJson.decodeFromString<GameEvent>(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding a single GameEvent from map: $eventMap", e)
                null // Skip this event if it's malformed
            }
        }
    }

    suspend fun addOrUpdateGame(userId: String, game: Game): Result<Unit> {
        return try {
            if (userId.isEmpty()) return Result.failure(IllegalArgumentException("User ID cannot be empty"))
            // The 'game.id' will be used as the document ID in Firestore
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(GAMES_COLLECTION)
                .document(game.id) // Use the ID from the game object
                .set(game) // 'set' will create if not exists, or overwrite if exists
                .await()
            Log.d(TAG, "Game ${game.id} saved successfully for user $userId.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving game ${game.id} for user $userId", e)
            Result.failure(e)
        }
    }

    // deleteGame and getGameById remain largely the same, just ensure they operate on the `Game` ID.
    suspend fun deleteGame(userId: String, gameId: String): Result<Unit> {
        // ... (implementation as before) ...
        return try {
            if (userId.isEmpty()) return Result.failure(IllegalArgumentException("User ID cannot be empty"))
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(GAMES_COLLECTION)
                .document(gameId)
                .delete()
                .await()
            Log.d(TAG, "Game $gameId deleted successfully for user $userId.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting game $gameId for user $userId", e)
            Result.failure(e)
        }
    }
}