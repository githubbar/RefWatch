package com.databelay.refwatch.wear.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Report
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.databelay.refwatch.common.CardIssuedEvent
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.GameStatus
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.isPlayablePhase
import com.databelay.refwatch.common.status
import com.databelay.refwatch.wear.WearGameViewModel
import com.databelay.refwatch.wear.presentation.screens.GameAnalyticsScreen
import com.databelay.refwatch.wear.presentation.screens.GameListScreen
import com.databelay.refwatch.wear.presentation.screens.GameLogScreen
import com.databelay.refwatch.wear.presentation.screens.GameScreenWithPager
import com.databelay.refwatch.wear.presentation.screens.KickOffSelectionScreen
import com.databelay.refwatch.wear.presentation.screens.LogCardScreen
import com.databelay.refwatch.wear.presentation.screens.PreGameSetupRoute
import kotlinx.coroutines.delay

const val TAG = "NavigationRoutes"

@Composable
fun NavigationRoutes() {
    val navController = rememberSwipeDismissableNavController()
    val gameViewModel: WearGameViewModel = hiltViewModel()
    val activeGame by gameViewModel.activeGame.collectAsStateWithLifecycle()
    val allGames by gameViewModel.gamesList.collectAsStateWithLifecycle() // Assuming gamesList is the correct source
    val isOnline by gameViewModel.isOnline.collectAsStateWithLifecycle()
    val collectPositionInfo by gameViewModel.collectPositionInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current // Get the context

    // Keep the screen on during an active match to ensure the app stays "on top"
    LaunchedEffect(activeGame?.currentPhase) {
        val window = (context as? android.app.Activity)?.window
        val isGameInProgress = activeGame?.currentPhase?.status() == GameStatus.IN_PROGRESS
        
        if (isGameInProgress) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "Keeping screen on for active game phase: ${activeGame?.currentPhase}")
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.d(TAG, "Clearing FLAG_KEEP_SCREEN_ON")
        }
    }

    // State to track if the permission has been explicitly denied by the user.
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    // SET UP THE PERMISSION LAUNCHER
    // This launcher will receive the result map from the user's choices.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                Log.i(TAG, "All permissions GRANTED by user.")
                showPermissionDeniedDialog = false
            } else {
                Log.w(TAG, "Some permissions DENIED by user: $permissions")
                showPermissionDeniedDialog = true
            }
        }
    )
    // TRIGGER THE CHECK ONCE WHEN THE APP LAUNCHES
    LaunchedEffect(key1 = true) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            val shouldShowRationale = missingPermissions.any {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
            if (shouldShowRationale) {
                showPermissionDeniedDialog = true
            } else {
                Log.d(TAG, "Permissions missing: $missingPermissions. Requesting now.")
                permissionLauncher.launch(missingPermissions.toTypedArray())
            }
        }
    }

    // Intent to open app settings
    val openSettingsIntent = remember {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // Dialog to show when permission is denied
    if (showPermissionDeniedDialog) {
        AlertDialog(
            visible = true,
            onDismissRequest = { showPermissionDeniedDialog = false }, // Hide dialog on timeout
            icon = {
                Icon(
                    imageVector = Icons.Default.Report,
                    contentDescription = "Warning Icon",
                    modifier = Modifier.size(ConfirmationDialogDefaults.IconSize)
                )
            },
            title = {
                Text("Permission Required")
            },
            text = {
                Text(
                    "This app requires Body Sensors, Location, and Notifications to track your game activity and keep the timer running reliably. Please enable them in settings.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                AlertDialogDefaults.ConfirmButton(
                    onClick = {
                        showPermissionDeniedDialog = false
                        context.startActivity(openSettingsIntent) // Go to settings
                    },
                )
            },
            dismissButton = {
                AlertDialogDefaults.DismissButton(
                    onClick = { showPermissionDeniedDialog = false }, // Just dismiss the dialog
                )
            },

        )
    }

    val startDestination = remember(activeGame) {
        activeGame?.let {
            Log.d(
                TAG,
                "Determined start destination based on active game phase: ${activeGame?.currentPhase}"
            )
            mapGamePhaseToRoute(it.currentPhase)
        } ?: WearNavRoutes.GAME_LIST_SCREEN
    }

    AppScaffold(
        timeText = { TimeText() },
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
    ) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(WearNavRoutes.GAME_LIST_SCREEN) {
                GameListScreen(
                    allGames = allGames,
                    activeGame = activeGame,
                    isOnline = isOnline,
                    onGameSelected = { selectedGame ->
                        gameViewModel.selectGameToStart(selectedGame)
                        gameViewModel.activeGame.value?.let { game ->
                            gameViewModel.proceedToNextPhaseManager(game.copy())
                        } ?: Log.w(
                            TAG,
                            "onGameSelected: Cannot proceed, active game is null after selection."
                        )
                        // Consider navigating only after activeGame is confirmed non-null or phase has progressed
                        navController.navigate(WearNavRoutes.PRE_GAME_SETUP_SCREEN)
                    },
                    onViewLog = { gameId ->
                        navController.navigate(WearNavRoutes.gameLogRoute(gameId))
                    },
                    onNavigateToNewGame = {
                        gameViewModel.createNewDefaultGame()
                        gameViewModel.activeGame.value?.let { game ->
                            gameViewModel.proceedToNextPhaseManager(game.copy())
                        } ?: Log.w(
                            TAG,
                            "onNavigateToNewGame: Cannot proceed, active game is null after creating new game."
                        )
                        navController.navigate(WearNavRoutes.PRE_GAME_SETUP_SCREEN)
                    }
                )
            }
            composable(WearNavRoutes.PRE_GAME_SETUP_SCREEN) {
                PreGameSetupRoute(
                    navController = navController,
                    gameViewModel = gameViewModel
                )

            }

            composable(WearNavRoutes.KICK_OFF_SELECTION_SCREEN) {
                KickOffSelectionScreen(
                    game = activeGame!!, // Assuming activeGame will not be null here
                    onSetKickOffTeam = { team ->
                        gameViewModel.setKickOffTeam(team)
                        gameViewModel.activeGame.value?.let { game ->
                            gameViewModel.proceedToNextPhaseManager(game.copy())
                        } ?: Log.w(
                            TAG,
                            "KickOffSelectionScreen onConfirm: Cannot proceed, active game is null."
                        )
                        navController.navigate(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                            popUpTo(WearNavRoutes.GAME_LIST_SCREEN) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                val isPlayableRegularPhase = activeGame?.currentPhase?.isPlayablePhase() == true &&
                        activeGame?.currentPhase != GamePhase.PENALTIES

                val horizontalPagerState = rememberPagerState(
                    initialPage = 1,
                    pageCount = { if (isPlayableRegularPhase) 3 else 1 })
                val verticalPagerState =
                    rememberPagerState(initialPage = 0, pageCount = { 2 }) // 0: Game, 1: Settings

                if (activeGame != null) {
                    val onKickOff = remember { { gameViewModel.kickOff() } }
                    val onResetGame = remember { { gameViewModel.resetGame() } }
                    val onToggleCollectPositionInfo = remember { { enabled: Boolean -> gameViewModel.setCollectPositionInfo(enabled) } }
                    val onSetToHaveExtraTime = remember { { gameViewModel.setToHaveExtraTime() } }
                    val onSetToHavePenalties = remember { { gameViewModel.setToHavePenalties() } }
                    val onToggleTimer = remember { { gameViewModel.toggleTimer() } }
                    val onAddGoal = remember { { team: Team -> gameViewModel.addGoal(team) } }
                    val onResetPeriodTimer = remember { { gameViewModel.resetTimer() } }
                    val onPenaltyAttemptRecorded = remember { { scored: Boolean -> gameViewModel.recordPenaltyAttempt(scored) } }
                    
                    val onNavigateToLogCard = remember(navController) { { team: Team, cardType: CardType ->
                        navController.navigate(WearNavRoutes.logCardRoute(team, cardType))
                    } }
                    
                    val onNavigateToGameLog = remember(navController) { {
                        gameViewModel.activeGame.value?.let { game ->
                            navController.navigate(WearNavRoutes.gameLogRoute(game.id))
                        } ?: Log.w(TAG, "Cannot navigate to game log, active game is null")
                        Unit
                    } }
                    
                    val onNavigateToAnalytics = remember(navController) { {
                        navController.navigate(WearNavRoutes.GAME_ANALYTICS_SCREEN)
                    } }
                    
                    val onEndPhase = remember { {
                        gameViewModel.activeGame.value?.let { game ->
                            gameViewModel.proceedToNextPhaseManager(game.copy())
                        } ?: Log.w(TAG, "GameScreenWithPager onEndPhase: Cannot proceed, active game is null.")
                        Unit
                    } }
                    
                    val onConfirmEndMatch = remember(navController) { {
                        val gameToEnd = gameViewModel.activeGame.value
                        if (gameToEnd != null) {
                            gameViewModel.finishAndSyncActiveGame(gameToEnd.id)
                            navController.navigate(WearNavRoutes.GAME_LIST_SCREEN) {
                                popUpTo(WearNavRoutes.GAME_LIST_SCREEN) { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            Log.w(TAG, "onConfirmEndMatch: Cannot finish game, activeGame is null.")
                            navController.popBackStack(WearNavRoutes.GAME_LIST_SCREEN, false)
                        }
                        Unit
                    } }

                    GameScreenWithPager(
                        modifier = Modifier.fillMaxSize(),
                        game = activeGame!!,
                        collectPositionInfo = collectPositionInfo,
                        onToggleCollectPositionInfo = onToggleCollectPositionInfo,
                        horizontalPagerState = horizontalPagerState,
                        verticalPagerState = verticalPagerState,
                        onKickOff = onKickOff,
                        onResetGame = onResetGame,
                        onSetToHaveExtraTime = onSetToHaveExtraTime,
                        onSetToHavePenalties = onSetToHavePenalties,
                        onToggleTimer = onToggleTimer,
                        onAddGoal = onAddGoal,
                        onNavigateToLogCard = onNavigateToLogCard,
                        onNavigateToGameLog = onNavigateToGameLog,
                        onNavigateToAnalytics = onNavigateToAnalytics,
                        onEndPhase = onEndPhase,
                        onResetPeriodTimer = onResetPeriodTimer,
                        onConfirmEndMatch = onConfirmEndMatch,
                        onPenaltyAttemptRecorded = onPenaltyAttemptRecorded
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading Game Details...")
                    }
                }
            }

            composable(
                route = "${WearNavRoutes.LOG_CARD_SCREEN}/{${WearNavRoutes.TEAM_ARG}}/{${WearNavRoutes.CARD_TYPE_ARG}}",
                arguments = listOf(
                    navArgument(WearNavRoutes.TEAM_ARG) { type = NavType.StringType },
                    navArgument(WearNavRoutes.CARD_TYPE_ARG) { type = NavType.StringType }
                )
            )
            { backStackEntry ->

                val teamId = backStackEntry.arguments?.getString(WearNavRoutes.TEAM_ARG)
                val cardTypeString = backStackEntry.arguments?.getString("cardType")

                val team =
                    teamId?.let { Team.valueOf(it.uppercase()) }
                val cardType = cardTypeString?.let { CardType.valueOf(it.uppercase()) }
                var confirmedRedPlayerNumber by remember { mutableStateOf<Int?>(null) }
                var showRedCardConfirmationDialog by remember { mutableStateOf(false) }
                if (team != null && cardType != null) {
                    LogCardScreen(
                        preselectedTeam = team,
                        cardType = cardType,
                        onLogCard = { loggedTeam, playerNum, loggedCardType ->
                            var autoRedCardIssued = false

                            // Log the original card (yellow or direct red)
                            gameViewModel.addCard(loggedTeam, playerNum, loggedCardType)

                            // --- Logic for two yellows leading to a red ---
                            if (loggedCardType == CardType.YELLOW) {
                                val activeGameSnapshot = gameViewModel.activeGame.value
                                if (activeGameSnapshot != null) {
                                    val yellowCardsForPlayer =
                                        activeGameSnapshot.events.filterIsInstance<CardIssuedEvent>()
                                            .count { event ->
                                                event.team == loggedTeam &&
                                                        event.playerNumber == playerNum &&
                                                        event.cardType == CardType.YELLOW
                                            }

                                    if (yellowCardsForPlayer == 2) { // Current yellow makes it the second
                                        // Issue an automatic red card
                                        gameViewModel.addCard(loggedTeam, playerNum, CardType.RED)
                                        // Unified Confirmation Dialog
                                        confirmedRedPlayerNumber = playerNum
                                        showRedCardConfirmationDialog = true
                                        Log.i(
                                            TAG,
                                            "Second yellow for player $playerNum of team $loggedTeam. Auto red card issued."
                                        )
                                    }
                                }
                            }

                            if (!showRedCardConfirmationDialog) { // Navigate only if dialog isn't shown
                                navController.navigate(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                                    popUpTo(WearNavRoutes.GAME_LIST_SCREEN) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onCancel = {
                            navController.navigate(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                                popUpTo(WearNavRoutes.GAME_LIST_SCREEN) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                    ConfirmationDialog(
                        visible = showRedCardConfirmationDialog,
                        onDismissRequest = {
                            showRedCardConfirmationDialog = false
                            navController.navigate(WearNavRoutes.GAME_IN_PROGRESS_SCREEN) {
                                popUpTo(WearNavRoutes.GAME_LIST_SCREEN) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        text = {
                            Text(
                                text = "Second yellow for player $confirmedRedPlayerNumber of team $team. Auto red card issued.",
                                color = MaterialTheme.colorScheme.onError

                            )
                        },
                        colors = ConfirmationDialogDefaults.colors(
                            iconColor = MaterialTheme.colorScheme.onErrorContainer,
                            iconContainerColor = MaterialTheme.colorScheme.primary,
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Report,
                            contentDescription = null,
                            modifier = Modifier.size(ConfirmationDialogDefaults.SmallIconSize),
                        )
                    }
                } else {
                    Text("Error: Invalid navigation arguments for Log Card.")
                    LaunchedEffect(Unit) {
                        delay(2000)
                        navController.popBackStack()
                    }
                }
            }
            composable(
                "${WearNavRoutes.GAME_LOG_SCREEN}/{${WearNavRoutes.GAME_ID_ARG}}",
                arguments = listOf(
                    navArgument(WearNavRoutes.GAME_ID_ARG) {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val currentActiveGameSnapshot = activeGame
                val gameIdString =
                    backStackEntry.arguments?.getString(WearNavRoutes.GAME_ID_ARG)
                var game: Game? = null
                if (currentActiveGameSnapshot != null && currentActiveGameSnapshot.id == gameIdString) {
                    // The requested gameId is the currently active game. Use its state directly.
                    Log.i(
                        TAG,
                        "GameLog: Using current active game data (ID: $gameIdString) from ViewModel. Event count: ${currentActiveGameSnapshot.events.size}"
                    )
                    game = currentActiveGameSnapshot
                } else {
                    game = gameIdString?.let { idToFind ->
                        allGames.find { game -> game.id == idToFind } // Assuming Game has a String id property
                    }
                }
                game?.let { game ->
                    GameLogScreen(
                        game = game,
                        onDismiss = { navController.popBackStack() },
                        onRemoveEvent = { event ->
                            gameViewModel.removeEvent(event, gameIdString)
                        },
                    )
                }
            }
            composable(WearNavRoutes.GAME_ANALYTICS_SCREEN) {
                activeGame?.let { game ->
                    GameAnalyticsScreen(
                        game = game,
                        collectPositionInfo = collectPositionInfo,
                        onDismiss = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}


fun mapGamePhaseToRoute(phase: GamePhase): String {
    return when (phase) {
        GamePhase.FIRST_HALF, GamePhase.HALF_TIME, GamePhase.SECOND_HALF,
        GamePhase.EXTRA_TIME_FIRST_HALF, GamePhase.EXTRA_TIME_HALF_TIME, GamePhase.EXTRA_TIME_SECOND_HALF,
        GamePhase.PENALTIES, GamePhase.GAME_ENDED -> WearNavRoutes.GAME_IN_PROGRESS_SCREEN

        GamePhase.NOT_STARTED -> WearNavRoutes.GAME_LIST_SCREEN
        GamePhase.PRE_GAME -> WearNavRoutes.PRE_GAME_SETUP_SCREEN
        GamePhase.KICK_OFF_SELECTION_FIRST_HALF,
        GamePhase.KICK_OFF_SELECTION_EXTRA_TIME,
        GamePhase.KICK_OFF_SELECTION_PENALTIES -> WearNavRoutes.KICK_OFF_SELECTION_SCREEN
    }
}