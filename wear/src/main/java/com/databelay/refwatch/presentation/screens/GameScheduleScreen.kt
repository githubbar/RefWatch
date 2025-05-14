package com.databelay.refwatch.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.databelay.refwatch.common.GameSettings // You'll need to define this data class

// Define this in your data package (e.g., models.kt)
// import android.os.Parcelable
// import kotlinx.parcelize.Parcelize
// @Parcelize
// data class GameSettings(
//    val id: String,
//    val summary: String, // e.g., "Team A vs Team B"
//    val startTimeEpochMillis: Long,
//    val location: String?,
//    val description: String?
// ) : Parcelable



@Composable
fun GameScheduleScreen(
    scheduledGames: List<GameSettings>, // <<<< List of GameSettings
    onGameSelected: (GameSettings) -> Unit, // <<<< Callback with GameSettings
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
                    onClick = { onGameSelected(game) }
                )
            }
        }
    }
}

@Composable
fun ScheduledGameItem(game: GameSettings, onClick: () -> Unit) { // <<<< Takes GameSettings
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
                game.ageGroup?.let { Text(text = it, /* ... */) }
                game.venue?.takeIf { it.isNotBlank() }?.let { Text(text = it, /* ... */) }
            }
        },
        // ...
    )
}
