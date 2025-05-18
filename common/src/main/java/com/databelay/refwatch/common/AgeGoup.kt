package com.databelay.refwatch.common

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.regex.Pattern

@Serializable
enum class AgeGroup(
    val displayName: String,
    val defaultHalfDurationMinutes: Int,
    val defaultHalftimeDurationMinutes: Int = 10, // Common default, can be overridden
    val players: Int? = null, // Optional: Number of players per side on field
    val notes: String? = null // Optional: Specific rules like ball size, headers
) {
    // User-defined age groups
    U8("8U", 25, players = 4, notes = "Size 3 ball."),
    U10("10U", 25, players = 7, notes = "Size 4 ball. Build-out line may apply."),
    U11("11U", 30, players = 9, notes = "Size 4 ball. No intentional heading."),
    U12("12U", 30, players = 9, notes = "Size 4 ball. No intentional heading."),
    U13("13U", 35, players = 11, notes = "Size 5 ball."),
    U14("14U", 35, players = 11, notes = "Size 5 ball."),
    U15("15U", 40, players = 11),
    U16("16U", 40, players = 11),
    U17("17U", 45, players = 11),
    U18("18U", 45, players = 11),
    U19("19U", 45, players = 11),
    // Fallback/Generic
    GENERIC_YOUTH("Youth Generic", 30, players = 11),
    GENERIC_ADULT("Adult Generic", 45, players = 11),
    UNKNOWN("Unknown", 30, defaultHalftimeDurationMinutes = 5); // A sensible default if truly unknown

    companion object {
        /**
         * Parses an AgeGroup from a string.
         * It tries to match common notations like "U10", "10U", "11U-12U", "U12", etc.
         * Also handles specific names like "15 & Over 7v7".
         */
        fun fromString(value: String?): AgeGroup {
            if (value.isNullOrBlank()) return UNKNOWN

            val upperValue = value.uppercase().replace(" ", "").replace("-", "")

            // Direct match for specific display names or enum names first
            entries.find {
                it.displayName.uppercase().replace(" ", "").replace("-", "") == upperValue ||
                        it.name == upperValue
            }?.let { return it }

            // Handle ranges or single 'U' values explicitly for better matching
            // Example: "U12" should match U11_U12, "14U" should match U13_U14
            val ageNumberMatch = Regex("(\\d+)U|U(\\d+)").find(upperValue)
            if (ageNumberMatch != null) {
                val age = (ageNumberMatch.groupValues[1].toIntOrNull() ?: ageNumberMatch.groupValues[2].toIntOrNull())
                if (age != null) {
                    return fromCalculatedAge(age)
                }
            }
            // Last resort, try partial contains (less reliable)
            return entries.find {
                upperValue.contains(it.displayName.uppercase().replace(" ", "").replace("-", "")) ||
                        upperValue.contains(it.name)
            } ?: UNKNOWN
        }

        /**
         * Derives an AgeGroup based on a calculated age (e.g., current year - birth year).
         * This is a general mapping and might not capture specific league rules like "7v7".
         */
        fun fromCalculatedAge(age: Int): AgeGroup {
            return when (age) {
                in 0..8 -> U8 // If 10 or less, default to U10 for simplicity here
                10 -> U10
                11 -> U11
                12 -> U12
                13 -> U13
                14 -> U14
                15 -> U15
                16 -> U16
                17 -> U17
                18 -> U18
                19 -> U19
                else -> if (age > 19) GENERIC_ADULT else UNKNOWN
            }
        }

        // Example of how you might extract birth year for age calculation
        // This is simplified and would live in your ICS parsing logic, not here.
        // Just for conceptual reference related to `fromCalculatedAge`.
        fun getAgeFromTeamName(teamName: String?, currentYear: Int = LocalDate.now().year): Int? {
            if (teamName == null) return null
            // Regex to find birth years like 2009, 2009/10, 2010B, etc.
            val birthYearPattern = Pattern.compile("\\b(20\\d{2}|19\\d{2})(?:[/\\-]\\d{2,4})?\\b")
            val matcher = birthYearPattern.matcher(teamName)
            if (matcher.find()) {
                val yearStr = matcher.group(1)
                try {
                    val year = yearStr.toInt()
                    if (year in 1950..(currentYear - 3)) {
                        return currentYear - year
                    }
                } catch (e: NumberFormatException) { /* ignore */ }
            }
            return null
        }
    }
}