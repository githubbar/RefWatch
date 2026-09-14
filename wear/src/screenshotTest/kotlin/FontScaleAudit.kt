package com.databelay.refwatch.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.MaterialTheme
import com.android.tools.screenshot.PreviewTest
import com.databelay.refwatch.common.CardIssuedEvent
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.GoalScoredEvent
import com.databelay.refwatch.common.PhaseChangedEvent
import com.databelay.refwatch.common.PreviewTools.createFirstHalfSampleGame
import com.databelay.refwatch.common.PreviewTools.createSampleGames
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.shortName
import com.databelay.refwatch.common.theme.PredefinedJerseyColors
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.presentation.screens.ConfirmationDialogInfo
import com.databelay.refwatch.wear.presentation.screens.GameAnalyticsScreen
import com.databelay.refwatch.wear.presentation.screens.GameListScreen
import com.databelay.refwatch.wear.presentation.screens.GameLogScreen
import com.databelay.refwatch.wear.presentation.screens.GameSettingsScreen
import com.databelay.refwatch.wear.presentation.screens.GoalScorerScreen
import com.databelay.refwatch.wear.presentation.screens.KickOffSelectionScreen
import com.databelay.refwatch.wear.presentation.screens.LogCardScreen
import com.databelay.refwatch.wear.presentation.screens.MainGameDisplayScreen
import com.databelay.refwatch.wear.presentation.screens.PenaltyShootoutScreen
import com.databelay.refwatch.wear.presentation.screens.PermissionRequiredDialogContent
import com.databelay.refwatch.wear.presentation.screens.PreGameSetupScreen
import com.databelay.refwatch.wear.presentation.screens.SecondYellowDialogContent
import com.databelay.refwatch.wear.presentation.screens.SimpleColorPickerDialogContent
import com.databelay.refwatch.wear.presentation.screens.TeamActionsPage
import com.databelay.refwatch.wear.presentation.screens.UnifiedConfirmationDialogContent
import java.util.TimeZone

/**
 * Renders every screen and dialog at the largest font scale Wear OS offers (1.24, "Largest"
 * in the watch's Settings app) on the smallest round screen. These exist
 * to be looked at: run `gradlew :wear:updateDebugScreenshotTest` and inspect the PNGs.
 *
 * Coverage matters more than anything else here. Play rejected the app repeatedly after
 * this audit passed, because it only covered full screens: the confirmation dialogs -- which
 * a reviewer reaches from the game menu -- were never rendered, and their fixed-size icon
 * buttons clipped the words "Confirm" and "Dismiss". Anything the user can reach belongs in
 * this file, dialogs included. A [androidx.wear.compose.material3.Dialog] opens its own
 * window that preview rendering does not capture, so render the `*Content` composable.
 *
 * Long team names are deliberate -- the default "Home"/"Away" fit at any scale and hide the
 * overflow that real fixtures produce. The defaults are covered too, since a reviewer with
 * no synced fixtures only ever sees those.
 *
 * NOT covered: the watch's "Bold text" setting. Compose reads it from the context
 * configuration, which preview rendering ignores -- a font resolver built from a bold
 * configuration context rendered pixel-identical. Check bold on a real device.
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

/**
 * Paints the black ground a Dialog window normally provides; dialog content has no
 * background of its own, so without this its light text renders on the preview's white.
 */
@Composable
private fun DialogFrame(content: @Composable () -> Unit) {
    RefWatchWearTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            content()
        }
    }
}

/** What a fresh "New Game" looks like -- the only thing a reviewer without fixtures sees. */
private fun defaultGame(phase: GamePhase) = Game.defaults().copy(
    id = "audit-default",
    currentPhase = phase,
)

// ------------------------------------------------------------------------------------------
// Screens
// ------------------------------------------------------------------------------------------

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
            onNavigateToNewGame = {},
            versionLabel = "Version: 1.2.0"
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "01b GameList empty offline")
@Composable
fun Audit_GameList_Empty() {
    RefWatchWearTheme {
        GameListScreen(
            allGames = emptyList(),
            activeGame = null,
            isOnline = false,
            onGameSelected = {},
            onViewLog = {},
            onNavigateToNewGame = {},
            versionLabel = "Version: 1.2.0 2026-09-14 12:00:00"
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
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "02b ColorPicker")
@Composable
fun Audit_ColorPicker() {
    RefWatchWearTheme {
        SimpleColorPickerDialogContent(
            title = "Home Color",
            availableColors = PredefinedJerseyColors,
            onColorSelected = {},
            onDismiss = {}
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
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "03b KickOff default names")
@Composable
fun Audit_KickOff_Default() {
    RefWatchWearTheme {
        KickOffSelectionScreen(
            game = defaultGame(GamePhase.KICK_OFF_SELECTION_FIRST_HALF),
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
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "04b MainGame before kick off")
@Composable
fun Audit_MainGameDisplay_KickOff() {
    RefWatchWearTheme {
        MainGameDisplayScreen(
            game = defaultGame(GamePhase.FIRST_HALF).copy(kickOffTeam = Team.HOME),
            onKickOff = {}
        )
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
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "05b MainGame Halftime")
@Composable
fun Audit_MainGameDisplay_Halftime() {
    RefWatchWearTheme {
        MainGameDisplayScreen(
            game = auditGame(GamePhase.HALF_TIME).copy(
                halftimeDurationMinutes = 15,
                actualTimeElapsedInPeriodMillis = 5 * 60000L + 25000L
            ),
            onKickOff = {}
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "05c MainGame Halftime over")
@Composable
fun Audit_MainGameDisplay_HalftimeOver() {
    RefWatchWearTheme {
        MainGameDisplayScreen(
            game = auditGame(GamePhase.EXTRA_TIME_HALF_TIME).copy(
                actualTimeElapsedInPeriodMillis = 20 * 60000L
            ),
            onKickOff = {}
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "05d MainGame Full time")
@Composable
fun Audit_MainGameDisplay_FullTime() {
    RefWatchWearTheme {
        MainGameDisplayScreen(
            game = auditGame(GamePhase.GAME_ENDED).copy(isTimerRunning = false),
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

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "10 GameLog")
@Composable
fun Audit_GameLog() {
    // Each entry shows its wall-clock time. Fixed timestamps plus a pinned zone keep the
    // render identical on every machine, including CI.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val start = 1_757_851_200_000.0 // 2025-09-14 12:00:00 UTC
    val game = auditGame().copy(
        events = listOf(
            PhaseChangedEvent(id = "e1", newPhase = GamePhase.FIRST_HALF, timestamp = start, gameTimeMillis = 0.0),
            GoalScoredEvent(
                id = "e2", team = Team.HOME, timestamp = start + 300_000, gameTimeMillis = 300_000.0,
                homeScoreAtTime = 1, awayScoreAtTime = 0, playerNumber = 10
            ),
            CardIssuedEvent(
                id = "e3", team = Team.AWAY, playerNumber = 4, cardType = CardType.YELLOW,
                timestamp = start + 600_000, gameTimeMillis = 600_000.0
            ),
        )
    )
    RefWatchWearTheme {
        GameLogScreen(game = game, onDismiss = {}, onRemoveEvent = {})
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

// ------------------------------------------------------------------------------------------
// Dialogs -- every ConfirmationDialogInfo variant plus the dialogs in Navigation.kt
// ------------------------------------------------------------------------------------------

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "13a Dialog EndPhase")
@Composable
fun Audit_Dialog_EndPhase() {
    DialogFrame {
        UnifiedConfirmationDialogContent(
            ConfirmationDialogInfo.EndPhase("1st Half (ET)", onConfirm = {}, onDialogClose = {})
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "13b Dialog FinishGame")
@Composable
fun Audit_Dialog_FinishGame() {
    DialogFrame {
        UnifiedConfirmationDialogContent(
            ConfirmationDialogInfo.FinishGame(onConfirm = {}, onDialogClose = {})
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "13c Dialog ResetTimer")
@Composable
fun Audit_Dialog_ResetPeriodTimer() {
    DialogFrame {
        UnifiedConfirmationDialogContent(
            ConfirmationDialogInfo.ResetPeriodTimer("2nd Half (ET)", onConfirm = {}, onDialogClose = {})
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "13d Dialog ResetGame")
@Composable
fun Audit_Dialog_ResetFullGame() {
    DialogFrame {
        UnifiedConfirmationDialogContent(
            ConfirmationDialogInfo.ResetFullGame(onConfirm = {}, onDialogClose = {})
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "13e Dialog ExtraTime")
@Composable
fun Audit_Dialog_EndOfMainTime() {
    DialogFrame {
        UnifiedConfirmationDialogContent(
            ConfirmationDialogInfo.EndOfMainTime(
                onSetExtraTimeAndPenalties = {},
                onSetPenaltiesOnly = {},
                onEndPhaseWithoutExtraTime = {},
                onDialogClose = {}
            )
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "13f Dialog DeleteEvent")
@Composable
fun Audit_Dialog_RemoveLogEvent() {
    DialogFrame {
        UnifiedConfirmationDialogContent(
            ConfirmationDialogInfo.RemoveLogEvent(onConfirm = {}, onDialogClose = {})
        )
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "13g Dialog SecondYellow")
@Composable
fun Audit_Dialog_SecondYellow() {
    DialogFrame {
        SecondYellowDialogContent(team = Team.AWAY, playerNumber = 99, onDismiss = {})
    }
}

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, fontScale = MAX_FONT_SCALE, name = "13h Dialog Permissions")
@Composable
fun Audit_Dialog_Permissions() {
    DialogFrame {
        PermissionRequiredDialogContent(onOpenSettings = {}, onDismiss = {})
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

@PreviewTest
@Preview(device = SMALL_ROUND, showSystemUi = true, name = "23 Dialog FinishGame 1x")
@Composable
fun Audit_Dialog_FinishGame_Normal() {
    DialogFrame {
        UnifiedConfirmationDialogContent(
            ConfirmationDialogInfo.FinishGame(onConfirm = {}, onDialogClose = {})
        )
    }
}
