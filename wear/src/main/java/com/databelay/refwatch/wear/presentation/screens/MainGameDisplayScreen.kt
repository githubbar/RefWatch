package com.databelay.refwatch.wear.presentation.screens // Example package

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.formatTime
import com.databelay.refwatch.common.hasTimer
import com.databelay.refwatch.common.isBreak
import com.databelay.refwatch.common.isPlayablePhase
import com.databelay.refwatch.common.readable
import com.databelay.refwatch.wear.presentation.components.ColorIndicator

@Composable
fun MainGameDisplayScreen(
    game: Game,
    onKickOff: () -> Unit, // New callback for kickoff button
    modifier: Modifier = Modifier // General modifier
) {
    val TAG = "MainGameDisplayScreen"
    val regulationDuration = game.regulationPeriodDurationMillis() // From Game data class
    // Determine if we are in "added time" for a playable phase
    val isPlayablePhaseAndInAddedTime = game.currentPhase.isPlayablePhase() &&
            game.actualTimeElapsedInPeriodMillis >= regulationDuration &&
            regulationDuration > 0 // Ensure regulation duration is positive to avoid division by zero or weird states

    val addedTime = game.actualTimeElapsedInPeriodMillis - regulationDuration
    val timerTextStyle = MaterialTheme.typography.display1.copy(
        fontSize = 50.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.onSurface // Default/fallback color
    )
    val displayTimerText = buildAnnotatedString {
        if (game.currentPhase.isPlayablePhase()) {
            if (isPlayablePhaseAndInAddedTime) {
                    append(regulationDuration.formatTime())
                    append(" \n")
                    withStyle(style = SpanStyle(color = Color.Red, fontSize = 36.sp)) { // This RED overrides timerTextStyle.color
                        append("+ ${addedTime.formatTime()}")
                    }
            } else {
                // In regulation
                val timeRemaining = regulationDuration - game.actualTimeElapsedInPeriodMillis
                append(timeRemaining.formatTime())
            }
        } else if (game.currentPhase.isBreak()) {
            // For breaks, display time remaining in the break
            val breakDuration = game.regulationPeriodDurationMillis() // Use your VM function
            val timeRemainingInBreak = breakDuration - game.actualTimeElapsedInPeriodMillis
            if (timeRemainingInBreak > 0)
                append(timeRemainingInBreak.formatTime())
            else {
                withStyle(style = SpanStyle(color = Color.Red)) {
                    append(game.regulationPeriodDurationMillis().formatTime())
                }
                withStyle(style = SpanStyle(color = Color.Red, fontSize = 36.sp)) {
                    append("\nis over")
                }
            }
        }
        else {
            // Pre-game, ended, etc.
            append("") // Or "--:--"
        }
    }

    Column(
        modifier = modifier // Apply passed modifier first
            .fillMaxSize()    // Then ensure it fills the available space
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround // Distribute elements
    ) {
        Spacer(Modifier.weight(4f))
        // Score and Team Colors
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            val homeHasKickOff = game.kickOffTeam == Team.HOME &&
                    game.currentPhase.isPlayablePhase()
            // FIXME: back swipe from home team exits the app (disable)
            // FIXME: at fulltime disable back navigation
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
            style = MaterialTheme.typography.title3,
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
            if (game.currentPhase.hasTimer()) {
                Text(
                    text = displayTimerText,
                    minLines = 2,
                    style = timerTextStyle // NO separate color, fontSize etc. here
                )
            }
        }
        // You might have a small text indicating "Long press for menu"
        Text(
            "Long press for menu",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.secondary
        )
        Spacer(Modifier.weight(4f))
    }
}

@Preview(device = "id:wearos_large_round", showSystemUi = true, backgroundColor = 0xff000000, showBackground = true)
@Composable
fun MainGameDisplayScreenPreviewLargeAddedTime() {
    MainGameDisplayScreen(
        game = Game.defaults().copy( // Use your Game.defaults() or a sample game
            id = "previewGame",
            currentPhase = GamePhase.FIRST_HALF,
            homeTeamName = "Red Team",
            awayTeamName = "Blue Team",
            homeTeamColorArgb = android.graphics.Color.BLACK,
            awayTeamColorArgb = android.graphics.Color.YELLOW,
            kickOffTeam = Team.AWAY,
            actualTimeElapsedInPeriodMillis = (45 * 60000L) + (2 * 60000L) + 15000L, // e.g., 45min + 2min 15sec

            halfDurationMinutes = 45,
            homeScore = 2
        ),
        onKickOff = {} // Example callback
    )
}

@Preview(device = "id:wearos_large_round", showSystemUi = true, backgroundColor = 0xff000000, showBackground = true)
@Composable
fun MainGameDisplayScreenPreviewLargeRegulationTime() {
    MainGameDisplayScreen(
        game = Game.defaults().copy( // Use your Game.defaults() or a sample game
            id = "previewGame",
            currentPhase = GamePhase.FIRST_HALF,
            homeTeamName = "Red Team",
            awayTeamName = "Blue Team",
            homeTeamColorArgb = android.graphics.Color.BLACK,
            awayTeamColorArgb = android.graphics.Color.YELLOW,
            kickOffTeam = Team.AWAY,
            actualTimeElapsedInPeriodMillis = (5 * 60000L) + (2 * 60000L) ,
            halfDurationMinutes = 45,
            homeScore = 2
        ),
        onKickOff = {} // Example callback
    )
}

@Preview(device = "id:wearos_large_round", showSystemUi = true, backgroundColor = 0xff000000, showBackground = true)
@Composable
fun MainGameDisplayScreenPreviewLargeHalftime() {
    MainGameDisplayScreen(
        game = Game.defaults().copy( // Use your Game.defaults() or a sample game
            id = "previewGame",
            currentPhase = GamePhase.HALF_TIME,
            homeTeamName = "Red Team",
            awayTeamName = "Blue Team",
            homeTeamColorArgb = android.graphics.Color.BLACK,
            awayTeamColorArgb = android.graphics.Color.RED,
            kickOffTeam = Team.HOME,
            actualTimeElapsedInPeriodMillis = (25 * 60000L) +5000L,
            halftimeDurationMinutes = 15,
            awayScore = 3
        ),
        onKickOff = {} // Example callback
    )
}