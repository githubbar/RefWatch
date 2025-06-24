package com.databelay.refwatch.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.databelay.refwatch.common.predefinedColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditGameScreen(
    navController: NavController, // To navigate back
    mobileGameViewModel: MobileGameViewModel, // To save the game
    // Pass gameToEdit if navigating for edit, null for new
    // This is tricky with Hilt ViewModel for AddEditGameScreen if gameToEdit is complex.
    // A better way for editing is to pass gameId and let AddEditGameViewModel load it.
    // For now, let's assume we pass the game object if editing for simplicity of example.
    // This means `AddEditGameViewModel.initializeForm` needs to be called from NavHost.
    addEditViewModel: AddEditGameViewModel = hiltViewModel()
) {
    val uiState by addEditViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Date Picker State
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = uiState.gameDateTimeEpochMillis ?: System.currentTimeMillis()
    )
    var showDatePicker by remember { mutableStateOf(false) }

    // Time Picker State
    val calendar = Calendar.getInstance()
    uiState.gameDateTimeEpochMillis?.let { calendar.timeInMillis = it }
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = true // Or based on locale
    )
    var showTimePicker by remember { mutableStateOf(false) }

    // Color Picker States
    var showHomeColorPicker by remember { mutableStateOf(false) }
    var showAwayColorPicker by remember { mutableStateOf(false) }
    var showKickOffPicker by remember { mutableStateOf(false) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit Game" else "Add New Game") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                addEditViewModel.onSaveGame { game ->
                    mobileGameViewModel.addOrUpdateGame(game) // Use the correct method name
                    navController.popBackStack()
                }
            }) {
                Icon(Icons.Filled.Save, contentDescription = "Save Game")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.homeTeamName,
                onValueChange = addEditViewModel::onHomeTeamNameChange,
                label = { Text("Home Team Name") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.awayTeamName,
                onValueChange = addEditViewModel::onAwayTeamNameChange,
                label = { Text("Away Team Name") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true
            )

            // Venue, Competition
            OutlinedTextField(
                value = uiState.venue,
                onValueChange = addEditViewModel::onVenueChange,
                label = { Text("Venue / Location (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.competition,
                onValueChange = { /* TODO: addEditViewModel::onCompetitionChange */ },
                label = { Text("Competition (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true
            )


            // Date Time Picker
            val sdfDate = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
            val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
            val selectedDateTimeString = uiState.gameDateTimeEpochMillis?.let {
                "${sdfDate.format(Date(it))} at ${sdfTime.format(Date(it))}"
            } ?: "Select Date & Time"

            OutlinedTextField(
                value = selectedDateTimeString,
                onValueChange = {},
                label = { Text("Game Date & Time") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                readOnly = true,
                trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "Select Date") }
            )

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showDatePicker = false
                            datePickerState.selectedDateMillis?.let { selectedDate ->
                                // Preserve time part if already set, otherwise default to noon or current time
                                val cal = Calendar.getInstance()
                                uiState.gameDateTimeEpochMillis?.let { cal.timeInMillis = it } // Keep existing time
                                cal.timeInMillis = selectedDate // Set the date part
                                // Now show time picker
                                timePickerState.hour = cal.get(Calendar.HOUR_OF_DAY)
                                timePickerState.minute = cal.get(Calendar.MINUTE)
                                showTimePicker = true
                            }
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showTimePicker) {
                TimePickerDialog( // You'll need to implement this or use a library
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showTimePicker = false
                            val cal = Calendar.getInstance()
                            // Start with the date picked (or current if somehow date wasn't set)
                            datePickerState.selectedDateMillis?.let { cal.timeInMillis = it }
                            cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            cal.set(Calendar.MINUTE, timePickerState.minute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            addEditViewModel.onGameDateTimeChange(cal.timeInMillis)
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
                ) {
                    TimePicker(state = timePickerState, modifier = Modifier.padding(16.dp))
                }
            }


            // Durations
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.halfDurationMinutes.toString(),
                    onValueChange = addEditViewModel::onHalfDurationChange,
                    label = { Text("Half (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = uiState.halftimeDurationMinutes.toString(),
                    onValueChange = addEditViewModel::onHalftimeDurationChange,
                    label = { Text("Halftime (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            // Colors & Kick-off
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { showHomeColorPicker = true }) {
                    Row {
                        Box(modifier = Modifier.size(20.dp).background(Color(uiState.homeTeamColorArgb)))
                        Spacer(Modifier.width(4.dp))
                        Text("Home Color")
                    }
                }
                Button(onClick = { showAwayColorPicker = true }) {
                    Row {
                        Box(modifier = Modifier.size(20.dp).background(Color(uiState.awayTeamColorArgb)))
                        Spacer(Modifier.width(4.dp))
                        Text("Away Color")
                    }
                }
            }
            Button(onClick = { showKickOffPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Kick-off: ${uiState.kickOffTeam.name}")
            }

            if (showHomeColorPicker) {
                ColorPickerDialog(
                    title = "Select Home Color",
                    availableColors = predefinedColors, // Pass your list of colors
                    selectedColor = Color(uiState.homeTeamColorArgb), // Convert ARGB to Color
                    onColorSelected = { color ->
                        addEditViewModel.onHomeColorSelected(color)
                        showHomeColorPicker = false
                    },
                    onDismiss = { showHomeColorPicker = false }
                )
            }
            if (showAwayColorPicker) {
                ColorPickerDialog(
                    title = "Select Away Color",
                    availableColors = predefinedColors,
                    selectedColor = Color(uiState.awayTeamColorArgb),
                    onColorSelected = { color ->
                        addEditViewModel.onAwayColorSelected(color)
                        showAwayColorPicker = false
                    },
                    onDismiss = { showAwayColorPicker = false }
                )
            }
            if (showKickOffPicker) {
                TeamPickerDialog( // You need to create this dialog
                    title = "Select Kick-off Team",
                    currentSelection = uiState.kickOffTeam,
                    onTeamSelected = { team ->
                        addEditViewModel.onKickOffTeamSelected(team)
                        showKickOffPicker = false
                    },
                    onDismiss = { showKickOffPicker = false }
                )
            }


            OutlinedTextField(
                value = uiState.notes,
                onValueChange = addEditViewModel::onNotesChanged,
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(60.dp)) // Space for FAB
        }
    }
}

// You will need to implement TimePickerDialog, ColorPickerDialog, and TeamPickerDialog
// For TimePickerDialog, Material 3 provides TimePicker, you just need to wrap it in a Dialog.
// For ColorPickerDialog and TeamPickerDialog, you can create custom dialogs.

@Composable
fun TimePickerDialog( // Basic example
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        text = { content() } // TimePicker often goes in text or a custom layout
    )
}