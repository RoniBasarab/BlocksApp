# Outstanding after Task-6, each with evidence

1. **Connected device tests have not been executed on hardware or emulator.**
   The D-series includes the D13/D14 process-kill restore tests added for the splash fix, and the
   13-item "Check on the phone (1.5.0)" list at the top of `MANUAL_TESTS.md` is entirely unrun.
   Evidence: `:app:compileDebugAndroidTestKotlin` compiles and
   `:app:assembleBenchmark`/`:macrobenchmark:assembleBenchmark` build, but
   `connectedDebugAndroidTest` needs a device and none was attached during this task.

2. **`SettingsViewModelTest.setHourFormat_persistsAndFlipsTheFormatter` races its own write.**
   `Settings.setHourFormat` returns before its write lands (`viewModelScope.launch { … }`), while the
   test waits on the in-memory `hourFormat24` flip and only then reads the persisted pref, so the read
   can see `null`.
   Evidence: `expected:<false> but was:<null>` — the failure recorded during the Task-6 full-suite runs
   in the previous revision, and it **recurred** in the 1.5.0 release gate (`180 tests completed,
   2 failed`). It passes in isolation and at baseline. The fix belongs in the test or in
   `setHourFormat`, not in Task-6.

3. **A coroutine that outlives its test class poisons the next Compose test class. The failing set
   changes between runs of the same tree; it is not one fixed failure.**
   Evidence, the two 1.5.0 release-gate runs plus an isolation control:
   - run 1: `180 tests completed, 2 failed` — item 2 above, plus
     `TemplatePickerTest.screen_rendersFourTaggedCardsWithOneSuggested`;
   - run 2: `180 tests completed, 1 failed` — `DayOrchestratorRestoreTest.legacyInterrupted_
     restoresAsRunning` (`:121`); neither run-1 failure recurred;
   - earlier Task-6 runs: two green (`176 tests completed, 0 failed`), one `1 failed`
     (`todaysRunningState_isRestoredAsRunning`, `:85`), one `3 failed` (`:103`, `:85`, `:121`).
   The `TemplatePickerTest` failure is **collateral, not a defect**:
   `kotlinx.coroutines.test.UncaughtExceptionsBeforeTest`, suppressed
   `UnsupportedOperationException: Dispatchers.Unconfined.dispatch function can only be used by the
   yield function`, thrown from `CoroutineId(1194)` on `Dispatchers.IO`. A coroutine outlives its
   test class, resumes *after* `Dispatchers.resetMain()`, and hits a still-installed
   `TestMainDispatcher`; `kotlinx.coroutines.test` records that uncaught exception per JVM and
   re-reports it at the *next* `runTest`, which is why an untouched class such as `TemplatePickerTest`
fails "before the test started". Proof: `TemplatePickerTest` and `SettingsViewModelTest` run
    together in isolation give `BUILD SUCCESSFUL`. `TemplatePickerViewModel` was never modified.
    Correction (Task-6-fixes, Slice 1): `DayOrchestratorRestoreTest` is **not** excluded. A 1.5.0
    report line claimed it was "excluded on instruction", and that was wrong on both counts — Roni
    gave no such instruction, and there is no exclusion to remove. The file has no `@Ignore` and no
    `@Disabled`; `app/build.gradle.kts` has no `testOptions.unitTests` filter, no
    `excludeTestsMatching` and no `includeTags`; and the last
    `TEST-…DayOrchestratorRestoreTest.xml` records `tests=4 failures=1 skipped=0`, i.e. all four of
    its tests run in every gate. Its nondeterminism is a real defect, fixed in Task-6-fixes
    Slice 4.

4. **Two names in `Task-6.md` §Slice 9 do not exist in the code.**
   `TimelineQueries` is a file of `ScheduleEngine` extension functions (`timelineAt`, `currentBlock`,
   `pomodoroStatus`), not a class, so its header went on those functions' file; and there is no
   `InsightsManager` anywhere in `src/main` or `src/test` — that logic is `InsightsViewModel`, which
   received the header the spec asked for. Evidence: a search for `InsightsManager` across
   `src/main` and `src/test` returns nothing. `Task-6.md` is left unchanged for Roni to correct.

Nothing else is open. Every non-flake gate task passes on this machine: `:app:compileDebugKotlin`,
`:app:compileDebugAndroidTestKotlin`, `assembleDebug`, `assembleRelease`, `:app:assembleBenchmark`
and `:macrobenchmark:assembleBenchmark` complete with `BUILD SUCCESSFUL` (`191 actionable tasks`).
`:app:testDebugUnitTest` is intermittently red only for items 2 and 3 above, both pre-existing and
both reproduced here as evidence rather than fixed.