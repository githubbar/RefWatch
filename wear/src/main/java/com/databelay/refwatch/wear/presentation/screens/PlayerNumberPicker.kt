package com.databelay.refwatch.wear.presentation.screens

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.foundation.hierarchicalFocusGroup
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.Picker
import androidx.wear.compose.material3.PickerState
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberPickerState

/** Holds the tens and units [PickerState]s so callers can read the combined value. */
class PlayerNumberPickerState internal constructor(
    internal val tens: PickerState,
    internal val units: PickerState
) {
    /** The selected number, 0..99. Zero is not a valid shirt number. */
    val value: Int
        get() = tens.selectedOptionIndex * 10 + units.selectedOptionIndex
}

@Composable
fun rememberPlayerNumberPickerState(): PlayerNumberPickerState {
    val tens = rememberPickerState(
        initialNumberOfOptions = 10,
        initiallySelectedIndex = 0,
        shouldRepeatOptions = true
    )
    val units = rememberPickerState(
        initialNumberOfOptions = 10,
        initiallySelectedIndex = 1, // Start on "1": player 0 is not a valid shirt number.
        shouldRepeatOptions = true
    )
    return remember(tens, units) { PlayerNumberPickerState(tens, units) }
}

/**
 * Two-digit shirt number entry built from rotary-friendly [Picker]s.
 *
 * A referee using this is standing on a pitch, so the crown reaches any number in at most
 * ten detents with no keyboard to summon and nothing to mistype. It also has no text input
 * surface that could be clipped at large font scales.
 */
@Composable
fun PlayerNumberPicker(
    state: PlayerNumberPickerState,
    modifier: Modifier = Modifier,
    height: Dp = pickerHeight()
) {
    // Units starts focused: most shirt numbers are single digit or end in the digit the
    // referee is most likely to adjust.
    var focusedPicker by remember { mutableStateOf(1) }

    // A plain centred Row rather than PickerGroup. PickerGroup positions its children from
    // an offset it computes during layout and then animates into place, so on the first
    // frame -- and permanently in @Preview, where animations never advance -- the pair sits
    // half its own width to the right of centre. A Row just centres them.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DigitPicker(
            state = state.tens,
            selected = focusedPicker == 0,
            onSelected = { focusedPicker = 0 },
            contentDescription = { "Tens digit, ${state.tens.selectedOptionIndex}" },
            height = height
        )
        DigitPicker(
            state = state.units,
            selected = focusedPicker == 1,
            onSelected = { focusedPicker = 1 },
            contentDescription = { "Units digit, ${state.units.selectedOptionIndex}" },
            height = height
        )
    }
}

/**
 * One digit column. Mirrors what PickerGroupItem does for focus so the rotary crown drives
 * whichever digit is selected: the unselected picker goes read-only, and hierarchical focus
 * moves to the selected one.
 */
@Composable
private fun DigitPicker(
    state: PickerState,
    selected: Boolean,
    onSelected: () -> Unit,
    contentDescription: () -> String,
    height: Dp
) {
    val latestOnSelected by rememberUpdatedState(onSelected)
    val touchExplorationEnabled = touchExplorationEnabled()

    Picker(
        state = state,
        contentDescription = contentDescription,
        readOnly = !selected,
        onSelected = onSelected,
        userScrollEnabled = !touchExplorationEnabled || selected,
        // Picker fades its top and bottom edges by PickerDefaults.GradientRatio. Over a
        // viewport only tall enough for one digit plus neighbours, that fade reaches the
        // selected digit and reads as the number being cut off. Two columns of large
        // digits are legible as a picker without it.
        gradientRatio = 0f,
        modifier = Modifier
            .size(width = pickerWidth(), height = height)
            // Picker only reports onSelected through semantics (i.e. to screen readers), so
            // an ordinary tap on the unselected digit needs handling here or the tens column
            // would be unreachable by touch. Skipped under touch exploration, where the
            // semantics path already covers it.
            .pointerInput(touchExplorationEnabled, selected) {
                if (touchExplorationEnabled || selected) return@pointerInput
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        latestOnSelected()
                    }
                }
            }
            .hierarchicalFocusGroup(active = selected)
            .requestFocusOnHierarchyActive()
    ) { optionIndex ->
        PickerDigit(optionIndex)
    }
}

/**
 * Whether the system is reading the screen aloud (TalkBack's explore-by-touch), kept live
 * so the pickers react if it is toggled while the screen is open.
 */
@Composable
private fun touchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val accessibilityManager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    var enabled by remember { mutableStateOf(accessibilityManager.isTouchExplorationEnabled) }
    DisposableEffect(accessibilityManager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        accessibilityManager.addTouchExplorationStateChangeListener(listener)
        enabled = accessibilityManager.isTouchExplorationEnabled
        onDispose { accessibilityManager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

@Composable
private fun PickerDigit(digit: Int) {
    Text(
        text = digit.toString(),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

/**
 * Sized from the digit's own text size rather than a fixed dp, so it grows with the user's
 * font scale. A fixed height clipped the digits top and bottom at the largest Wear font
 * scale, which is exactly the kind of cut-off text the store review flags.
 *
 * The multiplier leaves room for the selected digit plus a sliver of its neighbours, which
 * is what makes it read as a picker rather than a label.
 */
@Composable
private fun pickerHeight(): Dp {
    val fontSize = MaterialTheme.typography.displaySmall.fontSize
    return with(LocalDensity.current) { fontSize.toDp() * 2.2f }
}

/** Wide enough for one digit at the largest font scale, again derived from the text size. */
@Composable
private fun pickerWidth(): Dp {
    val fontSize = MaterialTheme.typography.displaySmall.fontSize
    return with(LocalDensity.current) { fontSize.toDp() * 1.6f }
}
