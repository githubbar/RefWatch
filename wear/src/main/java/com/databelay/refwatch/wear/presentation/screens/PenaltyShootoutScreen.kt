// File: PenaltyShootoutScreen.kt
package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.isPlayablePhase
import com.databelay.refwatch.common.readable
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.presentation.components.ColorIndicator

@Composable
fun PenaltyShootoutScreen(
    game: Game, // Pass the necessary game state
    onPenaltyAttemptRecorded: (scored: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    ScreenScaffold(
        scrollState = scrollState,
        scrollIndicator = { ScrollIndicator(state = scrollState) },
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            // Score and Team Colors
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                val homeHasKickOff =
                    game.kickOffTeam == Team.HOME && game.currentPhase.isPlayablePhase()
                ColorIndicator(
                    color = game.homeTeamColor,
                    hasKickOffBorder = homeHasKickOff,
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(
                    "${game.homeScore} - ${game.awayScore}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                val awayHasKickOff =
                    game.kickOffTeam == Team.AWAY && game.currentPhase.isPlayablePhase()
                ColorIndicator(
                    color = game.awayTeamColor,
                    hasKickOffBorder = awayHasKickOff
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Current Phase
            Text(
                text = "${game.currentPhase.readable()}: ${game.penaltiesTakenHome} - ${game.penaltiesTakenAway}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onPenaltyAttemptRecorded(true) },
                    modifier = Modifier
                        .size(ButtonDefaults.LargeIconSize),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Y",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = "Scored?",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Button(
                    onClick = { onPenaltyAttemptRecorded(false) },
                    modifier = Modifier
                        .size(ButtonDefaults.LargeIconSize),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "N",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Display current penalty count (taken by each team)
            Text(
                text = "Taken: ${game.penaltiesTakenHome} | ${game.penaltiesTakenAway}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}
}

// --------------------------------------- Previews ----------------------------------------
// -----------------------------------------------------------------------------------------

@Preview(device = "id:wearos_small_round", showBackground = true)
@Preview(device = "id:wearos_large_round", showBackground = true)
@Preview(device = "id:wearos_square", showBackground = true)
@WearPreviewFontScales
@Composable
fun Preview_PenaltiShootout() {
    RefWatchWearTheme {
        PenaltyShootoutScreen(
            game = Game.defaults().copy(
                currentPhase = GamePhase.PENALTIES,
                homeTeamName = "Red Team",
                awayTeamName = "Blue Team",
                homeScore = 3,
                awayScore = 1,
                penaltiesTakenHome = 2,
                penaltiesTakenAway = 1,
            ),
            onPenaltyAttemptRecorded = {}
        )
    }
}
