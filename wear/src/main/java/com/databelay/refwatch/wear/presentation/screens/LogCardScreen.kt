package com.databelay.refwatch.wear.presentation.screens // Adjust if your package is different

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn // The scrollable list
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import androidx.wear.compose.material.Text // Or androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField


import com.databelay.refwatch.common.*

import kotlinx.coroutines.delay

@Composable
fun LogCardScreen(
    onLogCard: () -> Unit,
    onCancel: () -> Unit,
) {
    var selectedTeam: Team? by remember { mutableStateOf(null) }
    var selectedCardType: CardType? by remember { mutableStateOf(null) }
    var playerNumberString by remember { mutableStateOf("") }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Attempt to request focus when the screen becomes visible
    // It can be tricky with navigation, a small delay might help
    LaunchedEffect(Unit) {
        delay(300) // Small delay to allow UI to settle
        try {
            focusRequester.requestFocus()
            keyboardController?.show() // Try to show keyboard
        } catch (e: Exception) {
            // Log or handle exception if focus request fails
        }
    }
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(listState))},
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp), // Overall horizontal padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically) // Center content
        ) {
            item {
                Text(
                    "Log Card",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Team Selection
            item { Text("Team:", style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center) }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { selectedTeam = Team.HOME },
//                        colors = ButtonDefaults.buttonColors(
//                            backgroundColor = if (selectedTeam == Team.HOME) homeTeamColor else homeTeamColor.copy(alpha = 0.5f),
//                            contentColor = if (homeTeamColor.luminance() > 0.5f && selectedTeam == Team.HOME) Color.Black else Color.White
//                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text("Home") }
                    Button(
                        onClick = { selectedTeam = Team.AWAY },
//                        colors = ButtonDefaults.buttonColors(
//                            backgroundColor = if (selectedTeam == Team.AWAY) awayTeamColor else awayTeamColor.copy(alpha = 0.5f),
//                            contentColor = if (awayTeamColor.luminance() > 0.5f && selectedTeam == Team.AWAY) Color.Black else Color.White
//                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text("Away") }
                }
            }

            // Card Type Selection
            item { Text("Card Type:", style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center) }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { selectedCardType = CardType.YELLOW },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (selectedCardType == CardType.YELLOW) Color.Yellow else Color.Yellow.copy(alpha = 0.5f),
                            contentColor = Color.Black // Yellow usually needs black text
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text("Yellow") }
                    Button(
                        onClick = { selectedCardType = CardType.RED },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (selectedCardType == CardType.RED) Color.Red else Color.Red.copy(alpha = 0.5f),
                            contentColor = Color.White // Red usually needs white text
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text("Red") }
                }
            }

            // Player Number
            item { Text("Player Number:", style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center) }
            item {
                OutlinedTextField(
                    value = playerNumberString,
                    onValueChange = { it ->
                        // Allow only up to 2 digits
                        if (it.length <= 2 && it.all { it.isDigit() }) {
                            playerNumberString = it
                        } else if (it.isEmpty()) { // Allow clearing the field
                            playerNumberString = ""
                        }
                    },
                    label = { Text("#") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword, // NumberPassword to show only numbers
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth(0.6f) // Make text field a bit wider
                        .focusRequester(focusRequester) // Apply focus requester
                )
            }

            // Action Buttons
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.secondaryButtonColors(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            val playerNum = playerNumberString.toIntOrNull()
                            if (selectedTeam != null && selectedCardType != null && playerNum != null && playerNum > 0) {
                                // TODO: implement log card
                                // Team, Int, CardType
                                // selectedTeam!!, playerNum, selectedCardType!!

                                onLogCard()

                                keyboardController?.hide() // Hide keyboard on success
                            } else {
                                Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = selectedTeam != null && selectedCardType != null && playerNumberString.isNotBlank() && (playerNumberString.toIntOrNull() ?: 0) > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("Log") }
                }
            }
        }
    }
}