package com.databelay.refwatch.wear.presentation.components // << YOUR PACKAGE NAME

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning // Or another appropriate icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog // Import Wear OS Dialog

@Composable
fun ConfirmationDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector = Icons.Filled.Warning, // Allow custom icon
    confirmButtonText: String = "Confirm",
    dismissButtonText: String = "Cancel"
) {
    Dialog( // Use androidx.wear.compose.material.dialog.Dialog
        true,
        onDismissRequest = onDismiss,
        // content is a lambda
    ) {
        Card(
            onClick = { /* The Card itself does nothing when clicked */ }, // <<<< ADD THIS
            modifier = Modifier
                .fillMaxWidth(0.95f), // Slightly wider for Wear OS dialogs
            // shape = MaterialTheme.shapes.medium // Default shape is usually fine
        ) {
            Column(
                modifier = Modifier.padding(all = 16.dp), // Standard padding
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Confirmation Icon", // Generic description
                    tint = MaterialTheme.colors.error, // Or adapt based on dialog type
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.body1, // Suitable for dialog message
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                // For Wear OS, buttons are often stacked vertically or use a CompactChip layout
                // However, for simple Confirm/Cancel, a Row can still work if space allows,
                // or they can be full width and stacked.
                // Let's try a Row first, common for simple confirmation.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally) // SpacedBy and Center
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.secondaryButtonColors(), // Use secondary for dismiss
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(dismissButtonText)
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.primaryButtonColors(), // Use primary for confirm (often error color if it's a destructive action)
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(confirmButtonText)
                    }
                }
                // Alternative for Wear: Stacked Buttons
                /*
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.primaryButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(confirmButtonText)
                }
                Spacer(Modifier.height(6.dp)) // Add a little space
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(dismissButtonText)
                }
                */
            }
        }
    }
}