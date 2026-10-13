package com.roni.blocks.feature.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Categories
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.OWN_TEMPLATE_ICONS
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.schedule.PlanBlock
import com.roni.blocks.core.schedule.Refusal
import com.roni.blocks.core.schedule.TemplateEdit
import com.roni.blocks.core.schedule.TemplateEditor
import com.roni.blocks.core.schedule.TemplateRules
import com.roni.blocks.core.schedule.toBlockSpecs
import com.roni.blocks.core.schedule.toPlanBlocks
import com.roni.blocks.core.storage.TemplateActions
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.BackTopBar
import com.roni.blocks.designsystem.BlockStrip
import com.roni.blocks.designsystem.Metrics
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.blockColors
import com.roni.blocks.feature.templatepicker.stripFor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One block of the editor's list: a name, a time range and a length (or "until 22:30" for kid time). */
@Immutable
data class TemplateRow(
    val id: String,
    val name: String,
    val range: String,
    val detail: String,
    val isFree: Boolean,
    val color: Color,
)

/** What the block sheet is editing right now; [result] and [line] follow every change live. */
@Immutable
data class BlockSheetUi(
    val id: String?,
    val type: BlockType,
    val startMin: Int,
    val lengthMin: Int,
    val isDayEnd: Boolean,
    val result: TemplateEdit,
    val line: String,
)

/** Everything the template editor shows: the template's display data, its rows, the open sheet. */
@Immutable
data class TemplateEditorState(
    val title: String = "",
    val iconKey: String = "event",
    val rows: List<TemplateRow> = emptyList(),
    val readOnly: Boolean = false,
    val strip: ImmutableList<Pair<Color, Float>> = persistentListOf(),
    val sheet: BlockSheetUi? = null,
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
    val use24: Boolean = true,
)

/** Everything the user can do in the template editor; [Sheet] carries the block sheet's actions. */
sealed interface TemplateEditorAction {
    data class Sheet(val action: BlockSheetAction) : TemplateEditorAction
    data class OpenBlock(val id: String) : TemplateEditorAction
    data object AddBlock : TemplateEditorAction
    data class Rename(val title: String) : TemplateEditorAction
    data class Icon(val key: String) : TemplateEditorAction
    data object DeleteTemplate : TemplateEditorAction
    data object ConfirmDelete : TemplateEditorAction
    data object DismissDialog : TemplateEditorAction
}

internal val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private const val MAX_LENGTH = 16 * 60

internal fun blockName(type: BlockType): String = when (type) {
    BlockType.Free -> "Free time"
    BlockType.KidTime -> "Kid time"
    else -> Categories.infoFor(type)?.name ?: "Block"
}

internal fun typeForCategory(info: CategoryInfo): BlockType = when (info.id) {
    "cv" -> BlockType.Cv
    "coding" -> BlockType.Coding
    "gym" -> BlockType.Gym
    "free" -> BlockType.Free
    else -> BlockType.Custom(info.id, info.name, info.colorArgb, info.work, info.pomodoro)
}

private fun iconFor(key: String): ImageVector = when (key) {
    "fitness" -> Icons.Outlined.FitnessCenter
    "code" -> Icons.Outlined.Code
    "weekend" -> Icons.Outlined.Weekend
    "games" -> Icons.Outlined.SportsEsports
    "work" -> Icons.Outlined.Work
    "school" -> Icons.Outlined.School
    "home" -> Icons.Outlined.Home
    else -> Icons.Outlined.Event
}

/**
 * The template editor (3.1.0): lists one template's blocks and edits them through
 * [TemplateEditor], saving every accepted change through [TemplateActions]. Built-ins are read-only.
 *
 * Talks to: [TemplateActions], [TemplateStore], [TimeFormatter].
 */
@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    private val actions: TemplateActions,
    private val store: TemplateStore,
    private val timeFormatter: TimeFormatter,
    private val logger: AppLogger,
) : ViewModel() {
    private val _state = MutableStateFlow(TemplateEditorState())
    val state: StateFlow<TemplateEditorState> = _state.asStateFlow()

    private var template: StoredTemplate? = null
    private var blocks: List<PlanBlock> = emptyList()

    fun load(templateId: TemplateId) {
        val t = store.find(templateId) ?: return
        template = t
        blocks = t.blocks.toPlanBlocks()
        _state.update {
            TemplateEditorState(
                title = t.title,
                iconKey = t.iconKey,
                readOnly = store.isBuiltIn(t.id),
                use24 = timeFormatter.use24.value,
            ).painted()
        }
    }

    fun onAction(action: TemplateEditorAction) = when (action) {
        is TemplateEditorAction.Sheet -> onSheet(action.action)
        is TemplateEditorAction.OpenBlock -> openBlock(action.id)
        TemplateEditorAction.AddBlock -> addBlock()
        is TemplateEditorAction.Rename -> rename(action.title)
        is TemplateEditorAction.Icon -> icon(action.key)
        TemplateEditorAction.DeleteTemplate -> _state.update { it.copy(confirmDelete = true) }
        TemplateEditorAction.DismissDialog -> _state.update { it.copy(confirmDelete = false) }
        TemplateEditorAction.ConfirmDelete -> confirmDelete()
    }

    fun onSheet(action: BlockSheetAction) {
        val current = _state.value.sheet ?: return
        when (action) {
            is BlockSheetAction.Type -> _state.update { it.copy(sheet = recompute(current.copy(type = action.type))) }
            is BlockSheetAction.StartBy -> {
                val start = (current.startMin + action.minutes).coerceAtLeast(TemplateEditor.EARLIEST)
                _state.update { it.copy(sheet = recompute(current.copy(startMin = start))) }
            }
            is BlockSheetAction.StartAt -> _state.update { it.copy(sheet = recompute(current.copy(startMin = action.minute))) }
            is BlockSheetAction.LengthBy -> {
                val length = (current.lengthMin + action.minutes).coerceIn(TemplateRules.MIN_BLOCK, MAX_LENGTH)
                _state.update { it.copy(sheet = recompute(current.copy(lengthMin = length))) }
            }
            BlockSheetAction.Delete -> deleteSheetBlock()
            BlockSheetAction.Save -> saveSheet()
            BlockSheetAction.Dismiss -> _state.update { it.copy(sheet = null) }
        }
    }

    private fun openBlock(rowId: String) {
        if (_state.value.readOnly) return
        val b = blocks.firstOrNull { it.id == rowId } ?: return
        val sheet = BlockSheetUi(
            id = b.id,
            type = b.type,
            startMin = b.startMin,
            lengthMin = b.length,
            isDayEnd = b.id == TemplateEditor.dayEnd(blocks).id,
            result = TemplateEdit.Ok(blocks),
            line = "",
        )
        _state.update { it.copy(sheet = recompute(sheet)) }
    }

    private fun addBlock() {
        if (_state.value.readOnly) return
        val type = Categories.current.builtIns.firstOrNull { it.work }?.let { typeForCategory(it) } ?: BlockType.Cv
        val sheet = BlockSheetUi(
            id = null,
            type = type,
            startMin = TemplateEditor.addStart(blocks),
            lengthMin = TemplateEditor.DEFAULT_LENGTH,
            isDayEnd = false,
            result = TemplateEdit.Ok(blocks),
            line = "",
        )
        _state.update { it.copy(sheet = recompute(sheet)) }
    }

    private fun saveSheet() {
        val s = _state.value.sheet ?: return
        val result = s.result as? TemplateEdit.Ok ?: return
        val t = template ?: return
        viewModelScope.launch {
            actions.save(t.copy(blocks = result.blocks.toBlockSpecs(t.blocks)))
            template = store.find(t.id)
            blocks = template?.blocks?.toPlanBlocks().orEmpty()
            _state.update { it.copy(sheet = null).painted() }
        }
    }

    private fun deleteSheetBlock() {
        val s = _state.value.sheet ?: return
        val rowId = s.id ?: return
        val result = TemplateEditor.delete(blocks, rowId) as? TemplateEdit.Ok ?: return
        val t = template ?: return
        viewModelScope.launch {
            actions.save(t.copy(blocks = result.blocks.toBlockSpecs(t.blocks)))
            template = store.find(t.id)
            blocks = template?.blocks?.toPlanBlocks().orEmpty()
            _state.update { it.copy(sheet = null).painted() }
        }
    }

    private fun rename(title: String) {
        val t = template ?: return
        val clean = title.trim()
        if (clean.isEmpty() || clean.length > 24 || clean == t.title) return
        viewModelScope.launch {
            actions.save(t.copy(title = clean))
            template = store.find(t.id)
            _state.update { it.painted() }
        }
    }

    private fun icon(key: String) {
        val t = template ?: return
        if (t.iconKey == key) return
        viewModelScope.launch {
            actions.save(t.copy(iconKey = key))
            template = store.find(t.id)
            _state.update { it.painted() }
        }
    }

    private fun confirmDelete() {
        val t = template ?: return
        viewModelScope.launch {
            actions.delete(t.id)
            _state.update { it.copy(confirmDelete = false, deleted = true) }
        }
    }

    /** Paint the rows and strip from the current template and blocks, keeping the screen fields. */
    private fun TemplateEditorState.painted(): TemplateEditorState {
        val t = template ?: return this
        return copy(
            title = t.title,
            iconKey = t.iconKey,
            rows = rowsOf(),
            readOnly = store.isBuiltIn(t.id),
            strip = stripFor(t.blocks),
            use24 = timeFormatter.use24.value,
        )
    }

    private fun recompute(sheet: BlockSheetUi): BlockSheetUi {
        val prefix = template?.id?.key ?: return sheet
        val editId = sheet.id ?: TemplateEditor.newId(blocks, prefix)
        val result = TemplateEditor.place(blocks, editId, sheet.type, sheet.startMin, sheet.lengthMin, prefix)
        return sheet.copy(result = result, line = lineFor(result, editId))
    }

    private fun rowsOf(): List<TemplateRow> {
        val use24 = _state.value.use24
        val sorted = blocks.sortedBy { it.startMin }
        val kidId = TemplateEditor.dayEnd(sorted).id
        return sorted.map { b ->
            val isKid = b.id == kidId
            val colors = blockColors(b.type)
            TemplateRow(
                id = b.id,
                name = blockName(b.type),
                range = TimeFormatter.label(b.startMin, use24) + "–" + TimeFormatter.label(b.endMin, use24),
                detail = if (isKid) "until ${TimeFormatter.label(TemplateRules.DAY_END, use24)}" else "${b.length} min",
                isFree = b.type == BlockType.Free,
                color = colors.accent,
            )
        }
    }

    private fun lineFor(result: TemplateEdit, editId: String): String = when (result) {
        is TemplateEdit.Ok -> movedLine(result.blocks, editId)
        is TemplateEdit.Refused -> when (val why = result.why) {
            is Refusal.StartsInside -> {
                val at = TimeFormatter.label(why.endMin, _state.value.use24)
                "${nameOf(why.id)} runs until $at. Start at $at or later."
            }
            Refusal.TooShort -> "A block is at least 10 minutes."
            Refusal.TooEarly -> "Blocks start at 05:00 or later."
            Refusal.NoRoomForKidTime -> "No room: kid time would be under 10 minutes."
            Refusal.KidTimeEndsTheDay -> "Every day ends with kid time."
            is Refusal.Broken -> {
                logger.error("template editor refused a broken plan: ${why.problems}")
                "That change doesn't fit."
            }
        }
    }

    private fun movedLine(result: List<PlanBlock>, editId: String): String {
        val use24 = _state.value.use24
        val original = blocks.associateBy { it.id }
        val kidId = TemplateEditor.dayEnd(blocks).id
        val moved = result.sortedBy { it.startMin }.filter {
            it.id != editId && it.id != kidId && it.type != BlockType.Free &&
                original[it.id] != null && original.getValue(it.id).startMin != it.startMin
        }
        val kid = result.first { it.id == kidId }
        val kidMoved = original[kidId]?.startMin != kid.startMin
        if (moved.isEmpty() && !kidMoved) return ""
        return buildString {
            if (moved.isNotEmpty()) {
                append("Moves later: ")
                append(moved.joinToString(", ") { blockName(it.type) })
            }
            if (kidMoved) {
                if (moved.isNotEmpty()) append(" · ")
                append("kid time from ${TimeFormatter.label(kid.startMin, use24)}")
            }
        }
    }

    private fun nameOf(id: String): String {
        val b = blocks.firstOrNull { it.id == id } ?: return "That block"
        return blockName(b.type)
    }
}

@Composable
fun TemplateEditorRoute(id: TemplateId, onBack: () -> Unit, viewModel: TemplateEditorViewModel = hiltViewModel()) {
    LaunchedEffect(id) { viewModel.load(id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TemplateEditorScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }
}

@Composable
fun TemplateEditorScreen(state: TemplateEditorState, onAction: (TemplateEditorAction) -> Unit, onBack: () -> Unit) {
    val colors = AppTheme.colors
    ScreenScaffold(topBar = { BackTopBar(title = state.title, onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.m),
        ) {
            if (!state.readOnly) {
                EditorHeader(title = state.title, iconKey = state.iconKey, onAction = onAction)
                Spacer(Modifier.height(Spacing.m))
            }
            BlockStrip(state.strip, modifier = Modifier.fillMaxWidth().height(28.dp))
            Spacer(Modifier.height(Spacing.m))
            state.rows.forEach { row ->
                TemplateRowRow(
                    row = row,
                    clickable = !state.readOnly,
                    onAction = onAction,
                )
            }
            Spacer(Modifier.height(Spacing.s))
            if (state.readOnly) {
                Text(
                    "Built-in templates can't be changed. Make your own from blank in Templates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            } else {
                Button(
                    onClick = { onAction(TemplateEditorAction.AddBlock) },
                    modifier = Modifier.fillMaxWidth().testTag("editor_add"),
                ) {
                    Text("+ Add block")
                }
                Spacer(Modifier.height(Spacing.m))
                TextButton(
                    onClick = { onAction(TemplateEditorAction.DeleteTemplate) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete template", color = colors.leak)
                }
            }
        }
        state.sheet?.let { sheet ->
            BlockSheet(
                ui = sheet,
                use24 = state.use24,
                onAction = { action -> onAction(TemplateEditorAction.Sheet(action)) },
            )
        }
        if (state.confirmDelete) {
            AlertDialog(
                onDismissRequest = { onAction(TemplateEditorAction.DismissDialog) },
                title = { Text("Delete ${state.title}?") },
                text = { Text("It leaves every profile. Days you already had keep their history.") },
                confirmButton = {
                    TextButton(onClick = { onAction(TemplateEditorAction.ConfirmDelete) }) {
                        Text("Delete", color = colors.leak)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onAction(TemplateEditorAction.DismissDialog) }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun EditorHeader(title: String, iconKey: String, onAction: (TemplateEditorAction) -> Unit) {
    val colors = AppTheme.colors
    var text by remember { mutableStateOf(title) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(24) },
        singleLine = true,
        label = { Text("Name") },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) onAction(TemplateEditorAction.Rename(text)) },
    )
    Spacer(Modifier.height(Spacing.m))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OWN_TEMPLATE_ICONS.forEach { key ->
            val selected = key == iconKey
            Box(
                Modifier
                    .size(Metrics.iconLarge)
                    .background(
                        if (selected) colors.primary.copy(alpha = 0.25f) else colors.surface,
                        CircleShape,
                    )
                    .clickable { onAction(TemplateEditorAction.Icon(key)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconFor(key),
                    null,
                    tint = if (selected) colors.primary else colors.textSecondary,
                    modifier = Modifier.size(Metrics.iconSm),
                )
            }
        }
    }
}

@Composable
private fun TemplateRowRow(row: TemplateRow, clickable: Boolean, onAction: (TemplateEditorAction) -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable, onClick = { onAction(TemplateEditorAction.OpenBlock(row.id)) })
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.isFree) {
            Box(
                Modifier
                    .size(10.dp)
                    .border(1.5.dp, colors.outlineStrong, CircleShape),
            )
        } else {
            Box(
                Modifier
                    .size(10.dp)
                    .background(row.color, CircleShape),
            )
        }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (row.isFree) colors.textSecondary else colors.textPrimary,
            )
            Text(
                "${row.range} · ${row.detail}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}