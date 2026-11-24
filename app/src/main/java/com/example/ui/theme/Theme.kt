package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PulsePadDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBg,
    primaryContainer = Color(0xFF003845),
    onPrimaryContainer = Color(0xFF9DF2FF),
    secondary = NeonIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF232054),
    onSecondaryContainer = Color(0xFFD9D6FF),
    tertiary = ButtonAEmerald,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = CarbonElevated,
    onSurfaceVariant = TextSecondary,
    outline = CarbonBorder,
    outlineVariant = Color(0xFF1F2738),
    error = StatusOfflineRed,
    onError = Color.White
)

private val PulsePadShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PulsePadTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PulsePadDarkColorScheme,
        typography = PulsePadTypography,
        shapes = PulsePadShapes,
        content = content
    )
}
