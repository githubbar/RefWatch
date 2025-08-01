package com.databelay.refwatch.common

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
        return foldedString.replace(Regex("[\r\t]"), "")
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
        "^\\s*(?:(?:Referee(?: Assignment)?:\\s*(?:Referee|Asst Referee \\d|AR\\d?|REF)?\\s*-\\s*\\d+\\s*)?)(.*?)" +
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