package com.databelay.refwatch.wear

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.navigation.Screen
import com.databelay.refwatch.wear.presentation.screens.GameLogScreen
import com.databelay.refwatch.wear.presentation.screens.GameScheduleScreen
import com.databelay.refwatch.wear.presentation.screens.GameScreenWithPager
import com.databelay.refwatch.wear.presentation.screens.KickOffSelectionScreen
import com.databelay.refwatch.wear.presentation.screens.LogCardScreen
import com.databelay.refwatch.wear.presentation.screens.PreGameSetupScreen
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint // This annotation enables Hilt injection for the Activity
class MainActivity : ComponentActivity() {
    // The Activity's only job is to set up the Compose content.
    // Data reception is handled by your background WearableListenerService.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RefWatchWearTheme {
                // Call your main Composable that contains the navigation logic.
                RefWatchWearApp()
            }
        }
    }
}


@Composable
fun RefWatchWearApp() {
    val navController = rememberSwipeDismissableNavController()
    val gameViewModel: WearGameViewModel = hiltViewModel()
    val scheduledGames by gameViewModel.scheduledGames.collectAsState()
    val activeGame by gameViewModel.activeGame.collectAsState()

    // Determine start destination based on whether a game is resumable
    val startDestination = remember(activeGame) {
        // If there's an active game in progress, start directly on the game screen.
        // Otherwise, start on the schedule/home screen.
        if (activeGame.currentPhase != GamePhase.PRE_GAME && activeGame.currentPhase != GamePhase.FULL_TIME) {
            // A more robust check for a default/empty game state
            val isDefaultGame = activeGame.homeTeamName == "Home" && activeGame.awayTeamName == "Away" && activeGame.events.isEmpty()
            if (!isDefaultGame) {
                Screen.Game.route // Base route name for the pager screen
            } else {
                Screen.GameSchedule.route // Default start
            }
        } else {
            Screen.GameSchedule.route
        }
    }

    Log.d("RefWatchWearApp", "Start destination: $startDestination, Active Game Phase: ${activeGame.currentPhase}")

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colors.background), // Use Wear MaterialTheme
        // timeText = { TimeText() } // Optional: standard time display
    ) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = startDestination // Dynamic start destination
        ) {
            composable(Screen.GameSchedule.route) {
                val scheduledGames by gameViewModel.scheduledGames.collectAsState()
                GameScheduleScreen(
                    scheduledGames = scheduledGames,
                    activeGame = activeGame,
                    onGameSelected = { selectedGame ->
                        gameViewModel.selectGameToStart(selectedGame) // Prepare the selected game
                        navController.navigate(Screen.PreGameSetup.route) {
                            // Clear back stack to home or make sense for your flow
                            popUpTo(Screen.Home.route)
                        }
                    },
                    onNavigateToGameScreen = { // <<<<  Implement the resume navigation
                        // The game is already active in the ViewModel, just navigate
                        navController.navigate(Screen.Game.route)
                    },
                    onNavigateToNewGame = {
                        gameViewModel.createNewDefaultGame() // Prepare a new default game
                        navController.navigate(Screen.PreGameSetup.route)
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
                            popUpTo(Screen.Home.route) {
                                inclusive = false
                            } // Go back to home, then to game
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
                    onNavigateToLogCard = { team -> navController.navigate(Screen.LogCard.createRoute(team)) }, // Navigation is separate
                    onNavigateToGameLog = { navController.navigate(Screen.GameLog.route) },
                    // This lambda defines what happens when the user finishes the game
                    onFinishGame = {
                        Log.d("RefWatchWearApp", "Finish Game action triggered from UI.")
                        gameViewModel.finishAndSyncActiveGame {
                            Log.d("RefWatchWearApp", "Sync complete. Navigating to Home.")
                            // Navigate back home and clear the history
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onResetGame = {
                        Log.d("RefWatchWearApp", "Reset Game action triggered from UI.")
                        // This action simply resets the current active game to a fresh default state.
                        gameViewModel.createNewDefaultGame()
                        // After resetting, we navigate to the PreGameSetup screen for this new default game.
                        navController.navigate(Screen.PreGameSetup.route) {
                            popUpTo(Screen.Home.route) // Go back to Home then to PreGameSetup
                        }
                    }
                )
            }
            composable(
                route = Screen.LogCard.route,
                arguments = listOf(navArgument("team") { type = NavType.StringType })
            ) { backStackEntry ->
                // Extract the team name from the route, convert it to the Enum
                val teamName = backStackEntry.arguments?.getString("team")
                val preselectedTeam = try {
                    teamName?.let { Team.valueOf(it) }
                } catch (e: IllegalArgumentException) {
                    null // Handle error if the team name is invalid
                }

                LogCardScreen(
                    preselectedTeam = preselectedTeam, // Pass the preselected team
                    onLogCard = { team, number, cardType ->
                        gameViewModel.addCard(team, number, cardType)
                        navController.popBackStack() // Go back after logging
                    },
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