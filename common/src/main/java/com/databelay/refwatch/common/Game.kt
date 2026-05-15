package com.databelay.refwatch.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.databelay.refwatch.common.theme.DefaultAwayJerseyColor
import com.databelay.refwatch.common.theme.DefaultHomeJerseyColor
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID

// --- Game Settings ---
@Serializable // Add this
@IgnoreExtraProperties // Add this annotation to the class
data class Game(
    // --- Core Game Mechanics Settings ---
    val id: String = UUID.randomUUID().toString(), // Unique ID for these game settings instance
    val userId: String = "", // User who created this game (for cache and syncing
    var lastUpdated: Long = System.currentTimeMillis(), // Timestamp for when this was last updated by user
    var halfDurationMinutes: Int = 45,
    var halftimeDurationMinutes: Int = 15,
    var extraTimeHalfDurationMinutes: Int = 15, // Optional for future
    var extraTimeHalftimeDurationMinutes: Int = 1, // Optional for future
//
    // --- Match Information (can be pre-filled from a schedule) ---
    var gameNumber: String = "XXXX", // Default, can be overridden
    var fieldNumber: String? = null, // Default, can be overridden
    var homeTeamName: String = "Home", // Default, can be overridden
    var awayTeamName: String = "Away", // Default, can be overridden
    var ageGroup: AgeGroup? = null,          // e.g., "U12 Boys", "Adult Men"
    var competition: String? = null,       // e.g., "League Match", "Cup Final"
    var refereeAssignment: String? = null, // e.g. Assistant Referee
    var venue: String? = null,             // e.g., "Field 3, West Park"
    var gameDateTimeEpochMillis: Long? = null, // Start date & time of the match, UTC epoch ms
    var notes: String? = null,
    // Live State Fields (updated by watch, synced via phone to Firebase)
    val inAddedTime: Boolean = false, // Is the current playable period in added time?
    var hasExtraTime: Boolean = false, // True if extra time has been initiated
    var hasPenalties: Boolean = false, // True if extra time has been initiated
    var homeTeamColorArgb: Int = DefaultHomeJerseyColor.toArgb(),
    var awayTeamColorArgb: Int = DefaultAwayJerseyColor.toArgb(),
    var kickOffTeam: Team = Team.HOME, // Actual team kicking off current period (managed by ViewModel)
    var penaltiesTakenHome: Int = 0, // Number of penalties scored by home team
    var penaltiesTakenAway: Int = 0, // Number of penalties scored by away team
    var currentPhase: GamePhase = GamePhase.NOT_STARTED,
    var homeScore: Int = 0,
    var awayScore: Int = 0,
    var displayedTimeMillis: Long = 45,
    var actualTimeElapsedInPeriodMillis: Long = 0L,
    var isTimerRunning: Boolean = false,
    val locationHistory: List<LocationSample> = emptyList(),
    val heartRateHistory: List<HeartRateSample> = emptyList(),
    val stepHistory: List<StepSample> = emptyList(),
    @get:Exclude
    val needsSyncWithPhone: Boolean = false, // Store locally on watch, needs sync with phone
    @get:Exclude
    val events: List<GameEvent> = emptyList() // We will sync this one manually
)  {
    // Computed property for GameStatus
    // If you use Kotlinx.serialization and don't want this in Firestore,
    // you might not need @Transient if it's just a getter.
    // If it were a var with a backing field you didn't want to store, you'd use @Transient.
    val status: GameStatus
        get() {
            return currentPhase.status()
        }
    // Secondary constructor for Firestore deserialization, ensures `id` is always present.
    // No-argument constructor is required by Firestore for deserialization to a custom object.
    // It's good practice to initialize all fields to default values.
    constructor() : this(
        id = UUID.randomUUID().toString(), // Generate a new ID if none provided
        userId = "",
        lastUpdated = System.currentTimeMillis(),
        halfDurationMinutes = 45,
        halftimeDurationMinutes = 15,
        extraTimeHalfDurationMinutes = 15,
        extraTimeHalftimeDurationMinutes = 1,
        gameNumber = "XXXX",
        fieldNumber = null,
        homeTeamName = "Home",
        awayTeamName = "Away",
        ageGroup = null,
        competition = null,
        refereeAssignment = null,
        venue = null,
        gameDateTimeEpochMillis = null,
        notes = null,
        inAddedTime = false,
        hasExtraTime = false,
        hasPenalties = false,
        homeTeamColorArgb = DefaultHomeJerseyColor.toArgb(),
        awayTeamColorArgb = DefaultAwayJerseyColor.toArgb(),
        kickOffTeam = Team.HOME,
        penaltiesTakenHome = 0,
        penaltiesTakenAway = 0,
        currentPhase = GamePhase.NOT_STARTED,
        homeScore = 0,
        awayScore = 0,
        displayedTimeMillis = 45L * 60 * 1000, // Default to half duration in millis
        actualTimeElapsedInPeriodMillis = 0L,
        isTimerRunning = false,
        locationHistory = emptyList(),
        heartRateHistory = emptyList(),
        stepHistory = emptyList(),
        needsSyncWithPhone = false,
        events = emptyList()
    )

    companion object {
        fun defaults(): Game {
            return Game( // Call primary constructor with all defaults explicitly for clarity
                id = UUID.randomUUID().toString(),
                userId = "",
                lastUpdated = System.currentTimeMillis(),
                halfDurationMinutes = 45,
                halftimeDurationMinutes = 15,
                extraTimeHalfDurationMinutes = 15,
                extraTimeHalftimeDurationMinutes = 1,
                gameNumber = "XXXX",
                fieldNumber = null,
                homeTeamName = "Home",
                awayTeamName = "Away",
                ageGroup = null,
                competition = null,
                refereeAssignment = null,
                venue = null,
                gameDateTimeEpochMillis = null,
                notes = null,
                inAddedTime = false,
                hasExtraTime = false,
                hasPenalties = false,
                homeTeamColorArgb = DefaultHomeJerseyColor.toArgb(),
                awayTeamColorArgb = DefaultAwayJerseyColor.toArgb(),
                kickOffTeam = Team.HOME,
                penaltiesTakenHome = 0,
                penaltiesTakenAway = 0,
                currentPhase = GamePhase.NOT_STARTED,
                homeScore = 0,
                awayScore = 0,
                displayedTimeMillis = 45L * 60 * 1000, // default half duration in millis
                actualTimeElapsedInPeriodMillis = 0L,
                isTimerRunning = false,
                locationHistory = emptyList(),
                heartRateHistory = emptyList(),
                stepHistory = emptyList(),
                needsSyncWithPhone = false, // Not typically set in defaults directly
                events = emptyList()
            )
        }
    }
    // Constructor to initialize from SimpleIcsEvent
    constructor(icsEvent: SimpleIcsEvent) : this(
        id = icsEvent.uid ?: UUID.randomUUID().toString(),
        gameNumber = icsEvent.gameNumber ?: "XXXX",
        fieldNumber = icsEvent.fieldNumber,
        homeTeamName = icsEvent.homeTeam ?: "Home",
        awayTeamName = icsEvent.awayTeam ?: "Away",
        refereeAssignment = icsEvent.refereeAssignment,
        venue = icsEvent.location,
        gameDateTimeEpochMillis = icsEvent.dtStart?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
        halfDurationMinutes = icsEvent.ageGroup?.defaultHalfDurationMinutes ?: 45, 
        halftimeDurationMinutes = icsEvent.ageGroup?.defaultHalftimeDurationMinutes ?: 10, 
        ageGroup = icsEvent.ageGroup,
        notes = listOfNotNull(icsEvent.summary, icsEvent.ageGroup?.notes).joinToString(" / ").ifEmpty { null },
        // Other fields will take defaults from the primary constructor via `this(...)` call
    )

    // --- Methods to modify events ---
    fun addEvent(event: GameEvent): Game {
        val updatedEvents = events + event
        return this.copy(events = updatedEvents, lastUpdated = System.currentTimeMillis())
    }

    // In Game.kt
    fun removeEvent(eventToRemove: GameEvent): Game {
        if (!events.contains(eventToRemove)) {
            return this // Event not found
        }
        val updatedEvents = events.filterNot { it == eventToRemove }
        var gameWithEventRemoved = this.copy(events = updatedEvents, lastUpdated = System.currentTimeMillis())

        // Adjust scores/stats based on the type of event removed
        when (eventToRemove) {
            is GoalScoredEvent -> {
                gameWithEventRemoved = gameWithEventRemoved.copy(
                    homeScore = if (eventToRemove.team == Team.HOME) gameWithEventRemoved.homeScore - 1 else gameWithEventRemoved.homeScore,
                    awayScore = if (eventToRemove.team == Team.AWAY) gameWithEventRemoved.awayScore - 1 else gameWithEventRemoved.awayScore
                )
            }
             is PenaltyEvent -> {
                gameWithEventRemoved = gameWithEventRemoved.copy(
                    homeScore = if (eventToRemove.team == Team.HOME && eventToRemove.scored) gameWithEventRemoved.homeScore - 1 else gameWithEventRemoved.homeScore,
                    awayScore = if (eventToRemove.team == Team.AWAY && eventToRemove.scored) gameWithEventRemoved.awayScore - 1 else gameWithEventRemoved.awayScore,
                    penaltiesTakenHome = if (eventToRemove.team == Team.HOME) (gameWithEventRemoved.penaltiesTakenHome - 1).coerceAtLeast(0) else gameWithEventRemoved.penaltiesTakenHome,
                    penaltiesTakenAway = if (eventToRemove.team == Team.AWAY) (gameWithEventRemoved.penaltiesTakenAway - 1).coerceAtLeast(0) else gameWithEventRemoved.penaltiesTakenAway
                )
            }
            else -> {

            }
        }
        return gameWithEventRemoved
    }
    
    fun undoLastEvent(): Game {
        if (events.isEmpty()) return this
        val lastEvent = events.last()
        // First, create a game state with the event removed
        val gameWithEventUndone = this.copy(events = events.dropLast(1), lastUpdated = System.currentTimeMillis())
        gameWithEventUndone.removeEvent(lastEvent)
        return gameWithEventUndone
    }


    // --- Computed Properties for UI ---
    fun regulationPeriodDurationMillis(phase: GamePhase = this.currentPhase): Long {
        return when (phase) {
            GamePhase.FIRST_HALF, GamePhase.SECOND_HALF -> halfDurationMinutes * 60 * 1000L
            GamePhase.EXTRA_TIME_FIRST_HALF, GamePhase.EXTRA_TIME_SECOND_HALF -> extraTimeHalfDurationMinutes * 60 * 1000L
            GamePhase.HALF_TIME -> halftimeDurationMinutes * 60 * 1000L
            GamePhase.EXTRA_TIME_HALF_TIME -> extraTimeHalftimeDurationMinutes * 60 * 1000L
            else -> 0L 
        }
    }
    val addedTimePlayedMillis: Long
        @JvmName("getAddedTimePlayedMillisInternal") 
        get() {
            val regulationDuration = this.regulationPeriodDurationMillis() 
            return if (actualTimeElapsedInPeriodMillis > regulationDuration) {
                actualTimeElapsedInPeriodMillis - regulationDuration
            } else {
                0L
            }
        }
    val isTied: Boolean
        get() = homeScore == awayScore

    val homeTeamColor: Color
        get() = Color(homeTeamColorArgb)

    val awayTeamColor: Color
        get() = Color(awayTeamColorArgb)

    val halfDurationMillis: Long
        get() = halfDurationMinutes * 60 * 1000L

    val halftimeDurationMillis: Long
        get() = halftimeDurationMinutes * 60 * 1000L

    val formattedGameDateTime: String?
        get() = gameDateTimeEpochMillis?.let {
            val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
            sdf.format(Date(it))
        }
}

data class GameSnapshotForStorage(
    val id: String,
    val currentPhase: GamePhase,
    val homeScore: Int,
    val awayScore: Int,
    val events: List<GameEvent>,
    val gameNumber: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val halfDurationMinutes: Int,
    val halftimeDurationMinutes: Int,
    val penaltiesTakenHome: Int,
    val penaltiesTakenAway: Int,
    val refereeAssignment: String?,
    val locationHistoryCount: Int,
    val heartRateHistoryCount: Int,
    val stepHistoryCount: Int
)

fun Game.toSnapshotForStorage(): GameSnapshotForStorage {
    return GameSnapshotForStorage(
        id = this.id,
        currentPhase = this.currentPhase,
        homeScore = this.homeScore,
        awayScore = this.awayScore,
        events = this.events.toList(), 
        gameNumber = this.gameNumber,
        homeTeamName = this.homeTeamName,
        awayTeamName = this.awayTeamName,
        halfDurationMinutes = this.halfDurationMinutes,
        halftimeDurationMinutes = this.halftimeDurationMinutes,
        penaltiesTakenHome = this.penaltiesTakenHome,
        penaltiesTakenAway = this.penaltiesTakenAway,
        refereeAssignment = this.refereeAssignment,
        locationHistoryCount = this.locationHistory.size,
        heartRateHistoryCount = this.heartRateHistory.size,
        stepHistoryCount = this.stepHistory.size
    )
}

fun Game.toFirestoreMap(): Map<String, Any?> {
    val gameData = mutableMapOf<String, Any?>(
        "id" to this.id,
        "userId" to this.userId,
        "lastUpdated" to this.lastUpdated, 
        "halfDurationMinutes" to this.halfDurationMinutes,
        "halftimeDurationMinutes" to this.halftimeDurationMinutes,
        "extraTimeHalfDurationMinutes" to this.extraTimeHalfDurationMinutes,
        "extraTimeHalftimeDurationMinutes" to this.extraTimeHalftimeDurationMinutes,
        "gameNumber" to this.gameNumber,
        "fieldNumber" to this.fieldNumber,
        "homeTeamName" to this.homeTeamName,
        "awayTeamName" to this.awayTeamName,
        "ageGroup" to this.ageGroup?.name, 
        "competition" to this.competition,
        "refereeAssignment" to this.refereeAssignment,
        "venue" to this.venue,
        "gameDateTimeEpochMillis" to this.gameDateTimeEpochMillis, 
        "notes" to this.notes,
        "refereeAssignment" to this.refereeAssignment, // Added field
        "inAddedTime" to this.inAddedTime,
        "hasExtraTime" to this.hasExtraTime,
        "hasPenalties" to this.hasPenalties,
        "homeTeamColorArgb" to this.homeTeamColorArgb,
        "awayTeamColorArgb" to this.awayTeamColorArgb,
        "kickOffTeam" to this.kickOffTeam.name,
        "penaltiesTakenHome" to this.penaltiesTakenHome,
        "penaltiesTakenAway" to this.penaltiesTakenAway,
        "currentPhase" to this.currentPhase.name,
        "homeScore" to this.homeScore,
        "awayScore" to this.awayScore,
        "displayedTimeMillis" to this.displayedTimeMillis,
        "actualTimeElapsedInPeriodMillis" to this.actualTimeElapsedInPeriodMillis,
        "isTimerRunning" to this.isTimerRunning,
        "locationHistory" to this.locationHistory,
        "heartRateHistory" to this.heartRateHistory,
        "stepHistory" to this.stepHistory
    )

    val eventsForFirestore = this.events.mapNotNull { event ->
        try {
            val eventJsonString = AppJsonConfiguration.encodeToString(event)
            val jsonObject = AppJsonConfiguration.parseToJsonElement(eventJsonString).jsonObject
            jsonObjectToMap(jsonObject) 
        } catch (e: Exception) {
            null
        }
    }
    gameData["events"] = eventsForFirestore

    return gameData.filterValues { it != null } // Remove nulls before sending to Firestore
}
