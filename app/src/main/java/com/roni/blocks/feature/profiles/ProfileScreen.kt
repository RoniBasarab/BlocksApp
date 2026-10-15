package com.roni.blocks.feature.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.roni.blocks.core.model.Profile
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.shortFor
import com.roni.blocks.core.profiles.ProfileBook
import com.roni.blocks.core.profiles.ProfileEdit
import com.roni.blocks.core.profiles.ProfileRefusal
import com.roni.blocks.core.profiles.Profiles
import com.roni.blocks.core.storage.ExplainerKey
import com.roni.blocks.core.storage.ExplainerStore
import com.roni.blocks.core.storage.ProfileActions
import com.roni.blocks.core.storage.TemplateActions
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.core.system.AppRescue
import com.roni.blocks.core.system.Failures
import com.roni.blocks.core.system.ProblemAction
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppSheet
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.ErrorCard
import com.roni.blocks.designsystem.HintCard
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.SectionHeader
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.icons.AppIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ProfileTemplateUi(
    val id: TemplateId,
    val title: String,
    val days: String,
    val icon: String,
    val accent: Long,
    val builtIn: Boolean,
    val removable: Boolean,
)

@Immutable
data class DayRowUi(val iso: Int, val dayName: String, val templateTitle: String?)

@Immutable
data class ProfileState(
    val id: String = "",
    val name: String = "",
    val active: Boolean = false,
    val canDelete: Boolean = false,
    val templates: ImmutableList<ProfileTemplateUi> = persistentListOf(),
    val days: ImmutableList<DayRowUi> = persistentListOf(),
    val dayPicker: Int? = null,
    val templatesHint: Boolean = false,
    val daysHint: Boolean = false,
    val addExisting: ImmutableList<ProfileTemplateUi>? = null,
    val newTemplate: String? = null,
    val confirmDelete: Boolean = false,
    val error: String? = null,
    val howTo: Boolean = false,
    val howToPage: Int = 0,
    val failed: Boolean = false,
)

sealed interface ProfileAction {
    data class Rename(val name: String) : ProfileAction
    data object UseThis : ProfileAction
    data class OpenTemplate(val id: TemplateId) : ProfileAction
    data class RemoveTemplate(val id: TemplateId) : ProfileAction
    data object AddExisting : ProfileAction
    data class AddTemplate(val id: TemplateId) : ProfileAction
    data object NewTemplate : ProfileAction
    data class NewTemplateName(val text: String) : ProfileAction
    data object CreateTemplate : ProfileAction
    data class OpenDay(val iso: Int) : ProfileAction
    data class DismissHint(val key: ExplainerKey) : ProfileAction
    data class SetDay(val iso: Int, val id: TemplateId?) : ProfileAction
    data object Delete : ProfileAction
    data object ConfirmDelete : ProfileAction
    data object DismissSheet : ProfileAction
    data object HowTo : ProfileAction
    data class HowToPage(val page: Int) : ProfileAction
    data object CloseHowTo : ProfileAction
    data class Problem(val action: ProblemAction) : ProfileAction
}

sealed interface ProfileEffect {
    data class OpenTemplate(val id: TemplateId) : ProfileEffect
    data object Closed : ProfileEffect
}

internal val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val ISO_ORDER = listOf(7, 1, 2, 3, 4, 5, 6)

/** One profile (3.2.0): its name, its templates, and which template each weekday suggests. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val store: TemplateStore,
    private val profiles: ProfileActions,
    private val templates: TemplateActions,
    private val explainers: ExplainerStore,
    private val failures: Failures,
    private val rescue: AppRescue,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ProfileEffect>()
    val effects: SharedFlow<ProfileEffect> = _effects.asSharedFlow()

    private var profileId: String? = null

    private fun fail() = _state.update { it.copy(failed = true) }

    init {
        failures.launch(viewModelScope, "Profile: hints", onFailure = ::fail) {
            _state.update {
                it.copy(
                    templatesHint = !explainers.seen(ExplainerKey.TEMPLATES),
                    daysHint = !explainers.seen(ExplainerKey.DAYS),
                )
            }
        }
        failures.launch(viewModelScope, "Profile: profiles", onFailure = ::fail) {
            store.profiles.collect { book -> if (profileId != null) refresh(book) }
        }
    }

    fun load(id: String) {
        profileId = id
        refresh(store.profiles.value)
    }

    fun onAction(action: ProfileAction) {
        val id = profileId
        when (action) {
            is ProfileAction.Rename -> rename(action.name)
            ProfileAction.UseThis -> if (id != null) failures.launch(viewModelScope, "Profile: use this", onFailure = ::fail) { profiles.switchTo(id) }
            is ProfileAction.OpenTemplate -> if (action.id != TemplateId.OFF) failures.launch(viewModelScope, "Profile: open template", onFailure = ::fail) { _effects.emit(ProfileEffect.OpenTemplate(action.id)) }
            is ProfileAction.RemoveTemplate -> if (id != null) failures.launch(viewModelScope, "Profile: remove template", onFailure = ::fail) { profiles.removeTemplate(id, action.id) }
            ProfileAction.AddExisting -> openAddExisting()
            is ProfileAction.AddTemplate -> if (id != null) failures.launch(viewModelScope, "Profile: add template", onFailure = ::fail) {
                profiles.addTemplate(id, action.id)
                _state.update { it.copy(addExisting = null) }
            }
            ProfileAction.NewTemplate -> _state.update { it.copy(newTemplate = "") }
            is ProfileAction.NewTemplateName -> _state.update { it.copy(newTemplate = action.text) }
            ProfileAction.CreateTemplate -> createTemplate()
            is ProfileAction.OpenDay -> openDay(action.iso)
            is ProfileAction.DismissHint -> dismissHint(action.key)
            is ProfileAction.SetDay -> if (id != null) failures.launch(viewModelScope, "Profile: set day", onFailure = ::fail) {
                profiles.setDay(id, action.iso, action.id)
                _state.update { it.copy(dayPicker = null) }
            }
            ProfileAction.Delete -> _state.update { it.copy(confirmDelete = true) }
            ProfileAction.ConfirmDelete -> confirmDelete()
            ProfileAction.DismissSheet -> _state.update {
                it.copy(dayPicker = null, addExisting = null, newTemplate = null, confirmDelete = false)
            }
            ProfileAction.HowTo -> _state.update { it.copy(howTo = true, howToPage = 0) }
            is ProfileAction.HowToPage -> _state.update { it.copy(howToPage = action.page) }
            ProfileAction.CloseHowTo -> _state.update { it.copy(howTo = false) }
            is ProfileAction.Problem -> rescue.handle(action.action)
        }
    }

    private fun openDay(iso: Int) {
        _state.update { it.copy(dayPicker = iso) }
    }

    private fun dismissHint(key: ExplainerKey) {
        failures.launch(viewModelScope, "Profile: mark hint seen", onFailure = ::fail) { explainers.markSeen(key) }
        _state.update {
            when (key) {
                ExplainerKey.TEMPLATES -> it.copy(templatesHint = false)
                ExplainerKey.DAYS -> it.copy(daysHint = false)
                else -> it
            }
        }
    }

    private fun rename(name: String) {
        val id = profileId ?: return
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > Profiles.MAX_NAME) return
        failures.launch(viewModelScope, "Profile: rename", onFailure = ::fail) {
            when (val result = profiles.rename(id, clean)) {
                is ProfileEdit.Refused -> _state.update { it.copy(error = refusalText(result.why)) }
                is ProfileEdit.Ok -> _state.update { it.copy(error = null) }
            }
        }
    }

    private fun createTemplate() {
        val id = profileId ?: return
        val name = _state.value.newTemplate?.trim() ?: return
        if (name.isEmpty() || name.length > Profiles.MAX_NAME) return
        failures.launch(viewModelScope, "Profile: create template", onFailure = ::fail) {
            val created = templates.createIn(id, name)
            _state.update { it.copy(newTemplate = null) }
            _effects.emit(ProfileEffect.OpenTemplate(created.id))
        }
    }

    private fun confirmDelete() {
        val id = profileId ?: return
        failures.launch(viewModelScope, "Profile: delete", onFailure = ::fail) {
            when (val result = profiles.delete(id)) {
                is ProfileEdit.Ok -> _effects.emit(ProfileEffect.Closed)
                is ProfileEdit.Refused -> _state.update { it.copy(confirmDelete = false, error = refusalText(result.why)) }
            }
        }
    }

    private fun openAddExisting() {
        val id = profileId ?: return
        val profile = store.profiles.value.find(id) ?: return
        val rows = store.library().filter { it.id !in profile.templateIds }
            .map { templateUi(it, profile) }
            .toPersistentList()
        _state.update { it.copy(addExisting = rows) }
    }

    private fun refresh(book: ProfileBook) {
        val profile = book.find(profileId ?: return) ?: return
        _state.update { state ->
            state.copy(
                id = profile.id,
                name = profile.name,
                active = profile.id == book.activeId,
                canDelete = profile.id != book.activeId && book.profiles.size > 1,
                templates = profile.templateIds.mapNotNull { templateId ->
                    store.find(templateId)?.let { templateUi(it, profile) }
                }.toPersistentList(),
                days = ISO_ORDER.map { iso ->
                    DayRowUi(iso, DAY_NAMES[iso - 1], profile.days.orEmpty()[iso]?.let { store.find(it)?.title })
                }.toPersistentList(),
            )
        }
    }

    private fun templateUi(template: com.roni.blocks.core.model.StoredTemplate, profile: Profile): ProfileTemplateUi {
        val days = Profiles.daysOf(profile, template.id)
        return ProfileTemplateUi(
            id = template.id,
            title = template.title,
            days = if (days.isEmpty()) "" else shortFor(days),
            icon = template.iconKey,
            accent = template.accentArgb,
            builtIn = store.isBuiltIn(template.id),
            removable = template.id != TemplateId.OFF,
        )
    }

    private fun refusalText(why: ProfileRefusal): String = when (why) {
        ProfileRefusal.EMPTY_NAME -> "Give it a name."
        ProfileRefusal.NAME_TAKEN -> "That name is taken."
        ProfileRefusal.TOO_LONG -> "24 characters at most."
        ProfileRefusal.LAST_PROFILE -> "You need at least one profile."
        ProfileRefusal.ACTIVE_PROFILE -> "Switch to another profile first."
        else -> "That didn't work."
    }
}

@Composable
fun ProfileRoute(
    id: String,
    onBack: () -> Unit,
    onOpenTemplate: (TemplateId) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { viewModel.load(id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProfileEffect.OpenTemplate -> onOpenTemplate(effect.id)
                ProfileEffect.Closed -> onBack()
            }
        }
    }
    ProfileScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

/** The stateless profile page; [ProfileRoute] owns the ViewModel. */
@Composable
fun ProfileScreen(state: ProfileState, onAction: (ProfileAction) -> Unit, onBack: () -> Unit) {
    val colors = AppTheme.colors
    var menuFor by remember { mutableStateOf<String?>(null) }
    ScreenScaffold(topBar = { ProfilesTopBar(title = state.name, onBack = onBack, onHowTo = { onAction(ProfileAction.HowTo) }) }) { _ ->
        if (state.failed) {
            ErrorCard(
                onAction = { onAction(ProfileAction.Problem(it)) },
                modifier = Modifier.padding(Spacing.m),
            )
            return@ScreenScaffold
        }
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.m),
            ) {
                AnimatedVisibility(
                    visible = state.templatesHint,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        HintCard(ExplainerKey.TEMPLATES, onGotIt = { onAction(ProfileAction.DismissHint(ExplainerKey.TEMPLATES)) })
                        Spacer(Modifier.height(Spacing.m))
                    }
                }
                var name by remember(state.id) { mutableStateOf(state.name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_field")
                        .onFocusChanged { if (!it.isFocused) onAction(ProfileAction.Rename(name)) },
                )
                Spacer(Modifier.height(Spacing.s))
                if (state.active) {
                    Tag("Active")
                } else {
                    Button(onClick = { onAction(ProfileAction.UseThis) }, modifier = Modifier.testTag("use_profile")) {
                        Text("Use this profile")
                    }
                    Text(
                        "Today's day keeps going; this profile starts with your next pick.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.leak, modifier = Modifier.padding(top = Spacing.s))
                }

                SectionHeader("Which template each day suggests")
                AnimatedVisibility(
                    visible = state.daysHint,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        HintCard(ExplainerKey.DAYS, onGotIt = { onAction(ProfileAction.DismissHint(ExplainerKey.DAYS)) })
                        Spacer(Modifier.height(Spacing.s))
                    }
                }
                state.days.forEach { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAction(ProfileAction.OpenDay(row.iso)) }
                            .testTag("day_row_${row.iso}")
                            .padding(vertical = Spacing.s),
                    ) {
                        Text(row.dayName, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            row.templateTitle ?: "No suggestion",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (row.templateTitle != null) colors.textPrimary else colors.textMuted,
                        )
                    }
                }

                SectionHeader("Templates in this profile")
                state.templates.forEach { template ->
                    TemplateCard(
                        template = template,
                        menuOpen = menuFor == template.id.key,
                        onOpen = { onAction(ProfileAction.OpenTemplate(template.id)) },
                        onMenu = { menuFor = if (menuFor == template.id.key) null else template.id.key },
                        onRemove = { menuFor = null; onAction(ProfileAction.RemoveTemplate(template.id)) },
                    )
                    Spacer(Modifier.height(Spacing.s))
                }
                TextButton(onClick = { onAction(ProfileAction.NewTemplate) }, modifier = Modifier.testTag("profile_new_template")) {
                    Text("+ New template")
                }
                TextButton(onClick = { onAction(ProfileAction.AddExisting) }, modifier = Modifier.testTag("profile_add_existing")) {
                    Text("+ Add existing")
                }

                if (state.canDelete) {
                    Spacer(Modifier.height(Spacing.l))
                    TextButton(onClick = { onAction(ProfileAction.Delete) }, modifier = Modifier.testTag("delete_profile")) {
                        Text("Delete profile", color = colors.leak)
                    }
                }
            }

            state.dayPicker?.let { iso -> DayPickerSheet(state, iso, onAction) }
            state.addExisting?.let { AddExistingSheet(it, onAction) }
            state.newTemplate?.let { NewTemplateDialog(it, onAction) }
            if (state.confirmDelete) DeleteDialog(state.name, onAction)
            if (state.howTo) {
                HowToUse(state.howToPage) { howToAction ->
                    onAction(
                        when (howToAction) {
                            is HowToAction.Page -> ProfileAction.HowToPage(howToAction.page)
                            HowToAction.Close -> ProfileAction.CloseHowTo
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: ProfileTemplateUi,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = AppTheme.colors
    val off = template.id == TemplateId.OFF
    AppCard(onClick = if (off) null else onOpen, modifier = Modifier.fillMaxWidth().testTag("profile_template_${template.id.key}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(iconFor(template.icon), null, tint = Color(template.accent))
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        template.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (template.builtIn) {
                        Spacer(Modifier.width(Spacing.s))
                        Tag("Built-in")
                    }
                }
                if (off) {
                    Text("No blocks, no pings", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                } else if (template.days.isNotEmpty()) {
                    Text(template.days, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
            if (template.removable) {
                Box {
                    IconButton(onClick = onMenu, modifier = Modifier.testTag("template_menu_${template.id.key}")) {
                        Icon(AppIcons.MoreVert, "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = onMenu, shape = AppShapes.card) {
                        DropdownMenuItem(
                            text = { Text("Remove from profile") },
                            onClick = onRemove,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPickerSheet(state: ProfileState, iso: Int, onAction: (ProfileAction) -> Unit) {
    AppSheet(onDismiss = { onAction(ProfileAction.DismissSheet) }) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(DAY_NAMES[iso - 1], style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.textPrimary)
            state.templates.forEach { template ->
                SheetRow(template.icon, template.title, template.accent) { onAction(ProfileAction.SetDay(iso, template.id)) }
            }
            SheetRow(null, "No suggestion", 0xFF9C968C) { onAction(ProfileAction.SetDay(iso, null)) }
        }
    }
}

@Composable
private fun AddExistingSheet(rows: ImmutableList<ProfileTemplateUi>, onAction: (ProfileAction) -> Unit) {
    AppSheet(onDismiss = { onAction(ProfileAction.DismissSheet) }) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Add existing", style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.textPrimary)
            if (rows.isEmpty()) {
                Text("Every template is already here.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            } else {
                rows.forEach { template ->
                    SheetRow(template.icon, template.title, template.accent) { onAction(ProfileAction.AddTemplate(template.id)) }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: String?, title: String, accent: Long, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.s),
    ) {
        if (icon != null) {
            Icon(iconFor(icon), null, tint = Color(accent))
            Spacer(Modifier.width(Spacing.m))
        } else {
            Box(Modifier.width(Spacing.m))
        }
        Text(title, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textPrimary)
    }
}

@Composable
private fun NewTemplateDialog(name: String, onAction: (ProfileAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(ProfileAction.DismissSheet) },
        title = { Text("New template", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 24) onAction(ProfileAction.NewTemplateName(it)) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("new_template_name"),
            )
        },
        confirmButton = { TextButton(onClick = { onAction(ProfileAction.CreateTemplate) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { onAction(ProfileAction.DismissSheet) }) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(name: String, onAction: (ProfileAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(ProfileAction.DismissSheet) },
        title = { Text("Delete $name?", style = MaterialTheme.typography.titleLarge) },
        text = { Text("Its templates stay in your library.") },
        confirmButton = {
            TextButton(onClick = { onAction(ProfileAction.ConfirmDelete) }) { Text("Delete", color = AppTheme.colors.leak) }
        },
        dismissButton = { TextButton(onClick = { onAction(ProfileAction.DismissSheet) }) { Text("Cancel") } },
    )
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.colors.textSecondary,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .border(1.dp, AppTheme.colors.outline, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun iconFor(key: String): ImageVector = when (key) {
    "fitness" -> AppIcons.FitnessCenter
    "code" -> AppIcons.Code
    "weekend" -> AppIcons.Weekend
    "games" -> AppIcons.SportsEsports
    "work" -> AppIcons.Work
    "school" -> AppIcons.School
    "home" -> AppIcons.Home
    else -> AppIcons.Event
}
