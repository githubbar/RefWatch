package com.databelay.refwatch.presentation.screens // << MAKE SURE THIS MATCHES YOUR PACKAGE

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.databelay.refwatch.data.GamePeriod
import com.databelay.refwatch.data.GameState
import com.databelay.refwatch.data.Team
import com.databelay.refwatch.presentation.components.ColorIndicator // Assuming ColorIndicator is also separate or in a common components package
import com.databelay.refwatch.presentation.components.ConfirmationDialog // Assuming ConfirmationDialog is also separate
import java.util.concurrent.TimeUnit

@Composable
fun GameScreen(
    gameState: GameState,
    onPauseResume: () -> Unit,
    onAddGoalHome: () -> Unit,
    onAddGoalAway: () -> Unit,
    onLogCard: () -> Unit,
    onViewLog: () -> Unit,
    onEndPeriod: () -> Unit,
    onResetGame: () -> Unit
) {
    val timeToDisplayMillis = gameState.remainingTimeInPeriodMillis
    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeToDisplayMillis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeToDisplayMillis) % 60

    val periodText = when (gameState.currentPeriod) {
        GamePeriod.FIRST_HALF -> "1st Half"
        GamePeriod.HALF_TIME -> "Halftime"
        GamePeriod.SECOND_HALF -> "2nd Half"
        GamePeriod.FULL_TIME -> "Full Time"
        GamePeriod.PRE_GAME -> "Pre Game"
        // Add other periods if you implement them
        else -> gameState.currentPeriod.name.replace("_", " ").capitalizeWords()
    }

    var showEndGameConfirmDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }


    Scaffold(
        timeText = { /* We are displaying the main game timer prominently */ },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround // Distributes space
        ) {
            // Score and Period
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                ColorIndicator(color = gameState.settings.homeTeamColor) // Make sure ColorIndicator is accessible
                Text(
                    "${gameState.homeScore} - ${gameState.awayScore}",
                    style = MaterialTheme.typography.display1,
                    fontWeight = FontWeight.Bold
                )
                ColorIndicator(color = gameState.settings.awayTeamColor) // Make sure ColorIndicator is accessible
            }
            Text(periodText, style = MaterialTheme.typography.title2, textAlign = TextAlign.Center)

            // Timer
            Text(
                String.format("%02d:%02d", minutes, seconds),
                style = MaterialTheme.typography.display3,
                fontWeight = FontWeight.Bold,
                fontSize = if (LocalConfiguration.current.isScreenRound) 50.sp else 55.sp, // Adjust for round/square
                textAlign = TextAlign.Center
            )

            // Action Buttons Column for better spacing control
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp) // Spacing between button rows
            ) {
                if (gameState.currentPeriod != GamePeriod.FULL_TIME && gameState.currentPeriod != GamePeriod.PRE_GAME) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onAddGoalHome,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(backgroundColor = gameState.settings.homeTeamColor.copy(alpha = 0.7f))
                        ) { Text("H Goal", color = if(gameState.settings.homeTeamColor.luminance() < 0.5f) Color.White else Color.Black) }
                        Button(
                            onClick = onAddGoalAway,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(backgroundColor = gameState.settings.awayTeamColor.copy(alpha = 0.7f))
                        ) { Text("A Goal", color = if(gameState.settings.awayTeamColor.luminance() < 0.5f) Color.White else Color.Black) }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onPauseResume,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (gameState.isTimerRunning) MaterialTheme.colors.surface else MaterialTheme.colors.primary
                            )
                        ) {
                            Icon(
                                if (gameState.isTimerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (gameState.isTimerRunning) "Pause" else "Resume"
                            )
                        }
                        Button(onClick = onLogCard, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Style, contentDescription = "Log Card") // Style icon for cards
                        }
                    }
                    Button(
                        onClick = onEndPeriod,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray)
                    ) {
                        Text(
                            when (gameState.currentPeriod) {
                                GamePeriod.FIRST_HALF -> "End 1st Half"
                                GamePeriod.HALF_TIME -> "Start 2nd Half" // Changed logic: End Halftime button should start next half
                                GamePeriod.SECOND_HALF -> "End 2nd Half"
                                else -> "End Period"
                            }
                        )
                    }
                } else if (gameState.currentPeriod == GamePeriod.FULL_TIME) {
                    Text("Game Over!", style = MaterialTheme.typography.title1)
                }
            }


            // Bottom Row for Log and Reset/End Game
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = onViewLog, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.ListAlt, contentDescription = "View Log")
                    Spacer(Modifier.width(4.dp))
                    Text("Log")
                }
                if(gameState.currentPeriod == GamePeriod.FULL_TIME || gameState.currentPeriod == GamePeriod.PRE_GAME) {
                    Button(
                        onClick = { showResetConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "New Game")
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                } else {
                    // End Game Button (when game is active) - This leads to reset
                    Button(
                        onClick = { showEndGameConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "End Game")
                        Spacer(Modifier.width(4.dp))
                        Text("End")
                    }
                }
            }
        }
    }

    if (showEndGameConfirmDialog) {
        ConfirmationDialog( // Make sure ConfirmationDialog is accessible
            message = "Are you sure you want to end the game and reset?",
            onConfirm = {
                showEndGameConfirmDialog = false
                onResetGame()
            },
            onDismiss = { showEndGameConfirmDialog = false }
        )
    }
    if (showResetConfirmDialog) {
        ConfirmationDialog( // Make sure ConfirmationDialog is accessible
            message = "Start a new game? Current data will be lost.",
            onConfirm = {
                showResetConfirmDialog = false
                onResetGame()
            },
            onDismiss = { showResetConfirmDialog = false }
        )
    }
}

// Helper extension function for capitalizing words (if not already available)
fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.lowercase().replaceFirstChar(Char::titlecase) }

// You might need to move ColorIndicator and ConfirmationDialog to a common 'components' package
// For example, in com.databelay.refwatch.presentation.components
/*
package com.databelay.refwatch.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*

@Composable
fun ColorIndicator(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .background(color, CircleShape)
            .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.5f), CircleShape)
    )
}

@Composable
fun ConfirmationDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog( // androidx.wear.compose.material.Dialog
        onDismissRequest = onDismiss,
        // properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true) // Not available in Wear Dialog
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colors.error,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}
*/