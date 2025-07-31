package com.databelay.refwatch.wear.presentation.screens // Or your package

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import com.databelay.refwatch.wear.WearGameViewModel // Your ViewModel
import com.databelay.refwatch.common.*

@Composable
fun KickOffSelectionScreen(
    gameViewModel: WearGameViewModel,
    onConfirm: () -> Unit // Callback to navigate away (e.g., back or to next step)
) {
    val activeGame by gameViewModel.activeGame.collectAsState()
    val selectedTeam = activeGame.kickOffTeam // Get current selection from ViewModel
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
                    "Select Kick-Off Team",
                    style = MaterialTheme.typography.caption1, // Same style as DurationSettingStepper label
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp) // Similar to DurationSettingStepper
                )

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Chip(
                        onClick = { gameViewModel.setKickOffTeam(Team.HOME) },
                        label = { Text("Home", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = if (activeGame.kickOffTeam == Team.HOME) MaterialTheme.colors.primary else MaterialTheme.colors.surface
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 1.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Chip(
                        onClick = { gameViewModel.setKickOffTeam(Team.AWAY) },
                        label = { Text("Away", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }, // Added fillMaxWidth for consistency
                        colors = ChipDefaults.chipColors(
                            backgroundColor = if (activeGame.kickOffTeam == Team.AWAY) MaterialTheme.colors.primary else MaterialTheme.colors.surface
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) } // Spacer before confirm button

        item {
            Button(
                onClick = {
                    onConfirm() // This lambda will navigate to GameScreen
                },
                modifier = Modifier.fillMaxWidth(0.8f), // Button takes 80% of width
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.error
                )
            ) {
                Text("Select")
            }
        }
    }
}