package com.databelay.refwatch.wear.presentation.screens

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.wear.input.RemoteInputIntentHelper

/**
 * Wear Compose has no TextField. The platform way to collect free text on a watch is to
 * hand off to the system input activity, which gives the user the on-screen keyboard,
 * voice dictation and handwriting in one full-screen surface. Because that surface belongs
 * to the system it also stays legible at every font scale, unlike an in-app text field
 * squeezed onto a round screen.
 */
class TextInputLauncher internal constructor(
    private val launch: (key: String, title: String, label: String, currentValue: String?) -> Unit
) {
    /**
     * Opens the system text input activity.
     *
     * @param key identifies which field the result belongs to; must match the key the
     *   caller switches on in its result handler.
     * @param currentValue offered as a tappable suggestion so the user can see, and reuse,
     *   what the field holds today. The system activity cannot pre-fill the edit box.
     */
    fun launch(key: String, title: String, label: String, currentValue: String? = null) {
        launch.invoke(key, title, label, currentValue)
    }
}

/**
 * Remembers a launcher for the system text input activity.
 *
 * [onResult] receives the key that was passed to [TextInputLauncher.launch] together with
 * the text the user entered, so a single launcher can serve several fields on a screen.
 * It is not called when the user cancels or submits nothing.
 */
@Composable
fun rememberTextInputLauncher(onResult: (key: String, value: String) -> Unit): TextInputLauncher {
    val currentOnResult by rememberUpdatedState(onResult)
    // The key travels through the launcher so the callback can tell which field came back.
    // Saved, because the system input activity can outlive this process on a watch.
    val pendingKey = rememberSaveable { mutableStateOf<String?>(null) }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val key = pendingKey.value ?: return@rememberLauncherForActivityResult
        pendingKey.value = null
        val results = RemoteInput.getResultsFromIntent(activityResult.data)
            ?: return@rememberLauncherForActivityResult
        val value = results.getCharSequence(key)?.toString()?.trim()
        if (!value.isNullOrBlank()) {
            currentOnResult(key, value)
        }
    }

    return remember(activityLauncher) {
        TextInputLauncher { key, title, label, currentValue ->
            pendingKey.value = key
            val remoteInput = RemoteInput.Builder(key)
                .setLabel(label)
                .apply {
                    if (!currentValue.isNullOrBlank()) {
                        setChoices(arrayOf(currentValue))
                    }
                }
                .build()

            val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
            RemoteInputIntentHelper.putTitleExtra(intent, title)
            activityLauncher.launch(intent)
        }
    }
}
