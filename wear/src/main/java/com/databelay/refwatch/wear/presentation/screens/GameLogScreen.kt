package com.databelay.refwatch.wear.presentation.screens // << MAKE SURE THIS MATCHES YOUR PACKAGE

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GameEvent
import com.databelay.refwatch.common.PreviewTools.createFirstHalfSampleGame
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameLogScreen(
    game: Game,
    onDismiss: () -> Unit,
    onRemoveEvent: (event: GameEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val tag = "GameLogScreen"
    var activeDialogInfo: ConfirmationDialogInfo? by remember { mutableStateOf(null) }
    // Common logic for closing any dialog and animating back to the main page (if applicable)

    LaunchedEffect(game) {
        game.let {
            Log.d(tag, "Displaying Game ID: ${it.id}, Status: ${it.status}")
            Log.d(tag, "Scores: ${it.homeScore}-${it.awayScore}")
            Log.d(tag, "Events Count: ${it.events.size}")
        }
    }
    val listState = rememberScalingLazyListState()
    ScreenScaffold(
        scrollState = listState,
        scrollIndicator = { ScrollIndicator(state = listState) },
        modifier = modifier.fillMaxSize(),
    ) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            // Consume the scaffold's responsive padding so the first and last cards
            // are not clipped by the bezel on round screens.
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp), // Adjusted padding slightly for cards
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp) // Adjusted spacing for cards
        ) {
            item {
                ListHeader {
                    Text(
                        "Game Log",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp), // Added vertical padding
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (game.events.isEmpty()) {
                item {
                    Text(
                        "No events yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(
                    game.events.asReversed(),
                    key = { event -> event.timestamp }) { event -> // Show newest events first
                    EventLogItem(
                        event = event,
                        onLongClick = {
                            activeDialogInfo = ConfirmationDialogInfo.RemoveLogEvent(
                                onConfirm = { onRemoveEvent(event)},
                                onDialogClose = onDismiss
                            )
                        }
                    )
                    // HorizontalDivider removed as Cards provide separation
                }
            }
            item {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 10.dp) // Added bottom padding
                        .fillMaxWidth(0.7f)
                ) {
                    Text("Back",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // Unified Confirmation Dialog
    activeDialogInfo?.let { dialogInfo ->
        UnifiedConfirmationDialog(dialogInfo = dialogInfo)
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventLogItem(
    event: GameEvent,
    onLongClick: () -> Unit
) {
    val wallTimestampStr = remember(event.timestamp) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.format(Date(event.timestamp.toLong()))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.shapes.medium
            ) // Style like a button
            .clip(MaterialTheme.shapes.medium) // For ripple effect if combinedClickable provides one
            .combinedClickable(
                onClick = { Log.d("EventLogItem", "Short click on event: ${event.displayString}") },
                onLongClick = onLongClick,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp)
        ) {
            Text(
                text = event.displayString,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary // Ensure text is visible on Card
            )
            Text(
                text = "Logged: $wallTimestampStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            )
        }
    }
}

// --------------------------------------- Previews ----------------------------------------
@Preview(device = "id:wearos_small_round", showBackground = true)
@Preview(device = "id:wearos_square", showBackground = true)
@Preview(device = "id:wearos_large_round", showBackground = true)
@WearPreviewFontScales
@Composable
fun GameLogScreenPreview() {
    val sampleGame = createFirstHalfSampleGame()
    RefWatchWearTheme {
        GameLogScreen(
            game = sampleGame,
            onDismiss = { Log.d("Preview", "Dismiss clicked") },
            onRemoveEvent = { Log.d("Preview", "Undo event clicked") }
        )
    }
}
