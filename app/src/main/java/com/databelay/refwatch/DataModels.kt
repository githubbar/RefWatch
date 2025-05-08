package com.databelay.refwatch

import androidx.compose.ui.graphics.Color
import java.util.UUID

// Enums
enum class Team { HOME, AWAY }
enum class CardType { YELLOW, RED }
enum class GamePhase {
    PRE_GAME,
    FIRST_HALF,
    HALF_TIME,
    SECOND_HALF,
    // Optional future states
    // EXTRA_TIME_FIRST_HALF,
    // EXTRA_TIME_HALF_TIME,
    // EXTRA_TIME_SECOND_HALF,
    FULL_TIME
}

// Event base interface (optional, but good for polymorphism in event list)
interface GameEvent {
    val id: String // Unique ID for each event
    val timestamp: Long // Wall clock time of the event
    val gameTimeMillis: Long // Game clock time when event occurred (elapsed time in current period)
    val displayString: String // How the event is shown in the log
}

// Data Classes
data class GameSettings(
    val halfDurationMinutes: Int = 45,
    val halftimeDurationMinutes: Int = 15,
    var homeTeamColor: Color = Color(0xFFE53935), // Default Red
    var awayTeamColor: Color = Color(0xFF1E88E5), // Default Blue
    var kickOffTeam: Team = Team.HOME,
    var currentPeriodKickOffTeam: Team = kickOffTeam // Tracks who kicks off the current period (1st, 2nd half etc.)
) {
    val halfDurationMillis: Long get() = halfDurationMinutes * 60 * 1000L
    val halftimeDurationMillis: Long get() = halftimeDurationMinutes * 60 * 1000L
}

data class GoalScoredEvent(
    override val id: String = UUID.randomUUID().toString(),
    val team: Team,
    override val timestamp: Long = System.currentTimeMillis(),
    override val gameTimeMillis: Long,
    val homeScoreAtTime: Int,
    val awayScoreAtTime: Int
) : GameEvent {
    override val displayString: String
        get() = "Goal: ${team.name} ($homeScoreAtTime-$awayScoreAtTime) at ${gameTimeMillis.formatTime()}"
}

data class CardIssuedEvent(
    override val id: String = UUID.randomUUID().toString(),
    val team: Team,
    val playerNumber: Int,
    val cardType: CardType,
    override val timestamp: Long = System.currentTimeMillis(),
    override val gameTimeMillis: Long
) : GameEvent {
    override val displayString: String
        get() = "${cardType.name} Card: ${team.name}, Player #$playerNumber at ${gameTimeMillis.formatTime()}"
}

data class PhaseChangedEvent(
    override val id: String = UUID.randomUUID().toString(),
    val newPhase: GamePhase,
    override val timestamp: Long = System.currentTimeMillis(),
    override val gameTimeMillis: Long = 0L // Typically 0 for phase start, or duration for phase end
) : GameEvent {
    override val displayString: String
        get() = "${newPhase.name.replace("_", " ")} Started"
}


data class GameState(
    val settings: GameSettings = GameSettings(),
    var currentPhase: GamePhase = GamePhase.PRE_GAME,
    var homeScore: Int = 0,
    var awayScore: Int = 0,
    var displayedTimeMillis: Long = settings.halfDurationMillis, // Time shown on clock, counts down
    var actualTimeElapsedInPeriodMillis: Long = 0L, // Actual time played in current period, counts up for logging
    var isTimerRunning: Boolean = false,
    val events: List<GameEvent> = emptyList()
)

// Helper extension function for formatting time (add to this file or a utils file)
fun Long.formatTime(): String {
    if (this < 0) return "00:00" // Handle potential negative values gracefully
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}