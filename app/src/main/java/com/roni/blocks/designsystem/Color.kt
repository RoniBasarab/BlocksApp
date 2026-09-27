package com.roni.blocks.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice

val BackgroundTop = Color(0xFF27282C)
val BackgroundBottom = Color(0xFF1C1B19)
val Background = Color(0xFF1C1B19)
val Surface = Color(0xFF312F2C)
val SurfaceRaised = Color(0xFF3A3834)
val Outline = Color(0xFF46433D)
val OutlineStrong = Color(0xFF56524B)
val Primary = Color(0xFF7A9CC6)
val OnPrimary = Color(0xFF1C1B19)
val Secondary = Color(0xFFD9C38A)
val OnSecondary = Color(0xFF1C1B19)
val TextPrimary = Color(0xFFE8E4DC)
val TextSecondary = Color(0xFFA8A298)
val TextMuted = Color(0xFF6F6A62)
val Leak = Color(0xFFD97070)
val LeakContainer = Color(0xFF4A2C2C)
val Good = Color(0xFF8FB58A)
val Lavender = Color(0xFFA99BC9)

@Immutable
data class BlockColors(val container: Color, val accent: Color)

@Immutable
data class AppColors(
    val background: Color = Background,
    val backgroundTop: Color = BackgroundTop,
    val backgroundBottom: Color = BackgroundBottom,
    val surface: Color = Surface,
    val surfaceRaised: Color = SurfaceRaised,
    val outline: Color = Outline,
    val outlineStrong: Color = OutlineStrong,
    val primary: Color = Primary,
    val onPrimary: Color = OnPrimary,
    val secondary: Color = Secondary,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textMuted: Color = TextMuted,
    val leak: Color = Leak,
    val leakContainer: Color = LeakContainer,
    val good: Color = Good,
    val neutralTrend: Color = TextSecondary,
    val blocks: Map<String, BlockColors> = emptyMap(),
) {
    fun block(type: BlockType): BlockColors = blockColors(type)
}

val LocalAppColors = staticCompositionLocalOf { AppPalette }

val AppPalette = AppColors()

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

fun blockColors(type: BlockType): BlockColors = when (type) {
    BlockType.Cv -> BlockColors(Color(0xFF3B3548), Color(0xFFA99BC9))
    BlockType.Coding -> BlockColors(Color(0xFF2E3B4D), Color(0xFF7A9CC6))
    BlockType.Gym -> BlockColors(Color(0xFF2F3F33), Color(0xFF8FB58A))
    BlockType.Free -> BlockColors(Color(0xFF463E2C), Color(0xFFD9C38A))
    BlockType.KidTime -> BlockColors(Color(0xFF33312D), Color(0xFF9C968C))
    BlockType.Dishes -> BlockColors(Color(0xFF2A3E3D), Color(0xFF7FB3AE))
    is BlockType.EveningChoice -> when (type.choice) {
        Choice.GAME -> BlockColors(Color(0xFF47332E), Color(0xFFC98F7A))
        Choice.PROJECT -> BlockColors(Color(0xFF333A52), Color(0xFF8C9BD1))
        Choice.READ -> BlockColors(Color(0xFF3D3F2C), Color(0xFFB8B77E))
        Choice.CHILL -> BlockColors(Color(0xFF43343F), Color(0xFFB58FAA))
    }
}
