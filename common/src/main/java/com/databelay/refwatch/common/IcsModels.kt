package com.databelay.refwatch.common

import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.regex.Pattern

data class SimpleIcsEvent(
    val uid: String?,
    val summary: String?,
    val description: String?,
    val location: String?,
    val dtStart: LocalDateTime?,
    val dtEnd: LocalDateTime?,
    var homeTeam: String?,
    var awayTeam: String?,
    var ageGroup: AgeGroup?
)

// --- Main ICS Parser Object ---
object SimpleIcsParser {
    private val EVENT_PATTERN: Pattern = Pattern.compile("BEGIN:VEVENT(.*?)END:VEVENT", Pattern.DOTALL)

    // Helper function to unfold ICS content lines
    // (Can be private if only used here, or public if needed elsewhere)
    private fun unfoldIcsLines(foldedString: String): String {
        // Remove CR LF Space, CR LF Tab, CR, and special characters and spaces
        return foldedString.replace(Regex("[\r\t ]"), "")
    }

    fun parse(icsContent: String): List<SimpleIcsEvent> {
        val events = mutableListOf<SimpleIcsEvent>()
        val unfoldedIcs = unfoldIcsLines(icsContent) // Unfold entire content once
        val eventMatcher = EVENT_PATTERN.matcher(unfoldedIcs)

        while (eventMatcher.find()) {
            val eventBlockContent = eventMatcher.group(1)?.trim() ?: continue
            // Use the factory method from SimpleIcsEvent.Companion
            SimpleIcsEventFactory.createFromEventBlock(eventBlockContent)?.let {
                events.add(it)
            }
        }
        return events
    }
}

// Factory object to construct SimpleIcsEvent
object SimpleIcsEventFactory {
    // Patterns and helpers moved from SimpleIcsEvent.Companion for clarity in factory
    private val UID_PATTERN: Pattern = Pattern.compile("^UID:(.+)$", Pattern.MULTILINE)
    private val SUMMARY_PATTERN: Pattern = Pattern.compile("^SUMMARY:(.+)$", Pattern.MULTILINE)
    private val DESCRIPTION_PATTERN: Pattern = Pattern.compile("^DESCRIPTION:(.+)$", Pattern.MULTILINE)
    private val LOCATION_PATTERN: Pattern = Pattern.compile("^LOCATION:(.+)$", Pattern.MULTILINE)
    private val DATETIME_PROPERTY_PATTERN: Pattern = Pattern.compile("^(DTSTART|DTEND)(?:;TZID=([^:]+))?:(\\d{8}T\\d{6})(Z)?$", Pattern.MULTILINE)
    private val ICS_DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val TEAM_BIRTH_YEAR_PATTERN: Pattern = Pattern.compile("\\b(\\d{4})(?:[/\\-]\\d{2,4})?\\b")
    private val TEAM_VS_PATTERN: Pattern = Pattern.compile(
        "^(?:(?:Referee(?: Assignment)?:\\s*(?:Referee|Asst Referee \\d|AR\\d?|REF)?\\s*-\\s*\\d+\\s*)?)(.*?)" +
                "(?:\\s+(?:vs?\\.?|v\\.?|-)\\s+)" +
                "(.*?)" +
                "(?:\\s+-\\s+(?:ISL|GLC)|\\s*\\(|\\s*$)",
        Pattern.CASE_INSENSITIVE
    )

    private fun getPropertyValue(pattern: Pattern, eventBlock: String): String? {
        return pattern.matcher(eventBlock).run { if (find()) group(1)?.trim() else null }
    }

    private fun parseIcsDateTime(dateTimeString: String, tzid: String?, isUtc: Boolean): LocalDateTime? {
        return try {
            val localTime = LocalDateTime.parse(dateTimeString, ICS_DATETIME_FORMATTER)
            when {
                isUtc -> ZonedDateTime.of(localTime, ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                tzid != null -> {
                    try {
                        ZonedDateTime.of(localTime, ZoneId.of(tzid)).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                    } catch (e: Exception) {
                        Log.w("IcsFactory", "Invalid TZID '$tzid', interpreting $dateTimeString as system local. ${e.message}")
                        localTime
                    }
                }
                else -> localTime
            }
        } catch (e: DateTimeParseException) {
            Log.e("IcsFactory","Error parsing ICS date-time: $dateTimeString with tzid=$tzid, isUtc=$isUtc. ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("IcsFactory","Generic error parsing ICS date-time: $dateTimeString. ${e.message}")
            null
        }
    }

    private fun extractBirthYearFromTeamName(teamName: String): Int? {
        val matcher = TEAM_BIRTH_YEAR_PATTERN.matcher(teamName)
        if (matcher.find()) {
            val yearStr = matcher.group(1)
            try {
                val year = yearStr.toInt()
                if (year in 1950..(LocalDate.now().year - 3)) {
                    Log.d("IcsFactory", "Extracted birth year: $year from team: $teamName")
                    return year
                }
            } catch (e: NumberFormatException) { /* Ignore */ }
        }
        return null
    }

    private fun parseTeamsAndDeriveAgeGroup(
        summaryText: String?,
        descriptionText: String?,
        currentYear: Int = LocalDate.now().year
    ): Triple<String?, String?, AgeGroup?> {
        var home: String? = null
        var away: String? = null
        var ageGroup: AgeGroup? = AgeGroup.UNKNOWN
        var birthYear: Int? = null

        if (summaryText != null) {
            val teamMatcher = TEAM_VS_PATTERN.matcher(summaryText)
            if (teamMatcher.find()) {
                home = teamMatcher.group(1)?.trim()?.replaceFirst(Regex("^Referee Assignment:.*?-\\s*\\d+\\s*", RegexOption.IGNORE_CASE), "")?.trim()
                away = teamMatcher.group(2)?.trim()
                birthYear = home?.let { extractBirthYearFromTeamName(it) } ?: away?.let { extractBirthYearFromTeamName(it) }
            } else {
                val teams = summaryText.split(Regex("\\s+(?:vs?\\.?|v\\.?|-)\\s+"), limit = 2)
                if (teams.size == 2) {
                    home = teams[0].replaceFirst(Regex("^Referee Assignment:.*?-\\s*\\d+\\s*", RegexOption.IGNORE_CASE), "").trim()
                    away = teams[1].substringBefore(" - ISL SPRING").substringBefore(" (").trim()
                    birthYear = home?.let { extractBirthYearFromTeamName(it) } ?: away?.let { extractBirthYearFromTeamName(it) }
                }
            }
        }

        if (birthYear != null) {
            val calculatedAge = currentYear - birthYear
            ageGroup = AgeGroup.fromCalculatedAge(calculatedAge)
        } else {
            ageGroup = AgeGroup.fromString(summaryText)
            if (ageGroup == null || ageGroup == AgeGroup.UNKNOWN) {
                ageGroup = AgeGroup.fromString(descriptionText)
            }
        }
        return Triple(home, away, ageGroup ?: AgeGroup.UNKNOWN)
    }

    fun createFromEventBlock(eventBlockContent: String): SimpleIcsEvent? {
        // TODO: doesn't find any patterns
//        val eventBlockContent = unfoldIcsLines(eventBlockContent) // UNFOLD THE BLOCK

        val uid = UID_PATTERN.matcher(eventBlockContent).run { if (find()) group(1)?.trim() else null }
        val summary = SUMMARY_PATTERN.matcher(eventBlockContent).run { if (find()) group(1)?.trim() else null }
        val description = DESCRIPTION_PATTERN.matcher(eventBlockContent).run { if (find()) group(1)?.trim() else null }
        val location = LOCATION_PATTERN.matcher(eventBlockContent).run { if (find()) group(1)?.trim() else null }

        var dtStart: LocalDateTime? = null
        var dtEnd: LocalDateTime? = null
        val dateTimePropertyMatcher = DATETIME_PROPERTY_PATTERN.matcher(eventBlockContent)
        while (dateTimePropertyMatcher.find()) {
            val propertyName = dateTimePropertyMatcher.group(1)
            val tzid = dateTimePropertyMatcher.group(2)
            val dateTimeValue = dateTimePropertyMatcher.group(3)
            val isUtc = dateTimePropertyMatcher.group(4) != null
            val parsedDateTime = parseIcsDateTime(dateTimeValue, tzid, isUtc)
            if (propertyName == "DTSTART") dtStart = parsedDateTime
            else if (propertyName == "DTEND") dtEnd = parsedDateTime
        }

        // If essential date is missing, maybe return null or throw
        if (uid == null || dtStart == null) {
            Log.w("IcsFactory", "Skipping event due to missing UID or DTSTART. UID: $uid, Summary: $summary")
            return null
        }

        val (homeTeam, awayTeam, ageGroup) = parseTeamsAndDeriveAgeGroup(summary, description)

        return SimpleIcsEvent(
            uid = uid,
            summary = summary,
            description = description,
            location = location,
            dtStart = dtStart,
            dtEnd = dtEnd,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            ageGroup = ageGroup
        )
    }

}

/*
package com.databelay.refwatch.common

import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.regex.Pattern

data class SimpleIcsEvent(
    val uid: String?, // Added UID field
    val summary: String?, // Typically "Team A vs Team B"
    val description: String?, // Could contain more details, like referee assignment
    val location: String?, // Field, Venue
    val dtStart: LocalDateTime?,
    val dtEnd: LocalDateTime?,
    var homeTeam: String? = null,
    var awayTeam: String? = null,
    var ageGroup: AgeGroup? = null, // Added AgeGroup field

)

// --- Basic ICS Parser (Very Naive - Use a library for production) ---
object SimpleIcsParser {
    private val EVENT_PATTERN: Pattern = Pattern.compile("BEGIN:VEVENT(.*?)END:VEVENT", Pattern.DOTALL)
    private val UID_PATTERN: Pattern = Pattern.compile("UID:(.+)") // New UID pattern
    private val SUMMARY_PATTERN: Pattern = Pattern.compile("SUMMARY:(.+)")
    private val DESCRIPTION_PATTERN: Pattern = Pattern.compile("DESCRIPTION:(.+)")
    private val LOCATION_PATTERN: Pattern = Pattern.compile("LOCATION:(.+)")
    // Updated DTSTART/DTEND patterns to capture TZID and the datetime value separately
    // Group 1: (Optional) TZID value like "America/New_York"
    // Group 2: The datetime string "20250329T143000"
    // Group 3: (Optional) "Z" for UTC

    fun parse(icsContent: String): List<SimpleIcsEvent> {
        val events = mutableListOf<SimpleIcsEvent>()
        val eventMatcher = EVENT_PATTERN.matcher(icsContent)

        while (eventMatcher.find()) {
            val eventBlockContent = eventMatcher.group(1)?.trim() ?: continue
            val eventBlock = unfoldIcsLines(eventBlockContent) // UNFOLD THE BLOCK

            val uid = UID_PATTERN.matcher(eventBlock).run { if (find()) group(1)?.trim() else null }
            val summary = SUMMARY_PATTERN.matcher(eventBlock).run { if (find()) group(1)?.trim() else null }
            val description = DESCRIPTION_PATTERN.matcher(eventBlock).run { if (find()) group(1)?.trim() else null }
            val location = LOCATION_PATTERN.matcher(eventBlock).run { if (find()) group(1)?.trim() else null }

            // Find all DTSTART/DTEND properties in the block
            val dateTimePropertyMatcher = DATETIME_PROPERTY_PATTERN.matcher(eventBlock)
            var dtStart: LocalDateTime? = null
            var dtEnd: LocalDateTime? = null

            while (dateTimePropertyMatcher.find()) {
                val propertyName = dateTimePropertyMatcher.group(1)
                val tzid = dateTimePropertyMatcher.group(2) // Captured TZID
                val dateTimeValue = dateTimePropertyMatcher.group(3) // The raw datetime numbers
                val isUtc = dateTimePropertyMatcher.group(4) != null // "Z" is present
                val parsedDateTime = parseIcsDateTime(dateTimeValue, tzid, isUtc)
                if (propertyName == "DTSTART") dtStart = parsedDateTime
                else if (propertyName == "DTEND") dtEnd = parsedDateTime
            }
            // Attempt to parse AgeGroup
            // Try summary first, then description if summary doesn't yield a result.
            var parsedAgeGroup = AgeGroup.fromString(summary)
            if (parsedAgeGroup == null) {
                parsedAgeGroup = AgeGroup.fromString(description)
            }

            val event = SimpleIcsEvent(uid, summary, description, location, dtStart, dtEnd, ageGroup = parsedAgeGroup)

            val teams = parseHomeVsAwayTeamFromString(summary)
            event.homeTeam = teams.first
            event.awayTeam = teams.second

            events.add(event)
        }
        return events
    }



    fun parseHomeVsAwayTeamFromString(text: String?): Pair<String?, String?> {
        val TEAM_VS_PATTERN = Pattern.compile(
            // Start of line/string ^
            // Non-greedy capture of everything before the 'vs' (Group 1: Home Team)
            // Optional prefix like "Referee Assignment: ... - \d+ "
            "^(?:(?:Referee Assignment:\\s*(?:Referee|Asst Referee \\d)\\s*-\\s*\\d+\\s*)?)(.*?)" +
                    // The 'vs' part (case insensitive, allowing 'v', 'v.', 'vs', 'vs.')
                    "(?:\\s+(?:vs?\\.?)\\s+)" +
                    // Non-greedy capture of everything after 'vs' (Group 2: Away Team)
                    // up to a common delimiter that signals end of team names
                    "(.*?)" +
                    // Delimiter like " - ISL SPRING" or " (" or end of line
                    "(?:\\s+-\\s+(?:ISL|GLC)|\\s*\\(|\\s*$)",
            Pattern.CASE_INSENSITIVE
        )
        // val cleanSummary = ... (any pre-cleaning if necessary, but the regex tries to handle prefixes)
        val teamMatcher = TEAM_VS_PATTERN.matcher(text) // Use original summary for this regex
        var homeTeam: String? = null
        var awayTeam: String? = null
        if (teamMatcher.find()) {
            homeTeam = teamMatcher.group(1)?.trim() // Group 1 after prefix
            awayTeam = teamMatcher.group(2)?.trim() // Group 2

            // Further cleanup if the prefix was not fully captured by the regex's optional part
            // This is a bit defensive
            homeTeam = homeTeam?.replaceFirst(Regex("^Referee Assignment:.*?-\\s*\\d+\\s*", RegexOption.IGNORE_CASE), "")?.trim()
            awayTeam = awayTeam?.replaceFirst(Regex("^.*?Assignment:.*?-\\s*", RegexOption.IGNORE_CASE),"")?.trim()

            Log.d("IcsParser", "Regex Matched: Home='${homeTeam}', Away='${awayTeam}' from summary: $text")
        } else {
            Log.w("IcsParser", "TEAM_VS_PATTERN did NOT match summary: $text")
            // Fallback to your simpler split as a last resort, though it might be inaccurate
            val teams = text.toString().split(Regex("\\s+(?:vs?\\.?|v\\.?)\\s+"), limit = 2) // Split only on 'vs' variants
            if (teams.size == 2) {
                homeTeam = teams[0].replaceFirst(Regex("^Referee Assignment:.*?-\\s*\\d+\\s*", RegexOption.IGNORE_CASE), "").trim()
                homeTeam = homeTeam?.replaceFirst(Regex("^.*?Assignment:.*?-\\s*", RegexOption.IGNORE_CASE),"")?.trim()
                awayTeam = teams[1].substringBefore(" - ISL SPRING").substringBefore(" (").trim() // Attempt to clean suffix
                Log.d("IcsParser", "Fallback Split: Home='${homeTeam}', Away='${awayTeam}'")
            }
        }
        return Pair(homeTeam, awayTeam)
    }

    private fun parseIcsDateTime(
        dateTimeString: String,
        tzid: String?,
        isUtc: Boolean
    ): LocalDateTime? {
        // ... (implementation from previous step)
        return try {
            val localTime = LocalDateTime.parse(dateTimeString, ICS_DATETIME_FORMATTER)
            when {
                isUtc -> ZonedDateTime.of(localTime, ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                tzid != null -> {
                    try {
                        ZonedDateTime.of(localTime, ZoneId.of(tzid)).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                    } catch (e: Exception) {
                        System.err.println("Warning: Invalid TZID '$tzid', interpreting $dateTimeString as system local.")
                        localTime
                    }
                }
                else -> localTime
            }
        } catch (e: DateTimeParseException) {
            System.err.println("Error parsing ICS date-time: $dateTimeString with tzid=$tzid, isUtc=$isUtc. ${e.message}")
            null
        } catch (e: Exception) {
            System.err.println("Generic error parsing ICS date-time: $dateTimeString. ${e.message}")
            null
        }
    }

    // Make sure ICS_DATETIME_FORMATTER is defined
    private val ICS_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    // And DATETIME_PROPERTY_PATTERN
    private val DATETIME_PROPERTY_PATTERN: Pattern = Pattern.compile("(DTSTART|DTEND)(?:;TZID=([^:]+))?:(\\d{8}T\\d{6})(Z)?")
}*/
