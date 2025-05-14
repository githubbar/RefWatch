package com.databelay.refwatch.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.databelay.refwatch.navigation.Screen // Your Screen sealed class

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit // Generic navigation callback
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
                onClick = { onNavigate(Screen.PreGameSetup.route) },
                label = { Text("Start New Game") },
                icon = { Icon(Icons.Filled.PlayCircleOutline, contentDescription = "Start New Game") },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors()
            )
        }

        item {
            Chip(
                onClick = { onNavigate(Screen.GameSchedule.route) },
                label = { Text("Game Schedule") },
                icon = { Icon(Icons.Filled.CalendarToday, contentDescription = "Game Schedule") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Chip(
                onClick = { onNavigate(Screen.LoadIcs.route) },
                label = { Text("Load Games (ICS)") },
                icon = { Icon(Icons.Filled.FileUpload, contentDescription = "Load Games from ICS") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}