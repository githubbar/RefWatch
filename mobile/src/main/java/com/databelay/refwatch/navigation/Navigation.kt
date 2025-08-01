package com.databelay.refwatch.navigation // Create this package

import android.net.Uri
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.databelay.refwatch.auth.AuthScreen
import com.databelay.refwatch.auth.AuthState
import com.databelay.refwatch.auth.AuthViewModel
import com.databelay.refwatch.common.Game // Your Game class
import com.databelay.refwatch.common.SimpleIcsParser
import com.databelay.refwatch.games.GameListScreen
import com.databelay.refwatch.games.MobileGameViewModel
import com.databelay.refwatch.games.AddEditGameViewModel
import com.databelay.refwatch.games.AddEditGameScreen
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.databelay.refwatch.common.SimpleIcsEvent
import com.databelay.refwatch.games.GameLogScreen
import kotlinx.coroutines.launch

const val TAG = "RefWatchNavHost"

/**
 * Creates and remembers an ActivityResultLauncher for picking a single content item (e.g., a file).
 *
 * @param onResult Callback function that will be invoked with the Uri of the selected content,
 *                 or null if the selection was cancelled or failed.
 * @return A ManagedActivityResultLauncher that you can call `.launch()` on.
 */
@Composable
fun rememberFilePickerLauncher(
    onResult: (Uri?) -> Unit
): ManagedActivityResultLauncher<String, Uri?> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = onResult
    )
}
@Composable
fun RefWatchNavHost() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val mobileGameViewModel: MobileGameViewModel = hiltViewModel() // GameViewModel for game list
    val authState by authViewModel.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope() // For parsing in a background thread

    // ---- STATE TO CONTROL DELAYED COMPOSITION ----
    var canInitializeViewModels by remember { mutableStateOf(false) }

    // This LaunchedEffect is responsible for reacting to authState changes globally
    // and navigating to the correct top-level screen (Auth or GameList).
    LaunchedEffect(authState) {
        Log.d(
            TAG,
            "AuthState changed: $authState. Current route: ${navController.currentBackStackEntry?.destination?.route}"
        )
        when (authState) {
            is AuthState.Authenticated -> {
                // If we are not already on a screen within the authenticated part of the app, navigate.
                // This prevents re-navigating if already on GameList or AddEditGame.
                if (navController.currentBackStackEntry?.destination?.route != MobileNavRoutes.GAME_LIST_SCREEN &&
                    navController.currentBackStackEntry?.destination?.route?.startsWith(
                        MobileNavRoutes.ADD_EDIT_GAME_SCREEN.substringBefore("?")
                    ) != true
                ) {
                    Log.d(TAG, "Navigating to GAME_LIST_SCREEN due to Authenticated state.")
                    navController.navigate(MobileNavRoutes.GAME_LIST_SCREEN) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            }

            is AuthState.Unauthenticated, is AuthState.Error -> {
                // If we are not already on the AuthScreen, navigate.
                if (navController.currentBackStackEntry?.destination?.route != MobileNavRoutes.AUTH_SCREEN) {
                    Log.d(
                        TAG,
                        "Navigating to AUTH_SCREEN due to Unauthenticated or Error state."
                    )
                    navController.navigate(MobileNavRoutes.AUTH_SCREEN) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            }

            AuthState.Loading -> {
                // If currently on Loading screen, do nothing, let it decide.
                // If on another screen and auth becomes Loading (e.g. during re-auth),
                // you might want to navigate to LoadingScreen or show an overlay.
                // For simplicity, often this state is handled within specific operations.
                Log.d(TAG, "AuthState is Loading.")
            }
        }
    }

    // Define the file picker launcher within the scope where you need it (or pass it down)
    // The onResult lambda will now handle parsing and updating the ViewModel
    val filePickerLauncher = rememberFilePickerLauncher { uri: Uri? ->
        if (uri != null) {
            // Use a coroutine to parse the file off the main thread
            coroutineScope.launch {
                Log.d(TAG, "URI selected: $uri. Starting ICS parsing.")
                val icsEvents: List<SimpleIcsEvent>? =
                    SimpleIcsParser.parseUri(context.contentResolver, uri)

                if (icsEvents != null) {
                    Log.d(TAG, "Successfully parsed ${icsEvents.size} events from URI.")
                    val gamesToImport = icsEvents.map { Game(it) } // Convert SimpleIcsEvent to Game
                    mobileGameViewModel.addOrUpdateGames(gamesToImport)
                    // Optionally, show a success message to the user (e.g., via a Snackbar or Toast)
                } else {
                    Log.e(TAG, "Failed to parse ICS events from URI.")
                    // Optionally, show an error message
                }
            }
        } else {
            Log.d(TAG, "File selection cancelled.")
            // Optionally, inform the user that selection was cancelled
        }
    }

    NavHost(
        navController = navController,
        startDestination = MobileNavRoutes.LOADING_SCREEN // Start with loading to check auth
    ) {
        composable(MobileNavRoutes.LOADING_SCREEN) {
            LoadingScreen()
            // No navigation logic directly here anymore, handled by the LaunchedEffect above.
            // This screen is just a placeholder until authState is resolved by the LaunchedEffect.
        }

        composable(MobileNavRoutes.AUTH_SCREEN) {
            AuthScreen(
                authViewModel = authViewModel,
                onSignInSuccess = {
                    // The LaunchedEffect(authState) above will handle navigating to GAME_LIST_SCREEN
                    // once authState becomes Authenticated. So this callback might not even need
                    // to navigate explicitly if the state change is reliable and quick.
                    // However, explicit navigation here can feel more responsive.
                    Log.d("RefWatchNavHost", "AuthScreen: onSignInSuccess triggered.")
                    // It's often fine to let the global LaunchedEffect handle it,
                    // or you can navigate here and ensure the LaunchedEffect doesn't fight it.
                    // For now, let the global LaunchedEffect handle it.
                    // If you want explicit navigation:
                    // navController.navigate(MobileNavRoutes.GAME_LIST_SCREEN) {
                    //    popUpTo(MobileNavRoutes.AUTH_SCREEN) { inclusive = true }
                    //    launchSingleTop = true
                    // }
                }
            )
        }

        composable(MobileNavRoutes.GAME_LIST_SCREEN) {
            val games by mobileGameViewModel.gamesList.collectAsState()
            GameListScreen(
                games = games,
                onAddGame = {
                    // Navigate to AddEditGameScreen for a new game
                    navController.navigate(MobileNavRoutes.addEditGameRoute(null))
                },
                onViewLog = { gameToView -> // <-- This correctly handles navigation for completed games
                    navController.navigate(MobileNavRoutes.gameLogRoute(gameToView.id))
                },
                onEditGame = { gameToEdit ->
                    navController.navigate(MobileNavRoutes.addEditGameRoute(gameToEdit.id))
                },
                onDeleteGame = { gameToDelete -> mobileGameViewModel.deleteGame(games, gameToDelete) },
                onSignOut = {
                    authViewModel.signOut() // Initiate sign out, AuthState change will trigger navigation
                },
                onImportGames = {
                    // Example Import (replace with your actual file picker logic)
                    Log.d(TAG, "onImportGames triggered. Launching file picker.")
                    filePickerLauncher.launch("text/calendar")

//                    val icsEvents = SimpleIcsParser.parse(
//                        """
//                        BEGIN:VEVENT
//                        DTSTAMP:20250327T113750Z
//                        UID:4829e374-f5e1-48d1-8c21-044404e66152
//                        DTSTART;TZID=America/New_York:20250329T143000
//                        DTEND;TZID=America/New_York:20250329T163000
//                        SUMMARY:Referee Assignment: Asst Referee 1 - 3071 Cutters SC U18 Boys Red vs.  Indy Eleven 2007/2008B White - ISL SPRING 2025 (11U-19/20U\, All Divisions)
//                        END:VEVENT
//                        BEGIN:VEVENT
//                        DTSTAMP:20250327T113750Z
//                        UID:826d01cc-f123-4018-bbd3-08c820b36cc6
//                        DTSTART;TZID=America/New_York:20250330T130000
//                        DTEND;TZID=America/New_York:20250330T144500
//                        SUMMARY:Referee Assignment: Referee - 2846 Cutters SC 2009/10 Boys Red vs.   SCSA Eleven 2009B Red - ISL SPRING 2025 (11U-19/20U\, All Divisions)
//                        END:VEVENT"""
//                    )
//                    val gamesToImport = icsEvents.map { Game(it) }
//                    mobileGameViewModel.addOrUpdateGames(gamesToImport)
                },
            )
        }
        // It tells the NavHost what to display for the "game_log?{gameId}" route.
        composable(
            // The route uses the base name and defines an optional query parameter "?gameId={gameId}"
            route = "${MobileNavRoutes.GAME_LOG_SCREEN}?gameId={gameId}",
            arguments = listOf(
                navArgument("gameId") {
                    type = NavType.StringType
                    nullable = true // Correctly marked as optional
                }
            )
        ) {
            backStackEntry ->
            // 1. Retrieve the gameId from the navigation arguments.
            //    It can be null if no ID was passed.
            val gameId = backStackEntry.arguments?.getString("gameId")

            // 2. Find the correct Game object from the ViewModel's list using the ID.
            //    The '.value' gets the current list from the StateFlow.
            //    If gameId is null, this 'find' operation will safely return null.
            val selectedGame = mobileGameViewModel.gamesList.value.find { it.id == gameId }

            // 3. Display the GameLogScreen with the found game.
            //    Your GameLogScreen should be designed to handle a null game.
            GameLogScreen(
                game = selectedGame,
                navController = navController
            )
        }
        composable(
            route = "${MobileNavRoutes.ADD_EDIT_GAME_SCREEN}?gameId={gameId}",
            arguments = listOf(navArgument("gameId") {
                type = NavType.StringType
                nullable = true
            })
        ) { backStackEntry ->
            val addEditViewModel: AddEditGameViewModel = hiltViewModel()
            val gameId = backStackEntry.arguments?.getString("gameId")

            LaunchedEffect(gameId) {
                if (gameId != null) {
                    // Fetch the game from the main list for editing
                    // This assumes MobileGameViewModel.gamesList is up-to-date
                    val gameToEdit =
                        mobileGameViewModel.gamesList.value.find { it.id == gameId }
                    addEditViewModel.initializeForm(gameToEdit) // Initialize with game data
                } else {
                    addEditViewModel.initializeForm(null) // New game
                }
            }

            AddEditGameScreen(
                navController = navController,
                mobileGameViewModel = mobileGameViewModel, // Needed to save the game
                addEditViewModel = addEditViewModel
            )
        }
    }
}

@Composable
fun LoadingScreen(message: String? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            message?.let {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.Text(it)
            }
        }
    }
}