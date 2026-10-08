package com.roni.blocks.feature.templatepicker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.Ticker
import com.roni.blocks.designsystem.AppBackground
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppChip
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.BlockStrip
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.blockColors
import com.roni.blocks.designsystem.templateDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Immutable
data class PickerCard(
    val id: TemplateId,
    val title: String,
    val subtitle: String,
    val summary: String,
    val icon: ImageVector,
    val accent: Color,
    val suggested: Boolean,
    val outlined: Boolean,
    val strip: ImmutableList<Pair<Color, Float>>,
)

@Immutable
data class PickerUiState(
    val greeting: String = "",
    val date: String = "",
    val cards: ImmutableList<PickerCard> = persistentListOf(),
)

sealed interface PickerAction {
    data class Pick(val id: TemplateId) : PickerAction
}

@HiltViewModel
/**
 * Lists the templates and previews what picking one would do to the rest of today. Rules: the
 * preview has to follow the clock, so state is republished when the hour changes rather than only on
 * open; applying a pick goes through [DayOrchestrator] so the day re-plans from now instead of the
 * template silently replacing history.
 *
 * Talks to: [DayOrchestrator], [TemplateManager], [Ticker].
 */
class TemplatePickerViewModel @Inject constructor(
    private val day: DayOrchestrator,
    private val templates: TemplateManager,
    private val ticker: Ticker,
    private val clock: Clock,
    private val zone: TimeZone,
) : ViewModel() {
    private val _state = MutableStateFlow(PickerUiState())
    val state = _state.asStateFlow()

    init {
        publish()
        viewModelScope.launch {
            ticker.minutes.distinctUntilChangedBy { it / 60 }.collect { publish() }
        }
    }

    fun onAction(action: PickerAction) {
        if (action is PickerAction.Pick) {
            if (action.id == TemplateId.OFF) day.send(DayEvent.TakeOffDay) else day.send(DayEvent.StartDay(action.id))
        }
    }

    private fun publish() {
        val local = clock.now().toLocalDateTime(zone)
        val today = todayTemplate(local.dayOfWeek)
        _state.value = PickerUiState(
            greeting = greetingFor(local.hour),
            date = "${local.dayOfWeek.title()}, ${local.day} ${local.month.name.title()}",
            cards = TemplateId.entries.map { id ->
                val display = templateDisplay(id)
                val template = templates.get(id)
                PickerCard(
                    id = id,
                    title = display.title,
                    subtitle = display.subtitle,
                    summary = summarize(template.blocks),
                    icon = display.icon,
                    accent = display.accent,
                    suggested = id == today,
                    outlined = id == TemplateId.OFF,
                    strip = stripFor(template.blocks),
                )
            }.toPersistentList(),
        )
    }
}

internal fun todayTemplate(day: DayOfWeek): TemplateId = when (day) {
    DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY -> TemplateId.SUN_TUE_WED
    DayOfWeek.MONDAY, DayOfWeek.THURSDAY -> TemplateId.MON_THU
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY -> TemplateId.FRI_SAT
}

internal fun greetingFor(hour: Int): String = when {
    hour < 12 -> "Good morning"
    hour < 17 -> "Good afternoon"
    else -> "Good evening"
}

private fun DayOfWeek.title(): String = name.title()

private fun String.title(): String = lowercase().replaceFirstChar { it.uppercase() }

private fun BlockSpec.minutes(): Int = (end.hour * 60 + end.minute) - (start.hour * 60 + start.minute)

private fun hoursLabel(minutes: Int): String =
    if (minutes % 60 == 0) "${minutes / 60}h" else "${minutes / 60}h ${minutes % 60}m"

internal fun summarize(blocks: List<BlockSpec>): String {
    if (blocks.isEmpty()) return "No blocks, no pings"
    val coding = blocks.filter { it.type == BlockType.Coding }.sumOf { it.minutes() }
    val free = blocks.filter { it.type == BlockType.Free }.sumOf { it.minutes() }
    val gym = blocks.any { it.type == BlockType.Gym }
    return when {
        coding > 0 -> if (gym) "${hoursLabel(coding)} coding · gym" else "${hoursLabel(coding)} coding"
        free > 0 -> "${hoursLabel(free)} free"
        else -> "No blocks, no pings"
    }
}

internal fun stripFor(blocks: List<BlockSpec>): ImmutableList<Pair<Color, Float>> =
    blocks.groupBy { it.type }
        .values
        .map { group -> blockColors(group.first().type).accent to group.size.toFloat() }
        .toPersistentList()

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val steel = colors.primary
    val sand = colors.secondary
    val lavender = blockColors(BlockType.Cv).accent
    val transition = rememberInfiniteTransition(label = "aurora")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit",
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(75_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val dust = remember {
        val random = Random(7)
        List(24) { Offset(random.nextFloat(), random.nextFloat()) to (1f + random.nextFloat()) }
    }
    Spacer(
        modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val radius = w * 0.7f
                fun blob(color: Color, alpha: Float, cx: Float, cy: Float, phase: Float) {
                    val a = (orbit + phase) * 2f * PI.toFloat()
                    val center = Offset(w * cx + cos(a) * w * 0.25f, h * cy + sin(a) * w * 0.18f)
                    drawCircle(
                        Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius),
                        radius,
                        center,
                    )
                }
                blob(steel, 0.18f, 0.25f, 0.25f, 0f)
                blob(sand, 0.12f, 0.8f, 0.55f, 0.33f)
                blob(lavender, 0.12f, 0.3f, 0.85f, 0.66f)
                dust.forEach { (point, size) ->
                    val y = ((point.y - drift) % 1f + 1f) % 1f
                    drawCircle(
                        Color.White.copy(alpha = 0.05f + 0.04f * (size - 1f)),
                        size.dp.toPx(),
                        Offset(point.x * w, y * h),
                    )
                }
            },
    )
}

@Composable
fun TemplatePickerRoute(onStarted: () -> Unit, viewModel: TemplatePickerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TemplatePickerScreen(
        state = state,
        onPick = {
            viewModel.onAction(PickerAction.Pick(it))
            onStarted()
        },
    )
}

@Composable
fun TemplatePickerScreen(state: PickerUiState, onPick: (TemplateId) -> Unit) {
    val colors = AppTheme.colors
    AppBackground {
        AuroraBackground()
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Spacing.l),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "${state.greeting}, Roni",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
            Text(state.date, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Text(
                "How does today look?",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = Spacing.xl),
            )
            state.cards.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    row.forEachIndexed { columnIndex, card ->
                        TemplateCard(
                            card = card,
                            index = rowIndex * 2 + columnIndex,
                            onClick = { onPick(card.id) },
                        )
                    }
                }
            }
            Text(
                "You can swap later from Home",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.l),
            )
        }
    }
}

@Composable
private fun RowScope.TemplateCard(card: PickerCard, index: Int, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        enter.animateTo(1f, tween(320, easing = LinearEasing))
    }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Metrics.pressScale else 1f,
        animationSpec = tween(120),
        label = "press",
    )
    AppCard(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(Metrics.cardAspect)
            .testTag("template_card_${card.id.name}")
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * Metrics.slide.toPx()
                scaleX = scale
                scaleY = scale
            },
        shape = AppShapes.hero,
        containerColor = if (card.outlined) Color.Transparent else null,
        spotColor = if (card.suggested) colors.secondary else null,
        borderColor = when {
            card.suggested -> colors.secondary
            card.outlined -> colors.outline
            else -> null
        },
        onClick = onClick,
        interactionSource = source,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(Metrics.iconLarge)
                        .clip(CircleShape)
                        .background(card.accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(card.icon, contentDescription = null, tint = card.accent)
                }
                Spacer(Modifier.weight(1f))
                if (card.suggested) AppChip("Suggested", colors.secondary)
            }
            Column {
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = card.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column {
                Text(
                    card.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.xs))
                BlockStrip(card.strip)
            }
        }
    }
}
