package com.databelay.refwatch.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.PreviewTools.createFirstHalfSampleGame
import com.databelay.refwatch.common.PreviewTools.createSampleGames
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.shortName
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.presentation.screens.GameAnalyticsScreen
import com.databelay.refwatch.wear.presentation.screens.GameListScreen
import com.databelay.refwatch.wear.presentation.screens.GameLogScreen
import com.databelay.refwatch.wear.presentation.screens.GameSettingsScreen
import com.databelay.refwatch.wear.presentation.screens.GoalScorerScreen
import com.databelay.refwatch.wear.presentation.screens.KickOffSelectionScreen
import com.databelay.refwatch.wear.presentation.screens.LogCardScreen
import com.databelay.refwatch.wear.presentation.screens.MainGameDisplayScreen
import com.databelay.refwatch.wear.presentation.screens.PenaltyShootoutScreen
import com.databelay.refwatch.wear.presentation.screens.PreGameSetupScreen
import com.databelay.refwatch.wear.presentation.screens.TeamActionsPage

/**
 * Renders every screen at the largest font scale Wear OS offers (1.24) on the smallest
 * round screen, which is the combination Play's reviewers use. These exist to be looked
 * at: run `gradlew :wear:updateDebugScreenshotTest` and inspect the PNGs.
 *
 * Long team names are deliberate -- the default "Home"/"Away" fit at any scale and hide
 * the overflow that real fixtures produce.
 */
private const val SMALL_ROUND = "id:wearos_small_round"
private const val MAX_FONT_SCALE = 1.24f

private fun auditGame(phase: GamePhase = GamePhase.FIRST_HALF) = Game.defaults().copy(
    id = "audit",
    homeTeamName = "Wanderers Athletic",
    awayTeamName = "Northside Rovers",
    currentPhase = phase,
    homeScore = 10,
    awayScore = 8,
    halfDurationMinutes = 45,
    actualTimeElapsedInPeriodMillis = 12 * 60000L,
    isTimerRunning = true
)

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "01 GameList")
@Composable
fun Audit_GameList() {
    RefWatchWearTheme {
        GameListScreen(
            allGames = createSampleGames(),
            activeGame = null,
            isOnline = true,
            onGameSelected = {},
            onViewLog = {},
            onNavigateToNewGame = {}
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "02 PreGameSetup")
@Composable
fun Audit_PreGameSetup() {
    RefWatchWearTheme {
        PreGameSetupScreen(
            game = auditGame(GamePhase.PRE_GAME),
            onEditHomeTeamNameClick = {},
            onEditAwayTeamNameClick = {},
            onHomeColorPickerClick = {},
            onAwayColorPickerClick = {},
            onSetHalfDuration = {},
            onSetHalftimeDuration = {},
            onCreateMatchClick = {}
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "03 KickOff")
@Composable
fun Audit_KickOff() {
    RefWatchWearTheme {
        KickOffSelectionScreen(
            game = auditGame(GamePhase.KICK_OFF_SELECTION_FIRST_HALF),
            onSetKickOffTeam = {}
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "04 MainGame")
@Composable
fun Audit_MainGameDisplay() {
    RefWatchWearTheme {
        MainGameDisplayScreen(game = auditGame(), onKickOff = {})
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "05 MainGame AddedTime")
@Composable
fun Audit_MainGameDisplay_AddedTime() {
    RefWatchWearTheme {
        MainGameDisplayScreen(
            game = auditGame().copy(
                actualTimeElapsedInPeriodMillis = (45 * 60000L) + (3 * 60000L) + 20000L
            ),
            onKickOff = {}
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "06 TeamActions")
@Composable
fun Audit_TeamActions() {
    RefWatchWearTheme {
        TeamActionsPage(
            team = Team.HOME,
            teamName = shortName("Wanderers Athletic"), // as GamePagerContent passes it
            teamColor = Color.Blue,
            isPlayablePhase = true,
            onAddGoal = {},
            onNavigateToLogCard = { _, _ -> }
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "07 GameSettings")
@Composable
fun Audit_GameSettings() {
    RefWatchWearTheme {
        GameSettingsScreen(
            game = auditGame(GamePhase.EXTRA_TIME_FIRST_HALF),
            onAttemptFinishGame = {},
            onAttemptResetPeriodTimer = {},
            onAttemptResetFullGame = {},
            onViewLog = {},
            onViewAnalytics = {},
            onToggleTimer = {},
            onAttemptEndPhase = {}
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "08 LogCard Yellow")
@Composable
fun Audit_LogCard() {
    RefWatchWearTheme {
        LogCardScreen(
            preselectedTeam = Team.HOME,
            cardType = CardType.YELLOW,
            onLogCard = { _, _, _ -> },
            onCancel = {}
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "09 GoalScorer")
@Composable
fun Audit_GoalScorer() {
    RefWatchWearTheme {
        GoalScorerScreen(
            team = Team.HOME,
            teamName = shortName("Wanderers Athletic"), // as GamePagerContent passes it
            teamColor = Color.Blue,
            onConfirm = {},
            onSkip = {}
        )
    }
}

// Not a @PreviewTest: GameLog stamps each event with System.currentTimeMillis(), so its
// render differs every run and could never match a stored baseline. Kept as a plain
// preview so it can still be inspected in Android Studio.
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "10 GameLog")
@Composable
fun Audit_GameLog() {
    RefWatchWearTheme {
        GameLogScreen(
            game = createFirstHalfSampleGame(),
            onDismiss = {},
            onRemoveEvent = {}
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "11 Analytics")
@Composable
fun Audit_Analytics() {
    RefWatchWearTheme {
        GameAnalyticsScreen(
            game = createFirstHalfSampleGame(),
            collectPositionInfo = true,
            onDismiss = {}
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "12 Penalties")
@Composable
fun Audit_Penalties() {
    RefWatchWearTheme {
        PenaltyShootoutScreen(
            game = auditGame(GamePhase.PENALTIES),
            onPenaltyAttemptRecorded = {}
        )
    }
}

// --- The same changed screens at the default font scale, to confirm the fixes for large
// --- text did not leave the common case looking sparse or oversized.

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, name = "20 TeamActions 1x")
@Composable
fun Audit_TeamActions_Normal() {
    RefWatchWearTheme {
        TeamActionsPage(
            team = Team.HOME,
            teamName = "Wanderers",
            teamColor = Color.Blue,
            isPlayablePhase = true,
            onAddGoal = {},
            onNavigateToLogCard = { _, _ -> }
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, name = "21 LogCard 1x")
@Composable
fun Audit_LogCard_Normal() {
    RefWatchWearTheme {
        LogCardScreen(
            preselectedTeam = Team.AWAY,
            cardType = CardType.RED,
            onLogCard = { _, _, _ -> },
            onCancel = {}
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, name = "22 GoalScorer 1x")
@Composable
fun Audit_GoalScorer_Normal() {
    RefWatchWearTheme {
        GoalScorerScreen(
            team = Team.HOME,
            teamName = "Wanderers",
            teamColor = Color.Blue,
            onConfirm = {},
            onSkip = {}
        )
    }
}
