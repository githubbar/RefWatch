package com.databelay.refwatch.games

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import java.text.SimpleDateFormat
import java.util.*
import com.databelay.refwatch.common.Game

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    games: List<Game>,
    onAddGame: () -> Unit,
    onEditGame: (Game) -> Unit, // Callback for editing
    onDeleteGame: (game: Game) -> Unit,
    onSignOut: () -> Unit,
    onImportGames: () -> Unit, // Callback for importing
    onSendPing: () -> Unit
) {
    // Debug
//    var debugAssetLoadAttempted by remember { mutableStateOf(false) }
//    if (BuildConfig.DEBUG && !debugAssetLoadAttempted && !isLoading) {
//        LaunchedEffect(Unit) {
//            debugAssetLoadAttempted = true
//            Toast.makeText(context, "DEBUG: Auto-processing ICS from assets", Toast.LENGTH_SHORT).show()
//            loadFromAssetsAndParse()
//        }
//    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Referee Games") },
                actions = {
                    TextButton(onClick = onSignOut) { Text("Sign Out") }
                }
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
            Text(
                text = "RefWatch",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(onClick = onSendPing, modifier = Modifier.fillMaxWidth()) {
                Text("Send Ping")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onImportGames, modifier = Modifier.fillMaxWidth()) {
                Text("Import ICS")
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (games.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No games scheduled. Add one or import ICS.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(games, key = { it.id }) { game ->
                        GameItem(
                            game = game,
                            onEdit = { onEditGame(game) }, // Call onEditGame
                            onDelete = { onDeleteGame(game) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GameItem(game: Game, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy 'at' HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
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