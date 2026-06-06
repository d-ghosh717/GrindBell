package com.grindbell.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GrindBellColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = TextOnGradient,
    primaryContainer = ElectricBlue.copy(alpha = 0.12f),
    onPrimaryContainer = ElectricBlue,
    secondary = Violet,
    onSecondary = TextOnGradient,
    secondaryContainer = Violet.copy(alpha = 0.12f),
    onSecondaryContainer = Violet,
    tertiary = HabitPurple,
    onTertiary = TextOnGradient,
    tertiaryContainer = HabitPurple.copy(alpha = 0.12f),
    onTertiaryContainer = HabitPurple,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = DividerColor,
    error = ErrorRed,
    onError = TextOnGradient
)

@Composable
fun GrindBellTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = GrindBellColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BackgroundLight.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GrindBellTypography,
        shapes = GrindBellShapes,
        content = content
    )
}
