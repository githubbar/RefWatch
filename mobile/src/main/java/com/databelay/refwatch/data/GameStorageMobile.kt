package com.databelay.refwatch.data

// import com.google.gson.Gson // No longer needed for event parsing if using ktx.serialization consistently

import android.util.Log
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.parseGameEventsFromDocument
import com.databelay.refwatch.common.toFirestoreMap
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.collections.iterator


class GameStorageMobile(private val firestore: FirebaseFirestore) {
    companion object {
        private const val USERS_COLLECTION = "users"
        private const val GAMES_COLLECTION = "games"
        private const val tag = "GameRepository"
    }

    fun getGamesFlow(userId: String): Flow<List<Game>> {
        if (userId.isBlank()) {
            Log.w(tag, "getGamesFlow: userId is blank. Returning empty flow.")
            return callbackFlow { trySend(emptyList()); awaitClose { } } // Or handle as an error
        }

        Log.d(tag, "getGamesFlow: Setting up listener for user $userId")
        val gamesCollectionRef = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(GAMES_COLLECTION)
            .orderBy("gameDateTimeEpochMillis", Query.Direction.ASCENDING) // Or another relevant field

        return callbackFlow {
            val listenerRegistration = gamesCollectionRef.addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(tag, "getGamesFlow: Listen failed for user $userId.", e)
                    close(e) // Close the flow with an error
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    Log.d(tag, "getGamesFlow: Snapshots object is null for user $userId. Sending empty list.")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                Log.d(tag, "getGamesFlow: Snapshot received. Number of documents: ${snapshots.size()} for user $userId.")

                val gamesList = snapshots.documents.mapNotNull { document ->
                    try {
                        // 1. Convert to Game object.
                        //    If Game.events has @Exclude, this 'gameBase' will have an empty events list.
                        val gameBase = document.toObject<Game>()

                        if (gameBase == null) {
                            Log.w(tag, "getGamesFlow: Failed to convert document ${document.id} to Game for user $userId. Skipping.")
                            return@mapNotNull null
                        }

                        // 2. Manually parse the events from the document data
                        val parsedEvents = parseGameEventsFromDocument(document)
                        Log.v(tag, "getGamesFlow: Parsed ${parsedEvents.size} events for game ${document.id}")


                        // 3. Return a new Game object with the manually parsed events
                        //    and ensure the Firestore document ID is used.
                        gameBase.copy(
                            id = document.id, // Ensure Firestore document ID is used as the game's ID
                            events = parsedEvents
                        )
                    } catch (ex: Exception) {
                        Log.e(tag, "getGamesFlow: Error converting document ${document.id} to Game for user $userId", ex)
                        null // Skip this document if there's an error
                    }
                }
                Log.d(tag, "getGamesFlow: Processed ${gamesList.size} games for user $userId after parsing events.")
                trySend(gamesList)
            }

            awaitClose {
                Log.d(tag, "getGamesFlow: Closing games flow listener for user $userId")
                listenerRegistration.remove()
            }
        }
    }

    suspend fun addOrUpdateGame(userId: String, gameToSaveInput: Game): Result<Unit> {
        Log.d(tag, "addOrUpdateGame: User: $userId, Input Game ID: ${gameToSaveInput.id}, Events: ${gameToSaveInput.events.size}")
        if (userId.isBlank()) {
            Log.e(tag, "addOrUpdateGame: userId is blank.")
            return Result.failure(IllegalArgumentException("User ID cannot be blank"))
        }
        if (gameToSaveInput.id.isBlank()) {
            // This is a tricky case. If the input ID is blank, it strongly suggests a new game.
            // The alternative search might not be useful or could lead to incorrect merges.
            // For now, let's assume if gameToSaveInput.id is blank, we treat it as purely new.
            Log.w(tag, "addOrUpdateGame: gameToSaveInput.id is blank for user $userId. Treating as new. Alternative search skipped.")
        }

        return try {
            val userGamesCollection = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(GAMES_COLLECTION)

            var effectiveGameId = gameToSaveInput.id
            var gameDocumentRef: DocumentReference
            var existingDocSnapshot: DocumentSnapshot? = null
            var gameToSave = gameToSaveInput // This might be reassigned if an alternative match is found

            // Attempt 1: Fetch by gameToSaveInput.id
            if (effectiveGameId.isNotBlank()) {
                gameDocumentRef = userGamesCollection.document(effectiveGameId)
                val primarySnapshot = gameDocumentRef.get().await()
                if (primarySnapshot.exists()) {
                    existingDocSnapshot = primarySnapshot
                    Log.d(tag, "Game found by input ID: $effectiveGameId")
                } else {
                    Log.d(tag, "Game NOT found by input ID: $effectiveGameId. Will try alternative search.")
                }
            } else {
                // If input ID is blank, we can't fetch by it. We might still proceed to alternative search
                // or decide to always create a new document with a generated ID.
                // For now, if ID is blank, the subsequent alternative search will be the only way to find an existing one.
                // If no alternative is found, a new ID *should* be generated if we decide to save.
                // The current logic path will eventually try to use a (potentially blank) effectiveGameId
                // to create a document, which Firestore will reject.
                // This highlights that gameToSave.id should ideally always be populated before this function
                // or generated here if it's truly new.
                // Let's assume for now the caller (ViewModel) ensures gameToSaveInput.id is set (new UUID for new games).
                // If gameToSaveInput.id was blank, we are now in the state where no game was found by ID.
                Log.d(tag, "Input game ID is blank. Proceeding to alternative search if applicable, or will create new.")
            }

            // Attempt 2: Alternative search if not found by ID (existingDocSnapshot == null)
            if (existingDocSnapshot == null) {
                Log.d(tag, "Attempting alternative search for gameNumber: '${gameToSave.gameNumber}' and timestamp: ${gameToSave.gameDateTimeEpochMillis}")
                if (gameToSave.gameNumber != Game.defaults().gameNumber && gameToSave.gameDateTimeEpochMillis != null
                    && gameToSave.gameDateTimeEpochMillis!! > 0) {
                    val query = userGamesCollection
                        .whereEqualTo("gameNumber", gameToSave.gameNumber)
                        .whereEqualTo("gameDateTimeEpochMillis", gameToSave.gameDateTimeEpochMillis)
                        // Add other fields to make the match more unique if necessary, e.g., homeTeamName
                        // .whereEqualTo("homeTeamName", gameToSave.homeTeamName)
                        .limit(1)

                    val alternativeQuerySnapshot = query.get().await()

                    if (!alternativeQuerySnapshot.isEmpty) {
                        val alternativeDoc = alternativeQuerySnapshot.documents.first()
                        existingDocSnapshot = alternativeDoc // Found an existing game by alternative criteria
                        effectiveGameId = alternativeDoc.id // CRITICAL: Use the ID of the document found by query
                        gameDocumentRef = alternativeDoc.reference
                        // Update gameToSave to use the ID of the found document to ensure consistency
                        // especially if toFirestoreMap() includes the 'id' field.
                        gameToSave = gameToSave.copy(id = effectiveGameId)
                        Log.i(tag, "Alternative search found matching game. Switched to existing document ID: $effectiveGameId (was input ID: ${gameToSaveInput.id})")
                    } else {
                        Log.d(tag, "Alternative search found NO matching game.")
                        // If still no game found, and original input ID was blank, we need to assign one.
                        // However, assuming ViewModel provides a valid ID for gameToSaveInput.
                        if (effectiveGameId.isBlank()) {
                            // This case means original ID was blank AND no alternative was found.
                            // This should be handled by the ViewModel by providing an ID.
                            // For safety, though, if we reach here with a blank ID, it's an error.
                            Log.e(tag, "Effective game ID is still blank after all checks. Cannot save without an ID.")
                            return Result.failure(IllegalArgumentException("Game ID is blank and no existing game found to update."))
                        }
                        gameDocumentRef = userGamesCollection.document(effectiveGameId) // Use original/input ID for new doc
                    }
                } else {
                    Log.w(tag, "Skipping alternative search: gameNumber or gameDateTimeEpochMillis is missing/invalid.")
                    if (effectiveGameId.isBlank()) {
                        Log.e(tag, "Effective game ID is blank and alternative search was skipped. Cannot save without an ID.")
                        return Result.failure(IllegalArgumentException("Game ID is blank and alternative search criteria insufficient."))
                    }
                    gameDocumentRef = userGamesCollection.document(effectiveGameId) // Use original/input ID for new doc
                }
            } else {
                gameDocumentRef = userGamesCollection.document(effectiveGameId) // Already found by ID
            }


            // At this point, existingDocSnapshot is either null (truly new game)
            // or refers to the document to be updated (found by ID or alternative query).
            // gameDocumentRef points to the correct document reference.
            // gameToSave has the data to save, and its .id matches effectiveGameId if an alternative was found.

            var dataToSet: Map<String, Any?>

            if (existingDocSnapshot != null && existingDocSnapshot.exists()) { // Check .exists() on the snapshot
                val existingGame = existingDocSnapshot.toObject<Game>()
                // Manually parse existing events as they are @Exclude for toObject<Game>()
                // val existingEvents = existingGame?.let { parseGameEventsFromDocument(existingDocSnapshot) } ?: emptyList() // If needed

                if (existingGame != null && existingGame.currentPhase == GamePhase.GAME_ENDED) {
                    Log.i(tag, "Game $effectiveGameId is already GAME_ENDED in Firestore. Preserving scores and events.")
                    val updatedDataForEndedGame = mutableMapOf<String, Any?>()
                    updatedDataForEndedGame["notes"] = gameToSave.notes
                    updatedDataForEndedGame["lastUpdated"] = gameToSave.lastUpdated // Or FieldValue.serverTimestamp()

                    // Fields to PRESERVE from existingGame
                    updatedDataForEndedGame["homeScore"] = existingGame.homeScore
                    updatedDataForEndedGame["awayScore"] = existingGame.awayScore
                    updatedDataForEndedGame["penaltiesTakenHome"] = existingGame.penaltiesTakenHome
                    updatedDataForEndedGame["penaltiesTakenAway"] = existingGame.penaltiesTakenAway
                    // Preserve existing events if game is ended and gameToSave's events shouldn't overwrite
                    // This depends on your business logic for ended games.
                    // If gameToSave comes from a full edit, maybe its events are newer.
                    // If gameToSave is just a partial update (e.g. only notes changed), preserve existing events.
                    // For now, let's assume we take events from gameToSave if it's an "update"
                    // but if this was a merge due to alternative find, it's more complex.
                    // The original code took events from existingGame.
                    val eventsToPersist = parseGameEventsFromDocument(existingDocSnapshot) // Re-parse from the found doc
//                    updatedDataForEndedGame["events"] = eventsToPersist.map { it.toFirestoreMap() }


                    updatedDataForEndedGame["currentPhase"] = existingGame.currentPhase
                    // ... preserve any other fields ...

                    val incomingGameMap = gameToSave.toFirestoreMap() // gameToSave.id is now effectiveGameId
                    for ((key, value) in incomingGameMap) {
                        if (!updatedDataForEndedGame.containsKey(key)) {
                            updatedDataForEndedGame[key] = value
                        }
                    }
                    // Ensure the ID field in the map matches the document ID, if toFirestoreMap includes 'id'
                    updatedDataForEndedGame["id"] = effectiveGameId
                    dataToSet = updatedDataForEndedGame
                    gameDocumentRef.set(dataToSet).await()

                } else {
                    // Game is not ended in Firestore, or was found by alternative ID. Overwrite with gameToSave.
                    // (gameToSave.id should be effectiveGameId here)
                    Log.d(tag, "Game $effectiveGameId is not ended or is being updated. Saving full game data from input (merged with alternative find if any).")
                    dataToSet = gameToSave.toFirestoreMap() // gameToSave has the correct ID now
                    gameDocumentRef.set(dataToSet).await()
                }
            } else {
                // Document does not exist by any means, so it's a new game. Save all data from gameToSave.
                // (gameToSaveInput.id should be effectiveGameId (original or new UUID) here, and gameToSave is gameToSaveInput)
                // Ensure gameToSave (which is gameToSaveInput at this point if no alt found) has a valid ID.
                if (gameToSave.id.isBlank()){ // Should have been caught earlier or assigned by ViewModel
                    Log.e(tag, "CRITICAL: Attempting to save a new game with a blank ID: ${gameToSave.id}")
                    return Result.failure(IllegalArgumentException("Cannot create a new game with a blank ID."))
                }
                dataToSet = gameToSave.toFirestoreMap()
                Log.d(tag, "Game ${gameToSave.id} does not exist. Creating new game document.")
                gameDocumentRef.set(dataToSet).await() // gameDocumentRef uses gameToSave.id
                Log.d(tag, "Successfully set document ${gameDocumentRef.path} in Firestore.")
            }

            Log.i(tag, "Successfully saved/updated game $effectiveGameId to Firestore for user $userId.")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(tag, "Error saving game ${gameToSaveInput.id} (effective ID: ${gameToSaveInput.id} if changed) for user $userId to Firestore. Error: ${e.message}", e)
            Result.failure(e)
        }
    }



    suspend fun deleteGame(userId: String, gameId: String): Result<Unit> {
        return try {
            if (userId.isEmpty()) return Result.failure(IllegalArgumentException("User ID cannot be empty"))
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(GAMES_COLLECTION)
                .document(gameId)
                .delete()
                .await()
            Log.d(tag, "Game $gameId deleted successfully for user $userId.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error deleting game $gameId for user $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Erases everything the user has stored in Firestore: every game, then the user document.
     * Must run while the user is still signed in, since the security rules only let a user touch
     * their own data. Games carry GPS and heart-rate history, so they are fetched a page at a time
     * and read from the server, so games that never reached this device's cache are deleted too.
     */
    suspend fun deleteAllUserData(userId: String): Result<Unit> {
        return try {
            if (userId.isEmpty()) return Result.failure(IllegalArgumentException("User ID cannot be empty"))
            val userDoc = firestore.collection(USERS_COLLECTION).document(userId)
            var deleted = 0
            while (true) {
                val page = userDoc.collection(GAMES_COLLECTION)
                    .limit(DELETE_PAGE_SIZE)
                    .get(Source.SERVER)
                    .await()
                if (page.isEmpty) break
                val batch = firestore.batch()
                page.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
                deleted += page.size()
            }
            userDoc.delete().await()
            Log.i(tag, "Deleted $deleted games and the user document for user $userId.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error deleting data for user $userId", e)
            Result.failure(e)
        }
    }
}

// Firestore caps a write batch at 500 operations; pages stay well below it and keep each
// download of GPS-heavy games small.
private const val DELETE_PAGE_SIZE = 100L

