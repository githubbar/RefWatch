package com.databelay.refwatch.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.databelay.refwatch.common.theme.RefWatchMobileTheme
import com.databelay.refwatch.data.garmin.GarminLinkUiState
import com.databelay.refwatch.data.garmin.GarminLinkViewModel
import com.databelay.refwatch.data.garmin.GarminStore
import com.databelay.refwatch.data.garmin.LinkedGarminDevice
import com.databelay.refwatch.data.garmin.PairingCode

@Composable
fun GarminLinkSection(viewModel: GarminLinkViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!GarminStore.showSection(state.storeUrl, state.devices.size)) return
    val context = LocalContext.current
    GarminLinkContent(
        state = state,
        storeUrl = state.storeUrl,
        onOpenStore = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: ActivityNotFoundException) {
                Log.e("GarminLinkSection", "No app can open $url", e)
            }
        },
        onLink = viewModel::requestCode,
        onUnlink = viewModel::unlink
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun GarminLinkContent(
    state: GarminLinkUiState,
    storeUrl: String?,
    onOpenStore: (String) -> Unit,
    onLink: () -> Unit,
    onUnlink: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Garmin watch", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        val code = state.code
        if (code != null) {
            Text(
                code.code.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Expires in ${state.secondsLeft / 60}:${"%02d".format(state.secondsLeft % 60)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "On the watch, open RefWatch → Link account and enter this code. " +
                    "Or, in the Garmin Connect app, open RefWatch's settings and enter it as the pairing code.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Text(
                "Link a Garmin watch to referee your games on it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!storeUrl.isNullOrBlank()) {
                TextButton(onClick = { onOpenStore(storeUrl) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Get RefWatch for Garmin in the Connect IQ store")
                }
            }
            Button(onClick = onLink, enabled = !state.busy, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Link a Garmin watch")
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        state.devices.forEach { device ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.deviceName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Last seen " + DateUtils.getRelativeTimeSpanString(device.lastSeenAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onUnlink(device.id) }) { Text("Unlink") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GarminLinkContentCodePreview() {
    RefWatchMobileTheme {
        GarminLinkContent(
            state = GarminLinkUiState(
                code = PairingCode("012345", 0),
                secondsLeft = 545,
                devices = listOf(LinkedGarminDevice("h1", "006-B2604-00", System.currentTimeMillis()))
            ),
            storeUrl = null,
            onOpenStore = {},
            onLink = {},
            onUnlink = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GarminLinkContentIdlePreview() {
    RefWatchMobileTheme {
        GarminLinkContent(
            state = GarminLinkUiState(),
            storeUrl = "https://apps.garmin.com/apps/preview",
            onOpenStore = {},
            onLink = {},
            onUnlink = {}
        )
    }
}
