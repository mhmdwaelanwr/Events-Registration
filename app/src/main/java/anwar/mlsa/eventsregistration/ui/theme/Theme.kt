package anwar.mlsa.eventsregistration.ui.theme

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
    secondary = FluentGreen,
    error = Color(0xFFFF99A4),
    background = FluentDarkBackground,
    surface = FluentDarkSurface,
    surfaceVariant = Color(0xFF333333),
    onBackground = Color(0xFFF2F6FC),
    onSurface = Color(0xFFF2F6FC),
    onSurfaceVariant = Color(0xFFC7C7C7),
    outline = Color(0xFF666666)
)

private val LightColorScheme = lightColorScheme(
    primary = FluentBlue,
    secondary = FluentGreen,
    error = FluentRed,
    background = FluentBackground,
    surface = FluentSurface,
    surfaceVariant = Color(0xFFF8F8F8),
    onPrimary = Color.White,
    onBackground = FluentInk,
    onSurface = FluentInk,
    onSurfaceVariant = FluentSecondary,
    outline = FluentStroke
)

private val FluentShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun MLSAEgyptEventsRegistrationTheme(
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
