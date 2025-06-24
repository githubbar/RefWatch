package com.databelay.refwatch.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun ColorPickerDialog(
    title: String,
    availableColors: List<Color>,
    selectedColor: Color, // Current selection to highlight it
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 48.dp), // Adjust minSize as needed
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 300.dp) // Limit height if many colors
                ) {
                    items(availableColors) { color ->
                        ColorPickerItem(
                            color = color,
                            isSelected = color == selectedColor,
                            onClick = { onColorSelected(color) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ColorPickerItem(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp) // Size of the color circle
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .then( // Add a border if selected
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
    )
}

// Example Usage (you'd call this from AddEditGameScreen)
// @Preview
// @Composable
// fun ColorPickerDialogPreview() {
//     val predefinedColors = listOf(
//         Color.Red, Color(0xFFFFA500), Color.Yellow, Color.Green,
//         Color.Cyan, Color.Blue, Color(0xFF800080), Color.Magenta,
//         Color.Black, Color.White, Color.Gray, Color.DarkGray
//     )
//     var showDialog by remember { mutableStateOf(true) }
//     var selected by remember { mutableStateOf(Color.Red) }
//
//     if (showDialog) {
//         ColorPickerDialog(
//             title = "Select Home Color",
//             availableColors = predefinedColors,
//             selectedColor = selected,
//             onColorSelected = {
//                 selected = it
//                 showDialog = false
//             },
//             onDismiss = { showDialog = false }
//         )
//     }
// }