# TASK 19: Your own templates and categories (3.1.0)

**Target version: 3.1.0** (versionCode 30100). Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/` or `docs/`.

This task runs as **four parts, by up to three agents**. Each prompt names its part.

| Part | Who, where | Branch | File | Lines |
|---|---|---|---|---|
| **Base** | one agent, `C:/Blocks App` | `task-19/v3.1.0` | this file | 1–38, then 40–145 |
| **Track A**: categories | its own agent, `C:/Blocks App 19a` | `task-19a/v3.1.0-categories` | `docs/Task-19a.md` | this file 1–38 first |
| **Track B**: template editor | its own agent, `C:/Blocks App 19b` | `task-19b/v3.1.0-editor` | `docs/Task-19b.md` | this file 1–38 first |
| **Finish** | one agent, `C:/Blocks App`, after A and B | `task-19/v3.1.0` | this file | 1–38, then 147–end |

Base does all the data work and the empty Templates tab, merges into `master`, and creates the two worktrees. A and B then only add screens and ViewModels in **separate files**, so they run at the same time without conflicts. Finish merges both and releases.

## Decisions (Roni, 13 Oct)

| Topic | Decision |
|---|---|
| Where | A new **Templates** tab in the bottom bar (Home · Weekly · Insights · Templates), with two sections: templates and categories |
| Editor | **List + sheet**: the template's blocks as a list with times, a small preview strip on top; tap a row → a sheet with category, start, length, Delete, Save |
| Overlaps | **Push later blocks**: free time is used first, then later blocks shift later and keep their length; kid time at the end gets shorter, and if it would drop under 10 min the change is refused. Blocks *before* the edited one never move: a start inside one is refused with a reason |
| Templates | **Start from blank** (only kid time 17:00–22:30) and **delete your own**. Built-ins: blocks read-only (no duplicate, no reset needed) |
| Weekdays | Every template, built-ins too, can claim weekdays for the picker's "Suggested". A day belongs to one template: claiming it moves it from the other |
| Categories | Built-ins (CV, Coding, Gym, Free): rename, recolour, Pomodoro on/off; never deleted. Your own: also "counts as work" and Delete (their template blocks become free time) |
| Colours | A **12-colour palette** (`CategoryPalette`), no free picker |
| Editing today's template | **Today follows too, if the day hasn't started** (still waiting for its first block). A started day keeps its plan |
| Today's plan on Home | Not in 3.1. 3.2.0 (Task-20) gives Home the same sheet: Add after / Delete on any block |

## Rules (all parts)

1. Branch, commit and worktree names as in the table. Commits: `task-19 slice N: <title>`, `task-19a slice N: …`, `task-19b slice N: …`.
2. *Verified*, don't edit: `core/schedule/TemplateEditor.kt` (new), `core/schedule/TemplateRules.kt` (3.1: free time may be shorter than 10 min) and their tests, plus every file named verified before.
3. Add only the tests named here; list any existing test you change.
4. Real names win: if a name here doesn't exist, use the real one and note it in `docs/NOTES.md` (Tracks: in your report instead; Finish writes NOTES).
5. Phone: only `com.roni.blocks.debug`.

---

## Base, slice 1: The data layer

**Prep:** `git checkout -b task-19/v3.1.0` ; `git add docs app` ; `git rm docs/Task-18.md` ; `git commit -m "task-19 prep: docs and verified TemplateEditor"`.

**`core/model/Templates.kt`:**
- `StoredTemplate` gets `val archived: Boolean = false` (last field; old JSON decodes with the default).
- Pure helpers next to it:
  ```kotlin
  /** "SUN · TUE · WED": ISO days, Sunday first; no days → "NO DAYS". */
  fun shortFor(days: Set<Int>): String =
      if (days.isEmpty()) "NO DAYS" else days.sortedBy { it % 7 }.joinToString(" · ") { listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")[it - 1] }
  /** "6h 30m work", "No work" — the subtitle of your own templates (built-ins keep theirs). */
  fun subtitleFor(blocks: List<BlockSpec>): String
  /** The colour of the work category with the most minutes; kid-time grey 0xFF9C968C if none. */
  fun accentFor(blocks: List<BlockSpec>): Long
  ```
- `val OWN_TEMPLATE_ICONS = listOf("event", "code", "fitness", "weekend", "games", "work", "school", "home")`. In `designsystem/TemplateDisplay.kt` `iconFor` add `"work" → Icons.Outlined.Work`, `"school" → Icons.Outlined.School`, `"home" → Icons.Outlined.Home`.

**`core/model/Categories.kt`:**
```kotlin
/** The colours a category can take (3.1.0): the four built-ins first, then eight that read well on the dark calendar. */
val CategoryPalette: List<Long> = listOf(
    0xFFA99BC9, 0xFF7A9CC6, 0xFF8FB58A, 0xFFD9C38A,
    0xFFE08A7A, 0xFFE3A35F, 0xFFC2C46A, 0xFF6FBFAF,
    0xFF6FB7D6, 0xFF8A92E0, 0xFFD58FC4, 0xFFA89A88,
)
```

**`core/storage/TemplateStore.kt`:**
- `fun find(id: TemplateId): StoredTemplate? = stored.firstOrNull { it.id == id } ?: seed.firstOrNull { it.id == id }`. `get(id)` = `find(id) ?: error("Unknown template ${id.key}")`. `templateDisplay` uses `find`. (Today `get` crashes on any id that isn't a seed.)
- `validated()`: a broken template that has a seed falls back to it (as now); one without a seed (your own) is kept with `archived = true` and logged, never a crash.
- `fun isBuiltIn(id) = id in TemplateId.builtIns`.
- Writes; each persists what it changed (`KEY_TEMPLATES`, and `KEY_PROFILES` when the profile changes) and returns the result:
  - `suspend fun create(title: String): StoredTemplate`: key `user_<n>` (1 + the highest n in `stored`, archived included), `blocks = TemplateEditor.blank(key).toBlockSpecs(emptyList())`, `weekdays = emptySet()`, `short = shortFor(…)`, `subtitle = subtitleFor(…)`, `iconKey = "event"`, `accentArgb = accentFor(…)`. Its id goes into the active profile **before `OFF`**.
  - `suspend fun save(template: StoredTemplate): Boolean`: false for built-ins or when `TemplateRules.problems` isn't empty. Recomputes `subtitle` and `accentArgb` from the blocks.
  - `suspend fun delete(id: TemplateId): Boolean`: false for built-ins. Sets `archived = true` and removes the id from every profile. It stays in `stored`, so History's titles still resolve.
  - `suspend fun setWeekdays(id: TemplateId, days: Set<Int>)`: works on built-ins too. Removes `days` from every other template, sets them here, recomputes `short` for every template it changed.
  - `suspend fun replaceCategory(categoryId: String, info: CategoryInfo?)`: in every stored template, a block whose type is `Custom` with that id takes `BlockType.Custom(info.id, info.name, info.colorArgb, info.work, info.pomodoro)`; with `info == null` it becomes `BlockType.Free`, and the blocks go through `TemplateRules.normalize` (via `toPlanBlocks()` / `toBlockSpecs(originals)`).

**`core/storage/CategoryStore.kt`:**
- `suspend fun update(info: CategoryInfo): CategoryResult`: name trimmed, 1–16 characters, not used by another category (case-insensitive). A built-in keeps its `work` flag and `builtIn`.
- `suspend fun add(name: String, colorArgb: Long, work: Boolean, pomodoro: Boolean): CategoryResult`: id `cat_<n>`.
- `suspend fun delete(id: String): CategoryResult`: built-ins refused.
- `sealed interface CategoryResult { data class Saved(val info: CategoryInfo?) ; data object EmptyName ; data object NameTaken ; data object TooLong ; data object BuiltIn }`.

**Two use-case classes** (`core/storage/`, `@Singleton`, Hilt-injected), the only way the screens write:
```kotlin
class TemplateActions @Inject constructor(private val store: TemplateStore, private val day: DayOrchestrator) {
    suspend fun create(title: String): StoredTemplate = store.create(title)
    suspend fun save(t: StoredTemplate): Boolean = store.save(t).also { if (it) day.onTemplateSaved(t.id) }
    suspend fun delete(id: TemplateId): Boolean = store.delete(id)
    suspend fun setWeekdays(id: TemplateId, days: Set<Int>) = store.setWeekdays(id, days)
}
class CategoryActions @Inject constructor(private val categories: CategoryStore, private val templates: TemplateStore, private val day: DayOrchestrator) {
    // update / add / delete: CategoryStore first; on Saved for a custom category also
    // templates.replaceCategory(id, info or null) and day.onCategoryChanged(id, info or null).
}
```

**`DayOrchestrator`:**
- `suspend fun onTemplateSaved(id: TemplateId)`: only while **waiting** (`waitingStart(now, today) != null`), when `todayPlan.templateId == id` and the plan isn't `edited`: today's plan takes the stored blocks; then exactly what `applyOneMissedTransition` does for a waiting day if the new first block's start is already due, otherwise `startsAt` and the alarm move to the new first start (`sideEffects()`, no transition). Persist. Anything else: nothing.
- `suspend fun onCategoryChanged(categoryId: String, info: CategoryInfo?)`: today's plan blocks of that custom category take the new copy (`info == null`: `BlockType.Free`, ids and times unchanged); the open segment of that category takes the new copy too when `info != null`. Closed segments and History keep theirs. Persist and re-render.

**Test helper** for the tracks: `app/src/test/java/com/roni/blocks/testing/TestActions.kt` with `fun testActions(context): Pair<TemplateActions, CategoryActions>` on real Robolectric stores and an orchestrator built the way `DayOrchestratorTest` builds one (fake clock, the store passed in).

**Tests:**
- `TemplateStoreTest`:
  - `create_isBlank_andListedBeforeOff`: `create("Office day")` → key `user_1`; blocks = kid time 17:00–22:30; `all().map { it.id.key }` == `[MON_THU, SUN_TUE_WED, FRI_SAT, user_1, OFF]`; a fresh store after `load()` sees the same.
  - `delete_hidesIt_butHistoryStillFindsIt`: create, delete → not in `all()`, `find(id)!!.title == "Office day"`; `delete(MON_THU)` → false.
  - `setWeekdays_movesTheDayFromAnotherTemplate`: `setWeekdays(user_1, setOf(1))` → `find(MON_THU)!!.weekdays == setOf(4)`, its `short == "THU"`, `suggestedFor(MONDAY) == user_1`.
  - `replaceCategory_renamesOrFrees`: a saved own template with a `Custom("cat_1", "Office", …)` block 09:00–17:00; rename → the block's type name is the new name; delete (`null`) → the template is free 09:00–17:00 then kid time 17:00–22:30 (merged, valid).
  - `aBrokenOwnTemplate_isArchived_notACrash`: write a `user_1` with overlapping blocks straight to prefs, `load()` → no exception, not in `all()`.
- `CategoryStoreTest` (new, Robolectric like `TemplateStoreTest`): `add_update_delete_followTheRules`: add "Office" → `cat_1`; add "office" → `NameTaken`; update CV to "CV sending" keeps `work = true`; `delete("cv")` → `BuiltIn`; add "" → `EmptyName`.
- `DayOrchestratorTest`:
  - `ownTemplateSaved_whileWaiting_todayFollows`: an own template with Coding 10:00–12:00, `StartDay` at 07:00 → waiting, `startsAt` 10:00. Save it with Coding 08:00–10:00 → `startsAt` 08:00, next alarm 08:00.
  - `ownTemplateSaved_afterTheDayStarted_todayKeepsItsPlan`: the same, but saved at 10:30 (Coding running) → today's plan unchanged.
  - `customCategoryRenamed_todaysPlanFollows`: today's plan holds a `cat_1` block; `onCategoryChanged("cat_1", renamed)` → the plan block's type has the new name.

---

## Base, slice 2: The Templates tab (read-only), then hand over

**Tab:** `AppTab.TEMPLATES` with `Icons.Outlined.ViewAgenda`, label "Templates", after Insights. `navigation/AppNav.kt`: `@Serializable data object TemplatesKey : NavKey`, `entry<TemplatesKey> { TemplatesRoute(onTab = ::openTab, onMenu = { backStack.add(it) }, onOpenTemplate = {}, onNewTemplate = {}) }`, and `tabKey` maps it (every `when (AppTab)` gets the branch).

**Files**, so the two tracks never edit the same file:

| File | Holds | Who edits it later |
|---|---|---|
| `feature/templates/TemplatesScreen.kt` | `TemplatesRoute` + the scaffold (copy Weekly's: top bar with History/Settings, bottom bar), a `LazyColumn` with `TemplateSection(onOpenTemplate, onNewTemplate)` then `CategorySection()` | nobody |
| `feature/templates/TemplateSection.kt` | `TemplateListViewModel` + `TemplateSection` | Track B |
| `feature/templates/CategorySection.kt` | `CategoryListViewModel` + `CategorySection` | Track A |

**Template section** (title "Templates"): one card per `TemplateStore.all()`, in order: icon in its accent, title, `short`, and the picker's strip (`stripFor`, make it `internal` in a shared place if needed). Built-ins show a small "Built-in" label. Tapping a card calls `onOpenTemplate(id)` (does nothing yet). No buttons yet.

**Category section** (title "Categories"): one row per category (built-ins, then yours): a 12 dp dot in its colour, the name, and muted labels "Work" and "Pomodoro" when on. Not clickable yet.

**Test:** `TemplatesScreenTest` (Robolectric Compose, like `ComposeSmokeTest`): `templatesTab_listsTheFourTemplatesAndTheFourCategories`.

**Hand over:**
1. Full suite, commit `task-19 slice 2: The Templates tab`.
2. `git checkout master ; git merge --ff-only task-19/v3.1.0 ; git push origin master task-19/v3.1.0` (no tag).
3. `git worktree add "C:/Blocks App 19a" -b task-19a/v3.1.0-categories master ; git worktree add "C:/Blocks App 19b" -b task-19b/v3.1.0-editor master`.
4. `Copy-Item "C:/Blocks App/local.properties" "C:/Blocks App 19a/" ; Copy-Item "C:/Blocks App/local.properties" "C:/Blocks App 19b/"` (plus `keystore.properties` if it exists).
5. Report in at most 8 lines, ending with: **"Base done. Start Track A and Track B."** Then stop.

---

## Finish, slice 3: Merge, docs, release 3.1.0

Run only when both tracks reported done (`git log --oneline master..task-19a/v3.1.0-categories` and `…-editor` both list their slices).

1. `git checkout task-19/v3.1.0 ; git merge --ff-only master ; git merge --no-ff task-19a/v3.1.0-categories ; git merge --no-ff task-19b/v3.1.0-editor`. A conflict can only be in `docs/` or a shared import: keep both sides. Full suite.
2. `docs/CODEMAP.md`: the Templates tab files, `TemplateEditor`, `TemplateActions`, `CategoryActions`, `CategoryPalette`. `docs/NOTES.md`: one line per decision above, plus the tracks' "real names" notes from their reports.
3. `docs/MANUAL_TESTS.md`, "Check on the phone (3.1.0)":
   1. Templates tab: the four templates and the four categories.
   2. New category "Office", a palette colour, Work on, Pomodoro off.
   3. New template "Office day": add Office 09:00, length 8h → kid time still 17:00–22:30; claim Monday → Mon/Thu shows THU.
   4. Make Office 8h 30m → "Moves later" mentions kid time from 17:30. Start Office at 08:55 when a block ends at 09:00 → the reason shows and Save is off.
   5. Picker on a Monday: "Office day" is Suggested.
   6. Rename CV, recolour Gym: Home, History and Weekly follow.
   7. Delete "Office": Office day becomes free time until kid time.
   8. Pick Office day before its first block, edit it in Templates: Home's "Day starts at" follows.
   9. Delete Office day: History of a day that used it still shows its name.
4. `versionName = "3.1.0"`, `versionCode = 30100`. `CHANGELOG.md`:
   ```markdown
   ## 3.1.0
   - A Templates tab: your templates and categories in one place.
   - Make your own templates from blank: add blocks, set their start and length; later blocks move along, and kid time at the end gets shorter.
   - Pick which days suggest which template.
   - Your own categories with 12 colours, "counts as work" and Pomodoro; built-ins can be renamed and recoloured.
   ```
5. Final gate, `git checkout master ; git merge --ff-only task-19/v3.1.0 ; git tag v3.1.0 ; git push origin master v3.1.0`. Then `git worktree remove --force "C:/Blocks App 19a" ; git worktree remove --force "C:/Blocks App 19b"`.
6. A report of at most 15 lines (every slice of every part, the tests changed), then stop.
