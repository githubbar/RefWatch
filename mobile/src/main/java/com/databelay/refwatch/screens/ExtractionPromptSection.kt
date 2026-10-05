package com.databelay.refwatch.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.databelay.refwatch.common.theme.RefWatchMobileTheme

/** A change that would replace the prompt in the editor, held until the user confirms it. */
private sealed interface PendingReplace {
    data object Default : PendingReplace
    data class Saved(val name: String) : PendingReplace
}

/**
 * Settings section for the prompt the AI uses to read imported schedules: an editor, a way back
 * to the shipped default, and named saved prompts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtractionPromptSection(
    prompt: String,
    defaultPrompt: String,
    savedPrompts: Map<String, String>,
    onPromptChange: (String) -> Unit,
    onLoadDefault: () -> Unit,
    onSaveAs: (String) -> Unit,
    onLoadSaved: (String) -> Unit,
    onDeleteSaved: (String) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var showSavedList by remember { mutableStateOf(false) }
    var pendingReplace by remember { mutableStateOf<PendingReplace?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    val isDefault = prompt == defaultPrompt
    val matchingSavedName = savedPrompts.entries.firstOrNull { it.value == prompt }?.key
    // Edits that exist only in the editor would be lost by loading another prompt.
    val hasUnsavedEdits = !isDefault && matchingSavedName == null

    fun requestReplace(target: PendingReplace) {
        if (hasUnsavedEdits) {
            pendingReplace = target
        } else {
            when (target) {
                PendingReplace.Default -> onLoadDefault()
                is PendingReplace.Saved -> onLoadSaved(target.name)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Schedule Import Prompt",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "When you import a calendar file, the AI follows these instructions to read " +
                "teams, age group and field from each game. If the AI can't be reached or " +
                "gives an unusable answer, the app's built-in parser is used instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when {
                isDefault -> "Using the default prompt"
                matchingSavedName != null -> "Using saved prompt \"$matchingSavedName\""
                else -> "Edited — not saved"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            minLines = 8,
            maxLines = 16,
            label = { Text("Prompt") }
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { requestReplace(PendingReplace.Default) }, enabled = !isDefault) {
                Text("Load default")
            }
            OutlinedButton(onClick = { showSaveDialog = true }) {
                Text("Save as…")
            }
            OutlinedButton(onClick = { showSavedList = true }, enabled = savedPrompts.isNotEmpty()) {
                Text("Saved prompts (${savedPrompts.size})")
            }
        }
    }

    if (showSaveDialog) {
        SavePromptDialog(
            initialName = matchingSavedName.orEmpty(),
            existingNames = savedPrompts.keys,
            onSave = { name ->
                onSaveAs(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showSavedList) {
        AlertDialog(
            onDismissRequest = { showSavedList = false },
            title = { Text("Saved prompts") },
            text = {
                LazyColumn {
                    items(savedPrompts.keys.toList(), key = { it }) { name ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = {
                                showSavedList = false
                                requestReplace(PendingReplace.Saved(name))
                            }) { Text("Load") }
                            IconButton(onClick = { pendingDelete = name }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete $name")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSavedList = false }) { Text("Close") }
            }
        )
    }

    pendingReplace?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingReplace = null },
            title = { Text("Replace your edits?") },
            text = {
                Text(
                    when (target) {
                        PendingReplace.Default -> "The prompt you edited will be replaced by the default."
                        is PendingReplace.Saved -> "The prompt you edited will be replaced by \"${target.name}\"."
                    } + " Use Save as… first to keep it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        PendingReplace.Default -> onLoadDefault()
                        is PendingReplace.Saved -> onLoadSaved(target.name)
                    }
                    pendingReplace = null
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplace = null }) { Text("Cancel") }
            }
        )
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"$name\"?") },
            text = { Text("This saved prompt will be removed. The prompt in the editor is not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSaved(name)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SavePromptDialog(
    initialName: String,
    existingNames: Set<String>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val replaces = existingNames.any { it.equals(trimmed, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save prompt as") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (replaces) {
                    Text(
                        text = "Replaces the saved prompt with this name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Reuse the stored spelling so "travel" does not sit beside "Travel".
                    onSave(existingNames.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed)
                },
                enabled = trimmed.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ExtractionPromptSectionPreview() {
    RefWatchMobileTheme {
        ExtractionPromptSection(
            prompt = "You extract soccer referee assignment details from calendar events.",
            defaultPrompt = "default",
            savedPrompts = mapOf("Tournament" to "…"),
            onPromptChange = {},
            onLoadDefault = {},
            onSaveAs = {},
            onLoadSaved = {},
            onDeleteSaved = {}
        )
    }
}
