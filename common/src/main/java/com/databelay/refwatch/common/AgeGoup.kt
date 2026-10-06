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
        // One age label as a whole token: "U12", "U-15", "BU12", "U11B", "12U", "Under 10".
        // League ranges such as "11U & Over" or "11U-19/20U" describe a division, not a team, so a
        // label followed by a range connector or preceded by "/" or "-" is skipped.
        private val AGE_LABEL = Regex(
            """(?<![\w/-])(?:[BG]?U-?(\d{1,2})[BG]?|(\d{1,2})U|Under\s+(\d{1,2}))(?!\w)(?!\s*(?:&|-|–|/|\+|to\b|and\b))""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Parses an AgeGroup from free text: an enum name or display name ("U12", "12U"), or the
         * first age label in the text ("Cutters Eleven U11 Girls", "BU12 Gold", "Under 10").
         */
        fun fromString(value: String?): AgeGroup {
            if (value.isNullOrBlank()) return UNKNOWN

            val normalized = value.trim().uppercase()
            entries.find { it.name == normalized || it.displayName.uppercase() == normalized }?.let { return it }

            val match = AGE_LABEL.find(value) ?: return UNKNOWN
            val age = match.groupValues.drop(1).firstNotNullOfOrNull { it.toIntOrNull() } ?: return UNKNOWN
            return fromCalculatedAge(age)
        }

        /** Soccer seasons run August to July and are named for the year they end in. */
        fun seasonEndYear(date: LocalDate): Int = if (date.monthValue >= 8) date.year + 1 else date.year

        /**
         * Derives an AgeGroup based on a calculated age (e.g., current year - birth year).
         * This is a general mapping and might not capture specific league rules like "7v7".
         */
        fun fromCalculatedAge(age: Int): AgeGroup {
            return when (age) {
                // Mapping age (typically SeasonEndYear - BirthYear) to the U-group.
                // For example, a 12-year-old (soccer age) plays in U12.
                in 0..8  -> U8
                9, 10    -> U10
                11       -> U11
                12       -> U12
                13       -> U13
                14       -> U14
                15       -> U15
                16       -> U16
                17       -> U17
                18       -> U18
                19       -> U19

                // For ages older than what's typical for youth soccer
                else -> if (age > 19) GENERIC_ADULT else UNKNOWN
            }
        }

        // Example of how you might extract birth year for age calculation
        // This is simplified and would live in your ICS parsing logic, not here.
        // Just for conceptual reference related to `fromCalculatedAge`.
        fun getAgeFromTeamName(teamName: String?, currentYear: Int = LocalDate.now().year): Int? {
            if (teamName == null) return null // Early exit for null teamName

            // Regex to find birth years like 2009, 2009/10, 2010B, etc.
            // Ensure your regex for group(1) is what you expect for the year.
            val birthYearPattern = Pattern.compile("\\b(20\\d{2}|19\\d{2})(?:[/\\-]\\d{2,4})?\\b")
            val matcher = birthYearPattern.matcher(teamName)

            if (matcher.find()) {
                val yearStr = matcher.group(1) // This is String?

                // Use toIntOrNull() for safe parsing
                val year = yearStr?.toIntOrNull()

                if (year != null && year in 1950..(currentYear - 3)) {
                    return currentYear - year
                }
                // Optionally log if yearStr was found but couldn't be parsed or was out of range
                // else if (yearStr != null) {
                //     Log.d("AgeGroup", "Parsed year '$year' from '$yearStr' is out of valid range or not a number.")
                // }
            }
            return null
        }
    }
}