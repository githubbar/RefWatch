// File: PenaltyShootoutScreen.kt
package com.databelay.refwatch.presentation.screens.pager // Or your preferred package

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text // Using Material 3 Text
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults // For Wear Button colors
import androidx.wear.compose.material.MaterialTheme // For Wear MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.isPlayablePhase
import com.databelay.refwatch.wear.presentation.components.ColorIndicator
import kotlin.text.take
import kotlin.text.uppercase

@Composable
fun PenaltyShootoutScreen(
    game: Game, // Pass the necessary game state
    onPenaltyAttemptRecorded: (scored: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp), // Added some padding for the dedicated screen
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center // Center content for this dedicated screen
    ) {
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

        val takerName = if (game.kickOffTeam == Team.HOME) game.homeTeamName else game.awayTeamName
        val takerAbbreviation = takerName.take(3).uppercase()

        Text(
            text = "Scored?",
            style = MaterialTheme.typography.display2,
            color = MaterialTheme.colors.onSurface,

            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp) // Increased bottom padding
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onPenaltyAttemptRecorded(true) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp) // Added more padding for larger tap targets
                    .height(ButtonDefaults.LargeButtonSize), // Use standard Wear button size
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
            ) {
                Text("Yes", style = MaterialTheme.typography.button)
            }

            Button(
                onClick = { onPenaltyAttemptRecorded(false) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(ButtonDefaults.LargeButtonSize), // Use standard Wear button size
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.error
                )
            ) {
                Text("No", style = MaterialTheme.typography.button)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display current penalty count (taken by each team)
        Text(
            text = "Taken: H: ${game.penaltiesTakenHome} | A: ${game.penaltiesTakenAway}",
            style = MaterialTheme.typography.body1, // Slightly larger than caption
            textAlign = TextAlign.Center
        )

        // Display current score during penalties
        Text(
            text = "Score: ${game.homeScore} - ${game.awayScore}",
            style = MaterialTheme.typography.title3,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
