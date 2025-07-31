package com.databelay.refwatch.presentation.screens.pager // Example package

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text // Use Material 3 Text
import androidx.wear.compose.material.* // Use Wear Material
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.sp
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.formatTime
import com.databelay.refwatch.common.hasDuration
import com.databelay.refwatch.common.isPlayablePhase
import com.databelay.refwatch.common.readable
import androidx.compose.ui.tooling.preview.Preview
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.wear.presentation.components.ColorIndicator
import com.databelay.refwatch.wear.presentation.screens.TeamActionsPage

@Composable
fun MainGameDisplayScreen(
    game: Game,
    onKickOff: () -> Unit, // New callback for kickoff button
    modifier: Modifier = Modifier // General modifier
) {
    /*    onToggleTimer: () -> Unit, // Renamed from onPlayPauseClick for clarity
    onEndPhase: () -> Unit, // For ending phase early, if needed*/
    val TAG = "MainGameDisplayScreen"
    Column(
        modifier = modifier // Apply passed modifier first
            .fillMaxSize()    // Then ensure it fills the available space
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround // Distribute elements
    ) {
        Spacer(Modifier.weight(12f))
        // Score and Team Colors
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            val homeHasKickOff = game.kickOffTeam == Team.HOME &&
                    game.currentPhase.isPlayablePhase()

            ColorIndicator(
                color = game.homeTeamColor,
                hasKickOffBorder = homeHasKickOff,
                // You can also pass kickOffBorderWidth and kickOffBorderColor from here if they vary
            )
            Text(
                "${game.homeScore} - ${game.awayScore}",
                style = MaterialTheme.typography.display2, // Wear typography
                color = MaterialTheme.colors.onSurface,
                fontWeight = FontWeight.Bold
            )
            val awayHasKickOff = game.kickOffTeam == Team.AWAY &&
                    game.currentPhase.isPlayablePhase()
            ColorIndicator(
                color = game.awayTeamColor,
                hasKickOffBorder = awayHasKickOff
            )
        }

        // Current Phase
        Text(
            game.currentPhase.readable(),
            style = MaterialTheme.typography.title2,
            color = MaterialTheme.colors.secondary,
            textAlign = TextAlign.Center
        )
        // Conditionally show Timer or Kickoff Button
        if (game.actualTimeElapsedInPeriodMillis == 0L && !game.isTimerRunning && game.currentPhase.isPlayablePhase()) {
            // Kickoff Button for the start of the periods with kickoffs
            Spacer(modifier = Modifier.height(8.dp)) // Add some spacing before the button
            Button(
                onClick = onKickOff, // Use the new callback
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
            ) {
                Text(
                    "Kick Off",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Main Timer Display (shown if not kickoff or timer is running)
            if (game.currentPhase.hasDuration()) {
                Text(
                    text = game.displayedTimeMillis.formatTime(),
                    style = MaterialTheme.typography.display1,
                    fontSize = 56.sp, // Large timer
                    color = MaterialTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.weight(12f))
        // You might have a small text indicating "Long press for menu"
        Text(
            "Long press for menu",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.secondary)
    }
}

@Preview(device = "id:wearos_large_round", showSystemUi = true, backgroundColor = 0xff000000, showBackground = true)
@Composable
fun MainGameDisplayScreenPreviewLarge() {
    MainGameDisplayScreen(
        game = Game.defaults().copy( // Use your Game.defaults() or a sample game
            id = "previewGame",
            currentPhase = GamePhase.FIRST_HALF,
            homeTeamName = "Red Team",
            homeScore = 2
        ),
        onKickOff = {} // Example callback
    )
}
