package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.TextField
import androidx.wear.compose.material.dialog.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import com.databelay.refwatch.wear.WearGameViewModel
import com.databelay.refwatch.common.theme.*

@Composable
fun PreGameSetupScreen(
    gameViewModel: WearGameViewModel,
    onCreateMatch: () -> Unit,
) {
    val activeGame by gameViewModel.activeGame.collectAsState()

    var showHomeColorPicker by remember { mutableStateOf(false) }
    var showAwayColorPicker by remember { mutableStateOf(false) }
    // State for managing which team name is being edited
    var teamNameToEdit by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp) // Increased default spacing a bit
    ) {
        item {
            Text(
                "Match Setup",
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp) // Added more padding
            )
            // Removed Spacer here as padding on Text is used
        }

        // Team Name Editors
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                // Home Team Name Chip
                OutlinedChip(
                    onClick = { teamNameToEdit = "Home" to gameViewModel::updateHomeTeamName },
                    label = { Text(activeGame.homeTeamName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Edit Home Team Name") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Away Team Name Chip
                OutlinedChip(
                    onClick = { teamNameToEdit = "Away" to gameViewModel::updateAwayTeamName },
                    label = { Text(activeGame.awayTeamName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Edit Away Team Name") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Jersey Colors
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                ColorPickerButton("Home", activeGame.homeTeamColor) { showHomeColorPicker = true }
                ColorPickerButton("Away", activeGame.awayTeamColor) { showAwayColorPicker = true }
            }
        }

        // Half Duration
        item {
            DurationSettingStepper(
                label = "Half Duration",
                currentValue = activeGame.halfDurationMinutes,
                onValueChange = { gameViewModel.setHalfDuration(it) },
                valueRange = 15..60
            )
        }

        // Halftime Duration
        item {
            DurationSettingStepper(
                label = "Halftime Duration",
                currentValue = activeGame.halftimeDurationMinutes,
                onValueChange = { gameViewModel.setHalftimeDuration(it) },
                valueRange = 5..30
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) } // Increased spacer before button

        // Create Match Button
        item {
            Button(
                onClick = onCreateMatch,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp), // Made it a bit taller
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.error
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = "Create Match")
                    Spacer(Modifier.width(8.dp)) // Reduced spacer for better balance
                    Text("Create")
                }
            }
        }
        item { Spacer(modifier = Modifier.height(10.dp)) } // Bottom padding
    }


    // --- DIALOGS ---

    // Team Name Edit Dialog
    teamNameToEdit?.let { (teamLabel, onSave) ->
        TeamNameEditDialog(
            teamLabel = teamLabel,
            initialValue = if (teamLabel == "Home") activeGame.homeTeamName else activeGame.awayTeamName,
            onSave = { newName ->
                onSave(newName)
                teamNameToEdit = null // Close dialog
            },
            onDismiss = { teamNameToEdit = null } // Close dialog
        )
    }

    // Color Picker Dialogs
    if (showHomeColorPicker) {
        SimpleColorPickerDialog(
            title = "Home Color",
            availableColors = PredefinedJerseyColors,
            onColorSelected = { color ->
                gameViewModel.updateHomeTeamColor(color)
                showHomeColorPicker = false
            },
            onDismiss = { showHomeColorPicker = false }
        )
    }

    if (showAwayColorPicker) {
        SimpleColorPickerDialog(
            title = "Away Color",
            availableColors = PredefinedJerseyColors,
            onColorSelected = { color ->
                gameViewModel.updateAwayTeamColor(color) // Ensure you have this method in ViewModel
                showAwayColorPicker = false
            },
            onDismiss = { showAwayColorPicker = false }
        )
    }
}


/**
 * A new dialog Composable for editing a team's name.
 */
@Composable
fun TeamNameEditDialog(
    teamLabel: String,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit $teamLabel Team Name", style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)

            // Text field for input. This will bring up the keyboard on a real device.
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Team Name") }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = onDismiss, colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(text) },
                    enabled = text.isNotBlank() // Save button is disabled if the name is empty
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
fun ColorPickerButton(label: String, currentColor: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Text(label, style = MaterialTheme.typography.button)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(currentColor)
                .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.7f), CircleShape)
        )
    }
}

@Composable
fun SimpleColorPickerDialog(
    title: String,
    availableColors: List<Color>,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        true,
        onDismissRequest = onDismiss,
        // No properties needed, swipe to dismiss is default for Wear OS Dialog
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize() // Dialogs often fill screen on Wear
                .background(MaterialTheme.colors.surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            // Use a scrollable column if many colors, or flow layout if supported/needed
            ScalingLazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f) // Takes available space
            ){
                items(availableColors.chunked(3)) { rowColors -> // Simple grid layout
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        rowColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.5f), CircleShape)
                                    .clickable { onColorSelected(color) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(0.7f)) {
                Text("Cancel")
            }
        }
    }
}

@Composable
fun DurationSettingStepper(
    label: String,
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange = 1..60, // Default range
    step: Int = 5 // Default step
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.caption1)
        Spacer(Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            CompactButton(
                onClick = { if (currentValue - step >= valueRange.first) onValueChange(currentValue - step) },
                modifier = Modifier.size(40.dp)
            ) { Text("-", fontSize = 18.sp) }

            Text(
                text = "$currentValue min",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(horizontal = 12.dp).defaultMinSize(minWidth = 60.dp),
                textAlign = TextAlign.Center
            )
            CompactButton(
                onClick = { if (currentValue + step <= valueRange.last) onValueChange(currentValue + step) },
                modifier = Modifier.size(40.dp)
            ) { Text("+", fontSize = 18.sp) }
        }
    }
}