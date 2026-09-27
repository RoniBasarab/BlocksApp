package com.roni.blocks.feature.templatepicker

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppChip
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.BlockStrip
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.blockColors
import com.roni.blocks.core.model.BlockType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class PickerCard(
    val id: TemplateId,
    val name: String,
    val caption: String,
    val summary: String,
    val icon: ImageVector,
    val accent: Color,
    val suggested: Boolean,
)

@Immutable
data class PickerState(val greeting: String = "Good morning", val date: String = "", val cards: ImmutableList<PickerCard> = persistentListOf())

sealed interface PickerAction {
    data class Pick(val id: TemplateId) : PickerAction
}

@HiltViewModel
class TemplatePickerViewModel @Inject constructor(
    private val day: DayOrchestrator,
) : ViewModel() {
    fun onAction(action: PickerAction) {
        if (action is PickerAction.Pick) {
            if (action.id == TemplateId.OFF) day.send(DayEvent.TakeOffDay) else day.send(DayEvent.StartDay(action.id))
        }
    }
}

@Composable
fun TemplatePickerRoute(onStarted: () -> Unit, viewModel: TemplatePickerViewModel = hiltViewModel()) {
    val t = rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Restart))
    val cards = pickerCards()
    Box(
        Modifier.fillMaxSize().drawBehind {
            val time = t.value
            drawRect(Color(0xFF1C1B19))
            val w = size.width
            listOf(0f to Color(0xFF7A9CC6), 0.33f to Color(0xFFD9C38A), 0.66f to Color(0xFFA99BC9)).forEachIndexed { i, (_, color) ->
                val angle = (time + i / 3f) * 6.28f
                val c = androidx.compose.ui.geometry.Offset(w * (0.5f + 0.25f * cos(angle)), size.height * (0.4f + 0.12f * sin(angle)))
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent), c, w * 0.7f), w * 0.7f, c)
            }
        },
    ) {
        Column(
            Modifier.fillMaxSize().padding(Spacing.l),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Good morning, Roni", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Text("Sunday, 27 September", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("How does today look?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.xl))
            cards.chunked(2).forEach { row ->
                Row(Modifier.padding(top = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    row.forEach { card ->
                        AppCard(Modifier.weight(1f).aspectRatio(Metrics.cardAspect), onClick = {
                            viewModel.onAction(PickerAction.Pick(card.id))
                            onStarted()
                        }) {
                            if (card.suggested) AppChip("Suggested", MaterialTheme.colorScheme.secondary)
                            IconLabel(card)
                            Text(card.summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            BlockStrip(if (card.id == TemplateId.OFF) emptyList() else listOf(card.accent to 1f, blockColors(BlockType.Free).accent to 1f))
                        }
                    }
                }
            }
            Text("You can swap later from Home", style = MaterialTheme.typography.labelMedium, color = LocalTextMuted(), modifier = Modifier.padding(top = Spacing.l))
        }
    }
}

@Composable
private fun IconLabel(card: PickerCard) {
    androidx.compose.material3.Icon(card.icon, null, tint = card.accent)
    Text(card.name, style = MaterialTheme.typography.titleMedium)
    Text(card.caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LocalTextMuted() = com.roni.blocks.designsystem.TextMuted

@Composable
private fun pickerCards(): List<PickerCard> = listOf(
    PickerCard(TemplateId.MON_THU, "Gym day", "MON · THU", "3h coding · gym", Icons.Outlined.FitnessCenter, blockColors(BlockType.Gym).accent, false),
    PickerCard(TemplateId.SUN_TUE_WED, "Focus day", "SUN · TUE · WED", "3h 30m coding", Icons.Outlined.Code, blockColors(BlockType.Coding).accent, true),
    PickerCard(TemplateId.FRI_SAT, "Family day", "FRI · SAT", "2h free", Icons.Outlined.Weekend, blockColors(BlockType.Free).accent, false),
    PickerCard(TemplateId.OFF, "Off day", "ANY DAY", "No blocks, no pings", Icons.Outlined.SportsEsports, blockColors(BlockType.EveningChoice(com.roni.blocks.core.model.Choice.CHILL)).accent, false),
)
