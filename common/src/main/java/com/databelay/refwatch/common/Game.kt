package com.databelay.refwatch.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.databelay.refwatch.common.SimpleIcsEvent
import com.databelay.refwatch.common.GameEvent
import com.databelay.refwatch.common.theme.DefaultAwayJerseyColor
import com.databelay.refwatch.common.theme.DefaultHomeJerseyColor
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.google.firebase.firestore.Exclude
import kotlin.Int

// --- Game Settings ---
@Serializable // Add this
data class Game(
    // --- Core Game Mechanics Settings ---
    val id: String = UUID.randomUUID().toString(), // Unique ID for these game settings instance
    var lastUpdated: Long = System.currentTimeMillis(), // Timestamp for when this was last updated by user
    var halfDurationMinutes: Int = 45,
    var halftimeDurationMinutes: Int = 15,
    var extraTimeHalfDurationMinutes: Int = 15, // Optional for future
    var extraTimeHalftimeDurationMinutes: Int = 1, // Optional for future
//
    // --- Match Information (can be pre-filled from a schedule) ---
    var gameNumber: String = "XXXX", // Default, can be overridden
    var homeTeamName: String = "Home", // Default, can be overridden
    var awayTeamName: String = "Away", // Default, can be overridden
    var ageGroup: AgeGroup? = null,          // e.g., "U12 Boys", "Adult Men"
    var competition: String? = null,       // e.g., "League Match", "Cup Final"
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
    var status: GameStatus = GameStatus.SCHEDULED,
    var currentPhase: GamePhase = GamePhase.NOT_STARTED,
    var homeScore: Int = 0,
    var awayScore: Int = 0,
    var displayedTimeMillis: Long = 45,
    var actualTimeElapsedInPeriodMillis: Long = 0L,
    var isTimerRunning: Boolean = false,
    // Tell Firestore to ignore this field during automatic toObject() mapping.
    // We will populate it manually.
    @get:Exclude // Crucial for Firestore to ignore this field during toObject()
    val events: List<GameEvent> = emptyList()
)  {
    companion object {
        fun defaults(): Game {
            val game = Game(
                id = UUID.randomUUID().toString(), // Or a default placeholder ID
                homeTeamName = "Home",
                awayTeamName = "Away",
                homeTeamColorArgb = DefaultHomeJerseyColor.toArgb(),
                awayTeamColorArgb = DefaultAwayJerseyColor.toArgb(),
                // Other fields will use their default values from the primary constructor
            )
            // Initialize other fields if their defaults in the primary constructor are not sufficient
            // or if you want specific values for the "defaults" case.
            game.lastUpdated = System.currentTimeMillis()
            game.halfDurationMinutes = 45
            game.halftimeDurationMinutes = 15
            game.extraTimeHalfDurationMinutes = 15
            game.extraTimeHalftimeDurationMinutes = 1
            game.ageGroup = null
            game.competition = null
            game.venue = null
            game.gameDateTimeEpochMillis = null
            game.notes = null
            game.hasExtraTime = false
            game.hasPenalties = false
            game.kickOffTeam = Team.HOME
            game.penaltiesTakenHome = 0
            game.penaltiesTakenAway = 0
            game.status = GameStatus.SCHEDULED
            game.currentPhase = GamePhase.PRE_GAME
            // displayedTimeMillis, actualTimeElapsedInPeriodMillis, isTimerRunning already defaulted
            return game
        }
    }
    // Constructor to initialize from SimpleIcsEvent
    constructor(icsEvent: SimpleIcsEvent) : this(
        id = icsEvent.uid ?: UUID.randomUUID().toString(), // Assign if uid is not null, otherwise generate
        gameNumber = icsEvent.gameNumber ?: "XXXX",
        homeTeamName = icsEvent.homeTeam ?: "Home",
        awayTeamName = icsEvent.awayTeam ?: "Away",
        venue = icsEvent.location,
        gameDateTimeEpochMillis = icsEvent.dtStart?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
        // Initialize durations and notes based on parsed AgeGroup
        halfDurationMinutes = icsEvent.ageGroup?.defaultHalfDurationMinutes ?: 45, // Fallback to 45
        halftimeDurationMinutes = icsEvent.ageGroup?.defaultHalftimeDurationMinutes ?: 10, // Fallback to 10 (as per your rule)
        ageGroup = icsEvent.ageGroup,
        // You could combine ICS notes with age group notes:
        notes = listOfNotNull(icsEvent.description, icsEvent.ageGroup?.notes).joinToString(" / ").ifEmpty { null }
        // competition might be part of description or summary - needs more complex parsing or manual entry
    )
    constructor() : this(
        id = java.util.UUID.randomUUID().toString(), // Ensure id is always initialized
        // other fields with defaults
    )
    // --- Computed Properties for UI ---
    // Example: If GameSettings was embedded or properties are direct
    // For this to work, ensure halfDurationMinutes, extraTimeHalfDurationMinutes are properties of Game
    fun regulationPeriodDurationMillis(phase: GamePhase = this.currentPhase): Long {
        return when (phase) {
            GamePhase.FIRST_HALF, GamePhase.SECOND_HALF -> halfDurationMinutes * 60 * 1000L
            GamePhase.EXTRA_TIME_FIRST_HALF, GamePhase.EXTRA_TIME_SECOND_HALF -> extraTimeHalfDurationMinutes * 60 * 1000L
            GamePhase.HALF_TIME -> halftimeDurationMinutes * 60 * 1000L
            GamePhase.EXTRA_TIME_HALF_TIME -> extraTimeHalftimeDurationMinutes * 60 * 1000L
            else -> 0L // Other phases don't have a "playable" regulation duration
        }
    }
    val addedTimePlayedMillis: Long
        @JvmName("getAddedTimePlayedMillisInternal") // Optional: For Java interop if needed
        get() {
            val regulationDuration = this.regulationPeriodDurationMillis() // Access the property
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


    // Optional: Formatted date/time string for display
    val formattedGameDateTime: String?
        get() = gameDateTimeEpochMillis?.let {
            val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
            // Consider device's time zone for display if gameDateTimeEpochMillis is UTC
            // sdf.timeZone = java.util.TimeZone.getDefault() // Example
            sdf.format(Date(it))
        }
    val summary: String
        get() {
            // ... (your existing summary logic)
            val parts = mutableListOf<String>()
            val teamsPart = if (homeTeamName.isNotBlank() && homeTeamName != "Home" || awayTeamName.isNotBlank() && awayTeamName != "Away") {
                "${homeTeamName.trim()} vs ${awayTeamName.trim()}"
            } else {
                "Game"
            }
            parts.add(teamsPart)
            ageGroup?.displayName?.let { if (it.isNotBlank() && it.lowercase() != "unknown") parts.add("($it)") }
            competition?.let { if (it.isNotBlank() && it.lowercase() != ageGroup?.displayName?.lowercase()) parts.add("- $it") }
            var summaryText = parts.joinToString(" ")
            if (summaryText == "Game") {
                formattedGameDateTime?.let { summaryText = "Game on $it" } ?: run { summaryText = "Game ID: ${id.substring(0, 8)}" }
            }
            return summaryText
        }
}