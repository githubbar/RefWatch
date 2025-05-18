package com.databelay.refwatch.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import kotlinx.coroutines.launch
import com.databelay.refwatch.common.*

@Composable
fun LoadIcsScreen(
    onIcsLoaded: (List<Game>) -> Unit, // <<<< Callback with List<GameSettings>    // viewModel: SomeViewModel // ViewModel to handle loading logic
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Load Game Schedule",
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            "To load games from an ICS file, please use the companion phone app (not yet implemented) or transfer the file via developer methods.",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Placeholder for actual loading mechanism
        Button(
            onClick = {
                isLoading = true
                message = null
                coroutineScope.launch {
                    // ** SIMULATED/PLACEHOLDER ICS LOADING **
                    // In a real app, this would involve:
                    // 1. Triggering file selection on a companion app.
                    // 2. Receiving file data via Wearable Data Layer.
                    // 3. OR, for testing, load from app assets.
                    try {
                        // val games: List<GameSettings> = parseIcsAndConvertToGameSettings(inputStream)
                        kotlinx.coroutines.delay(2000) // Simulate loading
                        // val games = parseIcsStream(inputStream)
                        // onIcsLoadedAndParsed(games)
                        message = "Simulated ICS load successful. (Functionality to be implemented via companion app)"
                    } catch (e: Exception) {
                        message = "Error loading ICS: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            } else {
                Text("Load Example File (Test)")
            }
        }

        message?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
        }
    }
}