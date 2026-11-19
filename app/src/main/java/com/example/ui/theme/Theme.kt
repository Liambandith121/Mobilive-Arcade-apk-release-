package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ArcadeDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianNight,
    primaryContainer = NeonCyanContainer,
    onPrimaryContainer = NeonCyan,
    secondary = ArcadeViolet,
    onSecondary = TextPrimary,
    secondaryContainer = ArcadeVioletContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldPulse,
    onTertiary = ObsidianNight,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldPulse,
    background = ObsidianNight,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianNight,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    surfaceContainerHighest = ObsidianSurfaceHighest,
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorder.copy(alpha = 0.6f),
    error = LiveCrimson,
    onError = TextPrimary,
    errorContainer = LiveCrimsonContainer,
    onErrorContainer = TextPrimary
)

val ArcadeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MobiliveArcadeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ArcadeDarkColorScheme,
        typography = ArcadeTypography,
        shapes = ArcadeShapes,
        content = content
    )
}
