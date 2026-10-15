package com.roni.blocks.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roni.blocks.designsystem.icons.AppIcons

enum class TrendDirection { UP, DOWN, FLAT }

@Composable
fun ScreenScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable (() -> Unit)? = null,
    snackbarHost: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            // Zero here on purpose: the top bar owns the status bar and the navigation bar owns
            // the gesture bar. Applying either again is what pushed screens down twice.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = topBar,
            bottomBar = { bottomBar?.invoke() },
            snackbarHost = { snackbarHost?.invoke() },
        ) { inner ->
            Box(Modifier.padding(inner)) {
                content(PaddingValues(0.dp))
            }
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    shape: Shape = AppShapes.card,
    containerColor: Color? = null,
    spotColor: Color? = null,
    elevated: Boolean = true,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.l),
    content: @Composable ColumnScope.() -> Unit,
) {
    val appColors = AppTheme.colors
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val border = borderColor?.let {
        Modifier.border(Metrics.borderSuggested, it, shape)
    } ?: Modifier.border(Metrics.hairline, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))), shape)
    CompositionLocalProvider(LocalContentColor provides appColors.textPrimary) {
        Column(
            modifier
                .then(
                    if (elevated) Modifier.shadow(
                        Metrics.shadowCard,
                        shape,
                        spotColor = spotColor?.copy(alpha = 0.4f) ?: Color.Black.copy(alpha = 0.6f),
                    ) else Modifier,
                )
                .clip(shape)
                .background(containerColor ?: appColors.surface)
                .then(border)
                .then(if (onClick != null) Modifier.clickable(interactionSource = source, indication = null, onClick = onClick) else Modifier)
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(top = Spacing.xl, bottom = Spacing.s, start = Spacing.xs),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun IconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = Metrics.iconCircleRow,
    iconSize: Dp = Metrics.iconInCircle,
    tint: Color = AppTheme.colors.primary,
    shape: Shape = CircleShape,
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = tint)
    }
}

@Composable
fun StatTile(icon: ImageVector, iconTint: Color, value: String, label: String, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        IconCircle(icon, size = Metrics.iconCircle, tint = iconTint, shape = AppShapes.chip)
        Text(value, style = MaterialTheme.typography.titleLarge.merge(AppType.numbers), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TrendPill(direction: TrendDirection, text: String, isGood: Boolean?, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val color = when (isGood) {
        true -> colors.good
        false -> colors.leak
        null -> colors.neutralTrend
    }
    val arrow = when (direction) {
        TrendDirection.UP -> "↑"
        TrendDirection.DOWN -> "↓"
        TrendDirection.FLAT -> "→"
    }
    Text(
        "$arrow $text",
        modifier.clip(AppShapes.chip).background(color.copy(alpha = 0.16f)).padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Composable
fun BlockStrip(
    segments: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier,
    height: Dp = Metrics.stripHeight,
) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().size(height)) {
        if (segments.isEmpty()) {
            drawRoundRect(
                color = outline,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                ),
                cornerRadius = CornerRadius(size.height / 2),
            )
            return@Canvas
        }
        val gap = 1.dp.toPx()
        val total = segments.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)
        var x = 0f
        segments.forEach { (color, weight) ->
            val w = (size.width - gap * (segments.size - 1)) * (weight / total)
            drawRoundRect(color, Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2))
            x += w + gap
        }
    }
}

@Composable
fun AppChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.clip(AppShapes.chip).background(color.copy(alpha = 0.16f)).padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        textAlign = TextAlign.Center,
    )
}

@Preview
@Composable
private fun ComponentsPreview() {
    BlocksTheme {
        Column {
            SectionHeader("Today")
            StatTile(AppIcons.HourglassBottom, Primary, "12 min", "Leak")
            TrendPill(TrendDirection.DOWN, "4 min", true)
            AppChip("Suggested", Secondary)
        }
    }
}
