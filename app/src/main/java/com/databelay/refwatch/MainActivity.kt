package com.databelay.refwatch // Replace with your package name

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
import com.databelay.refwatch.presentation.screens.GameLogScreen
import com.databelay.refwatch.presentation.screens.GameScreen
import com.databelay.refwatch.presentation.screens.PreGameSetupScreen
import com.databelay.refwatch.presentation.theme.RefWatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            RefWatchApp()
        }
    }
}

// Navigation Routes
sealed class Screen(val route: String) {
    object PreGameSetup : Screen("pre_game_setup")
    object Game : Screen("game_screen")
    object GameLog : Screen("game_log_screen")
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
                    Screen.PreGameSetup.route
                } else {
                    Screen.Game.route
                }
            ) {
                composable(Screen.PreGameSetup.route) {
                    PreGameSetupScreen(
                        viewModel = gameViewModel,
                        onStartGameConfirmed = {
                            gameViewModel.confirmSettingsAndStartGame()
                            navController.navigate(Screen.Game.route) {
                                popUpTo(Screen.PreGameSetup.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.Game.route) {
                    GameScreen(
                        viewModel = gameViewModel,
                        onNavigateToLog = { navController.navigate(Screen.GameLog.route) },
                        onEndGame = { // This will reset the game and go to setup
                            gameViewModel.resetGame()
                            navController.navigate(Screen.PreGameSetup.route) {
                                popUpTo(Screen.Game.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.GameLog.route) {
                    GameLogScreen(viewModel = gameViewModel)
                }
            }
        }
    }
}