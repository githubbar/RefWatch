package com.databelay.refwatch.common.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Define your base M3 Typography.
// You can customize fonts, weights, and sizes here.
// This will be used by your Phone App directly.
// Your Wear OS Typography will take inspiration from these values.
val MobileTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default, // Replace with your custom font if you have one
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal, // M3 often uses Normal or Medium for titles
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)


// Define your Wear OS Typography.
// Adjust font sizes and weights for the smaller Wear OS screen,
// taking inspiration from the M3 AppTypography.
val WearTypography = androidx.wear.compose.material.Typography(
    // Display styles (typically large, for timers or key info)
    display1 = TextStyle( // Similar to M3 displayLarge/Medium, but scaled down
        fontFamily = MobileTypography.displayLarge.fontFamily, // Use same family
        fontWeight = MobileTypography.displayLarge.fontWeight,
        fontSize = 40.sp, // Significantly smaller than 57sp
        lineHeight = 48.sp,
        letterSpacing = MobileTypography.displayLarge.letterSpacing
    ),
    display2 = TextStyle( // Similar to M3 displayMedium/Small
        fontFamily = MobileTypography.displayMedium.fontFamily,
        fontWeight = MobileTypography.displayMedium.fontWeight,
        fontSize = 34.sp, // Scaled down
        lineHeight = 40.sp,
        letterSpacing = MobileTypography.displayMedium.letterSpacing
    ),
    display3 = TextStyle( // Similar to M3 displaySmall/headlineLarge
        fontFamily = MobileTypography.displaySmall.fontFamily,
        fontWeight = MobileTypography.displaySmall.fontWeight,
        fontSize = 28.sp, // Scaled down
        lineHeight = 36.sp,
        letterSpacing = MobileTypography.displaySmall.letterSpacing
    ),

    // Title styles
    title1 = TextStyle( // Similar to M3 titleLarge
        fontFamily = MobileTypography.titleLarge.fontFamily,
        fontWeight = MobileTypography.titleLarge.fontWeight,
        fontSize = 20.sp, // Scaled down from 22sp
        lineHeight = 26.sp,
        letterSpacing = MobileTypography.titleLarge.letterSpacing
    ),
    title2 = TextStyle( // Similar to M3 titleMedium
        fontFamily = MobileTypography.titleMedium.fontFamily,
        fontWeight = MobileTypography.titleMedium.fontWeight,
        fontSize = 15.sp, // Scaled down from 16sp
        lineHeight = 22.sp,
        letterSpacing = MobileTypography.titleMedium.letterSpacing
    ),
    title3 = TextStyle( // Similar to M3 titleSmall
        fontFamily = MobileTypography.titleSmall.fontFamily,
        fontWeight = MobileTypography.titleSmall.fontWeight,
        fontSize = 13.sp, // Scaled down from 14sp
        lineHeight = 20.sp,
        letterSpacing = MobileTypography.titleSmall.letterSpacing
    ),

    // Body styles
    body1 = TextStyle( // Similar to M3 bodyLarge
        fontFamily = MobileTypography.bodyLarge.fontFamily,
        fontWeight = MobileTypography.bodyLarge.fontWeight,
        fontSize = 15.sp, // Slightly smaller than 16sp
        lineHeight = 22.sp,
        letterSpacing = MobileTypography.bodyLarge.letterSpacing
    ),
    body2 = TextStyle( // Similar to M3 bodyMedium
        fontFamily = MobileTypography.bodyMedium.fontFamily,
        fontWeight = MobileTypography.bodyMedium.fontWeight,
        fontSize = 13.sp, // Slightly smaller than 14sp
        lineHeight = 20.sp,
        letterSpacing = MobileTypography.bodyMedium.letterSpacing
    ),

    // Button style
    button = TextStyle( // Similar to M3 labelLarge
        fontFamily = MobileTypography.labelLarge.fontFamily,
        fontWeight = FontWeight.SemiBold, // Buttons often a bit bolder
        fontSize = 14.sp, // Same as M3 labelLarge
        lineHeight = 20.sp,
        letterSpacing = MobileTypography.labelLarge.letterSpacing
    ),

    // Caption styles
    caption1 = TextStyle( // Similar to M3 bodySmall
        fontFamily = MobileTypography.bodySmall.fontFamily,
        fontWeight = MobileTypography.bodySmall.fontWeight,
        fontSize = 12.sp, // Same as M3 bodySmall
        lineHeight = 16.sp,
        letterSpacing = MobileTypography.bodySmall.letterSpacing
    ),
    caption2 = TextStyle( // Similar to M3 labelMedium
        fontFamily = MobileTypography.labelMedium.fontFamily,
        fontWeight = MobileTypography.labelMedium.fontWeight,
        fontSize = 11.sp, // Slightly smaller than 12sp
        lineHeight = 16.sp,
        letterSpacing = MobileTypography.labelMedium.letterSpacing
    ),
    caption3 = TextStyle( // Similar to M3 labelSmall
        fontFamily = MobileTypography.labelSmall.fontFamily,
        fontWeight = MobileTypography.labelSmall.fontWeight,
        fontSize = 10.sp, // Slightly smaller than 11sp
        lineHeight = 14.sp,
        letterSpacing = MobileTypography.labelSmall.letterSpacing
    )
)