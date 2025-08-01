package com.databelay.refwatch.wear.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.hasDuration
import com.databelay.refwatch.common.isPlayablePhase
import com.databelay.refwatch.common.readable
import com.databelay.refwatch.presentation.screens.pager.PenaltyShootoutScreen
import com.databelay.refwatch.wear.WearGameViewModel
import com.databelay.refwatch.wear.presentation.components.ConfirmationDialog

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
    onResetPeriodTimer: () -> Unit,
    onConfirmEndMatch: () -> Unit, // To distinguish from onFinishGame
    onPenaltyAttemptRecorded: (scored: Boolean) -> Unit, // New callback for penalty attempts
) {

    val context = LocalContext.current
    val activeGame by gameViewModel.activeGame.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showEndOfMainTimeDialog by remember { mutableStateOf(false) }
    val isPenaltiesPhase = activeGame.currentPhase == GamePhase.PENALTIES
    val isPlayableRegularPhase = activeGame.currentPhase.isPlayablePhase() && !isPenaltiesPhase
    // Pager state is only relevant if it's a playable regular phase
    val pagerState = rememberPagerState(
        initialPage = 1, // Default to main display (index 1 of 3) when pager is active
        pageCount = { if (isPlayableRegularPhase) 3 else 1 } // Pager has 3 pages only if playable
    )
/*    // If the phase changes to PENALTIES while the user is on a TeamActionsPage,
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
    }*/

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
        when {
            isPenaltiesPhase -> {
                // Directly show PenaltyShootoutScreen
                PenaltyShootoutScreen(
                    game = activeGame,
                    onPenaltyAttemptRecorded = onPenaltyAttemptRecorded,
                    modifier = Modifier.fillMaxSize() // It takes the whole screen
                )
            }
            isPlayableRegularPhase -> {
                // Show HorizontalPager for playable regular phases
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

                        2 -> TeamActionsPage(
                            team = Team.AWAY,
                            game = activeGame,
                            onAddGoal = { onAddGoal(Team.AWAY) },
                            onNavigateToLogCard = onNavigateToLogCard
                        )
                    }
                }
            }
            else -> {
                // For non-playable, non-penalty phases (e.g., HALF_TIME, GAME_ENDED, NOT_STARTED)
                // Show only the MainGameDisplayScreen (which typically shows timer/status)
                MainGameDisplayScreen( // The main timer/score view
                    game = activeGame,
                    onKickOff = onKickOff
                )
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
                // "reset current period timer"
                onResetPeriodTimer = { // This is "End Game & Reset" from settings
                    showSettingsDialog = false
                    showResetConfirmDialog = true // Show confirmation for reset
                },
                onToggleTimer = {
                    showSettingsDialog = false // Also dismiss menu on timer toggle
                    onToggleTimer()
                },
                onEndPhase = {
                    showSettingsDialog = false // Also dismiss menu on timer toggle
                    if (activeGame.currentPhase == GamePhase.SECOND_HALF && activeGame.isTied)
                        showEndOfMainTimeDialog = true
                    else                            // For other phases, call general end phase
                        onEndPhase()
                },
            )
        }

        if (showResetConfirmDialog) {
            if (activeGame.currentPhase.hasDuration()) {
                ConfirmationDialog(
                    message = "Reset timer for ${activeGame.currentPhase.readable()}?",
                    onConfirm = {
                        showResetConfirmDialog = false
                        onResetPeriodTimer() // Call the passed lambda for resetting
                    },
                    onDismiss = { showResetConfirmDialog = false }
                )
            } else {
                // If the phase doesn't have a duration, show a toast and dismiss the dialog
                Toast.makeText(context, "No timer in this phase.", Toast.LENGTH_SHORT).show()
                showResetConfirmDialog = false
            }

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
//                    onConfirmEndMatch() // Call the lambda passed from NavHost/ViewModel
                    onEndPhase()
                }
            )
    }
}