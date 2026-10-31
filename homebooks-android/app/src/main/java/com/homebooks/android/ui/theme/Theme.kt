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
    primary = Color(0xFF2868B2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6F0FC),
    onPrimaryContainer = Color(0xFF15385E),
    secondary = Color(0xFFE38B62),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE9DF),
    onSecondaryContainer = Color(0xFF552A18),
    tertiary = Color(0xFF4F8E9C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2F1F4),
    onTertiaryContainer = Color(0xFF173B43),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF202A36),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202A36),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF586779),
    outline = Color(0xFFCBD6E2),
    outlineVariant = Color(0xFFE0E7EF),
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
