package com.databelay.refwatch.wear.presentation.screens // Or your chosen package

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog
import com.databelay.refwatch.BuildConfig
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.hasTimer
import com.databelay.refwatch.common.readable

// TODO: move build version text to common gradle, move text to main game list and in phone app too.
// Function to get the application's version name
fun getAppVersionName(context: Context): String {
    return try {
        val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName ?: "N/A" // Return "N/A" or some default if null
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
        "N/A" // Or handle the exception as appropriate
    }
}

// Function to get the application's version code
fun getAppVersionCode(context: Context): Long { // Or Int if you don't expect very large version codes
    return try {
        val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.longVersionCode
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
        -1L // Or handle the exception as appropriate
    }
}

@Composable
fun GameSettingsDialog(
    game: Game,
    onDismiss: () -> Unit,
    onFinishGame: () -> Unit,
    onResetPeriodTimer: () -> Unit,
    onViewLog: () -> Unit,
    onToggleTimer: () -> Unit,
    onEndPhase: () -> Unit,
) {
    val context = LocalContext.current // Get context
    var appVersionName by remember { mutableStateOf("Loading...") } // State for version name
    var appVersionNumber by remember { mutableLongStateOf(0L) } // State for version name
    val buildDateString = BuildConfig.BUILD_TIME

    // LaunchedEffect to get version name (it's a synchronous call but good practice
    // if it were asynchronous, and keeps UI responsive during initial composition)
    LaunchedEffect(Unit) {
        appVersionName = getAppVersionName(context)
        appVersionNumber = getAppVersionCode(context)
    }

    Dialog(
        showDialog = true,
        onDismissRequest = onDismiss
    ) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp, horizontal = 16.dp), // Adjusted padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            item {
                Text("Menu", style = MaterialTheme.typography.title3)
            }

            // Play/Pause Button - only if game is active (not PRE_GAME or FULL_TIME)
            if (game.currentPhase.hasTimer()) {
                item {
                    // Start/Pause Button
                    Button(
                        onClick = onToggleTimer,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.Green
                        ),
                        modifier = Modifier.size(ButtonDefaults.LargeButtonSize)
                    ) {
                        Icon(
                            imageVector = if (game.isTimerRunning) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                            contentDescription = if (game.isTimerRunning) "Pause Timer" else "Start Timer",
                            modifier = Modifier.size(ButtonDefaults.LargeIconSize)
                        )
                    }
                }
            // End Phase Early Button
                item {
                    Button(
                        onClick = onEndPhase,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red), // Or a distinct color
                        modifier = Modifier.fillMaxWidth(),
                        ) {
                        Text(
                            text = "End ${game.currentPhase.readable()}",
                            textAlign = TextAlign.Center
                        ) // Adding text
                    }
                }
            }
            item { // Finish Game Button
                Button(
                    onClick = onFinishGame,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Finish Game")}
            }

            item { // Reset/End Game Button
                Button(
                    onClick = onResetPeriodTimer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset Period Timer")}
            }

            item {
                Button(
                    onClick = onViewLog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View Game Log")}
            }
            item {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Close Menu")
                }
            }
            // --- ADD BUILD INFO TEXT HERE ---
            item {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    text = "Version: $appVersionName $buildDateString", // Display version name
                    color = MaterialTheme.colors.primary,
                    style = MaterialTheme.typography.caption1.copy(fontSize = 14.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // --- END BUILD INFO TEXT ---
        }
    }
}


@Preview(device = "id:wearos_large_round", showSystemUi = true, backgroundColor = 0xff000000, showBackground = true)
@Composable
fun GameSettingsDialogPreview_TimerRunning() {
    MaterialTheme {
        GameSettingsDialog(
            game = Game.defaults().copy( // Use your Game.defaults() or a sample game
                id = "previewGame",
                currentPhase = GamePhase.FIRST_HALF,
                homeTeamName = "Red Team",
                awayTeamName = "Blue Team",
                homeTeamColorArgb = android.graphics.Color.BLACK,
                awayTeamColorArgb = android.graphics.Color.YELLOW,
                kickOffTeam = Team.AWAY,
                actualTimeElapsedInPeriodMillis = (5 * 60000L) + (2 * 60000L),
                halfDurationMinutes = 45,
                homeScore = 2
            ),
            onDismiss = {}, // Empty lambda for preview
            onFinishGame = {},
            onResetPeriodTimer = {},
            onViewLog = {},
            onToggleTimer = {},
            onEndPhase = {}
        )
    }
}
