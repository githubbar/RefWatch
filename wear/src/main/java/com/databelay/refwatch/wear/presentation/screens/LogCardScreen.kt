package com.databelay.refwatch.wear.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.AlertDialogDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import kotlinx.coroutines.delay

@Composable
fun LogCardScreen(
    preselectedTeam: Team?,
    cardType: CardType,
    onLogCard: (team: Team, playerNumber: Int, cardType: CardType) -> Unit,
    onCancel: () -> Unit
) {
    var selectedTeam by remember { mutableStateOf(preselectedTeam) }
    var playerNumberString by remember { mutableStateOf("") }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        scrollState = listState,
        scrollIndicator = { ScrollIndicator(state = listState) }
    ) {
        // Request focus when the composable enters the composition
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 0.dp, bottom = 48.dp),
            autoCentering = null // Allow items to be placed freely
        ) {
            item {
                Text(
                    "Log Card", 
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
            
            preselectedTeam?.let {
                item {
                    Text(
                        "For Team: ${it.name}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                // Player Number
                OutlinedTextField(
                    value = playerNumberString,
                    onValueChange = {
                        if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                            playerNumberString = it
                        }
                    },
                    label = { Text("Player #", style = MaterialTheme.typography.labelSmall) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth(),

                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = Color.Transparent,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlertDialogDefaults.DismissButton(
                        onClick = onCancel,
                    )
                    
                    val playerNum = playerNumberString.toIntOrNull()
                    val canConfirm = selectedTeam != null && playerNum != null && playerNum > 0
                    
                    if (canConfirm) {
                        AlertDialogDefaults.ConfirmButton(
                            onClick = {
                                onLogCard(selectedTeam!!, playerNum, cardType)
                            },
                        )
                    } else {
                        AlertDialogDefaults.ConfirmButton(
                            onClick = {
                                if (selectedTeam == null) {
                                    Toast.makeText(context, "No team selected", Toast.LENGTH_SHORT).show()
                                } else if (playerNumberString.isBlank() || playerNum == null || playerNum <= 0) {
                                    Toast.makeText(context, "Enter a valid player number", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------- Previews ----------------------------------------
// -----------------------------------------------------------------------------------------
@WearPreviewFontScales
@Composable
fun LogCardScreenPreview_Yellow_Home_FontScales() {
    RefWatchWearTheme {
        LogCardScreen(
            preselectedTeam = Team.HOME,
            cardType = CardType.YELLOW,
            onLogCard = { _, _, _ -> },
            onCancel = {}
        )
    }
}

@Preview(device = "id:wearos_small_round", name = "LogCard SmRnd", showBackground = true)
@Preview(device = "id:wearos_large_round", name = "LogCard LrgRnd", showBackground = true)
@Preview(device = "id:wearos_square", name = "LogCard Sqr", showBackground = false)
@Composable
fun LogCardScreenPreview_Yellow_Home() {
    RefWatchWearTheme {
        LogCardScreen(
            preselectedTeam = Team.HOME,
            cardType = CardType.YELLOW,
            onLogCard = { _, _, _ -> },
            onCancel = {}
        )
    }
}


