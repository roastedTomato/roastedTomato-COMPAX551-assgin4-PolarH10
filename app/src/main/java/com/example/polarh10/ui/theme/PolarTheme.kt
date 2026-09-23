package com.example.polarh10.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.polarh10.processing.MovementLevel

val AppBackground = Color(0xFF080D13)
val Panel = Color(0xFF121821)
val PanelStrong = Color(0xFF18212C)
val Stroke = Color(0xFF263241)
val Good = Color(0xFF42D742)
val Warning = Color(0xFFFF9F1A)
val Danger = Color(0xFFFF3B3B)
val MutedText = Color(0xFF9AA4B2)

val PolarDarkColors = darkColorScheme(
    background = AppBackground,
    surface = Panel,
    surfaceVariant = PanelStrong,
    primary = Good,
    secondary = Color(0xFF24B7FF),
    tertiary = Warning,
    error = Danger,
    onBackground = Color(0xFFF5F7FA),
    onSurface = Color(0xFFF5F7FA),
    onSurfaceVariant = MutedText
)


@Composable
fun heartRateColor(hr: Int?): Color =
    when {
        hr == null -> MaterialTheme.colorScheme.onSurface
        hr >= 150 -> MaterialTheme.colorScheme.error
        hr >= 130 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

@Composable
fun movementColor(level: MovementLevel): Color =
    when (level) {
        MovementLevel.HIGH -> MaterialTheme.colorScheme.error
        MovementLevel.MODERATE -> MaterialTheme.colorScheme.tertiary
        MovementLevel.LOW -> MaterialTheme.colorScheme.primary
        MovementLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurface
    }
