package com.roni.blocks.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.roni.blocks.core.debug.RecompositionCounter
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.schedule.BlockEdit
import com.roni.blocks.core.schedule.Edge
import com.roni.blocks.core.schedule.PlanEditor
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppType
import com.roni.blocks.designsystem.BlockCorner
import com.roni.blocks.designsystem.Metrics
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

const val DAY_START_MIN = 8 * 60
const val DAY_END_MIN = 23 * 60
val HourHeight = 80.dp
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
    selectedBlockId: String?,
    draggingId: String?,
    onAction: (HomeAction) -> Unit,
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
                selectedBlockId = selectedBlockId,
                draggingId = draggingId,
                minuteOfDay = minuteOfDay,
                onBlockClick = onBlockClick,
                onAction = onAction,
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
    selectedBlockId: String?,
    draggingId: String?,
    minuteOfDay: () -> Int,
    onBlockClick: (String) -> Unit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SideEffect { RecompositionCounter.hit("BlocksLayout") }
    val anim = remember {
        mutableStateMapOf<String, Pair<Animatable<Float, AnimationVector1D>, Animatable<Float, AnimationVector1D>>>()
    }
    LaunchedEffect(blocks, draggingId) {
        val spec = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)
        val ids = blocks.mapTo(HashSet()) { it.id }
        anim.keys.filter { it !in ids }.forEach { anim.remove(it) }
        blocks.forEach { b ->
            val (start, end) = anim.getOrPut(b.id) { Animatable(b.startMin.toFloat()) to Animatable(b.endMin.toFloat()) }
            if (b.id == draggingId) {
                start.snapTo(b.startMin.toFloat())
                end.snapTo(b.endMin.toFloat())
            } else {
                launch { start.animateTo(b.startMin.toFloat(), spec) }
                launch { end.animateTo(b.endMin.toFloat(), spec) }
            }
        }
    }
    val selected = blocks.firstOrNull { it.id == selectedBlockId }
    val c = AppTheme.colors

    Layout(
        modifier = modifier,
        contents = listOf(
            {
                blocks.forEach { block ->
                    key(block.id) {
                        CalendarBlock(
                            block = block,
                            isCurrent = block.id == currentBlockId,
                            isSelected = block.id == selectedBlockId,
                            minuteOfDay = minuteOfDay,
                            onClick = onBlockClick,
                        )
                    }
                }
            },
            {
                if (selected != null) {
                    val accent = c.block(selected.type).accent
                    when (selected.edit) {
                        BlockEdit.FULL -> {
                            EdgeHandle(selected.id, Edge.TOP, selected.startMin, accent, onAction, Modifier.zIndex(2f))
                            EdgeHandle(selected.id, Edge.BOTTOM, selected.endMin, accent, onAction, Modifier.zIndex(2f))
                        }
                        BlockEdit.END_AND_TYPE, BlockEdit.END_ONLY ->
                            EdgeHandle(selected.id, Edge.BOTTOM, selected.endMin, accent, onAction, Modifier.zIndex(2f))
                        else -> Unit
                    }
                }
            },
        ),
    ) { measurables, constraints ->
        val pxPerMin = HourHeight.toPx() / 60f
        val left = (Gutter + Metrics.calendarGap).roundToPx()
        val width = constraints.maxWidth - left - Metrics.calendarRight.roundToPx()
        val gap = Metrics.calendarGap.toPx()
        val top = TopInset.toPx()
        val handlePx = 48.dp.roundToPx()
        val blockPlaceables = measurables[0].mapIndexed { i, m ->
            val b = blocks[i]
            val start = anim[b.id]?.first?.value ?: b.startMin.toFloat()
            val end = anim[b.id]?.second?.value ?: b.endMin.toFloat()
            val h = ((end - start) * pxPerMin - 2 * gap).roundToInt().coerceAtLeast(0)
            m.measure(Constraints.fixed(width, h)) to start
        }
        val handlePlaceables = measurables[1].map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            blockPlaceables.forEach { (p, start) ->
                p.place(left, (top + (start - DAY_START_MIN) * pxPerMin + gap).roundToInt())
            }
            if (selected != null && handlePlaceables.isNotEmpty()) {
                val start = anim[selected.id]?.first?.value ?: selected.startMin.toFloat()
                val end = anim[selected.id]?.second?.value ?: selected.endMin.toFloat()
                val topY = top + (start - DAY_START_MIN) * pxPerMin
                val bottomY = top + (end - DAY_START_MIN) * pxPerMin
                val rightX = left + width - handlePx
                when (selected.edit) {
                    BlockEdit.FULL -> {
                        handlePlaceables.getOrNull(0)?.place(left, (topY - handlePx / 2f).roundToInt())
                        handlePlaceables.getOrNull(1)?.place(rightX, (bottomY - handlePx / 2f).roundToInt())
                    }
                    BlockEdit.END_AND_TYPE, BlockEdit.END_ONLY ->
                        handlePlaceables.getOrNull(0)?.place(rightX, (bottomY - handlePx / 2f).roundToInt())
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun CalendarBlock(
    block: CalendarBlockUi,
    isCurrent: Boolean,
    isSelected: Boolean,
    minuteOfDay: () -> Int,
    onClick: (String) -> Unit,
) {
    SideEffect { RecompositionCounter.hit("CalendarBlock") }
    val c = AppTheme.colors
    val palette = c.block(block.type)
    val lifted = lerp(palette.container, Color.White, 0.06f)
    val emphasis by animateFloatAsState(if (isCurrent) 1f else 0f, tween(250), label = "emphasis")
    val measurer = rememberTextMeasurer()
    val sand = MaterialTheme.colorScheme.secondary
    val small = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp)

    BoxWithConstraints(
        Modifier
            .zIndex(if (isSelected) 1f else 0f)
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
            .drawWithContent {
                drawContent()
                if (block.phases.isNotEmpty()) {
                    val pxPerMin = size.height / (block.endMin - block.startMin)
                    val now = minuteOfDay()
                    block.phases.forEach { p ->
                        val top = (p.startMin - block.startMin) * pxPerMin
                        val bottom = (p.endMin - block.startMin) * pxPerMin
                        if (p.isBreak) {
                            drawRect(sand.copy(alpha = 0.16f), Offset(0f, top), Size(size.width, bottom - top))
                            drawLine(sand.copy(alpha = 0.45f), Offset(0f, top), Offset(size.width, top), 1.dp.toPx())
                            drawLine(sand.copy(alpha = 0.45f), Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
                        }
                        if (!isCurrent) return@forEach
                        val text = phaseLabel(p, now) ?: return@forEach
                        val color = when {
                            p.isBreak -> sand
                            now >= p.endMin -> c.textMuted
                            now >= p.startMin -> c.textPrimary
                            else -> c.textSecondary
                        }
                        val layout = measurer.measure(text, small.copy(color = color))
                        if (layout.size.height > bottom - top) return@forEach
                        val x = size.width - 4.dp.toPx() - layout.size.width
                        val y = if (p.isBreak) top + (bottom - top - layout.size.height) / 2 else top + 4.dp.toPx()
                        drawText(layout, topLeft = Offset(x, y))
                    }
                }
                if (block.type == BlockType.Walk) {
                    val midY = size.height / 2f
                    drawLine(palette.accent.copy(alpha = 0.6f), Offset(0f, midY), Offset(size.width, midY), Metrics.hairline.toPx())
                    val layout = measurer.measure("turn", small.copy(color = palette.accent))
                    val x = size.width - 4.dp.toPx() - layout.size.width
                    val y = midY + 2.dp.toPx()
                    if (y + layout.size.height <= size.height) drawText(layout, topLeft = Offset(x, y))
                }
                if (isCurrent) {
                    val y = (minuteOfDay() - block.startMin) * HourHeight.toPx() / 60f
                    if (y in 0f..size.height) {
                        drawLine(c.leak, Offset(0f, y), Offset(size.width, y), Metrics.nowLine.toPx())
                        drawCircle(c.leak, Metrics.nowDot.toPx(), Offset(Metrics.nowDot.toPx(), y))
                    }
                }
                if (isSelected) {
                    drawRoundRect(
                        color = palette.accent,
                        cornerRadius = CornerRadius(BlockCorner.toPx()),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
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

@Composable
private fun EdgeHandle(
    blockId: String,
    edge: Edge,
    edgeMin: Int,
    accent: Color,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pxPerMin = with(LocalDensity.current) { HourHeight.toPx() / 60f }
    val currentEdge by rememberUpdatedState(edgeMin)
    Box(
        modifier
            .size(48.dp)
            .pointerInput(blockId, edge) {
                var startMin = 0
                var dragPx = 0f
                var lastMin = 0
                detectVerticalDragGestures(
                    onDragStart = {
                        startMin = currentEdge
                        dragPx = 0f
                        lastMin = startMin
                    },
                    onDragEnd = { onAction(HomeAction.DragDone(blockId, edge, lastMin)) },
                    onDragCancel = { onAction(HomeAction.DragCancelled) },
                ) { change, dy ->
                    change.consume()
                    dragPx += dy
                    val m = PlanEditor.snap(startMin + (dragPx / pxPerMin).roundToInt())
                    if (m != lastMin) {
                        lastMin = m
                        onAction(HomeAction.DragEdge(blockId, edge, m))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(18.dp).background(AppTheme.colors.surface, CircleShape).padding(2.dp).background(accent, CircleShape))
    }
}
