package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.databelay.refwatch.common.*

@Composable
fun GameAnalyticsScreen(
    game: Game,
    collectPositionInfo: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    
    val totalDistanceMeters = if (collectPositionInfo) game.locationHistory.calculateTotalDistanceMeters() else 0.0
    val totalMiles = totalDistanceMeters / 1609.34
    val totalSteps = game.stepHistory.calculateTotalSteps()
    val avgHR = game.heartRateHistory.calculateAverageHeartRate()
    val currentHR = game.heartRateHistory.lastOrNull()?.bpm ?: 0.0

    ScreenScaffold(
        scrollIndicator = {
            ScrollIndicator(state = listState)
        },
        modifier = modifier
    ) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ListHeader {
                    Text("Game Analytics", style = MaterialTheme.typography.titleSmall)
                }
            }
            
            if (collectPositionInfo) {
                item {
                    AnalyticsCard(
                        label = "Distance",
                        value = "%.2f mi".format(totalMiles),
                        secondaryValue = "%.0f m".format(totalDistanceMeters)
                    )
                }
            }
            
            item {
                AnalyticsCard(
                    label = "Steps",
                    value = "$totalSteps",
                    secondaryValue = "steps"
                )
            }
            
            item {
                AnalyticsCard(
                    label = "Heart Rate",
                    value = "%.0f bpm".format(currentHR),
                    secondaryValue = "Avg: %.0f".format(avgHR)
                )
            }

            item {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth(0.7f)
                ) {
                    Text("Back", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun AnalyticsCard(label: String, value: String, secondaryValue: String) {
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.displaySmall)
            Text(secondaryValue, style = MaterialTheme.typography.bodySmall)
        }
    }
}
