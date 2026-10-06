package com.databelay.refwatch.navigation // Create this package

import android.net.Uri
import android.widget.Toast
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.databelay.refwatch.auth.AuthState
import com.databelay.refwatch.auth.AuthViewModel
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.SimpleIcsEvent
import com.databelay.refwatch.common.SimpleIcsParser
import com.databelay.refwatch.screens.AddEditGameRoute
import com.databelay.refwatch.data.AddEditGameViewModel
import com.databelay.refwatch.screens.AuthScreenRoute
import com.databelay.refwatch.screens.GameListScreen
import com.databelay.refwatch.screens.GameLogScreen
import com.databelay.refwatch.data.MobileGameViewModel
import com.databelay.refwatch.data.OnboardingStep
import com.databelay.refwatch.data.OnboardingViewModel
import com.databelay.refwatch.screens.SettingsScreen
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefWatchNavHost() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val mobileGameViewModel: MobileGameViewModel = hiltViewModel() // GameViewModel for game list
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope() // For parsing in a background thread

    // This LaunchedEffect is responsible for reacting to authState changes globally
    // and navigating to the correct top-level screen (Auth or GameList).
    LaunchedEffect(authState) {
        Log.d(
            TAG,
            "AuthState changed: $authState. Current route: ${navController.currentBackStackEntry?.destination?.route}"
        )
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        when (authState) {
            is AuthState.Authenticated -> {
                if (currentRoute != MobileNavRoutes.GAME_LIST_SCREEN &&
                    currentRoute?.startsWith(MobileNavRoutes.ADD_EDIT_GAME_SCREEN.substringBefore("?")) != true &&
                    currentRoute != MobileNavRoutes.SETTINGS_SCREEN && // Ensure settings screen doesn't cause re-navigation
                    currentRoute?.startsWith(MobileNavRoutes.GAME_LOG_SCREEN.substringBefore("?")) != true // Ensure game log doesn't cause re-navigation
                ) {
                    Log.d(TAG, "Navigating to GAME_LIST_SCREEN due to Authenticated state from $currentRoute.")
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
                if (currentRoute != MobileNavRoutes.AUTH_SCREEN) {
                    Log.d(
                        TAG,
                        "Navigating to AUTH_SCREEN due to Unauthenticated or Error state from $currentRoute."
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
                    val appContext = context.applicationContext
                    Toast.makeText(appContext, "Reading ${icsEvents.size} games…", Toast.LENGTH_SHORT).show()
                    mobileGameViewModel.importIcsEvents(icsEvents) { summary ->
                        val skipped = if (summary.alreadyImported > 0) {
                            " ${summary.alreadyImported} already in your list."
                        } else ""
                        val message = when {
                            summary.added == 0 -> "No new games.$skipped"
                            summary.aiFailure == null -> "Added ${summary.added} games.$skipped"
                            summary.readByAi == 0 ->
                                "Added ${summary.added} games with the built-in parser (AI unavailable: ${summary.aiFailure}).$skipped"
                            else ->
                                "Added ${summary.added} games; ${summary.added - summary.readByAi} read by the built-in parser (${summary.aiFailure}).$skipped"
                        }
                        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                    }
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
        composable(MobileNavRoutes.SETTINGS_SCREEN) {
            val authError by authViewModel.authError.collectAsState()
            val authLoading by authViewModel.isLoading.collectAsState()
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onDeleteAccountConfirmed = {
                    authViewModel.deleteUserAccount()
                },
                isDeletingAccount = authLoading,
                deleteAccountError = authError
            )
        }
        composable(MobileNavRoutes.LOADING_SCREEN) {
            LoadingScreen()
            // No navigation logic directly here anymore, handled by the LaunchedEffect above.
            // This screen is just a placeholder until authState is resolved by the LaunchedEffect.
        }

        composable(MobileNavRoutes.AUTH_SCREEN) {
            AuthScreenRoute(
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
            val authStateValue by authViewModel.authState.collectAsStateWithLifecycle()
            // Assuming MobileGameViewModel provides these:
            val gamesToDisplay by mobileGameViewModel.gamesList.collectAsStateWithLifecycle(emptyList())
            val selectedTab by mobileGameViewModel.selectedTab.collectAsStateWithLifecycle()

            // Access the onboarding state through the main ViewModel
            val onboardingState by onboardingViewModel.uiState.collectAsState()

            // Example of showing an onboarding step
            val currentStep = if (
                onboardingState.isTourActive &&
                onboardingState.steps.isNotEmpty() &&
                onboardingState.currentStepIndex < onboardingState.steps.size
            ) {
                onboardingState.steps[onboardingState.currentStepIndex]
            } else {
                null
            }
            Log.d(TAG, "Ran game list screen.")
            GameListScreen(
                authStateValue = authStateValue,
                games = gamesToDisplay,
                selectedTab = selectedTab,
                scrollToTopSignal = mobileGameViewModel.scrollToTopGamesListEvent, // Pass the SharedFlow
                onTabSelected = { newTab -> mobileGameViewModel.selectTab(newTab) }, // ViewModel handles tab change
                onAddGame = {
                    // Navigate to AddEditGameScreen for a new game
                    navController.navigate(MobileNavRoutes.addEditGameRoute(null))
                },
                onEditGame = { gameToEdit ->
                    navController.navigate(MobileNavRoutes.addEditGameRoute(gameToEdit.id))
                },
                onViewLog = { gameToView -> // <-- This correctly handles navigation for completed games
                    navController.navigate(MobileNavRoutes.gameLogRoute(gameToView.id))
                },
                onDeleteGame = { gameToDelete -> mobileGameViewModel.deleteGame(gameToDelete) },
                onSignOut = { authViewModel.signOut()},
                onImportGames = {filePickerLauncher.launch("text/calendar")},
                onNavigateToSettings = { navController.navigate(MobileNavRoutes.SETTINGS_SCREEN) },
                onboardingStep = currentStep, // States are held by the steps
                onNextTooltip = { onboardingViewModel.advanceTour() },
                onDismissTooltip = { onboardingViewModel.dismissTour() },
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
            val gameId = backStackEntry.arguments?.getString("gameId")

            // 1. Collect the gamesList state
            val allGames by mobileGameViewModel.gamesList.collectAsStateWithLifecycle() // Or .collectAsState()

            // 2. Find the selected game from the collected state
            //    This find operation will now re-run if 'allGames' (due to gamesList changing)
            //    or 'gameId' changes.
            val selectedGame = remember(allGames, gameId) { // Use remember to avoid re-calculating unless inputs change
                allGames.find { it.id == gameId }
            }

            // 3. Display the GameLogScreen
            if (gameId != null && selectedGame == null) {
                // Optional: Handle case where gameId is provided but game not found
                // This could be a loading state, an error message, or navigating back.
                // For now, GameLogScreen will receive null, which it should handle.
                Log.w(TAG, "GameLogScreen: gameId '$gameId' provided but game not found in the list.")
            }

            GameLogScreen(
                game = selectedGame, // Pass the derived selectedGame
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
                addEditViewModel.initializeForm(gameId) // Initialize with game data
            }

            AddEditGameRoute(navController = navController)
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