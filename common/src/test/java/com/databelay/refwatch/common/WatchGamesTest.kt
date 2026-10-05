package com.databelay.refwatch.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchGamesTest {
    private val now = 1_790_000_000_000L
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    private fun game(phase: GamePhase, start: Long?, lastUpdated: Long = now - 400 * day) =
        Game.defaults().copy(currentPhase = phase, gameDateTimeEpochMillis = start, lastUpdated = lastUpdated)

    @Test
    fun upcomingGamesAreShown() {
        assertTrue(WatchGames.isShown(game(GamePhase.NOT_STARTED, now + 5 * day), now))
    }

    @Test
    fun gamesFromTheLastTwoWeeksAreShownWhateverTheirState() {
        assertTrue(WatchGames.isShown(game(GamePhase.GAME_ENDED, now - 13 * day), now))
        assertTrue(WatchGames.isShown(game(GamePhase.NOT_STARTED, now - 2 * hour), now))
        assertTrue(WatchGames.isShown(game(GamePhase.SECOND_HALF, now - day), now))
    }

    @Test
    fun olderGamesAreHiddenWhateverTheirState() {
        assertFalse(WatchGames.isShown(game(GamePhase.GAME_ENDED, now - 15 * day), now))
        assertFalse(WatchGames.isShown(game(GamePhase.NOT_STARTED, now - 400 * day), now))
        // A game left "in progress" months ago was abandoned, not refereed.
        assertFalse(WatchGames.isShown(game(GamePhase.FIRST_HALF, now - 60 * day), now))
    }

    @Test
    fun undatedGamesUseTheirLastUpdate() {
        assertTrue(WatchGames.isShown(game(GamePhase.HALF_TIME, null, lastUpdated = now - day), now))
        assertFalse(WatchGames.isShown(game(GamePhase.HALF_TIME, null, lastUpdated = now - 30 * day), now))
    }

    @Test
    fun cutoffIsTwoWeeksBeforeNow() {
        assertTrue(WatchGames.recentCutoff(now) == now - 14 * day)
    }
}
