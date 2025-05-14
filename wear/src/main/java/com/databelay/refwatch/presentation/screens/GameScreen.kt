package com.databelay.refwatch.presentation.screens

// ... (Keep necessary imports for Text, Row, Column, ColorIndicator, GameState, TimeUnit, etc.)
// ... (Remove imports for Button, specific Icons for actions if they are no longer here)
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons // Keep if needed for other things
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.databelay.refwatch.common.*
import com.databelay.refwatch.presentation.components.ColorIndicator
import java.util.concurrent.TimeUnit

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
            Text(
                "${gameState.homeScore} - ${gameState.awayScore}",
                style = MaterialTheme.typography.display1,
                fontWeight = FontWeight.Bold
            )
            ColorIndicator(color = gameState.settings.awayTeamColor)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            periodText,
            style = MaterialTheme.typography.body1, // Made period text a bit larger
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp)) // More space before timer

        // Timer
        Text(
            String.format("%02d:%02d", minutes, seconds),
            style = MaterialTheme.typography.display2, // Made timer slightly larger too
            fontWeight = FontWeight.Bold,
            fontSize = if (LocalConfiguration.current.isScreenRound) 60.sp else 65.sp,
            textAlign = TextAlign.Center
        )

        // Game Over message if applicable
        if (gameState.currentPhase == GamePhase.FULL_TIME) {
            Spacer(Modifier.height(16.dp))
            Text("Game Over!", style = MaterialTheme.typography.title1)
        }
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