# TASK 27: Safe when something fails (3.5.0)

**Target version: 3.5.0** (versionCode 30500). The first version of the architecture overhaul: read `docs/Overhaul.md` (one page) first; it holds Roni's decisions. One agent, `C:/Blocks App`, branch `task-27/v3.5.0`. Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/`, `gradle/` or `docs/`.

**Nothing the user sees changes**, except the new error card (only when something fails) and one line in Settings → Your data (only when something couldn't be read).

## Rules

1. `git checkout -b task-27/v3.5.0` ; `git add docs` ; `git rm docs/Task-26.md docs/AGENT-26.md` ; `git commit -m "task-27 prep: docs"`. Then `task-27 slice N: <title>`.
2. Architecture (`docs/architecture.md` §5) is binding. Verified files stay untouched, **except** the files this task ships: copy them in as they are.
3. **Identical 1:1, all tests green (Roni, binding).** Nothing looks or behaves differently except the error card and the Settings line (both only on failure) and the debug button. Every existing test stays green after every slice. You may change a test's *setup* when a constructor changes, never its expectations. If a test fails, fix the code, not the test.
4. JVM / Robolectric tests only. Add only the tests named here; list every existing test whose setup you changed.
5. **Shipped, verified files** (compiled and tested by the planning agent; paste as they are):
   - `core/system/Failures.kt` and `app/src/test/.../core/system/FailuresTest.kt`
   - `core/model/SegmentTime.kt` and `app/src/test/.../core/model/SegmentTimeTest.kt`
   - `core/schedule/DayTimeline.kt` and its `DayTimelineTest.kt` (one `!!` removed, one test added)
   - `gradle/codeRules.gradle.kts`
   - `designsystem/icons/AppIcons.kt` belongs to 3.5.1 (Task-28) and arrives with this bundle: commit it untouched in slice 1 and don't use it yet.
   - Written for this task, not compiled here (Android): `core/storage/PrefsStorage.kt`, `core/system/Problems.kt`, `designsystem/ErrorCard.kt`. Use them as they are; fix only compile errors.

---

## Slice 1: Failures and safe storage

**`Failures` (shipped).** One place for every failure: `report(where, error)`, `launch(scope, where, onFailure) { }`, `attempt(where) { }`, and `Flow.reportFailures(failures, where, onFailure)`. Release logs and carries on; debug (`loud`) rethrows. Cancellation is never a failure; a `withTimeout` that ran out is.

**`core/di/AppModule.kt`:**
```kotlin
@Provides @Singleton fun failurePolicy(): FailurePolicy = FailurePolicy(loud = BuildConfig.DEBUG)

@Provides @Singleton
fun exceptionHandler(failures: Failures): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, error -> failures.report("app scope", error) }
```
The app scope (`CoroutinesModule`) already uses this handler, so the orchestrator, receivers and `App` follow the policy.

**`PrefsStorage` (shipped):**
- a value that can't be decoded is copied to the new `PrefsFile.UNREADABLE` file, removed from its own file once the copy is on disk, reported, and read as null;
- a failed `commit()` is reported (no more `check(...)` crash);
- `read` now runs on the IO dispatcher;
- `observe` re-reads through `read`;
- `unreadableCount()` and `unreadable()` are new.

**Tests and construction.** Hand-built code that constructs `PrefsStorage` needs a `Failures`.
- Add `app/src/test/.../testing/TestFailures.kt`:
  ```kotlin
  fun testFailures(context: Context, loud: Boolean = true) = Failures(AppLogger(context), FailurePolicy(loud))
  ```
- Tests stay **loud**, so a hidden failure still fails a test.
- In `TemplateManager`'s secondary constructor, use `Failures(AppLogger(context), FailurePolicy(loud = true))`. That constructor goes away in 4.1.0.

**No unknown template crashes.**
- Delete `TemplateStore.get`. `TemplateManager.get(id): Template` becomes `find(id): Template?`.
- Callers:
  - Where `runCatching { templates.get(...) }` was used, use `find(...)`.
  - In `DayOrchestrator`, `StartDay` / `SwapTemplate` with an unknown id: `failures.report("start day: unknown template ${id.key}", IllegalArgumentException())`, then ignore the event (no state change).
  - In `Weekly`: `find(it)?.blocks`.
  - In `DemoDataGenerator`: return early.
- `BlockDto.toSpec`'s `error(...)` stays: it only reads our own `assets/templates.json`, and tests cover that file.

**Settings → Your data:**
- Under "Restore from a backup", when `unreadableCount() > 0`, show the row "**N saved item(s) couldn't be read**", subtitle "Kept aside so nothing is lost. Share it to get it looked at.", with a Share action (`SettingsAction.ShareUnreadable` → `AppRescue.shareUnreadable()`).
- The state gets `unreadable: Int`. Read it when the screen opens and again after a restore.

**Tests:**
- `FailuresTest` (shipped).
- `PrefsStorageTest.unreadable_isSetAside_neverLost` (quiet `Failures`):
  - write `"not json"` raw under `DAY/day_state` through `context.getSharedPreferences("day", 0)`;
  - `read(DAY, "day_state", DayStateSerializer)` → null;
  - the `day` file no longer has the key;
  - `unreadable()` has one entry whose key starts with `day/day_state@` and whose value is `"not json"`;
  - `unreadableCount() == 1`.
- `PrefsStorageTest.loud_setsAsideThenCrashes` (loud):
  - the same read throws;
  - the value is already in `unreadable()`.
- `BackupTest`: the round trip still holds with the new file.

---

## Slice 2: No `!!`

**`SegmentTime.kt` (shipped):** `Segment.actualMinutes`, `plannedMinutes`, `minutesPastPlan`, each null instead of a promise.

Replace every `!!` in `src/main` (22 lines; `git grep -n "!!" -- "app/src/main/*.kt"`):

| Where | Becomes |
|---|---|
| `(x.actualEnd!! - x.actualStart).inWholeMinutes` (Weekly, Insights, History, SegmentBook) | `x.actualMinutes ?: 0` |
| `(x.plannedEnd!! - x.plannedStart!!)` (Weekly, Insights) | `x.plannedMinutes ?: 0` |
| `(actualEnd - segment.plannedEnd!!)` (Insights), `debtMinutes` (HistoryManager) | `segment.minutesPastPlan ?: 0` (keep the existing `coerceAtLeast` / conditions) |
| `DayOrchestrator` (3 places: `dishes.plannedEnd!!`, `open.plannedEnd!!`, `walk.plannedEnd!!`) | a local `val end = x.plannedEnd ?: return ...` or `?.let` |
| `Home.kt` `endedRecord!!` | `endedRecord ?: day.liveRecord()` |
| `DayRecap.kt` `happened.actualEnd!!` | skip a row whose segment is open (`?: continue` / `mapNotNull`) |
| `DemoDataGenerator` `transition(...)!!` | `?: return` (log it) |
| `DayTimeline.kt` | the shipped file |

Behaviour stays the same for valid data; the existing tests prove it.

**Tests:** `SegmentTimeTest` and `DayTimelineTest.aPastBlockWithAnOpenSegment_doesNotCrash` (both shipped).

---

## Slice 3: The error card

**Shipped:**
- `core/system/Problems.kt`: `ProblemAction` (Restart, SendLog) and `AppRescue`, which restarts, shares the log and shares the unreadable values, all from the app context.
- `designsystem/ErrorCard.kt`: the card itself, "Something went wrong" with **Restart app** and **Send log**.

**Every ViewModel**: CategoryList, HistoryDetail, History, Home, Insights, MainMenu, Profile, Profiles, Settings, TemplateEditor, TemplatePicker, Weekly.

1. **Inject** `Failures` and `AppRescue`.
2. **State** gets `val failed: Boolean = false`.
3. **Action** gets `data class Problem(val action: ProblemAction)`, handled by `rescue.handle(action.action)`. If a screen has no sealed Action yet (Weekly, Insights, History), add one holding only `Problem`. Its other public functions move in 4.1.0; leave them.
4. **Every `viewModelScope.launch { }`** becomes:
   ```kotlin
   failures.launch(viewModelScope, "<Screen>: <what>", onFailure = { _state.update { it.copy(failed = true) } }) { ... }
   ```
5. **Every long-lived flow** a ViewModel collects or `stateIn`s gets `.reportFailures(failures, "<Screen>: <what>") { _state.update { it.copy(failed = true) } }` as its last operator before `collect` / `stateIn`.
6. **The screen** shows `ErrorCard(onAction = { onAction(XAction.Problem(it)) })` **in place of its content** when `state.failed`. The top bar, the tabs and the menu stay.
   - On Home, the card replaces the now card and the calendar.
   - On a sheet, the sheet closes and the card shows on the screen.

**Settings:** `shareLog(context)`, `restartApp(context)` and `SettingsEffect.Restart` go. Share log and the restart after a restore call `AppRescue`, so the ViewModel no longer takes a `Context` for these.

**Tests:** `HomeViewModelTest.aFailure_showsTheCard_andHomeKeepsWorking`, with a quiet `Failures`:
- make one Home launch fail, the simplest way the code allows (for example the test clock throwing while a flag is set) → `failed == true`;
- the next action still works (the scope is alive).
- Note in `NOTES.md` which path you used.

---

## Slice 4: Code rules

**`gradle/codeRules.gradle.kts` (shipped).** It fails the build, with file, line and the fix, on:
- `!!`;
- `GlobalScope`;
- `runBlocking`;
- an `@OptIn` / `Experimental*Api` outside `designsystem/`.

It skips comments and strings.

**3.5.0 runs the first three rules only.** The experimental rule turns on in 3.5.1, once the opt-ins are wrapped. Comment it out with one line: `// 3.5.1 turns this on`.

`app/build.gradle.kts`:
```kotlin
apply(from = rootProject.file("gradle/codeRules.gradle.kts"))
tasks.named("preBuild") { dependsOn("codeRules") }
```
(`codeRules` reads `src/main/java` relative to the `app` project.)

Check it once by hand:
1. Add `val x: String? = null; val y = x!!` to any file.
2. `:app:compileDebugKotlin` must fail naming that line.
3. Remove it.

Note the result in `NOTES.md`.

`architecture.md` §14: replace the section with the policy from `docs/Overhaul.md` ("Failures", "Unreadable data", "Error card", "Guard rails") in five lines.

---

## Slice 5: Docs and release 3.5.0

1. `docs/CODEMAP.md`: `Failures`, `AppRescue`, `ProblemAction`, `ErrorCard`, `SegmentTime`, `codeRules`, `PrefsFile.UNREADABLE`. `docs/NOTES.md`: one line per decision you made.
2. `docs/MANUAL_TESTS.md`, "Check on the phone (3.5.0)":
   1. Everything works as in 3.4.3 (a normal day: start, Next, a walk, History, Weekly).
   2. Developer tools → a new debug button **"Break a saved value"** (add it: writes `"not json"` over `day_state`) → the debug build crashes once; open it again → the day starts fresh, and Settings → Your data shows "1 saved item couldn't be read" → Share works.
3. `versionName = "3.5.0"`, `versionCode = 30500`. `CHANGELOG.md`:
   ```markdown
   ## 3.5.0
   - When something fails, the app keeps going and shows "Something went wrong" with Restart and Send log, instead of closing.
   - Saved data that can't be read is kept aside, never overwritten; Settings → Your data can share it.
   ```
4. Final gate, merge into `master`, tag `v3.5.0`, push `master` and the tag. A report of at most 8 lines, then stop.
