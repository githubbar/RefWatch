package com.databelay.refwatch.wear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.android.tools.screenshot.PreviewTest
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.presentation.screens.GameScreenWithPager

// -------------------------------- Previews -----------------------------------------------

@PreviewTest
@Preview(
    device = "id:wearos_small_round",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun TestPreview() {
    RefWatchWearTheme {
        Text("Hello World")
    }
}

@PreviewTest
@OptIn(ExperimentalFoundationApi::class)
@Preview(
    device = "id:wearos_large_round",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun GameScreenWithPagerScreenshotPreview() {
    val sampleGame = Game.defaults().copy(
        currentPhase = GamePhase.FIRST_HALF,
        isTimerRunning = true,
        actualTimeElapsedInPeriodMillis = (10 * 60000L)
    )
    val horizontalPagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val verticalPagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    RefWatchWearTheme {
        GameScreenWithPager(
            game = sampleGame,
            collectPositionInfo = true,
            onToggleCollectPositionInfo = {},
            horizontalPagerState = horizontalPagerState,
            verticalPagerState = verticalPagerState,
            onKickOff = {},
            onResetGame = {},
            onSetToHaveExtraTime = {},
            onSetToHavePenalties = {},
            onToggleTimer = {},
            onAddGoal = {},
            onNavigateToLogCard = { _: Team, _: CardType -> },
            onNavigateToGameLog = {},
            onNavigateToAnalytics = {},
            onEndPhase = {},
            onResetPeriodTimer = {},
            onConfirmEndMatch = {},
            onPenaltyAttemptRecorded = {}
        )
    }
}

@PreviewTest
@OptIn(ExperimentalFoundationApi::class)
@Preview(
    device = "id:wearos_large_round",
    name = "Settings Page Open",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun GameScreenWithPagerSettingsScreenshotPreview() {
    val sampleGame = Game.defaults().copy(currentPhase = GamePhase.FIRST_HALF)
    val horizontalPagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val verticalPagerState =
        rememberPagerState(initialPage = 1, pageCount = { 2 }) // Start on settings page

    RefWatchWearTheme {
        GameScreenWithPager(
            game = sampleGame,
            collectPositionInfo = true,
            onToggleCollectPositionInfo = {},
            horizontalPagerState = horizontalPagerState,
            verticalPagerState = verticalPagerState,
            onKickOff = {},
            onResetGame = {},
            onSetToHaveExtraTime = {},
            onSetToHavePenalties = {},
            onToggleTimer = {},
            onAddGoal = {},
            onNavigateToLogCard = { _: Team, _: CardType -> },
            onNavigateToGameLog = {},
            onNavigateToAnalytics = {},
            onEndPhase = {},
            onResetPeriodTimer = {},
            onConfirmEndMatch = {},
            onPenaltyAttemptRecorded = {}
        )
    }
}

@PreviewTest
@OptIn(ExperimentalFoundationApi::class)
@Preview(
    device = "id:wearos_large_round",
    name = "Penalties Screenshot",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun GameScreenWithPagerPenaltiesScreenshotPreview() {
    val sampleGame = Game.defaults().copy(currentPhase = GamePhase.PENALTIES)
    val horizontalPagerState = rememberPagerState(initialPage = 1, pageCount = { 1 })
    val verticalPagerState =
        rememberPagerState(initialPage = 0, pageCount = { 2 })

    RefWatchWearTheme {
        GameScreenWithPager(
            game = sampleGame,
            collectPositionInfo = true,
            onToggleCollectPositionInfo = {},
            horizontalPagerState = horizontalPagerState,
            verticalPagerState = verticalPagerState,
            onKickOff = {},
            onResetGame = {},
            onSetToHaveExtraTime = {},
            onSetToHavePenalties = {},
            onToggleTimer = {},
            onAddGoal = {},
            onNavigateToLogCard = { _: Team, _: CardType -> },
            onNavigateToGameLog = {},
            onNavigateToAnalytics = {},
            onEndPhase = {},
            onResetPeriodTimer = {},
            onConfirmEndMatch = {},
            onPenaltyAttemptRecorded = {}
        )
    }
}
