package com.databelay.refwatch.common // Or your package

import androidx.compose.ui.graphics.Color // If GameSettings is in this file
import com.databelay.refwatch.common.Team.AWAY
import com.databelay.refwatch.common.Team.HOME
import java.util.Locale // For capitalizeWords if defined here
import java.util.concurrent.TimeUnit // For formatTime
import kotlinx.serialization.Serializable

// --- Enums (Ensure these are defined in this file or imported) ---
@Serializable
enum class Team {
    HOME,
    AWAY
}
fun Team.opposite(): Team {
    return if (this == HOME) AWAY else HOME
}
@Serializable
enum class CardType { YELLOW, RED }

@Serializable
enum class GameStatus {
    SCHEDULED,
    IN_PROGRESS, // Can be added for more clarity
    COMPLETED,
    CANCELLED    // Easy to add later
}

@Serializable
enum class GamePhase {
    PRE_GAME,

    // For first half
    KICK_OFF_SELECTION_FIRST_HALF,
    FIRST_HALF,
    HALF_TIME,
    // For second half
    SECOND_HALF,

    // For Extra Time
    KICK_OFF_SELECTION_EXTRA_TIME,
    EXTRA_TIME_FIRST_HALF,
    EXTRA_TIME_HALF_TIME,
    EXTRA_TIME_SECOND_HALF,


    // For Penalties
    KICK_OFF_SELECTION_PENALTIES,
    PENALTIES,
    GAME_ENDED,
    // Terminal states
    ABANDONED;
}

fun GamePhase.shouldTimerAutostart(): Boolean {
    return when (this) {
        GamePhase.HALF_TIME, GamePhase.EXTRA_TIME_HALF_TIME -> true // Breaks usually auto-start
        // Add other phases that should always auto-start their timers
        // GamePhase.FIRST_HALF -> true // If you want the first half to start immediately after KICK_OFF_SELECTION
        else -> false
    }
}
// --- Helper Extension Functions (Place here or in a utils.kt file) ---
fun Long.formatTime(): String {
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
        GamePhase.GAME_ENDED -> "Full Time"
        GamePhase.PRE_GAME -> "Pre Game"
        else -> this.name.replace("_", " ").capitalizeWords()
    }
}

// Helper extensions for GamePhase (can also be in a utils.kt file)
fun GamePhase.hasTimer(): Boolean {
    return this == GamePhase.FIRST_HALF ||
            this == GamePhase.SECOND_HALF ||
            this == GamePhase.HALF_TIME ||
            this == GamePhase.EXTRA_TIME_FIRST_HALF ||
            this == GamePhase.EXTRA_TIME_SECOND_HALF ||
            this == GamePhase.EXTRA_TIME_HALF_TIME
}

//
fun GamePhase.isBreak(): Boolean {
    return this == GamePhase.HALF_TIME ||
            this == GamePhase.EXTRA_TIME_HALF_TIME
}

fun GamePhase.isKickoffSelection(): Boolean {
    return this == GamePhase.KICK_OFF_SELECTION_FIRST_HALF ||
            this == GamePhase.KICK_OFF_SELECTION_EXTRA_TIME ||
            this == GamePhase.KICK_OFF_SELECTION_PENALTIES
}

fun GamePhase.isPlayablePhase(): Boolean { // Phases where goals/cards can be recorded
    return this == GamePhase.FIRST_HALF ||
            this == GamePhase.SECOND_HALF ||
            this == GamePhase.EXTRA_TIME_FIRST_HALF ||
            this == GamePhase.EXTRA_TIME_SECOND_HALF ||
            this == GamePhase.PENALTIES
}

fun GamePhase.hasKickoff(): Boolean { // Phases where kick of starts the phase
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


// From your common module or defined consistently
object WearSyncConstants {
    const val PHONE_APP_CAPABILITY = "phone_app_capability"
    const val GAMES_LIST_PATH = "/games_list_all"
    const val GAME_SETTINGS_KEY = "games_json"
    const val GAME_UPDATE_FROM_WATCH_PATH_PREFIX = "/game_update_from_watch"
    const val GAME_UPDATE_PAYLOAD_KEY = "game_update_json"
    const val NEW_AD_HOC_GAME_PATH = "/new_ad_hoc_game"
    const val NEW_GAME_PAYLOAD_KEY = "new_game_json"
}
