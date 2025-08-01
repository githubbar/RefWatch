package com.databelay.refwatch.wear.navigation

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.wear.WearGameViewModel
import com.databelay.refwatch.wear.presentation.screens.GameListScreen
import com.databelay.refwatch.wear.presentation.screens.GameLogScreen
import com.databelay.refwatch.wear.presentation.screens.GameScreenWithPager
import com.databelay.refwatch.wear.presentation.screens.KickOffSelectionScreen
import com.databelay.refwatch.wear.presentation.screens.LogCardScreen
import com.databelay.refwatch.wear.presentation.screens.PreGameSetupScreen
import kotlinx.coroutines.delay

const val TAG = "NavigationRoutes"

@Composable
fun NavigationRoutes() {
    val navController = rememberSwipeDismissableNavController()
    val gameViewModel: WearGameViewModel = hiltViewModel()
    val activeGame by gameViewModel.activeGame.collectAsState()
    val allGames by gameViewModel.allGamesMap.collectAsState()

    // Determine start destination based on whether a game is resumable
    val startDestination = remember(activeGame) {
        // If there's an active game in progress, start directly on the game screen.
        // Otherwise, start on the schedule/home screen.
        if (activeGame.currentPhase != GamePhase.PRE_GAME) {
            WearNavRoutes.GAME_IN_PROGRESS_SCREEN // Base route name for the pager screen
        } else {
            WearNavRoutes.GAME_LIST_SCREEN
        }
    }

//    Log.d(TAG, "Start destination: $startDestination, Active Game Phase: ${activeGame.currentPhase}")

    // FIXME: kickoff choice flicker before the first half (comes up twice?)
    // NEW: Observe currentPhase to trigger navigation to KickOffSelectionScreen
    LaunchedEffect(activeGame.currentPhase) {
        Log.d(TAG, "Current phase changed to: ${activeGame.currentPhase}")
        when (activeGame.currentPhase) {
            GamePhase.KICK_OFF_SELECTION_FIRST_HALF,
            GamePhase.KICK_OFF_SELECTION_EXTRA_TIME,
            GamePhase.KICK_OFF_SELECTION_PENALTIES -> {
                // Check if current route is not already kick off selection to avoid loop
                if (navController.currentBackStackEntry?.destination?.route != WearNavRoutes.KICK_OFF_SELECTION_SCREEN) {
                    Log.d(TAG, "Navigating to KICK_OFF_SELECTION_SCREEN for phase: ${activeGame.currentPhase}")
                    navController.navigate(WearNavRoutes.KICK_OFF_SELECTION_SCREEN) {
                        // Decide on popUpTo logic. Maybe popUpTo GameScreen so back goes to game?
                        // Or a more specific popUpTo behavior.
                        // For now, let's assume it should appear on top.
                    }
                }
            }
            else -> {
                // Not a kick-off selection phase
            }
        }
    }

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colors.background), // Use Wear MaterialTheme
        // timeText = { TimeText() } // Optional: standard time display
    ) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = startDestination // Dynamic start destination
        ) {
            composable(WearNavRoutes.GAME_LIST_SCREEN) {
                GameListScreen(
                    viewModel = gameViewModel, // Pass the shared ViewModel
                    onGameSelected = { selectedGame ->
                        gameViewModel.selectGameToStart(selectedGame) // ViewModel updates activeGame
                        navController.navigate(WearNavRoutes.PRE_GAME_SETUP_SCREEN)
                        // popUpTo can be tricky here if startDestination was PRE_GAME_SETUP_SCREEN
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
                    onCreateMatch = {
                        gameViewModel.activeGame.value.currentPhase = GamePhase.KICK_OFF_SELECTION_FIRST_HALF
//                        gameViewModel.activeGame.value.currentPhase = GamePhase.SECOND_HALF // TODO: TEMP for testing
                        navController.navigate(WearNavRoutes.KICK_OFF_SELECTION_SCREEN)
                    },
                )
            }

            composable(WearNavRoutes.KICK_OFF_SELECTION_SCREEN) {
                KickOffSelectionScreen(
                    gameViewModel = gameViewModel,
                    onConfirm = {
                        gameViewModel.endCurrentPhase()
                        navController.navigate(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                            popUpTo(WearNavRoutes.GAME_LIST_SCREEN) {
                                inclusive = false
                            } // Go back to home, then to game
                            launchSingleTop = true
                        }
//                        navController.popBackStack() // Go back to PreGameSetup
                    }
                )
            }
            composable(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                GameScreenWithPager( // Ensure this screen observes the activeGame state
                    // Pass specific lambdas for actions the Pager needs to trigger
                    gameViewModel = gameViewModel,
                    onToggleTimer = { gameViewModel.toggleTimer() },
                    onAddGoal = { team -> gameViewModel.addGoal(team) },
                    onKickOff = {gameViewModel.kickOff()},
                    onEndPhase = { gameViewModel.endCurrentPhase() },
                    onNavigateToLogCard = { team: Team, cardType: CardType ->
                        navController.navigate(WearNavRoutes.logCardRoute(team, cardType))
                        // TODO: Return to main tab after logging a card
                    },
                    onNavigateToGameLog = { navController.navigate(WearNavRoutes.gameLogRoute(activeGame.id)) },
                    // This lambda defines what happens when the user finishes the game
                    onResetPeriodTimer = {
                        Log.d(TAG, "Reset period action triggered from UI.")
                        gameViewModel.resetTimer()
                    },
                    onConfirmEndMatch = {
                        Log.d(TAG, "Finish Match action triggered from UI.")
                        gameViewModel.finishAndSyncActiveGame {
                            Log.d(TAG, "Sync complete. Navigating to Home.")
                            // Navigate back home and clear the history
                            navController.navigate(WearNavRoutes.GAME_LIST_SCREEN) {
                                popUpTo(WearNavRoutes.GAME_LIST_SCREEN) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onPenaltyAttemptRecorded = {
                        scored -> gameViewModel.recordPenaltyAttempt(scored)
                    }
                )
            }

            composable(route = "${WearNavRoutes.LOG_CARD_SCREEN}/{${WearNavRoutes.TEAM_ARG}}/{${WearNavRoutes.CARD_TYPE_ARG}}",
                arguments = listOf(
                    navArgument(WearNavRoutes.TEAM_ARG) { type = NavType.StringType },
                    navArgument(WearNavRoutes.CARD_TYPE_ARG) { type = NavType.StringType } // CardType passed as String
                )
            )
            { backStackEntry ->
                val teamId = backStackEntry.arguments?.getString(WearNavRoutes.TEAM_ARG)
                val cardTypeString = backStackEntry.arguments?.getString("cardType")

                // Convert teamId string back to Team enum (you'll need a robust way to do this)
                val team =
                    teamId?.let { Team.valueOf(it.uppercase()) } // Example, ensure this is safe
                // Convert cardTypeString back to CardType enum
                val cardType = cardTypeString?.let { CardType.valueOf(it.uppercase()) }

                if (team != null && cardType != null) {
                    LogCardScreen(
                        preselectedTeam = team,
                        cardType = cardType,
                        onLogCard = { loggedTeam, playerNum, loggedCardType ->
                            // Handle the logged card (e.g., call ViewModel)
                            gameViewModel.addCard(team, playerNum, cardType)
                            navController.popBackStack()
                        },
                        onCancel = {
                            navController.popBackStack()
                        }
                    )
                } else {
                    // Handle error: team or cardType not found, perhaps popBackStack or show error
                    Text("Error: Invalid navigation arguments for Log Card.")
                    LaunchedEffect(Unit) {
                        delay(2000)
                        navController.popBackStack()
                    }
                }
            }
            composable("${WearNavRoutes.GAME_LOG_SCREEN}/{${WearNavRoutes.GAME_ID_ARG}}",
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