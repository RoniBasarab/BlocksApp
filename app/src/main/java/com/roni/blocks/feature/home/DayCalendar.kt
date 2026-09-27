package com.roni.blocks.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import com.roni.blocks.designsystem.Metrics
import kotlinx.collections.immutable.ImmutableList

private const val START_MINUTE = 8 * 60
private const val END_MINUTE = 23 * 60

@Composable
fun DayCalendar(
    blocks: ImmutableList<HomeBlock>,
    currentIndex: Int,
    minuteOfDay: () -> Int,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline
    val leak = MaterialTheme.colorScheme.error
    Box(
        modifier.fillMaxSize().drawBehind {
            val px = Metrics.hourHeight.toPx() / 60f
            var minute = START_MINUTE
            while (minute <= END_MINUTE) {
                val y = (minute - START_MINUTE) * px
                drawLine(outline, androidx.compose.ui.geometry.Offset(Metrics.gutter.toPx(), y), androidx.compose.ui.geometry.Offset(size.width, y), Metrics.hairline.toPx())
                minute += 60
            }
        },
    ) {
        Layout(content = {
            blocks.forEachIndexed { index, block ->
                Text(
                    "${block.title} ${block.range}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer { alpha = if (index == currentIndex || currentIndex < 0) 1f else 0.6f },
                )
            }
        }) { measurables, constraints ->
            val px = Metrics.hourHeight.toPx() / 60f
            val placeables = measurables.mapIndexed { index, measurable ->
                val block = blocks[index]
                val height = ((block.endMinute - block.startMinute) * px).toInt().coerceAtLeast(1)
                measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
            }
            layout(constraints.maxWidth, (Metrics.hourHeight.toPx() * 15).toInt()) {
                placeables.forEachIndexed { index, placeable ->
                    val y = ((blocks[index].startMinute - START_MINUTE) * px).toInt()
                    placeable.place(Metrics.gutter.roundToPx(), y)
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val minute = minuteOfDay()
            if (currentIndex !in blocks.indices) return@Canvas
            val block = blocks[currentIndex]
            if (minute !in block.startMinute..block.endMinute) return@Canvas
            val px = Metrics.hourHeight.toPx() / 60f
            val y = (minute - START_MINUTE) * px
            drawLine(leak, androidx.compose.ui.geometry.Offset(Metrics.gutter.toPx(), y), androidx.compose.ui.geometry.Offset(size.width, y), Metrics.nowLine.toPx())
        }
    }
}
