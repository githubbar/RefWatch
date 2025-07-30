package com.databelay.refwatch.wear.presentation.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.GameStatus
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow

private const val TAG = "GameScheduleScreen"


@Composable
fun GameListScreen(
    games: List<Game>,
    activeGame: Game, // The current game state from the ViewModel
    onGameSelected: (Game) -> Unit,
    onViewLog: (String) -> Unit, // Callback for viewing a FINISHED game's log
    onNavigateToNewGame: () -> Unit,
    onNavigateToGameScreen: () -> Unit // Simple callback to navigate
) {
    var selectedTab by remember { mutableStateOf(GameStatus.SCHEDULED) }
    LaunchedEffect(games) { // Log whenever the games list changes
        Log.d(TAG, "Received games list: Count = ${games.size}")
        games.forEachIndexed { index, game ->
            Log.d(TAG, "Game $index: ID=${game.id}, Status=${game.status}, DateTimeEpoch=${game.gameDateTimeEpochMillis}, Home=${game.homeTeamName}")
        }
    }
    // Filter and sort the lists, just like on the watch
    val (upcomingGames, pastGames) = remember(games) {
        val (scheduled, completed) = games.partition { it.status == GameStatus.SCHEDULED }
        Log.d(TAG, "Scheduled partition: Count = ${scheduled.size}")
        Log.d(TAG, "Completed partition (Past Games): Count = ${completed.size}")
        completed.forEach { game -> // Log details of games considered "past"
            Log.d(TAG, "Past Game (after partition): ID=${game.id}, Status=${game.status}, Home=${game.homeTeamName}")
        }
        Pair(
            scheduled.sortedBy { it.gameDateTimeEpochMillis },
            completed.sortedByDescending { it.gameDateTimeEpochMillis }
        )
    }
    val gamesToDisplay = if (selectedTab == GameStatus.SCHEDULED) upcomingGames else pastGames

    // --- LOGIC TO DETERMINE IF A GAME IS RESUMABLE ---
    val isGameResumable = remember(activeGame) {
        // A game is resumable if it's not in its initial pre-game state
        // AND not in a final state.
        activeGame.currentPhase != GamePhase.PRE_GAME && activeGame.currentPhase != GamePhase.GAME_ENDED
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- RESUME GAME CHIP (shows at the very top if a game is resumable) ---
        if (isGameResumable) {
            item {
                Chip(
                    onClick = onNavigateToGameScreen, // Navigate directly to the game screen
                    label = { Text("Resume Game") },
                    secondaryLabel = { Text("${activeGame.homeTeamName} vs ${activeGame.awayTeamName}") },
                    icon = {
                        Icon(
                            Icons.Outlined.PlayCircle,
                            contentDescription = "Resume Current Game"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(0.95f),
                    colors = ChipDefaults.primaryChipColors() // Primary action
                )
            }
            item {
                ListHeader { Text("Or Select Another") } // Header to separate resume from the list
            }
        } else {
            // Show the main title only if there's no game to resume
            item {
                Text(
                    "Game Schedule",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
            }
        }


        // --- TABS FOR FILTERING ---
        item {
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == GameStatus.SCHEDULED,
                    onClick = { selectedTab = GameStatus.SCHEDULED },
                    text = { Text(
                        "Upcoming",
                        style = MaterialTheme.typography.button,
                        color = if (selectedTab == GameStatus.SCHEDULED) {
                            MaterialTheme.colors.primary
                        } else {
                            MaterialTheme.colors.primary.copy(alpha = 0.7f)
                        }
                    ) }
                )
                Tab(
                    selected = selectedTab == GameStatus.COMPLETED,
                    onClick = { selectedTab = GameStatus.COMPLETED },
                    text = { Text(
                        "Past",
                        style = MaterialTheme.typography.button,
                        color = if (selectedTab == GameStatus.COMPLETED) {
                        MaterialTheme.colors.primary
                        } else {
                            MaterialTheme.colors.primary.copy(alpha = 0.7f)
                        }
                    )}
                )
            }
        }

        // --- AD-HOC GAME BUTTON (only on "Upcoming" tab) ---
        if (selectedTab == GameStatus.SCHEDULED) {
            item {
                Chip(
                    onClick = onNavigateToNewGame,
                    label = { Text("New Ad-Hoc Game") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Start New Ad-Hoc Game") },
                    modifier = Modifier.fillMaxWidth(0.9f),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }

        // --- GAME LIST or EMPTY MESSAGE ---
        if (gamesToDisplay.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(0.7f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No ${selectedTab.name.lowercase()} games.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(
                items = gamesToDisplay,
                key = { game -> game.id }
            ) { game ->
                ScheduledGameItem(
                    game = game,
                    onClick = {
                        if (game.status == GameStatus.SCHEDULED) {
                            // TODO: Add a confirmation dialog here if a game is already in progress
                            // "This will replace your current active game. Continue?"
                            onGameSelected(game)
                        }
                        else {
                            // If game is finished, navigate to the log
                            onViewLog(game.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ScheduledGameItem(game: Game, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.95f),
        colors = ChipDefaults.primaryChipColors(),
        label = {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "${game.homeTeamName} vs ${game.awayTeamName}",
                    maxLines = 2, // Allow two lines for long names
                    style = MaterialTheme.typography.button
                )
                // Show score for completed games
                if (game.status == GameStatus.COMPLETED) {
                    Text(
                        text = "Final: ${game.homeScore} - ${game.awayScore}",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        },
        secondaryLabel = {
            Column(horizontalAlignment = Alignment.Start) {
                // Combine venue and date/time if available
                val dateTimeString = game.formattedGameDateTime ?: "No time set"
                val venueString = game.venue?.takeIf { it.isNotBlank() }

                Text(text = dateTimeString)
                if (venueString != null) {
                    Text(text = venueString, maxLines = 1)
                }
            }
        }
    )
}