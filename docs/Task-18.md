# TASK 18: The foundation for your own templates (3.0.0)

**Target version: 3.0.0** (versionCode 30000). **Nothing visible changes.** The app looks and behaves exactly as 2.7.1. Underneath:
- templates become stored data instead of a fixed list;
- block kinds can be your own categories;
- one built-in profile holds your four templates.

3.1 (the editor) and 3.2 (profiles) build on this. It's a major version because stored data gains new shapes that 2.x can't read. Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/` or `docs/`.

## Slice map

Read lines 1–46 once (decisions, safety, rules), then each slice's lines when you start it.

| Slice | What | Lines | Start in | Tests |
|---|---|---|---|---|
| 1 | TemplateId → string, golden test | 48–82 | core/model/Model.kt (+ compiler errors) | LegacyFormatTest |
| 2 | Categories | 83–144 | core/model/Model.kt, new core/model/Categories.kt, new core/storage/CategoryStore.kt, NotificationModel.kt, Color.kt, CalendarMapper.kt, DayRecap.kt, EditCard.kt, PlanConversion.kt | CategoriesTest |
| 3 | Stored templates, one profile | 145–188 | new core/model/Templates.kt, new core/storage/TemplateStore.kt, TemplateManager.kt, designsystem/TemplateDisplay.kt, feature/templatepicker/TemplatePicker.kt | TemplateStoreTest |
| 4 | Docs and release | 189–205 | docs/, CHANGELOG.md, app/build.gradle.kts | final gate |

## Decisions (Roni, 13 Oct)

| Topic | Decision |
|---|---|
| Built-in categories | CV, Coding, Gym and Free stay forever: they can be renamed and recoloured, never deleted |
| Your own categories | A name, a colour, "counts as work" and Pomodoro on/off. Each block keeps a copy of its category, so a saved day still says what it was after the category changes |
| Pomodoro | On or off per category, always 50/10. Coding starts on, the rest off |
| Kid time | Every template ends with kid time until 22:30; more kid time earlier is fine (Fri/Sat). Kid asleep → Dishes → activity is unchanged |
| Evening activities | Stay the four (Game, Project, Read, Chill) |
| Profiles | 3.0 creates one, "Unemployed", with today's four templates and their weekdays; switching comes in 3.2 |
| History | Old days must keep reading back: no conversion of stored data |

## How it stays safe (read this first)

- **`TemplateId` stays named `TemplateId`** and keeps `TemplateId.MON_THU` and friends. It changes from an `enum` to a `value class` around a String. An enum is saved as its name (`"MON_THU"`) and a String value class is saved as the same text, so **every saved day, plan and state decodes unchanged.** Slice 1 proves it with a golden test written *before* the change.
- **Built-in block kinds keep their classes** (`BlockType.Cv`…). Your own categories are a new subclass, `BlockType.Custom`, so old data never contains it.
- **Templates are seeded from today's `assets/templates.json`** plus today's picker texts and icons, so the first launch of 3.0 has exactly what 2.7.1 had.

## Rules

1. Branch **`task-18/v3.0.0`** from `master`. First `git add docs app ; git rm docs/Task-17.md ; git commit -m "task-18 prep: docs and verified TemplateRules"`, then one commit per slice: **`task-18 slice N: <title>`**. Tag `v3.0.0`.
2. `docs/AGENT.md` has the shell, Gradle and flow rules.
3. *Verified*, don't edit: `core/schedule/TemplateRules.kt` and `TemplateRulesTest.kt`, plus every file named verified before.
4. Add only the tests named here; list every existing test you change. Real names win; note adaptations in `docs/NOTES.md`.

---

## Slice 1: TemplateId becomes a string, and old data still reads

**Step 1, before changing anything:** add `app/src/test/.../core/model/LegacyFormatTest.kt`. With the **current** code, encode these and copy the exact strings into the test as constants:
- `DayState.Running(TemplateId.MON_THU, 2)` with the serializer `DayOrchestrator` persists the state with (`DayStateSerializer`);
- a `DayRecord(date = 2026-10-12, template = TemplateId.SUN_TUE_WED, segments = emptyList())`;
- a `TodayPlan(TemplateId.FRI_SAT, <its template's blocks>)`.

Get the strings the quick way: write `assertEquals("x", encoded)`, run the test once, and read the real value from the failure message. Then assert that each constant **decodes** back to the same object. Commit nothing yet.

**Step 2:** in `core/model/Model.kt` replace the enum with:
```kotlin
/** A template's key (3.0.0). Saved as plain text, exactly like the 2.x enum, so old data decodes unchanged. */
@Serializable
@JvmInline
value class TemplateId(val key: String) {
    companion object {
        val MON_THU = TemplateId("MON_THU")
        val SUN_TUE_WED = TemplateId("SUN_TUE_WED")
        val FRI_SAT = TemplateId("FRI_SAT")
        val OFF = TemplateId("OFF")
        /** The four templates 2.x shipped with, in picker order. */
        val builtIns = listOf(MON_THU, SUN_TUE_WED, FRI_SAT, OFF)
    }
}
```
Then fix what the compiler reports:
- `.name` → `.key`;
- `TemplateId.entries` → `TemplateId.builtIns` (Slice 3 switches these to the store);
- `valueOf(x)` → `TemplateId(x)`;
- an exhaustive `when (id)` gets an `else` branch, which behaves like `OFF` or throws, whichever the surrounding code needs. Note each one.

**Test:** `LegacyFormatTest` (the three decodes) passes after the change, unedited. The full suite must be green.

---

## Slice 2: Categories

**Model** (`core/model/Model.kt`):
```kotlin
/** One of your own block kinds (3.0.0). The block carries a copy, so history keeps what it was. */
@Serializable
data class Custom(val id: String, val name: String, val colorArgb: Long, val work: Boolean, val pomodoro: Boolean) : BlockType
```
Add it inside `sealed interface BlockType`. In `isWork`, `Custom` → `work`.

**Category data** (new `core/model/Categories.kt`):
```kotlin
@Serializable
data class CategoryInfo(val id: String, val name: String, val colorArgb: Long, val work: Boolean, val pomodoro: Boolean, val builtIn: Boolean)

@Serializable
data class CategorySet(val builtIns: List<CategoryInfo>, val customs: List<CategoryInfo> = emptyList()) {
    companion object {
        val DEFAULT = CategorySet(
            listOf(
                CategoryInfo("cv", "CV", 0xFFA99BC9, work = true, pomodoro = false, builtIn = true),
                CategoryInfo("coding", "Coding", 0xFF7A9CC6, work = true, pomodoro = true, builtIn = true),
                CategoryInfo("gym", "Gym", 0xFF8FB58A, work = true, pomodoro = false, builtIn = true),
                CategoryInfo("free", "Free", 0xFFD9C38A, work = false, pomodoro = false, builtIn = true),
            ),
        )
    }
}

/** The categories in use, readable from pure mappers and composables. Defaults until CategoryStore loads. */
object Categories {
    @Volatile var current: CategorySet = CategorySet.DEFAULT
    fun infoFor(type: BlockType): CategoryInfo? = when (type) {
        BlockType.Cv -> current.builtIns.first { it.id == "cv" }
        BlockType.Coding -> current.builtIns.first { it.id == "coding" }
        BlockType.Gym -> current.builtIns.first { it.id == "gym" }
        BlockType.Free -> current.builtIns.first { it.id == "free" }
        is BlockType.Custom -> CategoryInfo(type.id, type.name, type.colorArgb, type.work, type.pomodoro, builtIn = false)
        else -> null                                                  // kid time, Dishes, evening, Walk: not categories
    }
}
```

**`CategoryStore`** (new, `core/storage/CategoryStore.kt`, a Hilt singleton):
- `load()` reads `CategorySet` from `PrefsFile.SETTINGS` key `categories` (missing → `DEFAULT`) and sets `Categories.current`. Call it in `DayOrchestrator.ensureRestored()` before anything maps blocks.
- `save(set)` writes it and sets `Categories.current`.
- No UI in 3.0.

**One source for names, colours and Pomodoro.** Each of these consults `Categories.infoFor(type)` first and keeps its old branches for the non-category types:
- `blockName` (`NotificationModel.kt`), `blockTitle` (`CalendarMapper.kt`), `recapTitle` (`DayRecap.kt`), `typeKey` (`EditCard.kt`): use `info.name`.
- `blockColors` (`designsystem/Color.kt`):
  - when `info.colorArgb` equals the default for that built-in, return today's exact `BlockColors`, so nothing changes;
  - otherwise `BlockColors(container = lerp(Color(info.colorArgb), Background, 0.78f), accent = Color(info.colorArgb))`.
- `PlanConversion.toBlockSpec`: `pomodoro = if (Categories.infoFor(type)?.pomodoro == true) original?.pomodoro ?: Pomodoro() else null`, instead of `type == BlockType.Coding`.

**Tests** (`CategoriesTest`):
- `defaults_matchTodaysNamesAndColours`: the four names, and `blockColors(BlockType.Coding)` equal to today's pair.
- `renamingCoding_changesItEverywhere`: set `current` with Coding renamed "Deep work" → `blockName`, `blockTitle` and the recap title say "Deep work". Reset `current` in `@After`.
- `custom_isWorkAndKeepsItsOwnName`: `Custom("office", "Office", 0xFF6FA8DC, work = true, pomodoro = false)`: `isWork` is true, `blockName` is "Office", and `isEditable()` is true.

---

## Slice 3: Stored templates and one profile

**Model** (`core/model/Templates.kt`, new):
```kotlin
@Serializable
data class StoredTemplate(
    val id: TemplateId,
    val title: String,                 // "Monday / Thursday"
    val subtitle: String,              // "Gym day"
    val short: String,                 // "MON · THU"
    val iconKey: String,               // "fitness" | "code" | "weekend" | "games"
    val accentArgb: Long,
    val weekdays: Set<Int>,            // ISO day numbers it's suggested on (Mon = 1 … Sun = 7); plain Ints serialize safely
    val blocks: List<BlockSpec>,
)

@Serializable
data class Profile(val id: String, val name: String, val templateIds: List<TemplateId>)
```

**`TemplateStore`** (new, `core/storage/TemplateStore.kt`, Hilt singleton):
- **Seed:**
  - the four templates from `assets/templates.json` (today's parsing, moved here from `TemplateManager`);
  - the texts, icon key and accent from today's `TemplateDisplay` map;
  - weekdays: MON_THU = {1, 4}; SUN_TUE_WED = {7, 2, 3}; FRI_SAT = {5, 6}; OFF = none.
  - Profile `Profile("unemployed", "Unemployed", TemplateId.builtIns)`.
- **Storage:** `PrefsFile.SETTINGS` keys `templates` (a list), `profiles` (a list) and `active_profile` (a String). On the first 3.0 launch nothing is stored, so the seed is written.
- **In memory:** after `load()` (called in `ensureRestored()`, next to `CategoryStore.load()`), everything is synchronous: `all()` (the active profile's templates, in its order), `get(id)` and `suggestedFor(day: DayOfWeek): TemplateId?` (the first template of the active profile whose `weekdays` contain `day.isoDayNumber`). Before `load()`, the getters return the seed, parsed synchronously from assets as today, so nothing ever blocks.
- **Validation on load:** for every template except `OFF`, `TemplateRules.problems(blocks.toPlanBlocks())`. A template with problems is replaced by its seed, and the replacement is logged.

**Wire it in:**
- `TemplateManager` becomes a thin façade over `TemplateStore`, so `get`/`all` keep their call sites. `Template(id, name, blocks)` is built from `StoredTemplate`.
- `templateDisplay(id)` reads the store: the title, subtitle and short text; the icon from `iconKey` (`fitness`→`FitnessCenter`, `code`→`Code`, `weekend`→`Weekend`, `games`→`SportsEsports`, anything else → `Event`); the accent from `accentArgb`.
- The picker's `todayTemplate(dayOfWeek)` → `templateStore.suggestedFor(dayOfWeek)`. Its cards come from `templateStore.all()` (in the same order as today).
- `DemoDataGenerator.templateFor` → the same lookup.

**Tests** (`TemplateStoreTest`, Robolectric like the other prefs tests):
- `seed_isExactlyTheShippedTemplates`: each template's blocks equal what 2.7.1's `TemplateManager` parsed (ids, types, times, Pomodoro, quiet), and the titles equal today's `TemplateDisplay`.
- `suggestedFor_followsTheWeekdays`: Monday → MON_THU, Tuesday → SUN_TUE_WED, Saturday → FRI_SAT.
- `brokenStoredTemplate_fallsBackToItsSeed`: store MON_THU with overlapping blocks, `load()` → `get(MON_THU)` equals the seed.
- `restart_keepsWhatWasStored`: save a renamed title, make a new store on the same prefs, `load()` → the renamed title.

---

## Slice 4: Docs and release 3.0.0

1. `docs/CODEMAP.md`: `Categories.kt`, `CategoryStore`, `Templates.kt`, `TemplateStore`, `TemplateRules`, and the `TemplateId` change. `docs/NOTES.md`: one line per decision, plus the list of exhaustive `when`s that got an `else`.
2. `docs/MANUAL_TESTS.md`, "Check on the phone (3.0.0)". **Install over 2.7.1, don't uninstall:**
   1. Today's day continues exactly where it was (same block, same timer).
   2. History shows every past day as before; Weekly and Insights show the same numbers.
   3. The picker shows the same four cards, texts, icons and "Suggested" day.
   4. Coding still has its Pomodoro; colours are unchanged.
   5. Kill the app and reopen it: all of the above again.
3. `versionName = "3.0.0"`, `versionCode = 30000`. `CHANGELOG.md`:
   ```markdown
   ## 3.0.0
   - Under the hood: templates are now stored data, block kinds can be your own categories, and your four templates live in a profile ("Unemployed"). Nothing looks different yet; the template editor (3.1) and profiles (3.2) build on this.
   - Data saved by 3.0 can't be opened by 2.x.
   ```
4. Final gate (the `cmd /c` form), merge `task-18/v3.0.0` into `master`, `git tag v3.0.0`, push. A report of at most 15 lines, then stop.
