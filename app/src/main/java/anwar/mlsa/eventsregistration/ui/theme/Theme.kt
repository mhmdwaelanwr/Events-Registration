package anwar.mlsa.eventsregistration.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlueDark,
    secondary = SuccessGreen,
    background = DarkBackground,
    surface = Color(0xFF101D2D),
    onBackground = Color(0xFFF2F6FC),
    onSurface = Color(0xFFF2F6FC)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    secondary = SuccessGreen,
    background = AppBackground,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Ink,
    onSurface = Ink
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
        content = content
    )
}
