package com.databelay.refwatch.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Style // Icon for card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.databelay.refwatch.common.*

@Composable
fun HomeTeamActionScreen(
    gameState: GameState, // To get home team color
    onAddGoalHome: () -> Unit,
    onLogCardForHome: () -> Unit, // This will likely navigate to a generic LogCardScreen pre-filled for Home
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center // Or Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Home Team",
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = onAddGoalHome,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = gameState.settings.homeTeamColor.copy(alpha = 0.8f)
            )
        ) {
            Icon(Icons.Filled.SportsSoccer, contentDescription = "Home Goal")
            Spacer(Modifier.width(8.dp))
//            Text(
//                "Add Goal",
//                color = if (gameState.settings.homeTeamColor.luminance() < 0.5f) Color.White else Color.Black
//            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onLogCardForHome,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
            // You can use a neutral color or home team color for card button too
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Icon(Icons.Filled.Style, contentDescription = "Log Card for Home")
            Spacer(Modifier.width(8.dp))
//            Text("Log Card")
        }
    }
}