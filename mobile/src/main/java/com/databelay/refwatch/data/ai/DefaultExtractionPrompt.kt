package com.databelay.refwatch.data.ai

/**
 * The prompt shipped with the app, used until the user edits it in Settings and restored by
 * "Load default". Tested against real referee_assignments.ics exports (see
 * age-extraction-results.csv). The response format is fixed by [AiScheduleExtractor]'s schema,
 * not by this text, so an edited prompt cannot change the shape of the answer.
 */
const val DEFAULT_EXTRACTION_PROMPT = """You extract soccer referee assignment details from calendar events. Input: JSON array of {uid, start, summary, description, location}. Return one object per input, same order.

AGE GROUP RULES (follow in order):
1. SEASON END YEAR. AUGUST 1 IS THE DIVIDING LINE BETWEEN SEASONS. A season runs August 1 through July 31 and is named for the year it ENDS in.
   - Game on or after August 1: seasonEndYear = the game's calendar year + 1.
   - Game from January 1 through July 31: seasonEndYear = the game's calendar year.
   Read the month from start (YYYY-MM-DD). Months 08, 09, 10, 11, 12 -> add 1 to the year. Months 01 through 07 -> keep the year.
   Examples: 2023-07-31 -> 2023. 2023-08-01 -> 2024. 2023-08-26 -> 2024. 2023-09-16 -> 2024. 2023-11-04 -> 2024. 2024-03-09 -> 2024. 2024-06-15 -> 2024.
   Compute seasonEndYear for EVERY event, including events with no team or age information.
   Always use seasonEndYear, never the calendar year of the game, for any birth-year arithmetic.
2. An explicit age label on a TEAM wins over everything else ("U11", "11U", "BU12", "U11B", "U-15", "Under 10", "15U Girls").
   If either team has a label, use it even if the other team shows a birth year.
   Ignore league or division ranges such as "11U & Over" or "11U-19/20U"; they are not a team's age.
3. Otherwise use team birth years ("2014B", "G2014", "Cutters SC 2013 Boys"). For a combined year ("2009/10", "2007-08G") take the OLDER (smaller) year.
   Teams play up, never down, so the OLDEST team decides: birthYearUsed = the smallest birth year across both teams.
   Example: "SCSA Eleven 2007-08G" vs "USAI 2008G" -> birthYearUsed 2007.
   age = seasonEndYear - birthYearUsed. Example: birth year 2016, start 2026-10-31 -> seasonEndYear 2027 -> age 11 -> U11.
   If two teams carry different age labels, use the higher one.
4. Map age to ageGroup: 8 or less -> U8, 9 or 10 -> U10, 11..19 -> U11..U19, 20 or more -> GENERIC_ADULT.
5. High-school varsity -> U19, JV -> U17. "3rd/4th grade" style -> use the higher grade + 6 as age. Adult, men's, women's, over-30 -> GENERIC_ADULT.
6. Two-digit team codes like "12G" or "11B" are ambiguous; use them only if no team has a label or a 4-digit birth year.
7. If nothing in the event text indicates age, ageGroup is UNKNOWN. Never infer age from the venue, the league or club name, or the competition name.

Fill seasonEndYear and birthYearUsed (null if a label decided it) BEFORE choosing ageGroup.
ageEvidence: the exact substring you based the age on, or null.
Other fields: null if absent. Do not invent team names. gameNumber is the assignor's game id if present.
"""
