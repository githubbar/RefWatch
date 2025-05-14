package com.databelay.refwatch.presentation // Replace with your package name

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.databelay.refwatch.GameViewModel
import com.databelay.refwatch.data.GamePhase
import com.databelay.refwatch.data.Team
import com.databelay.refwatch.presentation.screens.GameLogScreen
import com.databelay.refwatch.presentation.screens.GameScreenWithPager
import com.databelay.refwatch.presentation.screens.LogCardScreen
import com.databelay.refwatch.presentation.screens.PreGameSetupScreen
import com.databelay.refwatch.presentation.screens.KickOffSelectionScreen
import com.databelay.refwatch.presentation.screens.HomeScreen
import com.databelay.refwatch.presentation.screens.GameScheduleScreen
import com.databelay.refwatch.presentation.screens.LoadIcsScreen
import com.databelay.refwatch.presentation.theme.RefWatchTheme
import com.databelay.refwatch.navigation.Screen


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            RefWatchTheme { // Or whatever you named your theme function
                // Your NavHost and screens go here
                RefWatchApp() // Example
            }
        }
    }
}

@Composable
fun RefWatchApp(gameViewModel: GameViewModel = viewModel()) {
    RefWatchTheme {
        val navController = rememberSwipeDismissableNavController()
        val context = LocalContext.current

        // Initialize Vibrator in ViewModel
        LaunchedEffect(key1 = Unit) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            gameViewModel.setVibrator(vibrator)
        }

        val currentGameState by gameViewModel.gameState.collectAsState()

        Scaffold(
            modifier = Modifier.background(MaterialTheme.colors.background)
            // You can add TimeText here if desired
            // timeText = { TimeText() }
        ) {
            SwipeDismissableNavHost(
                navController = navController,
                // Start at pre-game setup, or game screen if game is already in progress
                startDestination = if (currentGameState.currentPhase == GamePhase.PRE_GAME || currentGameState.currentPhase == GamePhase.FULL_TIME) {
                    Screen.Home.route
                } else {
                    Screen.Game.route
                }
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(onNavigate = { route -> navController.navigate(route) })
                }
                // Define each screen (destination) using composable()
                composable(Screen.PreGameSetup.route) {
                    PreGameSetupScreen(
                        viewModel = gameViewModel
                    ) { // Example navigation action
                        navController.navigate(Screen.KickOffSelection.route) {
                            popUpTo(Screen.PreGameSetup.route) { inclusive = true }
                        }
                    }
                }
                composable(Screen.KickOffSelection.route) {
                    KickOffSelectionScreen(
                        viewModel = gameViewModel,
                        onConfirm = {
                            gameViewModel.startTimer()
                            navController.navigate(Screen.Game.route) {
                                popUpTo(Screen.PreGameSetup.route) { inclusive = true }
                            }
                        }
                    )
                }
                //TODO: add companion app and parse and store ICS files from GotSport there

                composable(Screen.Game.route) { // This route now points to the Pager screen
                    val gameState by gameViewModel.gameState.collectAsState() // Collect the state here
                    GameScreenWithPager(
                        gameState = gameState, // Pass the collected state
                        onPauseResume = { if (gameState.isTimerRunning) gameViewModel.pauseTimer() else gameViewModel.startTimer() },
                        onAddGoalHome = { gameViewModel.addGoal(Team.HOME) },
                        onAddGoalAway = { gameViewModel.addGoal(Team.AWAY) },
                        onLogCardForHome = {
                            // Navigate to LogCardScreen, potentially passing Team.HOME as an argument
                            // or have LogCardScreen allow team selection if not pre-filled.
                            // For simplicity, let's assume LogCardScreen handles team selection:
                            navController.navigate(Screen.LogCard.route) // You might want to pass team info
                        },
                        onLogCardForAway = {
                            navController.navigate(Screen.LogCard.route) // You might want to pass team info
                        },
                        onViewLog = { navController.navigate(Screen.GameLog.route) },
                        onEndPeriod = { gameViewModel.endCurrentPhaseEarly() },
                        onResetGame = {
                            gameViewModel.resetGame()
                            navController.navigate(Screen.PreGameSetup.route) {
                                popUpTo(Screen.Game.route) { inclusive = true }
                            }
                        },
                        isTimerRunning = gameState.isTimerRunning // Pass current timer state
                    )
                }
                composable(Screen.LogCard.route) {
                    val gameState by gameViewModel.gameState.collectAsState()
                    LogCardScreen(
                        onLogCard = { team, playerNumber, cardType ->
                            gameViewModel.addCard(team, playerNumber, cardType)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                        homeTeamColor = gameState.settings.homeTeamColor,
                        awayTeamColor = gameState.settings.awayTeamColor
                    )
                }
                composable(Screen.GameLog.route) { // Or NavRoutes.GAME_LOG_SCREEN
                    val gameState by gameViewModel.gameState.collectAsState() // Collect the full game state

                    GameLogScreen(
                        events = gameState.events, // Pass the list of events from the game state
                        onDismiss = {
                            navController.popBackStack() // Standard way to dismiss/go back
                        }
                    )
                }
                composable(Screen.GameSchedule.route) {
                    // val games by scheduleViewModel.scheduledGames.collectAsState() // If using a separate ViewModel
                    val games by gameViewModel.scheduledGames.collectAsState() // If combined in GameViewModel
                    GameScheduleScreen(
                         scheduledGames = games,
                         onGameSelected = { /* Navigate to game details or start pre-filled game */ }
                    )
                }
                composable(Screen.LoadIcs.route) {
                    LoadIcsScreen(
                         onIcsLoaded = { loadedGames ->
                            gameViewModel.addScheduledGames(loadedGames)
                            navController.popBackStack() // Go back after loading
                         }
                    )
                }
            }
        }
    }
}