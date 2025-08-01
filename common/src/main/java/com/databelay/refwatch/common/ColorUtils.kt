package com.databelay.refwatch.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Checks if a color is perceived as "dark".
 *
 * This is determined by calculating the color's luminance.
 * A common threshold for luminance to distinguish between light and dark is 0.5.
 * Colors with luminance below this threshold are considered dark.
 *
 * @return True if the color is dark, false otherwise.
 */
fun Color.isDark(): Boolean {
    return this.luminance() < 0.5f // Threshold can be adjusted (0.0 to 1.0)
}


