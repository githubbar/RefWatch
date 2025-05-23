package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi // Keep for Pager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.databelay.refwatch.common.*
import com.databelay.refwatch.wear.presentation.components.ConfirmationDialog
import com.databelay.refwatch.presentation.screens.pager.MainGameDisplayScreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameScreenWithPager(
    activeGame: Game,                   // <<< Receives the active Game state
    // Lambdas for actions the Pager or its settings dialog might trigger
    onToggleTimer: () -> Unit,
    onAddGoal: (Team) -> Unit,
    // TODO: add onaddcard
    //onAddCard is more complex: either navigate or show a dialog from here.
    // Let's assume navigation for now.
    onNavigateToLogCard: () -> Unit,
    onNavigateToGameLog: () -> Unit,
    onEndPhaseEarly: () -> Unit,
    onResetGame: () -> Unit // For resetting the entire game state
) {
    val pagerState = rememberPagerState(initialPage = 1) { 3 } // 0: Home Actions, 1: Main Display, 2: Away Actions
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showEndGameConfirmDialog by remember { mutableStateOf(false) }

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
        ) { pageIndex ->
            // Pass the 'game' state and relevant action lambdas to each page
            when (pageIndex) {
                0 -> HomeTeamActionScreen(
                    game = activeGame, // Pass game state
                    onAddGoal = { onAddGoal(Team.HOME) },
                    onLogCard = onNavigateToLogCard, // Or pass team specific lambda if LogCardScreen expects it
                    modifier = Modifier.fillMaxSize()
                )
                1 -> MainGameDisplayScreen( // Renamed for clarity
                    game = activeGame, // Pass game state
                    onToggleTimer = onToggleTimer,
                    onEndPhaseEarly = onEndPhaseEarly,
                    modifier = Modifier.fillMaxSize()
                )
                2 -> AwayTeamActionScreen(
                    game = activeGame, // Pass game state
                    onAddGoal = { onAddGoal(Team.AWAY) },
                    onLogCard = onNavigateToLogCard, // Or pass team specific lambda
                    modifier = Modifier.fillMaxSize()
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
                onDismiss = { showSettingsDialog = false },
                onViewLog = {
                    showSettingsDialog = false
                    onNavigateToGameLog() // Use the passed lambda
                },
                onResetGame = { // This is "End Game & Reset" from settings
                    showSettingsDialog = false
                    showEndGameConfirmDialog = true // Show confirmation for reset
                },
                onToggleTimer = onToggleTimer, // Pass toggle timer lambda
                isTimerRunning = activeGame.isTimerRunning, // Get from game state
                // Determine if game is active or finished based on game.currentPhase
                isGameActive = activeGame.currentPhase != GamePhase.FULL_TIME && activeGame.currentPhase != GamePhase.PRE_GAME,
                isGameFinished = activeGame.currentPhase == GamePhase.FULL_TIME
            )
        }

        if (showEndGameConfirmDialog) {
            val message = if (activeGame.currentPhase == GamePhase.FULL_TIME || activeGame.currentPhase == GamePhase.PRE_GAME) {
                "Start a new default game? Current game settings will be reset." // Or "Select new game from schedule?"
            } else {
                "End the current game and reset to pre-game defaults?"
            }
            ConfirmationDialog(
                message = message,
                onConfirm = {
                    showEndGameConfirmDialog = false
                    onResetGame() // Call the passed lambda for resetting
                },
                onDismiss = { showEndGameConfirmDialog = false }
            )
        }
    }
}