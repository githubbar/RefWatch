package com.databelay.refwatch.wear.presentation.screens // Or your chosen package

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.hasTimer
import com.databelay.refwatch.common.readable
import com.databelay.refwatch.common.theme.RefWatchWearTheme

@Composable
fun GameSettingsScreen(
    game: Game,
    collectPositionInfo: Boolean,
    onToggleCollectPositionInfo: (Boolean) -> Unit,
    onAttemptFinishGame: () -> Unit,
    onAttemptResetPeriodTimer: () -> Unit,
    onAttemptResetFullGame: () -> Unit,
    onViewLog: () -> Unit,
    onViewAnalytics: () -> Unit,
    onToggleTimer: () -> Unit,
    onAttemptEndPhase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(
        scrollIndicator = {
            ScrollIndicator(
                modifier = Modifier.align(Alignment.CenterStart),
                state = listState
            )
        },
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
    ) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = modifier
                .padding(horizontal = 8.dp, vertical = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {

            item {
                Text(
                    "Game Menu",
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center
                )
            }

            if (game.currentPhase.hasTimer()) {
                item {
                    Button(
                        onClick = onToggleTimer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (game.isTimerRunning) Color(0xFFFF6822) else Color.Green,
                            contentColor = Color.Black
                        ),
                    ) {
                        Icon(
                            imageVector = if (game.isTimerRunning) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                            contentDescription = if (game.isTimerRunning) "Pause Timer" else "Start Timer",
                        )
                    }
                }
                item {
                    Button(
                        onClick = onAttemptEndPhase,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),

                        ) {
                        Text(
                            text = "End ${game.currentPhase.readable()}",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onAttemptFinishGame,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Finish Game",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Button(
                    onClick = onAttemptResetPeriodTimer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Reset Period Timer",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }


            item {
                Button(
                    onClick = onViewLog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "View Game Log",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Button(
                    onClick = onViewAnalytics,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "View Analytics",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                CheckboxButton(
                    checked = collectPositionInfo,
                    onCheckedChange = onToggleCollectPositionInfo,
                    label = { Text("Collect Position") },
                    secondaryLabel = { Text("GPS Tracking") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    onClick = onAttemptResetFullGame,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Game")
                        Icon(
                            imageVector = Icons.Filled.PriorityHigh,
                            contentDescription = "Warning"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewableAlertDialog(
    title: String,
    message: String? = null,
    confirmButtonText: String = "Confirm",
    dismissButtonText: String = "Dismiss",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    RefWatchWearTheme { // Ensure the dialog is themed
        ConfirmationDialogInfo.FinishGame(
            onConfirm = { },
            onDialogClose = { }
        )
    }
}

@Preview(
    device = "id:wearos_small_round",
    name = "End Phase Dialog Preview",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun PreviewSettingsEndPhaseDialog() {
    PreviewableAlertDialog(
        title = "End First Half?",
        onConfirm = {},
        onDismiss = {}
    )
}

@Preview(
    device = "id:wearos_small_round",
    name = "Finish Game Dialog Preview",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun PreviewSettingsFinishGameDialog() {
    PreviewableAlertDialog(
        title = "Finish Game?",
        message = "Are you sure you want to end and save this game?",
        onConfirm = {},
        onDismiss = {}
    )
}

@Preview(
    device = "id:wearos_small_round",
    name = "Reset Timer Dialog Preview",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun PreviewSettingsResetPeriodTimerDialog() {
    PreviewableAlertDialog(
        title = "Reset Timer?",
        message = "Reset timer for First Half?",
        onConfirm = {},
        onDismiss = {}
    )
}

@Preview(
    device = "id:wearos_small_round",
    name = "Reset Full Game Dialog Preview",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun PreviewSettingsResetFullGameDialog() {
    PreviewableAlertDialog(
        title = "Reset Full Game?",
        message = "This will erase all scores and logs for this game. Are you sure?",
        confirmButtonText = "Yes, Reset",
        onConfirm = {},
        onDismiss = {}
    )
}

@Preview(
    device = "id:wearos_small_round",
    name = "Extra Time Dialog Preview",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun PreviewSettingsExtraTimeDialog() {
    PreviewableAlertDialog(
        title = "Extra Time?",
        confirmButtonText = "Yes",
        dismissButtonText = "No",
        onConfirm = {},
        onDismiss = {}
    )
}


@Preview(
    device = "id:wearos_small_round",
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun SettingsPageContentPreview() {
    RefWatchWearTheme {
        GameSettingsScreen(
            game = Game.defaults().copy(currentPhase = GamePhase.FIRST_HALF, isTimerRunning = true),
            collectPositionInfo = true,
            onToggleCollectPositionInfo = {},
            onAttemptFinishGame = {},
            onAttemptResetPeriodTimer = {},
            onAttemptResetFullGame = {},
            onViewLog = {},
            onViewAnalytics = {},
            onToggleTimer = {},
            onAttemptEndPhase = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
