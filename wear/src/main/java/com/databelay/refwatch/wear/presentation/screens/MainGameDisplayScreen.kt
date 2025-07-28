package com.databelay.refwatch.presentation.screens.pager // Example package

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.formatTime
import com.databelay.refwatch.common.hasDuration
import com.databelay.refwatch.common.isPlayablePhase
import com.databelay.refwatch.common.readable
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.presentation.components.ColorIndicator

/**
 * Returns a Modifier for the kick-off border.
 * The border is a green circle shown around the team indicator of the team that has possession.
 * It is only shown during active play.
 * If the conditions are not met, an empty Modifier is returned.
 */

@Composable
fun MainGameDisplayScreen(
    game: Game,
    onToggleTimer: () -> Unit, // Renamed from onPlayPauseClick for clarity
    onEndPhaseEarly: () -> Unit, // For ending phase early, if needed
    onKickOff: () -> Unit, // New callback for kickoff button
    modifier: Modifier = Modifier // General modifier
) {
    val TAG = "MainGameDisplayScreen"
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
            val homeHasKickOff = game.currentPeriodKickOffTeam == Team.HOME &&
                    game.currentPhase.isPlayablePhase()

            ColorIndicator(
                color = game.homeTeamColor,
                hasKickOffBorder = homeHasKickOff,
                // You can also pass kickOffBorderWidth and kickOffBorderColor from here if they vary
            )
            Text(
                "${game.homeScore} - ${game.awayScore}",
                style = MaterialTheme.typography.display2, // Wear typography
                color = MaterialTheme.colors.onSurface,
                fontWeight = FontWeight.Bold
            )
            val awayHasKickOff = game.currentPeriodKickOffTeam == Team.AWAY &&
                    game.currentPhase.isPlayablePhase()
            ColorIndicator(
                color = game.awayTeamColor,
                hasKickOffBorder = awayHasKickOff
            )        }

        // Current Phase
        Text(
            game.currentPhase.readable(),
            style = MaterialTheme.typography.title2,
            color = MaterialTheme.colors.secondary,
            textAlign = TextAlign.Center
        )

        // Conditionally show Timer or Kickoff Button
        if (game.actualTimeElapsedInPeriodMillis == 0L && !game.isTimerRunning) {
            // Kickoff Button for the start of the first half
            Spacer(modifier = Modifier.height(8.dp)) // Add some spacing before the button
            Button(
                onClick = onKickOff, // Use the new callback
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
            ) {
                Text(
                    "Kick Off",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Main Timer Display (shown if not kickoff or timer is running)
            Text(
                text = game.displayedTimeMillis.formatTime(),
                style = MaterialTheme.typography.display1,
                fontSize = 56.sp, // Large timer
                color = MaterialTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // You might have a small text indicating "Long press for menu"
        Text("Long press for menu", style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center)
        // Placeholder for Play/Pause button for simplicity, will be part of the main display logic
        // This should be handled by the onToggleTimer passed to the screen
        // You can add a visible button if needed:
        // Button(onClick = onToggleTimer) { Text(if (game.isTimerRunning) "Pause" else "Play") }
    }
}
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