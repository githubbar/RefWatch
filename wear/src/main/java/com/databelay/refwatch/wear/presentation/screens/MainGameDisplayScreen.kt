package com.databelay.refwatch.wear.presentation.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.formatTime
import com.databelay.refwatch.common.hasTimer
import com.databelay.refwatch.common.isBreak
import com.databelay.refwatch.common.isPlayablePhase
import com.databelay.refwatch.common.readable
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.presentation.components.ColorIndicator
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.graphics.Color as ComposeColor


@Composable
fun MainGameDisplayScreen(
    game: Game,
    onKickOff: () -> Unit, // New callback for kickoff button
    modifier: Modifier = Modifier // General modifier
) {
    val tag = "MainGameDisplayScreen"
    val regulationDuration = game.regulationPeriodDurationMillis() // From Game data class
    // Determine if we are in "added time" for a playable phase
    val isPlayablePhaseAndInAddedTime = game.currentPhase.isPlayablePhase() &&
            game.actualTimeElapsedInPeriodMillis >= regulationDuration &&
            regulationDuration > 0 // Ensure regulation duration is positive to avoid division by zero or weird states

    val addedTime = game.actualTimeElapsedInPeriodMillis - regulationDuration
    val parStyle = ParagraphStyle(
        lineBreak = LineBreak.Simple,
        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
        textAlign = TextAlign.Center
    )
    val mainTimerStyle = MaterialTheme.typography.displayLarge.toSpanStyle().copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface // Default/fallback color
    )
    val halfTimerStyle = MaterialTheme.typography.displaySmall.toSpanStyle().copy()
    val extraTimerStyle = MaterialTheme.typography.bodyLarge.toSpanStyle().copy(
        color = ComposeColor.Red,
    )
    val displayTimerText = remember(
        game.currentPhase,
        game.actualTimeElapsedInPeriodMillis,
        game.inAddedTime
    ) {
        buildAnnotatedString {
            withStyle(parStyle) {
                if (game.currentPhase.isPlayablePhase()) {
                    if (isPlayablePhaseAndInAddedTime) {
                        withStyle(mainTimerStyle) {
                            append(regulationDuration.formatTime())
                        }
                        append(" \n")
                        withStyle(extraTimerStyle) {
                            append("+ ${addedTime.formatTime()}")
                        }
                    } else {
                        // In regulation
                        val timeRemaining = regulationDuration - game.actualTimeElapsedInPeriodMillis
                        withStyle(mainTimerStyle) {
                            append(timeRemaining.formatTime())
                        }
                    }
                } else if (game.currentPhase.isBreak()) {
                    // For breaks, display time remaining in the break
                    val breakDuration = game.regulationPeriodDurationMillis() // Use your VM function
                    val timeRemainingInBreak = breakDuration - game.actualTimeElapsedInPeriodMillis
                    if (timeRemainingInBreak > 0)
                        withStyle(halfTimerStyle) {
                            append(timeRemainingInBreak.formatTime())
                        }
                    else {
                        withStyle(extraTimerStyle) {
                            append(game.regulationPeriodDurationMillis().formatTime())
                            append("\nis over")
                        }
                    }
                } else {
                    // Pre-game, ended, etc.
                    append("") // Or "--:--"
                }
            }
        }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        val currentTime = remember(game.actualTimeElapsedInPeriodMillis / 1000) { // Update once per second
            timeFormatter.format(java.util.Date())
        }

        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround
        ) {
        Spacer(modifier = Modifier.height(1.dp))
        // Time display if TimeText isn't sufficient or for specific styling
        Text(
            text = currentTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
            // Score and Team Colors
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                // ... content of score row (ColorIndicator, Score Text) ...
                val homeHasKickOff =
                    game.kickOffTeam == Team.HOME && game.currentPhase.isPlayablePhase()
                ColorIndicator(
                    color = game.homeTeamColor,
                    hasKickOffBorder = homeHasKickOff,
                )
                Text(
                    "${game.homeScore} - ${game.awayScore}",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                val awayHasKickOff =
                    game.kickOffTeam == Team.AWAY && game.currentPhase.isPlayablePhase()
                ColorIndicator(
                    color = game.awayTeamColor,
                    hasKickOffBorder = awayHasKickOff
                )
            }
            // Current Phase
            Text(
                game.currentPhase.readable(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.padding(1.dp))

            // Conditionally show Timer or Kickoff Button
            // Using a Box to center its content if the content itself doesn't fill width

            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (game.actualTimeElapsedInPeriodMillis == 0L && !game.isTimerRunning && game.currentPhase.isPlayablePhase()) {
                    Button(
                        onClick = onKickOff,
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(horizontal = 32.dp),
                    ) {
                        Text(
                            "Kick Off",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }

                } else {
                    if (game.currentPhase.hasTimer()) {
                        Text(
                            text = displayTimerText, // Your AnnotatedString for the timer
                            style = TextStyle( // Apply base text style, specific spans override parts
                                fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                                textAlign = TextAlign.Center
                                // Line height and font sizes are in your displayTimerText's ParagraphStyle/SpanStyles
                            ),
                        )
                    } else {
                        // Consistent placeholder height if no timer
                        Spacer(modifier = Modifier.height(50.dp)) // Adjust to match approx. timer height
                    }
                }
            }
            Spacer(modifier = Modifier.padding(8.dp))
        }
    }
}

// --------------------------------------- Previews ----------------------------------------
// -----------------------------------------------------------------------------------------

// Helper function to create a base game for previews
private fun createPreviewGame(
    currentPhase: GamePhase,
    actualTimeElapsedInPeriodMillis: Long,
    kickOffTeam: Team = Team.AWAY,
    homeScore: Int = 0,
    awayScore: Int = 0,
    halfDurationMinutes: Int = 45,
    halftimeDurationMinutes: Int = 15,
    homeTeamColorArgb: Int = android.graphics.Color.BLACK, // ARGB Int
    awayTeamColorArgb: Int = android.graphics.Color.YELLOW, // ARGB Int
    isTimerRunningOverride: Boolean? = null
): Game {
    val baseGame = Game.defaults().copy(
        id = "previewGame-${currentPhase.name}-${System.nanoTime()}",
        currentPhase = currentPhase,
        homeTeamName = "Red Team",
        awayTeamName = "Blue Team",
        homeTeamColorArgb = homeTeamColorArgb,
        awayTeamColorArgb = awayTeamColorArgb,
        kickOffTeam = kickOffTeam,
        actualTimeElapsedInPeriodMillis = actualTimeElapsedInPeriodMillis,
        halfDurationMinutes = halfDurationMinutes,
        halftimeDurationMinutes = halftimeDurationMinutes,
        homeScore = homeScore,
        awayScore = awayScore
        // isTimerRunning will be set below
    )

    val finalIsTimerRunning = isTimerRunningOverride ?: (actualTimeElapsedInPeriodMillis > 0 &&
            baseGame.currentPhase.hasTimer() &&
            (baseGame.currentPhase.isPlayablePhase() && actualTimeElapsedInPeriodMillis < baseGame.regulationPeriodDurationMillis()) || // Timer runs if playable and not past full reg time
            (baseGame.currentPhase.isBreak() && actualTimeElapsedInPeriodMillis < baseGame.regulationPeriodDurationMillis())) // Timer runs for break if not past break duration

    return baseGame.copy(isTimerRunning = finalIsTimerRunning)
}

@Preview(device = "id:wearos_small_round",name = "AddedTime SmRnd",showBackground = true)
@Preview(device = "id:wearos_large_round",name = "AddedTime LrgRnd",showBackground = true)
@Preview(device = "id:wearos_square",name = "AddedTime Sqr",showBackground = true)
@WearPreviewFontScales
@Composable
fun Preview_MainGameDisplay_RegulationTime() {
    RefWatchWearTheme {
        MainGameDisplayScreen(
            game = createPreviewGame(
                currentPhase = GamePhase.FIRST_HALF,
                actualTimeElapsedInPeriodMillis = 5 * 60000L, // 5 minutes into the game
                homeScore = 2,
                isTimerRunningOverride = true
            ),
            onKickOff = {}
        )
    }
}

@Preview(device = "id:wearos_small_round",name = "AddedTime SmRnd",showBackground = true)
@Preview(device = "id:wearos_large_round",name = "AddedTime LrgRnd",showBackground = true)
@Preview(device = "id:wearos_square",name = "AddedTime Sqr",showBackground = true)
@WearPreviewFontScales
@Composable
fun Preview_MainGameDisplay_AddedTime() {
    RefWatchWearTheme {
        MainGameDisplayScreen(
            game = createPreviewGame(
                currentPhase = GamePhase.FIRST_HALF,
                actualTimeElapsedInPeriodMillis = (45 * 60000L) + (2 * 60000L) + 15000L, // 45min + 2min 15sec
                halfDurationMinutes = 45,
                homeScore = 2,
                isTimerRunningOverride = true
            ),
            onKickOff = {}
        )
    }
}
@Preview(device = "id:wearos_small_round",name = "AddedTime SmRnd",showBackground = true)
@Preview(device = "id:wearos_large_round",name = "AddedTime LrgRnd",showBackground = true)
@Preview(device = "id:wearos_square",name = "AddedTime Sqr",showBackground = true)
@WearPreviewFontScales
@Composable
fun Preview_MainGameDisplay_Halftime() {
    RefWatchWearTheme {
        MainGameDisplayScreen(
            game = createPreviewGame(
                currentPhase = GamePhase.HALF_TIME,
                actualTimeElapsedInPeriodMillis = 5 * 60000L + 25000, // 5 minutes into halftime
                halftimeDurationMinutes = 15,
                kickOffTeam = Team.HOME,
                awayScore = 3,
                awayTeamColorArgb = android.graphics.Color.RED,
                isTimerRunningOverride = true // Assuming halftime timer runs
            ),
            onKickOff = {}
        )
    }
}

@Preview(device = "id:wearos_small_round",name = "AddedTime SmRnd",showBackground = true)
@Preview(device = "id:wearos_large_round",name = "AddedTime LrgRnd",showBackground = true)
@Preview(device = "id:wearos_square",name = "AddedTime Sqr",showBackground = true)
@WearPreviewFontScales
@Composable
fun Preview_MainGameDisplay_Kickoff() {
    RefWatchWearTheme {
        MainGameDisplayScreen(
            game = createPreviewGame(
                currentPhase = GamePhase.FIRST_HALF,
                actualTimeElapsedInPeriodMillis = 0L,
                homeScore = 0,
                awayScore = 0,
                isTimerRunningOverride = false // Important: timer NOT running for kickoff button
            ),
            onKickOff = {}
        )
    }
}
