package com.databelay.refwatch.navigation // Create this package

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.databelay.refwatch.common.SimpleIcsEvent // If still used for import simulation
import com.databelay.refwatch.common.SimpleIcsEventFactory
import com.databelay.refwatch.common.SimpleIcsParser
import com.databelay.refwatch.games.GameListScreen
import com.databelay.refwatch.games.MobileGameViewModel
import com.databelay.refwatch.games.AddEditGameScreen // Your new screen
import com.databelay.refwatch.games.AddEditGameViewModel

const val TAG = "RefWatchNavHost"

@Composable
fun RefWatchNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val mobileGameViewModel: MobileGameViewModel = hiltViewModel() // GameViewModel for game list
    val authState by authViewModel.authState.collectAsState()


    // This LaunchedEffect is responsible for reacting to authState changes globally
    // and navigating to the correct top-level screen (Auth or GameList).
    LaunchedEffect(authState) {
        Log.d(TAG, "AuthState changed: $authState. Current route: ${navController.currentBackStackEntry?.destination?.route}")
        when (authState) {
            is AuthState.Authenticated -> {
                // If we are not already on a screen within the authenticated part of the app, navigate.
                // This prevents re-navigating if already on GameList or AddEditGame.
                if (navController.currentBackStackEntry?.destination?.route != MobileNavRoutes.GAME_LIST_SCREEN &&
                    navController.currentBackStackEntry?.destination?.route?.startsWith(MobileNavRoutes.ADD_EDIT_GAME_SCREEN.substringBefore("?")) != true) {
                    Log.d(TAG, "Navigating to GAME_LIST_SCREEN due to Authenticated state.")
                    navController.navigate(MobileNavRoutes.GAME_LIST_SCREEN) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            is AuthState.Unauthenticated, is AuthState.Error -> {
                // If we are not already on the AuthScreen, navigate.
                if (navController.currentBackStackEntry?.destination?.route != MobileNavRoutes.AUTH_SCREEN) {
                    Log.d(TAG, "Navigating to AUTH_SCREEN due to Unauthenticated or Error state.")
                    navController.navigate(MobileNavRoutes.AUTH_SCREEN) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
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

            // Make sure MobileGameViewModel is aware of user changes for Firestore queries
            // This is now handled by MobileGameViewModel observing authViewModel.currentUser
            // LaunchedEffect(authViewModel.currentUser.value) {
            //     authViewModel.currentUser.value?.let { user ->
            //         mobileGameViewModel.onUserChanged(user) // Or however your VM listens to auth state
            //     }
            // }

            GameListScreen(
                games = games,
                onAddGame = {
                    // Navigate to AddEditGameScreen for a new game
                    navController.navigate(MobileNavRoutes.addEditGameRoute(null))
                },
                onEditGame = { gameToEdit ->
                    navController.navigate(MobileNavRoutes.addEditGameRoute(gameToEdit.id))
                },
                onDeleteGame = { gameToDelete -> mobileGameViewModel.deleteGame(gameToDelete) },
                onSignOut = {
                    authViewModel.signOut() // Initiate sign out, AuthState change will trigger navigation
                },
                onImportGames = {
                    // Example Import (replace with your actual file picker logic)
                    val icsEvents = SimpleIcsParser.parse("""
                            BEGIN:VEVENT
                            DTSTAMP:20250327T113750Z
                            UID:4829e374-f5e1-48d1-8c21-044404e66152
                            DTSTART;TZID=America/New_York:20250329T143000
                            DTEND;TZID=America/New_York:20250329T163000
                            SUMMARY:Referee Assignment: Asst Referee 1 - 3071 Cutters SC U18 Boys Red vs.  Indy Eleven 2007/2008B White - ISL SPRING 2025 (11U-19/20U\, All Divisions)
                            END:VEVENT
                            BEGIN:VEVENT
                            DTSTAMP:20250327T113750Z
                            UID:826d01cc-f123-4018-bbd3-08c820b36cc6
                            DTSTART;TZID=America/New_York:20250330T130000
                            DTEND;TZID=America/New_York:20250330T144500
                            SUMMARY:Referee Assignment: Referee - 2846 Cutters SC 2009/10 Boys Red vs.   SCSA Eleven 2009B Red - ISL SPRING 2025 (11U-19/20U\, All Divisions)
                            END:VEVENT""")
                    val gamesToImport = icsEvents.map { Game(it) }
                    mobileGameViewModel.addOrUpdateGames(gamesToImport)
                }
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
                    val gameToEdit = mobileGameViewModel.gamesList.value.find { it.id == gameId }
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
fun LoadingScreen() { // Simple placeholder
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}