package com.databelay.refwatch.wear.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.Team
import kotlinx.coroutines.delay

@Composable
fun LogCardScreen(
    preselectedTeam: Team?,
    cardType: CardType,
    onLogCard: (team: Team, playerNumber: Int, cardType: CardType) -> Unit,
    onCancel: () -> Unit
) {

    var selectedTeam by remember { mutableStateOf(preselectedTeam) }
//    var selectedCardType by remember { mutableStateOf<CardType?>(CardType.YELLOW) }
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
            preselectedTeam?.let {
                item {
                    Text(
                        "For Team: ${it.name}",
                        style = MaterialTheme.typography.caption1,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
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
                        .fillMaxWidth(0.4f)
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
                            // Read selectedTeam into a local immutable variable
                            val currentSelectedTeam = selectedTeam // selectedTeam is MutableState<Team?>

                            if (currentSelectedTeam != null && playerNum != null && playerNum > 0) {
                                // Now currentSelectedTeam can be smart-cast to Team
                                onLogCard(currentSelectedTeam, playerNum, cardType)
                            } else {
                                if (currentSelectedTeam == null) {
                                    Toast.makeText(context, "No team selected", Toast.LENGTH_SHORT).show()
                                } else { // playerNum is null or not > 0
                                    Toast.makeText(context, "Enter a valid player number", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = selectedTeam != null && playerNumberString.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Log") }
                }
            }
        }
    }
}
