package com.databelay.refwatch.common

import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Events as a Garmin watch uploads them (garmin/source/model/MatchState.mc) and garminUploadGame
// stores them, read back the way the phone reads a game document. Firestore returns whole numbers
// as Long, so timestamps and clock times arrive as Long or Double depending on the value.
class GarminEventsTest {
    private val t0 = 1_791_500_460_000L

    private val stored: List<Map<String, Any?>> = listOf(
        mapOf("eventType" to "PHASE_CHANGE", "id" to "e1", "newPhase" to "FIRST_HALF",
            "timestamp" to t0, "gameTimeMillis" to 0L),
        mapOf("eventType" to "GOAL", "id" to "e2", "team" to "HOME", "timestamp" to t0 + 754_000L,
            "gameTimeMillis" to 754_000.5, "homeScoreAtTime" to 1L, "awayScoreAtTime" to 0L,
            "playerNumber" to 9L),
        mapOf("eventType" to "GOAL", "id" to "e3", "team" to "AWAY", "timestamp" to t0 + 800_000.25,
            "gameTimeMillis" to 800_000L, "homeScoreAtTime" to 1L, "awayScoreAtTime" to 1L),
        mapOf("eventType" to "CARD", "id" to "e4", "team" to "AWAY", "playerNumber" to 4L,
            "cardType" to "YELLOW", "timestamp" to t0 + 900_000L, "gameTimeMillis" to 900_000L),
        mapOf("eventType" to "PHASE_CHANGE", "id" to "e5", "newPhase" to "GAME_ENDED",
            "timestamp" to t0 + 1_800_000L, "gameTimeMillis" to 1_800_000L),
    )

    private fun decode(map: Map<String, Any?>): GameEvent =
        AppJsonConfiguration.decodeFromJsonElement(mapToJsonObject(map))

    @Test
    fun everyEventAGarminWatchUploadsDecodes() {
        val events = stored.map(::decode)

        val kickOff = events[0] as PhaseChangedEvent
        assertEquals(GamePhase.FIRST_HALF, kickOff.newPhase)
        assertEquals(t0.toDouble(), kickOff.timestamp, 0.0)

        val goal = events[1] as GoalScoredEvent
        assertEquals(Team.HOME, goal.team)
        assertEquals(9, goal.playerNumber)
        assertEquals(754_000.5, goal.gameTimeMillis, 0.0)
        assertEquals(1, goal.homeScoreAtTime)

        val unscored = events[2] as GoalScoredEvent
        assertNull(unscored.playerNumber)
        assertEquals(1, unscored.awayScoreAtTime)

        val card = events[3] as CardIssuedEvent
        assertEquals(CardType.YELLOW, card.cardType)
        assertEquals(4, card.playerNumber)
        assertEquals(Team.AWAY, card.team)

        assertEquals(GamePhase.GAME_ENDED, (events[4] as PhaseChangedEvent).newPhase)
    }
}
