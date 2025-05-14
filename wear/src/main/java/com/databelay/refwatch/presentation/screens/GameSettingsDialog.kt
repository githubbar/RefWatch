package com.databelay.refwatch.presentation.screens // Or your chosen package

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.rememberScalingLazyListState
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.TimeText // Optional: if you want time in the dialog
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow

@Composable
fun GameSettingsDialog(
    onDismiss: () -> Unit,
    onViewLog: () -> Unit,
    onResetGame: () -> Unit,
    onPauseResume: () -> Unit, // New callback
    isTimerRunning: Boolean,  // Current timer state
    isGameActive: Boolean,
    isGameFinished: Boolean
) {
    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp, horizontal = 16.dp), // Adjusted padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            item {
                Text("Menu", style = MaterialTheme.typography.title3)
            }

            // Play/Pause Button - only if game is active (not PRE_GAME or FULL_TIME)
            if (isGameActive) {
                item {
                    Button(
                        onClick = onPauseResume,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isTimerRunning) MaterialTheme.colors.surface else MaterialTheme.colors.primary
                        )
                    ) {
                        Icon(
                            if (isTimerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isTimerRunning) "Pause Timer" else "Resume Timer"
                        )
//                        Spacer(Modifier.width(8.dp))
//                        Text(if (isTimerRunning) "Pause Timer" else "Resume Timer")
                    }
                }
            }


            item {
                Button(onClick = onViewLog,
                    modifier = Modifier.fillMaxWidth(),
                    ) {
                    Text("View Game Log")}
            }

            item { // Reset/End Game Button
                Button(onClick = onResetGame,
                    modifier = Modifier.fillMaxWidth(),
                    ) {
                    Text("Reset/End Game")}
            }

            item {
                Button(onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    ) {
                    Text("Close Menu")
                }
            }
        }
    }
}
