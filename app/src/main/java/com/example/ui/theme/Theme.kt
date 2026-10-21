package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyberPrimary,
    onPrimary = CyberOnPrimary,
    primaryContainer = CyberPrimaryContainer,
    onPrimaryContainer = CyberPrimary,
    secondary = CyberSecondary,
    onSecondary = OnSurfaceWhite,
    secondaryContainer = CyberSecondaryContainer,
    tertiary = CyberTertiary,
    tertiaryContainer = CyberTertiaryContainer,
    background = CyberBg,
    onBackground = OnSurfaceWhite,
    surface = CyberSurface,
    onSurface = OnSurfaceWhite,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = OnSurfaceDim,
    error = CyberError,
    outline = CyberCardBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = CyberBg.toArgb()
            window.navigationBarColor = CyberBg.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
