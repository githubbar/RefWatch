package com.databelay.refwatch.presentation.theme // Your package

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors

val Purple200 = Color(0xFFBB86FC)
val Purple500 = Color(0xFF6200EE)
val Purple700 = Color(0xFF3700B3)
val Teal200 = Color(0xFF03DAC5)
val Orange400 = Color(0xFFF4511E)
val Red400 = Color(0xFFCF6679)
val Pink400 = Color(0xFFEC407A)
// vv ADD YOUR DEFAULT COLOR CONSTANTS HERE vv
val DefaultHomeColor: Color = Color.White // Example default
val DefaultAwayColor: Color = Color.Black // Example default

internal val wearColorPalette: Colors = Colors(
    primary = Purple200,
    primaryVariant = Purple700,
    secondary = Teal200,
    secondaryVariant = Teal200,
    error = Red400,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onError = Color.Black,
    // You might want to define specific background/surface colors for your app
    // For now, we'll use defaults which are typically dark for Wear OS
    background = Color.Black,
    surface = Color(0xFF303030), // A slightly lighter dark gray for cards/chips
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFDADADA) // For less prominent text on surface
)