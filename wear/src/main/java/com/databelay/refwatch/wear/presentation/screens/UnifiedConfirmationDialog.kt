package com.databelay.refwatch.wear.presentation.screens

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
fun UnifiedConfirmationDialog(dialogInfo: ConfirmationDialogInfo) {
    Log.d("ConfirmationDialog", "Showing dialog: ${dialogInfo.title}")
    AlertDialog(
        visible = true,
        onDismissRequest = {
            dialogInfo.onDismissDialogAction() // This handles specific dismiss logic + common close logic
        },
        title = { Text(dialogInfo.title, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) },
        dismissButton = {
            if (dialogInfo.neutralButtonText == null) {
                AlertDialogDefaults.DismissButton(
                    onClick = {
                        dialogInfo.onDismissDialogAction() // This handles specific dismiss logic + common close logic
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent, // Standard for dismiss
//                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text(dialogInfo.dismissButtonText) }
            }
        },
        text = {
            if (dialogInfo.neutralButtonText != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    dialogInfo.text?.let { 
                        Text(it, textAlign = TextAlign.Center) 
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Button(
                        onClick = { dialogInfo.onConfirmAction() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text(dialogInfo.confirmButtonText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Button(
                        onClick = { dialogInfo.onNeutralAction?.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text(dialogInfo.neutralButtonText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Button(
                        onClick = { dialogInfo.onDismissDialogAction() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text(dialogInfo.dismissButtonText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                dialogInfo.text?.let { Text(it, textAlign = TextAlign.Center) }
            }
        },
        confirmButton = {
            if (dialogInfo.neutralButtonText == null) {
                AlertDialogDefaults.ConfirmButton(
                    onClick = {
                        dialogInfo.onConfirmAction() // This handles specific confirm logic + common close logic
                    }
                ) { Text(dialogInfo.confirmButtonText) }
            }
        },
    )
}
