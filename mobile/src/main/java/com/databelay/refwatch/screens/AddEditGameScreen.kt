package com.databelay.refwatch.screens

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.databelay.refwatch.common.predefinedColors
import com.databelay.refwatch.common.theme.RefWatchMobileTheme
import com.databelay.refwatch.data.AddEditGameUiState
import com.databelay.refwatch.data.AddEditGameViewModel
import com.databelay.refwatch.data.ColorPickerDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


// --- Stateless Composable ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditGameScreen(
    uiState: AddEditGameUiState,
    onNavigateBack: () -> Unit,
    onHomeTeamNameChange: (String) -> Unit,
    onAwayTeamNameChange: (String) -> Unit,
    onFieldNumberChange: (String) -> Unit,
    onRefereeAssignmentChange: (String) -> Unit,
    onVenueChange: (String) -> Unit,
    onCompetitionChange: (String) -> Unit,
    onGameDateTimeChange: (Long) -> Unit,
    onHalfDurationChange: (String) -> Unit,
    onHalftimeDurationChange: (String) -> Unit,
    onHomeColorSelected: (Color) -> Unit,
    onAwayColorSelected: (Color) -> Unit,
    onNotesChanged: (String) -> Unit,
    onHomeScoreChange: (String) -> Unit, // New callback
    onAwayScoreChange: (String) -> Unit, // New callback
    onSaveGame: () -> Unit,
) {
    val scrollState = rememberScrollState()

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = uiState.gameDateTimeEpochMillis ?: System.currentTimeMillis()
    )

    val calendar = Calendar.getInstance()
    uiState.gameDateTimeEpochMillis?.let { calendar.timeInMillis = it }
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = true
    )
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showHomeColorPicker by remember { mutableStateOf(false) }
    var showAwayColorPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit Game" else "Add New Game") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onSaveGame) {
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
                onValueChange = onHomeTeamNameChange,
                label = { Text("Home Team Name") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.awayTeamName,
                onValueChange = onAwayTeamNameChange,
                label = { Text("Away Team Name") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true
            )

            // Scores Row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.homeScore,
                    onValueChange = onHomeScoreChange,
                    label = { Text("Home Score") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.awayScore,
                    onValueChange = onAwayScoreChange,
                    label = { Text("Away Score") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            OutlinedTextField(
                value = uiState.refereeAssignment,
                onValueChange = onRefereeAssignmentChange,
                label = { Text("Assignment") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true
            )
            // Location fields. These carry the longest labels on the form, so they are
            // stacked full width rather than shared across a row -- at large font scales
            // a half-width label is truncated.
            OutlinedTextField(
                value = uiState.venue,
                onValueChange = onVenueChange,
                label = { Text("Venue / Location (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.fieldNumber,
                onValueChange = onFieldNumberChange,
                label = { Text("Field Number (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.competition,
                onValueChange = onCompetitionChange,
                label = { Text("Competition (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true
            )


            val sdfDate = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
            val sdfTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val selectedDateTimeString = uiState.gameDateTimeEpochMillis?.let {
                "${sdfDate.format(Date(it))} at ${sdfTime.format(Date(it))}"
            } ?: "Select Date & Time"

            // Wrap the TextField in a Box to intercept the click
            Box {
                OutlinedTextField(
                    value = selectedDateTimeString,        onValueChange = {},
                    label = { Text("Game Date & Time") },
                    modifier = Modifier.fillMaxWidth(),
                    // Set enabled to false to prevent focus and cursor
                    enabled = false,
                    // Change colors to make it look enabled
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "Select Date") }
                )
                // Apply the clickable modifier to a covering Box
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker = true }
                )
            }
            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showDatePicker = false
                            datePickerState.selectedDateMillis?.let { selectedDate ->
                                val cal = Calendar.getInstance()
                                uiState.gameDateTimeEpochMillis?.let { cal.timeInMillis = it }
                                cal.timeInMillis = selectedDate
                                timePickerState.hour = cal.get(Calendar.HOUR_OF_DAY)
                                timePickerState.minute = cal.get(Calendar.MINUTE)
                                showTimePicker = true
                            }
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showDatePicker = false
                        }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showTimePicker) {
                TimePickerDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showTimePicker = false
                            val cal = Calendar.getInstance()
                            datePickerState.selectedDateMillis?.let { cal.timeInMillis = it }
                            cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            cal.set(Calendar.MINUTE, timePickerState.minute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            onGameDateTimeChange(cal.timeInMillis)
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showTimePicker = false
                        }) { Text("Cancel") }
                    }
                ) {
                    TimePicker(state = timePickerState, modifier = Modifier.padding(16.dp))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.halfDurationMinutes.toString(),
                    onValueChange = onHalfDurationChange,
                    label = { Text("Half (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = uiState.halftimeDurationMinutes.toString(),
                    onValueChange = onHalftimeDurationChange,
                    label = { Text("Halftime (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { showHomeColorPicker = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(uiState.homeTeamColorArgb))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Home Color")
                    }
                }
                Button(onClick = { showAwayColorPicker = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(uiState.awayTeamColorArgb))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Away Color")
                    }
                }
            }

            if (showHomeColorPicker) {
                ColorPickerDialog(
                    title = "Select Home Color",
                    availableColors = predefinedColors,
                    selectedColor = Color(uiState.homeTeamColorArgb),
                    onColorSelected = { color: Color ->
                        onHomeColorSelected(color)
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
                    onColorSelected = { color: Color ->
                        onAwayColorSelected(color)
                        showAwayColorPicker = false
                    },
                    onDismiss = { showAwayColorPicker = false }
                )
            }

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChanged,
                label = { Text("Notes (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            uiState.errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(60.dp)) // Space for FAB
        }
    }
}


// --- ViewModel-Driven Route Composable ---
@Composable
fun AddEditGameRoute(
    navController: NavController,
    addEditViewModel: AddEditGameViewModel = hiltViewModel(),
) {
    val tag = "AddEditGameRoute"
    val uiState by addEditViewModel.uiState.collectAsStateWithLifecycle()

    val gameId = navController.currentBackStackEntry?.arguments?.getString("gameId")

    LaunchedEffect(gameId) {
        Log.d(
            tag,
            "LaunchedEffect triggered. gameIdFromNav: $gameId, Current uiState.gameId: ${uiState.gameId}, isEditing: ${uiState.isEditing}"
        )
        if (gameId != null && !uiState.isEditing) {
            addEditViewModel.initializeForm(gameId)
        } else if (gameId == null) {
            addEditViewModel.initializeForm(null) // New game
        }
    }

    AddEditGameScreen(
        uiState = uiState,
        onNavigateBack = { navController.popBackStack() },
        onHomeTeamNameChange = addEditViewModel::onHomeTeamNameChange,
        onAwayTeamNameChange = addEditViewModel::onAwayTeamNameChange,
        onFieldNumberChange = addEditViewModel::onFieldNumberChange,
        onRefereeAssignmentChange = addEditViewModel::onRefereeAssignmentChange,
        onVenueChange = addEditViewModel::onVenueChange,
        onCompetitionChange = addEditViewModel::onCompetitionChange,
        onGameDateTimeChange = addEditViewModel::onGameDateTimeChange,
        onHalfDurationChange = addEditViewModel::onHalfDurationChange,
        onHalftimeDurationChange = addEditViewModel::onHalftimeDurationChange,
        onHomeColorSelected = addEditViewModel::onHomeColorSelected,
        onAwayColorSelected = addEditViewModel::onAwayColorSelected,
        onNotesChanged = addEditViewModel::onNotesChanged,
        onHomeScoreChange = addEditViewModel::onHomeScoreChange, // Pass new handler
        onAwayScoreChange = addEditViewModel::onAwayScoreChange, // Pass new handler
        onSaveGame = {
            addEditViewModel.onSaveGame()
            navController.popBackStack()

        }
    )
}

// --- Previews ---

@Preview(
    name = "Add New Game (Dark)",
    device = "id:7in WSVGA (Tablet)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(
    name = "Add New Game (Dark)",
    device = "id:10.1in WXGA (Tablet)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(
    name = "Add New Game (Dark)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun AddEditGameScreen_AddNewPreview() {
    RefWatchMobileTheme {
        AddEditGameScreen(
            uiState = AddEditGameUiState(
                isEditing = false,
                fieldNumber = "",
                homeScore = "0",
                awayScore = "0",
                refereeAssignment = ""
            ),
            onNavigateBack = {},
            onHomeTeamNameChange = {},
            onAwayTeamNameChange = {},
            onFieldNumberChange = {},
            onVenueChange = {},
            onCompetitionChange = {},
            onGameDateTimeChange = {},
            onHalfDurationChange = {},
            onHalftimeDurationChange = {},
            onHomeColorSelected = {},
            onAwayColorSelected = {},
            onNotesChanged = {},
            onHomeScoreChange = {}, // Add dummy handler
            onAwayScoreChange = {}, // Add dummy handler
            onRefereeAssignmentChange = {}, // Add dummy handler
            onSaveGame = {}
        )
    }
}


@Preview(name = "Edit Game (Dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AddEditGameScreen_EditPreview() {
    RefWatchMobileTheme {
        AddEditGameScreen(
            uiState = AddEditGameUiState(
                gameId = "previewGame123",
                homeTeamName = "Preview Home Team",
                awayTeamName = "Preview Away Team",
                fieldNumber = "7",
                refereeAssignment = "AR 1",
                venue = "Preview Venue",
                competition = "Preview League",
                gameDateTimeEpochMillis = System.currentTimeMillis() + (3 * 24 * 60 * 60 * 1000),
                halfDurationMinutes = 40,
                halftimeDurationMinutes = 10,
                homeTeamColorArgb = Color.Blue.toArgb(),
                awayTeamColorArgb = Color.Red.toArgb(),
                notes = "This is a note for the preview of an edited game.",
                homeScore = "1", // Example score
                awayScore = "2", // Example score
                isEditing = true,
                errorMessage = null
            ),
            onNavigateBack = {},
            onHomeTeamNameChange = {},
            onAwayTeamNameChange = {},
            onFieldNumberChange = {},
            onRefereeAssignmentChange = {}, // Add dummy handler
            onVenueChange = {},
            onCompetitionChange = {},
            onGameDateTimeChange = {},
            onHalfDurationChange = {},
            onHalftimeDurationChange = {},
            onHomeColorSelected = {},
            onAwayColorSelected = {},
            onNotesChanged = {},
            onHomeScoreChange = {}, // Add dummy handler
            onAwayScoreChange = {}, // Add dummy handler
            onSaveGame = {}
        )
    }
}

// Dummy composable for previewing TimePickerDialog if Material 3 doesn't have a direct one yet
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Time") },
        text = content,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}