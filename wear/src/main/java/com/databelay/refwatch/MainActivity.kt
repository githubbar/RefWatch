package com.databelay.refwatch

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

@AndroidEntryPoint // Add if using Hilt for this Activity or ViewModels obtained via hiltViewModel()
class MainActivity : ComponentActivity() {

    // If WearGameViewModel is @HiltViewModel, you'd typically get it in Composable scope.
    // If not using Hilt or if Activity needs direct access (less common with Compose):
    // private val gameViewModel: WearGameViewModel by viewModels()

    private val GAME_SETTINGS_TRANSFER_PATH = "/game_settings_data" // Matches phone
    private val TAG_WEAR_ACTIVITY = "RefWatchWearActivity"

    private lateinit var channelClient: ChannelClient
    private var channelCallback: ChannelClient.ChannelCallback? = null
    private val activityScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) // For UI-related coroutines in Activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channelClient = Wearable.getChannelClient(this)

        setContent {
            RefWatchWearTheme {
                // gameViewModel is now obtained via hiltViewModel() in RefWatchWearApp
                RefWatchWearApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerChannelCallback()
    }

    override fun onPause() {
        super.onPause()
        unregisterChannelCallback()
    }

    private fun registerChannelCallback() {
        if (channelCallback != null) {
            Log.d(TAG_WEAR_ACTIVITY, "ChannelCallback already registered.")
            return
        }

        channelCallback = object : ChannelClient.ChannelCallback() {
            override fun onChannelOpened(channel: ChannelClient.Channel) {
                super.onChannelOpened(channel)
                Log.d(TAG_WEAR_ACTIVITY, "Channel opened: ${channel.path}")

                if (channel.path == GAME_SETTINGS_TRANSFER_PATH) {
                    // It's better to pass the Application context to the ViewModel
                    // and let the ViewModel handle the data processing and storage.
                    // This avoids holding a direct ViewModel reference in the Activity if using Hilt for ViewModels.
                    // For now, if not using Hilt for VM in Activity:
                    // val gameViewModelInstance: WearGameViewModel by viewModels() // this would create a new one if not careful
                    // Instead, we'll assume WearGameViewModel is a singleton or accessible via Hilt in Composable.
                    // The data received here needs to be funnelled to the active ViewModel.
                    // One way is to use a SharedFlow/EventBus or have ViewModel expose a method.
                    // For simplicity, if your ViewModel is a singleton or easily accessible for this:
                    // This example assumes you'll get the ViewModel instance correctly later.
                    // A better way would be for the service/listener (if not in Activity) to update storage,
                    // and ViewModel observes storage.

                    // Since we're in Activity, we can obtain the ViewModel instance associated
                    // with the Activity's lifecycle if WearGameViewModel is @HiltViewModel
                    // For this specific ChannelClient callback which is tied to Activity lifecycle,
                    // it's okay to get it here for this task, but be mindful of context.
                    // However, a service is better for background data reception.
                    // Let's assume RefWatchWearApp gets the ViewModel and we need to signal it.
                    // For this direct example:
                    val gameViewModelForCallback: WearGameViewModel by viewModels()


                    activityScope.launch { // Use Activity's scope
                        try {
                            Log.d(TAG_WEAR_ACTIVITY, "Processing data from channel: ${channel.path}")
                            val inputStream = channelClient.getInputStream(channel).await()
                            inputStream.use { stream ->
                                InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                                    val jsonString = reader.readText()
                                    Log.d(TAG_WEAR_ACTIVITY, "Received JSON ($jsonString)")
                                    if (jsonString.isNotEmpty()) {
                                        // Use kotlinx.serialization.json.Json
                                        val gamesList = Json {
                                            ignoreUnknownKeys = true
                                        }.decodeFromString<List<Game>>(jsonString)
                                        Log.d(TAG_WEAR_ACTIVITY, "Decoded ${gamesList.size} games from channel.")
                                        gameViewModelForCallback.updateScheduledGames(gamesList) // Update ViewModel
                                    } else {
                                        Log.w(TAG_WEAR_ACTIVITY, "Received empty data for game settings via channel.")
                                        gameViewModelForCallback.updateScheduledGames(emptyList()) // Clear if empty received
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG_WEAR_ACTIVITY, "Error receiving/processing game settings from channel", e)
                        } finally {
                            Log.d(TAG_WEAR_ACTIVITY, "Closing channel from receiver side: ${channel.path}")
                            channelClient.close(channel).await() // Receiver should close its end
                        }
                    }
                } else {
                    Log.w(TAG_WEAR_ACTIVITY, "Channel opened for unknown path: ${channel.path}, closing.")
                    activityScope.launch { channelClient.close(channel).await() }
                }
            }

            override fun onInputClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
                super.onInputClosed(channel, closeReason, appSpecificErrorCode)
                Log.d(TAG_WEAR_ACTIVITY, "Input closed for channel: ${channel.path}, reason: $closeReason (usually indicates sender closed output)")
            }

            override fun onChannelClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
                super.onChannelClosed(channel, closeReason, appSpecificErrorCode)
                Log.d(TAG_WEAR_ACTIVITY, "Channel closed: ${channel.path}, reason: $closeReason")
            }
        }
        channelClient.registerChannelCallback(channelCallback!!)
        Log.i(TAG_WEAR_ACTIVITY, "ChannelCallback registered.")
    }

    private fun unregisterChannelCallback() {
        channelCallback?.let {
            try {
                channelClient.unregisterChannelCallback(it)
                Log.i(TAG_WEAR_ACTIVITY, "ChannelCallback unregistered.")
            } catch (e: Exception) {
                Log.w(TAG_WEAR_ACTIVITY, "Error unregistering ChannelCallback: ${e.message}")
            } finally {
                channelCallback = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // activityScope.cancel() // Cancel coroutines if any are long-running
    }
}