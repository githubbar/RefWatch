package com.databelay.refwatch.presentation.screens.pager // Example package

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.StopCircle // For ending phase
import androidx.compose.material3.Text // Use Material 3 Text
import androidx.wear.compose.material.* // Use Wear Material
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.formatTime
import com.databelay.refwatch.common.hasDuration
import com.databelay.refwatch.common.readable
import com.databelay.refwatch.wear.presentation.components.ColorIndicator

@Composable
fun MainGameDisplayScreen(
    game: Game,
    onToggleTimer: () -> Unit,
    onEndPhaseEarly: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround // Distribute elements
    ) {
        // Score and Team Colors
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            ColorIndicator(color = game.homeTeamColor)
            Box(modifier = Modifier.size(16.dp).background(Color(game.homeTeamColorArgb)))
            Text(
                "${game.homeScore} - ${game.awayScore}",
                style = MaterialTheme.typography.display2, // Wear typography
                fontWeight = FontWeight.Bold
            )
            Box(modifier = Modifier.size(16.dp).background(Color(game.awayTeamColorArgb)))
            ColorIndicator(color = game.awayTeamColor)
        }

        // Current Phase
        Text(
            game.currentPhase.readable(),
            style = MaterialTheme.typography.title2,
            textAlign = TextAlign.Center
        )

        // Main Timer Display
        Text(
            text = game.displayedTimeMillis.formatTime(),
            style = MaterialTheme.typography.display1.copy(fontSize = 56.sp), // Large timer
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Action Buttons for this page
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Start/Pause Button
            if (game.currentPhase.hasDuration() && game.currentPhase != GamePhase.FULL_TIME) {
                Button(
                    onClick = onToggleTimer,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (game.isTimerRunning) MaterialTheme.colors.surface else MaterialTheme.colors.primary
                    ),
                    modifier = Modifier.size(ButtonDefaults.LargeButtonSize)
                ) {
                    Icon(
                        imageVector = if (game.isTimerRunning) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                        contentDescription = if (game.isTimerRunning) "Pause Timer" else "Start Timer",
                        modifier = Modifier.size(ButtonDefaults.LargeIconSize)
                    )
                }
            } else {
                // Placeholder or disabled button if timer cannot be run
                Spacer(modifier = Modifier.size(ButtonDefaults.LargeButtonSize))
            }

            // End Phase Early Button
            if (game.currentPhase.hasDuration() && game.currentPhase != GamePhase.FULL_TIME && game.currentPhase != GamePhase.PRE_GAME) {
                Button(
                    onClick = onEndPhaseEarly,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray), // Or a distinct color
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.StopCircle, // Example icon
                        contentDescription = "End Phase Early",
                        modifier = Modifier.size(ButtonDefaults.DefaultIconSize)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(ButtonDefaults.DefaultButtonSize))
            }
        }
        // You might have a small text indicating "Long press for menu"
        Text("Long press for menu", style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center)
    }
}
/*

// Note: The `onPauseResume`, `onAddGoalHome`, `onAddGoalAway`, `onLogCard` callbacks
// are no longer directly used by THIS Composable. They will be used by the Pager wrapper.
@Composable
fun SimplifiedGameScreen(
    gameState: GameState,
    modifier: Modifier = Modifier // Modifier passed from Pager
) {
    val timeToDisplayMillis = gameState.displayedTimeMillis
    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeToDisplayMillis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeToDisplayMillis) % 60

    val periodText = when (gameState.currentPhase) {
        GamePhase.FIRST_HALF -> "1st Half"
        GamePhase.HALF_TIME -> "Halftime"
        GamePhase.SECOND_HALF -> "2nd Half"
        GamePhase.FULL_TIME -> "Full Time"
        GamePhase.PRE_GAME -> "Pre Game"
        else -> gameState.currentPhase.name.replace("_", " ").capitalizeWords()
    }

    Column(
        modifier = modifier // Use the modifier from the Pager
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center // Center the content vertically now
    ) {
        Spacer(Modifier.height(16.dp)) // Space after timer

        // Score and Period
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            ColorIndicator(color = gameState.settings.homeTeamColor)
            androidx.wear.compose.material.Text(
                "${gameState.homeScore} - ${gameState.awayScore}",
                style = MaterialTheme.typography.display1,
                fontWeight = FontWeight.Bold
            )
            ColorIndicator(color = gameState.settings.awayTeamColor)
        }
        Spacer(Modifier.height(8.dp))
        androidx.wear.compose.material.Text(
            periodText,
            style = MaterialTheme.typography.body1, // Made period text a bit larger
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp)) // More space before timer

        // Timer
        androidx.wear.compose.material.Text(
            String.format("%02d:%02d", minutes, seconds),
            style = MaterialTheme.typography.display2, // Made timer slightly larger too
            fontWeight = FontWeight.Bold,
            fontSize = if (LocalConfiguration.current.isScreenRound) 60.sp else 65.sp,
            textAlign = TextAlign.Center
        )

        // Game Over message if applicable
        if (gameState.currentPhase == GamePhase.FULL_TIME) {
            Spacer(Modifier.height(16.dp))
            androidx.wear.compose.material.Text(
                "Game Over!",
                style = MaterialTheme.typography.title1
            )
        }
    }
}
*/

// Helper extension function for capitalizing words (if not already available)
//fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.lowercase().replaceFirstChar(Char::titlecase) }

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