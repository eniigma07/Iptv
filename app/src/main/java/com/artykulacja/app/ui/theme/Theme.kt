package com.artykulacja.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.artykulacja.app.R

object C {
    val Bg = Color(0xFF0A0E1A)
    val Surface = Color(0xFF111728)
    val Surface2 = Color(0xFF182035)
    val Surface3 = Color(0xFF212B44)
    val Line = Color(0xFF26304A)
    val Text = Color(0xFFF3F1EC)
    val Text2 = Color(0xFFA9B1C6)
    val Text3 = Color(0xFF6E7892)
    val Gold = Color(0xFFD9B26F)
    val GoldSoft = Color(0xFF2A2417)
    val GoldDeep = Color(0xFFB08A47)
    val Good = Color(0xFF4FD1A1)
    val Mid = Color(0xFFF2B544)
    val Bad = Color(0xFFFF6B6B)
    val Model = Color(0xFF8FA3FF)

    fun score(s: Float): Color = when {
        s >= 0.80f -> Good
        s >= 0.55f -> Mid
        else -> Bad
    }

    fun scoreSoft(s: Float): Color = score(s).copy(alpha = 0.14f)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val Serif = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_italic, FontWeight.Normal, FontStyle.Italic),
)

private val type = Typography(
    displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.02).em),
    displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.01).em),
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em),
)

private val colors = darkColorScheme(
    primary = C.Gold,
    onPrimary = Color(0xFF1A1408),
    primaryContainer = C.GoldSoft,
    onPrimaryContainer = C.Gold,
    secondary = C.Model,
    background = C.Bg,
    onBackground = C.Text,
    surface = C.Bg,
    onSurface = C.Text,
    surfaceVariant = C.Surface2,
    onSurfaceVariant = C.Text2,
    surfaceContainer = C.Surface,
    surfaceContainerHigh = C.Surface2,
    surfaceContainerHighest = C.Surface3,
    surfaceContainerLow = C.Surface,
    surfaceContainerLowest = C.Bg,
    outline = C.Line,
    outlineVariant = C.Line,
    error = C.Bad,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
