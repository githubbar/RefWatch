package com.databelay.refwatch.wear.presentation.screens // Your package

// No items(count) or items(list) import needed here as we use item { Row { ... } } for the grid rows
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.scrollAway
import com.databelay.refwatch.common.luminance
import com.databelay.refwatch.common.predefinedColors
import com.databelay.refwatch.wear.presentation.components.ColorIndicator

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun ColorSelectionScreen(
    title: String,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onConfirm: () -> Unit
) {
    // Using ScalingLazyColumn for overall screen structure and scrolling behavior
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Top) // Align to top
        ) {
            item {
                Text(
                    title,
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Group colors into rows of 3 for better layout on small screens
            val colorRows = predefinedColors.chunked(3)
            items(colorRows.size) { rowIndex ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp) // Add some horizontal padding for the row
                ) {
                    colorRows[rowIndex].forEach { color ->
                        Chip(
                            onClick = { onColorSelected(color) },
                            label = { /* Text(color.toHexString()) Optional: for debugging */ },
                            icon = {
                                ColorIndicator(
                                    color = color,
                                    modifier = Modifier.size(30.dp)
                                )
                            },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = if (color == selectedColor) color else color.copy(alpha = 0.6f),
                                contentColor = if (color.luminance() > 0.5f) Color.Black else Color.White // Adjust text color for visibility
                            ),
                            modifier = Modifier.weight(1f),
                            enabled = true // Ensure chip is clickable
                        )
                    }
                    // Fill empty spots in the row if less than 3 colors
                    repeat(3 - colorRows[rowIndex].size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).fillMaxWidth(0.7f)
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}
