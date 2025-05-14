package com.databelay.refwatch.common

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

data class SimpleIcsEvent(
    val summary: String?, // Typically "Team A vs Team B"
    val description: String?, // Could contain more details, like referee assignment
    val location: String?, // Field, Venue
    val dtStart: LocalDateTime?,
    val dtEnd: LocalDateTime?,
    var homeTeam: String? = null,
    var awayTeam: String? = null
)

// --- Basic ICS Parser (Very Naive - Use a library for production) ---
object SimpleIcsParser {

    private val EVENT_PATTERN: Pattern = Pattern.compile("BEGIN:VEVENT(.*?)END:VEVENT", Pattern.DOTALL)
    private val SUMMARY_PATTERN: Pattern = Pattern.compile("SUMMARY:(.+)")
    private val DESCRIPTION_PATTERN: Pattern = Pattern.compile("DESCRIPTION:(.+)")
    private val LOCATION_PATTERN: Pattern = Pattern.compile("LOCATION:(.+)")
    private val DTSTART_PATTERN: Pattern = Pattern.compile("DTSTART(?:;TZID=[^:]+)?:(\\d{8}T\\d{6}Z?)") // Handles UTC (Z) or local
    private val DTEND_PATTERN: Pattern = Pattern.compile("DTEND(?:;TZID=[^:]+)?:(\\d{8}T\\d{6}Z?)")

    // Basic ISO 8601 / ICS date-time formatters
    private val ICS_UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val ICS_LOCAL_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")


    fun parse(icsContent: String): List<SimpleIcsEvent> {
        val events = mutableListOf<SimpleIcsEvent>()
        val eventMatcher = EVENT_PATTERN.matcher(icsContent)

        while (eventMatcher.find()) {
            val eventBlock = eventMatcher.group(1) ?: continue

            val summary = SUMMARY_PATTERN.matcher(eventBlock).run { if (find()) group(1)?.trim() else null }
            val description = DESCRIPTION_PATTERN.matcher(eventBlock).run { if (find()) group(1)?.trim() else null }
            val location = LOCATION_PATTERN.matcher(eventBlock).run { if (find()) group(1)?.trim() else null }

            val dtStartString = DTSTART_PATTERN.matcher(eventBlock).run { if (find()) group(1)?.trim() else null }
            val dtEndString = DTEND_PATTERN.matcher(eventBlock).run { if (find()) group(1)?.trim() else null }

            val dtStart = dtStartString?.let { parseIcsDateTime(it) }
            val dtEnd = dtEndString?.let { parseIcsDateTime(it) }

            val event = SimpleIcsEvent(summary, description, location, dtStart, dtEnd)

            // Attempt to parse Home vs Away from summary (very basic)
            summary?.let {
                val teams = it.split("vs", "VS", "-").map { team -> team.trim() }
                if (teams.size >= 2) {
                    event.homeTeam = teams[0]
                    event.awayTeam = teams[1]
                }
            }
            events.add(event)
        }
        return events
    }

    private fun parseIcsDateTime(dateTimeString: String): LocalDateTime? {
        return try {
            if (dateTimeString.endsWith("Z")) {
                LocalDateTime.parse(dateTimeString, ICS_UTC_FORMATTER).atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            } else {
                LocalDateTime.parse(dateTimeString, ICS_LOCAL_FORMATTER) // Assumes local if no Z and no TZID handled
            }
        } catch (e: Exception) {
            null // Or log error
        }
    }
}