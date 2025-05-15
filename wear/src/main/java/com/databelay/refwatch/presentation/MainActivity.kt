package com.databelay.refwatch.presentation // Replace with your package name

import android.content.Context
import android.os.Bundle
import android.os.VibratorManager
import android.util.Log
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
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

import com.databelay.refwatch.GameViewModel
import com.databelay.refwatch.presentation.screens.GameLogScreen
import com.databelay.refwatch.presentation.screens.GameScreenWithPager
import com.databelay.refwatch.presentation.screens.LogCardScreen
import com.databelay.refwatch.presentation.screens.PreGameSetupScreen
import com.databelay.refwatch.presentation.screens.KickOffSelectionScreen
import com.databelay.refwatch.presentation.screens.HomeScreen
import com.databelay.refwatch.presentation.screens.GameScheduleScreen
import com.databelay.refwatch.presentation.screens.LoadIcsScreen
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.navigation.Screen
import com.databelay.refwatch.common.*

private const val GAME_SETTINGS_TRANSFER_PATH = "/game_settings_data"
private const val TAG_WEAR = "RefWatchWearActivity"

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()
    private lateinit var channelClient: ChannelClient
    private var channelCallback: ChannelClient.ChannelCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
//        installSplashScreen()
        super.onCreate(savedInstanceState)
        channelClient = Wearable.getChannelClient(this)
        setContent {
            RefWatchWearTheme { // Or whatever you named your theme function
                // Your NavHost and screens go here
                RefWatchApp(gameViewModel) // Example
            }
        }
    }
    override fun onResume() {
        super.onResume()
        registerChannelCallback()
    }

    override fun onPause() {
        super.onPause()
        unregisterChannelCallback()
    }

    private fun registerChannelCallback() {
        if (channelCallback != null) return // Already registered

        channelCallback = object : ChannelClient.ChannelCallback() {
            override fun onChannelOpened(channel: ChannelClient.Channel) {
                super.onChannelOpened(channel)
                Log.d(TAG_WEAR, "Channel opened: ${channel.path}")

                if (channel.path == GAME_SETTINGS_TRANSFER_PATH) {
                    lifecycleScope.launch { // Use lifecycleScope for coroutines
                        try {
                            val inputStream = channelClient.getInputStream(channel).await()
                            inputStream.use { stream ->
                                InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                                    val jsonString = reader.readText()
                                    Log.d(TAG_WEAR, "Received JSON: $jsonString")
                                    if (jsonString.isNotEmpty()) {
                                        val gamesList = Json.decodeFromString<List<GameSettings>>(jsonString)
                                        Log.d(TAG_WEAR, "Decoded ${gamesList.size} games.")
                                        gameViewModel.addScheduledGames(gamesList) // Use your ViewModel method
                                        // Optionally, show a toast or navigate
                                        // Toast.makeText(this@MainActivity, "${gamesList.size} games received", Toast.LENGTH_LONG).show()
                                    } else {
                                        Log.w(TAG_WEAR, "Received empty data for game settings.")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG_WEAR, "Error receiving/processing game settings", e)
                        } finally {
                            // It's good practice for the receiver to close its end of the channel
                            // once data processing is done or if an error occurs.
                            channelClient.close(channel).await()
                            Log.d(TAG_WEAR, "Receiver closed channel: ${channel.path}")
                        }
                    }
                } else {
                    Log.d(TAG_WEAR, "Channel opened for unknown path: ${channel.path}, closing.")
                    lifecycleScope.launch { channelClient.close(channel).await() }
                }
            }

            override fun onInputClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
                super.onInputClosed(channel, closeReason, appSpecificErrorCode)
                Log.d(TAG_WEAR, "Input closed for channel: ${channel.path}, reason: $closeReason")
                // The sender usually closes the channel, which triggers this.
                // No explicit close needed here usually if the sender manages closure.
            }

            override fun onChannelClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
                super.onChannelClosed(channel, closeReason, appSpecificErrorCode)
                Log.d(TAG_WEAR, "Channel closed: ${channel.path}, reason: $closeReason")
            }
        }
        channelClient.registerChannelCallback(channelCallback!!)
        Log.d(TAG_WEAR, "ChannelCallback registered.")
    }

    private fun unregisterChannelCallback() {
        channelCallback?.let {
            channelClient.unregisterChannelCallback(it)
            channelCallback = null
            Log.d(TAG_WEAR, "ChannelCallback unregistered.")
        }
    }
}

@Composable
fun RefWatchApp(gameViewModel: GameViewModel = viewModel()) {
    RefWatchWearTheme {
        val navController = rememberSwipeDismissableNavController()
        val context = LocalContext.current

        // Initialize Vibrator in ViewModel
        LaunchedEffect(key1 = Unit) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            gameViewModel.setVibrator(vibratorManager.defaultVibrator)
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
                        onLogCard = { team: Team, playerNumber, cardType: CardType ->
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