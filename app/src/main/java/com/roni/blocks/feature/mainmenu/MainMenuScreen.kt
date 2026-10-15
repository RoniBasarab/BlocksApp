package com.roni.blocks.feature.mainmenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.roni.blocks.core.storage.ProfileActions
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.core.system.AppRescue
import com.roni.blocks.core.system.Failures
import com.roni.blocks.core.system.ProblemAction
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.ErrorCard
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.designsystem.icons.AppIcons
import com.roni.blocks.feature.profiles.HowToAction
import com.roni.blocks.feature.profiles.HowToUse
import com.roni.blocks.feature.profiles.NewProfileUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
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
import kotlinx.datetime.TimeZone

@Immutable
data class MenuTemplateUi(val title: String, val days: String)

@Immutable
data class MenuProfileUi(
    val id: String,
    val name: String,
    val active: Boolean,
    val templates: ImmutableList<MenuTemplateUi>,
)

@Immutable
data class MainMenuState(
    val greeting: String = "",
    val profiles: ImmutableList<MenuProfileUi> = persistentListOf(),
    val newProfile: NewProfileUi? = null,
    val howTo: Boolean = false,
    val howToPage: Int = 0,
    val failed: Boolean = false,
)

sealed interface MainMenuAction {
    data class UseProfile(val id: String) : MainMenuAction
    data object NewProfile : MainMenuAction
    data class NewName(val text: String) : MainMenuAction
    data class CopyActive(val copy: Boolean) : MainMenuAction
    data object Create : MainMenuAction
    data object DismissNew : MainMenuAction
    data object OpenCategories : MainMenuAction
    data object HowTo : MainMenuAction
    data class HowToPage(val page: Int) : MainMenuAction
    data object CloseHowTo : MainMenuAction
    data class Problem(val action: ProblemAction) : MainMenuAction
}

sealed interface MainMenuEffect {
    data object OpenPicker : MainMenuEffect
    data object OpenCategories : MainMenuEffect
}

/** The first-run main menu (3.3.0): pick a profile once, then it never shows again. */
@HiltViewModel
class MainMenuViewModel @Inject constructor(
    private val store: TemplateStore,
    private val profiles: ProfileActions,
    private val clock: Clock,
    private val zone: TimeZone,
    private val failures: Failures,
    private val rescue: AppRescue,
) : ViewModel() {
    private val _state = MutableStateFlow(MainMenuState(greeting = "Welcome, Roni"))
    val state: StateFlow<MainMenuState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MainMenuEffect>()
    val effects: SharedFlow<MainMenuEffect> = _effects.asSharedFlow()

    private fun fail() = _state.update { it.copy(failed = true) }

    init {
        failures.launch(viewModelScope, "Main menu: profiles", onFailure = ::fail) {
            store.profiles.collect { book -> _state.update { it.copy(profiles = profilesOf(book)) } }
        }
    }

    fun onAction(action: MainMenuAction) {
        when (action) {
            is MainMenuAction.UseProfile -> useProfile(action.id)
            MainMenuAction.NewProfile -> _state.update {
                it.copy(newProfile = NewProfileUi(activeName = store.profiles.value.active.name))
            }
            is MainMenuAction.NewName -> _state.update { it.copy(newProfile = it.newProfile?.copy(name = action.text, error = null)) }
            is MainMenuAction.CopyActive -> _state.update { it.copy(newProfile = it.newProfile?.copy(copyActive = action.copy)) }
            MainMenuAction.Create -> create()
            MainMenuAction.DismissNew -> _state.update { it.copy(newProfile = null) }
            is MainMenuAction.Problem -> rescue.handle(action.action)
            MainMenuAction.OpenCategories -> failures.launch(viewModelScope, "Main menu: open categories", onFailure = ::fail) { _effects.emit(MainMenuEffect.OpenCategories) }
            MainMenuAction.HowTo -> _state.update { it.copy(howTo = true, howToPage = 0) }
            is MainMenuAction.HowToPage -> _state.update { it.copy(howToPage = action.page) }
            MainMenuAction.CloseHowTo -> _state.update { it.copy(howTo = false) }
        }
    }

    private fun useProfile(id: String) {
        failures.launch(viewModelScope, "Main menu: use profile", onFailure = ::fail) {
            profiles.switchTo(id)
            store.markProfileChosen()
            _effects.emit(MainMenuEffect.OpenPicker)
        }
    }

    private fun create() {
        val ui = _state.value.newProfile ?: return
        failures.launch(viewModelScope, "Main menu: create profile", onFailure = ::fail) {
            when (val result = profiles.create(ui.name, ui.copyActive)) {
                is ProfileEdit.Refused -> _state.update { it.copy(newProfile = ui.copy(error = refusalText(result.why))) }
                is ProfileEdit.Ok -> {
                    _state.update { it.copy(newProfile = null) }
                    _effects.emit(MainMenuEffect.OpenPicker)
                }
            }
        }
    }

    private fun profilesOf(book: ProfileBook): ImmutableList<MenuProfileUi> =
        book.profiles.sortedByDescending { it.id == book.activeId }.map { profile ->
            MenuProfileUi(
                id = profile.id,
                name = profile.name,
                active = profile.id == book.activeId,
                templates = menuTemplates(profile),
            )
        }.toPersistentList()

    private fun menuTemplates(profile: Profile): ImmutableList<MenuTemplateUi> =
        store.profileTemplates(profile.id)
            .filter { it.id != TemplateId.OFF }
            .map { template ->
                val days = Profiles.daysOf(profile, template.id)
                MenuTemplateUi(template.title, if (days.isEmpty()) "No days" else shortFor(days))
            }
            .toPersistentList()

    private fun refusalText(why: ProfileRefusal): String = when (why) {
        ProfileRefusal.EMPTY_NAME -> "Give it a name."
        ProfileRefusal.NAME_TAKEN -> "That name is taken."
        ProfileRefusal.TOO_LONG -> "24 characters at most."
        else -> "That didn't work."
    }
}

@Composable
fun MainMenuRoute(
    onOpenPicker: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: MainMenuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MainMenuEffect.OpenPicker -> onOpenPicker()
                MainMenuEffect.OpenCategories -> onOpenCategories()
            }
        }
    }
    MainMenuScreen(state = state, onAction = viewModel::onAction)
}

/** The stateless first-run menu; [MainMenuRoute] owns the ViewModel. */
@Composable
fun MainMenuScreen(state: MainMenuState, onAction: (MainMenuAction) -> Unit) {
    val colors = AppTheme.colors
    ScreenScaffold(topBar = { MainMenuTopBar(state.greeting) { onAction(MainMenuAction.HowTo) } }) { _ ->
        if (state.failed) {
            ErrorCard(
                onAction = { onAction(MainMenuAction.Problem(it)) },
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
            return@ScreenScaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                "Pick the profile that fits your weeks right now. A profile holds your templates; each " +
                    "template is one kind of day, suggested on its days.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
            Button(
                onClick = { onAction(MainMenuAction.NewProfile) },
                modifier = Modifier.fillMaxWidth().testTag("main_menu_new_profile"),
            ) {
                Text("+ New profile")
            }
            state.profiles.forEach { profile ->
                MenuProfileCard(profile) { onAction(MainMenuAction.UseProfile(profile.id)) }
            }
            AppCard(
                onClick = { onAction(MainMenuAction.OpenCategories) },
                modifier = Modifier.fillMaxWidth().testTag("main_menu_categories"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Palette, null, tint = colors.textPrimary)
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text("Categories", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Text(
                            "The kinds of blocks: CV, Coding, Gym and your own. Shared by every profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    Icon(AppIcons.KeyboardArrowRight, null, tint = colors.textMuted)
                }
            }
        }
        state.newProfile?.let { ui -> NewProfileDialog(ui, onAction) }
        if (state.howTo) {
            HowToUse(state.howToPage) { howToAction ->
                onAction(
                    when (howToAction) {
                        is HowToAction.Page -> MainMenuAction.HowToPage(howToAction.page)
                        HowToAction.Close -> MainMenuAction.CloseHowTo
                    },
                )
            }
        }
    }
}

@Composable
private fun MainMenuTopBar(greeting: String, onHowTo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            greeting,
            style = MaterialTheme.typography.headlineSmall,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onHowTo, modifier = Modifier.testTag("how_to")) {
            Icon(AppIcons.HelpOutline, "How to use")
        }
    }
}

@Composable
private fun MenuProfileCard(profile: MenuProfileUi, onUse: () -> Unit) {
    val colors = AppTheme.colors
    AppCard(
        borderColor = if (profile.active) colors.secondary else null,
        modifier = Modifier.fillMaxWidth().testTag("menu_profile_${profile.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                profile.name,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (profile.active) {
                Spacer(Modifier.width(Spacing.s))
                ActiveChip()
            }
        }
        profile.templates.forEach { template ->
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                Text(template.title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    template.days,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (template.days == "No days") colors.textMuted else colors.textSecondary,
                )
            }
        }
        Button(
            onClick = onUse,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).testTag("use_${profile.id}"),
        ) {
            Text("Use this profile")
        }
    }
}

@Composable
private fun ActiveChip() {
    Text(
        "Active",
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.colors.textSecondary,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .border(1.dp, AppTheme.colors.outline, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun NewProfileDialog(ui: NewProfileUi, onAction: (MainMenuAction) -> Unit) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = { onAction(MainMenuAction.DismissNew) },
        title = { Text("New profile", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = ui.name,
                    onValueChange = { if (it.length <= 24) onAction(MainMenuAction.NewName(it)) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("menu_profile_name"),
                )
                Spacer(Modifier.height(Spacing.m))
                RadioRow("Start empty", !ui.copyActive) { onAction(MainMenuAction.CopyActive(false)) }
                RadioRow("Copy ${ui.activeName}", ui.copyActive) { onAction(MainMenuAction.CopyActive(true)) }
                ui.error?.let {
                    Spacer(Modifier.height(Spacing.s))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.leak)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAction(MainMenuAction.Create) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { onAction(MainMenuAction.DismissNew) }) { Text("Cancel") } },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(Spacing.s))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textPrimary)
    }
}
