package com.databelay.refwatch.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.wear.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.isPlayablePhase

@Composable
fun AwayTeamActionScreen(
    game: Game,
    onAddGoal: () -> Unit,      // Action already knows it's for AWAY
    onLogCard: () -> Unit,      // Navigate to LogCardScreen
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Away: ${game.awayTeamName}", style = MaterialTheme.typography.title2)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAddGoal,
            modifier = Modifier.fillMaxWidth(),
            enabled = game.currentPhase.isPlayablePhase()
        ) {
            Text("Add Goal for Away")
        }
        Button(
            onClick = onLogCard,
            modifier = Modifier.fillMaxWidth(),
            enabled = game.currentPhase.isPlayablePhase()
        ) {
            Text("Log Card (Away)")
        }
    }
}