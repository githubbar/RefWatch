package com.databelay.refwatch.wear.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.databelay.refwatch.common.isDark

@Composable
fun ColorIndicator(
    modifier: Modifier = Modifier,
    color: Color,
    indicatorSize: Dp = 20.dp, // This is the diameter of the colored circle
    outlineWidth: Dp = 1.dp,
    // Default outline to white (good for dark backgrounds and standard Wear theme)
    // You can make this dynamic if needed: = if (color.isDark()) Color.White else Color.Black
    hasKickOffBorder: Boolean = false,
    kickOffBorderWidth: Dp = 4.dp // Adjusted for better visual balance
) {
    val density = LocalDensity.current
    val kickOffBorderColor: Color = Color.Green
    val dynamicOutlineColor = if (color.isDark()) Color.White else Color.Black
    // Calculate total size needed for the Canvas
    // Start with the indicator size
    // Add space for the outline (on both sides of the diameter)
    // Add space for the kickoff border (on both sides of the diameter, outside the outline)
    val totalDiameter = remember(indicatorSize, outlineWidth, hasKickOffBorder, kickOffBorderWidth) {
        var diameter = indicatorSize
        if (outlineWidth > 0.dp) {
            diameter += outlineWidth * 2
        }
        if (hasKickOffBorder) {
            diameter += kickOffBorderWidth * 2
        }
        diameter
    }

    Box(modifier = modifier.size(totalDiameter)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val canvasRadius = this.size.minDimension / 2f
            val canvasCenter = this.center

            // Convert Dp to Px for drawing
            val indicatorRadiusPx = with(density) { (indicatorSize / 2f).toPx() }
            val outlineWidthPx = with(density) { outlineWidth.toPx() }
            val kickOffBorderWidthPx = with(density) { kickOffBorderWidth.toPx() }

            var currentRadiusForStroke = canvasRadius // Start from the outermost edge for strokes

            // 1. Draw KickOff border (outermost)
            if (hasKickOffBorder && kickOffBorderWidthPx > 0) {
                val kickOffStrokeRadius = currentRadiusForStroke - (kickOffBorderWidthPx / 2f)
                if (kickOffStrokeRadius > 0) {
                    drawCircle(
                        color = kickOffBorderColor,
                        radius = kickOffStrokeRadius,
                        style = Stroke(width = kickOffBorderWidthPx, cap = StrokeCap.Butt), // Butt cap for cleaner edge
                        center = canvasCenter
                    )
                }
                currentRadiusForStroke -= kickOffBorderWidthPx
            }

            // 2. Draw the contrasting outline (inside kickoff, if present)
            if (outlineWidthPx > 0) {
                val outlineStrokeRadius = currentRadiusForStroke - (outlineWidthPx / 2f)
                if (outlineStrokeRadius > 0) {
                    drawCircle(
                        color = dynamicOutlineColor,
                        radius = outlineStrokeRadius,
                        style = Stroke(width = outlineWidthPx),
                        center = canvasCenter
                    )
                }
                // currentRadiusForStroke -= outlineWidthPx // Not needed if main color is drawn from center
            }

            // 3. Draw the main color indicator (innermost, fills up to indicatorSize)
            // Its radius is simply half of the `indicatorSize` parameter.
            if (indicatorRadiusPx > 0) {
                drawCircle(
                    color = color,
                    radius = indicatorRadiusPx, // This ensures the colored part is always `indicatorSize`
                    center = canvasCenter
                )
            }
        }
    }
}
