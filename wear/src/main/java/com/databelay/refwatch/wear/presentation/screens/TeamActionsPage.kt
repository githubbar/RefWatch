// In TeamActionsPage.kt
package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
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
    val scrollState = rememberScrollState()
    ScreenScaffold(
        modifier = modifier.fillMaxSize(),
        scrollState = scrollState,
        scrollIndicator = { ScrollIndicator(state = scrollState) }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding)
                // No extra vertical inset: contentPadding already reserves 10% of the
                // screen top and bottom, and doubling it pushed the cards off the bottom.
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Text: Team Name
            Text(
                text = teamName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (teamColor.isDark()) Color.White else teamColor,
                textAlign = TextAlign.Center,
                // Callers pass a name already shortened by shortName(), but guard anyway so
                // a long name cannot push the cards off the bottom of the screen.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Goal Button
            if (isPlayablePhase) {
                Button(
                    onClick = { onAddGoal(team) },
                    shape = CircleShape,
                    modifier = Modifier.defaultMinSize(minWidth = 60.dp, minHeight = 60.dp),
                ) {
                    Text(
                        "+1",
                        style = MaterialTheme.typography.displayMedium,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(ButtonDefaults.LargeIconSize))
            }
            Spacer(modifier = Modifier.height(8.dp))

            // The two cards, drawn as the cards themselves rather than labelled buttons.
            // A word like "Yellow" cannot wrap, and side by side there is not enough width
            // for it at the largest font scale -- it was being clipped mid-word. Stacking
            // the buttons fixed the text but pushed the red card off the bottom, and a
            // referee needs both within one tap. Shape and colour carry the meaning here as
            // well as any label would, and they cost no width at any font scale.
            Row(
                modifier = Modifier.fillMaxWidth(CardButtonWidthFraction),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardShapedButton(
                    onClick = { onNavigateToLogCard(team, CardType.YELLOW) },
                    contentDescription = "Yellow card",
                    cardColor = Color.Yellow
                )
                CardShapedButton(
                    onClick = { onNavigateToLogCard(team, CardType.RED) },
                    contentDescription = "Red card",
                    cardColor = Color.Red
                )
            }
        }
    }
}

/**
 * A referee's card, shaped and coloured like the real thing, as a button.
 *
 * Carries no text, so there is nothing to clip at any font scale. [contentDescription] is
 * what a screen reader announces, and it is the only thing that names the card.
 */
@Composable
fun CardShapedButton(
    onClick: () -> Unit,
    contentDescription: String,
    cardColor: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            // A real card is taller than it is wide. Sized in dp on purpose: this is a
            // graphic, not text, so it should not grow with the font scale and squeeze the
            // rest of the screen.
            .size(width = 44.dp, height = 60.dp)
            .clip(shape)
            .background(cardColor)
            .border(1.dp, Color.Black.copy(alpha = 0.35f), shape)
            .clickable(
                onClick = onClick,
                onClickLabel = contentDescription,
                role = Role.Button
            )
            .semantics { this.contentDescription = contentDescription }
    )
}

/**
 * Content on a round screen has to stay inside the circle. At the vertical position these
 * sit, a full-width row would have its ends cut off by the bezel.
 */
private const val CardButtonWidthFraction = 0.82f

@Preview(device = WearDevices.SMALL_ROUND, showBackground = true)
@Preview(device = WearDevices.LARGE_ROUND, showBackground = true)
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
