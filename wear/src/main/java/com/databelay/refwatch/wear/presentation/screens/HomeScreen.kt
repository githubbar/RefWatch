package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun HomeScreen(
    onNavigateToSchedule: () -> Unit,
    onNavigateToNewGame: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically) // Center content
    ) {
        item {
            Text(
                "RefWatch", // App Name
                style = MaterialTheme.typography.title1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }
        item {
            Text(
                "Soccer Referee Assistant",
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        item {
            Chip(
                onClick = { onNavigateToNewGame() },
                label = { Text("Start New Game") },
                icon = { Icon(Icons.Filled.PlayCircleOutline, contentDescription = "Start New Game") },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors()
            )
        }

        item {
            Chip(
                onClick = { onNavigateToSchedule()},
                label = { Text("Game Schedule") },
                icon = { Icon(Icons.Filled.CalendarToday, contentDescription = "Game Schedule") },
                modifier = Modifier.fillMaxWidth()
            )
        }

//        item {
//            Chip(
//                onClick = { onNavigate(Screen.LoadIcs.route) },
//                label = { Text("Load Games (ICS)") },
//                icon = { Icon(Icons.Filled.FileUpload, contentDescription = "Load Games from ICS") },
//                modifier = Modifier.fillMaxWidth()
//            )
//        }
    }
}