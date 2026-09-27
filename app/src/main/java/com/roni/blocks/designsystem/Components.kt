package com.roni.blocks.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class TrendDirection { UP, DOWN, FLAT }

@Composable
fun ScreenScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = topBar,
        bottomBar = { bottomBar?.invoke() },
    ) { inner ->
        Box(Modifier.padding(horizontal = Spacing.l)) {
            content(inner)
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = AppShapes.card
    val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f)))
    Column(
        modifier
            .shadow(Metrics.shadow, shape, spotColor = Color.Black.copy(alpha = 0.6f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(Metrics.hairline, edge, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Spacing.l),
        content = content,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(top = Spacing.xl, bottom = Spacing.s),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun StatTile(icon: ImageVector, iconTint: Color, value: String, label: String, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Box(
            Modifier.size(Metrics.iconCircle).clip(AppShapes.chip).background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = iconTint) }
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
            StatTile(Icons.Outlined.HourglassBottom, Primary, "12 min", "Leak")
            TrendPill(TrendDirection.DOWN, "4 min", true)
            AppChip("Suggested", Secondary)
        }
    }
}
