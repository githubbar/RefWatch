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
import com.databelay.refwatch.presentation.screens.pager.PenaltyShootoutScreen
import com.databelay.refwatch.wear.WearGameViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameScreenWithPager(
    gameViewModel: WearGameViewModel,
// Lambdas for actions the Pager or its settings dialog might trigger
    onToggleTimer: () -> Unit,
    onAddGoal: (Team) -> Unit,
    onNavigateToLogCard: (team: Team, cardType: CardType) -> Unit,
    onNavigateToGameLog: () -> Unit,
    onKickOff: () -> Unit,
    onEndPhase: () -> Unit,
    onResetGame: () -> Unit,
    onConfirmEndMatch: () -> Unit, // To distinguish from onFinishGame
    onPenaltyAttemptRecorded: (scored: Boolean) -> Unit, // New callback for penalty attempts
) {
    val activeGame by gameViewModel.activeGame.collectAsState()
    val pagerState = rememberPagerState(initialPage = 1) { 3 } // 0: Home Actions, 1: Main Display, 2: Away Actions
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showEndOfMainTimeDialog by remember { mutableStateOf(false) }
    val isPenaltiesPhase = activeGame.currentPhase == GamePhase.PENALTIES
    // If in penalties, we only want the main display. Otherwise, 3 pages.
    val pagerPageCount = if (isPenaltiesPhase) 1 else 3
    // If in penalties, always ensure the pager is on the (now only) main page.
    // The initialPage calculation needs to be smart if the phase can change while this screen is active.
    val initialPage = if (isPenaltiesPhase) 0 else 1 // Page 0 of 1, or Page 1 of 3
    // If the phase changes to PENALTIES while the user is on a TeamActionsPage,
    // snap them back to the MainGameDisplayScreen.
    LaunchedEffect(isPenaltiesPhase, pagerPageCount) {
        if (isPenaltiesPhase && pagerState.currentPage != 0) { // If penalties and not on the (new) page 0
            pagerState.scrollToPage(0) // Snap to the main display (which is now page 0)
        } else if (!isPenaltiesPhase && pagerState.currentPage != 1 && pagerPageCount == 3) {
            // Optional: If phase changes *from* penalties back to something else,
            // and you want to ensure they are on the central page (page 1 of 3).
            // This might be less critical or could be handled by user expectation.
            // pagerState.scrollToPage(1)
        }
    }

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
        if (isPenaltiesPhase) {
            // Only one page: MainGameDisplayScreen
            // Here, pageIndexInPager will always be 0
            PenaltyShootoutScreen(
                game = activeGame,
                onPenaltyAttemptRecorded = onPenaltyAttemptRecorded,
                modifier = Modifier.fillMaxSize() // It takes the whole screen
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // beyondBoundsPageCount = 1 // Consider if needed for performance/preloading
            ) { page ->
                when (page) {
                    0 -> TeamActionsPage(
                        team = Team.HOME,
                        game = activeGame,
                        onAddGoal = { onAddGoal(Team.HOME) },
                        onNavigateToLogCard = onNavigateToLogCard
                    )

                    1 -> MainGameDisplayScreen( // The main timer/score view
                        game = activeGame,
                        onKickOff = onKickOff
                    )
                    /*                        onEndPhase = {
                                                // If this button is used for SECOND_HALF, it should also trigger the dialog
                                                if (activeGame.currentPhase == GamePhase.SECOND_HALF && activeGame.isTied)
                                                    showEndOfMainTimeDialog = true
                                                else                            // For other phases, call general end phase
                                                    onEndPhase()
                                            },*/

                    2 -> TeamActionsPage(
                        team = Team.AWAY,
                        game = activeGame,
                        onAddGoal = { onAddGoal(Team.AWAY) },
                        onNavigateToLogCard = onNavigateToLogCard
                    )
                }
            }
        }

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
                // TODO: test vibrate alarm at the end of each timed game phase
                onEndPhase = {
                    showSettingsDialog = false // Also dismiss menu on timer toggle
                    if (activeGame.currentPhase == GamePhase.SECOND_HALF && activeGame.isTied)
                        showEndOfMainTimeDialog = true
                    else                            // For other phases, call general end phase
                        onEndPhase()
                },
                isTimerRunning = activeGame.isTimerRunning, // Get from game state
                isGameActive = activeGame.currentPhase != GamePhase.GAME_ENDED && activeGame.currentPhase != GamePhase.PRE_GAME,
                isGameFinished = activeGame.currentPhase == GamePhase.GAME_ENDED
            )
        }

        if (showResetConfirmDialog) {
            val message = if (activeGame.currentPhase == GamePhase.GAME_ENDED || activeGame.currentPhase == GamePhase.PRE_GAME) {
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
        if (showEndOfMainTimeDialog)
            EndOfMainTimeDialog(
                onDismiss = {
                    showEndOfMainTimeDialog = false /* Consider if dismissing should do anything else */
                },
                onStartExtraTime = {
                    showEndOfMainTimeDialog = false
                    gameViewModel.setToHaveExtraTime()
                    onEndPhase()
                },
                onEndMatch = {
                    showEndOfMainTimeDialog = false
                    onConfirmEndMatch() // Call the lambda passed from NavHost/ViewModel
                    onEndPhase()
                }
            )
    }
}