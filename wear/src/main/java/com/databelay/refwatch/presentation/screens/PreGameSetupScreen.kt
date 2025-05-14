package com.databelay.refwatch.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.wear.compose.material.dialog.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
//import androidx.compose.foundation.lazy.LazyColumn
import com.databelay.refwatch.GameViewModel
import com.databelay.refwatch.common.theme.*

@Composable
fun PreGameSetupScreen(
    viewModel: GameViewModel,
    onStartGameConfirmed: () -> Unit
) {
    val gameState by viewModel.gameState.collectAsState()
    var showHomeColorPicker by remember { mutableStateOf(false) }
    var showAwayColorPicker by remember { mutableStateOf(false) }

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

        // Jersey Colors
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp) // Added a bit more vertical padding
            ) {
                ColorPickerButton("Home", gameState.settings.homeTeamColor) { showHomeColorPicker = true }
                ColorPickerButton("Away", gameState.settings.awayTeamColor) { showAwayColorPicker = true } // Assuming gameState.settings.awayColor
            }
        }

        // Half Duration
        item {
            DurationSettingStepper(
                label = "Half Duration",
                currentValue = gameState.settings.halfDurationMinutes,
                onValueChange = { viewModel.setHalfDuration(it) },
                valueRange = 15..60
            )
        }

        // Halftime Duration
        item {
            DurationSettingStepper(
                label = "Halftime Duration",
                currentValue = gameState.settings.halftimeDurationMinutes,
                onValueChange = { viewModel.setHalftimeDuration(it) },
                valueRange = 5..30
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) } // Increased spacer before button

        // Start Game Button
        item {
            Button(
                onClick = onStartGameConfirmed,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp), // Made it a bit taller
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.error
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = "Kickoff")
                    Spacer(Modifier.width(8.dp)) // Reduced spacer for better balance
                    Text("Kickoff")
                }
            }
        }
        item { Spacer(modifier = Modifier.height(10.dp)) } // Bottom padding
    }

    // Color Picker Dialogs
    if (showHomeColorPicker) {
        SimpleColorPickerDialog(
            title = "Home Color",
            availableColors = PredefinedJerseyColors,
            onColorSelected = { color ->
                viewModel.updateHomeTeamColor(color)
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
                viewModel.updateAwayTeamColor(color) // Ensure you have this method in ViewModel
                showAwayColorPicker = false
            },
            onDismiss = { showAwayColorPicker = false }
        )
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