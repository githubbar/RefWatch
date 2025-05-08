package com.databelay.refwatch.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi // For ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn // The scrollable list
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold // Material Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText // Material TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.scrollAway // Modifier for TimeText

import com.databelay.refwatch.data.Team

@OptIn(ExperimentalWearFoundationApi::class) // Still good to keep if using experimental SLC features
@Composable
fun KickOffSelectionScreen(
    selectedTeam: Team,
    onTeamSelected: (Team) -> Unit,
    onConfirm: () -> Unit
) {
    // 1. Create the ScalingLazyListState (from androidx.wear.compose.material)
    val listState = rememberScalingLazyListState()

    // 2. Use Scaffold. It doesn't take 'state' directly for these components anymore.
    Scaffold(
        timeText = {
            // TimeText uses Modifier.scrollAway with the listState
            TimeText(modifier = Modifier.scrollAway(listState))
        },
        positionIndicator = {
            // PositionIndicator is directly given the listState
            PositionIndicator(scalingLazyListState = listState)
        },
        vignette = {
            Vignette(vignettePosition = VignettePosition.TopAndBottom)
        }
    ) { // Content lambda of the Scaffold
        // 3. ScalingLazyColumn uses the listState
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            contentPadding = PaddingValues(top = 28.dp, bottom = 28.dp, start = 8.dp, end = 8.dp) // Adjust padding
        ) {
            item {
                Text(
                    "Who Kicks Off?",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp) // Increased padding
                )
            }
            item {
                ToggleChip(
                    checked = selectedTeam == Team.HOME,
                    onCheckedChange = { onTeamSelected(Team.HOME) },
                    label = { Text("Home Team") },
                    modifier = Modifier.fillMaxWidth(0.85f), // Slightly wider
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.radioIcon(checked = selectedTeam == Team.HOME), // Using radioIcon for mutual exclusivity
                            contentDescription = if (selectedTeam == Team.HOME) "Selected Home" else "Select Home"
                        )
                    }
                )
            }
            item {
                ToggleChip(
                    checked = selectedTeam == Team.AWAY,
                    onCheckedChange = { onTeamSelected(Team.AWAY) },
                    label = { Text("Away Team") },
                    modifier = Modifier.fillMaxWidth(0.85f),
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.radioIcon(checked = selectedTeam == Team.AWAY),
                            contentDescription = if (selectedTeam == Team.AWAY) "Selected Away" else "Select Away"
                        )
                    }
                )
            }
            item {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(0.7f)
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}