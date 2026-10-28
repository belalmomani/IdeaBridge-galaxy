package com.ideabridge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// الأفكار = نيلي (primary) ، الروابط = تيل (secondary) ، الاقتراحات = بنفسجي (tertiary)
private val LightColors = lightColorScheme(
    primary = Color(0xFF3F4BC4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF0E1559),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F2EA),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF7B3FC4),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0DEFF),
    onTertiaryContainer = Color(0xFF2B0A55),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFF7F8FC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C0FF),
    onPrimary = Color(0xFF1B2380),
    primaryContainer = Color(0xFF2E3AA8),
    onPrimaryContainer = Color(0xFFE0E3FF),
    secondary = Color(0xFF7FD8C9),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFC8F2EA),
    tertiary = Color(0xFFD9B8FF),
    onTertiary = Color(0xFF42157F),
    tertiaryContainer = Color(0xFF5B2AA0),
    onTertiaryContainer = Color(0xFFF0DEFF),
    background = Color(0xFF0B1224),
    surface = Color(0xFF0B1224),
)

@Composable
fun IdeaBridgeTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
