package com.databelay.refwatch.wear.presentation.screens // Or your chosen package

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.dialog.Dialog
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.hasDuration
import com.databelay.refwatch.common.readable

@Composable
fun GameSettingsDialog(
    game: Game,
    onDismiss: () -> Unit,
    onFinishGame: () -> Unit,
    onResetGame: () -> Unit,
    onViewLog: () -> Unit,
    onToggleTimer: () -> Unit,
    onEndPhase: () -> Unit,
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
                if (game.currentPhase.hasDuration() && game.currentPhase != GamePhase.GAME_ENDED) {
                    item {
                        // Start/Pause Button
                        Button(
                            onClick = onToggleTimer,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color.Green
                            ),
                            modifier = Modifier.size(ButtonDefaults.LargeButtonSize)
                        ) {
                            Icon(
                                imageVector = if (game.isTimerRunning) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                                contentDescription = if (game.isTimerRunning) "Pause Timer" else "Start Timer",
                                modifier = Modifier.size(ButtonDefaults.LargeIconSize)
                            )
                        }
                    }
                }
                // End Phase Early Button
                if (game.currentPhase.hasDuration() && game.currentPhase != GamePhase.GAME_ENDED && game.currentPhase != GamePhase.PRE_GAME) {
                    item {
                        Button(
                            onClick = onEndPhase,
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red), // Or a distinct color
                            modifier = Modifier.fillMaxWidth(),
                            ) {
                            Text(
                                text = "End ${game.currentPhase.readable()}",
                                textAlign = TextAlign.Center
                            ) // Adding text
                        }
                    }
                }
            }
            item { // Finish Game Button
                Button(onClick = onFinishGame,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Finish Game")}
            }

            item { // Reset/End Game Button
                Button(onClick = onResetGame,
                    modifier = Modifier.fillMaxWidth(),
                    ) {
                    Text("Reset Game")}
            }

            item {
                Button(onClick = onViewLog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View Game Log")}
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
