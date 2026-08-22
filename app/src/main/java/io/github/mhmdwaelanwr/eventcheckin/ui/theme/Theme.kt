package io.github.mhmdwaelanwr.eventcheckin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = FluentBlueDark,
    onPrimary = Color(0xFF002A3A),
    primaryContainer = Color(0xFF153A4A),
    onPrimaryContainer = Color(0xFFD9F2FF),
    secondary = Color(0xFF92C353),
    error = Color(0xFFFF99A4),
    errorContainer = Color(0xFF5A1E22),
    background = Color(0xFF18181B),
    surface = Color(0xFF242427),
    surfaceVariant = Color(0xFF303036),
    onBackground = Color(0xFFF5F7FA),
    onSurface = Color(0xFFF5F7FA),
    onSurfaceVariant = Color(0xFFB9C0CA),
    outline = Color(0xFF46464D),
    outlineVariant = Color(0xFF35353A)
)

private val LightColorScheme = lightColorScheme(
    primary = FluentBlue,
    primaryContainer = Color(0xFFEEF6FC),
    onPrimaryContainer = Color(0xFF0F3A55),
    secondary = FluentGreen,
    error = FluentRed,
    background = FluentBackground,
    surface = FluentSurface,
    surfaceVariant = Color(0xFFF8F8F8),
    onPrimary = Color.White,
    onBackground = FluentInk,
    onSurface = FluentInk,
    onSurfaceVariant = FluentSecondary,
    outline = FluentStroke,
    outlineVariant = Color(0xFFE8EBEF)
)

private val FluentShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun EventCheckInTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = FluentShapes,
        content = content
    )
}
