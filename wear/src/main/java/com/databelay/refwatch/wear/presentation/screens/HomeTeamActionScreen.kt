package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.wear.compose.material.* // Use Wear Material
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.isPlayablePhase


@Composable
fun HomeTeamActionScreen(
    game: Game,
    onAddGoal: () -> Unit,      // Simplified: action already knows it's for HOME
    onLogCard: () -> Unit,      // Navigate to LogCardScreen
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Home: ${game.homeTeamName}", style = MaterialTheme.typography.title2)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAddGoal,
            modifier = Modifier.fillMaxWidth(),
            enabled = game.currentPhase.isPlayablePhase()
        ) {
            Text("Add Goal for Home")
        }
        Button(
            onClick = onLogCard,
            modifier = Modifier.fillMaxWidth(),
            enabled = game.currentPhase.isPlayablePhase()
        ) {
            Text("Log Card (Home)") // Text implies team, LogCardScreen handles specifics
        }
    }
}