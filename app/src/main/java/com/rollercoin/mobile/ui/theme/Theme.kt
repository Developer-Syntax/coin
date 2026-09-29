package com.rollercoin.mobile.ui.theme

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
    primary = RcAmber,
    onPrimary = RcDarkBackground,
    primaryContainer = RcSurfaceElevated,
    onPrimaryContainer = RcAmberLight,
    secondary = RcCyan,
    onSecondary = RcDarkBackground,
    secondaryContainer = RcSurfaceElevated,
    onSecondaryContainer = RcCyanLight,
    tertiary = RcGreen,
    onTertiary = RcDarkBackground,
    background = RcDarkBackground,
    onBackground = RcTextPrimary,
    surface = RcSurface,
    onSurface = RcTextPrimary,
    surfaceVariant = RcSurfaceElevated,
    onSurfaceVariant = RcTextSecondary,
    outline = RcSurfaceBorder,
    error = RcRed,
    onError = RcTextPrimary
)

@Composable
fun RollerCoinTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = RcDarkBackground.toArgb()
            window.navigationBarColor = RcDarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
