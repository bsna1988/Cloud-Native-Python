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
    primary = Color(0xFF287A52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEFE3),
    onPrimaryContainer = Color(0xFF123D29),
    secondary = Color(0xFFE58B63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE8DC),
    onSecondaryContainer = Color(0xFF552712),
    tertiary = Color(0xFF4F9889),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDF2E9),
    onTertiaryContainer = Color(0xFF153D32),
    background = Color(0xFFF4F8F3),
    onBackground = Color(0xFF202B24),
    surface = Color(0xFFFFFEFC),
    onSurface = Color(0xFF202B24),
    surfaceVariant = Color(0xFFEBF1EA),
    onSurfaceVariant = Color(0xFF58655B),
    outline = Color(0xFFCBD6CC),
    outlineVariant = Color(0xFFDCE5DC),
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
