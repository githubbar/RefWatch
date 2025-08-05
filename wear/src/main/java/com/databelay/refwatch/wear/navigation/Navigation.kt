package com.databelay.refwatch.wear.navigation

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isEmpty
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
//import androidx.wear.compose.material.MaterialTheme
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
import androidx.navigation.plusAssign
import com.databelay.refwatch.common.GameStatus
import com.databelay.refwatch.wear.presentation.screens.SimpleTestScreenWithNextButton

const val TAG = "NavigationRoutes"

@Composable
fun NavigationRoutes() {
    val navController = rememberSwipeDismissableNavController()
    val gameViewModel: WearGameViewModel = hiltViewModel()
    val activeGame by gameViewModel.activeGame.collectAsState()
    val allGames by gameViewModel.allGamesMap.collectAsState()

    // Determine start destination based on whether a game is resumable

    val startDestination = remember(activeGame.id) {
        Log.d("${TAG}:startDestination", "Current start route: ${navController.currentDestination?.route}")
        if (activeGame.status != GameStatus.IN_PROGRESS)
            WearNavRoutes.GAME_LIST_SCREEN
        else mapGamePhaseToRoute(activeGame.currentPhase)
    }

/*    // Add the destination changed listener (for debugging)
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { controller, destination, arguments ->
            // Construct a message indicating the change
            val contextMsg = "Destination Changed to: ${destination.route ?: "Unknown"}, Args: ${arguments?.let { bundle ->
                bundle.keySet().map { key -> "$key=${bundle.get(key)}" }.joinToString()
            } ?: "None"}"
            logBackStack(controller, contextMsg)
        }
        navController.addOnDestinationChangedListener(listener)

        // When the effect leaves the Composition, remove the listener
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }*/

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background), // Use Wear MaterialTheme
        // timeText = { TimeText() } // Optional: standard time display
    ) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = startDestination, // Dynamic start destination
            modifier = Modifier.padding(it)
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
                        val route = mapGamePhaseToRoute(activeGame.currentPhase)
                        navController.navigate(route)
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
                        navController.navigate(WearNavRoutes.KICK_OFF_SELECTION_SCREEN) {
                            popUpTo(WearNavRoutes.GAME_LIST_SCREEN) {inclusive = false}
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(WearNavRoutes.KICK_OFF_SELECTION_SCREEN) {
                KickOffSelectionScreen(
                    gameViewModel = gameViewModel,
                    onConfirm = {
                        gameViewModel.proceedToNextPhaseManager(gameViewModel.activeGame.value.copy())
                        navController.navigate(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                            popUpTo(WearNavRoutes.GAME_LIST_SCREEN) {inclusive = false}
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                GameScreenWithPager( // Ensure this screen observes the activeGame state
                    // Pass specific lambdas for actions the Pager needs to trigger
                    gameViewModel = gameViewModel,
                    onToggleTimer = { gameViewModel.toggleTimer() },
                    onAddGoal = { team -> gameViewModel.addGoal(team) },
                    onEndPhase = { gameViewModel.proceedToNextPhaseManager(gameViewModel.activeGame.value.copy()) },
                    onNavigateToLogCard = { team: Team, cardType: CardType ->

                        navController.navigate(WearNavRoutes.logCardRoute(team, cardType))
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
                                popUpTo(WearNavRoutes.GAME_LIST_SCREEN) { inclusive = false }
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
                            navController.navigate(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                                popUpTo(WearNavRoutes.GAME_LIST_SCREEN) {inclusive = false}
                                launchSingleTop = true
                            }
                        },
                        onCancel = {
                            navController.navigate(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                                popUpTo(WearNavRoutes.GAME_LIST_SCREEN) {inclusive = false}
                                launchSingleTop = true
                            }
                        },
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

/*@SuppressLint("RestrictedApi")
fun logBackStack(navController: NavController, contextMessage: String = "") {
    val routes = navController
        .currentBackStack.value
        .map { it.destination.route }
        .joinToString(", ")

    Log.d("BackStackLog", "BackStack: $routes")
}*/

@SuppressLint("RestrictedApi")
fun logBackStack(navController: NavController, contextMessage: String = "") {
    val stack = navController.currentBackStack.value // Get the current list

    // Get the current destination directly from the NavController
    val currentNavControllerDestination = navController.currentDestination
    val currentNavControllerRoute = currentNavControllerDestination?.route
    val currentNavControllerId = currentNavControllerDestination?.id
    val currentNavControllerClass =
        currentNavControllerDestination?.displayName // or ::class.simpleName

    Log.d("${TAG}:stack", "---- NavController Back Stack ($contextMessage) ----")
    Log.d(
        "${TAG}:stack",
        "NavController Current Destination: Route='${currentNavControllerRoute ?: "null"}', ID='${currentNavControllerId ?: "null"}', Class='${currentNavControllerClass ?: "null"}'"
    )

    if (stack.isEmpty()) {
        Log.d("${TAG}:stack", "Back stack is empty.")
    } else {
        // Remember: currentBackStack.value lists entries from newest (top) to oldest (bottom)
        // So, stack[0] should ideally correspond to navController.currentDestination
        stack.forEachIndexed { index, navBackStackEntry ->
            val entryDestination = navBackStackEntry.destination
            val route = entryDestination.route
            val arguments = navBackStackEntry.arguments?.let { bundle ->
                bundle.keySet().joinToString(", ") { key -> "$key=${bundle.get(key)}" }
            } ?: "null"
            // Using displayName for a more user-friendly class name if available
            val destDisplayName = entryDestination.displayName

            Log.d(
                "${TAG}:stack",
                "$index: Route='${route ?: "null"}', Args=[$arguments], ID='${navBackStackEntry.id}', NavDestId='${entryDestination.id}', NavDestClass='${destDisplayName ?: entryDestination::class.simpleName}'"
            )
        }
    }
    Log.d("${TAG}:stack", "------------------------------------------")
}

fun mapGamePhaseToRoute(phase:GamePhase) : String {
    return when (phase) {
        GamePhase.GAME_ENDED, GamePhase.ABANDONED, GamePhase.NOT_STARTED -> WearNavRoutes.GAME_LIST_SCREEN
        GamePhase.PRE_GAME -> WearNavRoutes.PRE_GAME_SETUP_SCREEN
        GamePhase.KICK_OFF_SELECTION_FIRST_HALF,
        GamePhase.KICK_OFF_SELECTION_EXTRA_TIME,
        GamePhase.KICK_OFF_SELECTION_PENALTIES -> WearNavRoutes.KICK_OFF_SELECTION_SCREEN
        else -> WearNavRoutes.GAME_IN_PROGRESS_SCREEN
    }
}