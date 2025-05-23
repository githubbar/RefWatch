package com.databelay.refwatch.wear.presentation.screens // << MAKE SURE THIS MATCHES YOUR PACKAGE

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import androidx.compose.material3.HorizontalDivider
import com.databelay.refwatch.common.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun GameLogScreen(events: List<GameEvent>, onDismiss: () -> Unit) {
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    "Game Log",
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
            if (events.isEmpty()) {
                item { Text("No events yet.", style = MaterialTheme.typography.body2, textAlign = TextAlign.Center) }
            }
            items(events.asReversed()) { event -> // Show newest events first
                EventLogItem(event)
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f))
            }
            item {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(0.7f)
                ) {
                    Text("Back")
                }
            }
        }
    }
}


@Composable
fun EventLogItem(event: GameEvent) {
    // You might still want the wall clock for extra detail, or remove if displayString is enough
    val wallTimestampStr = remember(event.timestamp) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.format(Date(event.timestamp))
    }

    // The 'when' statement is no longer needed to build the description
    // if each event has a 'displayString'.

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = event.displayString, // <<<< SIMPLY USE THIS!
            style = MaterialTheme.typography.body2
        )
        // Optional: Keep wall clock time for more detailed logging if desired
        Text(
            text = "Logged: $wallTimestampStr",
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
    }
}
// Helper extension function if you placed it here or in a common utility file
// fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.lowercase().replaceFirstChar(Char::titlecase) }