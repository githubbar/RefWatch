package com.databelay.refwatch.common

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic

// Summaries are copied from real referee_assignments.ics exports unless noted.
class AgeExtractionTest {
    private var logMock: MockedStatic<Log>? = null

    @Before
    fun setUp() {
        logMock = org.mockito.Mockito.mockStatic(Log::class.java)
    }

    @After
    fun tearDown() {
        logMock?.close()
    }

    private fun parseOne(dtStart: String, summary: String): SimpleIcsEvent {
        val ics = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:test\nDTSTART:$dtStart\nSUMMARY:$summary\nEND:VEVENT\nEND:VCALENDAR\n"
        return SimpleIcsParser.parse(ics).single()
    }

    private fun ageOf(dtStart: String, summary: String) = parseOne(dtStart, summary).ageGroup

    @Test
    fun teamLabelBeatsOpponentBirthYear() {
        assertEquals(AgeGroup.U11, ageOf("20261010T130000",
            "Referee Assignment: Referee - 179 Cutters Eleven U11 Girls Red vs.  USAI 2015G Red - ISL FALL 2026 (11U & Over\\, All Divisions) - Karst Farm Park - Field 09B"))
        assertEquals(AgeGroup.U14, ageOf("20261004T133000",
            "Referee Assignment: Asst Referee 1 - 44 Cutters Eleven U14 Boys Red vs.  Goshen Stars 2012/2013 Boys Gray - ISL FALL 2026 (11U & Over\\, All Divisions) - Karst Farm Park - Field 11"))
        assertEquals(AgeGroup.U12, ageOf("20261003T130000",
            "Referee Assignment: Referee - 341 Cutters Eleven U12 Girls Red vs.  Indy Eleven 2015G Academy 1 - ISL FALL 2026 (11U & Over\\, All Divisions) - Karst Farm Park - Field 09B"))
    }

    @Test
    fun awayTeamLabelBeatsHomeBirthYear() {
        assertEquals(AgeGroup.U11, ageOf("20260927T110000",
            "Referee Assignment: Asst Referee 2 - 882 Columbus Express 2015/16B Blue vs.  FCE United U11B White - ISL FALL 2026 (11U & Over\\, All Divisions) - Karst Farm Park - Field 09B"))
    }

    @Test
    fun seasonComesFromGameDateNotToday() {
        // Spring 2025: season ends 2025, so 2013 -> U12.
        assertEquals(AgeGroup.U12, ageOf("20250413T130000",
            "Referee Assignment: Referee - 2183 Cutters SC 2013 Boys White vs.  TH Premier 2013B Samba - ISL SPRING 2025 (11U-19/20U\\, All Divisions) - Karst Farm Park - Field 08S"))
        // Fall 2025: season ends 2026, so 2014 -> U12.
        assertEquals(AgeGroup.U12, ageOf("20251005T160000",
            "Referee Assignment: Referee - 487 Cutters SC 2014 Girls Red vs.  TH Premier 2014G Copa - ISL FALL 2025 (11U & Over\\, All Divisions) - Karst Farm Park - Field 08S"))
    }

    @Test
    fun oldestTeamSetsTheAgeGroup() {
        // Spring 2025: 2009 -> U16, whichever team carries it.
        assertEquals(AgeGroup.U16, ageOf("20250329T163000",
            "Referee Assignment: Referee - 2844 Cutters SC 2009/10 Boys Red vs.  St Francis Premier 2009B Grey - ISL SPRING 2025 (11U-19/20U\\, All Divisions) - Karst Farm Park - Field 01"))
        // Teams play up, never down: a team with 2007 players makes the game U19.
        assertEquals(AgeGroup.U19, ageOf("20260607T080000",
            "Referee Assignment: Asst Referee 2 - 675 SCSA Eleven 2007-08G Red vs.  USAI 2008G Red - Classic At The Rock 2026 - SCSA Complex - Field 10"))
        assertEquals(AgeGroup.U18, ageOf("20260607T092000",
            "Referee Assignment: Asst Referee 1 - 492 SCSA Eleven 2009/10G Red vs.  Impact SC 2008 GE1 - Classic At The Rock 2026 - SCSA Complex - Field 10"))
    }

    @Test
    fun anyRoleParsesTeams() {
        val event = parseOne("20260607T120000",
            "Referee Assignment: 4th Official - 596 FC Midwest Academy - 2012/13 Boys vs.  SCSA Eleven 2012B Red - Classic At The Rock 2026 - SCSA Complex - Field 10")
        assertEquals("4th Official", event.refereeAssignment)
        assertEquals("596", event.gameNumber)
        assertEquals("SCSA Eleven 2012B Red", event.awayTeam)
        assertEquals(AgeGroup.U14, event.ageGroup)
    }

    @Test
    fun combinedYearsUseTheOlderYear() {
        assertEquals(AgeGroup.U14, ageOf("20260606T183000",
            "Referee Assignment: Referee - 641 MidState 2012/2013 Girls vs.  MSSC 2012/13 Girls - Classic At The Rock 2026 - SCSA Complex - Field 01"))
    }

    @Test
    fun leagueAgeRangeIsNotATeamAge() {
        // "12G" is ambiguous (birth year 2012 or U12); the league's "11U & Over" must not decide it.
        assertEquals(AgeGroup.UNKNOWN, ageOf("20261025T130000",
            "Referee Assignment: Asst Referee 1 - 2210 Indy Eleven Spirit 12G White vs.  Union FC 12G Teal - ISL FALL 2026 (11U & Over\\, All Divisions) - Karst Farm Park - Field 03"))
    }

    @Test
    fun gameNumberNextToTeamLabelIsNotAnAge() {
        // Synthetic: a rec team named by its age group, right after the game number.
        assertEquals(AgeGroup.U10, ageOf("20261017T090000",
            "Referee Assignment: Referee - 412 U9 Coed Blue vs. U9 Coed Red - Rec League Games 2026 - Karst Farm Park - Field 05"))
    }

    @Test
    fun recGameWithoutTeamsHasNoAgeAndKeepsBareField() {
        val event = parseOne("20261010T091500",
            "Referee Assignment: Referee - 118  vs.   - Rec League Games 2026 - Karst Farm Park - 02B")
        assertEquals(AgeGroup.UNKNOWN, event.ageGroup)
        assertEquals("02B", event.fieldNumber)
        assertEquals("118", event.gameNumber)
    }

    @Test
    fun fieldWordStillParses() {
        assertEquals("09B", parseOne("20261010T130000",
            "Referee Assignment: Referee - 179 Cutters Eleven U11 Girls Red vs.  USAI 2015G Red - ISL FALL 2026 (11U & Over\\, All Divisions) - Karst Farm Park - Field 09B").fieldNumber)
    }

    @Test
    fun fromStringReadsCommonLabels() {
        assertEquals(AgeGroup.U12, AgeGroup.fromString("Center Referee: BU12 Gold - Eagles SC vs Hawks United"))
        assertEquals(AgeGroup.U15, AgeGroup.fromString("[ACCEPTED] U-15 Boys D1: Kickers vs Rovers"))
        assertEquals(AgeGroup.U10, AgeGroup.fromString("Referee - Under 10 Girls: Lightning vs Thunder"))
        assertEquals(AgeGroup.U15, AgeGroup.fromString("Age group: 15U Girls. Home: Columbus Express 15G"))
        assertEquals(AgeGroup.U11, AgeGroup.fromString("FCE United U11B White"))
        assertEquals(AgeGroup.U12, AgeGroup.fromString("12U"))
        assertEquals(AgeGroup.U12, AgeGroup.fromString("U12"))
    }

    @Test
    fun fromStringIgnoresLeagueRanges() {
        assertEquals(AgeGroup.UNKNOWN, AgeGroup.fromString("ISL FALL 2026 (11U & Over, All Divisions)"))
        assertEquals(AgeGroup.UNKNOWN, AgeGroup.fromString("ISL SPRING 2025 (11U-19/20U, All Divisions)"))
    }
}
