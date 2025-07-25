package com.databelay.refwatch.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.databelay.refwatch.common.theme.DefaultAwayJerseyColor
import com.databelay.refwatch.common.theme.DefaultHomeJerseyColor
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.google.firebase.firestore.Exclude

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
    var status: GameStatus = GameStatus.SCHEDULED,
    var currentPhase: GamePhase = GamePhase.PRE_GAME,
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