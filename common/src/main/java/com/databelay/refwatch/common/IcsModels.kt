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
) {
    // Companion object to hold parsing utilities and patterns specific to an event block
    companion object {
        private val UID_PATTERN: Pattern = Pattern.compile("^UID:(.+)$", Pattern.MULTILINE)
        private val SUMMARY_PATTERN: Pattern = Pattern.compile("^SUMMARY:(.+)$", Pattern.MULTILINE)
        private val DESCRIPTION_PATTERN: Pattern = Pattern.compile("^DESCRIPTION:(.+)$", Pattern.MULTILINE)
        private val LOCATION_PATTERN: Pattern = Pattern.compile("^LOCATION:(.+)$", Pattern.MULTILINE)
        private val DATETIME_PROPERTY_PATTERN: Pattern = Pattern.compile("^(DTSTART|DTEND)(?:;TZID=([^:]+))?:(\\d{8}T\\d{6})(Z)?$", Pattern.MULTILINE)
        private val ICS_DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

        // Regex to find birth years like 2009, 2009/10, 2010B, etc.
        // Captures the first 4-digit year.
        private val TEAM_BIRTH_YEAR_PATTERN: Pattern = Pattern.compile(
            "\\b(\\d{4})(?:[/\\-]\\d{2,4})?\\b" // \b for word boundary, captures "2009" from "2009/10" or "2009-10"
        )


        // Moved TEAM_VS_PATTERN here as it's used for parsing within the constructor
        private val TEAM_VS_PATTERN: Pattern = Pattern.compile(
            "^(?:(?:Referee(?: Assignment)?:\\s*(?:Referee|Asst Referee \\d|AR\\d?|REF)?\\s*-\\s*\\d+\\s*)?)(.*?)" +
                    "(?:\\s+(?:vs?\\.?|v\\.?|-)\\s+)" + // Added '-' as a team separator
                    "(.*?)" +
                    "(?:\\s+-\\s+(?:ISL|GLC)|\\s*\\(|\\s*$)",
            Pattern.CASE_INSENSITIVE
        )


        private fun parseIcsDateTime(
            dateTimeString: String,
            tzid: String?,
            isUtc: Boolean
        ): LocalDateTime? {
            return try {
                val localTime = LocalDateTime.parse(dateTimeString, ICS_DATETIME_FORMATTER)
                when {
                    isUtc -> ZonedDateTime.of(localTime, ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                    tzid != null -> {
                        try {
                            ZonedDateTime.of(localTime, ZoneId.of(tzid)).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                        } catch (e: Exception) {
                            Log.w("IcsEvent", "Invalid TZID '$tzid', interpreting $dateTimeString as system local. ${e.message}")
                            localTime // Fallback to localTime if TZID is invalid
                        }
                    }
                    else -> localTime // No TZID, assume local (as per original ICS spec for floating times)
                }
            } catch (e: DateTimeParseException) {
                Log.e("IcsEvent","Error parsing ICS date-time: $dateTimeString with tzid=$tzid, isUtc=$isUtc. ${e.message}")
                null
            } catch (e: Exception) {
                Log.e("IcsEvent","Generic error parsing ICS date-time: $dateTimeString. ${e.message}")
                null
            }
        }

        private fun parseTeamsAndDeriveAgeGroup(
            summaryText: String?,
            descriptionText: String?, // For fallback age group parsing if needed
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

                    Log.d("IcsEvent", "Teams Parsed: Home='${home}', Away='${away}' from summary: $summaryText")

                    // Attempt to extract birth year from home team first
                    birthYear = home?.let { extractBirthYearFromTeamName(it) }
                    // If not found in home, try away team
                    if (birthYear == null) {
                        birthYear = away?.let { extractBirthYearFromTeamName(it) }
                    }

                } else {
                    Log.w("IcsEvent", "TEAM_VS_PATTERN did NOT match summary: $summaryText")
                    // Fallback for team splitting if primary regex fails
                    val teams = summaryText.split(Regex("\\s+(?:vs?\\.?|v\\.?|-)\\s+"), limit = 2)
                    if (teams.size == 2) {
                        home = teams[0].replaceFirst(Regex("^Referee Assignment:.*?-\\s*\\d+\\s*", RegexOption.IGNORE_CASE), "").trim()
                        away = teams[1].substringBefore(" - ISL SPRING").substringBefore(" (").trim()
                        Log.d("IcsEvent", "Fallback Team Split: Home='${home}', Away='${away}'")
                        birthYear = home?.let { extractBirthYearFromTeamName(it) } ?: away?.let { extractBirthYearFromTeamName(it) }
                    }
                }
            }

            if (birthYear != null) {
                val calculatedAge = currentYear - birthYear
                ageGroup = AgeGroup.fromCalculatedAge(calculatedAge)
                Log.d("IcsEvent", "Derived Age Group: $ageGroup based on birth year $birthYear and current year $currentYear")
            } else {
                // Fallback to parsing age group from summary or description string if birth year not found
                ageGroup = AgeGroup.fromString(summaryText)
                if (ageGroup == null || ageGroup == AgeGroup.UNKNOWN) {
                    ageGroup = AgeGroup.fromString(descriptionText)
                }
                Log.d("IcsEvent", "Fallback Age Group Parsing from text: $ageGroup")
            }
            return Triple(home, away, ageGroup)
        }

        private fun extractBirthYearFromTeamName(teamName: String): Int? {
            val matcher = TEAM_BIRTH_YEAR_PATTERN.matcher(teamName)
            if (matcher.find()) {
                val yearStr = matcher.group(1) // Group 1 is the (\d{4})
                try {
                    val year = yearStr.toInt()
                    // Add some sanity check for plausible birth years
                    if (year in 1950..(LocalDate.now().year - 3)) { // e.g., not a future year or too far in past
                        Log.d("IcsEvent", "Extracted birth year: $year from team: $teamName")
                        return year
                    }
                } catch (e: NumberFormatException) {
                    Log.w("IcsEvent", "Could not parse '$yearStr' as birth year from team: $teamName")
                }
            }
            Log.d("IcsEvent", "No birth year pattern matched in team: $teamName")
            return null
        }

        // Helper to get a single property value from the block
        private fun getPropertyValue(pattern: Pattern, eventBlock: String): String? {
            return pattern.matcher(eventBlock).run { if (find()) group(1)?.trim() else null }
        }
    }

    // Constructor takes the raw, unfolded VEVENT block content
    constructor(eventBlockContent: String) : this(
        uid = getPropertyValue(UID_PATTERN, eventBlockContent),
        summary = getPropertyValue(SUMMARY_PATTERN, eventBlockContent),
        description = getPropertyValue(DESCRIPTION_PATTERN, eventBlockContent),
        location = getPropertyValue(LOCATION_PATTERN, eventBlockContent),
        dtStart = null, // Placeholder, will be parsed below
        dtEnd = null,   // Placeholder, will be parsed below
        homeTeam = null, // Placeholder
        awayTeam = null, // Placeholder
        ageGroup = null  // Placeholder
    ) {
        // Parse DTSTART and DTEND
        val dateTimePropertyMatcher = DATETIME_PROPERTY_PATTERN.matcher(eventBlockContent)
        var tempDtStart: LocalDateTime? = null
        var tempDtEnd: LocalDateTime? = null
        while (dateTimePropertyMatcher.find()) {
            val propertyName = dateTimePropertyMatcher.group(1) // DTSTART or DTEND
            val tzid = dateTimePropertyMatcher.group(2)         // Optional TZID
            val dateTimeValue = dateTimePropertyMatcher.group(3)// Raw datetime string
            val isUtc = dateTimePropertyMatcher.group(4) != null // "Z" presence

            val parsedDateTime = parseIcsDateTime(dateTimeValue, tzid, isUtc)
            if (propertyName == "DTSTART") tempDtStart = parsedDateTime
            else if (propertyName == "DTEND") tempDtEnd = parsedDateTime
        }
        // Re-assign to the actual properties (since constructor params are val)
        // This is a bit of a workaround for constructor limitations with complex parsing.
        // A secondary constructor or factory method might be cleaner if this becomes too unwieldy.
        // For now, we'll update the vars declared in the data class.
        // However, data class properties are val. We need to pass them through the primary constructor.
        // This requires parsing them before calling `this(...)`.

        // The above approach of initializing with null and then setting won't work
        // because the primary constructor's parameters are `val`.
        // We need to call the primary constructor with all values already parsed.
        // This means the parsing logic should ideally be outside or passed into the this() call.

        // Let's adjust the structure:
        // The primary constructor will be simpler.
        // We'll use a secondary constructor or a factory method in the companion object.
        // For this refactor, let's make the primary constructor do the work, requiring
        // that the complex parsing happens to prepare arguments for its `this(...)` call if needed,
        // or simply parse directly into its parameters.

        // Corrected approach: Parse directly into the 'val' properties via the primary constructor.
        // The current `this(...)` structure is already trying to do this,
        // but dtStart, dtEnd, homeTeam, awayTeam, ageGroup need to be determined *before* `this` is called.

        // This means the current secondary constructor `constructor(eventBlockContent: String)`
        // must fully resolve all values for the primary constructor.

        // Let's re-evaluate the constructor strategy.
        // The simplest way is to have the primary constructor take all parsed values,
        // and the companion object has a factory method.

        // --> SEE REVISED SimpleIcsEvent AND SimpleIcsParser BELOW for the factory method approach
    }
}


// --- Main ICS Parser Object ---
object SimpleIcsParser {
    private val EVENT_PATTERN: Pattern = Pattern.compile("BEGIN:VEVENT(.*?)END:VEVENT", Pattern.DOTALL)

    // Helper function to unfold ICS content lines
    // (Can be private if only used here, or public if needed elsewhere)
    fun unfoldIcsLines(foldedString: String): String {
        // Replace CR LF SPACE with nothing (unfold)
        // Then replace remaining CR LF with LF for simpler line splitting
        return foldedString.replace("\r\n ", "").replace("\r\n\t", "").replace("\r\n", "\n")
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
        val uid = getPropertyValue(UID_PATTERN, eventBlockContent)
        val summary = getPropertyValue(SUMMARY_PATTERN, eventBlockContent)
        val description = getPropertyValue(DESCRIPTION_PATTERN, eventBlockContent)
        val location = getPropertyValue(LOCATION_PATTERN, eventBlockContent)

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

    // Helper function to unfold ICS content lines
    private fun unfoldIcsLines(foldedString: String): String {
        // Replace special characters
        return foldedString.replace(Regex("[\n\r\t]"), "")
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
