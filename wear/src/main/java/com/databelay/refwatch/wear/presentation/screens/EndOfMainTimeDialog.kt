package com.databelay.refwatch.wear.presentation.screens

// Remove M3 AlertDialog import if not used elsewhere in this file
// import androidx.compose.material3.AlertDialog
// import androidx.compose.material3.Button // Use Wear Button
// import androidx.compose.material3.Text   // Use Wear Text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog

@Composable
fun EndOfMainTimeDialog(
    onDismiss: () -> Unit,
    onStartExtraTime: () -> Unit,
    onEndMatch: () -> Unit
) {
    Dialog( // Use androidx.wear.compose.material.dialog.Dialog
        showDialog = true, // This is a bit redundant if the outer `if` controls it, but Dialog needs it
        onDismissRequest = onDismiss,
        // You can also use the simpler `Alert` if it fits your needs:
        // Alert(
        //    title = { Text("Main Time Finished", textAlign = TextAlign.Center, color = MaterialTheme.colors.onSurface) },
        //    onDismissRequest = onDismiss,
        //    positiveButton = {}, // Alert has specific slots for buttons
        //    negativeButton = {}
        // ) { // Content slot for Alert
        //    // ... content for Alert ...
        // }
    ) {
        // Content of the Dialog
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp), // Adjust padding as needed
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Main Time Finished",
                style = MaterialTheme.typography.title3, // Use Wear typography
                color = MaterialTheme.colors.onSurface, // Use Wear theme colors
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Extra Time?",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button( // androidx.wear.compose.material.Button
                onClick = onStartExtraTime,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary, // Use Wear theme colors
                    contentColor = MaterialTheme.colors.onPrimary
                )
            ) {
                Text("Yes")
            }

            Button( // androidx.wear.compose.material.Button
                onClick = onEndMatch,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.secondary, // Use Wear theme colors
                    contentColor = MaterialTheme.colors.onSecondary
                )
            ) {
                Text("No")
            }

            // Optional: A dismiss/cancel button if needed and not just relying on onDismissRequest
            // Button(
            // onClick = onDismiss,
            // modifier = Modifier.fillMaxWidth(),
            // colors = ButtonDefaults.outlinedButtonColors() // Example for an outlined/secondary action
            // ) {
            // Text("Cancel")
            // }
        }
    }
}

