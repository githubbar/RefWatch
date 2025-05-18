package com.databelay.refwatch.presentation.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*

import com.databelay.refwatch.common.Game // You'll need to define this data class

@Composable
fun GameScheduleScreen(
    scheduledGames: List<Game>, // <<<< List of GameSettings
    onGameSelected: (Game) -> Unit, // <<<< Callback with GameSettings
    modifier: Modifier = Modifier
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Game Schedule",
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        if (scheduledGames.isEmpty()) { /* ... */
        } else {
            items(scheduledGames) { game -> // game is now a GameSettings object
                ScheduledGameItem( // This composable now takes GameSettings
                    game = game,
                    onClick = {
                        Log.d("GameScheduleScreen", "Game selected: $game")
                        onGameSelected(game)
                    }

                )
            }
        }
    }
}

@Composable
fun ScheduledGameItem(game: Game, onClick: () -> Unit) { // <<<< Takes GameSettings
    Chip(
        onClick = onClick,
        label = {
            Text(
                text =  "${game.homeTeamName} vs ${game.awayTeamName}" ,
                // ...
            )
        },
        secondaryLabel = {
            Column(horizontalAlignment = Alignment.Start) {
                game.ageGroup?.let { Text(it.displayName, /* ... */) }
                game.venue?.takeIf { it.isNotBlank() }?.let { Text(text = it, /* ... */) }
            }
        },
        // ...
    )
}
