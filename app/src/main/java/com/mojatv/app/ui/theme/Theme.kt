package com.mojatv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFE5484D)
val Background = Color(0xFF0E1116)
val Surface = Color(0xFF161A21)
val SurfaceHigh = Color(0xFF1F242D)
val TextSecondary = Color(0xFF9BA3AF)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1A1C),
    onPrimaryContainer = Color(0xFFFFDAD9),
    secondary = Color(0xFF9BA3AF),
    background = Background,
    onBackground = Color(0xFFECEFF4),
    surface = Background,
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = Color(0xFF272D37),
    surfaceContainerLow = Surface,
    surfaceContainerLowest = Background,
    secondaryContainer = Color(0xFF3A1F22),
    onSecondaryContainer = Color(0xFFFFDAD9),
    outline = Color(0xFF3A414D),
    outlineVariant = Color(0xFF2A303A),
)

@Composable
fun MojaTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
