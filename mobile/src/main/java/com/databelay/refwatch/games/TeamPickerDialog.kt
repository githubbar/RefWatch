package com.databelay.refwatch.games

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.databelay.refwatch.common.Team // Your common Team enum

@Composable
fun TeamPickerDialog(
    title: String,
    currentSelection: Team,
    onTeamSelected: (Team) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f) // Smaller width for simple selection
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(24.dp), // More padding for dialog content
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)

                Column { // Use Column for radio buttons
                    Team.entries.forEach { team -> // Iterate through enum values
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onTeamSelected(team) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (team == currentSelection),
                                onClick = { onTeamSelected(team) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(team.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    // "OK" button is optional here as selection is immediate, but good for confirmation
                    // TextButton(
                    //     onClick = { onTeamSelected(currentSelection) /* or just onDismiss */ },
                    //     modifier = Modifier.padding(start = 8.dp)
                    // ) {
                    //     Text("OK")
                    // }
                }
            }
        }
    }
}

// Example Usage (you'd call this from AddEditGameScreen)
// @Preview
// @Composable
// fun TeamPickerDialogPreview() {
//     var showDialog by remember { mutableStateOf(true) }
//     var selectedTeam by remember { mutableStateOf(Team.HOME) }
//
//     if (showDialog) {
//         TeamPickerDialog(
//             title = "Select Kick-off Team",
//             currentSelection = selectedTeam,
//             onTeamSelected = {
//                 selectedTeam = it
//                 showDialog = false
//             },
//             onDismiss = { showDialog = false }
//         )
//     }
// }