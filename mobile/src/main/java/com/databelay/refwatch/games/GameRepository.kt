package com.databelay.refwatch.games

import android.util.Log
import androidx.compose.foundation.layout.add
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GameEvent
import com.databelay.refwatch.common.GenericLogEvent
import com.databelay.refwatch.common.PhaseChangedEvent
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
// import com.google.gson.Gson // No longer needed for event parsing if using ktx.serialization consistently
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map // Import for flow transformation
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString // Explicit import for clarity
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.json.JsonElement // <-- Add this import
import kotlinx.serialization.json.JsonObject // <-- Add this import
import kotlinx.serialization.json.JsonPrimitive // <-- Add this import
import kotlinx.serialization.json.JsonNull // <-- Add this import
import kotlinx.serialization.json.JsonArray // <-- Add this import
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject


class GameRepository(private val firestore: FirebaseFirestore) {

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val GAMES_COLLECTION = "games"
        private const val TAG = "GameRepository"
    }

    // Configure ktxJson - this MUST match the config in your common module,
    // watch app, and phone's MobileGameViewModel or DataLayerListenerService
    private val ktxJson = Json {
        prettyPrint = true
        classDiscriminator = "eventType"
        ignoreUnknownKeys = true
        encodeDefaults = true
        serializersModule = SerializersModule {
            polymorphic(GameEvent::class) {
                subclass(GenericLogEvent::class)
                subclass(PhaseChangedEvent::class)
            }
        }
    }

    // We no longer need Gson for parsing events if using ktxJson consistently
    // private val gson = Gson()


    fun getGamesFlow(userId: String): Flow<List<Game>> {
        if (userId.isBlank()) {
            Log.w(TAG, "getGamesFlow: userId is blank. Returning empty flow.")
            return callbackFlow { trySend(emptyList()); awaitClose { } } // Or handle as an error
        }

        Log.d(TAG, "getGamesFlow: Setting up listener for user $userId")
        val gamesCollectionRef = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(GAMES_COLLECTION)
            .orderBy("gameDateTimeEpochMillis", Query.Direction.ASCENDING) // Or another relevant field

        return callbackFlow {
            val listenerRegistration = gamesCollectionRef.addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "getGamesFlow: Listen failed for user $userId.", e)
                    close(e) // Close the flow with an error
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    Log.d(TAG, "getGamesFlow: Snapshots object is null for user $userId. Sending empty list.")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                Log.d(TAG, "getGamesFlow: Snapshot received. Number of documents: ${snapshots.size()} for user $userId.")

                val gamesList = snapshots.documents.mapNotNull { document ->
                    try {
                        // 1. Convert to Game object.
                        //    If Game.events has @Exclude, this 'gameBase' will have an empty events list.
                        val gameBase = document.toObject<Game>()

                        if (gameBase == null) {
                            Log.w(TAG, "getGamesFlow: Failed to convert document ${document.id} to Game for user $userId. Skipping.")
                            return@mapNotNull null
                        }

                        // 2. Manually parse the events from the document data
                        val parsedEvents = parseGameEventsFromDocument(document)
                        Log.v(TAG, "getGamesFlow: Parsed ${parsedEvents.size} events for game ${document.id}")


                        // 3. Return a new Game object with the manually parsed events
                        //    and ensure the Firestore document ID is used.
                        gameBase.copy(
                            id = document.id, // Ensure Firestore document ID is used as the game's ID
                            events = parsedEvents
                        )
                    } catch (ex: Exception) {
                        Log.e(TAG, "getGamesFlow: Error converting document ${document.id} to Game for user $userId", ex)
                        null // Skip this document if there's an error
                    }
                }
                Log.d(TAG, "getGamesFlow: Processed ${gamesList.size} games for user $userId after parsing events.")
                trySend(gamesList)
            }

            awaitClose {
                Log.d(TAG, "getGamesFlow: Closing games flow listener for user $userId")
                listenerRegistration.remove()
            }
        }
    }


    // This function is now correctly placed and used by getGamesFlow
    private fun parseGameEventsFromDocument(document: DocumentSnapshot): List<GameEvent> {
        val eventMapsFirestore = document.get("events") as? List<Map<String, Any?>> ?: run {
            Log.d(TAG, "parseGameEventsFromDocument: Document ${document.id} has no 'events' field, or it's not a list. Returning empty list.")
            return emptyList()
        }

        if (eventMapsFirestore.isEmpty()) {
            Log.d(TAG, "parseGameEventsFromDocument: Document ${document.id} has an empty 'events' list (read from Firestore).")
            return emptyList()
        }
        Log.d(TAG, "parseGameEventsFromDocument: Document ${document.id} has ${eventMapsFirestore.size} event maps to parse from Firestore.")

        return eventMapsFirestore.mapNotNull { eventMapFromFirestore ->
            // LOG 3: Log the individual event map from Firestore
            Log.v(TAG, "parseGameEventsFromDocument: Attempting to decode event map for doc ${document.id}: $eventMapFromFirestore")
            try {
                // 1. Convert Map<String, Any?> from Firestore TO a kotlinx.serialization.json.JsonObject
                val jsonObject = mapToJsonObject(eventMapFromFirestore)
                // LOG 4: Log the JsonObject before ktx.serialization decodes it to GameEvent
                Log.v(TAG, "parseGameEventsFromDocument: Converted Firestore map to JsonObject for doc ${document.id}: $jsonObject")

                // 2. Deserialize the JsonObject TO a GameEvent object
                // ktxJson needs classDiscriminator and SerializersModule configured to work here.
                // This also works if GameEvent is a sealed class and setup for polymorphism
                val event: GameEvent = ktxJson.decodeFromJsonElement(jsonObject)
                // LOG 5: Log the successfully decoded event
                Log.d(TAG, "parseGameEventsFromDocument: Successfully decoded event for doc ${document.id}: $event")
                event
            } catch (e: Exception) {
                // LOG 6: THIS IS VERY IMPORTANT IF EVENTS ARE MISSING or parsing fails
                Log.e(TAG, "parseGameEventsFromDocument: Error decoding a single GameEvent from map for doc ${document.id}. Map from Firestore: $eventMapFromFirestore. Error: ", e)
                // Optionally log the JsonObject if the mapToJsonObject conversion seems problematic:
                // try { val problematicJson = mapToJsonObject(eventMapFromFirestore); Log.e(TAG, "Problematic JsonObject: $problematicJson") } catch (jsonEx: Exception) { Log.e(TAG, "Failed to convert map to JsonObject for error logging", jsonEx) }
                null
            }
        }
    }


    // Ensure ktxJson is defined as a class member or accessible, configured correctly
    // private val ktxJson = Json { ignoreUnknownKeys = true; classDiscriminator = "eventType"; encodeDefaults = true }

    suspend fun addOrUpdateGame(userId: String, game: Game): Result<Unit> {
        Log.d(TAG, "addOrUpdateGame: User: $userId, Game ID: ${game.id}, Events in Game object: ${game.events.size}")
        if (userId.isBlank()) {
            Log.e(TAG, "addOrUpdateGame: userId is blank.")
            return Result.failure(IllegalArgumentException("User ID cannot be blank"))
        }
        if (game.id.isBlank()) {
            Log.e(TAG, "addOrUpdateGame: game.id is blank for user $userId.")
            return Result.failure(IllegalArgumentException("Game ID cannot be blank for addOrUpdateGame"))
        }

        return try {
            val gameDocumentRef = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(GAMES_COLLECTION)
                .document(game.id) // Using game.id as the document ID

            // Convert Game object to a Map for Firestore
            val gameDataForFirestore = mutableMapOf<String, Any?>(
                "id" to game.id,
                "lastUpdated" to game.lastUpdated,
                "halfDurationMinutes" to game.halfDurationMinutes,
                "halftimeDurationMinutes" to game.halftimeDurationMinutes,
                "homeTeamName" to game.homeTeamName,
                "awayTeamName" to game.awayTeamName,

                // Correctly handling AgeGroup (which is an enum)
                "ageGroup" to game.ageGroup?.name, // Store the enum constant's name as a String

                "competition" to game.competition,
                "venue" to game.venue,
                "gameDateTimeEpochMillis" to game.gameDateTimeEpochMillis,
                "notes" to game.notes,
                "homeTeamColorArgb" to game.homeTeamColorArgb,
                "awayTeamColorArgb" to game.awayTeamColorArgb,
                "kickOffTeam" to game.kickOffTeam.name,
                "currentPeriodKickOffTeam" to game.currentPeriodKickOffTeam.name,
                "status" to game.status.name,
                "currentPhase" to game.currentPhase.name,
                "homeScore" to game.homeScore,
                "awayScore" to game.awayScore,
                "displayedTimeMillis" to game.displayedTimeMillis,
                "actualTimeElapsedInPeriodMillis" to game.actualTimeElapsedInPeriodMillis,
                "isTimerRunning" to game.isTimerRunning,
                "userId" to userId
            )

            // Convert List<GameEvent> to List<Map<String, Any?>> for Firestore
            val eventsForFirestore = game.events.mapNotNull { event ->
                try {
                    // 1. Serialize GameEvent to JSON String
                    // This uses the specific serializer for the concrete type of 'event'
                    // (e.g., GoalScoredEvent.serializer(), GenericLogEvent.serializer())
                    // and includes the classDiscriminator if configured.
                    val eventJsonString = ktxJson.encodeToString(event)
                    Log.v(TAG, "addOrUpdateGame: Serialized event to JSON string: $eventJsonString")


                    // 2. Parse the JSON String into a kotlinx.serialization.json.JsonObject
                    // This is a generic JSON object structure.
                    val jsonObject = ktxJson.parseToJsonElement(eventJsonString).jsonObject
                    Log.v(TAG, "addOrUpdateGame: Parsed JSON string to JsonObject: $jsonObject")

                    // 3. Convert the JsonObject to Map<String, Any?>
                    // This map is what Firestore can store.
                    val eventMap = jsonObjectToMap(jsonObject)
                    Log.v(TAG, "addOrUpdateGame: Converted JsonObject to Map: $eventMap")
                    eventMap

                } catch (e: Exception) {
                    Log.e(TAG, "addOrUpdateGame: Failed to process a GameEvent for Firestore. Game: ${game.id}, Event: $event, Error: ${e.message}", e)
                    null
                }
            }
            gameDataForFirestore["events"] = eventsForFirestore

            Log.d(TAG, "addOrUpdateGame: Saving game ${game.id} for user $userId with ${eventsForFirestore.size} events.")
            Log.v(TAG, "addOrUpdateGame: Data being sent to Firestore: $gameDataForFirestore")

            gameDocumentRef.set(gameDataForFirestore).await()
            Log.i(TAG, "addOrUpdateGame: Successfully saved game ${game.id} to Firestore for user $userId.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addOrUpdateGame: Error saving game ${game.id} for user $userId to Firestore. Error: ${e.message}", e)
            Result.failure(e)
        }

    }

    // Helper function to convert JsonElement to Any? for Firestore compatibility
    // Make sure this is part of your GameRepository class or accessible to it.
    private fun jsonElementToAny(jsonElement: JsonElement): Any? {
        return when (jsonElement) {
            is JsonNull -> null
            is JsonPrimitive -> {
                if (jsonElement.isString) jsonElement.content
                else if (jsonElement.content == "true" || jsonElement.content == "false") jsonElement.content.toBoolean()
                else jsonElement.content.toDoubleOrNull() ?: jsonElement.content.toLongOrNull() ?: jsonElement.content // Fallback to string if not clearly number/boolean
            }
            is JsonObject -> jsonObjectToMap(jsonElement)
            is JsonArray -> jsonArrayToList(jsonElement)
        }
    }

    // Helper function to convert JsonObject to Map<String, Any?>
    private fun jsonObjectToMap(jsonObject: JsonObject): Map<String, Any?> {
        return jsonObject.entries.associate { (key, jsonElement) ->
            key to jsonElementToAny(jsonElement)
        }
    }

    // Helper function to convert JsonArray to List<Any?>
    private fun jsonArrayToList(jsonArray: JsonArray): List<Any?> {
        return jsonArray.map { jsonElementToAny(it) }
    }

    // Helper function to convert a Map<String, Any?> from Firestore to JsonObject
// This needs to handle various types Firestore might return
    private fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
        return buildJsonObject {
            map.forEach { (key, value) ->
                put(key, anyToJsonElement(value))
            }
        }
    }

    // Helper function to convert Any? from Firestore map value to JsonElement
    private fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value) // Handles Int, Long, Double, Float
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> {
                // Ensure keys are Strings for JsonObject
                @Suppress("UNCHECKED_CAST")
                mapToJsonObject(value as? Map<String, Any?> ?: emptyMap())
            }
            is List<*> -> buildJsonArray {
                value.forEach { item -> add(anyToJsonElement(item)) }
            }
            else -> {
                // Fallback for unknown types: try converting to string.
                // This might not be ideal for all complex types but can prevent crashes.
                // Consider logging a warning here if you hit this case often.
                Log.w(TAG, "anyToJsonElement: Encountered an unknown type (${value::class.java.name}), converting to JsonPrimitive string: $value")
                JsonPrimitive(value.toString())
            }
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
            Log.d(TAG, "Game $gameId deleted successfully for user $userId.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting game $gameId for user $userId", e)
            Result.failure(e)
        }
    }
}

