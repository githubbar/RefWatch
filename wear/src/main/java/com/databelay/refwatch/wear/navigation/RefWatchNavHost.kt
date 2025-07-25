package com.databelay.refwatch.wear.navigation

import android.util.Log
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
import com.databelay.refwatch.wear.WearGameViewModel
import com.databelay.refwatch.wear.presentation.screens.GameLogScreen
import com.databelay.refwatch.wear.presentation.screens.GameScheduleScreen
import com.databelay.refwatch.wear.presentation.screens.GameScreenWithPager
import com.databelay.refwatch.wear.presentation.screens.KickOffSelectionScreen
import com.databelay.refwatch.wear.presentation.screens.LogCardScreen
import com.databelay.refwatch.wear.presentation.screens.PreGameSetupScreen

const val TAG = "RefWatchNavHost"

@Composable
fun RefWatchNavHost() {
    val navController = rememberSwipeDismissableNavController()
    val gameViewModel: WearGameViewModel = hiltViewModel()
    val scheduledGames by gameViewModel.scheduledGames.collectAsState()
    val activeGame by gameViewModel.activeGame.collectAsState()
    val allGames by gameViewModel.allGamesMap.collectAsState()

    // Determine start destination based on whether a game is resumable
    val startDestination = remember(activeGame) {
        // If there's an active game in progress, start directly on the game screen.
        // Otherwise, start on the schedule/home screen.
        if (activeGame.currentPhase != GamePhase.PRE_GAME && activeGame.currentPhase != GamePhase.FULL_TIME) {
            // A more robust check for a default/empty game state
            val isDefaultGame = activeGame.homeTeamName == "Home" && activeGame.awayTeamName == "Away" && activeGame.events.isEmpty()
            if (!isDefaultGame) {
                WearNavRoutes.GAME_IN_PROGRESS_SCREEN // Base route name for the pager screen
            } else {
                WearNavRoutes.GAME_LIST_SCREEN // Default start
            }
        } else {
            WearNavRoutes.GAME_LIST_SCREEN
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
            composable(WearNavRoutes.GAME_LIST_SCREEN) {
                val scheduledGames by gameViewModel.scheduledGames.collectAsState()
                GameScheduleScreen(
                    scheduledGames = scheduledGames,
                    activeGame = activeGame,
                    onGameSelected = { selectedGame ->
                        gameViewModel.selectGameToStart(selectedGame) // Prepare the selected game
                        navController.navigate(WearNavRoutes.PRE_GAME_SETUP_SCREEN) {
                            // Clear back stack to home or make sense for your flow
                            popUpTo(WearNavRoutes.GAME_LIST_SCREEN)
                        }
                    },
                    onViewLog = { gameId ->
                        navController.navigate(WearNavRoutes.gameLogRoute(gameId))
                    },
                    onNavigateToGameScreen = { // <<<<  Implement the resume navigation
                        // The game is already active in the ViewModel, just navigate
                        navController.navigate(WearNavRoutes.GAME_IN_PROGRESS_SCREEN)
                    },
                    onNavigateToNewGame = {
                        gameViewModel.createNewDefaultGame() // Prepare a new default game
                        navController.navigate(WearNavRoutes.PRE_GAME_SETUP_SCREEN)
                    }
                )
            }
            composable(WearNavRoutes.PRE_GAME_SETUP_SCREEN) {
                // ViewModel is already available via hiltViewModel or passed if needed
                PreGameSetupScreen(
                    gameViewModel = gameViewModel, // Pass the ViewModel
                    onNavigateToKickOff = {
                        navController.navigate(WearNavRoutes.KICK_OFF_SELECTION_SCREEN)
                    },
                    onStartGameConfirmed = { // New callback for when settings are confirmed
                        gameViewModel.confirmSettingsAndStartGame() // ViewModel handles phase change
                        navController.navigate(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                            popUpTo(WearNavRoutes.GAME_LIST_SCREEN) {
                                inclusive = false
                            } // Go back to home, then to game
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(WearNavRoutes.KICK_OFF_SELECTION_SCREEN) {
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
            composable(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                val currentActiveGame by gameViewModel.activeGame.collectAsState()
                GameScreenWithPager( // Ensure this screen observes the activeGame state
                    activeGame = currentActiveGame, // Pass the whole Game object
                    // Pass specific lambdas for actions the Pager needs to trigger
                    onToggleTimer = { gameViewModel.toggleTimer() },
                    onAddGoal = { team -> gameViewModel.addGoal(team) },
                    onEndPhaseEarly = { gameViewModel.endCurrentPhaseEarly() },
                    onNavigateToLogCard = { team -> navController.navigate(WearNavRoutes.logCardRoute(team)) }, // Navigation is separate
                    onNavigateToGameLog = { navController.navigate(WearNavRoutes.gameLogRoute(activeGame.id)) },
                    // This lambda defines what happens when the user finishes the game
                    onFinishGame = {
                        Log.d("RefWatchWearApp", "Finish Game action triggered from UI.")
                        gameViewModel.finishAndSyncActiveGame {
                            Log.d("RefWatchWearApp", "Sync complete. Navigating to Home.")
                            // Navigate back home and clear the history
                            navController.navigate(WearNavRoutes.GAME_LIST_SCREEN) {
                                popUpTo(WearNavRoutes.GAME_LIST_SCREEN) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onResetGame = {
                        Log.d("RefWatchWearApp", "Reset Game action triggered from UI.")
                        // This action simply resets the current active game to a fresh default state.
                        gameViewModel.createNewDefaultGame()
                        // After resetting, we navigate to the PreGameSetup screen for this new default game.
                        navController.navigate(WearNavRoutes.PRE_GAME_SETUP_SCREEN) {
                            popUpTo(WearNavRoutes.GAME_LIST_SCREEN) // Go back to Home then to PreGameSetup
                        }
                    }
                )
            }
            composable(route = WearNavRoutes.LOG_CARD_SCREEN,
                arguments = listOf(
                    navArgument(WearNavRoutes.TEAM_ARG) { // The argument name must match the placeholder
                        type = NavType.StringType
                        // It's a required part of the path, so it's not nullable
                    }
                )
            ) { backStackEntry ->
                // Extract the team name from the route, convert it to the Enum
                val teamName = backStackEntry.arguments?.getString(WearNavRoutes.TEAM_ARG)
                val preselectedTeam = teamName?.let { Team.valueOf(it) }

                LogCardScreen(
                    preselectedTeam = preselectedTeam, // Pass the preselected team
                    onLogCard = { team, number, cardType ->
                        gameViewModel.addCard(team, number, cardType)
                        navController.popBackStack() // Go back after logging
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable("${WearNavRoutes.GAME_LOG_SCREEN}?${WearNavRoutes.GAME_ID_ARG}={${WearNavRoutes.GAME_ID_ARG}}",
                arguments = listOf(
                    navArgument(WearNavRoutes.GAME_ID_ARG) { // Tell NavController to expect a "gameId"
                        type = NavType.StringType
                    }
                )
            ) {backStackEntry ->
                // 1. Retrieve the gameId from the navigation arguments.
                val gameId = backStackEntry.arguments?.getString(WearNavRoutes.GAME_ID_ARG)

                // 2. Find the correct game from the permanent 'scheduledGames' list,
                //    NOT from the 'activeGame' state.
                val gameForLog = gameId?.let { allGames[it] }

                // 3. Pass the found game (which can be null if not found) to your log screen.
                //    Your GameLogScreen should take the full Game object to display headers.
                GameLogScreen(
                    game = gameForLog,
                    onDismiss = { navController.popBackStack() }
                )
            }
        }
    }
}