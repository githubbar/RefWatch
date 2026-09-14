package com.databelay.refwatch.wear.presentation.screens

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.AlertDialogContent
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
fun UnifiedConfirmationDialog(dialogInfo: ConfirmationDialogInfo) {
    Log.d("ConfirmationDialog", "Showing dialog: ${dialogInfo.title}")
    Dialog(
        visible = true,
        onDismissRequest = { dialogInfo.onDismissDialogAction() },
    ) {
        UnifiedConfirmationDialogContent(dialogInfo)
    }
}

/**
 * The dialog body without its window, so the font-scale screenshot audit can render it --
 * a [Dialog] opens a separate window that preview rendering does not capture.
 *
 * Button labels never go inside [AlertDialogDefaults.ConfirmButton] /
 * [AlertDialogDefaults.DismissButton]: those are fixed-size icon buttons (63x54dp and 60dp),
 * so words like "Confirm" or "Dismiss" were clipped at large font sizes. That is what got
 * the app rejected from Play. They carry an icon, and the label becomes the content
 * description. Choices that need words use full-width [Button]s, which grow with the text.
 */
@Composable
fun UnifiedConfirmationDialogContent(dialogInfo: ConfirmationDialogInfo) {
    val title: @Composable () -> Unit = {
        Text(dialogInfo.title, color = MaterialTheme.colorScheme.primary)
    }
    val text: (@Composable () -> Unit)? = dialogInfo.text?.let { { Text(it) } }

    if (dialogInfo.neutralButtonText != null) {
        // Three labelled choices: a scrollable stack of full-width buttons.
        AlertDialogContent(title = title, text = text) {
            item {
                LabelledDialogButton(
                    label = dialogInfo.confirmButtonText,
                    onClick = dialogInfo.onConfirmAction
                )
            }
            item {
                LabelledDialogButton(
                    label = dialogInfo.neutralButtonText,
                    onClick = { dialogInfo.onNeutralAction?.invoke() }
                )
            }
            item {
                LabelledDialogButton(
                    label = dialogInfo.dismissButtonText,
                    onClick = dialogInfo.onDismissDialogAction,
                    secondary = true
                )
            }
        }
    } else {
        AlertDialogContent(
            title = title,
            text = text,
            confirmButton = {
                AlertDialogDefaults.ConfirmButton(onClick = dialogInfo.onConfirmAction) {
                    Icon(Icons.Filled.Check, contentDescription = dialogInfo.confirmButtonText)
                }
            },
            dismissButton = {
                AlertDialogDefaults.DismissButton(onClick = dialogInfo.onDismissDialogAction) {
                    Icon(Icons.Filled.Close, contentDescription = dialogInfo.dismissButtonText)
                }
            },
        )
    }
}

@Composable
private fun LabelledDialogButton(label: String, onClick: () -> Unit, secondary: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (secondary) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
    ) {
        Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
