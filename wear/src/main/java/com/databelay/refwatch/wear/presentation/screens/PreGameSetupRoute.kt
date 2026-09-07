package com.databelay.refwatch.wear.presentation.screens

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.wear.compose.material3.ConfirmationDialog
import com.databelay.refwatch.common.theme.PredefinedJerseyColors
import com.databelay.refwatch.wear.WearGameViewModel
import com.databelay.refwatch.wear.navigation.WearNavRoutes

private const val TAG = "PreGameSetupRoute"

private const val KEY_HOME_TEAM_NAME = "home_team_name"
private const val KEY_AWAY_TEAM_NAME = "away_team_name"

@Composable
fun PreGameSetupRoute(
    navController: NavController,
    gameViewModel: WearGameViewModel = hiltViewModel()
) {
    val activeGame by gameViewModel.activeGame.collectAsState()

    var showHomeColorPickerDialog by remember { mutableStateOf(false) }
    var showAwayColorPickerDialog by remember { mutableStateOf(false) }

    // Team names are collected through the system input activity (keyboard, voice or
    // handwriting) rather than an in-app text field -- Wear Compose has no TextField.
    val textInputLauncher = rememberTextInputLauncher { key, value ->
        when (key) {
            KEY_HOME_TEAM_NAME -> gameViewModel.updateHomeTeamName(value)
            KEY_AWAY_TEAM_NAME -> gameViewModel.updateAwayTeamName(value)
            else -> Log.w(TAG, "Unexpected text input key: $key")
        }
    }

    PreGameSetupScreen(
        game = activeGame,
        onEditHomeTeamNameClick = {
            textInputLauncher.launch(
                key = KEY_HOME_TEAM_NAME,
                title = "Home team",
                label = "Home team name",
                currentValue = activeGame?.homeTeamName
            )
        },
        onEditAwayTeamNameClick = {
            textInputLauncher.launch(
                key = KEY_AWAY_TEAM_NAME,
                title = "Away team",
                label = "Away team name",
                currentValue = activeGame?.awayTeamName
            )
        },
        onHomeColorPickerClick = { showHomeColorPickerDialog = true },
        onAwayColorPickerClick = { showAwayColorPickerDialog = true },
        onSetHalfDuration = { duration -> gameViewModel.setHalfDuration(duration) },
        onSetHalftimeDuration = { duration -> gameViewModel.setHalftimeDuration(duration) },
        onCreateMatchClick = {
            gameViewModel.activeGame.value?.let { game ->
                gameViewModel.proceedToNextPhaseManager(game.copy())
            } ?: Log.w(TAG, "onCreateMatchClick: Cannot proceed, active game is null.")
            navController.navigate(WearNavRoutes.KICK_OFF_SELECTION_SCREEN) {
                popUpTo(WearNavRoutes.GAME_LIST_SCREEN) { inclusive = false }
                launchSingleTop = true
            }
        }
    )

    if (showHomeColorPickerDialog) {
        SimpleColorPickerDialog(
            title = "Home Color",
            availableColors = PredefinedJerseyColors,
            onColorSelected = {
                gameViewModel.updateHomeTeamColor(it)
                showHomeColorPickerDialog = false
            },
            onDismiss = { showHomeColorPickerDialog = false }
        )
    }

    if (showAwayColorPickerDialog) {
        SimpleColorPickerDialog(
            title = "Away Color",
            availableColors = PredefinedJerseyColors,
            onColorSelected = {
                gameViewModel.updateAwayTeamColor(it)
                showAwayColorPickerDialog = false
            },
            onDismiss = { showAwayColorPickerDialog = false }
        )
    }
}
