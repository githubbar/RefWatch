package com.databelay.refwatch.wear.presentation.screens // Or your package

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.readable
import com.databelay.refwatch.common.PreviewTools.createFirstHalfSampleGame
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.presentation.components.ColorIndicator


@Composable
fun KickOffSelectionScreen(
    game: Game,
    onSetKickOffTeam: (Team) -> Unit
) {
    val scrollState = rememberScrollState()
    ScreenScaffold(
        scrollState = scrollState,
        scrollIndicator = { ScrollIndicator(state = scrollState) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Text(
            text = "${game.currentPhase.readable()} : Kick-Off",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium) // Add clip for visual feedback if needed
                    .clickable { onSetKickOffTeam(Team.HOME) }
                    .padding(8.dp) // Add padding to increase clickable area and for visual spacing
            ) {
                Text("Home", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                ColorIndicator(
                    color = game.homeTeamColor,
                    indicatorSize = 60.dp,
                    outlineWidth = 2.dp,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium) // Add clip for visual feedback if needed
                    .clickable { onSetKickOffTeam(Team.AWAY) }
                    .padding(8.dp) // Add padding to increase clickable area and for visual spacing
            ) {
                Text("Away", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                ColorIndicator(
                    color = game.awayTeamColor,
                    indicatorSize = 60.dp,
                    outlineWidth = 2.dp,
                )
            }
        }

        Spacer(Modifier.height(16.dp)) 
    }
}
}


// ------------------------ Previews -----------------------------
@Preview(device = "id:wearos_small_round", name = "KickOffSelection SmRnd", showBackground = true)
@Preview(device = "id:wearos_large_round", name = "KickOffSelection LrgRnd", showBackground = true)
@Preview(device = "id:wearos_square", name = "KickOffSelection Sqr", showBackground = true)
@WearPreviewFontScales
@Composable
fun KickOffSelectionScreenPreview_FirstHalf() {
    RefWatchWearTheme {
        KickOffSelectionScreen(
            game = createFirstHalfSampleGame(),
            onSetKickOffTeam = {} // Empty lambda for preview
        )
    }
}
