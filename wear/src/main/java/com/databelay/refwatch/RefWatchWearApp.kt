package com.databelay.refwatch

import android.content.Context
import android.os.VibratorManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.navigation.Screen
import com.databelay.refwatch.presentation.screens.GameLogScreen
import com.databelay.refwatch.presentation.screens.GameScheduleScreen
import com.databelay.refwatch.presentation.screens.GameScreenWithPager
import com.databelay.refwatch.presentation.screens.HomeScreen
import com.databelay.refwatch.presentation.screens.KickOffSelectionScreen
import com.databelay.refwatch.presentation.screens.LogCardScreen
import com.databelay.refwatch.presentation.screens.PreGameSetupScreen

@Composable
fun RefWatchWearApp(
    // gameViewModel is now obtained via hiltViewModel() directly here
    gameViewModel: WearGameViewModel = hiltViewModel() // Use Hilt to get ViewModel
) {
    val navController = rememberSwipeDismissableNavController()
    val context = LocalContext.current

    LaunchedEffect(key1 = Unit) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator?.let { gameViewModel.setVibrator(it) }
    }

    // Observe the activeGame from the ViewModel to decide the start destination
    val activeGameSnapshot by gameViewModel.activeGame.collectAsState()

    // Determine start destination based on the current active game's phase
    val startDestination = remember(activeGameSnapshot.currentPhase) { // Recalculate if phase changes
        if (activeGameSnapshot.currentPhase == GamePhase.PRE_GAME || activeGameSnapshot.currentPhase == GamePhase.FULL_TIME) {
            Screen.Home.route // Start at Home if no game active or game ended
        } else {
            Screen.Game.route // Go directly to game if it's in progress
        }
    }
    Log.d("RefWatchWearApp", "Start destination determined: $startDestination based on phase: ${activeGameSnapshot.currentPhase}")


    Scaffold(
        modifier = Modifier.background(MaterialTheme.colors.background), // Use Wear MaterialTheme
        // timeText = { TimeText() } // Optional: standard time display
    ) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = startDestination // Dynamic start destination
        ) {
            composable(Screen.Home.route) {
                // Pass NavController and ViewModel
                HomeScreen(
                    onNavigateToSchedule = { navController.navigate(Screen.GameSchedule.route) },
                    onNavigateToNewGame = {
                        gameViewModel.createNewDefaultGame() // Prepare a new default game
                        navController.navigate(Screen.PreGameSetup.route)
                    }
                )
            }
            composable(Screen.GameSchedule.route) {
                val scheduledGames by gameViewModel.scheduledGames.collectAsState()
                GameScheduleScreen(
                    scheduledGames = scheduledGames,
                    onGameSelected = { selectedGame ->
                        gameViewModel.selectGameToStart(selectedGame) // Prepare the selected game
                        navController.navigate(Screen.PreGameSetup.route) {
                            // Clear back stack to home or make sense for your flow
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }
            composable(Screen.PreGameSetup.route) {
                // ViewModel is already available via hiltViewModel or passed if needed
                PreGameSetupScreen(
                    gameViewModel = gameViewModel, // Pass the ViewModel
                    onNavigateToKickOff = {
                        navController.navigate(Screen.KickOffSelection.route)
                    },
                    onStartGameConfirmed = { // New callback for when settings are confirmed
                        gameViewModel.confirmSettingsAndStartGame() // ViewModel handles phase change
                        navController.navigate(Screen.Game.route) {
                            popUpTo(Screen.Home.route) { inclusive = false } // Go back to home, then to game
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.KickOffSelection.route) {
                KickOffSelectionScreen(
                    gameViewModel = gameViewModel,
                    onConfirm = {
                        // confirmSettingsAndStartGame should be called before navigating if this is the final step
                        // Or, PreGameSetupScreen's "Start Game" button is the one to call confirmSettingsAndStartGame
                        // For now, let's assume PreGameSetup handles the final confirmation
                        navController.popBackStack() // Go back to PreGameSetup
                    }
                )
            }
            composable(Screen.Game.route) {
                val currentActiveGame by gameViewModel.activeGame.collectAsState()
                GameScreenWithPager( // Ensure this screen observes the activeGame state
                    activeGame = currentActiveGame, // Pass the whole Game object
                    // Pass specific lambdas for actions the Pager needs to trigger
                    onToggleTimer = { gameViewModel.toggleTimer() },
                    onAddGoal = { team -> gameViewModel.addGoal(team) },
                    onEndPhaseEarly = { gameViewModel.endCurrentPhaseEarly() },
                    onNavigateToLogCard = { navController.navigate(Screen.LogCard.route) }, // Navigation is separate
                    onNavigateToGameLog = { navController.navigate(Screen.GameLog.route) },
                    //TODO: implement resetgame
                    onResetGame = { /*gameViewModel.resetGame()*/ }

                )
            }
            composable(Screen.LogCard.route) {
                // val currentActiveGame by gameViewModel.activeGame.collectAsState() // Already available
                LogCardScreen(
                    onLogCard = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable(Screen.GameLog.route) {
                val currentActiveGame by gameViewModel.activeGame.collectAsState()
                GameLogScreen(
                    events = currentActiveGame.events,
                    onDismiss = { navController.popBackStack() }
                )
            }
        }
    }
}