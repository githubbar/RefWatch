package com.databelay.refwatch.presentation.screens // Or your package

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.wear.compose.material.ButtonDefaults
import com.databelay.refwatch.GameViewModel // Your ViewModel
import com.databelay.refwatch.data.Team // Your Team enum


@Composable
fun KickOffSelectionScreen(
    viewModel: GameViewModel,
    onConfirm: () -> Unit // Callback to navigate away (e.g., back or to next step)
) {
    val gameState by viewModel.gameState.collectAsState()
    val selectedTeam = gameState.settings.kickOffTeam // Get current selection from ViewModel

    // Use ScalingLazyColumn for standard Wear OS screen structure
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp), // Adjust padding as needed
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically) // Center content
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Kickoff",
                    style = MaterialTheme.typography.caption1, // Same style as DurationSettingStepper label
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp) // Similar to DurationSettingStepper
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Chip(
                        onClick = { viewModel.setKickOffTeam(Team.HOME) },
                        label = { Text("Home", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = if (gameState.settings.kickOffTeam == Team.HOME) MaterialTheme.colors.primary else MaterialTheme.colors.surface
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 1.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Chip(
                        onClick = { viewModel.setKickOffTeam(Team.AWAY) },
                        label = { Text("Away", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, // Added fillMaxWidth for consistency
                        colors = ChipDefaults.chipColors(
                            backgroundColor = if (gameState.settings.kickOffTeam == Team.AWAY) MaterialTheme.colors.primary else MaterialTheme.colors.surface
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) } // Spacer before confirm button

        item {
            Button(
                onClick = {
                    viewModel.confirmSettingsAndStartGame() // Prepare game state and timer internally
                    onConfirm() // This lambda will navigate to GameScreen
                },
                modifier = Modifier.fillMaxWidth(0.8f), // Button takes 80% of width
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.error
                )
            ) {
                Text("Start")
            }
        }
    }
}