package com.databelay.refwatch.common.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.databelay.refwatch.common.theme.Typography
@Composable
fun RefWatchTheme(
    content: @Composable () -> Unit
) {
    /**
     * Empty theme to customize for your app.
     * See: https://developer.android.com/jetpack/compose/designsystems/custom
     */
    MaterialTheme(
        colors = wearColorPalette, // Use the new palette
        content = content
    )
}




private val DarkColorScheme = darkColorScheme(
    primary = com.databelay.refwatch.ui.theme.Purple80,
    secondary = com.databelay.refwatch.ui.theme.PurpleGrey80,
    tertiary = com.databelay.refwatch.ui.theme.Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = com.databelay.refwatch.ui.theme.Purple40,
    secondary = com.databelay.refwatch.ui.theme.PurpleGrey40,
    tertiary = com.databelay.refwatch.ui.theme.Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

/*
@Composable
fun RefWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}*/
