package com.databelay.refwatch.common

import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.collections.plus

interface IMobileGameViewModel {
    val gamesList: StateFlow<List<Game>>
}

interface IWearGameViewModel {
    val gamesList: StateFlow<List<Game>>
    val isOnline: StateFlow<Boolean>
    val activeGame: StateFlow<Game?>
    val collectPositionInfo: StateFlow<Boolean>
}

object PreviewTools {
    fun createFirstHalfSampleGame(): Game {
        var game = Game.defaults().copy(
            id = "scheduledGame1",
            homeTeamName = "Alpha FC",
            awayTeamName = "Beta United",
            currentPhase = GamePhase.FIRST_HALF,
            gameDateTimeEpochMillis = System.currentTimeMillis() + (2 * 60 * 60 * 1000L), // 2 hours from now
            refereeAssignment = "Referee",
            venue = "Stadium One",
        )

        game = game.addEvent(
            GoalScoredEvent(
                team = Team.HOME,
                gameTimeMillis = ((2 * 60  + 25) * 1000).toDouble(),
                homeScoreAtTime = 0,
                awayScoreAtTime = 0
            )
        )
        game = game.addEvent(
            GoalScoredEvent(
                team = Team.HOME,
                gameTimeMillis = ((6 * 60  + 45) * 1000).toDouble(),
                homeScoreAtTime = 1,
                awayScoreAtTime = 0
            )
        )
        return game
    }

    fun createExtraFirstHalfSampleGame(): Game {
        return Game.defaults().copy(
            id = "scheduledGame1",
            homeTeamName = "Alpha FC",
            awayTeamName = "Beta United",
            currentPhase = GamePhase.EXTRA_TIME_FIRST_HALF,
            gameDateTimeEpochMillis = System.currentTimeMillis() + (2 * 60 * 60 * 1000L), // 2 hours from now
            refereeAssignment = "Assistant Referee 1",
            venue = "Stadium One",
        )
    }

    fun createPenaltiesSampleGame(): Game {
        return Game.defaults().copy(
            id = "scheduledGame1",
            homeTeamName = "Alpha FC",
            awayTeamName = "Beta United",
            currentPhase = GamePhase.PENALTIES,
            gameDateTimeEpochMillis = System.currentTimeMillis() + (2 * 60 * 60 * 1000L), // 2 hours from now
            venue = "Stadium One",
        )
    }

    fun createSampleGames(): List<Game> {
        return listOf(
            // --- Scheduled Games ---
            Game.defaults().copy(
                id = "scheduledGame1",
                homeTeamName = "Alpha FC",
                awayTeamName = "Beta United",
                gameDateTimeEpochMillis = System.currentTimeMillis() + (2 * 60 * 60 * 1000L), // 2 hours from now
                gameNumber = "123",
                venue = "Stadium One",
            ),
            Game.defaults().copy(
                id = "scheduledGame2",
                homeTeamName = "Gamma Rovers",
                awayTeamName = "Delta City",
                homeTeamColorArgb = Color.parseColor("#3F51B5"), // Indigo
                awayTeamColorArgb = Color.parseColor("#FFC107"), // Amber
                gameDateTimeEpochMillis = System.currentTimeMillis() + (26 * 60 * 60 * 1000L), // 26 hours from now
                gameNumber = "124",
                venue = "Community Park",
            ),

            // --- In-Progress Game Example (for potential "Resume Game" chip) ---
            Game.defaults().copy(
                id = "inProgressGame1",
                homeTeamName = "Red Warriors",
                awayTeamName = "Blue Thunder",
                currentPhase = GamePhase.FIRST_HALF,
                isTimerRunning = true,
                displayedTimeMillis = 15 * 60 * 1000L, // 15:00 on the clock
                actualTimeElapsedInPeriodMillis = 15 * 60 * 1000L + (30 * 1000L), // 15m 30s actual
                halfDurationMinutes = 40,
                homeScore = 1,
                awayScore = 0,
                kickOffTeam = Team.HOME,
                homeTeamColorArgb = Color.RED,
                awayTeamColorArgb = Color.BLUE,
                gameNumber = "125",
            ),

            // --- Completed Games ---
            Game.defaults().copy(
                id = "completedGame1",
                homeTeamName = "Green Hornets",
                awayTeamName = "Purple Haze",
                currentPhase = GamePhase.GAME_ENDED,
                isTimerRunning = false,
                gameDateTimeEpochMillis = System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000L), // 1 day ago
                homeScore = 3,
                awayScore = 2,
                homeTeamColorArgb = Color.GREEN,
                awayTeamColorArgb = Color.MAGENTA, // Using MAGENTA for Purple
                gameNumber = "126",
                venue = "Old Trafford (simulated)",
            ),
            Game.defaults().copy(
                id = "completedGame2",
                homeTeamName = "Black Cats",
                awayTeamName = "White Knights",
                currentPhase = GamePhase.GAME_ENDED,
                gameDateTimeEpochMillis = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L), // 5 days ago
                homeScore = 0,
                awayScore = 0,
                homeTeamColorArgb = Color.BLACK,
                awayTeamColorArgb = Color.WHITE,
                gameNumber = "127",
                venue = "The Den",
            ),
            // Your example item (slightly adjusted if `halfDurationMinutes` affects display directly)
            Game.defaults().copy(
                id = "previewGameYourExample",
                currentPhase = GamePhase.GAME_ENDED,
                homeTeamName = "Red Team Example",
                awayTeamName = "Blue Team Example",
                homeTeamColorArgb = Color.BLACK, // As per your example
                awayTeamColorArgb = Color.YELLOW,
                kickOffTeam = Team.AWAY,
                actualTimeElapsedInPeriodMillis = (5 * 60000L), // 5 minutes (assuming your (5*L)+(2*L) was an example)
                displayedTimeMillis = (45 * 60000L) - (5 * 60000L), // If half is 45m, and 5m elapsed, 40m displayed (countdown)
                halfDurationMinutes = 45,
                homeScore = 2,
                isTimerRunning = true, // Implied if it's FIRST_HALF with time elapsed
                gameNumber = "128",
            )
        )
    }
}