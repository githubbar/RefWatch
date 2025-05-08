package com.databelay.refwatch.data // Or your package

import androidx.compose.ui.graphics.Color // If GameSettings is in this file
import java.util.UUID
import java.util.Locale // For capitalizeWords if defined here
import java.util.concurrent.TimeUnit // For formatTime

// --- Enums (Ensure these are defined in this file or imported) ---
enum class Team { HOME, AWAY }

enum class CardType { YELLOW, RED }

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
    return this.name.replace("_", " ").capitalizeWords()
}


// --- Game Event Sealed Class and its Subclasses ---
sealed class GameEvent {
    abstract val id: String
    abstract val timestamp: Long // Wall-clock time of event logging
    abstract val gameTimeMillis: Long // Game clock time when event occurred
    abstract val displayString: String // User-friendly string for the log

    data class GoalScoredEvent(
        override val id: String = UUID.randomUUID().toString(),
        val team: Team,
        override val timestamp: Long = System.currentTimeMillis(),
        override val gameTimeMillis: Long, // Game clock time of goal
        val homeScoreAtTime: Int, // Score *after* this goal
        val awayScoreAtTime: Int  // Score *after* this goal
        // val scoringPlayerNumber: Int? = null, // Optional: add if you want to log scorer
    ) : GameEvent() {
        override val displayString: String
            get() = "Goal: ${team.name} ($homeScoreAtTime-$awayScoreAtTime) at ${gameTimeMillis.formatTime()}"
    }

    data class CardIssuedEvent(
        override val id: String = UUID.randomUUID().toString(),
        val team: Team,
        val playerNumber: Int,
        val cardType: CardType,
        override val timestamp: Long = System.currentTimeMillis(),
        override val gameTimeMillis: Long // Game clock time of card
    ) : GameEvent() {
        override val displayString: String
            get() = "${cardType.name.capitalizeWords()} Card: ${team.name}, Player #$playerNumber at ${gameTimeMillis.formatTime()}"
    }

    data class PhaseChangedEvent(
        override val id: String = UUID.randomUUID().toString(),
        val newPhase: GamePhase,
        override val timestamp: Long = System.currentTimeMillis(),
        // gameTimeMillis here represents the game clock reading when this phase *starts*
        // or the duration of the *previous* phase if that's more relevant for the log.
        // The ViewModel's changePhase logic sets this to the duration of the previous phase,
        // or the current actualTimeElapsedInPeriodMillis if ended early.
        override val gameTimeMillis: Long
    ) : GameEvent() {
        override val displayString: String
            // Example: "First Half Started (00:00)" or "Halftime Started (45:00)"
            get() = "${newPhase.readable()} (Clock: ${gameTimeMillis.formatTime()})"
    }

    // For miscellaneous logs like "Timer Paused", "Referee ends period early", "Team X kicks off"
    data class GenericLogEvent(
        override val id: String = UUID.randomUUID().toString(),
        val message: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val gameTimeMillis: Long = 0L // Can be current game time if relevant
    ) : GameEvent() {
        override val displayString: String
            get() {
                val clockInfo = if (gameTimeMillis > 0L &&
                    !message.contains("kicks off", ignoreCase = true) &&
                    !message.startsWith("Game Started", ignoreCase = true) &&
                    !message.contains("Ended", ignoreCase = true) // "Phase Ended" messages might not need clock if PhaseChangedEvent has it
                ) {
                    " (at ${gameTimeMillis.formatTime()})"
                } else {
                    ""
                }
                return "$message$clockInfo"
            }
    }
}

// --- Other Data Classes (GameState, GameSettings) should also be in this file or imported ---

// Example GameSettings (ensure it has properties used by GameViewModel)
data class GameSettings(
    val id: String = UUID.randomUUID().toString(),
    var homeTeamColor: Color = Color.Red,
    var awayTeamColor: Color = Color.Blue,
    var kickOffTeam: Team = Team.HOME, // Who is designated to kick off at the start (e.g., by coin toss)
    var currentPeriodKickOffTeam: Team = kickOffTeam, // Who actually kicks off the current period (changes for 2nd half)
    val halfDurationMinutes: Int = 45,
    val halftimeDurationMinutes: Int = 15
    // Add extra time durations if needed
    // val extraTimeHalfDurationMinutes: Int = 15,
    // val extraTimeHalftimeDurationMinutes: Int = 1
) {
    val halfDurationMillis: Long get() = halfDurationMinutes * 60 * 1000L
    val halftimeDurationMillis: Long get() = halftimeDurationMinutes * 60 * 1000L
    // val extraTimeHalfDurationMillis: Long get() = extraTimeHalfDurationMinutes * 60 * 1000L
    // val extraTimeHalftimeDurationMillis: Long get() = extraTimeHalftimeDurationMinutes * 60 * 1000L
}

// Example GameState (ensure it has properties used by GameViewModel)
data class GameState(
    val settings: GameSettings = GameSettings(),
    var currentPhase: GamePhase = GamePhase.PRE_GAME,
    var homeScore: Int = 0,
    var awayScore: Int = 0,
    var displayedTimeMillis: Long = settings.halfDurationMillis, // Time shown on the countdown timer
    var actualTimeElapsedInPeriodMillis: Long = 0L, // Actual accumulated time for the current phase
    var isTimerRunning: Boolean = false,
    val events: List<GameEvent> = emptyList(), // Use immutable list, update with `+` operator
    var kickOffTeamActual: Team = settings.kickOffTeam // Tracks who kicked off first half for 2nd half logic
    // currentTimerStartTimeMillis might not be needed if actualTimeElapsed... is managed correctly
)

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
