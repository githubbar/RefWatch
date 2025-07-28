package com.databelay.refwatch.wear.presentation.components // << YOUR PACKAGE

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme

@Composable
fun ColorIndicator(
    color: Color,
    modifier: Modifier = Modifier, // For general layout positioning from the parent
    hasKickOffBorder: Boolean = false, // New parameter to control the kick-off border
    kickOffBorderWidth: Dp = 4.dp,
    kickOffBorderColor: Color = Color.Green // Or MaterialTheme.colors.primary
) {
    Box(
        modifier = modifier // Apply layout modifiers passed from the parent first
            .size(20.dp + if (hasKickOffBorder) kickOffBorderWidth * 2 else 0.dp) // Increase size to accommodate border
            .then(
                if (hasKickOffBorder) {
                    Modifier.border(kickOffBorderWidth, kickOffBorderColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .padding(if (hasKickOffBorder) kickOffBorderWidth else 0.dp) // Content padding so background doesn't overlap border
            .background(color, CircleShape) // Background is drawn inside the (now potentially larger) padded area
        // Optional: A very thin inner border for the color circle itself if desired
        // .border(0.5.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.2f), CircleShape)
    ) {
        // Content of the Box, if any (usually none for a simple color indicator)
    }
}
