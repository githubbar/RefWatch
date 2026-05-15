package com.databelay.refwatch.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GameEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import com.databelay.refwatch.common.calculateAverageHeartRate
import com.databelay.refwatch.common.calculateTotalDistanceMeters
import com.databelay.refwatch.common.calculateTotalSteps
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.TileOverlay as TileOverlayComposable
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng

import androidx.compose.ui.platform.LocalContext
import com.databelay.refwatch.common.isGoogleMapsAvailable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameLogScreen(
    game: Game?,
    navController: NavController
) {
    val context = LocalContext.current
    val mapsAvailable = remember(context) { isGoogleMapsAvailable(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Log") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (game == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Error: Game not found.")
            }
            return@Scaffold
        }

        val totalDistanceMeters = game.locationHistory.calculateTotalDistanceMeters()
        val totalMiles = totalDistanceMeters / 1609.34
        val totalSteps = game.stepHistory.calculateTotalSteps()
        val avgHR = game.heartRateHistory.calculateAverageHeartRate()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Header item with the final score
            item {
                Text(
                    text = "${game.homeTeamName} vs ${game.awayTeamName}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Final Score: ${game.homeScore} - ${game.awayScore}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HorizontalDivider()
            }

            // Analytics Section
            item {
                Text(
                    "Analytics",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    if (mapsAvailable) {
                        StatCard(Modifier.weight(1f), "Miles", "%.2f".format(totalMiles))
                    }
                    StatCard(Modifier.weight(1f), "Steps", "$totalSteps")
                    StatCard(Modifier.weight(1f), "Avg HR", "%.0f".format(avgHR))
                }
            }

            // Map Section
            if (mapsAvailable && game.locationHistory.isNotEmpty()) {
                item {
                    Text(
                        "Movement Map",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    val firstLocation = game.locationHistory.first()
                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(
                            LatLng(firstLocation.latitude, firstLocation.longitude),
                            16f
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(250.dp).padding(bottom = 16.dp)) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState
                        ) {
                            val heatmapProvider = remember(game.locationHistory) {
                                HeatmapTileProvider.Builder()
                                    .weightedData(game.locationHistory.map { WeightedLatLng(LatLng(it.latitude, it.longitude)) })
                                    .radius(20)
                                    .build()
                            }
                            TileOverlayComposable(tileProvider = heatmapProvider)
                            
                            Polyline(
                                points = game.locationHistory.map { LatLng(it.latitude, it.longitude) },
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                width = 2f
                            )
                        }
                    }
                }

                item {
                    Text(
                        "Positioning Heatmap (Field View)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    SoccerFieldHeatmap(
                        locationHistory = game.locationHistory,
                        isAssistantReferee = game.refereeAssignment?.contains("Assistant", ignoreCase = true) == true
                    )
                }
            }

            item {
                Text(
                    "Event Log",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // List of all game events
            items(game.events) { event ->
                GameLogItem(event = event)
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier, label: String, value: String) {
    Card(modifier = modifier) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GameLogItem(event: GameEvent) {
    // Format the wall-clock timestamp for display
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTimestamp = remember(event.timestamp) { sdf.format(Date(event.timestamp.toLong())) }

    ListItem(
        headlineContent = { Text(event.displayString, fontWeight = FontWeight.Medium) },
        supportingContent = { Text("Event time: $formattedTimestamp") }
    )
}