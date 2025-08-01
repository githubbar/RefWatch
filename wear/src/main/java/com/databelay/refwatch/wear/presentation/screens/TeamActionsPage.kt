// In TeamActionsPage.kt
package com.databelay.refwatch.wear.presentation.screens

// Remove ScalingLazyColumn related imports
// import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
// import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
// import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.isDark
import com.databelay.refwatch.common.isPlayablePhase


@Composable
fun TeamActionsPage(
    team: Team,
    game: Game,
    onAddGoal: (Team) -> Unit,
    onNavigateToLogCard: (team: Team, cardType: CardType) -> Unit,
    modifier: Modifier = Modifier
) {
    val teamColor = if (team == Team.HOME) game.homeTeamColor else game.awayTeamColor
    val teamName = if (team == Team.HOME) game.homeTeamName else game.awayTeamName
    val score = if (team == Team.HOME) game.homeScore else game.awayScore

    Column(
        modifier = modifier
            .fillMaxSize()
            // You can be more specific with WindowInsetsSides.Top if only top is an issue
            .padding(all = 16.dp), // Add overall padding for the content within the screen
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
//                Arrangement.spacedBy(
//            space = 16.dp, // Consistent spacing between elements
//            alignment = Alignment.Top // Align content to the top
//        )
    ) {
        // Text: Team Name and Score
        Text(
            modifier = Modifier.padding(top=20.dp),
            text = "$teamName - Score: $score",
            style = MaterialTheme.typography.body1,
            color = if (teamColor.isDark()) Color.White else teamColor.let { Color(it.red, it.green, it.blue, it.alpha) },
            textAlign = TextAlign.Center // Center text if it wraps
        )

        // Goal Button
        if (game.currentPhase.isPlayablePhase()) {
            Button(
                onClick = { onAddGoal(team) },
                modifier = Modifier
                    .fillMaxWidth(0.8f), // Takes 80% of the available width
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
            ) {
                Text("Add Goal")
            }
        } else {
            // Optional: Show a placeholder or disabled button if not in a playable phase
            // Or simply omit it, and the space will be taken up by other elements
            // For example, a Spacer to maintain height:
            Spacer(modifier = Modifier.height(ButtonDefaults.LargeButtonSize)) // Standard Wear Button height
        }

        // Card Buttons in a Row
        Row(
            modifier = Modifier
                .fillMaxWidth(0.6f), // Adjust width as needed, e.g., 0.8f for consistency
            horizontalArrangement = Arrangement.spacedBy(10.dp), // Spacing between card buttons
            verticalAlignment = Alignment.CenterVertically // Align card buttons vertically
        ) {
            // Yellow Card Button
            CardShapedButton(
                onClick = { onNavigateToLogCard(team, CardType.YELLOW) },
                text = "Yellow",
                backgroundColor = Color.Yellow,
                contentColor = Color.Black,
                modifier = Modifier.weight(1f) // Distribute space equally
            )

            // Red Card Button
            CardShapedButton(
                onClick = { onNavigateToLogCard(team, CardType.RED) },
                text = "Red",
                backgroundColor = Color.Red,
                contentColor = Color.White,
                modifier = Modifier.weight(1f) // Distribute space equally
            )
        }

        // You can add more elements directly here if needed.
        // If the content might overflow and you *do* need scrolling,
        // then ScalingLazyColumn (or LazyColumn if scaling isn't critical)
        // would be the way to go. For a few fixed items, Column is fine.
        // Add a flexible spacer at the end if you want to push content up
        // when using Arrangement.Top and the content is short.
        // However, with Arrangement.spacedBy(..., Alignment.Top) and enough items,
        // it usually lays out as expected from the top.
        // If content is very short and you want to ensure it doesn't center,
        // this can be useful.
        // Spacer(Modifier.weight(1f)) // Uncomment if you want to push all content to the top definitively
    }
}

// CardShapedButton Composable remains the same
@Composable
fun CardShapedButton(
    onClick: () -> Unit,
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = backgroundColor,
            contentColor = contentColor
        ),
        modifier = modifier
            .height(70.dp)
            // .width(30.dp) // width on CardShapedButton was very narrow, weight(1f) in Row is better
            .border(1.dp, contentColor.copy(alpha = 0.5f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
    ) {
        Text(text, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

// Dummy Preview (update with your actual Game and Team state for a useful preview)
@Preview(device = "id:wearos_large_round", showSystemUi = true, backgroundColor = 0xff000000, showBackground = true)
@Composable
fun TeamActionsPagePreview() {
    MaterialTheme { // Wrap in MaterialTheme for previews
        TeamActionsPage(
            team = Team.HOME,
            game = Game.defaults().copy( // Use your Game.defaults() or a sample game
                id = "previewGame",
                currentPhase = GamePhase.FIRST_HALF,
                homeTeamName = "Red Team",
                homeTeamColorArgb = android.graphics.Color.BLACK,
                awayTeamColorArgb = android.graphics.Color.YELLOW,
                homeScore = 2
            ),
            onAddGoal = {},
            onNavigateToLogCard = { _, _ -> }
        )
    }
}
