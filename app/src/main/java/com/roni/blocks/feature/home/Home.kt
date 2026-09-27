package com.roni.blocks.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.daystate.EndReason
import com.roni.blocks.core.daystate.isDishesSlot
import com.roni.blocks.core.daystate.templateId
import com.roni.blocks.core.debug.RecompositionCounter
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.TemplateManager
import com.roni.blocks.core.time.Ticker
import com.roni.blocks.core.time.minuteOfDay
import com.roni.blocks.designsystem.AppBottomBar
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppChip
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTab
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.AppType
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.templateDisplay
import com.roni.blocks.feature.weekly.tabKey
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.SettingsKey
import com.roni.blocks.navigation.TimeDebtKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val day: DayOrchestrator,
    private val templates: TemplateManager,
    private val ticker: Ticker,
    private val clock: Clock,
    private val zone: TimeZone,
    private val prefs: PrefsStorage,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _suppressedConfirmations = MutableStateFlow<Set<NowButtonId>>(emptySet())

    /** Buttons the user chose "Don't ask again" for; survives process death. */
    val suppressedConfirmations: StateFlow<Set<NowButtonId>> = _suppressedConfirmations.asStateFlow()

    private var selectedBlockId: String? = null
    private var eveningSheetOpen = false

    val minuteOfDay: StateFlow<Int> =
        ticker.minutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val secondOfDay: StateFlow<Int> =
        ticker.seconds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val countdownText: StateFlow<String> =
        combine(_state, minuteOfDay, secondOfDay) { s, minute, second -> countdown(s, minute, second) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val progress: StateFlow<Float> =
        combine(_state, minuteOfDay) { s, minute -> progressOf(s, minute) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    init {
        viewModelScope.launch { day.state.collect { publish(it) } }
        // Leaking is a boundary, not a per-minute value: only re-publish when it flips.
        viewModelScope.launch { minuteOfDay.collect { publishLeakIfChanged() } }
        viewModelScope.launch {
            val stored = prefs.read(
                PrefsFile.SETTINGS,
                SUPPRESSED_CONFIRMATIONS,
                SuppressedConfirmations.serializer(),
            )
            _suppressedConfirmations.value = suppressedFromKeys(stored?.ids.orEmpty())
        }
    }

    fun onSuppressConfirmation(button: NowButtonId) {
        if (button in _suppressedConfirmations.value) return
        val updated = _suppressedConfirmations.value + button
        _suppressedConfirmations.value = updated
        viewModelScope.launch {
            prefs.write(
                PrefsFile.SETTINGS,
                SUPPRESSED_CONFIRMATIONS,
                SuppressedConfirmations(keysFromSuppressed(updated)),
                SuppressedConfirmations.serializer(),
            )
        }
    }

    fun onAskAgain(button: NowButtonId) {
        if (button !in _suppressedConfirmations.value) return
        val updated = _suppressedConfirmations.value - button
        _suppressedConfirmations.value = updated
        viewModelScope.launch {
            if (updated.isEmpty()) {
                prefs.remove(PrefsFile.SETTINGS, SUPPRESSED_CONFIRMATIONS)
            } else {
                prefs.write(
                    PrefsFile.SETTINGS,
                    SUPPRESSED_CONFIRMATIONS,
                    SuppressedConfirmations(keysFromSuppressed(updated)),
                    SuppressedConfirmations.serializer(),
                )
            }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Next -> day.send(DayEvent.Next(_state.value.currentBlockId.orEmpty()))
            HomeAction.Pause -> day.send(DayEvent.Pause)
            HomeAction.Emergency -> day.send(DayEvent.EndDay(EndReason.EMERGENCY))
            HomeAction.Resume -> day.send(DayEvent.Resume)
            HomeAction.KidAsleep -> day.send(DayEvent.KidAsleep)
            HomeAction.ChooseEvening -> eveningSheetOpen = true
            HomeAction.EndDay -> day.send(DayEvent.EndDay(EndReason.NORMAL))
            is HomeAction.PickEvening -> {
                eveningSheetOpen = false
                day.send(DayEvent.EveningChoice(action.choice))
            }
            is HomeAction.BlockClicked -> selectedBlockId = action.id
            HomeAction.DismissBlockSheet -> selectedBlockId = null
            HomeAction.SwapTemplate,
            HomeAction.History,
            HomeAction.TimeDebt,
            HomeAction.Settings,
            -> Unit
        }
        if (action.affectsOverlays) publish(day.state.value)
    }

    private fun publish(dayState: DayState) {
        val now = clock.now()
        val minute = now.minuteOfDay(zone)
        val id = dayState.templateId()
        val template = id?.let { runCatching { templates.get(it) }.getOrNull() }
        val blocks = template?.let { CalendarMapper.toBlocks(it) } ?: persistentListOf()
        val index = currentIndex(dayState)
        val current = blocks.getOrNull(index)
        val segments = day.segments.value
        val open = segments.lastOrNull { it.kind == SegmentKind.PLANNED && it.actualEnd == null }
        val paused = segments
            .filter { it.kind == SegmentKind.PAUSE }
            .sumOf { ((it.actualEnd ?: now) - it.actualStart).inWholeMinutes }
            .toInt()
        val effectiveEndMin = open?.plannedEnd?.let { it.minuteOfDay(zone) + paused }
        val leaking = current != null && effectiveEndMin != null && minute > effectiveEndMin
        val nextLabel = blocks.getOrNull(index + 1)

        if (dayState is DayState.Ended || dayState is DayState.Off || dayState is DayState.NotStarted) {
            selectedBlockId = null
        }
        if (!dayState.isDishesSlot) eveningSheetOpen = false

        _state.value = HomeState(
            weekdayShort = now.toLocalDateTime(zone).dayOfWeek.name.take(3),
            dayOfMonth = now.toLocalDateTime(zone).day.toString(),
            title = id?.let { templateDisplay(it).title }.orEmpty(),
            status = status(blocks, index, leaking, effectiveEndMin, minute),
            hourLabels = CalendarMapper.hourLabels().toPersistentList(),
            blocks = blocks,
            currentBlockId = current?.id,
            leakFromMin = if (leaking) effectiveEndMin else null,
            nowCard = nowCard(dayState, current, index, blocks.size, leaking, effectiveEndMin, minute, nextLabel),
            eveningChoicePending = dayState.isDishesSlot,
            eveningSheetOpen = eveningSheetOpen,
            blockSheet = blockSheet(blocks.firstOrNull { it.id == selectedBlockId }, segments, minute),
        )
    }

    private fun publishLeakIfChanged() {
        val before = _state.value.leakFromMin
        publish(day.state.value)
        if (_state.value.leakFromMin != before) return
        // Undo cosmetic churn when only the flag is unchanged.
        if (before == null) _state.value = _state.value.copy(leakFromMin = null)
    }

    private fun currentIndex(dayState: DayState): Int = when (dayState) {
        is DayState.Running -> dayState.blockIndex
        is DayState.Paused -> dayState.running.blockIndex
        is DayState.Evening -> dayState.blockIndex
        else -> -1
    }

    private fun blockSheet(
        block: CalendarBlockUi?,
        segments: List<Segment>,
        minute: Int,
    ): BlockSheetUi? {
        if (block == null) return null
        val own = segments.filter { it.plannedStart?.minuteOfDay(zone) == block.startMin }
        val main = own.firstOrNull()
        val actualStart = main?.actualStart?.minuteOfDay(zone)
        val actualEnd = main?.actualEnd?.minuteOfDay(zone)
        val actual = actualStart?.let { start ->
            "${hhmm(start)}–${hhmm(actualEnd ?: minute)}"
        }
        val drift = if (actualStart != null && actualEnd != null) {
            (actualEnd - actualStart) - (block.endMin - block.startMin)
        } else {
            null
        }
        val pauses = segments.filter {
            it.kind == SegmentKind.PAUSE && it.plannedStart?.minuteOfDay(zone)?.let { m -> m in block.startMin until block.endMin } == true
        }
        val pausedMin = pauses.sumOf { ((it.actualEnd ?: it.actualStart) - it.actualStart).inWholeMinutes }.toInt()
        return BlockSheetUi(
            title = block.title,
            timeLabel = block.timeLabel,
            planned = block.timeLabel,
            actual = actual,
            delta = drift?.let { if (it > 0) "+$it min" else if (it < 0) "−${-it} min" else "on time" },
            pauses = when {
                pauses.isEmpty() -> null
                pauses.size == 1 -> "1 pause · $pausedMin min"
                else -> "${pauses.size} pauses · $pausedMin min"
            },
        )
    }

    private fun status(
        blocks: List<CalendarBlockUi>,
        index: Int,
        leaking: Boolean,
        effectiveEndMin: Int?,
        minute: Int,
    ): String {
        if (blocks.isEmpty()) return if (index < 0) "Pick a template" else ""
        val left = (blocks.size - index - 1).coerceAtLeast(0)
        val leak = if (leaking && effectiveEndMin != null) " · leak ${minute - effectiveEndMin} min" else ""
        return "$left blocks left$leak"
    }

    private fun nowCard(
        dayState: DayState,
        current: CalendarBlockUi?,
        index: Int,
        total: Int,
        leaking: Boolean,
        effectiveEndMin: Int?,
        minute: Int,
        next: CalendarBlockUi?,
    ): NowCardUi? {
        val nextTitle = next?.title
        return when (dayState) {
            DayState.NotStarted -> NowCardUi(
                title = "Ready when you are",
                type = null,
                chip = null,
                chipTone = ChipTone.NEUTRAL,
                subtitle = "Pick a template to start the day",
                isLeaking = false,
                showProgress = false,
                buttons = persistentListOf(NowButton(NowButtonId.NEXT, "Start", NowButtonStyle.PRIMARY)),
            )
            is DayState.Running -> {
                val title = current?.title ?: "Running"
                val pomodoro = current?.subtitle != null
                if (leaking) {
                    NowCardUi(
                        title = title,
                        type = current?.type,
                        chip = "Ran over",
                        chipTone = ChipTone.LEAK,
                        subtitle = effectiveEndMin?.let { "$title ended at ${hhmm(it)}" },
                        isLeaking = true,
                        showProgress = false,
                        buttons = buildList {
                            nextTitle?.let { add(NowButton(NowButtonId.NEXT, "Start $it", NowButtonStyle.PRIMARY)) }
                            add(NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY))
                        }.toPersistentList(),
                    )
                } else {
                    NowCardUi(
                        title = title,
                        type = current?.type,
                        chip = if (pomodoro) "Focus" else null,
                        chipTone = ChipTone.NEUTRAL,
                        subtitle = effectiveEndMin?.let { "until ${hhmm(it)}" },
                        isLeaking = false,
                        showProgress = pomodoro,
                        buttons = persistentListOf(
                            NowButton(NowButtonId.PAUSE, "Pause", NowButtonStyle.SECONDARY),
                            NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
                        ),
                    )
                }
            }
            is DayState.Paused -> NowCardUi(
                title = "Paused",
                type = current?.type,
                chip = null,
                chipTone = ChipTone.NEUTRAL,
                subtitle = effectiveEndMin?.let { "until ${hhmm(it)}" },
                isLeaking = false,
                showProgress = false,
                buttons = persistentListOf(
                    NowButton(NowButtonId.RESUME, "Resume", NowButtonStyle.PRIMARY),
                    NowButton(NowButtonId.EMERGENCY, "Emergency", NowButtonStyle.SECONDARY),
                ),
            )
            DayState.KidTime -> NowCardUi(
                title = "Kid time",
                type = BlockType.KidTime,
                chip = null,
                chipTone = ChipTone.NEUTRAL,
                subtitle = "Evening starts when you tap Kid asleep",
                isLeaking = false,
                showProgress = false,
                buttons = persistentListOf(
                    NowButton(NowButtonId.KID_ASLEEP, "Kid asleep", NowButtonStyle.PRIMARY),
                    NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY),
                ),
            )
            is DayState.Evening -> if (dayState.isDishesSlot) {
                NowCardUi(
                    title = "Dishes",
                    type = BlockType.Dishes,
                    chip = null,
                    chipTone = ChipTone.NEUTRAL,
                    subtitle = "Pick what tonight is about",
                    isLeaking = false,
                    showProgress = false,
                    buttons = persistentListOf(
                        NowButton(NowButtonId.CHOOSE_EVENING, "Choose evening", NowButtonStyle.PRIMARY),
                        NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY),
                    ),
                )
            } else {
                NowCardUi(
                    title = current?.title ?: "Evening",
                    type = current?.type,
                    chip = null,
                    chipTone = ChipTone.NEUTRAL,
                    subtitle = "until ${hhmm(22 * 60 + 30)}",
                    isLeaking = false,
                    showProgress = false,
                    buttons = persistentListOf(NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY)),
                )
            }
            DayState.Ended -> NowCardUi(
                title = "Day done",
                type = null,
                chip = null,
                chipTone = ChipTone.NEUTRAL,
                subtitle = "Leak ${(effectiveEndMin?.let { minute - it } ?: 0).coerceAtLeast(0)} min · $total blocks",
                isLeaking = false,
                showProgress = false,
                buttons = persistentListOf(),
            )
            DayState.Off -> NowCardUi(
                title = "Off day. Enjoy.",
                type = null,
                chip = null,
                chipTone = ChipTone.NEUTRAL,
                subtitle = null,
                isLeaking = false,
                showProgress = false,
                buttons = persistentListOf(),
            )
        }
    }

    private fun countdown(s: HomeState, minute: Int, second: Int): String {
        val block = s.blocks.firstOrNull { it.id == s.currentBlockId } ?: return ""
        if (minute < block.endMin) return "${block.endMin - minute} min left"
        val over = (minute - block.endMin) * 60 + second
        val h = over / 3600
        val m = (over % 3600) / 60
        val sec = over % 60
        return if (h > 0) "-$h:${"%02d".format(m)}:${"%02d".format(sec)}" else "-${"%02d".format(m)}:${"%02d".format(sec)}"
    }

    private fun progressOf(s: HomeState, minute: Int): Float {
        val block = s.blocks.firstOrNull { it.id == s.currentBlockId } ?: return 0f
        val span = (block.endMin - block.startMin).coerceAtLeast(1)
        return ((minute - block.startMin).toFloat() / span).coerceIn(0f, 1f)
    }
}

@Composable
fun HomeRoute(
    onMenu: (NavKey) -> Unit,
    onSwap: () -> Unit,
    onTab: (NavKey) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val minute by viewModel.minuteOfDay.collectAsStateWithLifecycle()
    val countdown by viewModel.countdownText.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val suppressed by viewModel.suppressedConfirmations.collectAsStateWithLifecycle()
    val minuteOfDay = { minute }
    val countdownText = { countdown }
    val progressValue = { progress }

    ScreenScaffold(bottomBar = { AppBottomBar(AppTab.HOME) { onTab(tabKey(it)) } }) { _ ->
        HomeScreen(
            state = state,
            minuteOfDay = minuteOfDay,
            countdownText = countdownText,
            progress = progressValue,
            suppressedConfirmations = suppressed,
            onSuppressConfirmation = viewModel::onSuppressConfirmation,
            onAskAgain = viewModel::onAskAgain,
            onAction = { action ->
                when (action) {
                    HomeAction.SwapTemplate -> onSwap()
                    HomeAction.History -> onMenu(HistoryKey)
                    HomeAction.TimeDebt -> onMenu(TimeDebtKey)
                    HomeAction.Settings -> onMenu(SettingsKey)
                    else -> viewModel.onAction(action)
                }
            },
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeState,
    minuteOfDay: () -> Int,
    countdownText: () -> String,
    progress: () -> Float,
    onAction: (HomeAction) -> Unit,
    suppressedConfirmations: Set<NowButtonId> = emptySet(),
    onSuppressConfirmation: (NowButtonId) -> Unit = {},
    onAskAgain: (NowButtonId) -> Unit = {},
) {
    SideEffect { RecompositionCounter.hit("HomeScreen") }
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(state.currentBlockId) {
        val start = state.blocks.firstOrNull { it.id == state.currentBlockId }?.startMin
            ?: return@LaunchedEffect
        val target = with(density) {
            TopInset.toPx() + (start - DAY_START_MIN) * HourHeight.toPx() / 60f - Metrics.scrollLeadIn.toPx()
        }
        scroll.animateScrollTo(target.toInt().coerceAtLeast(0))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(
                weekdayShort = state.weekdayShort,
                dayOfMonth = state.dayOfMonth,
                title = state.title,
                status = state.status,
                showDivider = { scroll.value > 0 },
                onAction = onAction,
            )
            DayCalendar(
                blocks = state.blocks,
                currentBlockId = state.currentBlockId,
                leakFromMin = state.leakFromMin,
                hourLabels = state.hourLabels,
                minuteOfDay = minuteOfDay,
                scrollState = scroll,
                bottomPadding = NowCardReservedHeight,
                onBlockClick = { onAction(HomeAction.BlockClicked(it)) },
                modifier = Modifier.weight(1f).testTag("home_calendar"),
            )
        }
        state.nowCard?.let { ui ->
            NowCard(
                ui = ui,
                countdownText = countdownText,
                progress = progress,
                onAction = onAction,
                suppressedConfirmations = suppressedConfirmations,
                onSuppressConfirmation = onSuppressConfirmation,
                onAskAgain = onAskAgain,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = Spacing.l, vertical = Spacing.s)
                    .testTag("now_card"),
            )
        }
    }

    state.blockSheet?.let { sheet ->
        BlockDetailSheet(ui = sheet, onDismiss = { onAction(HomeAction.DismissBlockSheet) })
    }
    if (state.eveningChoicePending || state.eveningSheetOpen) {
        EveningChoiceSheet(onPick = { onAction(HomeAction.PickEvening(it)) })
    }
}

@Composable
fun HomeTopBar(
    weekdayShort: String,
    dayOfMonth: String,
    title: String,
    status: String,
    showDivider: () -> Boolean,
    onAction: (HomeAction) -> Unit,
) {
    SideEffect { RecompositionCounter.hit("HomeTopBar") }
    val c = AppTheme.colors
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (showDivider()) {
                    drawLine(c.outline, Offset(0f, size.height), Offset(size.width, size.height), Metrics.hairline.toPx())
                }
            }
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(weekdayShort, style = MaterialTheme.typography.labelSmall, color = c.primary)
            Box(
                Modifier.size(Metrics.dateBadge).background(c.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(dayOfMonth, style = MaterialTheme.typography.titleMedium.merge(AppType.numbers), color = c.onPrimary)
            }
        }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(status, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
        IconButton(onClick = { onAction(HomeAction.SwapTemplate) }) {
            Icon(Icons.Outlined.SwapHoriz, "Swap template")
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.Menu, "Menu") }
            AppDropdownMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onAction = { action ->
                    menuOpen = false
                    onAction(action)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (HomeAction) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = AppShapes.card,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        MenuRow(Icons.Outlined.History, "History") { onAction(HomeAction.History) }
        MenuRow(Icons.Outlined.HourglassBottom, "Time Debt") { onAction(HomeAction.TimeDebt) }
        MenuRow(Icons.Outlined.Settings, "Settings") { onAction(HomeAction.Settings) }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) },
        leadingIcon = { Icon(icon, null) },
        onClick = onClick,
    )
}

@Composable
fun NowCard(
    ui: NowCardUi,
    countdownText: () -> String,
    progress: () -> Float,
    onAction: (HomeAction) -> Unit,
    infoDialogOpen: Boolean = false,
    onDismissInfoDialog: () -> Unit = {},
    suppressedConfirmations: Set<NowButtonId> = emptySet(),
    onSuppressConfirmation: (NowButtonId) -> Unit = {},
    onAskAgain: (NowButtonId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SideEffect { RecompositionCounter.hit("NowCard") }
    val c = AppTheme.colors
    val accent = ui.type?.let { c.block(it).accent } ?: c.primary
    var dialog by remember { mutableStateOf<NowCardDialog?>(if (infoDialogOpen) NowCardDialog.Info else null) }

    AppCard(modifier.fillMaxWidth(), borderColor = if (ui.isLeaking) c.leak else null) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(Metrics.nowDot * 2).background(accent, CircleShape))
                Spacer(Modifier.width(Spacing.s))
                Text(
                    ui.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ui.chip?.let { AppChip(it, ui.chipTone.color(c)) }
                IconButton(
                    onClick = { dialog = NowCardDialog.Info },
                    modifier = Modifier.size(Metrics.touch),
                ) { Icon(Icons.Outlined.Info, "What the buttons do") }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                CountdownText(countdownText, if (ui.isLeaking) c.leak else c.textPrimary, Modifier.weight(1f))
                ui.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
            }
            if (ui.showProgress) ProgressLine(progress, accent)
            ui.buttons.filter { it.style == NowButtonStyle.PRIMARY }.forEach { button ->
                NowPrimaryButton(button) { onAction(button.id.toAction()) }
            }
            val secondary = ui.buttons.filter { it.style == NowButtonStyle.SECONDARY }
            if (secondary.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    secondary.forEach { button ->
                        NowSecondaryButton(
                            button = button,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (button.id.needsConfirmation && button.id !in suppressedConfirmations) {
                                    dialog = NowCardDialog.Confirm(button.id)
                                } else {
                                    onAction(button.id.toAction())
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    when (val open = dialog) {
        null -> Unit
        NowCardDialog.Info -> NowCardInfoDialog(
            blockTitle = ui.title,
            onDismiss = {
                dialog = null
                onDismissInfoDialog()
            },
        )
        is NowCardDialog.Confirm -> ConfirmationDialog(
            spec = checkNotNull(open.id.confirmation(ui.title)),
            onConfirm = { askedAgain ->
                dialog = null
                if (askedAgain) onSuppressConfirmation(open.id) else onAskAgain(open.id)
                onAction(open.id.toAction())
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun CountdownText(text: () -> String, color: Color, modifier: Modifier = Modifier) {
    SideEffect { RecompositionCounter.hit("CountdownText") }
    val value = text()
    if (value.isNotEmpty()) {
        Text(value, style = MaterialTheme.typography.headlineSmall.merge(AppType.numbers), color = color, modifier = modifier)
    } else {
        Spacer(modifier)
    }
}

@Composable
private fun ProgressLine(progress: () -> Float, color: Color) {
    val track = AppTheme.colors.outline
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(Metrics.progress)
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                drawRoundRect(color, size = Size(size.width * progress().coerceIn(0f, 1f), size.height), cornerRadius = r)
            },
    )
}

@Composable
private fun NowPrimaryButton(button: NowButton, onClick: () -> Unit) {
    val c = AppTheme.colors
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.touch).testTag("now_primary"),
        shape = AppShapes.chip,
        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
    ) {
        Text(button.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun NowSecondaryButton(button: NowButton, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Metrics.touch),
        shape = AppShapes.chip,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.surfaceRaised, contentColor = c.textPrimary),
    ) {
        Icon(nowButtonIcon(button.id), contentDescription = null, modifier = Modifier.size(Metrics.iconSm))
        Spacer(Modifier.width(Spacing.xs))
        Text(button.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

private fun nowButtonIcon(id: NowButtonId): ImageVector = when (id) {
    NowButtonId.PAUSE -> Icons.Outlined.Pause
    NowButtonId.EMERGENCY -> Icons.Outlined.WarningAmber
    NowButtonId.END_DAY -> Icons.Outlined.Stop
    NowButtonId.NEXT -> Icons.Outlined.PlayArrow
    NowButtonId.RESUME -> Icons.Outlined.PlayArrow
    NowButtonId.KID_ASLEEP -> Icons.Outlined.Nightlight
    NowButtonId.CHOOSE_EVENING -> Icons.Outlined.Nightlight
}

private fun NowButtonId.toAction(): HomeAction = when (this) {
    NowButtonId.PAUSE -> HomeAction.Pause
    NowButtonId.EMERGENCY -> HomeAction.Emergency
    NowButtonId.NEXT -> HomeAction.Next
    NowButtonId.RESUME -> HomeAction.Resume
    NowButtonId.KID_ASLEEP -> HomeAction.KidAsleep
    NowButtonId.CHOOSE_EVENING -> HomeAction.ChooseEvening
    NowButtonId.END_DAY -> HomeAction.EndDay
}

private fun ChipTone.color(c: com.roni.blocks.designsystem.AppColors): Color = when (this) {
    ChipTone.NEUTRAL -> c.textSecondary
    ChipTone.BREAK -> c.secondary
    ChipTone.LEAK -> c.leak
}
