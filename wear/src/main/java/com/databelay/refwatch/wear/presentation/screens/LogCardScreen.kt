package com.databelay.refwatch.wear.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.databelay.refwatch.common.*
import kotlinx.coroutines.delay

@Composable
fun LogCardScreen(
    preselectedTeam: Team?,
    onLogCard: (team: Team, playerNumber: Int, cardType: CardType) -> Unit,
    onCancel: () -> Unit
) {

    var selectedTeam by remember { mutableStateOf(preselectedTeam) }
    var selectedCardType by remember { mutableStateOf<CardType?>(CardType.YELLOW) }
    var playerNumberString by remember { mutableStateOf("") }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberScalingLazyListState()

    // Request focus when the screen is first composed
    LaunchedEffect(Unit) {
        listState.scrollToItem(2)
        delay(200) // A small delay can help ensure the UI is ready for focus
        focusRequester.requestFocus()
    }
    Scaffold(
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            item {
                Text("Log Card", style = MaterialTheme.typography.title3)
            }
            // Card Type Selection
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { selectedCardType = CardType.YELLOW },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (selectedCardType == CardType.YELLOW) Color.Yellow else Color.Yellow.copy(alpha = 0.4f),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text("Yellow") }
                    Button(
                        onClick = { selectedCardType = CardType.RED },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (selectedCardType == CardType.RED) Color.Red else Color.Red.copy(alpha = 0.4f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Text("Red") }
                }
            }

            // Player Number
            item {
                // Use the correct TextField for Wear OS
                TextField(
                    value = playerNumberString,
                    onValueChange = {
                        if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                            playerNumberString = it
                        }
                    },
                    label = { Text("#") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .focusRequester(focusRequester)
                )
            }

            // Action Buttons
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                // Call the updated onLogCard lambda with all the details
                                onLogCard(selectedTeam!!, playerNum, selectedCardType!!)
                            } else {
                                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = selectedTeam != null && selectedCardType != null && playerNumberString.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Log") }
                }
            }
        }
    }
}
