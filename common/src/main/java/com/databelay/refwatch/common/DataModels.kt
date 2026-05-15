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

fun shortName(name: String, maxLength: Int = 10): String {
    if (name.length <= maxLength) {
        return name
    }

    val words = name.split(' ').filter { it.isNotBlank() }

    val candidateName = when {
        words.isEmpty() -> "" // Should ideally not happen for team names
        words.size == 1 -> words[0]
        else -> "${words[0]} ${words[1]}" // First two words
    }

    return candidateName.take(maxLength)
}

@Serializable
enum class CardType { YELLOW, RED }

@Serializable
enum class GameStatus {
    SCHEDULED,
    COMPLETED,
    IN_PROGRESS, // Can be added for more clarity
}


@Serializable
enum class GamePhase {
    NOT_STARTED,
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
}

// --- Helper Extension Functions (Place here or in a utils.kt file) ---
fun Long.formatTime(isInAddedTime: Boolean = false): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)+ if (isInAddedTime) {" (Added Time)" } else {""}
}

fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
    word.lowercase(Locale.getDefault()).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
}


// Helper extension function, consider moving to common GamePhase related file
fun GamePhase.needsKickOffSelection(): Boolean {
    return this == GamePhase.FIRST_HALF ||
            this == GamePhase.EXTRA_TIME_FIRST_HALF ||
            this == GamePhase.PENALTIES
}

fun GamePhase.needsKickOff(): Boolean {
    return this == GamePhase.FIRST_HALF || this == GamePhase.SECOND_HALF ||
            this == GamePhase.EXTRA_TIME_FIRST_HALF || this == GamePhase.EXTRA_TIME_SECOND_HALF ||
            this == GamePhase.PENALTIES
}

fun GamePhase.isKickOffSelectionPhase(): Boolean { // More precise than needsKickOffSelection
    return this == GamePhase.KICK_OFF_SELECTION_FIRST_HALF ||
            this == GamePhase.KICK_OFF_SELECTION_EXTRA_TIME ||
            this == GamePhase.KICK_OFF_SELECTION_PENALTIES
}

fun GamePhase.usesHalfDuration(): Boolean {
    return this == GamePhase.FIRST_HALF || this == GamePhase.SECOND_HALF ||
            this == GamePhase.EXTRA_TIME_FIRST_HALF || this == GamePhase.EXTRA_TIME_SECOND_HALF ||
            this == GamePhase.PRE_GAME // Allow setting duration in pre-game
}

fun GamePhase.readable(): String {
    return when (this) {
        GamePhase.FIRST_HALF -> "1st Half"
        GamePhase.HALF_TIME -> "Halftime"
        GamePhase.SECOND_HALF -> "2nd Half"
        GamePhase.GAME_ENDED -> "Full Time"
        GamePhase.PRE_GAME -> "Pre Game"
        GamePhase.KICK_OFF_SELECTION_FIRST_HALF -> "Kick-off Selection"
        GamePhase.KICK_OFF_SELECTION_EXTRA_TIME -> "ET Kick-off Selection"
        GamePhase.KICK_OFF_SELECTION_PENALTIES -> "Penalties Kick-off Selection"
        GamePhase.EXTRA_TIME_FIRST_HALF -> "1st Half (ET)"
        GamePhase.EXTRA_TIME_HALF_TIME -> "Halftime (ET)"
        GamePhase.EXTRA_TIME_SECOND_HALF -> "2nd Half (ET)"
        GamePhase.PENALTIES -> "Penalties"
        GamePhase.NOT_STARTED -> "Not Started"
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

fun GamePhase.canHaveAddedTime(): Boolean {
    return this == GamePhase.FIRST_HALF ||
            this == GamePhase.SECOND_HALF ||
            this == GamePhase.EXTRA_TIME_FIRST_HALF ||
            this == GamePhase.EXTRA_TIME_SECOND_HALF
}

fun GamePhase.status(): GameStatus {
    return when (this) {
        GamePhase.NOT_STARTED -> GameStatus.SCHEDULED
        GamePhase.PRE_GAME,
        GamePhase.KICK_OFF_SELECTION_FIRST_HALF,
        GamePhase.FIRST_HALF,
        GamePhase.HALF_TIME,
        GamePhase.SECOND_HALF,
        GamePhase.KICK_OFF_SELECTION_EXTRA_TIME,
        GamePhase.EXTRA_TIME_FIRST_HALF,
        GamePhase.EXTRA_TIME_HALF_TIME,
        GamePhase.EXTRA_TIME_SECOND_HALF,
        GamePhase.KICK_OFF_SELECTION_PENALTIES,
        GamePhase.PENALTIES -> GameStatus.IN_PROGRESS
        GamePhase.GAME_ENDED -> GameStatus.COMPLETED
    }
}
//
fun GamePhase.isBreak(): Boolean {
    return this == GamePhase.HALF_TIME ||
            this == GamePhase.EXTRA_TIME_HALF_TIME
}

fun GamePhase.isPlayablePhase(): Boolean { // Phases where goals/cards can be recorded
    return this == GamePhase.FIRST_HALF ||
            this == GamePhase.SECOND_HALF ||
            this == GamePhase.EXTRA_TIME_FIRST_HALF ||
            this == GamePhase.EXTRA_TIME_SECOND_HALF ||
            this == GamePhase.PENALTIES
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
    const val PATH_PHONE_USER_ID = "/phone_user_id" // Must match watch
    const val KEY_USER_ID = "userId"             // Must match watch
    const val KEY_CUSTOM_AUTH_TOKEN = "customAuthToken" // Must match watch
    const val PATH_GAMES_LIST = "/games_list_all"
    const val KEY_GAMES_JSON = "games_json"
    const val PATH_GAME_UPDATE = "/game_update_from_watch"
    const val KEY_GAME_UPDATE = "game_update_json"
    const val KEY_DATA_FETCH_STATUS = "data_fetch_status"
    const val KEY_GAMES_LIST_CACHE = "games_cache_json"
    const val PHONE_APP_CAPABILITY = "phone_app_capability"
    const val NEW_ADHOC_GAME_FROM_WATCH_PATH_PREFIX = "/new_adhoc_game_from_watch"

}
