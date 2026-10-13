package com.roni.blocks.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.roni.blocks.core.profiles.ProfileBook
import com.roni.blocks.core.profiles.ProfileEdit
import com.roni.blocks.core.profiles.ProfileRefusal
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.storage.ProfileActions
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.SectionHeader
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing
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
import kotlinx.serialization.builtins.serializer

@Immutable
data class ProfileRowUi(val id: String, val name: String, val active: Boolean, val summary: String)

@Immutable
data class NewProfileUi(
    val name: String = "",
    val copyActive: Boolean = true,
    val activeName: String = "",
    val error: String? = null,
)

@Immutable
data class ProfilesState(
    val rows: ImmutableList<ProfileRowUi> = persistentListOf(),
    val showHint: Boolean = false,
    val newProfile: NewProfileUi? = null,
    val howTo: Boolean = false,
    val howToPage: Int = 0,
)

sealed interface ProfilesAction {
    data class Open(val id: String) : ProfilesAction
    data object NewProfile : ProfilesAction
    data class NewName(val text: String) : ProfilesAction
    data class CopyActive(val copy: Boolean) : ProfilesAction
    data object Create : ProfilesAction
    data object DismissNew : ProfilesAction
    data object OpenCategories : ProfilesAction
    data object HowTo : ProfilesAction
    data class HowToPage(val page: Int) : ProfilesAction
    data object CloseHowTo : ProfilesAction
}

sealed interface ProfilesEffect {
    data class OpenProfile(val id: String) : ProfilesEffect
    data object OpenCategories : ProfilesEffect
}

/** The Profiles list (3.2.0): every profile, the active one first, plus the shared categories entry. */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val store: TemplateStore,
    private val actions: ProfileActions,
    private val prefs: PrefsStorage,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfilesState())
    val state: StateFlow<ProfilesState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ProfilesEffect>()
    val effects: SharedFlow<ProfilesEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            val seen = prefs.read(PrefsFile.SETTINGS, HOW_TO_SEEN, Boolean.serializer()) ?: false
            _state.update { it.copy(showHint = !seen) }
        }
        viewModelScope.launch {
            store.profiles.collect { book -> _state.update { it.copy(rows = rowsOf(book)) } }
        }
    }

    fun onAction(action: ProfilesAction) {
        when (action) {
            is ProfilesAction.Open -> viewModelScope.launch { _effects.emit(ProfilesEffect.OpenProfile(action.id)) }
            ProfilesAction.NewProfile -> _state.update {
                it.copy(newProfile = NewProfileUi(activeName = store.profiles.value.active.name))
            }
            is ProfilesAction.NewName -> _state.update { it.copy(newProfile = it.newProfile?.copy(name = action.text, error = null)) }
            is ProfilesAction.CopyActive -> _state.update { it.copy(newProfile = it.newProfile?.copy(copyActive = action.copy)) }
            ProfilesAction.Create -> create()
            ProfilesAction.DismissNew -> _state.update { it.copy(newProfile = null) }
            ProfilesAction.OpenCategories -> viewModelScope.launch { _effects.emit(ProfilesEffect.OpenCategories) }
            ProfilesAction.HowTo -> {
                _state.update { it.copy(howTo = true, howToPage = 0, showHint = false) }
                viewModelScope.launch { prefs.write(PrefsFile.SETTINGS, HOW_TO_SEEN, true, Boolean.serializer()) }
            }
            is ProfilesAction.HowToPage -> _state.update { it.copy(howToPage = action.page) }
            ProfilesAction.CloseHowTo -> _state.update { it.copy(howTo = false) }
        }
    }

    private fun create() {
        val ui = _state.value.newProfile ?: return
        viewModelScope.launch {
            when (val result = actions.create(ui.name, ui.copyActive)) {
                is ProfileEdit.Refused -> _state.update { it.copy(newProfile = ui.copy(error = refusalText(result.why))) }
                is ProfileEdit.Ok -> {
                    _state.update { it.copy(newProfile = null) }
                    _effects.emit(ProfilesEffect.OpenProfile(result.book.profiles.last().id))
                }
            }
        }
    }

    private fun rowsOf(book: ProfileBook): ImmutableList<ProfileRowUi> =
        book.profiles.sortedByDescending { it.id == book.activeId }.map {
            ProfileRowUi(
                id = it.id,
                name = it.name,
                active = it.id == book.activeId,
                summary = "${it.templateIds.size} templates · ${it.days.orEmpty().size} days set",
            )
        }.toPersistentList()

    private fun refusalText(why: ProfileRefusal): String = when (why) {
        ProfileRefusal.EMPTY_NAME -> "Give it a name."
        ProfileRefusal.NAME_TAKEN -> "That name is taken."
        ProfileRefusal.TOO_LONG -> "24 characters at most."
        else -> "That didn't work."
    }

    private companion object {
        const val HOW_TO_SEEN = "how_to_seen"
    }
}

@Composable
fun ProfilesRoute(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProfilesEffect.OpenProfile -> onOpenProfile(effect.id)
                ProfilesEffect.OpenCategories -> onOpenCategories()
            }
        }
    }
    ProfilesScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

/** The stateless Profiles list; [ProfilesRoute] owns the ViewModel. */
@Composable
fun ProfilesScreen(state: ProfilesState, onAction: (ProfilesAction) -> Unit, onBack: () -> Unit) {
    ScreenScaffold(topBar = { ProfilesTopBar(onBack = onBack, onHowTo = { onAction(ProfilesAction.HowTo) }) }) { _ ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.m),
            ) {
                item(key = "new") {
                    Button(
                        onClick = { onAction(ProfilesAction.NewProfile) },
                        modifier = Modifier.fillMaxWidth().testTag("profile_new"),
                    ) {
                        Text("+ New profile")
                    }
                    Spacer(Modifier.height(Spacing.m))
                }
                item(key = "header") { SectionHeader("Profiles") }
                if (state.showHint) {
                    item(key = "hint") {
                        Text(
                            "New here? Tap ? for a quick tour.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(bottom = Spacing.s),
                        )
                    }
                }
                items(state.rows) { row ->
                    AppCard(
                        onClick = { onAction(ProfilesAction.Open(row.id)) },
                        modifier = Modifier.fillMaxWidth().testTag("profile_${row.id}"),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        row.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = AppTheme.colors.textPrimary,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (row.active) {
                                        Spacer(Modifier.width(Spacing.s))
                                        Tag("Active")
                                    }
                                }
                                Text(row.summary, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.s))
                }
                item(key = "categories") {
                    AppCard(
                        onClick = { onAction(ProfilesAction.OpenCategories) },
                        modifier = Modifier.fillMaxWidth().testTag("categories_row"),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Palette, null, tint = AppTheme.colors.textPrimary)
                            Spacer(Modifier.width(Spacing.m))
                            Column(Modifier.weight(1f)) {
                                Text("Categories", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                                Text(
                                    "The kinds of blocks: CV, Coding, Gym and your own. Shared by every profile.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary,
                                )
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AppTheme.colors.textMuted)
                        }
                    }
                }
            }
            state.newProfile?.let { ui -> NewProfileDialog(ui = ui, onAction = onAction) }
            if (state.howTo) {
                HowToUse(state.howToPage) { howToAction ->
                    onAction(
                        when (howToAction) {
                            is HowToAction.Page -> ProfilesAction.HowToPage(howToAction.page)
                            HowToAction.Close -> ProfilesAction.CloseHowTo
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilesTopBar(title: String = "Profiles", onBack: () -> Unit, onHowTo: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
        },
        actions = {
            IconButton(onClick = onHowTo, modifier = Modifier.testTag("how_to")) {
                Icon(Icons.Outlined.HelpOutline, "How to use")
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        modifier = Modifier.testTag("top_bar"),
    )
}

@Composable
private fun NewProfileDialog(ui: NewProfileUi, onAction: (ProfilesAction) -> Unit) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = { onAction(ProfilesAction.DismissNew) },
        title = { Text("New profile", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = ui.name,
                    onValueChange = { if (it.length <= 24) onAction(ProfilesAction.NewName(it)) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_name"),
                )
                Spacer(Modifier.height(Spacing.m))
                RadioRow("Start empty", !ui.copyActive) { onAction(ProfilesAction.CopyActive(false)) }
                RadioRow("Copy ${ui.activeName}", ui.copyActive) { onAction(ProfilesAction.CopyActive(true)) }
                ui.error?.let {
                    Spacer(Modifier.height(Spacing.s))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.leak)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(ProfilesAction.Create) }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ProfilesAction.DismissNew) }) { Text("Cancel") }
        },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.xs),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(Spacing.s))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textPrimary)
    }
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
