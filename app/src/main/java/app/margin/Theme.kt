package app.margin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class MarginColors(
    val paper: Color,
    val card: Color,
    val ink: Color,
    val graphite: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color
)

val LightColors = MarginColors(
    paper = Color(0xFFF4EFE6),
    card = Color(0xFFFBF8F2),
    ink = Color(0xFF23201B),
    graphite = Color(0xFF5B5750),
    line = Color(0xFFDDD4C3),
    accent = Color(0xFFB8432A),
    onAccent = Color(0xFFFFFFFF)
)

val DarkColors = MarginColors(
    paper = Color(0xFF1B1916),
    card = Color(0xFF25221E),
    ink = Color(0xFFF1EBDD),
    graphite = Color(0xFFADA79B),
    line = Color(0xFF37332D),
    accent = Color(0xFFE58A6E),
    onAccent = Color(0xFF1B1916)
)

val LocalMarginColors = staticCompositionLocalOf { LightColors }

@Composable
fun MarginTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalMarginColors provides (if (dark) DarkColors else LightColors)) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
            content = content
        )
    }
}
