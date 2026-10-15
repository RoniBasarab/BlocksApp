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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
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
import com.roni.blocks.core.model.shortFor
import com.roni.blocks.core.profiles.Profiles
import com.roni.blocks.core.storage.ProfileActions
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.core.system.AppRescue
import com.roni.blocks.core.system.Failures
import com.roni.blocks.core.system.ProblemAction
import com.roni.blocks.core.time.Ticker
import com.roni.blocks.designsystem.AppBackground
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppChip
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.BlockStrip
import com.roni.blocks.designsystem.ErrorCard
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.blockColors
import com.roni.blocks.designsystem.icons.AppIcons
import com.roni.blocks.designsystem.templateDisplay
import com.roni.blocks.feature.profiles.HowToAction
import com.roni.blocks.feature.profiles.HowToUse
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
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
    /** "MON · THU" from the active profile, or "" when no day suggests it (3.2.0). */
    val days: String = "",
)

@Immutable
data class PickerUiState(
    val greeting: String = "",
    val date: String = "",
    val cards: ImmutableList<PickerCard> = persistentListOf(),
    val profileName: String = "",
    val profiles: ImmutableList<Pair<String, String>> = persistentListOf(),
    val profileMenu: Boolean = false,
    val howTo: Boolean = false,
    val howToPage: Int = 0,
    val failed: Boolean = false,
)

sealed interface PickerAction {
    data class Pick(val id: TemplateId) : PickerAction
    data object OpenProfileMenu : PickerAction
    data class SwitchProfile(val id: String) : PickerAction
    data object DismissProfileMenu : PickerAction
    data object HowTo : PickerAction
    data class HowToPage(val page: Int) : PickerAction
    data object CloseHowTo : PickerAction
    data class Problem(val action: ProblemAction) : PickerAction
}

/** What the picker asks the screen around it to do once a pick is applied. */
sealed interface PickerEffect {
    data object Started : PickerEffect
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
    private val templates: TemplateStore,
    private val profiles: ProfileActions,
    private val ticker: Ticker,
    private val clock: Clock,
    private val zone: TimeZone,
    private val failures: Failures,
    private val rescue: AppRescue,
) : ViewModel() {
    private val _state = MutableStateFlow(PickerUiState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<PickerEffect>()
    val effects: SharedFlow<PickerEffect> = _effects.asSharedFlow()

    private fun fail() = _state.update { it.copy(failed = true) }

    init {
        publish()
        failures.launch(viewModelScope, "Picker: hour", onFailure = ::fail) {
            ticker.minutes.distinctUntilChangedBy { it / 60 }.collect { publish() }
        }
        failures.launch(viewModelScope, "Picker: templates", onFailure = ::fail) {
            templates.templates.collect { publish() }
        }
    }

    fun onAction(action: PickerAction) {
        when (action) {
            is PickerAction.Pick -> {
                if (action.id == TemplateId.OFF) day.send(DayEvent.TakeOffDay) else day.send(DayEvent.StartDay(action.id))
                failures.launch(viewModelScope, "Picker: started", onFailure = ::fail) { _effects.emit(PickerEffect.Started) }
            }
            PickerAction.OpenProfileMenu -> _state.update { it.copy(profileMenu = true) }
            is PickerAction.SwitchProfile -> {
                _state.update { it.copy(profileMenu = false) }
                failures.launch(viewModelScope, "Picker: switch profile", onFailure = ::fail) { profiles.switchTo(action.id) }
            }
            PickerAction.DismissProfileMenu -> _state.update { it.copy(profileMenu = false) }
            PickerAction.HowTo -> _state.update { it.copy(howTo = true, howToPage = 0) }
            is PickerAction.HowToPage -> _state.update { it.copy(howToPage = action.page) }
            PickerAction.CloseHowTo -> _state.update { it.copy(howTo = false) }
            is PickerAction.Problem -> rescue.handle(action.action)
        }
    }

    private fun publish() {
        val local = clock.now().toLocalDateTime(zone)
        val today = templates.suggestedFor(local.dayOfWeek)
        val book = templates.profiles.value
        val cards = templates.all().map { template ->
            val display = templateDisplay(template.id)
            val days = Profiles.daysOf(book.active, template.id)
            PickerCard(
                id = template.id,
                title = display.title,
                subtitle = display.subtitle,
                summary = summarize(template.blocks),
                icon = display.icon,
                accent = display.accent,
                suggested = template.id == today,
                outlined = template.id == TemplateId.OFF,
                strip = stripFor(template.blocks),
                days = if (days.isEmpty()) "" else shortFor(days),
            )
        }.toPersistentList()
        _state.update {
            it.copy(
                greeting = greetingFor(local.hour),
                date = "${local.dayOfWeek.title()}, ${local.day} ${local.month.name.title()}",
                cards = cards,
                profileName = book.active.name,
                profiles = book.profiles.map { profile -> profile.id to profile.name }.toPersistentList(),
            )
        }
    }
}

internal fun todayTemplate(day: DayOfWeek): TemplateId =
    TemplateStore.instance?.suggestedFor(day) ?: seedSuggestion(day)

/** The weekdays 2.x shipped, used when no store is up (previews and pure tests). */
private fun seedSuggestion(day: DayOfWeek): TemplateId = when (day) {
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
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PickerEffect.Started -> onStarted()
            }
        }
    }
    TemplatePickerScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun TemplatePickerScreen(state: PickerUiState, onAction: (PickerAction) -> Unit) {
    val colors = AppTheme.colors
    AppBackground {
        AuroraBackground()
        if (state.failed) {
            ErrorCard(
                onAction = { onAction(PickerAction.Problem(it)) },
                modifier = Modifier
                    .padding(Spacing.l)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            )
        } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("picker_grid"),
            contentPadding = PaddingValues(Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Box(Modifier.testTag("picker_profile").clickable { onAction(PickerAction.OpenProfileMenu) }) {
                                AppChip("${state.profileName} ▾", colors.primary)
                            }
                            DropdownMenu(
                                expanded = state.profileMenu,
                                onDismissRequest = { onAction(PickerAction.DismissProfileMenu) },
                                shape = AppShapes.card,
                            ) {
                                state.profiles.forEach { (id, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = { onAction(PickerAction.SwitchProfile(id)) },
                                        trailingIcon = if (name == state.profileName) {
                                            { Icon(AppIcons.Check, "Active") }
                                        } else null,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onAction(PickerAction.HowTo) }, modifier = Modifier.testTag("how_to")) {
                            Icon(AppIcons.HelpOutline, "How to use")
                        }
                    }
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
                }
            }
            itemsIndexed(state.cards, key = { _, card -> card.id.key }) { index, card ->
                TemplateCard(
                    card = card,
                    index = index,
                    onClick = { onAction(PickerAction.Pick(card.id)) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
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
        if (state.howTo) {
            HowToUse(state.howToPage) { howToAction ->
                onAction(
                    when (howToAction) {
                        is HowToAction.Page -> PickerAction.HowToPage(howToAction.page)
                        HowToAction.Close -> PickerAction.CloseHowTo
                    },
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(card: PickerCard, index: Int, onClick: () -> Unit) {
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
            .fillMaxWidth()
            .aspectRatio(Metrics.cardAspect)
            .testTag("template_card_${card.id.key}")
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * Metrics.slide.toPx()
                scaleX = scale
                scaleY = scale
            },
        shape = AppShapes.hero,
        containerColor = if (card.outlined) Color.Transparent else null,
        elevated = !card.outlined,
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
                if (card.days.isNotEmpty()) {
                    Text(
                        card.days,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
