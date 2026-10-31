package com.homebooks.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private val HomeBooksColorScheme = lightColorScheme(
    primary = Color(0xFF6538D4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE6FF),
    onPrimaryContainer = Color(0xFF2D175E),
    secondary = Color(0xFFF08A66),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE8DF),
    onSecondaryContainer = Color(0xFF542416),
    tertiary = Color(0xFF52AFA0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDF5EF),
    onTertiaryContainer = Color(0xFF123D36),
    background = Color(0xFFF7F5FC),
    onBackground = Color(0xFF292538),
    surface = Color(0xFFFFFEFF),
    onSurface = Color(0xFF292538),
    surfaceVariant = Color(0xFFF0EDF7),
    onSurfaceVariant = Color(0xFF696477),
    outline = Color(0xFFD8D3E3),
    outlineVariant = Color(0xFFE7E2EF),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val HomeBooksShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

private val HomeBooksTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = FontFamily.SansSerif),
    displayMedium = Typography().displayMedium.copy(fontFamily = FontFamily.SansSerif),
    displaySmall = Typography().displaySmall.copy(fontFamily = FontFamily.SansSerif),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = FontFamily.SansSerif),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = FontFamily.SansSerif),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = FontFamily.SansSerif),
    titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.SansSerif),
    titleMedium = Typography().titleMedium.copy(fontFamily = FontFamily.SansSerif),
    titleSmall = Typography().titleSmall.copy(fontFamily = FontFamily.SansSerif),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = Typography().bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = Typography().labelLarge.copy(fontFamily = FontFamily.SansSerif),
    labelMedium = Typography().labelMedium.copy(fontFamily = FontFamily.SansSerif),
    labelSmall = Typography().labelSmall.copy(fontFamily = FontFamily.SansSerif)
)

@Composable
fun HomeBooksTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HomeBooksColorScheme,
        typography = HomeBooksTypography,
        shapes = HomeBooksShapes,
        content = content
    )
}
