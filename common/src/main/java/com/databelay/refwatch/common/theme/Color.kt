package com.databelay.refwatch.common.theme // Your package
import androidx.compose.ui.graphics.Color

// Define your M3 color palette.
// You can generate these using the Material Theme Builder: https://m3.material.io/theme-builder
// Example Light Scheme Colors
val md_theme_light_primary = Color(0xFF006874)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFF9EEFFD)
val md_theme_light_onPrimaryContainer = Color(0xFF001F24)
val md_theme_light_secondary = Color(0xFF4A6267)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFCDE7ED)
val md_theme_light_onSecondaryContainer = Color(0xFF051F23)
val md_theme_light_tertiary = Color(0xFF545D7E)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFDCE1FF)
val md_theme_light_onTertiaryContainer = Color(0xFF101A37)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFBFCFC)
val md_theme_light_onBackground = Color(0xFF191C1D)
val md_theme_light_surface = Color(0xFFFBFCFC)
val md_theme_light_onSurface = Color(0xFF191C1D)
val md_theme_light_surfaceVariant = Color(0xFFDBE4E6)
val md_theme_light_onSurfaceVariant = Color(0xFF3F484A)
val md_theme_light_outline = Color(0xFF6F797B)
val md_theme_light_inverseOnSurface = Color(0xFFEFF1F1)
val md_theme_light_inverseSurface = Color(0xFF2E3132)
val md_theme_light_inversePrimary = Color(0xFF82D3E0)
// val md_theme_light_shadow = Color(0xFF000000) // Usually not needed directly
val md_theme_light_surfaceTint = Color(0xFF006874)
val md_theme_light_outlineVariant = Color(0xFFBFC8CA)
val md_theme_light_scrim = Color(0xFF000000)

// Example Dark Scheme Colors
val md_theme_dark_primary = Color(0xFF82D3E0)
val md_theme_dark_onPrimary = Color(0xFF00363D)
val md_theme_dark_primaryContainer = Color(0xFF004F58)
val md_theme_dark_onPrimaryContainer = Color(0xFF9EEFFD)
val md_theme_dark_secondary = Color(0xFFB1CBD0)
val md_theme_dark_onSecondary = Color(0xFF1C3438)
val md_theme_dark_secondaryContainer = Color(0xFF334B4F)
val md_theme_dark_onSecondaryContainer = Color(0xFFCDE7ED)
val md_theme_dark_tertiary = Color(0xFFBBC5EA)
val md_theme_dark_onTertiary = Color(0xFF262F4D)
val md_theme_dark_tertiaryContainer = Color(0xFF3D4565)
val md_theme_dark_onTertiaryContainer = Color(0xFFDCE1FF)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF191C1D)
val md_theme_dark_onBackground = Color(0xFFE1E3E3)
val md_theme_dark_surface = Color(0xFF191C1D)
val md_theme_dark_onSurface = Color(0xFFE1E3E3)
val md_theme_dark_surfaceVariant = Color(0xFF3F484A)
val md_theme_dark_onSurfaceVariant = Color(0xFFBFC8CA)
val md_theme_dark_outline = Color(0xFF899294)
val md_theme_dark_inverseOnSurface = Color(0xFF191C1D)
val md_theme_dark_inverseSurface = Color(0xFFE1E3E3)
val md_theme_dark_inversePrimary = Color(0xFF006874)
// val md_theme_dark_shadow = Color(0xFF000000)
val md_theme_dark_surfaceTint = Color(0xFF82D3E0)
val md_theme_dark_outlineVariant = Color(0xFF3F484A)
val md_theme_dark_scrim = Color(0xFF000000)

// --- Default Jersey Colors (Conceptually part of your app's "theme") ---
// These can be some of your M3 palette colors or distinct colors.
val DefaultHomeJerseyColor: Color = md_theme_light_primary // Example: Use M3 primary for home
val DefaultAwayJerseyColor: Color = md_theme_light_secondary // Example: Use M3 secondary for away

// --- Predefined selectable jersey colors ---
// It's good to offer a palette that works well with your theme.
val PredefinedJerseyColors: List<Color> = listOf(
    Color.Red, // Classic Red
    Color(0xFFFFA500), // Orange
    Color.Yellow,
    Color(0xFF008000), // Green (darker than Color.Green)
    Color.Cyan,
    Color.Blue,
    Color(0xFF800080), // Purple
    Color.Black,
    Color.White,
    Color.Gray,
    md_theme_light_primary, // Your M3 Primary
    md_theme_light_secondary, // Your M3 Secondary
    md_theme_light_tertiary, // Your M3 Tertiary
    Color(0xFFF08080), // Light Coral
    Color(0xFFADD8E6)  // Light Blue
).distinct() // Ensure no duplicates if M3 colors are similar to classics