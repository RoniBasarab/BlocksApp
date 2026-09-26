package com.roni.blocks.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Scheme = darkColorScheme(
    background = Background,
    surface = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceRaised,
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

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

val AppTypography = Typography(
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = TextSecondary).merge(Tabular),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, color = TextPrimary).merge(Tabular),
)

@Composable
fun BlocksTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppColors provides AppColors()) {
        MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
    }
}
