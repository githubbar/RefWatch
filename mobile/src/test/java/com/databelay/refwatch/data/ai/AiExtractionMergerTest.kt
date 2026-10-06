package com.databelay.refwatch.data.ai

import com.databelay.refwatch.common.AgeGroup
import com.databelay.refwatch.common.SimpleIcsEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime

class AiExtractionMergerTest {

    private fun regexEvent(
        start: String = "2026-10-31T09:00",
        ageGroup: AgeGroup = AgeGroup.UNKNOWN
    ) = SimpleIcsEvent(
        uid = "u1",
        summary = "Referee Assignment: Referee - 77 Cutters Eleven 2016 Boys Blue vs.  Seymour Cyclones - ISL FALL 2026 - Karst Farm Park - Field 09A",
        dtStart = LocalDateTime.parse(start),
        refereeAssignment = "Referee",
        gameNumber = "77",
        homeTeam = "Cutters Eleven 2016 Boys Blue",
        awayTeam = "Seymour Cyclones",
        fieldNumber = "09A",
        ageGroup = ageGroup
    )

    @Test
    fun codeRedoesBirthYearArithmeticFromTheGameDate() {
        // The model subtracted from the calendar year (2026 - 2016 = 10); the season ends in 2027.
        val ai = AiExtraction(uid = "u1", seasonEndYear = 2026, birthYearUsed = 2016, ageGroup = "U10")
        assertThat(AiExtractionMerger.merge(regexEvent(), ai).ageGroup).isEqualTo(AgeGroup.U11)
    }

    @Test
    fun labelDecidedAgeIsTakenFromTheModel() {
        val ai = AiExtraction(uid = "u1", seasonEndYear = 2027, birthYearUsed = null, ageGroup = "U11")
        assertThat(AiExtractionMerger.merge(regexEvent(), ai).ageGroup).isEqualTo(AgeGroup.U11)
    }

    @Test
    fun missingExtractionKeepsTheRegexEvent() {
        val event = regexEvent(ageGroup = AgeGroup.U12)
        assertThat(AiExtractionMerger.merge(event, null)).isEqualTo(event)
    }

    @Test
    fun unknownOrInvalidAiAgeFallsBackToRegex() {
        val event = regexEvent(ageGroup = AgeGroup.U12)
        assertThat(AiExtractionMerger.merge(event, AiExtraction(uid = "u1", ageGroup = "UNKNOWN")).ageGroup)
            .isEqualTo(AgeGroup.U12)
        assertThat(AiExtractionMerger.merge(event, AiExtraction(uid = "u1", ageGroup = "U9")).ageGroup)
            .isEqualTo(AgeGroup.U12)
        assertThat(AiExtractionMerger.merge(event, AiExtraction(uid = "u1", ageGroup = null)).ageGroup)
            .isEqualTo(AgeGroup.U12)
    }

    @Test
    fun implausibleBirthYearIsIgnored() {
        val ai = AiExtraction(uid = "u1", birthYearUsed = 2026, ageGroup = "U13")
        assertThat(AiExtractionMerger.merge(regexEvent(), ai).ageGroup).isEqualTo(AgeGroup.U13)
    }

    @Test
    fun blankAiFieldsFallBackToRegex() {
        val merged = AiExtractionMerger.merge(
            regexEvent(),
            AiExtraction(uid = "u1", role = " ", homeTeam = null, awayTeam = "", gameNumber = null, field = null)
        )
        assertThat(merged.refereeAssignment).isEqualTo("Referee")
        assertThat(merged.homeTeam).isEqualTo("Cutters Eleven 2016 Boys Blue")
        assertThat(merged.awayTeam).isEqualTo("Seymour Cyclones")
        assertThat(merged.gameNumber).isEqualTo("77")
        assertThat(merged.fieldNumber).isEqualTo("09A")
    }

    @Test
    fun aiFieldsWinWhenPresent() {
        val merged = AiExtractionMerger.merge(
            regexEvent(),
            AiExtraction(uid = "u1", role = "AR1", homeTeam = "Carmel FC 2013B Red", awayTeam = "Indy Premier U13 Boys",
                gameNumber = "20431", field = "Field 22")
        )
        assertThat(merged.refereeAssignment).isEqualTo("AR1")
        assertThat(merged.homeTeam).isEqualTo("Carmel FC 2013B Red")
        assertThat(merged.awayTeam).isEqualTo("Indy Premier U13 Boys")
        assertThat(merged.gameNumber).isEqualTo("20431")
        // The game list already prefixes "Field ".
        assertThat(merged.fieldNumber).isEqualTo("22")
    }

    @Test
    fun parsesModelResponseLeniently() {
        val json = """[{"uid":"u1","role":"Referee","ageGroup":"U12","seasonEndYear":2027,"extra":"ignored"},{"uid":"u2"}]"""
        val parsed = AiExtractionMerger.parseResponse(json)
        assertThat(parsed.map { it.uid }).containsExactly("u1", "u2")
        assertThat(parsed[0].ageGroup).isEqualTo("U12")
    }
}
