package com.databelay.refwatch.presentation.screens.pager // Example package

import android.util.Log
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
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.presentation.components.ColorIndicator

@Composable
fun MainGameDisplayScreen(
    game: Game,
    onToggleTimer: () -> Unit,
    onEndPhaseEarly: () -> Unit,
    modifier: Modifier = Modifier
) {
    val TAG = "MainGameDisplay"

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
            Text(
                "${game.homeScore} - ${game.awayScore}",
                style = MaterialTheme.typography.display2, // Wear typography
                color = MaterialTheme.colors.onSurface,
                fontWeight = FontWeight.Bold
            )
            ColorIndicator(color = game.awayTeamColor)
        }

        // Current Phase
        Text(
            game.currentPhase.readable(),
            style = MaterialTheme.typography.title2,
            color = MaterialTheme.colors.secondary,
            textAlign = TextAlign.Center
        )
        // Main Timer Display
        Text(
            text = game.displayedTimeMillis.formatTime(),
            style = MaterialTheme.typography.display1,
            fontSize = 56.sp, // Large timer
            color = MaterialTheme.colors.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        // You might have a small text indicating "Long press for menu"
        Text("Long press for menu", style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center)
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