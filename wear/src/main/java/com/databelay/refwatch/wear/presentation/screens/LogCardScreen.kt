package com.databelay.refwatch.wear.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Picker
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.databelay.refwatch.common.CardType
import com.databelay.refwatch.common.Team
import com.databelay.refwatch.common.theme.RefWatchWearTheme

/**
 * Collects the player number for a card.
 *
 * The number is entered with two rotary-friendly [Picker]s (tens and units) rather than a
 * text field. A referee entering this is standing on a pitch holding a card up: the crown
 * reaches any number in at most ten detents with no keyboard to summon, nothing to mistype,
 * and no input surface that can be clipped at large font scales.
 */
@Composable
fun LogCardScreen(
    preselectedTeam: Team?,
    cardType: CardType,
    onLogCard: (team: Team, playerNumber: Int, cardType: CardType) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val pickerState = rememberPlayerNumberPickerState()

    val playerNumber = pickerState.value
    val canConfirm = preselectedTeam != null && playerNumber > 0

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
            // Tight, evenly spaced: at the default font scale the header, picker and
            // actions have to fit a 192dp small round screen without scrolling. The
            // screen still scrolls when a large font scale pushes it over.
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            CardTypeHeader(cardType = cardType, team = preselectedTeam)

            // No "Player #" caption: the two large digits under a card header are
            // self-evident, and the caption cost a line the screen does not have.
            PlayerNumberPicker(state = pickerState)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AlertDialogDefaults.DismissButton(onClick = onCancel)
                // AlertDialogDefaults.ConfirmButton has no `enabled` parameter, so the
                // disabled state is expressed through colors. The pickers cannot produce an
                // out-of-range number, so the only reachable invalid value is 00, which the
                // user can see on screen -- no toast needed to explain it.
                AlertDialogDefaults.ConfirmButton(
                    onClick = {
                        if (canConfirm) {
                            onLogCard(preselectedTeam, playerNumber, cardType)
                        }
                    },
                    colors = if (canConfirm) {
                        IconButtonDefaults.filledIconButtonColors()
                    } else {
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                )
            }
        }
    }
}

/**
 * Card swatch and team, on a single line.
 *
 * The previous version accepted [cardType] but never displayed it at all, so a referee
 * could not tell a yellow from a red once on this screen. The colour of the swatch now
 * carries that, and its content description spells it out for screen readers.
 */
@Composable
private fun CardTypeHeader(cardType: CardType, team: Team?) {
    val cardColor = when (cardType) {
        CardType.YELLOW -> Color.Yellow
        CardType.RED -> Color.Red
    }
    val cardName = if (cardType == CardType.YELLOW) "Yellow card" else "Red card"
    val teamName = team?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(width = 14.dp, height = 19.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(cardColor)
                .semantics { contentDescription = cardName }
        )
        if (teamName != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = teamName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// --------------------------------------- Previews ----------------------------------------
// -----------------------------------------------------------------------------------------
@Preview(device = "id:wearos_small_round", name = "LogCard SmRnd", showBackground = true)
@Preview(device = "id:wearos_large_round", name = "LogCard LrgRnd", showBackground = true)
@Preview(device = "id:wearos_square", name = "LogCard Sqr", showBackground = true)
@WearPreviewFontScales
@Composable
fun LogCardScreenPreview_Yellow_Home() {
    RefWatchWearTheme {
        LogCardScreen(
            preselectedTeam = Team.HOME,
            cardType = CardType.YELLOW,
            onLogCard = { _, _, _ -> },
            onCancel = {}
        )
    }
}

@Preview(device = "id:wearos_small_round", name = "LogCard Red", showBackground = true)
@WearPreviewFontScales
@Composable
fun LogCardScreenPreview_Red_Away() {
    RefWatchWearTheme {
        LogCardScreen(
            preselectedTeam = Team.AWAY,
            cardType = CardType.RED,
            onLogCard = { _, _, _ -> },
            onCancel = {}
        )
    }
}
