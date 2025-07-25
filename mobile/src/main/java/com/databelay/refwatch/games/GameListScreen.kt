package com.databelay.refwatch.games

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import java.text.SimpleDateFormat
import java.util.*
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.GameStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    games: List<Game>,
    onAddGame: () -> Unit,
    onEditGame: (Game) -> Unit,
    onViewLog: (Game) -> Unit, // <-- Ensure this is passed
    onDeleteGame: (Game) -> Unit,
    onSignOut: () -> Unit,
    onImportGames: () -> Unit, // Callback for importing
    onSendPing: () -> Unit
) {
    Log.d("GameListScreen", "Received games: ${games.map { it.id + " -> " + it.status }}") // Log input games

    var selectedTab by remember { mutableStateOf(GameStatus.SCHEDULED) }

    // Filter and sort the lists, just like on the watch
    val (upcomingGames, pastGames) = remember(games) {
        val (scheduled, completed) = games.partition { it.status == GameStatus.SCHEDULED }
        Pair(
            scheduled.sortedBy { it.gameDateTimeEpochMillis },
            completed.sortedByDescending { it.gameDateTimeEpochMillis }
        )
    }
    val gamesToDisplay = if (selectedTab == GameStatus.SCHEDULED) upcomingGames else pastGames

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Games") },
                actions = {
                    IconButton(onClick = onImportGames) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import ICS")
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out")
                    }                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddGame) {
                Icon(Icons.Filled.Add, "Add Game")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            // --- TABS FOR FILTERING ---
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == GameStatus.SCHEDULED,
                    onClick = { selectedTab = GameStatus.SCHEDULED },
                    text = { Text("Upcoming (${upcomingGames.size})") }
                )
                Tab(
                    selected = selectedTab == GameStatus.COMPLETED,
                    onClick = { selectedTab = GameStatus.COMPLETED },
                    text = { Text("Past (${pastGames.size})") }
                )
            }

            // --- GAME LIST or EMPTY MESSAGE ---
            if (gamesToDisplay.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No games scheduled. Add one or import ICS.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(gamesToDisplay , key = { it.id }) { game ->
                        GameListItem(
                            game = game,
                            onEditGame = onEditGame,
                            onViewLog = onViewLog, // <-- Pass it down to the item
                            onDelete = { onDeleteGame(game) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListItem(
    game: Game,
    onEditGame: (Game) -> Unit,
    onViewLog: (Game) -> Unit, // The callback to view the log
    onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy 'at' HH:mm", Locale.getDefault()) }

    Card(
        // Add conditional logic to the onClick lambda
        onClick = {
            if (game.status == GameStatus.SCHEDULED) {
                // Otherwise (if it's upcoming or in-progress), call the function to edit
                onEditGame(game)
            } else {
                // If the game is finished, call the function to view the log
                onViewLog(game)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column( modifier = Modifier
                .weight(1f) // <<<< KEY CHANGE: Makes this column flexible
                .padding(end = 8.dp) // Optional: Add some padding between text and button
            ) {
                Text("${game.homeTeamName} vs ${game.awayTeamName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color =  MaterialTheme.colorScheme.error)
                game.ageGroup?.let {
                    Text("Age Group: ${it.displayName}", style = MaterialTheme.typography.bodySmall,
                        color =  MaterialTheme.colorScheme.tertiary)
                }
                game.formattedGameDateTime?.let {
                    Text("Time: $it", style = MaterialTheme.typography.bodyMedium)
                }
                game.venue?.let {
                    Text("Location: $it", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "H: ${game.homeScore} - A: ${game.awayScore} (${game.currentPhase})",
                    style = MaterialTheme.typography.bodySmall
                )
                // You can add more details from GameSettings here
                // Row(verticalAlignment = Alignment.CenterVertically) {
                //     Box(modifier = Modifier.size(16.dp).background(game.homeTeamColor))
                //     Spacer(Modifier.width(4.dp))
                //     Text("Home", style = MaterialTheme.typography.bodySmall)
                //     Spacer(Modifier.width(8.dp))
                //     Box(modifier = Modifier.size(16.dp).background(game.awayTeamColor))
                //     Spacer(Modifier.width(4.dp))
                //     Text("Away", style = MaterialTheme.typography.bodySmall)
                // }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete Game", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

