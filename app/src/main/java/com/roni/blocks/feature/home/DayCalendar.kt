package com.roni.blocks.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.roni.blocks.core.debug.RecompositionCounter
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppType
import com.roni.blocks.designsystem.BlockCorner
import com.roni.blocks.designsystem.Metrics
import kotlinx.collections.immutable.ImmutableList

const val DAY_START_MIN = 8 * 60
const val DAY_END_MIN = 23 * 60
val HourHeight = 64.dp
val Gutter = 56.dp
val TopInset = 8.dp

@Composable
fun DayCalendar(
    blocks: ImmutableList<CalendarBlockUi>,
    currentBlockId: String?,
    leakFromMin: Int?,
    hourLabels: ImmutableList<String>,
    minuteOfDay: () -> Int,
    scrollState: ScrollState,
    bottomPadding: Dp,
    onBlockClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SideEffect { RecompositionCounter.hit("DayCalendar") }
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.merge(AppType.numbers).copy(color = c.textSecondary)
    val gridHeight = TopInset + HourHeight * ((DAY_END_MIN - DAY_START_MIN) / 60f)

    Box(modifier.fillMaxWidth().verticalScroll(scrollState)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(gridHeight + bottomPadding),
        ) {
            HourGrid(hourLabels, Modifier.fillMaxWidth().height(gridHeight))
            BlocksLayout(
                blocks = blocks,
                currentBlockId = currentBlockId,
                minuteOfDay = minuteOfDay,
                onBlockClick = onBlockClick,
                modifier = Modifier.fillMaxWidth().height(gridHeight),
            )
            LeakOverlay(
                leakFromMin = leakFromMin,
                minuteOfDay = minuteOfDay,
                modifier = Modifier.fillMaxWidth().height(gridHeight),
            )
        }
    }
}

@Composable
private fun HourGrid(hourLabels: ImmutableList<String>, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.merge(AppType.numbers).copy(color = c.textSecondary)
    Box(
        modifier.drawWithCache {
            val pxPerMin = HourHeight.toPx() / 60f
            val gutterPx = Gutter.toPx()
            val top = TopInset.toPx()
            val labels: List<TextLayoutResult> = hourLabels.map { measurer.measure(it, labelStyle) }
            onDrawBehind {
                labels.forEachIndexed { i, label ->
                    val y = top + i * 60 * pxPerMin
                    drawLine(c.outline, Offset(gutterPx, y), Offset(size.width, y), Metrics.hairline.toPx())
                    if (i < labels.lastIndex) {
                        val half = y + 30 * pxPerMin
                        drawLine(
                            c.outline.copy(alpha = 0.5f),
                            Offset(gutterPx, half),
                            Offset(size.width, half),
                            Metrics.halfHairline.toPx(),
                        )
                    }
                    drawText(
                        label,
                        topLeft = Offset(gutterPx - Metrics.blockPadH.toPx() - label.size.width, y - label.size.height / 2f),
                    )
                }
            }
        },
    )
}

@Composable
private fun BlocksLayout(
    blocks: ImmutableList<CalendarBlockUi>,
    currentBlockId: String?,
    minuteOfDay: () -> Int,
    onBlockClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SideEffect { RecompositionCounter.hit("BlocksLayout") }
    Layout(
        modifier = modifier,
        content = {
            blocks.forEach { block ->
                key(block.id) {
                    CalendarBlock(block, block.id == currentBlockId, minuteOfDay, onBlockClick)
                }
            }
        },
    ) { measurables, constraints ->
        val pxPerMin = HourHeight.toPx() / 60f
        val left = (Gutter + Metrics.calendarGap).roundToPx()
        val width = constraints.maxWidth - left - Metrics.calendarRight.roundToPx()
        val gap = Metrics.calendarGap.toPx()
        val top = TopInset.toPx()
        val placeables = measurables.mapIndexed { i, m ->
            val b = blocks[i]
            val h = ((b.endMin - b.startMin) * pxPerMin - 2 * gap).roundToInt().coerceAtLeast(0)
            m.measure(Constraints.fixed(width, h))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                p.place(left, (top + (blocks[i].startMin - DAY_START_MIN) * pxPerMin + gap).roundToInt())
            }
        }
    }
}

@Composable
private fun CalendarBlock(
    block: CalendarBlockUi,
    isCurrent: Boolean,
    minuteOfDay: () -> Int,
    onClick: (String) -> Unit,
) {
    SideEffect { RecompositionCounter.hit("CalendarBlock") }
    val c = AppTheme.colors
    val palette = c.block(block.type)
    val lifted = lerp(palette.container, Color.White, 0.06f)
    val emphasis by animateFloatAsState(if (isCurrent) 1f else 0f, tween(250), label = "emphasis")

    BoxWithConstraints(
        Modifier
            .graphicsLayer {
                alpha = 0.6f + 0.4f * emphasis
                scaleX = 1f + 0.02f * emphasis
                scaleY = 1f + 0.02f * emphasis
                shadowElevation = Metrics.shadowBlock.toPx() * emphasis
                shape = AppShapes.block
                clip = true
                spotShadowColor = palette.accent.copy(alpha = 0.35f)
            }
            .drawBehind { drawRect(lerp(palette.container, lifted, emphasis)) }
            .then(
                if (isCurrent) Modifier.drawWithContent {
                    drawContent()
                    val y = (minuteOfDay() - block.startMin) * HourHeight.toPx() / 60f
                    if (y in 0f..size.height) {
                        drawLine(c.leak, Offset(0f, y), Offset(size.width, y), Metrics.nowLine.toPx())
                        drawCircle(c.leak, Metrics.nowDot.toPx(), Offset(Metrics.nowDot.toPx(), y))
                    }
                } else Modifier,
            )
            .clickable { onClick(block.id) }
            .padding(horizontal = Metrics.blockPadH, vertical = Metrics.blockPadV),
    ) {
        val timeStyle = MaterialTheme.typography.labelSmall.merge(AppType.numbers)
        val blockHeight = maxHeight
        if (blockHeight < Metrics.blockOneLineMax) {
            Text(
                "${block.title} · ${block.timeLabel}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Column {
                Text(block.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(block.timeLabel, style = timeStyle, color = palette.accent, maxLines = 1)
                if (blockHeight >= Metrics.dayRow && block.subtitle != null) {
                    Text(block.subtitle, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun LeakOverlay(leakFromMin: Int?, minuteOfDay: () -> Int, modifier: Modifier = Modifier) {
    if (leakFromMin == null) return
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.merge(AppType.numbers).copy(color = c.leak)
    Spacer(
        modifier.drawBehind {
            val now = minuteOfDay()
            if (now <= leakFromMin) return@drawBehind
            val pxPerMin = HourHeight.toPx() / 60f
            val left = (Gutter + Metrics.calendarGap).toPx()
            val right = size.width - Metrics.calendarRight.toPx()
            val top = TopInset.toPx() + (leakFromMin - DAY_START_MIN) * pxPerMin
            val bottom = TopInset.toPx() + (now - DAY_START_MIN) * pxPerMin
            drawRoundRect(
                c.leakContainer.copy(alpha = 0.85f),
                Offset(left, top),
                Size(right - left, bottom - top),
                CornerRadius(BlockCorner.toPx()),
            )
            drawLine(c.leak, Offset(left, top), Offset(right, top), Metrics.hairline.toPx())
            drawLine(c.leak, Offset(left, bottom), Offset(right, bottom), Metrics.nowLine.toPx())
            drawCircle(c.leak, Metrics.nowDot.toPx(), Offset(left + Metrics.nowDot.toPx(), bottom))
            if (bottom - top >= Metrics.leakLabelMin.toPx()) {
                val t = measurer.measure("+${now - leakFromMin} min", style)
                drawText(t, topLeft = Offset(right - t.size.width - Metrics.blockPadH.toPx(), bottom - t.size.height - Metrics.blockPadV.toPx()))
            }
        },
    )
}

/**
 * Diagonal hatching for the parts of a day that were not spent working on a block: a pause inside
 * a block, and downtime where the phone was off.
 */
fun DrawScope.hatch(rect: Rect, color: Color, gap: Dp = 6.dp) {
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        val step = gap.toPx()
        var x = rect.left - rect.height
        while (x < rect.right) {
            drawLine(color, Offset(x, rect.bottom), Offset(x + rect.height, rect.top), strokeWidth = 1.dp.toPx())
            x += step
        }
    }
}

/**
 * A finished day, drawn from [ActualDayMapper] output and nothing else. Same grid and block
 * styling as [DayCalendar], but with no now line, no current-block emphasis and no auto-scroll:
 * the day is over, so there is nothing left to follow.
 */
@Composable
fun ActualDayCanvas(
    day: ActualDayUi,
    hourLabels: ImmutableList<String>,
    scrollState: ScrollState,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    SideEffect { RecompositionCounter.hit("ActualDayCanvas") }
    val gridHeight = TopInset + HourHeight * ((DAY_END_MIN - DAY_START_MIN) / 60f)

    Box(modifier.fillMaxWidth().verticalScroll(scrollState)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(gridHeight + bottomPadding),
        ) {
            HourGrid(hourLabels, Modifier.fillMaxWidth().height(gridHeight))
            ActualBlocks(day.blocks, Modifier.fillMaxWidth().height(gridHeight))
            DowntimeOverlay(day.downtime, Modifier.fillMaxWidth().height(gridHeight))
            EmergencyOverlay(day.emergencyAtMin, Modifier.fillMaxWidth().height(gridHeight))
        }
    }
}

@Composable
private fun ActualBlocks(blocks: ImmutableList<ActualBlockUi>, modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier,
        content = {
            blocks.forEach { block ->
                key(block.id) { ActualBlock(block) }
            }
        },
    ) { measurables, constraints ->
        val pxPerMin = HourHeight.toPx() / 60f
        val left = (Gutter + Metrics.calendarGap).roundToPx()
        val width = constraints.maxWidth - left - Metrics.calendarRight.roundToPx()
        val gap = Metrics.calendarGap.toPx()
        val top = TopInset.toPx()
        val placeables = measurables.mapIndexed { i, m ->
            val b = blocks[i]
            val h = ((b.endMin - b.startMin) * pxPerMin - 2 * gap).roundToInt().coerceAtLeast(0)
            m.measure(Constraints.fixed(width, h))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                p.place(left, (top + (blocks[i].startMin - DAY_START_MIN) * pxPerMin + gap).roundToInt())
            }
        }
    }
}

@Composable
private fun ActualBlock(block: ActualBlockUi) {
    val c = AppTheme.colors
    val palette = c.block(block.type)
    val measurer = rememberTextMeasurer()
    val overStyle = MaterialTheme.typography.labelSmall.merge(AppType.numbers).copy(color = c.leak)

    BoxWithConstraints(
        Modifier
            .graphicsLayer {
                shape = AppShapes.block
                clip = true
            }
            .drawBehind { drawRect(palette.container) }
            .drawWithContent {
                drawContent()
                val pxPerMin = HourHeight.toPx() / 60f
                val plannedEnd = block.plannedEndMin
                if (plannedEnd != null && plannedEnd < block.endMin) {
                    val y = (plannedEnd - block.startMin) * pxPerMin
                    drawRect(
                        c.leakContainer.copy(alpha = 0.85f),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, size.height - y),
                    )
                    drawLine(c.leak, Offset(0f, y), Offset(size.width, y), Metrics.hairline.toPx())
                    if (size.height - y >= Metrics.leakLabelMin.toPx()) {
                        val t = measurer.measure("+${block.endMin - plannedEnd}m", overStyle)
                        drawText(
                            t,
                            topLeft = Offset(
                                size.width - t.size.width - Metrics.blockPadH.toPx(),
                                y + Metrics.blockPadV.toPx(),
                            ),
                        )
                    }
                }
                block.pauses.forEach { range ->
                    val top = (range.first - block.startMin) * pxPerMin
                    val bottom = (range.last - block.startMin) * pxPerMin
                    hatch(Rect(0f, top, size.width, bottom), c.textSecondary.copy(alpha = 0.6f))
                }
            }
            .padding(horizontal = Metrics.blockPadH, vertical = Metrics.blockPadV),
    ) {
        when {
            maxHeight >= Metrics.blockOneLineMax -> Column {
                Text(block.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    block.timeLabel,
                    style = MaterialTheme.typography.labelSmall.merge(AppType.numbers),
                    color = palette.accent,
                    maxLines = 1,
                )
            }

            maxHeight >= Metrics.blockTextMin -> Text(
                "${block.title} · ${block.timeLabel}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DowntimeOverlay(downtime: ImmutableList<IntRange>, modifier: Modifier = Modifier) {
    if (downtime.isEmpty()) return
    val c = AppTheme.colors
    Spacer(
        modifier.drawBehind {
            val pxPerMin = HourHeight.toPx() / 60f
            val left = (Gutter + Metrics.calendarGap).toPx()
            val right = size.width - Metrics.calendarRight.toPx()
            downtime.forEach { range ->
                val top = TopInset.toPx() + (range.first - DAY_START_MIN) * pxPerMin
                val bottom = TopInset.toPx() + (range.last - DAY_START_MIN) * pxPerMin
                hatch(Rect(left, top, right, bottom), c.textSecondary)
            }
        },
    )
}

@Composable
private fun EmergencyOverlay(emergencyAtMin: Int?, modifier: Modifier = Modifier) {
    if (emergencyAtMin == null) return
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.merge(AppType.numbers).copy(color = c.leak)
    Spacer(
        modifier.drawBehind {
            val pxPerMin = HourHeight.toPx() / 60f
            val left = (Gutter + Metrics.calendarGap).toPx()
            val right = size.width - Metrics.calendarRight.toPx()
            val y = TopInset.toPx() + (emergencyAtMin - DAY_START_MIN) * pxPerMin
            drawLine(c.leak, Offset(left, y), Offset(right, y), Metrics.nowLine.toPx())
            val t = measurer.measure("Emergency stop", style)
            drawText(t, topLeft = Offset(left, y - t.size.height - Metrics.blockPadV.toPx()))
        },
    )
}
