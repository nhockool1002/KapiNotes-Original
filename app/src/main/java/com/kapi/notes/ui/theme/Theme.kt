package com.kapi.notes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Kapi500,
    onPrimary = Color.White,
    primaryContainer = Kapi50,
    onPrimaryContainer = Kapi700,
    secondary = InkMuted,
    onSecondary = Color.White,
    tertiary = AccentYellow,
    onTertiary = InkMain,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92650C),
    background = Paper50,
    surface = Color.White,
    onSurface = InkMain,
    surfaceVariant = Paper100,
    onSurfaceVariant = InkMuted,
    outline = Paper200,
    outlineVariant = Paper100,
    error = AccentCoral,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Kapi400,
    onPrimary = Kapi900,
    primaryContainer = Kapi800,
    onPrimaryContainer = Kapi100,
    secondary = Color(0xFF94A3B8),
    tertiary = Color(0xFFFCD34D),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = InkMain,
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A)
)

// A soft, rounded shape scale (used throughout dashboard/dialog redesign) instead of the
// sharp-cornered Material defaults - cards, buttons and dialogs all read as one consistent system.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// Outfit for headings/titles (brand "heading" font), Plus Jakarta Sans for body/labels
// (brand "body/UI" font) - matches the brand guidelines' typographic pairing.
private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = OutfitFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp),
    headlineSmall = TextStyle(fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = OutfitFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

@Composable
fun KapiNotesTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
