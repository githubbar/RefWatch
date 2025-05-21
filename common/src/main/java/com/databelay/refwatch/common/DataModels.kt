package com.databelay.refwatch.common // Or your package

import androidx.compose.ui.graphics.Color // If GameSettings is in this file
import android.os.Parcelable // Import Parcelable
import androidx.compose.ui.graphics.toArgb
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.IgnoredOnParcel
import java.util.UUID
import java.util.Locale // For capitalizeWords if defined here
import java.util.concurrent.TimeUnit // For formatTime
import com.databelay.refwatch.common.theme.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient // Import for non-serializable fields
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date

// --- Enums (Ensure these are defined in this file or imported) ---
@Serializable
enum class Team { HOME, AWAY }

@Serializable
enum class CardType { YELLOW, RED }

@Serializable
enum class GamePhase {
    PRE_GAME,
    FIRST_HALF,
    HALF_TIME,
    SECOND_HALF,
    FULL_TIME,
    EXTRA_TIME_FIRST_HALF, // Optional
    EXTRA_TIME_SECOND_HALF, // Optional
    EXTRA_TIME_HALF_TIME // Optional
    // Add more as needed (e.g., PENALTIES)
}

// --- Helper Extension Functions (Place here or in a utils.kt file) ---
fun Long.formatTime(): String {
    if (this < 0L) return "00:00" // Handle invalid or uninitialized times
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
    word.lowercase(Locale.getDefault()).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
}

fun GamePhase.readable(): String {
    return when (this) {
        GamePhase.FIRST_HALF -> "1st Half"
        GamePhase.HALF_TIME -> "Halftime"
        GamePhase.SECOND_HALF -> "2nd Half"
        GamePhase.FULL_TIME -> "Full Time"
        GamePhase.PRE_GAME -> "Pre Game"
        else -> this.name.replace("_", " ").capitalizeWords()
    }
}

// Helper extensions for GamePhase (can also be in a utils.kt file)
fun GamePhase.hasDuration(): Boolean {
    return this == GamePhase.FIRST_HALF ||
            this == GamePhase.SECOND_HALF ||
            this == GamePhase.HALF_TIME ||
            this == GamePhase.EXTRA_TIME_FIRST_HALF ||
            this == GamePhase.EXTRA_TIME_SECOND_HALF ||
            this == GamePhase.EXTRA_TIME_HALF_TIME
}

fun GamePhase.isPlayablePhase(): Boolean { // Phases where goals/cards can be recorded
    return this == GamePhase.FIRST_HALF ||
            this == GamePhase.SECOND_HALF ||
            this == GamePhase.EXTRA_TIME_FIRST_HALF ||
            this == GamePhase.EXTRA_TIME_SECOND_HALF
}

// --- Game Event Sealed Class and its Subclasses ---
@Serializable
sealed class GameEvent : Parcelable {
    abstract val id: String
    abstract val timestamp: Long // Wall-clock time of event logging
    abstract val gameTimeMillis: Long // Game clock time when event occurred
    abstract val displayString: String // User-friendly string for the log

    @Parcelize
    data class GoalScoredEvent(
        override val id: String = UUID.randomUUID().toString(),
        val team: Team,
        override val timestamp: Long = System.currentTimeMillis(),
        override val gameTimeMillis: Long,
        val homeScoreAtTime: Int,
        val awayScoreAtTime: Int
    ) : GameEvent() {
        @IgnoredOnParcel // displayString getter doesn't need to be parcelled
        override val displayString: String
            get() = "Goal: ${team.name} ($homeScoreAtTime-$awayScoreAtTime) at ${gameTimeMillis.formatTime()}"
    }

    @Parcelize
    data class CardIssuedEvent(
        override val id: String = UUID.randomUUID().toString(),
        val team: Team,
        val playerNumber: Int,
        val cardType: CardType,
        override val timestamp: Long = System.currentTimeMillis(),
        override val gameTimeMillis: Long
    ) : GameEvent() {
        @IgnoredOnParcel
        override val displayString: String
            get() = "${cardType.name.capitalizeWords()} Card: ${team.name}, Player #$playerNumber at ${gameTimeMillis.formatTime()}"
    }

    @Parcelize
    data class PhaseChangedEvent(
        override val id: String = UUID.randomUUID().toString(),
        val newPhase: GamePhase,
        override val timestamp: Long = System.currentTimeMillis(),
        override val gameTimeMillis: Long
    ) : GameEvent() {
        @IgnoredOnParcel
        override val displayString: String
            get() = "${newPhase.readable()} (Clock: ${gameTimeMillis.formatTime()})"
    }

    // For miscellaneous logs like "Timer Paused", "Referee ends period early", "Team X kicks off"
    @Parcelize
    data class GenericLogEvent(
        override val id: String = UUID.randomUUID().toString(),
        val message: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val gameTimeMillis: Long = 0L
    ) : GameEvent() {
        @IgnoredOnParcel
        override val displayString: String
            get() { /* ... build string ... */ return message } // Simplified example
    }
}

// --- Game Settings ---
@Serializable // Add this
data class Game(
    // --- Core Game Mechanics Settings ---
    val id: String = UUID.randomUUID().toString(), // Unique ID for these game settings instance
    var lastUpdated: Long = System.currentTimeMillis(), // Timestamp for when this was last updated by user
    var halfDurationMinutes: Int = 45,
    var halftimeDurationMinutes: Int = 15,
    // var extraTimeHalfDurationMinutes: Int = 15, // Optional for future
    // var extraTimeHalftimeDurationMinutes: Int = 5, // Optional for future

    // --- Match Information (can be pre-filled from a schedule) ---
    var homeTeamName: String = "Home", // Default, can be overridden
    var awayTeamName: String = "Away", // Default, can be overridden
    var ageGroup: AgeGroup? = null,          // e.g., "U12 Boys", "Adult Men"
    var competition: String? = null,       // e.g., "League Match", "Cup Final"
    var venue: String? = null,             // e.g., "Field 3, West Park"
    var gameDateTimeEpochMillis: Long? = null, // Start date & time of the match, UTC epoch ms
    var notes: String? = null,

    // Live State Fields (updated by watch, synced via phone to Firebase)
    var homeTeamColorArgb: Int = DefaultHomeJerseyColor.toArgb(),
    var awayTeamColorArgb: Int = DefaultAwayJerseyColor.toArgb(),
    var kickOffTeam: Team = Team.HOME, // Who is designated to kick off (can be changed pre-game)
    var currentPeriodKickOffTeam: Team = kickOffTeam, // Actual team kicking off current period (managed by ViewModel)
    var currentPhase: GamePhase = GamePhase.PRE_GAME,
    var homeScore: Int = 0,
    var awayScore: Int = 0,
    var displayedTimeMillis: Long = 45,
    var actualTimeElapsedInPeriodMillis: Long = 0L,
    var isTimerRunning: Boolean = false,
    // List<GameEvent> is Parcelable because GameEvent is Parcelable and List is supported
    val events: List<GameEvent> = emptyList(),
)  {
    // Constructor to initialize from SimpleIcsEvent
    constructor(icsEvent: SimpleIcsEvent) : this(
        id = icsEvent.uid ?: UUID.randomUUID().toString(), // Assign if uid is not null, otherwise generate
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
            val parts = mutableListOf<String>()

            // Team names
            val teamsPart = if (homeTeamName.isNotBlank() && homeTeamName != "Home" || awayTeamName.isNotBlank() && awayTeamName != "Away") {
                "${homeTeamName.trim()} vs ${awayTeamName.trim()}"
            } else {
                "Game" // Fallback if team names are default/empty
            }
            parts.add(teamsPart)

            // Age Group
            ageGroup?.displayName?.let {
                if (it.isNotBlank() && it.lowercase() != "unknown") {
                    parts.add("($it)")
                }
            }

            // Competition (if available and different from age group)
            competition?.let {
                if (it.isNotBlank() && it.lowercase() != ageGroup?.displayName?.lowercase()) {
                    parts.add("- $it")
                }
            }

            // Date/Time (optional, can make the summary long)
            // formattedGameDateTime?.let { parts.add("on $it") }

            // Venue (optional)
            // venue?.let { if (it.isNotBlank()) parts.add("@ $it") }


            var summary = parts.joinToString(" ")

            // If the summary is just "Game", try to use date or ID as a fallback
            if (summary == "Game") {
                formattedGameDateTime?.let {
                    summary = "Game on $it"
                } ?: run {
                    summary = "Game ID: ${id.substring(0, 8)}" // Shortened ID
                }
            }
            return summary
        }
}

// vv DEFINE IT HERE vv
val predefinedColors: List<Color> = listOf(
    Color.Red,
    Color(0xFFFFA500), // Orange
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color(0xFF800080), // Purple
    Color.Magenta,
    Color.Black,
    Color.White,
    Color.Gray,
    Color.DarkGray
    // Add or remove colors as you see fit
)

// Helper function for luminance (can also be here or in a utils file)
fun Color.luminance(): Float {
    val red = this.red
    val green = this.green
    val blue = this.blue
    return (0.2126f * red + 0.7152f * green + 0.0722f * blue)
}


// From your common module or defined consistently
object WearSyncConstants {
    const val GAMES_LIST_PATH = "/games_list_all"
    const val GAME_SETTINGS_KEY = "games_json"
    const val GAME_UPDATE_FROM_WATCH_PATH_PREFIX = "/game_update_from_watch"
    const val GAME_UPDATE_PAYLOAD_KEY = "game_update_json"
}
