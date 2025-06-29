package com.databelay.refwatch.wear.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.databelay.refwatch.common.Team

@Composable
fun TeamActionsPage(
    team: Team,
    teamColor: Color,
    onAddGoal: () -> Unit,
    onLogCard: () -> Unit
)
{
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),

    ) {
        item {
            ListHeader {
                Text("${team.name} Actions", color = teamColor)
            }
        }
        item {
            Button(
                onClick = onAddGoal,
                modifier = Modifier.fillMaxWidth(0.85f),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Green)
            ) {
                Text("+1 Goal")
            }
        }
        item {
            Button(
                onClick = onLogCard,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text("Issue Card")
            }
        }
    }
}