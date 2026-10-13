# TASK 20: A picker that scrolls, a template list that stays fresh (3.1.1)

**Target version: 3.1.1** (versionCode 30101). One agent, `C:/Blocks App`, branch `task-20/v3.1.1`. Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/` or `docs/`.

## What Roni found on 3.1.0 (13 Oct, screenshot)

1. With a fifth template ("In-Office"), the picker's Off day card is drawn **over** Friday/Saturday and In-Office.
2. (Planning review) After renaming, re-daying or deleting a template in the editor, back in the Templates tab the card still shows the old version.

## Causes

| # | Cause |
|---|---|
| 1 | `TemplatePickerScreen` is a non-scrolling `Column(fillMaxSize, Arrangement.Center)` that stacks `cards.chunked(2)` rows of `aspectRatio` cards. Four cards fit; five overflow the screen, and the odd last card takes the full row width (`weight(1f)` alone in its row) |
| 2 | `TemplateListViewModel` reads `TemplateStore.instance` once (and on `refresh()`), so it never hears about writes made by the editor. It's also not a `@HiltViewModel`, and `NewTemplateViewModel` + a callback do the create, against `docs/architecture.md` §5 |

## Rules

1. `git checkout -b task-20/v3.1.1` ; `git add docs` ; `git rm docs/Task-19.md docs/Task-19a.md docs/Task-19b.md` ; `git commit -m "task-20 prep: docs"`. Then `task-20 slice N: <title>`.
2. **Architecture (`docs/architecture.md` §5) is binding.** Per screen: `@Immutable` State, sealed Action, sealed Effect, `@HiltViewModel` with injected managers, one `fun onAction(action)`, state only via `_state.update { it.copy(...) }`, effects through a `MutableSharedFlow`. The Route (or the section's top composable) is the only composable that knows the ViewModel; everything below takes (state, `onAction`). No callback-parameter lists. If something here seems to require breaking that, stop and say so in the report instead.
3. Verified, don't edit: every file named verified before (`TemplateEditor.kt`, `TemplateRules.kt`, `PlanEditor.kt`, …).
4. Add only the tests named here; list any existing test you change.

---

## Slice 1: The store publishes its templates; the list follows

**`core/storage/TemplateStore.kt`:**
```kotlin
private val _templates = MutableStateFlow<List<StoredTemplate>>(emptyList())
/** The active profile's templates, in its order (3.1.1). Every write and every load publishes. */
val templates: StateFlow<List<StoredTemplate>> = _templates.asStateFlow()
```
Initialise it with `all()` once `seed` is parsed, and set `_templates.value = all()` at the end of `load()`, `saveSeed()`, `create`, `save`, `delete`, `setWeekdays` and `replaceCategory`. `all()` stays as it is.

**`feature/templates/TemplateSection.kt`:**
```kotlin
@Immutable
data class TemplateSectionState(
    val templates: ImmutableList<StoredTemplate> = persistentListOf(),
    val newDialog: NewTemplateUi? = null,          // null = the New dialog is closed
)
@Immutable
data class NewTemplateUi(val name: String = "", val canCreate: Boolean = false)   // canCreate: 1–24 chars after trim

sealed interface TemplateListAction {
    data class Open(val id: TemplateId) : TemplateListAction
    data object NewTemplate : TemplateListAction
    data class NewName(val text: String) : TemplateListAction
    data object Create : TemplateListAction
    data object DismissNew : TemplateListAction
}

sealed interface TemplateListEffect {
    data class OpenTemplate(val id: TemplateId) : TemplateListEffect
}

@HiltViewModel
class TemplateListViewModel @Inject constructor(
    private val store: TemplateStore,
    private val actions: TemplateActions,
) : ViewModel()
```
- `init`: `store.templates` collected in `viewModelScope` → `_state.update { it.copy(templates = list.toPersistentList()) }`.
- `Open(id)` → emit `OpenTemplate(id)`. `Create` → `actions.create(name.trim())`, close the dialog, emit `OpenTemplate(created.id)`.
- Delete `refresh()`, `NewTemplateViewModel`, and the `TemplateStore.instance` fallback in this file.
- `TemplateSection(onOpenTemplate: (TemplateId) -> Unit)`: gets the ViewModel with `hiltViewModel()`, collects state, collects effects in a `LaunchedEffect(Unit)` and calls `onOpenTemplate`. It passes `(state, viewModel::onAction)` to a stateless `TemplateList(state, onAction)` and `NewTemplateDialog(ui, onAction)`. The name lives in `NewTemplateUi`, not in `remember`.
- `TemplatesRoute` and `AppNav`: drop the unused `onNewTemplate` parameter.

**Tests:**
- `TemplateStoreTest.templatesFlow_followsEveryWrite`: `create("Office day")` → `templates.value` has `user_1` before `OFF`; `save` with a new title → the flow has the new title; `delete` → gone.
- `TemplateListViewModelTest` (new; Robolectric; `testActions(context)`, ViewModel built from `rig.store` and `rig.templates`):
  - `list_followsTheEditor`: create through `rig.templates`, rename it through `rig.templates.save(...)` → `state.templates` has the new title, with no other call; delete → gone.
  - `create_opensTheNewTemplate`: `NewTemplate`, `NewName("Office day")`, `Create` → one `OpenTemplate(TemplateId("user_1"))` effect, `newDialog == null`.

---

## Slice 2: The picker scrolls

**`feature/templatepicker/TemplatePicker.kt`:**
- `TemplatePickerViewModel` also collects `templates.templates` (`TemplateStore`) and republishes on every change, besides the hourly tick.
- Add `sealed interface PickerEffect { data object Started : PickerEffect }`. `Pick` sends the day event, then emits `Started`. `TemplatePickerRoute` collects effects and calls `onStarted()`; `TemplatePickerScreen(state, onAction: (PickerAction) -> Unit)` replaces the `onPick` lambda.
- The body becomes **`LazyVerticalGrid(columns = GridCells.Fixed(2))`** (tag `picker_grid`), filling the screen inside the safe-drawing insets, with `contentPadding = PaddingValues(Spacing.l)`, `horizontalArrangement` and `verticalArrangement` = `spacedBy(Spacing.m)`:
  - the greeting, the date and "How does today look?" as one `item(span = { GridItemSpan(maxLineSpan) })`, top-aligned (no more `Arrangement.Center`);
  - one `items(state.cards, key = { it.id.key })` per card;
  - "You can swap later from Home" as a last full-span item.
- `TemplateCard` drops its `RowScope` receiver and `weight(1f)`: `Modifier.fillMaxWidth().aspectRatio(Metrics.cardAspect)`. An odd last card therefore keeps half width, in the left column.
- The cards' enter animation (`delay(index * 60L)`) stays.

**Test:** `TemplatePickerTest.manyTemplates_scrollAndNeverOverlap` (Robolectric Compose): a store with three own templates (seven cards) → `onNodeWithTag("picker_grid").performScrollToNode(hasTestTag("template_card_OFF"))` succeeds, and the bounds of `template_card_FRI_SAT` and `template_card_OFF` don't intersect.

---

## Slice 3: Docs and release 3.1.1

1. `docs/CODEMAP.md` (`TemplateStore.templates`, the grid), `docs/NOTES.md` (one line per cause above).
2. `docs/MANUAL_TESTS.md`, "Check on the phone (3.1.1)":
   1. Five or more templates: the picker scrolls; Off day sits in the grid, nothing overlaps.
   2. Templates tab: open a template, rename it, change a day, back → the card shows the new name and days at once.
   3. Delete a template, back → its card is gone.
   4. + New template → name → Create opens the editor.
3. `versionName = "3.1.1"`, `versionCode = 30101`. `CHANGELOG.md`:
   ```markdown
   ## 3.1.1
   - The template picker scrolls: any number of templates fits, nothing overlaps.
   - The Templates list updates as soon as you come back from the editor.
   ```
4. Final gate (the `cmd /c` form), merge into `master`, tag `v3.1.1`, push `master` and the tag. A report of at most 10 lines (slices, tests changed), then stop.
