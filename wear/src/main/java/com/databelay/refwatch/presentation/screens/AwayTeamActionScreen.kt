package com.databelay.refwatch.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.databelay.refwatch.common.*

@Composable
fun AwayTeamActionScreen(
    gameState: GameState, // To get away team color
    onAddGoalAway: () -> Unit,
    onLogCardForAway: () -> Unit, // This will likely navigate to LogCardScreen pre-filled for Away
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Away Team",
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = onAddGoalAway,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = gameState.settings.awayTeamColor.copy(alpha = 0.8f)
            )
        ) {
            Icon(Icons.Filled.SportsSoccer, contentDescription = "Away Goal")
            Spacer(Modifier.width(8.dp))
//            Text(
//                "Add Goal",
//                color = if (gameState.settings.awayTeamColor.luminance() < 0.5f) Color.White else Color.Black
//            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onLogCardForAway,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Icon(Icons.Filled.Style, contentDescription = "Log Card for Away")
            Spacer(Modifier.width(8.dp))
//            Text("Log Card")
        }
    }
}