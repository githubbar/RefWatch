package com.databelay.refwatch.wear.presentation.screens

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.databelay.refwatch.common.CardType // Import CardType
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.isPlayablePhase

@Composable
fun TeamActionsPage(
    team: Team,
    game: Game,
    onAddGoal: (Team) -> Unit,
    // Updated lambda to include CardType
    onNavigateToLogCard: (team: Team, cardType: CardType) -> Unit,
    modifier: Modifier = Modifier
) {
    val teamColor = if (team == Team.HOME) game.homeTeamColor else game.awayTeamColor
    val teamName = if (team == Team.HOME) game.homeTeamName else game.awayTeamName
    val score = if (team == Team.HOME) game.homeScore else game.awayScore

    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(all = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        item {
            Text(
                text = "$teamName - Score: $score",
                style = MaterialTheme.typography.title2,
                color = teamColor.let {
                    Color(
                        it.red,
                        it.green,
                        it.blue,
                        it.alpha
                    )
                } // Assuming teamColor is your common.Color
            )
        }

        item {
            // Goal Button
            if (game.currentPhase.isPlayablePhase()) {
                Button(
                    onClick = { onAddGoal(team) },
                    modifier = Modifier.fillMaxWidth(0.8f),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary // Or a specific color
                    )
                ) {
                    Text("Add Goal")
                }
            }
        }
        item {
            // Card Buttons
            Row(
                modifier = Modifier.fillMaxWidth(0.6f).padding(bottom=24.dp), // Adjust width as needed
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Yellow Card Button
                CardShapedButton(
                    onClick = { onNavigateToLogCard(team, CardType.YELLOW) },
                    text = "Yellow",
                    backgroundColor = Color.Yellow,
                    contentColor = Color.Black,
                    modifier = Modifier.weight(0.7f) // Make yellow card slightly narrower
                )

                // Red Card Button
                CardShapedButton(
                    onClick = { onNavigateToLogCard(team, CardType.RED) },
                    text = "Red",
                    backgroundColor = Color.Red,
                    contentColor = Color.White,
                    modifier = Modifier.weight(0.7f) // Make red card slightly narrower
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp)) // Add a 16.dp spacer
        }
    }
}

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
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp), // More card-like shape
        colors = ButtonDefaults.buttonColors(
            backgroundColor = backgroundColor,
            contentColor = contentColor
        ),
        modifier = modifier
            .height(70.dp) // Adjust height for a card-like aspect ratio
            .width(30.dp)  // Adjust width for a card-like aspect ratio
            .border(1.dp, contentColor.copy(alpha = 0.5f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
    ) {
        Text(text, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}


