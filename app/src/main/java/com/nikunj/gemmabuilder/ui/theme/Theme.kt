package com.nikunj.gemmabuilder

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Calm neutral surfaces with a single indigo accent, in the style of current AI chat apps.

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF5B6475),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E9F0),
    onSecondaryContainer = Color(0xFF151A24),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0C2E44),
    background = Color(0xFFFBFBFC),
    onBackground = Color(0xFF14161B),
    surface = Color(0xFFFBFBFC),
    onSurface = Color(0xFF14161B),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF5C6270),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F6F8),
    surfaceContainer = Color(0xFFF0F1F4),
    surfaceContainerHigh = Color(0xFFEAECF0),
    surfaceContainerHighest = Color(0xFFE4E6EB),
    outline = Color(0xFFC8CCD5),
    outlineVariant = Color(0xFFE2E5EA),
    error = Color(0xFFD92D20),
    onError = Color.White,
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF55160C)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF34318A),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF9AA3B5),
    onSecondary = Color(0xFF1A202B),
    secondaryContainer = Color(0xFF262B36),
    onSecondaryContainer = Color(0xFFE3E7EE),
    tertiary = Color(0xFF7DD3FC),
    onTertiary = Color(0xFF082F49),
    tertiaryContainer = Color(0xFF0C3A55),
    onTertiaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFF0D0E12),
    onBackground = Color(0xFFE7E8EC),
    surface = Color(0xFF0D0E12),
    onSurface = Color(0xFFE7E8EC),
    surfaceVariant = Color(0xFF1D2028),
    onSurfaceVariant = Color(0xFF9CA2AF),
    surfaceContainerLowest = Color(0xFF09090C),
    surfaceContainerLow = Color(0xFF131519),
    surfaceContainer = Color(0xFF17191F),
    surfaceContainerHigh = Color(0xFF1E2128),
    surfaceContainerHighest = Color(0xFF262A32),
    outline = Color(0xFF3B404C),
    outlineVariant = Color(0xFF2A2E37),
    error = Color(0xFFF97066),
    onError = Color(0xFF55160C),
    errorContainer = Color(0xFF7A271A),
    onErrorContainer = Color(0xFFFEE4E2)
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val InterFamily = FontFamily(inter(400), inter(500), inter(600), inter(700))

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

private val AppTypography = Typography(
    displayLarge = style(52, 60, 600, -1.0),
    displayMedium = style(42, 50, 600, -0.8),
    displaySmall = style(34, 42, 600, -0.6),
    headlineLarge = style(30, 38, 600, -0.5),
    headlineMedium = style(26, 34, 600, -0.4),
    headlineSmall = style(22, 30, 600, -0.3),
    titleLarge = style(20, 28, 600, -0.2),
    titleMedium = style(16, 24, 600, -0.1),
    titleSmall = style(14, 20, 600),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(14, 21, 400),
    bodySmall = style(12, 17, 400),
    labelLarge = style(14, 20, 500),
    labelMedium = style(12, 16, 500),
    labelSmall = style(11, 15, 500, 0.1)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * App theme. [dynamicColor] uses the wallpaper-based Material You palette (Android 12+) instead of
 * the brand colors. Also keeps the status and navigation bar icons readable in both modes.
 */
@Composable
fun OnDeviceTheme(dark: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors: ColorScheme = when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
