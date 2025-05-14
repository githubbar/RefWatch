package com.databelay.refwatch.presentation.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager // Pager import
import androidx.compose.foundation.pager.rememberPagerState // Pager state
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.wear.compose.material.*
import androidx.compose.ui.unit.dp

import com.databelay.refwatch.data.GameState
import com.databelay.refwatch.data.GamePhase
import com.databelay.refwatch.presentation.components.ConfirmationDialog
import com.databelay.refwatch.presentation.screens.GameSettingsDialog // Your existing dialog
import com.databelay.refwatch.presentation.components.HorizontalPagerIndicator

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class) // For Pager
@Composable
fun GameScreenWithPager(
    gameState: GameState,
    onPauseResume: () -> Unit,
    onAddGoalHome: () -> Unit,
    onAddGoalAway: () -> Unit,
    onLogCardForHome: () -> Unit, // To navigate to LogCardScreen for Home
    onLogCardForAway: () -> Unit, // To navigate to LogCardScreen for Away
    onViewLog: () -> Unit,
    onEndPeriod: () -> Unit,
    onResetGame: () -> Unit,
    isTimerRunning: Boolean // Pass this explicitly for the dialog
) {
    val pagerState = rememberPagerState(initialPage = 1) { 3 } // 0: Home, 1: Main, 2: Away

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showEndGameConfirmDialog by remember { mutableStateOf(false) }
    // showResetConfirmDialog is likely handled within the EndGameConfirm now

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        // Only show settings if on the main page (pagerState.currentPage == 1)
                        // Or allow from any page - your preference. For simplicity, allow from any.
                        showSettingsDialog = true
                    }
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
//            beyondBoundsPageCount = 1 // Keep adjacent pages composed
        ) { pageIndex ->
            when (pageIndex) {
                0 -> HomeTeamActionScreen(
                    gameState = gameState,
                    onAddGoalHome = onAddGoalHome,
                    onLogCardForHome = onLogCardForHome,
                    modifier = Modifier.fillMaxSize()
                )
                1 -> SimplifiedGameScreen(
                    gameState = gameState,
                    modifier = Modifier.fillMaxSize()
                )
                2 -> AwayTeamActionScreen(
                    gameState = gameState,
                    onAddGoalAway = onAddGoalAway,
                    onLogCardForAway = onLogCardForAway,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

//        // Page indicators (optional, but good for UX)
//        HorizontalPagerIndicator(
//            pagerState = pagerState,
//            modifier = Modifier
//                .align(Alignment.BottomCenter)
//                .padding(bottom = 8.dp),
//            activeColor = MaterialTheme.colors.primary,
//            inactiveColor = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
//        )

        if (showSettingsDialog) {
            // Pass the onPauseResume callback and isTimerRunning to the dialog
            GameSettingsDialog(
                onDismiss = { showSettingsDialog = false },
                onViewLog = {
                    showSettingsDialog = false
                    onViewLog()
                },
                onResetGame = {
                    showSettingsDialog = false
                    showEndGameConfirmDialog = true
                },
                onPauseResume = onPauseResume, // New callback
                isTimerRunning = isTimerRunning, // Pass current timer state
                isGameActive = gameState.currentPhase != GamePhase.FULL_TIME && gameState.currentPhase != GamePhase.PRE_GAME,
                isGameFinished = gameState.currentPhase == GamePhase.FULL_TIME
            )
        }

        if (showEndGameConfirmDialog) {
            val message = if (gameState.currentPhase == GamePhase.FULL_TIME || gameState.currentPhase == GamePhase.PRE_GAME) {
                "Start a new game? Current data will be lost."
            } else {
                "Are you sure you want to end the current game and reset?"
            }
            ConfirmationDialog(
                message = message,
                onConfirm = {
                    showEndGameConfirmDialog = false
                    onResetGame()
                },
                onDismiss = { showEndGameConfirmDialog = false }
            )
        }
    }
}