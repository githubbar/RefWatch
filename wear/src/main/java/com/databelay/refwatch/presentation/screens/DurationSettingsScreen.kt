package com.databelay.refwatch.presentation.screens // Adjust if your package is different

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.databelay.refwatch.common.theme.WearTypography


//@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun DurationSettingsScreen(
    halfDurationMinutes: Long,
    halftimeDurationMinutes: Long,
    onHalfDurationChanged: (Long) -> Unit,
    onHalftimeDurationChanged: (Long) -> Unit,
    onConfirm: () -> Unit
) {
    val listState = rememberScalingLazyListState()

    // Define ranges and steps
    val halfDurationRange = 5f..60f
    val halfDurationStep = 5
    val halftimeDurationRange = 1f..30f
    val halftimeDurationStep = 1

    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            item {
                Text(
                    "Time Settings",
                    style = WearTypography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Half Duration Setting
            item {
                Text(
                    "Half Duration: ${halfDurationMinutes} min",
                    textAlign = TextAlign.Center
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Button(
                        onClick = {
                            val newValue = (halfDurationMinutes - halfDurationStep).coerceIn(
                                halfDurationRange.start.toLong(),
                                halfDurationRange.endInclusive.toLong()
                            )
                            onHalfDurationChanged(newValue)
                        },
                        enabled = halfDurationMinutes > halfDurationRange.start
                    ) { Icon(Icons.Filled.Remove, "Decrease Half Duration") }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "$halfDurationMinutes",
                        style = MaterialTheme.typography.title2,
                        modifier = Modifier.width(50.dp), // Give it some fixed width for stability
                        textAlign = TextAlign.Center
                    )
                    // InlineSlider can be an option, but +/- buttons are often easier on Wear
                    /*
                InlineSlider(
                    value = halfDurationMinutes.toFloat(),
                    onValueChange = { onHalfDurationChanged(it.toLong()) },
                    valueRange = halfDurationRange,
                    steps = ((halfDurationRange.endInclusive - halfDurationRange.start) / halfDurationStep).toInt() - 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                */
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val newValue = (halfDurationMinutes + halfDurationStep).coerceIn(
                                halfDurationRange.start.toLong(),
                                halfDurationRange.endInclusive.toLong()
                            )
                            onHalfDurationChanged(newValue)
                        },
                        enabled = halfDurationMinutes < halfDurationRange.endInclusive
                    ) { Icon(Icons.Filled.Add, "Increase Half Duration") }
                }
            }

            // Halftime Duration Setting
            item {
                Text(
                    "Halftime: ${halftimeDurationMinutes} min",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Button(
                        onClick = {
                            val newValue =
                                (halftimeDurationMinutes - halftimeDurationStep).coerceIn(
                                    halftimeDurationRange.start.toLong(),
                                    halftimeDurationRange.endInclusive.toLong()
                                )
                            onHalftimeDurationChanged(newValue)
                        },
                        enabled = halftimeDurationMinutes > halftimeDurationRange.start
                    ) { Icon(Icons.Filled.Remove, "Decrease Halftime Duration") }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "$halftimeDurationMinutes",
                        style = MaterialTheme.typography.title2,
                        modifier = Modifier.width(50.dp),
                        textAlign = TextAlign.Center
                    )
                    /*
               InlineSlider(
                   value = halftimeDurationMinutes.toFloat(),
                   onValueChange = { onHalftimeDurationChanged(it.toLong()) },
                   valueRange = halftimeDurationRange,
                   steps = ((halftimeDurationRange.endInclusive - halftimeDurationRange.start) / halftimeDurationStep).toInt() - 1,
                   modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
               )
               */
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val newValue =
                                (halftimeDurationMinutes + halftimeDurationStep).coerceIn(
                                    halftimeDurationRange.start.toLong(),
                                    halftimeDurationRange.endInclusive.toLong()
                                )
                            onHalftimeDurationChanged(newValue)
                        },
                        enabled = halftimeDurationMinutes < halftimeDurationRange.endInclusive
                    ) { Icon(Icons.Filled.Add, "Increase Halftime Duration") }
                }
            }

            item {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.padding(top = 20.dp).fillMaxWidth(0.7f)
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}