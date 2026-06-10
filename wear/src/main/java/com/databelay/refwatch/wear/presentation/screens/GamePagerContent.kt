package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerState
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.isPlayablePhase
import com.databelay.refwatch.common.shortName
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.PagerScaffoldDefaults
import androidx.wear.tooling.preview.devices.WearDevices
import com.databelay.refwatch.common.theme.RefWatchWearTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamePagerContent(
    game: Game,
    pagerState: PagerState,
    onKickOff: () -> Unit,
    onAddGoal: (Team) -> Unit,
    onNavigateToLogCard: (Team, CardType) -> Unit,
    onPenaltyAttemptRecorded: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    val isPenaltiesPhase = game.currentPhase == GamePhase.PENALTIES
    val isPlayableRegularPhase = game.currentPhase.isPlayablePhase() && !isPenaltiesPhase

    Box(modifier = modifier) {
        when {
            isPenaltiesPhase -> {
                PenaltyShootoutScreen(
                    game = game,
                    onPenaltyAttemptRecorded = onPenaltyAttemptRecorded,
                    modifier = Modifier.fillMaxSize()
                )
            }

            isPlayableRegularPhase -> {
                HorizontalPagerScaffold(
                    pagerState = pagerState,
                    pageIndicator = { HorizontalPageIndicator(pagerState = pagerState) },
                ) {
                    HorizontalPager(
                        state = pagerState,
                        flingBehavior = PagerScaffoldDefaults.snapWithSpringFlingBehavior(state = pagerState),
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        when (page) {
                            0 -> AnimatedPage(pageIndex = page, pagerState = pagerState) {
                                val onAddGoalHome = remember(onAddGoal, coroutineScope, pagerState) {
                                    { _: Team ->
                                        onAddGoal(Team.HOME)
                                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                        Unit
                                    }
                                }
                                TeamActionsPage(
                                    team = Team.HOME,
                                    teamName = remember(game.homeTeamName) { shortName(game.homeTeamName) },
                                    teamColor = game.homeTeamColor,
                                    isPlayablePhase = remember(game.currentPhase) { game.currentPhase.isPlayablePhase() },
                                    onAddGoal = onAddGoalHome,
                                    onNavigateToLogCard = onNavigateToLogCard
                                )
                            }
                            1 -> AnimatedPage(pageIndex = page, pagerState = pagerState) {
                                MainGameDisplayScreen(
                                    game = game,
                                    onKickOff = onKickOff
                                )
                            }
                            2 -> AnimatedPage(pageIndex = page, pagerState = pagerState) {
                                val onAddGoalAway = remember(onAddGoal, coroutineScope, pagerState) {
                                    { _: Team ->
                                        onAddGoal(Team.AWAY)
                                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                        Unit
                                    }
                                }
                                TeamActionsPage(
                                    team = Team.AWAY,
                                    teamName = remember(game.awayTeamName) { shortName(game.awayTeamName) },
                                    teamColor = game.awayTeamColor,
                                    isPlayablePhase = remember(game.currentPhase) { game.currentPhase.isPlayablePhase() },
                                    onAddGoal = onAddGoalAway,
                                    onNavigateToLogCard = onNavigateToLogCard
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                MainGameDisplayScreen(
                    game = game,
                    onKickOff = onKickOff,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "GamePagerContent - Playable")
@Composable
fun GamePagerContentPreview_Playable() {
    val sampleGame = Game.defaults().copy(currentPhase = GamePhase.FIRST_HALF)
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    RefWatchWearTheme {
        GamePagerContent(
            game = sampleGame,
            pagerState = pagerState,
            onKickOff = {},
            onAddGoal = {},
            onNavigateToLogCard = { _, _ -> },
            onPenaltyAttemptRecorded = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "GamePagerContent - Half Time")
@Composable
fun GamePagerContentPreview_HalfTime() {
    val sampleGame = Game.defaults().copy(currentPhase = GamePhase.HALF_TIME)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 1 })
    RefWatchWearTheme {
        GamePagerContent(
            game = sampleGame,
            pagerState = pagerState,
            onKickOff = {},
            onAddGoal = {},
            onNavigateToLogCard = { _, _ -> },
            onPenaltyAttemptRecorded = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
