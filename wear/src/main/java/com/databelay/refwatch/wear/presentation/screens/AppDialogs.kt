package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialogContent
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.databelay.refwatch.common.Team

// Dialog bodies shown from Navigation.kt. They are kept apart from their Dialog windows so
// the font-scale screenshot audit can render them; a Dialog window is not captured there.

private val DialogIconSize = 32.dp

@Composable
fun PermissionRequiredDialogContent(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialogContent(
        icon = {
            Icon(
                imageVector = Icons.Default.Report,
                contentDescription = null,
                modifier = Modifier.size(DialogIconSize)
            )
        },
        title = { Text("Permissions needed") },
        text = {
            Text(
                "Body sensors, location and notifications let RefWatch track the " +
                    "match and keep the timer running. Enable them in Settings."
            )
        },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Open settings")
            }
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(onClick = onDismiss)
        },
    )
}

/**
 * Tells the referee a second yellow was converted to a red.
 *
 * This used to be a ConfirmationDialog, whose text is capped at three non-scrolling lines --
 * the sentence was ellipsised at large font sizes. An AlertDialog scrolls instead.
 */
@Composable
fun SecondYellowDialogContent(team: Team, playerNumber: Int?, onDismiss: () -> Unit) {
    val teamLabel = if (team == Team.HOME) "Home" else "Away"
    val player = playerNumber?.let { "#$it" } ?: "Player"
    AlertDialogContent(
        icon = {
            Icon(
                imageVector = Icons.Filled.Report,
                contentDescription = null,
                modifier = Modifier.size(DialogIconSize)
            )
        },
        title = { Text("Red card") },
        text = { Text("$player ($teamLabel) received a second yellow card.") },
        edgeButton = {
            AlertDialogDefaults.EdgeButton(onClick = onDismiss)
        },
    )
}
