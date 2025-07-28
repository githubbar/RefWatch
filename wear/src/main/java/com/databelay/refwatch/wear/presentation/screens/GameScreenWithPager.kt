package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi // Keep for Pager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.databelay.refwatch.common.*
import com.databelay.refwatch.wear.presentation.components.ConfirmationDialog
import com.databelay.refwatch.presentation.screens.pager.MainGameDisplayScreen
import com.databelay.refwatch.wear.presentation.components.TeamActionsPage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameScreenWithPager(
    activeGame: Game,                   // <<< Receives the active Game state
    // Lambdas for actions the Pager or its settings dialog might trigger
    onToggleTimer: () -> Unit,
    onAddGoal: (Team) -> Unit,
    onNavigateToLogCard: (Team) -> Unit, // Changed to pass the team
    onNavigateToGameLog: () -> Unit,
    onKickOff: () -> Unit,
    onEndPhase: () -> Unit,
    onResetGame: () -> Unit,
    onStartExtraTime: () -> Unit,
    onConfirmEndMatch: () -> Unit // To distinguish from onFinishGame
) {
    val pagerState = rememberPagerState(initialPage = 1) { 3 } // 0: Home Actions, 1: Main Display, 2: Away Actions
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showEndOfMainTimeDialog by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { // Long press anywhere to open settings
                detectTapGestures(
                    onLongPress = {
                        showSettingsDialog = true
                    }
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // beyondBoundsPageCount = 1 // Consider if needed for performance/preloading
        ) { page ->
            when (page) {
                0 -> TeamActionsPage(
                    team = Team.HOME,
                    teamColor = activeGame.homeTeamColor,
                    onAddGoal = { onAddGoal(Team.HOME) },
                    onLogCard = { onNavigateToLogCard(Team.HOME) }
                )
                1 -> MainGameDisplayScreen( // The main timer/score view
                    game = activeGame,
                    onToggleTimer = onToggleTimer,
                    onEndPhaseEarly = onEndPhase,
                    onKickOff = onKickOff
                )
                2 -> TeamActionsPage(
                    team = Team.AWAY,
                    teamColor = activeGame.awayTeamColor,
                    onAddGoal = { onAddGoal(Team.AWAY) },
                    onLogCard = { onNavigateToLogCard(Team.AWAY) }
                )
            }
        }

//        HorizontalPagerIndicator(
//            pagerState = pagerState,
//            modifier = Modifier
//                .align(Alignment.BottomCenter)
//                .padding(bottom = 8.dp)
//            // ... other indicator properties
//        )
        if (showSettingsDialog) {
            GameSettingsDialog(
                game = activeGame,
                onDismiss = { showSettingsDialog = false },
                onViewLog = {
                    showSettingsDialog = false
                    onNavigateToGameLog() // Use the passed lambda
                },
                onFinishGame = {
                    // --- FINISH CODE IS HERE ---
                    // Hide the settings dialog first.
                    showSettingsDialog = false
                    // Then, call the onFinishGame callback that was passed from the NavHost.
                    // This signals the user's intent to the parent composable.
                    onConfirmEndMatch()
                },
                // FIXME: change to "reset current period timer"
                onResetGame = { // This is "End Game & Reset" from settings
                    showSettingsDialog = false
                    showResetConfirmDialog = true // Show confirmation for reset
                },
                onToggleTimer = {
                    showSettingsDialog = false // Also dismiss menu on timer toggle
                    onToggleTimer()
                },
                // TODO: "start penalties/end match" after 2nd extra half
                // TODO: add HOME/AWAY and "record penalty" screen maybe with swiping left and right
                // TODO: test vibrate alarm at the end of each timed game phase
                onEndPhase = {
                    showSettingsDialog = false // Also dismiss menu on timer toggle
                    showEndOfMainTimeDialog = activeGame.currentPhase == GamePhase.SECOND_HALF
                    if (!showEndOfMainTimeDialog) onEndPhase()
                },
                isTimerRunning = activeGame.isTimerRunning, // Get from game state
                // Determine if game is active or finished based on game.currentPhase
                isGameActive = activeGame.currentPhase != GamePhase.FULL_TIME && activeGame.currentPhase != GamePhase.PRE_GAME,
                isGameFinished = activeGame.currentPhase == GamePhase.FULL_TIME
            )
        }

        if (showResetConfirmDialog) {
            val message = if (activeGame.currentPhase == GamePhase.FULL_TIME || activeGame.currentPhase == GamePhase.PRE_GAME) {
                "Start a new default game? Current game settings will be reset." // Or "Select new game from schedule?"
            } else {
                "End the current game and reset to pre-game defaults?"
            }
            ConfirmationDialog(
                message = message,
                onConfirm = {
                    showResetConfirmDialog = false
                    onResetGame() // Call the passed lambda for resetting
                },
                onDismiss = { showResetConfirmDialog = false }
            )
        }

        // --- New Dialog for End of Main Time ---
        EndOfMainTimeDialog(
            showDialog = showEndOfMainTimeDialog,
            onDismiss = { showEndOfMainTimeDialog = false /* Consider if dismissing should do anything else */ },
            onStartExtraTime = {
                showEndOfMainTimeDialog = false
                onStartExtraTime() // Call the lambda passed from NavHost/ViewModel
            },
            onEndMatch = {
                showEndOfMainTimeDialog = false
                onConfirmEndMatch() // Call the lambda passed from NavHost/ViewModel
            }
        )
    }
}