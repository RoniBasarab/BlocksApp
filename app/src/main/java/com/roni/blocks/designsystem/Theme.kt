package com.roni.blocks.designsystem

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Scheme = darkColorScheme(
    background = BackgroundBottom,
    surface = Surface,
    surfaceContainerLowest = BackgroundBottom,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceRaised,
    surfaceContainerHighest = SurfaceRaised,
    surfaceVariant = SurfaceRaised,
    surfaceTint = Color.Transparent,
    inverseSurface = TextPrimary,
    inverseOnSurface = BackgroundBottom,
    scrim = Color.Black.copy(alpha = 0.6f),
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    error = Leak,
    outline = Outline,
    outlineVariant = Outline,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
)

object AppType {
    val numbers = TextStyle(fontFeatureSettings = "tnum")
}

val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 36.sp).merge(AppType.numbers),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 24.sp).merge(AppType.numbers),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp).merge(AppType.numbers),
)

/**
 * Root theme. [LocalContentColor] is provided here on purpose: text without an
 * explicit color falls back to it, and its Material default is black.
 */
@Composable
fun BlocksTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppColors provides AppPalette,
        LocalContentColor provides AppPalette.textPrimary,
    ) {
        MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
    }
}
