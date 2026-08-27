package com.databelay.refwatch.wear.presentation.screens

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.databelay.refwatch.common.theme.RefWatchWearTheme

@Composable
fun UnifiedConfirmationDialog(dialogInfo: ConfirmationDialogInfo) {
    Log.d("ConfirmationDialog", "Showing dialog: ${dialogInfo.title}")
    
    if (dialogInfo.neutralButtonText != null) {
        // Scrollable dialog for 3+ buttons
        AlertDialog(
            visible = true,
            onDismissRequest = { dialogInfo.onDismissDialogAction() },
            title = { Text(dialogInfo.title, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) },
            edgeButton = {
                AlertDialogDefaults.EdgeButton(
                    onClick = { dialogInfo.onDismissDialogAction() }
                ) { Text(dialogInfo.dismissButtonText) }
            }
        ) {
            item {
                dialogInfo.text?.let { 
                    Text(it, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp)) 
                }
            }
            item {
                Button(
                    onClick = { dialogInfo.onConfirmAction() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(dialogInfo.confirmButtonText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                Button(
                    onClick = { dialogInfo.onNeutralAction?.invoke() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(dialogInfo.neutralButtonText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    } else {
        // Standard 2-button dialog
        AlertDialog(
            visible = true,
            onDismissRequest = {
                dialogInfo.onDismissDialogAction()
            },
            title = { Text(dialogInfo.title, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) },
            text = {
                dialogInfo.text?.let { Text(it, textAlign = TextAlign.Center) }
            },
            dismissButton = {
                AlertDialogDefaults.DismissButton(
                    onClick = {
                        dialogInfo.onDismissDialogAction()
                    }
                ) { Text(dialogInfo.dismissButtonText) }
            },
            confirmButton = {
                AlertDialogDefaults.ConfirmButton(
                    onClick = {
                        dialogInfo.onConfirmAction()
                    }
                ) { Text(dialogInfo.confirmButtonText) }
            },
        )
    }
}

@Preview(device = "id:wearos_large_round", fontScale = 2.0f, showSystemUi = true)
@Composable
fun Preview_ExtraTimeDialog_2x() {
    val dialogInfo = ConfirmationDialogInfo.EndOfMainTime(
        onSetExtraTimeAndPenalties = {},
        onSetPenaltiesOnly = {},
        onEndPhaseWithoutExtraTime = {},
        onDialogClose = {}
    )
    RefWatchWearTheme {
        UnifiedConfirmationDialog(dialogInfo = dialogInfo)
    }
}
