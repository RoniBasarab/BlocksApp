# TASK 6 FIXES: make 1.5.0 stable

You're the same agent that did Task-6, so you already know the repo, the specs and the code. Don't re-read `SUMMARY.md` or the specs unless a slice points to them. (If you're a fresh session, read `SUMMARY.md` and `Task-6.md` first.)

**Target version: 1.5.1** (patch: fixes only, no behavior change).

This comes from the planning agent's review of your Task-6 report. The release is fine on paper, but the unit suite is flaky, the restore tests are among the flaky ones, and some named tests aren't accounted for. Roni won't install until this is done.

## Rules

1. Branch `task6-fixes` from `master` (`v1.5.0`). One slice = at most ~3 files, the gate, one commit `task6-fixes slice N: <title>`. Never start a slice on a red build.
2. Foreground only. No sub-agents, no background jobs.
3. Gradle: the gate exactly as before (Start-Process, 10-minute kill, `gate.log` / `gate.err`), the 4-minute test timeout, the stuck ladder, the 10-minute rule. `BUILD SUCCESSFUL` = commit and go. The **only** place you run the suite more than once is the acceptance check in Slice 2.
4. Don't weaken any assertion to make a test pass. Fix the code or the test's setup and teardown, never its expectations.
5. Strings stay inline, as now. Roni's decision: they move to `strings.xml` in a later task, not this one.
6. Don't ask Roni questions. Unclear → simplest option, logged in `NOTES.md`.

---

## Slice 1: Two answers before any change (no code)

Answer each in the report, with evidence.

1. **"`DayOrchestratorRestoreTest` remains excluded on instruction."** Roni gave no such instruction. Show exactly how it's excluded: an `@Ignore`, a filter or `exclude` in `app/build.gradle.kts`, a `--tests` pattern, or something else. If it's excluded anywhere, remove the exclusion in this slice and commit. Every test runs in the gate.
2. **The 8 test files you changed in Task-6.** Run `git diff v1.4.0 v1.5.0 --` on each and list every changed **assertion** (not setup or teardown): old line, new line, why. If an assertion got weaker, restore it in the slice where you fix the cause.

---

## Slice 2: A deterministic unit suite

**Bug (your own diagnosis, `issues.md` item 3):** a coroutine started in production code on a real dispatcher outlives its test and fails the next test class (`UncaughtExceptionsBeforeTest`). The failing set changes from run to run.

**Fix the cause, not the symptom.** The test must own every coroutine the code under test starts.

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

   Classes that use `Dispatchers.IO` or build their own scope take `@IoDispatcher` / `@ApplicationScope` through their constructor instead. Change nothing else in those classes.
3. In tests, pass the test's own dispatcher and scope:

```kotlin
@Test fun example() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val orchestrator = DayOrchestrator(/* … */, io = dispatcher, appScope = backgroundScope)
    // backgroundScope is cancelled by runTest when the test ends, so nothing outlives it
}
```

   `backgroundScope` (from `kotlinx-coroutines-test`) is the scope for anything long-lived the code starts. Remove `Dispatchers.setMain` / `resetMain` only where it's no longer needed; keep it for ViewModels (`viewModelScope` uses Main).
4. Split this over as many slices as it needs (2a, 2b, …), at most ~3 production files each.

**Acceptance (the one allowed repeat):** after the last 2x slice, run `:app:testDebugUnitTest` **three times in a row** with the normal gate. All three must pass with the same test count. Report the three counts. If any run fails, the slice isn't done: find which coroutine still escapes (the `CoroutineId` in the stack trace names the dispatcher; the test that started it ran just before the failing class).

---

## Slice 3: Settings saves before it says so

**Bug (`issues.md` item 2):** `setHourFormat` flips the in-memory flag in `viewModelScope.launch` before the write lands, so a reader right after it can see the old value. Your report also names `settingsState_isLoadingUntilTheLoadCompletes`; check that one too.

- Fix in the code: the state changes **after** the write completes (`write()` is `suspend` and returns once `commit()` is done; update the state after it). Same pattern for any other Settings action that writes and then updates state.
- Both tests must pass unchanged (Rule 4).

---

## Slice 4: Is the restore flakiness a real race?

`todaysRunningState_isRestoredAsRunning` and `legacyInterrupted_restoresAsRunning` failed in some runs. Restore is where the splash bug lived, so this must be understood, not only made green.

1. After Slice 2, run `DayOrchestratorRestoreTest` alone 5 times (`--tests`, normal gate each time). Report the results.
2. Look for a production race. Candidates:
   - something writes `NotStarted` (or any state) to storage before restore has read it: an `init` block, a state collector, the date-rollover check;
   - two entry points restore at once (`Application.onCreate` and a receiver) and the second doesn't wait for the first;
   - `send()` applies an event before `ensureRestored()` has returned.
3. Write the cause in one or two sentences in the report: "test-only: …" or "production: …". If it's production, fix it.
4. Add this test whatever the cause, in `DayOrchestratorProcessDeathTest`:
   - `concurrentEntryPoints_restoreOnce_keepSavedDay`: saved Running on `sun_tue_wed.coding_am`, clock 10:00. On a fresh orchestrator, launch `ensureRestored()` from three coroutines and `send(Pause)` from a fourth, all at once. Assert: state is `Paused` on `sun_tue_wed.coding_am`, restore ran exactly once (count it with the fake storage's read counter or the logger), and storage still holds today's segments.

---

## Slice 5: A real clock change replays like a restart

Your `onTimeTravelled()` note says `onTimeChanged()` never applies missed transitions. `architecture.md` §11 says `SystemEventsReceiver` must replay them on `TIME_SET` and `TIMEZONE_CHANGED` (and on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`).

- Check what `SystemEventsReceiver` calls for each of the four actions. Report it.
- If any of them doesn't replay, route it to the same replay path `onTimeTravelled()` uses. One replay path for restore, time travel and system events; don't write a second one.
- Test (in the orchestrator or receiver test, whichever already exists): `timeSet_jumpForward_replaysMissedTransitions`: Running on `sun_tue_wed.cv` at 09:10; the clock jumps to 16:30; the time-set path runs → state `KidTime`.
- If it already replays everywhere, say so, add the test anyway, and move on.

---

## Slice 6: The named tests from Task-6

Your report doesn't name these. For each: confirm it exists **with this exact name** and passes, or add it now exactly as `Task-6.md` specifies.

| Test | Task-6 |
|---|---|
| `swapAfterEmergency_restartsFromNow_keepsEarlierSegments` | Slice 2 |
| `swapMidDay_replansFromNow` | Slice 2 |
| `swapAfter2230_isRefused` | Slice 2 |
| `homeTitle_afterEmergency_isTheTemplateTitle` | Slice 2 |
| `insightsHeadline_countsBlocks_notDays` | Slice 6 |
| `SettingsViewModelTest`: demo load success → "Demo data loaded: 28 days"; failure → "Couldn't load demo data. Details are in the log." | Slice 7 |

If a test exists under another name, rename it to the one above. Report each row: existed / renamed / added.

---

## Slice 7: Housekeeping

- `git stash show -p stash@{0}`: if everything in it is already on `master`, `git stash drop`. If not, don't drop it; list what's missing in the report.
- `.gitignore`: add `.idea/`, `gate.log`, `gate.err`, `*.log` if they're not there.
- `issues.md`: remove item 4 (the planning agent fixes the Task-6 naming in its docs). Remove items 2 and 3 if Slices 2–4 fixed them. Keep item 1 (device tests) until Roni runs them.
- `NOTES.md`: one line each for the injected dispatcher/scope pattern and the single replay path, so later tasks follow them.
- `CODEMAP.md`: rows for any new file (e.g. `CoroutinesModule.kt`).

---

## Slice 8: Release 1.5.1

1. `versionName = "1.5.1"`, `versionCode = 10501`.
2. `CHANGELOG.md`, on top. Keep only the lines that are true:

```markdown
## 1.5.1
- Unit tests are deterministic: every coroutine the code starts belongs to the test that started it.
- Settings saves a change before showing it.
- Fixed: a race when several parts of the app restore the day at once.        (only if Slice 4 found a production race)
- Fixed: a clock or timezone change now catches up on missed blocks, like a restart.   (only if Slice 5 changed code)
```

3. `MANUAL_TESTS.md`: no new list. Change the heading "Check on the phone (1.5.0)" to "Check on the phone (1.5.1)"; Roni runs it on this release.
4. Final gate: `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin assembleDebug assembleRelease :app:assembleBenchmark :macrobenchmark:assembleBenchmark`. It must be fully green. A red unit test is not "pre-existing" anymore: it blocks the release.
5. Merge `task6-fixes` into `master`, `git tag v1.5.1`.
6. **Push to GitHub.** The repo has no remote yet; Roni's is `https://github.com/RoniBasarab/BlocksApp`.
   - If `git remote` lists nothing: `git remote add origin https://github.com/RoniBasarab/BlocksApp.git`. If `origin` exists and points elsewhere, don't change it; report it.
   - Push with prompts disabled, so a login request fails instead of hanging the session:
     ```powershell
     $env:GIT_TERMINAL_PROMPT = "0"
     git push -u origin master
     git push origin --tags
     ```
   - If the push fails (authentication, a non-empty remote with other history, anything else), **don't force-push and don't retry**. Report the exact error; Roni pushes by hand.
   - Push only `master` and the tags. Branches and the stash stay local.

---

## Final report (short)

1. Slice 1: how the restore test was excluded (and that it's back), and the list of changed assertions.
2. Slice 2: the places that picked their own dispatcher or scope; the three suite counts.
3. Slice 4: the restore cause in one or two sentences, test-only or production.
4. Slice 5: what each system event called, and whether code changed.
5. Slice 6: each named test, existed / renamed / added.
6. Each slice done or not, with its commit.
7. `versionName` / `versionCode`, the `CHANGELOG.md` entry, the tag.
8. The push: done, or the exact error.
