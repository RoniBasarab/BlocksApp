# TASK 22, Track B: First run, explainers and Profiles (3.3.0)

You work in `C:/Blocks App 22b` on `task-22b/v3.3.0-menu`. Another agent works on Home and the evening in `C:/Blocks App 22a` right now. Read `docs/Task-22.md` lines 1–36 first.

**Your files:** `feature/profiles/` (all), new `feature/mainmenu/`, `designsystem/` (new `Explainer.kt`; the tag style), `navigation/AppNav.kt`, `MainActivity.kt`, `feature/settings/Settings.kt` (two debug buttons only), `core/storage/TemplateStore.kt` (the chosen flag), `core/storage/Actions.kt` (`ProfileActions` marks it), new `core/storage/ExplainerStore.kt`, and their tests. **Not** `feature/home/`, `feature/templates/`, `core/daystate/`, `feature/history/`, `feature/insights/`, `core/notifications/`: Track A owns those.

## Slice map

| Slice | What |
|---|---|
| 1 | Bugs: Off day, the "Built-in" tag; Profiles list changes |
| 2 | Explainer cards |
| 3 | The first-run main menu |
| 4 | Done (push, no release) |

---

## Slice 1: Bugs and the Profiles list

- **Off day** on a profile's page: its card isn't clickable and reads "No blocks, no pings" under the title (no ⋮ either, as now). `ProfileViewModel` ignores `OpenTemplate(OFF)`.
- **"Built-in" tag:** never wraps. The tag is one line (`maxLines = 1`, `softWrap = false`); the title next to it gets `Modifier.weight(1f, fill = false)`, `maxLines = 1`, `overflow = Ellipsis`. Same everywhere that tag is used (`git grep -n "Built-in"`).
- **Profiles list (`ProfilesScreen.kt`):**
  - "+ New profile" is the first thing under the top bar: a full-width button.
  - The Categories row explains itself: title "Categories", text "The kinds of blocks: CV, Coding, Gym and your own. Shared by every profile.", chevron.

**Tests:** `ProfileViewModelTest.offDay_opensNothing` (`OpenTemplate(OFF)` emits no effect).

---

## Slice 2: Explainer cards

**`core/storage/ExplainerStore.kt`** (`@Singleton`, injected `PrefsStorage`): `suspend fun seen(key: ExplainerKey): Boolean`, `suspend fun markSeen(key)`, `suspend fun resetAll()`. Keys in `SETTINGS`: `explained_<key.name.lowercase()>`.
```kotlin
enum class ExplainerKey { PROFILES, TEMPLATES, DAYS, BLOCKS, CATEGORIES }
```

**The cards' texts** move out of `HowToUse.kt` into one shared list (`HowToCards`, keyed by `ExplainerKey`, same icon/title/text). The tour and the explainers both read it; the texts don't change.

**`designsystem/Explainer.kt`:** `ExplainerDialog(key: ExplainerKey, onGotIt: () -> Unit)`: a `Dialog` with `DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)`, the card's 96 dp icon circle, title, text and one primary button "Got it" (tag `explainer_ok`). Nothing else closes it.

**`FirstVisit` wrapper** (in `feature/profiles/`, used from `AppNav`):
```kotlin
@Immutable data class FirstVisitState(val show: Boolean = false)
sealed interface FirstVisitAction { data object GotIt : FirstVisitAction }
@HiltViewModel class FirstVisitViewModel @Inject constructor(store: ExplainerStore, savedState: SavedStateHandle)   // or a key param via the repo's existing pattern
@Composable fun FirstVisit(key: ExplainerKey, content: @Composable () -> Unit)   // draws content, then the dialog while state.show
```
- The ViewModel reads `seen(key)` once; `show = !seen`. `GotIt` → `markSeen`, `show = false`.
- In `AppNav`: wrap the Profiles list (`PROFILES`), a profile's page (`TEMPLATES`), the template editor entry (`BLOCKS`) and Categories (`CATEGORIES`). The main menu (slice 3) uses `PROFILES` too.
- **Days** is a tap, not a screen: `ProfileState` gets `explainDays: Boolean`; the first `OpenDay` while `DAYS` isn't seen shows the dialog **instead of** the day sheet (`ProfileAction.GotItDays` marks it seen and then opens the sheet for that day).
- **Developer tools** (`Settings.kt`, debug section): "Reset explainers" → `ExplainerStore.resetAll()` and the `how_to_seen` hint key.

**Tests:**
- `FirstVisitViewModelTest.showsOnce`: fresh prefs → `show`; `GotIt` → not; a new ViewModel → still not.
- `ProfileViewModelTest.firstDayTap_explainsThenOpens`: first `OpenDay(1)` → `explainDays`, `dayPicker == null`; `GotItDays` → `dayPicker == 1`; a second `OpenDay(2)` opens directly.
- `ExplainerDialogTest.onlyGotItCloses` (Robolectric Compose): pressing Back and clicking outside leave it shown; "Got it" calls `onGotIt`.

---

## Slice 3: The first-run main menu

**The chosen flag (`TemplateStore`):** `SETTINGS` key `profile_chosen`. `fun profileChosen(): Boolean`, `suspend fun markProfileChosen()`. In `load()`: when the key is missing and the stored templates were **read** (not seeded now), write `true` (an existing install has chosen). A fresh install (seeded in this `load`) stays without it. `ProfileActions.switchTo` and `create` also mark it.

**Start destination (`AppNav.kt`, `MainActivity.kt`):** `StartDestination.MAIN_MENU`; `resolveStartDestination(state, profileChosen)`: `NotStarted` and not chosen → `MAIN_MENU`; otherwise as now. Update its existing tests (list them).

**`feature/mainmenu/MainMenuScreen.kt`:**
```kotlin
@Immutable data class MenuTemplateUi(val title: String, val days: String)                         // "Gym day", "MON · THU" or "No days"
@Immutable data class MenuProfileUi(val id: String, val name: String, val active: Boolean, val templates: ImmutableList<MenuTemplateUi>)
@Immutable data class MainMenuState(
    val greeting: String = "", val profiles: ImmutableList<MenuProfileUi> = persistentListOf(),
    val newProfile: NewProfileUi? = null, val howTo: Boolean = false, val howToPage: Int = 0,
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
}
sealed interface MainMenuEffect { data object OpenPicker : MainMenuEffect ; data object OpenCategories : MainMenuEffect }
@HiltViewModel class MainMenuViewModel @Inject constructor(store: TemplateStore, profiles: ProfileActions, clock: Clock, zone: TimeZone)
```
Layout (the picker's background and type):
1. Top: "Welcome, Roni" (`headlineSmall`) and the **?** (tag `how_to`).
2. "Pick the profile that fits your weeks right now. A profile holds your templates; each template is one kind of day, suggested on its days." (`bodyLarge`, `textSecondary`).
3. "+ New profile", full width (the same dialog as the Profiles list: empty or copy).
4. One **big card per profile** (active first, a sand outline and an "Active" chip): the name (`titleLarge`), then each template on its own line: title left, days right ("MON · THU", muted "No days"; Off day is left out). At the bottom: primary **"Use this profile"** (tag `use_<id>`).
5. A Categories card: "Categories", "The kinds of blocks: CV, Coding, Gym and your own. Shared by every profile." → `OpenCategories`.

`UseProfile(id)` → `ProfileActions.switchTo(id)`, `markProfileChosen()`, emit `OpenPicker`. The route replaces the back stack with the picker. Wrapped in `FirstVisit(PROFILES)`.

**Developer tools** (`Settings.kt`, debug section): "Show main menu" → opens it (a `MainMenuKey` push), without touching the flag.

**Tests:**
- `NavTest` (or the existing start-destination test): not started + not chosen → `MAIN_MENU`; not started + chosen → `TEMPLATE_PICKER`; running → `HOME` regardless.
- `TemplateStoreTest.existingInstall_countsAsChosen` (prefs with templates, no flag → after `load()` chosen) and `freshInstall_isNotChosen`.
- `MainMenuViewModelTest.useProfile_switchesMarksAndOpensThePicker`.

---

## Slice 4: Done (no release)

1. Full suite (the `cmd /c` form); each slice committed as `task-22b slice N: <title>`.
2. `git push origin task-22b/v3.3.0-menu`. Don't merge, don't touch `master`.
3. A report of at most 10 lines: commits, tests added/changed/deleted, real-name differences. End with **"Track B done."** Then stop.
