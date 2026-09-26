package com.roni.blocks.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.roni.blocks.designsystem.Outline
import com.roni.blocks.designsystem.TextSecondary
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
    Box(
        modifier.drawBehind {
            val px = size.height / (END_MINUTE - START_MINUTE)
            var minute = START_MINUTE
            while (minute <= END_MINUTE) {
                val y = (minute - START_MINUTE) * px
                drawLine(Outline, androidx.compose.ui.geometry.Offset(48.dp.toPx(), y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
                minute += 60
            }
        },
    ) {
        Layout(
            content = {
                blocks.forEachIndexed { index, block ->
                    Text(
                        "${block.title} ${block.range}",
                        modifier = Modifier.graphicsLayer {
                            alpha = if (index == currentIndex || currentIndex < 0) 1f else 0.6f
                            scaleX = if (index == currentIndex) 1.02f else 1f
                            scaleY = if (index == currentIndex) 1.02f else 1f
                        },
                    )
                }
            },
        ) { measurables, constraints ->
            val px = constraints.maxHeight.toFloat() / (END_MINUTE - START_MINUTE)
            val placeables = measurables.mapIndexed { index, measurable ->
                val block = blocks[index]
                val height = ((block.endMinute - block.startMinute) * px).toInt().coerceAtLeast(1)
                measurable.measure(constraints.copy(minHeight = height, maxHeight = height, minWidth = constraints.maxWidth - 64))
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { index, placeable ->
                    val y = ((blocks[index].startMinute - START_MINUTE) * px).toInt()
                    placeable.place(64, y)
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val minute = minuteOfDay()
            if (currentIndex !in blocks.indices) return@Canvas
            val block = blocks[currentIndex]
            if (minute !in block.startMinute..block.endMinute) return@Canvas
            val px = size.height / (END_MINUTE - START_MINUTE)
            val y = (minute - START_MINUTE) * px
            drawLine(Color(0xFFD97070), androidx.compose.ui.geometry.Offset(64f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.5.dp.toPx())
        }
    }
}
