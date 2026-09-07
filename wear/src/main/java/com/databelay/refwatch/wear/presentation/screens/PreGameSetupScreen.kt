package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.theme.PredefinedJerseyColors
import com.databelay.refwatch.common.theme.RefWatchWearTheme

@Composable
fun PreGameSetupScreen(
    game: Game?,
    onEditHomeTeamNameClick: () -> Unit,
    onEditAwayTeamNameClick: () -> Unit,
    onHomeColorPickerClick: () -> Unit,
    onAwayColorPickerClick: () -> Unit,
    onSetHalfDuration: (Int) -> Unit,
    onSetHalftimeDuration: (Int) -> Unit,
    onCreateMatchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    val homeTeamName = game?.homeTeamName ?: "Home"
    val awayTeamName = game?.awayTeamName ?: "Away"
    val homeTeamColor = game?.homeTeamColor ?: Color.Gray
    val awayTeamColor = game?.awayTeamColor ?: Color.LightGray
    val halfDurationMinutes = game?.halfDurationMinutes ?: 30
    val halftimeDurationMinutes = game?.halftimeDurationMinutes ?: 10

    ScreenScaffold(
        scrollState = listState,
        scrollIndicator = {
            ScrollIndicator(state = listState)
        },
        edgeButton = {
            EdgeButton(
                onClick = onCreateMatchClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Create Match")
            }
        },
        modifier = modifier,
        // Left at the responsive default so the list clears the bezel and the EdgeButton.
    ) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
        ) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Match Setup",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Team Name Editors. These are stacked rather than placed side by side so
            // that each name gets the full screen width; at large font scales a
            // half-width chip ellipsises the name down to a character or two.
            item {
                OutlinedButton(
                    onClick = onEditHomeTeamNameClick,
                    icon = {
                        Icon(
                            Icons.Default.Edit,
                            modifier = Modifier.size(ButtonDefaults.SmallIconSize),
                            contentDescription = "Edit Home Team Name"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    label = {
                        Text(
                            homeTeamName,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
            item {
                OutlinedButton(
                    onClick = onEditAwayTeamNameClick,
                    icon = {
                        Icon(
                            Icons.Default.Edit,
                            modifier = Modifier.size(ButtonDefaults.SmallIconSize),
                            contentDescription = "Edit Away Team Name"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    label = {
                        Text(
                            awayTeamName,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            // Jersey Colors
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    ColorPickerButton(
                        "Home",
                        homeTeamColor,
                        onClick = onHomeColorPickerClick
                    )
                    ColorPickerButton(
                        "Away",
                        awayTeamColor,
                        onClick = onAwayColorPickerClick
                    )
                }
            }

            // Half Duration
            item {
                DurationSettingStepper(
                    label = "Half Duration",
                    currentValue = halfDurationMinutes,
                    onValueChange = onSetHalfDuration,
                    valueRange = 15..60
                )
            }

            // Halftime Duration
            item {
                DurationSettingStepper(
                    label = "Halftime Duration",
                    currentValue = halftimeDurationMinutes,
                    onValueChange = onSetHalftimeDuration,
                    valueRange = 5..30
                )
            }
        }
    }
}


@Composable
fun ColorPickerButton(label: String, currentColor: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(currentColor)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    CircleShape
                )
        )
    }
}

/**
 * A dialog Composable for picking a color.
 * This remains available for the parent composable to use.
 */
@Composable
fun SimpleColorPickerDialogContent(
    title: String,
    availableColors: List<Color>,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(
        scrollState = listState,
        scrollIndicator = { ScrollIndicator(state = listState) }
    ) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            item {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center
                )
            }
            items(availableColors.chunked(3)) { rowColors ->
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    rowColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    CircleShape
                                )
                                .clickable { onColorSelected(color) }
                        )
                    }
                }
            }
            item {
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * A dialog Composable for picking a color.
 * This remains available for the parent composable to use.
 */
@Composable
fun SimpleColorPickerDialog(
    title: String,
    availableColors: List<Color>,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        visible = true,
        onDismissRequest = onDismiss,
    ) {
        SimpleColorPickerDialogContent(
            title = title,
            availableColors = availableColors,
            onColorSelected = onColorSelected,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun DurationSettingStepper(
    label: String,
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange = 1..60,
    step: Int = 5
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall
        ) // Consider a smaller style if too large e.g. titleMedium
        Spacer(Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Material3 Button only applies a minimum height, so it grows with the font
            // scale. The Material2 CompactButton used here previously forced
            // requiredSize(32.dp), which clipped the glyph at large font sizes.
            Button(
                onClick = {
                    if (currentValue - step >= valueRange.first) onValueChange(
                        currentValue - step
                    )
                },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "-",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = "$currentValue min",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = {
                    if (currentValue + step <= valueRange.last) onValueChange(
                        currentValue + step
                    )
                },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "+",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// --------------------------------------- Previews ----------------------------------------
@Preview(device = "id:wearos_small_round", showBackground = true)
@Preview(device = "id:wearos_square", showBackground = true)
@Preview(device = "id:wearos_large_round", showBackground = true)
@WearPreviewFontScales
@Composable
fun PreviewPreGameSetupScreen() {
    RefWatchWearTheme {
        // Create a sample Game object for the preview
        val sampleGame = Game.defaults().copy(
            homeTeamName = "Spartans",
            awayTeamName = "Vikings",
            halfDurationMinutes = 40,
            halftimeDurationMinutes = 15
        )
        PreGameSetupScreen(
            game = sampleGame,
            onEditHomeTeamNameClick = {},
            onEditAwayTeamNameClick = {},
            onHomeColorPickerClick = {},
            onAwayColorPickerClick = {},
            onSetHalfDuration = {},
            onSetHalftimeDuration = {},
            onCreateMatchClick = {}
        )
    }
}
@Preview(device = "id:wearos_small_round", showBackground = true)
@Preview(device = "id:wearos_square", showBackground = true)
@Preview(device = "id:wearos_large_round", showBackground = true)
@WearPreviewFontScales
@Composable
fun PreviewSimpleColorPickerDialog() {
    RefWatchWearTheme {
        SimpleColorPickerDialogContent(
            title = "Home Color",
            availableColors = PredefinedJerseyColors,
            onColorSelected = {},
            onDismiss = {}
        )
    }
}
