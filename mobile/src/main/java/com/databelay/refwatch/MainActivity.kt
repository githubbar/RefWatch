package com.databelay.refwatch // Your phone app's package name

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.* // Using Material 3 for the phone app
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.BufferedReader
import java.io.InputStreamReader

import com.databelay.refwatch.common.GameSettings // <<<< USING SHARED GameSettings
import com.databelay.refwatch.common.SimpleIcsParser
import com.databelay.refwatch.common.SimpleIcsEvent
import com.databelay.refwatch.common.Team // If Team enum is in common
import com.databelay.refwatch.common.theme.*

// --- Constants for Wearable Communication ---
private const val TAG = "RefWatchCompanion"
private const val ICS_FILE_TRANSFER_PATH = "/ics_file_transfer" // Must match Wear OS app
private const val WEAR_APP_CAPABILITY = "refwatch_wear_app"   // Must match Wear OS app's wear.xml
private const val DEBUG_ASSET_ICS_FILENAME = "referee_assignments.ics" // Name of the file in assets

// New data class to represent GameSettings, similar to what Wear OS might need
// This should eventually match or be translatable to your Wear OS GameSettings
data class GameSettingsForPhone(
    val id: String = java.util.UUID.randomUUID().toString(),
    val dateTime: String, // Formatted date and time
    val homeTeam: String,
    val awayTeam: String,
    val location: String,
    val description: String? = null,
    // Add other fields that your Wear OS GameSettings might have
    // e.g., halfDurationMillis, kickOffTeam (might be set later)
)

// Helper to read text content from URI
suspend fun readTextFromUri(context: Context, uri: Uri): String? { // Ensure this is defined
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading text from URI $uri", e)
            null
        }
    }
}

// Helper to read text content from Assets
suspend fun readTextFromAssets(context: Context, fileName: String): String? { // Ensure this is defined
    return withContext(Dispatchers.IO) {
        try {
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading text from assets file $fileName", e)
            null
        }
    }
}

// Helper to convert SimpleIcsEvents to GameSettingsForPhone
fun convertSimpleIcsToGameSettings(icsEvents: List<SimpleIcsEvent>): List<GameSettingsForPhone> { // Ensure this is defined
    val dateTimeOutputFormatter = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a")
    return icsEvents.mapNotNull { event ->
        if (event.summary == null || event.dtStart == null) {
            return@mapNotNull null
        }
        GameSettingsForPhone(
            dateTime = event.dtStart.format(dateTimeOutputFormatter),
            homeTeam = event.homeTeam ?: "Home Team?",
            awayTeam = event.awayTeam ?: "Away Team?",
            location = event.location ?: "Unknown Location",
            description = event.description
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RefWatchCompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompanionScreen()
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionScreen() {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var parsedGameSettingsList by remember { mutableStateOf(emptyList<GameSettings>()) }

    suspend fun parseIcsContentAndUpdateUi(icsContent: String?, source: String) {
        isLoading = true
        try {
            if (icsContent == null) {
                statusMessage = "Error: Could not read file content from $source."
                parsedGameSettingsList = emptyList()
                return
            }
            val simpleIcsEvents = SimpleIcsParser.parse(icsContent) // Assuming SimpleIcsParser is accessible
            val gameSettings = convertSimpleIcsToGameSettings(simpleIcsEvents, context) // Pass context if needed for defaults
            parsedGameSettingsList = gameSettings

            if (gameSettings.isNotEmpty()) {
                statusMessage = "ICS parsed successfully from $source: ${gameSettings.size} games found."
            } else {
                statusMessage = "No games found in the ICS file from $source or parsing failed."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing or processing ICS from $source", e)
            statusMessage = "Error parsing $source: ${e.localizedMessage}"
            parsedGameSettingsList = emptyList()
        } finally {
            isLoading = false
        }
    }


    // Function to handle processing the URI
    val processUriAndParse = { uri: Uri ->
        Log.d(TAG, "Processing ICS File: $uri")
        isLoading = true
        statusMessage = "Parsing ICS file..."
        parsedGameSettingsList  = emptyList()// Clear previous results

        coroutineScope.launch {
            try {
                val icsContent = readTextFromUri(context, uri)
                if (icsContent == null) {
                    statusMessage = "Error: Could not read file content."
                    isLoading = false
                    return@launch
                }

                val simpleIcsEvents = SimpleIcsParser.parse(icsContent)
                val gameSettingsList = convertSimpleIcsToGameSettings(simpleIcsEvents)
                parsedGameSettingsList  = gameSettingsList

                if (gameSettingsList.isNotEmpty()) {
                    statusMessage = "ICS parsed successfully: ${gameSettingsList.size} games found."
                    // NEXT STEP: Send gameSettingsList to Wear OS
                    // For now, we just display them.
                    // val sendSuccess = sendGameSettingsToWear(context, gameSettingsList)
                    // statusMessage = if(sendSuccess) "Games sent to watch!" else "Failed to send games to watch."
                } else {
                    statusMessage = "No games found in the ICS file or parsing failed."
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error parsing or processing ICS", e)
                statusMessage = "Error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                processUriAndParse(it)
                Log.d(TAG, "ICS File selected: $it")
                isLoading = true
                statusMessage = "Preparing to send file..."
                coroutineScope.launch { // Launch in the composable's scope
                    val success = sendIcsFileToWear(context, it)
                    isLoading = false
                    statusMessage = if (success) "ICS file sent successfully!" else "Failed to send ICS file."
                }
            } ?: run {
                Log.d(TAG, "No file selected.")
                statusMessage = "File selection cancelled."
            }
        }
    )

    // Function to handle loading from assets
    val loadFromAssetsAndParse = {
        Log.d(TAG, "Processing ICS File from Assets: $DEBUG_ASSET_ICS_FILENAME")
        isLoading = true
        statusMessage = "Reading ICS file from assets..."
        parsedGameSettingsList  = emptyList<GameSettingsForPhone>() // Be explicit when resetting

        coroutineScope.launch {
            val icsContent = readTextFromAssets(context, DEBUG_ASSET_ICS_FILENAME)
            parseIcsContentAndUpdateUi(icsContent, "assets")
        }
    }

    var debugAssetLoadAttempted by remember { mutableStateOf(false) }
    if (BuildConfig.DEBUG && !debugAssetLoadAttempted && !isLoading) {
        LaunchedEffect(Unit) {
            debugAssetLoadAttempted = true
            Toast.makeText(context, "DEBUG: Auto-processing ICS from assets", Toast.LENGTH_SHORT).show()
            loadFromAssetsAndParse()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "RefWatch Companion",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = {
                if (!isLoading) {
                    filePickerLauncher.launch("text/calendar")
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            if (isLoading && !statusMessage.toString().contains("DEBUG")) { // Avoid showing "Sending..." if debug auto-triggered
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("Sending...")
            } else {
                Text("Select & Send ICS Schedule")
            }
        }
        statusMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (it.contains("successfully")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = "Ensure your RefWatch app is installed on your Wear OS device and the device is connected.",
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ... (sendIcsFileToWear and RefWatchCompanionTheme remain the same)
// Make sure BuildConfig is imported: import com.databelay.refwatch.BuildConfig
// If it's not found, ensure your module's build.gradle has:
// android { ... buildFeatures { buildConfig = true } ... }

suspend fun sendIcsFileToWear(context: Context, fileUri: Uri): Boolean {
    // Running on a background thread is good practice for wearable API calls
    return withContext(Dispatchers.IO) {
        try {
            // 1. Find a connected Wear OS node that has your app
            val capabilityInfo = Wearable.getCapabilityClient(context)
                .getCapability(WEAR_APP_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()

            val connectedNode = capabilityInfo.nodes.firstOrNull { it.isNearby } ?: capabilityInfo.nodes.firstOrNull()
            if (connectedNode == null) {
                Log.e(TAG, "No connected Wear OS node found with capability '$WEAR_APP_CAPABILITY'.")
                withContext(Dispatchers.Main) { Toast.makeText(context, "Watch not connected or app not installed.", Toast.LENGTH_LONG).show() }
                return@withContext false
            }
            Log.d(TAG, "Found node: ${connectedNode.displayName} (${connectedNode.id})")


            // 2. Open a Channel
            val channelClient = Wearable.getChannelClient(context)
            val channel: ChannelClient.Channel = channelClient.openChannel(
                connectedNode.id,
                ICS_FILE_TRANSFER_PATH
            ).await()
            Log.d(TAG, "Channel opened to ${connectedNode.displayName}: ${channel.path}")

            // 3. Get an OutputStream from the Channel
            val outputStreamTask = channelClient.getOutputStream(channel)
            val outputStream: OutputStream = outputStreamTask.await() ?: run {
                Log.e(TAG, "Failed to get output stream for channel.")
                channelClient.close(channel).await()
                return@withContext false
            }


            // 4. Read the ICS file and write to the OutputStream
            var success = false
            outputStream.use { os -> // Use 'use' to ensure the stream is closed
                context.contentResolver.openInputStream(fileUri)?.use { inputStream: InputStream ->
                    Log.d(TAG, "Streaming file...")
                    val buffer = ByteArray(4096) // 4KB buffer
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                    }
                    os.flush()
                    Log.d(TAG, "File streaming complete.")
                    success = true
                } ?: run {
                    Log.e(TAG, "Could not open input stream for URI: $fileUri")
                    // Show a toast on the main thread for this specific error
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: Could not open file. Does it exist and is accessible? URI: $fileUri", Toast.LENGTH_LONG).show()
                    }
                }
            }

            // 5. Close the Channel (closing the output stream doesn't close the channel)
            channelClient.close(channel).await()
            Log.d(TAG, "Channel closed.")

            if (success) {
                Log.d(TAG, "File sent successfully.")
                return@withContext true
            } else {
                Log.e(TAG, "File sending failed (stream issue or file not found).")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sending ICS file", e)
            withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
            return@withContext false
        }
    }
}

@Composable
fun GameSettingsItem(game: GameSettings) { // Now takes the shared GameSettings
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("${game.homeTeamName} vs ${game.awayTeamName}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        game.formattedGameDateTime?.let {
            Text("Time: $it", style = MaterialTheme.typography.bodyMedium)
        }
        game.venue?.let {
            Text("Location: $it", style = MaterialTheme.typography.bodyMedium)
        }
        // You can add more details from GameSettings here
        // Row(verticalAlignment = Alignment.CenterVertically) {
        //     Box(modifier = Modifier.size(16.dp).background(game.homeTeamColor))
        //     Spacer(Modifier.width(4.dp))
        //     Text("Home", style = MaterialTheme.typography.bodySmall)
        //     Spacer(Modifier.width(8.dp))
        //     Box(modifier = Modifier.size(16.dp).background(game.awayTeamColor))
        //     Spacer(Modifier.width(4.dp))
        //     Text("Away", style = MaterialTheme.typography.bodySmall)
        // }
    }
}

// --- UPDATED CONVERTER FUNCTION ---
fun convertSimpleIcsToGameSettings(icsEvents: List<SimpleIcsEvent>, context: Context): List<GameSettings> {
    // Using context for potential default color access from themes if needed, though GameSettings uses ARGB directly
    return icsEvents.mapNotNull { event ->
        if (event.summary == null || event.dtStart == null) {
            return@mapNotNull null
        }

        // Convert LocalDateTime to epoch milliseconds (UTC)
        val gameDateTimeEpoch = event.dtStart
            .atZone(ZoneId.systemDefault()) // Assuming ICS dtStart was parsed to system default
            .withZoneSameInstant(ZoneId.of("UTC")) // Convert to UTC
            .toInstant()
            .toEpochMilli()

        GameSettings(
            // id can be default or from ICS if UID is present
            homeTeamName = event.homeTeam ?: "Home",
            awayTeamName = event.awayTeam ?: "Away",
            venue = event.location ?: "Unknown",
            gameDateTimeEpochMillis = gameDateTimeEpoch,
            // scheduledGameId = event.uid_from_ics_if_available, // If your SimpleIcsEvent could parse UID
            // Set default colors or parse them if available in description/location
            // homeTeamColorArgb = DefaultHomeColor.toArgb(), // Get DefaultHomeColor from your common theme or hardcode
            // awayTeamColorArgb = DefaultAwayColor.toArgb(),
            kickOffTeam = Team.HOME // Default, can be changed later
            // Add other fields from your GameSettings as needed
        )
    }
}

// Basic Material 3 Theme (you can customize this further)
@Composable
fun RefWatchCompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme( // Using androidx.compose.material3.MaterialTheme
        colorScheme = lightColorScheme( // Or darkColorScheme()
            primary = MaterialTheme.colors.primary, // Example from your theme,
            onPrimary = md_theme_light_onPrimary,
            secondary = md_theme_light_secondary, // Added for debug button example
            // ... define other colors from your theme
            background = md_theme_light_background,
            surface = md_theme_light_surface,
            error = md_theme_light_error
        ),
        typography = Typography, // Assuming you have a Typography.kt for M3
        content = content
    )
}