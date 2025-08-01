package com.databelay.refwatch.wear.presentation.screens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GameStatus
import com.databelay.refwatch.wear.WearGameViewModel
import com.databelay.refwatch.wear.data.DataFetchStatus

// Make sure this Composable receives the ViewModel
// import androidx.hilt.navigation.compose.hiltViewModel // If using Hilt for ViewModel in NavGraph

private const val TAG = "GameListScreen"

@Composable
fun GameListScreen(
    // viewModel: WearGameViewModel = hiltViewModel(), // Example if using Hilt in NavGraph
    viewModel: WearGameViewModel, // Or pass it directly
    onGameSelected: (Game) -> Unit,
    onViewLog: (String) -> Unit,
    onNavigateToNewGame: () -> Unit,
    onNavigateToGameScreen: () -> Unit
) {
    // Collect states from ViewModel
    val allGames by viewModel.gamesList.collectAsState()
    val dataFetchStatus by viewModel.dataFetchStatus.collectAsState()
    val activeGameFromVM by viewModel.activeGame.collectAsState() // Assuming this holds the resumable game

    var selectedTab by remember { mutableStateOf(GameStatus.SCHEDULED) }

    // This logic to derive upcoming/past games from allGames is good
    val (upcomingGames, pastGames) = remember(allGames) {
        val (scheduled, completed) = allGames.partition { it.status == GameStatus.SCHEDULED }
        Pair(
            scheduled.sortedBy { it.gameDateTimeEpochMillis },
            completed.sortedByDescending { it.gameDateTimeEpochMillis }
        )
    }
    val gamesToDisplay = if (selectedTab == GameStatus.SCHEDULED) upcomingGames else pastGames

    // Assuming activeGame for resuming is derived or passed to the ViewModel
    val isGameResumable = remember(activeGameFromVM) {
        activeGameFromVM != null && activeGameFromVM?.status != GameStatus.COMPLETED && activeGameFromVM?.status != GameStatus.SCHEDULED
        // Adjust this logic based on how your 'activeGame' is defined and when it's resumable
    }

    LaunchedEffect(Unit) {
        // You might want to trigger a connectivity check when the screen is first composed
        // if onCapabilityChanged isn't updating fast enough or for an initial proactive check.
        // viewModel.performConnectivityCheck() // Uncomment if you want this behavior
    }

    // Use a Column as the root layout
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // --- Fixed Content Area ---

        // "Resume Game" Chip or "Game Schedule" Title (conditionally placed first)
        // This logic ensures these items are also fixed above the scrollable list.
        if (isGameResumable && activeGameFromVM != null) {
            Chip(
                onClick = onNavigateToGameScreen,
                label = { Text("Resume Game") }, // Assuming androidx.wear.compose.material.Text
                secondaryLabel = { Text("${activeGameFromVM!!.homeTeamName} vs ${activeGameFromVM!!.awayTeamName}") },
                icon = { Icon(Icons.Outlined.PlayCircle, contentDescription = "Resume Current Game") },
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(top = 12.dp, bottom = 4.dp), // Added some padding
                colors = ChipDefaults.primaryChipColors()
            )
            ListHeader { Text("Or Select Another", modifier = Modifier.padding(bottom = 4.dp)) }
        } else {
            Text( // Assuming androidx.wear.compose.material.Text
                "Game Schedule",
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )
        }

        // TabRow - This will now be fixed
        TabRow(
            selectedTabIndex = if (selectedTab == GameStatus.SCHEDULED) 0 else 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp) // Add some space below the tabs
        ) {
            Tab(
                selected = selectedTab == GameStatus.SCHEDULED,
                onClick = { selectedTab = GameStatus.SCHEDULED },
                text = {
                    Text( // Using M3 Text for Tab content
                        text = "Future (${upcomingGames.size})",
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge, // M3 typography
                        color = if (selectedTab == GameStatus.SCHEDULED) {
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        }
                    )
                }
            )
            Tab(
                selected = selectedTab == GameStatus.COMPLETED,
                onClick = { selectedTab = GameStatus.COMPLETED },
                text = {
                    Text( // Using M3 Text for Tab content
                        "Past (${pastGames.size})",
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge, // M3 typography
                        color = if (selectedTab == GameStatus.COMPLETED) {
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        }
                    )
                }
            )
        }

        // --- Scrollable Content Area ---
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                // "New Ad-Hoc Game" Chip (conditionally fixed if it's always above the list for "Scheduled")
                if (selectedTab == GameStatus.SCHEDULED) {
                    Chip(
                        onClick = onNavigateToNewGame,
                        label = {
                            Text(
                                "New Ad-Hoc Game",
                                color = MaterialTheme.colors.onSurface
                            )
                        }, // Wear Text
                        icon = { Icon(Icons.Filled.Add, contentDescription = "Start New Ad-Hoc Game") },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(bottom = 8.dp), // Add space if it's above the list
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }
            if (gamesToDisplay.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize(0.7f) // Make the box take up significant space
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val emptyMessage = when {
                            selectedTab == GameStatus.SCHEDULED -> {
                                when (dataFetchStatus) {
                                    DataFetchStatus.INITIAL, DataFetchStatus.ERROR_PHONE_UNREACHABLE -> "Can't fetch games.\nPlease connect to RefWatch on the phone."
                                    DataFetchStatus.FETCHING -> "Loading games..."
                                    DataFetchStatus.ERROR_PARSING -> "Error: Could not read game data from phone."
                                    DataFetchStatus.ERROR_UNKNOWN -> "An error occurred while loading games."
                                    DataFetchStatus.NO_DATA_AVAILABLE -> "No upcoming games scheduled on your phone."
                                    DataFetchStatus.SUCCESS -> "No upcoming games." // Should be NO_DATA_AVAILABLE if list is empty
                                    DataFetchStatus.LOADED_FROM_CACHE -> "No upcoming games in cache. Connect to phone to update." // Or show cached if any
                                }
                            }

                            else -> "No past games." // For "Past" tab
                        }
                        Text(
                            text = emptyMessage,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(items = gamesToDisplay, key = { game -> game.id }) { game ->
                    ScheduledGameItem(
                        game = game,
                        onClick = {
                            if (game.status == GameStatus.SCHEDULED) {
                                onGameSelected(game)
                            } else {
                                onViewLog(game.id)
                            }
                        }
                    )
                }
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