package com.databelay.refwatch.mobile.games

import android.util.Log
import com.databelay.refwatch.common.Game
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GameRepository(private val firestore: FirebaseFirestore) {

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val GAMES_COLLECTION = "games"
        private const val TAG = "GameRepository"
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
                        // Firestore will attempt to map the document to your Game data class
                        document.toObject<Game>()
                            ?.copy(id = document.id) // Ensure ID from document is used
                    } catch (ex: Exception) {
                        Log.e(
                            TAG,
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