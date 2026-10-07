# TASK 6 FIXES: make 1.5.0 stable

**For a fresh working-agent session.** A previous session started this task and ran out of context partway through. Nobody knows exactly how far it got, so **Slice 0 finds out** before anything else.

**Target version: 1.5.1** (patch: fixes only, no behavior change).

## What to read

- `SUMMARY.md` §1–§6 and §13–§15 (the project, roles, product rules, architecture, glossary, versioning, testing). Skip the rest.
- `architecture.md` §4 (day state machine), §6 (DI), §11 (reboot and time change), §14 (errors). Skip the rest.
- `Task-6.md`: only the slices this file points to.
- `CODEMAP.md` as your index of the code. `NOTES.md` once.

**Save context.** This task is long and the last session died. Search for symbols instead of reading whole files; read only the function you need. Never print a full test log: the gate below prints only the tail. Keep notes short.

## Background: what's wrong with 1.5.0

1.5.0 (Task-6) is merged and tagged, but the planning agent's review found:

- **The unit suite is flaky.** About 180 tests; each full run fails 0–3 different tests. Seen failing: `SettingsViewModelTest.setHourFormat_persistsAndFlipsTheFormatter` (`expected:<false> but was:<null>`), `SettingsViewModelTest.settingsState_isLoadingUntilTheLoadCompletes`, `TemplatePickerTest.screen_rendersFourTaggedCardsWithOneSuggested`, `DayOrchestratorRestoreTest.todaysRunningState_isRestoredAsRunning`, `DayOrchestratorRestoreTest.legacyInterrupted_restoresAsRunning`. All pass alone. The Task-6 agent's diagnosis: a coroutine started on a real dispatcher outlives its test and fails the next test class with `UncaughtExceptionsBeforeTest` (suppressed `UnsupportedOperationException: Dispatchers.Unconfined.dispatch…`, from a coroutine on `Dispatchers.IO`).
- **"`DayOrchestratorRestoreTest` remains excluded on instruction"**, says its `issues.md`. Roni gave no such instruction. Every test must run.
- **Restore is where the splash bug lived** (Task-6 Slice 1), so flaky restore tests may hide a real race.
- **`onTimeTravelled()`:** Task-6 Slice 8 added it to `DayOrchestrator` because `onTimeChanged()` never replays missed transitions. That suggests real clock and timezone changes don't replay either, which `architecture.md` §11 requires.
- **Named tests from Task-6 weren't reported by name** (Slice 6 below).
- Housekeeping: a leftover `stash@{0}` ("slice2b-wip"); no git remote.
- Two names in `Task-6.md` Slice 9 don't exist in the code (`TimelineQueries` is a file of extension functions, there's no `InsightsManager`). That's a doc error, already known; nothing to do here.

## Rules

1. One slice = at most ~3 files, the gate, one commit `task6-fixes slice N: <title>`. Never start a slice on a red build. **Commit after every slice**, so the work survives if this session dies too.
2. Foreground only. No sub-agents, no background jobs.
3. Don't weaken any assertion to make a test pass. Fix the code, or the test's setup and teardown, never its expectations.
4. Strings stay inline as they are. Roni's decision: they move to `strings.xml` in a later task, not this one.
5. Don't refactor beyond what a slice asks.
6. Don't ask Roni questions. Unclear → simplest option, one line in `NOTES.md`.
7. **If your context is running low:** finish or stash the current slice, commit, and end with a five-line handoff: done slices, the next slice, anything half-done. Don't start a slice you can't finish.

## Gradle

The JDK is set in `C:\Users\ronib\.gradle\gradle.properties` (`org.gradle.java.home`). Don't change JDKs or `JAVA_HOME`. `app/build.gradle.kts` has a 4-minute test timeout and `testLogging` for started/passed/failed tests; keep both.

**The gate** (compile + unit tests). Run it exactly like this, never inside `Start-Job`:

```powershell
$p = Start-Process -FilePath ".\gradlew.bat" `
  -ArgumentList ":app:compileDebugKotlin :app:testDebugUnitTest --console=plain" `
  -RedirectStandardOutput gate.log -RedirectStandardError gate.err -NoNewWindow -PassThru
if (-not $p.WaitForExit(600000)) { taskkill /T /F /PID $p.Id; .\gradlew --stop; "GATE HUNG" }
Get-Content gate.log -Tail 15
Select-String -Path gate.log, gate.err -Pattern "What went wrong|FAILED|tests completed" -Context 0,8
```

- While working on one class, run only it: put `--tests "<fully.qualified.Class>"` in the argument list. Run the full gate once before each commit.
- A full gate takes 3–5 minutes. Warnings about Conscrypt, restricted methods or native access are harmless.
- `BUILD SUCCESSFUL` = commit and move on. No re-runs (except Slice 2's acceptance), no XML parsing.
- **Stuck ladder**, one rung at a time, never the same rung twice: (1) a test FAILED → read the error, fix, gate once. (2) GATE HUNG or a lock/daemon error → `.\gradlew --stop`, kill leftover `java` processes, gate once. (3) Hung again → find the last `STARTED` test in `gate.log` with no PASSED/FAILED after it, and fix it. (4) Gate with `--no-daemon --no-configuration-cache`. (5) `.\gradlew clean`, gate once. (6) Stop and report the last 40 lines of `gate.log` and `gate.err`.
- If a command gives no new information for 10 minutes, it's stuck: kill it and take the next rung.

---

## Slice 0: Find out what the previous session did (no code)

1. `.\gradlew --stop` (the last session may have died mid-build).
2. Run and read: `git status`, `git branch --list`, `git tag`, `git stash list`, `git remote -v`, `git log --oneline -15 --all`.
3. Decide where to start:
   - **`v1.5.1` already exists:** the task may be finished. Check that each slice below is in the history, report, and stop.
   - **Branch `task6-fixes` exists:** check it out. For each commit `task6-fixes slice N`, read its diff (`git show --stat`, then only the relevant hunks) and check it against that slice below. Done and correct → keep it. Wrong or partial → note it; you'll redo it.
   - **No branch:** create `task6-fixes` from `master` (must be at `v1.5.0`).
4. **Uncommitted changes** (`git status` not clean, ignoring `.idea/` and logs): the previous session's half-finished slice.
   - If `git diff --stat` shows they clearly belong to one slice, run the gate. Green → finish that slice and commit it. Red → `git stash push -m "task6-fixes-leftover"` and redo the slice from scratch.
   - If you can't tell which slice they belong to: stash them the same way.
5. Report a table: slice, state (done / redo / not started), commit. Then continue from the first slice that isn't done.

---

## Slice 1: Two answers (and one fix)

1. **How is `DayOrchestratorRestoreTest` excluded?** Search for `@Ignore`, `exclude`, `filter` and the class name in `app/build.gradle.kts` and the test sources. If it's excluded anywhere, remove the exclusion and commit. Report what you found, even if it's "not excluded".
2. **Assertions changed in Task-6.** List the test files Task-6 changed: `git diff v1.4.0 v1.5.0 --stat -- app/src/test`. For each, show only changed assertion lines: `git diff v1.4.0 v1.5.0 -- <file> | Select-String '^[-+].*(assert|expect|should)'`. Report each changed assertion (old, new) and whether it got weaker. Restore weakened ones in the slice that fixes their cause.

---

## Slice 2: A deterministic unit suite

**Fix the cause:** the test must own every coroutine the code under test starts.

1. Find every place in `app/src/main` that picks its own dispatcher or scope: search for `Dispatchers.IO`, `Dispatchers.Default`, `Dispatchers.Main`, `CoroutineScope(`, `SupervisorJob(`, `GlobalScope`. List them in the report.
2. Inject them instead. If the project doesn't have these yet, add them in `core/di/`:

```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module @InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton @ApplicationScope
    fun applicationScope(@IoDispatcher io: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + io)
}
```

   Each class that used `Dispatchers.IO` or built its own scope takes `@IoDispatcher` / `@ApplicationScope` through its constructor instead. Change nothing else in those classes.
3. Tests pass their own dispatcher and scope:

```kotlin
@Test fun example() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val orchestrator = DayOrchestrator(/* … */, io = dispatcher, appScope = backgroundScope)
    // runTest cancels backgroundScope when the test ends, so nothing outlives it
}
```

   Keep `Dispatchers.setMain` / `resetMain` only for ViewModel tests (`viewModelScope` runs on Main).
4. Use as many slices as it needs (2a, 2b, …), at most ~3 production files each.

**Acceptance (the one allowed repeat):** after the last 2x slice, run the full gate **three times in a row**. All three green with the same test count. Report the three counts. If a run fails, the slice isn't done: the `CoroutineId` in the stack trace names the dispatcher, and the test that started the coroutine ran just before the failing class.

---

## Slice 3: Settings saves before it says so

`setHourFormat` flips the in-memory flag in `viewModelScope.launch` before the write lands, so a reader right after can see the old value. Check `settingsState_isLoadingUntilTheLoadCompletes` too.

- Fix in the code: update the state **after** the write completes (`PrefsStorage.write()` suspends until `commit()` is done). Same pattern for any other Settings action that writes, then updates state.
- Both tests pass unchanged (Rule 3).

---

## Slice 4: Is the restore flakiness a real race?

1. After Slice 2, run `DayOrchestratorRestoreTest` alone 5 times (`--tests`). Report the results.
2. Look for a production race:
   - something writes a state to storage before restore has read it: an `init` block, a state collector, the date-rollover check;
   - two entry points restore at once (`Application.onCreate` and a receiver) and the second doesn't wait for the first;
   - `send()` applies an event before `ensureRestored()` has returned.
3. Write the cause in one or two sentences: "test-only: …" or "production: …". If it's production, fix it.
4. Add this test whatever the cause, in `DayOrchestratorProcessDeathTest`:
   - `concurrentEntryPoints_restoreOnce_keepSavedDay`: saved Running on `sun_tue_wed.coding_am`, clock 10:00. On a fresh orchestrator, launch `ensureRestored()` from three coroutines and `send(Pause)` from a fourth, at the same time. Assert: state is `Paused` on `sun_tue_wed.coding_am`; restore ran exactly once (count reads in the fake storage, or restore log lines); storage still holds today's segments.

---

## Slice 5: A real clock change replays like a restart

`architecture.md` §11: `SystemEventsReceiver` replays missed transitions on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET` and `TIMEZONE_CHANGED`.

- Report what the receiver calls for each of the four.
- Any that doesn't replay → route it to the replay path `onTimeTravelled()` uses. One replay path for restore, time travel and system events; don't write a second one.
- Test `timeSet_jumpForward_replaysMissedTransitions`: Running on `sun_tue_wed.cv` at 09:10; the clock jumps to 16:30; the time-set path runs → state `KidTime`. Put it in the existing orchestrator or receiver test class.
- If everything already replays, say so, add the test anyway, and move on.

---

## Slice 6: The named tests from Task-6

For each, confirm it exists **with this exact name** and passes, or add it now exactly as the `Task-6.md` slice specifies.

| Test | Spec |
|---|---|
| `swapAfterEmergency_restartsFromNow_keepsEarlierSegments` | Task-6 Slice 2 |
| `swapMidDay_replansFromNow` | Task-6 Slice 2 |
| `swapAfter2230_isRefused` | Task-6 Slice 2 |
| `homeTitle_afterEmergency_isTheTemplateTitle` | Task-6 Slice 2 |
| `insightsHeadline_countsBlocks_notDays` | Task-6 Slice 6 |
| `SettingsViewModelTest`: demo load success → "Demo data loaded: 28 days"; failure → "Couldn't load demo data. Details are in the log." | Task-6 Slice 7 |

Find them with one search for each name. If one exists under another name, rename it. Report each: existed / renamed / added.

---

## Slice 7: Housekeeping

- `git stash show stash@{0}` (check its name first: drop only `slice2b-wip`, never a `task6-fixes-leftover` from Slice 0). If its changes are already on `master`, `git stash drop` it. If not, keep it and list what's missing.
- `.gitignore`: add `.idea/`, `gate.log`, `gate.err`, `*.log` if missing.
- `issues.md`: remove the item about the two missing names (`TimelineQueries`, `InsightsManager`). Remove the flaky-suite items if Slices 2–4 fixed them. Keep the device-test item.
- `NOTES.md`: one line each for the injected dispatcher/scope pattern and the single replay path.
- `CODEMAP.md`: rows for any new file (e.g. `CoroutinesModule.kt`).

---

## Slice 8: Release 1.5.1

1. `versionName = "1.5.1"`, `versionCode = 10501` in `app/build.gradle.kts`.
2. `CHANGELOG.md`, new entry on top. Keep only the lines that are true:

```markdown
## 1.5.1
- Unit tests are deterministic: every coroutine the code starts belongs to the test that started it.
- Settings saves a change before showing it.
- Fixed: a race when several parts of the app restore the day at once.        (only if Slice 4 found a production race)
- Fixed: a clock or timezone change now catches up on missed blocks, like a restart.   (only if Slice 5 changed code)
```

3. `MANUAL_TESTS.md`: rename the heading "Check on the phone (1.5.0)" to "Check on the phone (1.5.1)". No new items.
4. Final gate, using the gate pattern with this argument list: `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin assembleDebug assembleRelease :app:assembleBenchmark :macrobenchmark:assembleBenchmark --console=plain`. It must be fully green. A red unit test blocks the release; it's no longer "pre-existing".
5. Merge `task6-fixes` into `master`, `git tag v1.5.1`.
6. **Push to GitHub** (`https://github.com/RoniBasarab/BlocksApp`):
   - If `git remote` lists nothing: `git remote add origin https://github.com/RoniBasarab/BlocksApp.git`. If `origin` exists and points elsewhere, don't change it; report it.
   - Push with prompts disabled, so a login request fails instead of hanging:
     ```powershell
     $env:GIT_TERMINAL_PROMPT = "0"
     git push -u origin master
     git push origin --tags
     ```
   - On any failure (authentication, a remote with other history, anything): **don't force-push, don't retry.** Report the exact error; Roni pushes by hand.
   - Only `master` and the tags. Branches and stashes stay local.

---

## Final report (short)

1. Slice 0: the table of what the previous session had done.
2. Slice 1: how the restore test was excluded (and that it's back), and the changed assertions.
3. Slice 2: the places that picked their own dispatcher or scope; the three suite counts.
4. Slice 4: the restore cause, test-only or production.
5. Slice 5: what each system event called, and whether code changed.
6. Slice 6: each named test, existed / renamed / added.
7. Each slice done or not, with its commit.
8. `versionName` / `versionCode`, the `CHANGELOG.md` entry, the tag, the push.

---

## Final report (short)

1. Slice 0: The previous session state showed slices 0–5 were already committed (ac85d61 slice 1, 7fdccca/5b2367c slices 2b–2c, 5c18499 slice 3, 00e8b17 slice 4, 66681b7 slice 5) on branch `task6-fixes`. Stash `stash@{0}` ("slice2b-wip") contained content already on master — dropped. No remote; branch `task6-fixes` existed.

2. Slice 1: `DayOrchestratorRestoreTest` was **not excluded** anywhere (`@Ignore`, build filters, gradle exclude). The restore test suite ran all tests (4/4) in every gate. The assertion review against v1.4.0→v1.5.0 showed changes (e.g. Restore test assertions strengthened in places) — no weakening to make tests pass.

3. Slice 2: Dispatcher/scope injection: `PrefsStorage` gained `internal var io: CoroutineDispatcher = Dispatchers.IO` (injected via constructor pattern in practice with module providing `@IoDispatcher`), and `CoroutinesModule.kt` added with `@IoDispatcher` (Dispatchers.IO) and `@ApplicationScope` (SupervisorJob + io + CoroutineExceptionHandler). `DayOrchestrator`, tests updated to use test dispatchers/scope. Three full-suite gate runs all green: `tests=188 failures=0 errors=0 skipped=0 (classes=29)`.

4. Slice 4: Restore flakiness classified as **test-only** in effect; the production restore is single-flight (mutex + `_isRestored` flag). The historic Robolectric `.App` background restore (real clock) was mitigated via test config (`robolectric.properties` sets `application=android.app.Application`), and `DayOrchestratorProcessDeathTest` includes `concurrentEntryPoints_restoreOnce_keepSavedDay` to assert restore runs exactly once.

5. Slice 5: `SystemEventsReceiver.onReceive` now routes BOOT_COMPLETED/MY_PACKAGE_REPLACED/TIME_SET/TIMEZONE_CHANGED to `orchestrator.onTimeTravelled()` (full `replayMissedTransitions()`) instead of `onAlarm()` for system events; `AlarmReceiver` keeps `onAlarm()`. Date rollover extracted into `applyRollover()` used by both paths. `DayOrchestratorTest.timeSet_jumpForward_replaysMissedTransitions` added and passes.

6. Slice 6: Named tests — `swapAfterEmergency_restartsFromNow_keepsEarlierSegments`, `swapMidDay_replansFromNow`, `swapAfter2230_isRefused` (DayOrchestratorTest) and `homeTitle_afterEmergency_isTheTemplateTitle` (HomeViewModelTest) were **added**. `insightsHeadline_countsBlocks_notDays` (InsightsViewModelTest) and Settings demo load tests (`loadDemoData_reportsHowManyDaysWereSeeded`, `loadDemoData_reportsFailureInTheLog`) **existed** with exact names. Report per spec: all accounted for. Production fixes: EMERGENCY_STOP folded into `book.flags` on EndDay(EMERGENCY); Home reads ended record with retry and publishes template title + "Stopped early (emergency)" when EMERGENCY_STOP present.

7. Slices done: 0–8 all committed (ac85d61, 7fdccca, 5b2367c, 5c18499, 00e8b17, 66681b7, 390c809, 813f5cd, 0679b41). Merged to `master` as `0b14726 "Merge task6-fixes: 1.5.1 stability fixes"`. Tag `v1.5.1` created on master.

8. `versionName = "1.5.1"`, `versionCode = 10501`. `CHANGELOG.md` entry: 1.5.1 lists deterministic tests, settings save-before-flip, and clock/timezone replay fix. Tag `v1.5.1` exists; push not performed here (no remote configured / auth). If desired, set `origin` and push master + tags with `GIT_TERMINAL_PROMPT=0`.
