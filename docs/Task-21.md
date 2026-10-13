# TASK 21: Profiles (3.2.0)

**Target version: 3.2.0** (versionCode 30200). One agent, `C:/Blocks App`, branch `task-21/v3.2.0`. Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/` or `docs/`.

## Slice map

Read lines 1–38 once, then each slice's lines when you start it.

| Slice | What | Lines | Start in |
|---|---|---|---|
| 1 | Profiles in the data | 40–95 | `core/model/Templates.kt`, `core/storage/TemplateStore.kt`, `core/storage/Actions.kt`, `core/daystate/DayOrchestrator.kt` |
| 2 | The Profiles screens; the Templates tab goes | 97–182 | `navigation/AppNav.kt`, `designsystem/Shell.kt`, new `feature/profiles/` |
| 3 | Picker switcher, How to use, History label | 184–211 | `feature/templatepicker/TemplatePicker.kt`, new `feature/profiles/HowToUse.kt`, `feature/history/History.kt` |
| 4 | Docs and release | 213–end | `docs/`, `CHANGELOG.md`, `app/build.gradle.kts` |

## Decisions (Roni, 13 Oct)

| Topic | Decision |
|---|---|
| Where | The **Templates tab goes away**. The menu (≡) gets **Profiles**, between History and Settings: a list of profiles → a profile's page. Building templates is no longer "inside" the running day |
| Templates | One **shared library**. A profile picks which library templates it uses, and which one each weekday suggests. Editing a template changes it for every profile that uses it. Deleting a template removes it from every profile |
| Weekdays | Belong to the **profile** now (a day → one template of that profile). The editor's weekday chips move to the profile page. The 3.1 weekdays are moved in automatically on the first launch of 3.2.0 |
| New profile | You choose: **Start empty** (only Off day) or **Copy <active profile>** (its templates and days) |
| Switching | From the top of the morning picker ("Unemployed ▾") or a profile's page ("Use this profile"). **Today keeps going**: today's plan never changes; the new profile applies from the next pick (Change day on Home already opens the picker, which then lists the new profile's templates) |
| Categories | **Shared by all profiles**: their own screen, reached from the Profiles list |
| How to use | **Step-by-step cards** (5, swipeable, an icon each) from a **?** button: top right of the Profiles screens (next to the menu) and of the picker. The Profiles list shows a one-line hint "New here? Tap ? for a quick tour." until the tour was opened once |
| History | A small **profile label** on each day ("Unemployed"): the profile the day *started* under. Weekly and Insights count everything together, as now |
| Deleting a profile | Never the active one, never the last one. Its templates stay in the library |

## Rules

1. `git checkout -b task-21/v3.2.0` ; `git add docs app` ; `git rm docs/Task-20.md` ; `git commit -m "task-21 prep: docs and verified Profiles"`. Then `task-21 slice N: <title>`.
2. **Architecture (`docs/architecture.md` §5) is binding**: per screen an `@Immutable` State, a sealed Action, a sealed Effect, a `@HiltViewModel` with injected managers and one `fun onAction(action)`, state only via `_state.update { it.copy(...) }`, effects via `MutableSharedFlow`. The Route is the only composable that knows the ViewModel and turns effects into navigation lambdas; every other composable takes (state, `onAction`). No callback-parameter lists, no `TemplateStore.instance` in new code. Each new screen below lists its types: use them as written. If something seems to need breaking that, stop and say so in the report.
3. *Verified*, don't edit: `core/profiles/Profiles.kt` and `ProfilesTest.kt` (new), plus every file named verified before (`TemplateEditor.kt`, `TemplateRules.kt`, `PlanEditor.kt`, …).
4. Add only the tests named here. List every existing test you change or delete.
5. Real names win: if a name here doesn't exist, use the real one and note it in `docs/NOTES.md`.

---

## Slice 1: Profiles in the data

**`core/model/Templates.kt`:**
```kotlin
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val templateIds: List<TemplateId>,
    /** ISO day (Mon = 1 … Sun = 7) → the template it suggests (3.2.0). Null only in 3.1 data, until [Profiles.repair] moves the weekdays in. */
    val days: Map<Int, TemplateId>? = null,
)
```
`StoredTemplate.weekdays` and `short` stay in the class (old data decodes; `repair` reads `weekdays` once), but nothing writes or shows them any more: mark both `/** 3.1 only; read once by Profiles.repair (3.2.0). */`. New templates write `emptySet()` / `""`.

**`core/profiles/Profiles.kt`** (verified, delivered): `ProfileBook`, `ProfileEdit`, `ProfileRefusal`, `Profiles.repair/create/rename/delete/switchTo/addTemplate/removeTemplate/setDay/dropTemplate/suggestedFor/daysOf`. Read its KDoc; don't re-implement any rule.

**`core/storage/TemplateStore.kt`:**
- Replace `profile` with `book: ProfileBook`, and publish it: `val profiles: StateFlow<ProfileBook>`. `templates` (3.1.1) stays: the **active** profile's templates.
- `load()`: after reading, `book = Profiles.repair(profiles, activeId, stored.associate { it.id to it.weekdays }, UNEMPLOYED_PROFILE)`; if the result differs from what was read, write `KEY_PROFILES` and `KEY_ACTIVE` back. `saveSeed()` goes through `repair` the same way (the seed's weekdays fill Unemployed's days).
- `fun library(): List<StoredTemplate>`: every stored template that isn't archived, built-ins first, then yours in creation order.
- `fun profileTemplates(profileId: String): List<StoredTemplate>`: that profile's templates in its order.
- `suggestedFor(day)` = `Profiles.suggestedFor(book, day.isoDayNumber)`.
- `suspend fun edit(change: (ProfileBook) -> ProfileEdit): ProfileEdit`: runs the change; on `Ok`, sets `book`, writes `KEY_PROFILES` + `KEY_ACTIVE`, publishes `profiles` and `templates`. Every profile write goes through it.
- `create(title)` becomes `createIn(profileId: String, title: String)`: the new library template, then `Profiles.addTemplate`.
- `delete(id)` also applies `Profiles.dropTemplate`.
- Delete `setWeekdays`.
- `fun activeProfileName(): String = book.active.name`.

**`core/storage/Actions.kt`:** `TemplateActions.create(title)` → `createIn(profileId, title)`; drop `setWeekdays`. New:
```kotlin
@Singleton
class ProfileActions @Inject constructor(private val store: TemplateStore) {
    suspend fun create(name: String, copyActive: Boolean) = store.edit { Profiles.create(it, name, copyActive) }
    suspend fun rename(id: String, name: String) = store.edit { Profiles.rename(it, id, name) }
    suspend fun delete(id: String) = store.edit { Profiles.delete(it, id) }
    suspend fun switchTo(id: String) = store.edit { Profiles.switchTo(it, id) }
    suspend fun addTemplate(profileId: String, id: TemplateId) = store.edit { Profiles.addTemplate(it, profileId, id) }
    suspend fun removeTemplate(profileId: String, id: TemplateId) = store.edit { Profiles.removeTemplate(it, profileId, id) }
    suspend fun setDay(profileId: String, iso: Int, id: TemplateId?) = store.edit { Profiles.setDay(it, profileId, iso, id) }
}
```
Switching never touches `DayOrchestrator`: today keeps its plan.

**The day remembers its profile:** `TodayPlan` gets `val profileName: String? = null`, and `DayRecord` gets `val profileName: String? = null` (both last, with defaults: old data decodes). `StartDay` and `TakeOffDay` set it from `templateStore?.activeProfileName()`; `flush()` and `liveRecord()` copy it into the record. For the Off day (no plan), keep it in a `DAY` prefs key `profile_name`, written at `TakeOffDay` and cleared with the other day keys.

**Template editor:** delete the weekday chips, `ToggleDay`, `dayMoved` and `weekdays` from `TemplateEditorState` (days live on the profile page now). The delete dialog text becomes "Delete <title>? It leaves every profile. Days you already had keep their history."

**Tests:**
- `TemplateStoreTest.load31_movesTheWeekdaysIntoTheProfile`: write 3.1-shaped prefs (Unemployed without `days`; the four built-ins with their seed weekdays except Monday, and `user_1` "In-Office" with weekdays `{1}`) → after `load()`: `suggestedFor(MONDAY) == user_1`, `suggestedFor(SUNDAY) == SUN_TUE_WED`, and the stored profiles JSON now has `days`.
- `TemplateStoreTest.profiles_surviveARestart`: create "Employed" as a copy, switch to it, `setDay(…, 1, MON_THU)` → a fresh store after `load()` has Employed active with Monday → `MON_THU`.
- `TemplateStoreTest.deletedTemplate_leavesEveryProfile`: a template in two profiles with a day each; `delete` → in neither, no day points at it, `find()` still finds it (History).
- `DayOrchestratorTest.dayRecord_keepsTheProfileItStartedWith`: `StartDay` under Unemployed, `ProfileActions.switchTo(employed)`, end the day → the record's `profileName == "Unemployed"`, and today's plan didn't change at the switch.
- Update the 3.1 tests that used `setWeekdays`, `create(title)` or the editor's weekday chips (`TemplateStoreTest`, `TemplateEditorViewModelTest`, `TemplatePickerTest`) and list them.

---

## Slice 2: The Profiles screens; the Templates tab goes

**Remove:** `AppTab.TEMPLATES` (and its bar item), `TemplatesKey`, `TemplatesScreen.kt`, `TemplateSection.kt` with `TemplateListViewModel`, and their tests (`TemplatesScreenTest`, `TemplateListViewModelTest`: list them as deleted). Their card look moves to the profile page.

**Menu:** `MenuDestination.Profiles("Profiles", Icons.Outlined.People)` between History and Settings. Every menu caller handles it (`AppTopBar` gets `onProfiles: () -> Unit = {}` beside `onHistory`/`onSettings`, the existing style; Home, Weekly, Insights route it to `ProfilesKey`).

**Navigation (`AppNav.kt`):** `ProfilesKey`, `data class ProfileKey(val id: String)`, `CategoriesKey`, plus the existing `TemplateEditorKey`. All pushed like History (back pops).

### `feature/profiles/ProfilesScreen.kt`: the list
```kotlin
@Immutable data class ProfileRowUi(val id: String, val name: String, val active: Boolean, val summary: String)   // "5 templates · 7 days set"
@Immutable data class NewProfileUi(val name: String = "", val copyActive: Boolean = true, val activeName: String = "", val error: String? = null)
@Immutable data class ProfilesState(val rows: ImmutableList<ProfileRowUi> = persistentListOf(), val showHint: Boolean = false, val newProfile: NewProfileUi? = null, val howTo: Boolean = false)
sealed interface ProfilesAction {
    data class Open(val id: String) : ProfilesAction
    data object NewProfile : ProfilesAction
    data class NewName(val text: String) : ProfilesAction
    data class CopyActive(val copy: Boolean) : ProfilesAction
    data object Create : ProfilesAction
    data object DismissNew : ProfilesAction
    data object OpenCategories : ProfilesAction
    data object HowTo : ProfilesAction            // also hides the hint for good
    data object CloseHowTo : ProfilesAction
}
sealed interface ProfilesEffect { data class OpenProfile(val id: String) : ProfilesEffect ; data object OpenCategories : ProfilesEffect }
@HiltViewModel class ProfilesViewModel @Inject constructor(store: TemplateStore, actions: ProfileActions, prefs: PrefsStorage)
```
- Rows follow `store.profiles`; the active one first, with an "Active" chip.
- **Hint:** `showHint` while the `SETTINGS` key `how_to_seen` is absent; `HowTo` writes it.
- **New profile dialog:** name field; two radio rows "Start empty" / "Copy <activeName>" (default: copy); "Create". `Create` → `ProfileActions.create`; `Refused` → `error` ("Give it a name." / "That name is taken." / "24 characters at most."); `Ok` → close, emit `OpenProfile(<the new id>)` (the last profile in the returned book).
- Under the list: a "Categories" row (icon `Outlined.Palette`, "Shared by every profile") → `OpenCategories`.
- Top bar: back, "Profiles", the **?** button (`Icons.Outlined.HelpOutline`, tag `how_to`) → `HowTo`. With `howTo`, show `HowToUse` (slice 3; until then a placeholder `Box`).

### `feature/profiles/ProfileScreen.kt`: one profile
```kotlin
@Immutable data class ProfileTemplateUi(val id: TemplateId, val title: String, val days: String, val icon: String, val accent: Long, val builtIn: Boolean, val removable: Boolean)  // days: shortFor(daysOf(...)), "" when none
@Immutable data class DayRowUi(val iso: Int, val dayName: String, val templateTitle: String?)        // "Sunday", "Sunday / Tuesday / Wednesday" or null → "No suggestion"
@Immutable data class ProfileState(
    val id: String = "", val name: String = "", val active: Boolean = false, val canDelete: Boolean = false,
    val templates: ImmutableList<ProfileTemplateUi> = persistentListOf(),
    val days: ImmutableList<DayRowUi> = persistentListOf(),          // Sunday first: 7, 1, 2, 3, 4, 5, 6
    val dayPicker: Int? = null,                                     // the ISO day whose sheet is open
    val addExisting: ImmutableList<ProfileTemplateUi>? = null,      // library templates not in this profile; null = sheet closed
    val newTemplate: String? = null,                                // the New template dialog's name; null = closed
    val confirmDelete: Boolean = false, val error: String? = null, val howTo: Boolean = false,
)
sealed interface ProfileAction {
    data class Rename(val name: String) : ProfileAction            // on focus loss
    data object UseThis : ProfileAction
    data class OpenTemplate(val id: TemplateId) : ProfileAction
    data class RemoveTemplate(val id: TemplateId) : ProfileAction
    data object AddExisting : ProfileAction
    data class AddTemplate(val id: TemplateId) : ProfileAction
    data object NewTemplate : ProfileAction
    data class NewTemplateName(val text: String) : ProfileAction
    data object CreateTemplate : ProfileAction
    data class OpenDay(val iso: Int) : ProfileAction
    data class SetDay(val iso: Int, val id: TemplateId?) : ProfileAction
    data object Delete : ProfileAction
    data object ConfirmDelete : ProfileAction
    data object DismissSheet : ProfileAction                        // closes whichever sheet or dialog is open
    data object HowTo : ProfileAction
    data object CloseHowTo : ProfileAction
}
sealed interface ProfileEffect { data class OpenTemplate(val id: TemplateId) : ProfileEffect ; data object Closed : ProfileEffect }
@HiltViewModel class ProfileViewModel @Inject constructor(savedState: SavedStateHandle /* or the repo's existing way to pass ProfileKey.id */, store: TemplateStore, profiles: ProfileActions, templates: TemplateActions)
```
Layout, top to bottom:
1. Top bar: back, the profile's name, **?**.
2. Name field (1–24 chars, saved on focus loss; errors as in the list). Under it: "Active" chip, or a primary **"Use this profile"** button (`UseThis` → `switchTo`; caption "Today's day keeps going; this profile starts with your next pick.").
3. **Days** (header "Which template each day suggests"): 7 rows, Sunday first: "Sunday" + the template's title or muted "No suggestion". Tap → a sheet listing this profile's templates (icon + title) and "No suggestion" (`SetDay`).
4. **Templates** (header "Templates in this profile"): one card each, as 3.1's Templates tab drew them (icon in accent, title, days, strip, "Built-in" tag). Tap → `OpenTemplate` (the editor). A ⋮ on each card except Off: "Remove from profile" (`RemoveTemplate`).
5. Two buttons: **"+ New template"** (dialog with a name → `TemplateActions.createIn(id, name)` → emit `OpenTemplate`) and **"+ Add existing"** (a sheet of library templates not in this profile, or "Every template is already here."; tap adds and closes).
6. **"Delete profile"** in `Leak`, only when `canDelete` (not active, not the last) → confirm "Delete <name>? Its templates stay in your library." → `Closed` effect (back).

### `feature/profiles/CategoriesScreen.kt`
A route with a top bar (back, "Categories", **?**) whose content is the existing `CategorySection()` (unchanged ViewModel and sheet). Move `CategorySection.kt` / `CategorySheet.kt` to `feature/profiles/` only if it's a pure move; otherwise leave them where they are.

**Tests** (`testActions(context)`; add `profiles: ProfileActions` to `TestRig`):
- `ProfilesViewModelTest.newProfile_copy_opensIt`: `NewProfile`, `NewName("Employed")`, `Create` → one `OpenProfile("profile_1")`; `rows` has two, Unemployed active.
- `ProfilesViewModelTest.hint_untilTheTourIsOpened`: fresh prefs → `showHint`; `HowTo` → `howTo` and `!showHint`; a new ViewModel → still `!showHint`.
- `ProfileViewModelTest.setDay_changesTheSuggestion`: open Unemployed, `SetDay(1, MON_THU)` → `days` row Monday reads "Monday / Thursday", and `store.suggestedFor(MONDAY) == MON_THU`.
- `ProfileViewModelTest.addExisting_thenRemove`: a new empty profile; `AddExisting` lists the 3 non-Off built-ins plus own templates; `AddTemplate(MON_THU)` → in `templates`; `RemoveTemplate(MON_THU)` → gone and no day points at it.
- `ProfileViewModelTest.activeProfile_cantBeDeleted`: open the active profile → `!canDelete`; open the other one → `canDelete`; `UseThis` on it → it's active, the first one's `canDelete` turns true.

---

## Slice 3: Picker switcher, How to use, History label

**Picker (`TemplatePicker.kt`):**
- `PickerUiState` gets `profileName: String`, `profiles: ImmutableList<Pair<String, String>>` (id, name), `profileMenu: Boolean`, `howTo: Boolean`. `PickerAction` gets `OpenProfileMenu`, `SwitchProfile(id)`, `DismissProfileMenu`, `HowTo`, `CloseHowTo`.
- The grid's first full-span item starts with a row: a chip "**Unemployed ▾**" (tag `picker_profile`; tap → a `DropdownMenu` of profiles, the active one checked; `SwitchProfile` → `ProfileActions.switchTo`) and, at the end, the **?** button (tag `how_to`). Then the greeting, date and question as now.
- Cards: the active profile's templates (already through `templates`); `suggested` and the card's day line from the active profile (`Profiles.suggestedFor`, `shortFor(Profiles.daysOf(...))`).

**`feature/profiles/HowToUse.kt`:** `HowToUse(page: Int, onAction: (HowToAction) -> Unit)` as a full-height `ModalBottomSheet` with a `HorizontalPager` of 5 cards, dots under it, and "Back" / "Next" (on the last card "Done"). The page lives in the caller's state (`howToPage: Int` next to `howTo` in each screen's State; `sealed interface HowToAction { data class Page(val page: Int); data object Close }`, wrapped as each screen's `HowTo…` actions). Each card: a 56 dp icon in a 96 dp circle of `surfaceRaised`, a title (`titleLarge`), the text (`bodyLarge`, `textSecondary`). Exactly:

| # | Icon | Title | Text |
|---|---|---|---|
| 1 | `Outlined.People` | Profiles | A profile is how your weeks look right now, like Unemployed or Employed. It holds the templates you use and which one each day suggests. Switch it at the top of the morning picker. |
| 2 | `Outlined.ViewAgenda` | Templates | A template is one kind of day: a gym day, an office day. All templates live in one library, and each profile picks the ones it uses. Change a template once and every profile using it follows. |
| 3 | `Outlined.Schedule` | Blocks | Open a template and tap + Add block. Choose a category, a start and a length. Blocks after it move later to make room, and kid time at the end gets shorter. Built-in templates can't be changed: start your own from blank. |
| 4 | `Outlined.CalendarMonth` | Days | On a profile's page, tap a weekday to choose its template. That morning the picker marks it Suggested; you can always pick another. |
| 5 | `Outlined.Palette` | Categories | Categories are the kinds of blocks: CV, Coding, Gym, or your own. Each has a colour, Pomodoro on or off, and whether it counts as work. Every profile shares them. |

Shown from the **?** on the Profiles list, a profile's page, Categories and the picker.

**History:** the day header shows the profile under the template title, `labelSmall` in `textMuted` ("Unemployed"), when `profileName` isn't null. `HistoryViewModel` maps it; old days show nothing.

**Tests:**
- `TemplatePickerTest.switchProfile_changesTheCards`: an empty "Employed" profile; `SwitchProfile("profile_1")` → `profileName == "Employed"`, the only card is Off day, nothing `suggested`.
- `TemplatePickerTest.suggested_comesFromTheProfile`: Unemployed with Monday → `user_1`, the clock on a Monday → that card is `suggested`.
- `HowToUseTest.fiveCards_doneOnTheLast` (Robolectric Compose): page 0 shows "Profiles" and "Next"; page 4 shows "Categories" and "Done"; "Done" sends `Close`.
- `HistoryViewModelTest.day_showsItsProfile`: a record with `profileName = "Unemployed"` → the header's profile text; a record without → none.

---

## Slice 4: Docs and release 3.2.0

1. `docs/CODEMAP.md` (`core/profiles/Profiles.kt`, `ProfileActions`, the three profile screens, `HowToUse`; remove the Templates tab), `docs/architecture.md` (the menu's Profiles entry; profiles in the data model), `docs/NOTES.md` (one line per decision).
2. `docs/MANUAL_TESTS.md`, "Check on the phone (3.2.0)":
   1. After installing over 3.1.1: ≡ → Profiles shows Unemployed, active, and its days match what 3.1 suggested (Monday = In-Office if you set it).
   2. The hint shows; ? opens the 5 cards; after closing, the hint is gone for good.
   3. New profile "Employed", Start empty → only Off day. + New template "Office day", add blocks; + Add existing → Gym day; Days: Sunday–Thursday → Office day.
   4. Use this profile → today's day keeps going unchanged; tomorrow's picker shows "Employed ▾" and suggests Office day.
   5. The picker's switcher flips between the two; the cards follow.
   6. Remove a template from Employed: it stays in Unemployed. Delete a template in the editor: it leaves both.
   7. Delete Employed while it's active: not offered. Switch back, then delete it.
   8. History: today's day says "Unemployed" under its template.
3. `versionName = "3.2.0"`, `versionCode = 30200`. `CHANGELOG.md`:
   ```markdown
   ## 3.2.0
   - Profiles: sets of templates with their own weekday suggestions (Unemployed, Employed…). Menu → Profiles.
   - Templates are one library: a profile picks the ones it uses; + New template or + Add existing.
   - Switch profiles from the top of the picker. Today keeps going; the new profile starts with your next pick.
   - How to use: a short tour from the ? button.
   - History shows which profile each day ran under.
   - The Templates tab is gone: everything lives under Profiles.
   ```
4. Final gate (the `cmd /c` form), merge into `master`, tag `v3.2.0`, push `master` and the tag. A report of at most 15 lines (every slice, the tests changed or deleted), then stop.
