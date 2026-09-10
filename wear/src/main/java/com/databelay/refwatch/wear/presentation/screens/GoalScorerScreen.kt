package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.theme.RefWatchWearTheme
import com.databelay.refwatch.wear.presentation.components.ColorIndicator

/**
 * Asks who scored, shown only when the "Log goal scorer" setting is on.
 *
 * Both actions record the goal. Skip records it without a scorer, so a referee who taps
 * the wrong thing or simply did not see the number never loses the goal itself -- the
 * scorer is strictly extra information on top of the existing one-tap flow.
 */
@Composable
fun GoalScorerScreen(
    team: Team,
    teamName: String,
    teamColor: Color,
    onConfirm: (playerNumber: Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val pickerState = rememberPlayerNumberPickerState()
    val playerNumber = pickerState.value

    ScreenScaffold(
        modifier = modifier.fillMaxSize(),
        scrollState = scrollState,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Matches LogCardScreen: tight enough to clear a 192dp small round screen at
            // the default font scale, still scrollable when a large scale pushes it over.
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            // Inset like the buttons below: this row sits near the top of the screen where
            // the circle is at its narrowest, and a full-width row put the colour dot
            // outside the visible area.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(ActionWidthFraction)
            ) {
                ColorIndicator(color = teamColor, indicatorSize = 14.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = teamName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // The team header plus two large digits carry the meaning; a "Scored by #"
            // caption cost a line this screen does not have.
            PlayerNumberPicker(state = pickerState)

            // Side by side, but inset from the edges. These were originally laid out across
            // the full width, which put both ends outside the round screen; stacking them
            // instead pushed Skip off the bottom. "Save" and "Skip" are short enough to sit
            // together inside the circle even at the largest font scale.
            Row(
                modifier = Modifier.fillMaxWidth(ActionWidthFraction),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(),
                    contentPadding = CompactActionPadding
                ) {
                    Text(
                        "Skip",
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = {
                        // Player 0 is not a valid shirt number; treat it as a skip rather
                        // than blocking the referee on a screen that is holding up the game.
                        if (playerNumber > 0) onConfirm(playerNumber) else onSkip()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = CompactActionPadding
                ) {
                    Text(
                        "Save",
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// --------------------------------------- Previews ----------------------------------------
@Preview(device = "id:wearos_small_round", name = "GoalScorer SmRnd", showBackground = true)
@Preview(device = "id:wearos_large_round", name = "GoalScorer LrgRnd", showBackground = true)
@Preview(device = "id:wearos_square", name = "GoalScorer Sqr", showBackground = true)
@WearPreviewFontScales
@Composable
fun GoalScorerScreenPreview() {
    RefWatchWearTheme {
        GoalScorerScreen(
            team = Team.HOME,
            teamName = "Alpha FC",
            teamColor = Color.Red,
            onConfirm = {},
            onSkip = {}
        )
    }
}

/**
 * Buttons on a round screen have to stay inside the circle; a full-width button at this
 * vertical position has its ends cut off by the bezel.
 */
private const val ActionWidthFraction = 0.82f

/**
 * ButtonDefaults.ContentPadding reserves 14dp each side, which at the largest font scale
 * left too little room for the label and ellipsised "Skip" down to "S...". These labels are
 * short, so they do not need the standard inset.
 */
private val CompactActionPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
