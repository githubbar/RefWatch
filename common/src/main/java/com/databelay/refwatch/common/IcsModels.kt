package com.databelay.refwatch.common // Make sure this matches your actual package

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.databelay.refwatch.common.AgeGroup
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.regex.Pattern
import java.io.BufferedReader
import java.io.InputStreamReader


data class SimpleIcsEvent(
    var uid: String? = null,
    var summary: String? = null,
    var description: String? = null,
    var location: String? = null,
    var dtStart: LocalDateTime? = null,
    var dtEnd: LocalDateTime? = null,
    var homeTeam: String? = null,
    var awayTeam: String? = null,
    var ageGroup: AgeGroup? = null,
    var gameNumber: String? = null
) {
    override fun toString(): String {
        val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return """
            SimpleIcsEvent(
                UID: $uid,
                Summary: $summary,
                Description: $description,
                Location: $location,
                Start: ${dtStart?.format(dateFormat) ?: "N/A"},
                End: ${dtEnd?.format(dateFormat) ?: "N/A"},
                Home Team: $homeTeam,
                Away Team: $awayTeam,
                Age Group: ${ageGroup?.displayName ?: "N/A"},
                Game Number: $gameNumber
            )
        """.trimIndent()
    }
}



// --- Main ICS Parser Object ---
object SimpleIcsParser {
    private val EVENT_PATTERN: Pattern = Pattern.compile("BEGIN:VEVENT(.*?)END:VEVENT", Pattern.DOTALL)

    // Helper function to unfold ICS content lines
    // (Can be private if only used here, or public if needed elsewhere)
    // Standard for iCalendar (ICS) files is defined in RFC 5545 (Internet Calendaring and
    // Scheduling Core Object Specification - iCalendar), specifically in section 3.1. Content Lines.
    private fun unfoldIcsLines(foldedString: String): String {
        // 1. Normalize all known line endings to LF (\n)
        val normalizedString = foldedString.replace(Regex("\\r\\n|\\r|\\n"), "\n")

        // 2. Unfold lines based on the now consistent LF followed by a space or tab
        //    A line that is folded starts with a whitespace character (space or tab)
        //    and the preceding line break should be removed.
        return normalizedString.replace(Regex("\n[ \t]"), "")
    }

    /**
     * Parses ICS content from a given string.
     */
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

    /**
     * Parses ICS content from a Uri, typically obtained from a file picker.
     *
     * @param contentResolver The ContentResolver to open the Uri.
     * @param fileUri The Uri of the .ics file to parse.
     * @return A list of SimpleIcsEvent objects, or null if an error occurs during reading.
     */
    fun parseUri(contentResolver: ContentResolver, fileUri: Uri): List<SimpleIcsEvent>? {
        Log.d("SimpleIcsParser", "Attempting to parse ICS from URI: $fileUri")
        try {
            contentResolver.openInputStream(fileUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line).append("\n")
                    }
                    val icsContent = stringBuilder.toString()
                    if (icsContent.isBlank()) {
                        Log.w("SimpleIcsParser", "ICS file from URI is empty or could not be read properly.")
                        return emptyList() // Or null, depending on how you want to signal empty
                    }
                    Log.d("SimpleIcsParser", "Successfully read ${icsContent.length} chars from URI. Starting parse.")
                    return parse(icsContent) // Reuse the existing string parsing logic
                }
            } ?: run {
                Log.e("SimpleIcsParser", "Failed to open InputStream for URI: $fileUri")
                return null
            }
        } catch (e: IOException) {
            Log.e("SimpleIcsParser", "IOException while reading ICS from URI: $fileUri", e)
            return null
        } catch (e: SecurityException) {
            Log.e("SimpleIcsParser", "SecurityException: Permission denied for URI: $fileUri", e)
            return null
        } catch (e: Exception) {
            Log.e("SimpleIcsParser", "Unexpected error parsing ICS from URI: $fileUri", e)
            return null
        }
    }
}

// Factory object to construct SimpleIcsEvent
object SimpleIcsEventFactory {
    // Patterns and helpers moved from SimpleIcsEvent.Companion for clarity in factory
    private val UID_PATTERN: Pattern = Pattern.compile("^\\s*UID:(.+)$", Pattern.MULTILINE)
    private val SUMMARY_PATTERN: Pattern = Pattern.compile("^\\s*SUMMARY:(.+)$", Pattern.MULTILINE)
    private val DESCRIPTION_PATTERN: Pattern = Pattern.compile("^\\s*DESCRIPTION:(.+)$", Pattern.MULTILINE)
    private val LOCATION_PATTERN: Pattern = Pattern.compile("^\\s*LOCATION:(.+)$", Pattern.MULTILINE)
    private val DATETIME_PROPERTY_PATTERN: Pattern = Pattern.compile("^\\s*(DTSTART|DTEND)(?:;TZID=([^:]+))?:(\\d{8}T\\d{6})(Z)?$", Pattern.MULTILINE)
    private val ICS_DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val TEAM_BIRTH_YEAR_PATTERN: Pattern = Pattern.compile("\\b(\\d{4})(?:[/\\-]\\d{2,4})?\\b")
    private val TEAM_VS_PATTERN: Pattern = Pattern.compile(
        """Referee Assignment:\s*(?:Referee|Asst Referee \d)\s*-\s*(\d+)(?:\s+(.*?))?\s*vs\.?\s*(.*?)\s*-\s*(.*)""",
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

    private fun populateUid(eventBlockContent: String, event: SimpleIcsEvent): Boolean {
        event.uid = getPropertyValue(UID_PATTERN, eventBlockContent)
        return event.uid != null
    }

    private fun populateBasicDetails(eventBlockContent: String, event: SimpleIcsEvent) {
        event.summary = getPropertyValue(SUMMARY_PATTERN, eventBlockContent)
        event.description = getPropertyValue(DESCRIPTION_PATTERN, eventBlockContent)
        event.location = getPropertyValue(LOCATION_PATTERN, eventBlockContent)
    }

    private fun populateDateTimes(eventBlockContent: String, event: SimpleIcsEvent): Boolean {
        val dateTimePropertyMatcher = DATETIME_PROPERTY_PATTERN.matcher(eventBlockContent)
        var dtStartFound = false
        while (dateTimePropertyMatcher.find()) {
            val propertyName = dateTimePropertyMatcher.group(1)
            val tzid = dateTimePropertyMatcher.group(2)
            val dateTimeValue = dateTimePropertyMatcher.group(3)
            val isUtc = dateTimePropertyMatcher.group(4) != null
            val parsedDateTime = parseIcsDateTime(dateTimeValue, tzid, isUtc)

            if (propertyName == "DTSTART") {
                event.dtStart = parsedDateTime
                if (parsedDateTime != null) dtStartFound = true
            } else if (propertyName == "DTEND") {
                event.dtEnd = parsedDateTime
            }
        }
        return dtStartFound // Return true if DTSTART was found and parsed
    }

    /**
     * Parses team names, game number from summary and derives age group.
     * Modifies the passed-in event directly.
     */
    private fun populateGameAndTeamDetails(eventBlockContent: String, event: SimpleIcsEvent) {
        var gameNumber: String? = null
        var home: String? = null
        var away: String? = null
        var ageGroup: AgeGroup = AgeGroup.UNKNOWN
        var birthYear: Int? = null
        val currentYear: Int = LocalDate.now().year
        if (event.summary != null) {
            val teamMatcher = TEAM_VS_PATTERN.matcher(event.summary)
            if (teamMatcher.find()) {
                gameNumber = teamMatcher.group(1)?.trim()
                home = teamMatcher.group(2)?.trim()
                away = teamMatcher.group(3)?.trim()
                gameNumber = if (gameNumber.isNullOrEmpty()) "EMPTY" else gameNumber
                home = if (home.isNullOrEmpty()) "EMPTY" else home
                away = if (away.isNullOrEmpty()) "EMPTY" else away
                birthYear = home.let { extractBirthYearFromTeamName(it) } ?: away?.let { extractBirthYearFromTeamName(it) }
            }
        }
        event.gameNumber = gameNumber
        event.homeTeam = home
        event.awayTeam = away
        if (birthYear != null) {
            val calculatedAge = currentYear - birthYear
            ageGroup = AgeGroup.fromCalculatedAge(calculatedAge)
        } else {
            ageGroup = AgeGroup.fromString(event.summary)
            if (ageGroup == AgeGroup.UNKNOWN) {
                ageGroup = AgeGroup.fromString(event.description)
            }
        }
    }

    fun createFromEventBlock(eventBlockContent: String): SimpleIcsEvent? {

        val event = SimpleIcsEvent()

        // Populate UID first, as it's a primary identifier
        if (!populateUid(eventBlockContent, event)) {
            Log.w("IcsFactory", "Skipping event due to missing UID. Content block: ${eventBlockContent.take(100)}...")
            return null // UID is mandatory
        }

        // Populate date-times, DTSTART is critical
        if (!populateDateTimes(eventBlockContent, event)) {
            Log.w("IcsFactory", "Skipping event UID: ${event.uid} due to missing or invalid DTSTART.")
            return null // DTSTART is mandatory
        }

        // Populate other details
        populateBasicDetails(eventBlockContent, event) // Fills summary, description, location

        populateGameAndTeamDetails(eventBlockContent, event)

        return event

    }

}