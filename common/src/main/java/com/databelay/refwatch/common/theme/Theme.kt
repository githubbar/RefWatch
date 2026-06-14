package com.databelay.refwatch.common.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.wear.compose.material3.MaterialTheme // Wear Material Theme

// Define your M3 ColorSchemes using the colors from Color.kt
val AppLightColorScheme: ColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceTint = md_theme_light_surfaceTint,
    outlineVariant = md_theme_light_outlineVariant,
    scrim = md_theme_light_scrim,
)

val AppDarkColorScheme: ColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
)

// Create a Wear OS Colors object based on your common M3 colors
private val WearAppDarkColorScheme: androidx.wear.compose.material3.ColorScheme =
    androidx.wear.compose.material3.ColorScheme(
        primary = md_theme_dark_primary,              // From Color.kt (Vibrant Green)
        primaryDim = md_theme_dark_primaryDim, // From Color.kt (Darker Green)
        primaryContainer = md_theme_dark_primaryContainer,
        secondary = md_theme_dark_secondary,            // From Color.kt (Vibrant Cyan/Blue)
        secondaryDim = md_theme_dark_secondaryDim, // From Color.kt (Darker Cyan/Blue)
        secondaryContainer = md_theme_dark_secondaryContainer,
        error = md_theme_dark_error,                  // From Color.kt (Bright Red)
        errorDim = md_theme_dark_errorDim,
        errorContainer = md_theme_dark_errorContainer,
        onPrimary = md_theme_dark_onPrimary,            // From Color.kt (Pure Black)
        onPrimaryContainer = md_theme_dark_onPrimaryContainer,
        onSecondary = md_theme_dark_onSecondary,          // From Color.kt (Pure Black)
        onSecondaryContainer = md_theme_dark_onSecondaryContainer,
        onError = md_theme_dark_onError,                // From Color.kt (Pure Black)
        onErrorContainer = md_theme_dark_onErrorContainer,
        background = md_theme_dark_background,          // From Color.kt (Pure Black)
        onBackground = md_theme_dark_onBackground,        // From Color.kt (Pure White)
        surfaceContainer = md_theme_dark_surfaceContainer,
        surfaceContainerLow = md_theme_dark_surfaceContainerLow,
        surfaceContainerHigh = md_theme_dark_surfaceContainerHigh, // From Color.kt (Very dark gray)
        onSurface = md_theme_dark_onSurface,              // From Color.kt (Pure White)
        onSurfaceVariant = md_theme_dark_onSurfaceVariant // From Color.kt (Very light gray / off-white)
    )
// Define WearAppLightColorScheme using your M3 light theme color tokens
val WearAppLightColorScheme: androidx.wear.compose.material3.ColorScheme =
    androidx.wear.compose.material3.ColorScheme(
        primary = md_theme_light_primary,
        primaryDim = md_theme_light_primaryDim, // Ensure md_theme_light_primaryDim is in Color.kt
        primaryContainer = md_theme_light_primaryContainer,
        secondary = md_theme_light_secondary,
        secondaryDim = md_theme_light_secondaryDim, // Ensure md_theme_light_secondaryDim is in Color.kt
        secondaryContainer = md_theme_light_secondaryContainer,
        error = md_theme_light_error,
        errorDim = md_theme_light_errorDim,         // Ensure md_theme_light_errorDim is in Color.kt
        errorContainer = md_theme_light_errorContainer,
        onPrimary = md_theme_light_onPrimary,
        onPrimaryContainer = md_theme_light_onPrimaryContainer,
        onSecondary = md_theme_light_onSecondary,
        onSecondaryContainer = md_theme_light_onSecondaryContainer,
        onError = md_theme_light_onError,
        onErrorContainer = md_theme_light_onErrorContainer,
        background = md_theme_light_background,
        onBackground = md_theme_light_onBackground,
        surfaceContainer = md_theme_light_surfaceContainer,    // Ensure md_theme_light_surfaceContainer is in Color.kt
        surfaceContainerLow = md_theme_light_surfaceContainerLow, // Ensure md_theme_light_surfaceContainerLow is in Color.kt
        surfaceContainerHigh = md_theme_light_surfaceContainerHigh, // Ensure md_theme_light_surfaceContainerHigh is in Color.kt
        onSurface = md_theme_light_onSurface,
        onSurfaceVariant = md_theme_light_onSurfaceVariant
    )

@Composable
fun RefWatchWearTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme( // androidx.wear.compose.material3.MaterialTheme
        colorScheme = WearAppDarkColorScheme,
//            else -> {WearAppLightColorScheme}
//        }, // Your WearAppColorPalette
        typography = WearTypography,  // Use WearTypography defined in this module
        content = content
    )
}

// Basic Material 3 Theme (you can customize this further)
@Composable
fun RefWatchMobileTheme( // This is your PHONE App's M3 Theme
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Dynamic color is available on Android 12+
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> AppDarkColorScheme // Use common dark scheme
        else -> AppLightColorScheme     // Use common light scheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb() // Example
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !darkTheme // Or based on primary's luminance
        }
    }

    androidx.compose.material3.MaterialTheme(
        colorScheme = colorScheme,
        typography = MobileTypography, // Your phone app's M3 Typography
        content = content
    )
}