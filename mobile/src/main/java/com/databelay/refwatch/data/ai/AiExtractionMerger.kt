package com.databelay.refwatch.data.ai

import com.databelay.refwatch.common.AgeGroup
import com.databelay.refwatch.common.SimpleIcsEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

/** One event as the model returns it. Every field is optional so a partial answer still parses. */
@Serializable
data class AiExtraction(
    val uid: String,
    val role: String? = null,
    val gameNumber: String? = null,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val field: String? = null,
    val ageEvidence: String? = null,
    val seasonEndYear: Int? = null,
    val birthYearUsed: Int? = null,
    val ageGroup: String? = null
)

/**
 * Lays the model's answer over the regex parse. The regex value stays wherever the model
 * returned nothing usable, so a bad answer degrades to the old behaviour instead of losing data.
 */
object AiExtractionMerger {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseResponse(text: String): List<AiExtraction> = json.decodeFromString(text)

    fun merge(event: SimpleIcsEvent, ai: AiExtraction?): SimpleIcsEvent {
        if (ai == null) return event
        return event.copy(
            refereeAssignment = ai.role.usable() ?: event.refereeAssignment,
            gameNumber = ai.gameNumber.usable() ?: event.gameNumber,
            homeTeam = ai.homeTeam.usable() ?: event.homeTeam,
            awayTeam = ai.awayTeam.usable() ?: event.awayTeam,
            fieldNumber = ai.field.usable()?.removePrefix("Field ")?.removePrefix("field ") ?: event.fieldNumber,
            ageGroup = resolveAgeGroup(ai, event.dtStart)
                ?: event.ageGroup
        )
    }

    /**
     * The model is reliable at finding the birth year but not at season arithmetic, so when it
     * names a birth year the age is recomputed here from the game date.
     */
    private fun resolveAgeGroup(ai: AiExtraction, start: LocalDateTime?): AgeGroup? {
        val seasonEndYear = start?.let { AgeGroup.seasonEndYear(it.toLocalDate()) }
        val fromBirthYear = ai.birthYearUsed
            ?.takeIf { seasonEndYear != null && it in 1950..(seasonEndYear - 3) }
            ?.let { AgeGroup.fromCalculatedAge(seasonEndYear!! - it) }
        val fromModel = ai.ageGroup?.let { name -> AgeGroup.entries.find { it.name == name } }
        return (fromBirthYear ?: fromModel)?.takeIf { it != AgeGroup.UNKNOWN }
    }

    private fun String?.usable(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
