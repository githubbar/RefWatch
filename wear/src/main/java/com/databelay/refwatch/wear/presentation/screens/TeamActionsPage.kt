// In TeamActionsPage.kt
package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import androidx.wear.tooling.preview.devices.WearDevices
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.Game
import com.databelay.refwatch.common.GamePhase
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.isDark
import com.databelay.refwatch.common.shortName
import com.databelay.refwatch.common.theme.RefWatchWearTheme

@Composable
fun TeamActionsPage(
    team: Team,
    teamName: String,
    teamColor: Color,
    isPlayablePhase: Boolean,
    onAddGoal: (Team) -> Unit,
    onNavigateToLogCard: (team: Team, cardType: CardType) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround
        ) {
            // Text: Team Name
            Text(
                text = teamName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (teamColor.isDark()) Color.White else teamColor,
                textAlign = TextAlign.Center,
            )

            // Goal Button
            if (isPlayablePhase) {
                Button(
                    onClick = { onAddGoal(team) },
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                ) {
                    Text(
                        "+1",
                        style = MaterialTheme.typography.displayLarge,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(ButtonDefaults.LargeIconSize))
            }

            // Card Buttons in a Row
            Row(
                modifier = Modifier.fillMaxWidth(.5f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Yellow Card Button
                CardShapedButton(
                    onClick = { onNavigateToLogCard(team, CardType.YELLOW) },
                    text = "Yellow",
                    backgroundColor = Color.Yellow,
                    contentColor = Color.Black,
                    modifier = Modifier.weight(1f)
                )

                // Red Card Button
                CardShapedButton(
                    onClick = { onNavigateToLogCard(team, CardType.RED) },
                    text = "Red",
                    backgroundColor = Color.Red,
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
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
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        modifier = modifier
            .aspectRatio(.90f)
            .border(
                1.dp,
                contentColor.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyExtraSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showBackground = true)
@Preview(device =  WearDevices.SMALL_ROUND, showBackground = true)
@Preview(device =  WearDevices.SQUARE, showBackground = true)
@WearPreviewFontScales
@Composable
fun TeamActionsPagePreview() {
    RefWatchWearTheme {
        TeamActionsPage(
            team = Team.HOME,
            teamName = "Red Team",
            teamColor = Color.Black,
            isPlayablePhase = true,
            onAddGoal = {},
            onNavigateToLogCard = { _, _ -> }
        )
    }
}
