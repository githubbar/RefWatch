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
import java.text.SimpleDateFormat
import java.util.Date

// --- Enums (Ensure these are defined in this file or imported) ---
@Parcelize
enum class Team : Parcelable { HOME, AWAY }
@Parcelize
enum class CardType : Parcelable { YELLOW, RED }
@Parcelize
enum class GamePhase : Parcelable {
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
    return this.name.replace("_", " ").capitalizeWords()
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
@Parcelize // Base class needs it too
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

// --- Other Data Classes (GameState, GameSettings) should also be in this file or imported ---
// --- Game Settings ---
@Parcelize
data class GameSettings(
    // --- Core Game Mechanics Settings ---
    val id: String = UUID.randomUUID().toString(), // Unique ID for these game settings instance
    var homeTeamColorArgb: Int = DefaultHomeJerseyColor.toArgb(),
    var awayTeamColorArgb: Int = DefaultAwayJerseyColor.toArgb(),
    var kickOffTeam: Team = Team.HOME, // Who is designated to kick off (can be changed pre-game)
    var currentPeriodKickOffTeam: Team = kickOffTeam, // Actual team kicking off current period (managed by ViewModel)
    var halfDurationMinutes: Int = 45,
    var halftimeDurationMinutes: Int = 15,
    // var extraTimeHalfDurationMinutes: Int = 15, // Optional for future
    // var extraTimeHalftimeDurationMinutes: Int = 5, // Optional for future

    // --- Match Information (can be pre-filled from a schedule) ---
    var homeTeamName: String = "Home", // Default, can be overridden
    var awayTeamName: String = "Away", // Default, can be overridden
    var ageGroup: String? = null,          // e.g., "U12 Boys", "Adult Men"
    var competition: String? = null,       // e.g., "League Match", "Cup Final"
    var venue: String? = null,             // e.g., "Field 3, West Park"
    var gameDateTimeEpochMillis: Long? = null, // Start date & time of the match, UTC epoch ms

    // --- Internal ID if loaded from a schedule ---
    val scheduledGameId: String? = null // Reference to the GameSettings.id if this was loaded
) : Parcelable {

    // --- Computed Properties for UI ---
    @IgnoredOnParcel
    val homeTeamColor: Color
        get() = Color(homeTeamColorArgb)

    @IgnoredOnParcel
    val awayTeamColor: Color
        get() = Color(awayTeamColorArgb)

    @IgnoredOnParcel
    val halfDurationMillis: Long
        get() = halfDurationMinutes * 60 * 1000L

    @IgnoredOnParcel
    val halftimeDurationMillis: Long
        get() = halftimeDurationMinutes * 60 * 1000L

    // Optional: Formatted date/time string for display
    @IgnoredOnParcel
    val formattedGameDateTime: String?
        get() = gameDateTimeEpochMillis?.let {
            val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
            // Consider device's time zone for display if gameDateTimeEpochMillis is UTC
            // sdf.timeZone = java.util.TimeZone.getDefault() // Example
            sdf.format(Date(it))
        }
}

// --- Game State ---
@Parcelize
data class GameState(
    val settings: GameSettings = GameSettings(),
    var currentPhase: GamePhase = GamePhase.PRE_GAME,
    var homeScore: Int = 0,
    var awayScore: Int = 0,
    var displayedTimeMillis: Long = settings.halfDurationMillis,
    var actualTimeElapsedInPeriodMillis: Long = 0L,
    var isTimerRunning: Boolean = false,
    // List<GameEvent> is Parcelable because GameEvent is Parcelable and List is supported
    val events: List<GameEvent> = emptyList(),
    var kickOffTeamActual: Team = settings.kickOffTeam
) : Parcelable

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
