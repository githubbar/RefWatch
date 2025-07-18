package com.databelay.refwatch.common // Or your package

import android.os.Parcelable
import androidx.compose.ui.graphics.Color // If GameSettings is in this file
import androidx.compose.ui.graphics.toArgb
import java.util.UUID
import java.util.Locale // For capitalizeWords if defined here
import java.util.concurrent.TimeUnit // For formatTime
import com.databelay.refwatch.common.theme.*
import com.google.firebase.firestore.Exclude
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient // Import for non-serializable fields
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.IgnoredOnParcel

// --- Enums (Ensure these are defined in this file or imported) ---
@Serializable
enum class Team { HOME, AWAY }

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
    const val NEW_AD_HOC_GAME_PATH = "/new_ad_hoc_game"
    const val NEW_GAME_PAYLOAD_KEY = "new_game_json"
}
