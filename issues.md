# Outstanding after Task-5, each with evidence

1. **Roborazzi screenshots not re-recorded for the new/changed screens.**
   Task-5 requires `now_card_paused.png`, `dialog_emergency.png`, `settings_debug.png`, and data
   screens rendered from `DemoDataGenerator` output (seed 42) instead of hand-made fakes.
   Evidence: `recordRoborazziDebug` was never invoked in this task, and `ScreenshotsTest` has no
   entries for those captures; the existing captures still use hand-made fakes.

2. **Connected device tests have not been executed on hardware or emulator.**
   The D-series now includes new D13/D14 process-kill restore tests. Evidence:
   `:app:compileDebugAndroidTestKotlin` compiles, but `connectedDebugAndroidTest` needs a device;
   they are documented in `MANUAL_TESTS.md` for an on-device pass.

3. **`SettingsViewModelTest.setHourFormat_persistsAndFlipsTheFormatter` races its own write.**
   `Settings.setHourFormat` returns before its write lands (`viewModelScope.launch { … }`), while
   the test waits on the in-memory `hourFormat24` flip and only then reads the persisted pref, so
   the read can see `null`. Evidence: observed once as `expected:<false> but was:<null>` at
   `SettingsViewModelTest.kt:138` during a Task-6 full suite run. It is unrelated to Task-6, which
   touches only `DayOrchestrator` and `SegmentBook`; it passes in isolation 3/3 and at baseline 2/2,
   and did not recur across 3 full runs after the Task-6 process-death test stopped leaking
   unscoped coroutines. The fix belongs in the test or in `setHourFormat`, not in Task-6.

4. **`DayOrchestratorRestoreTest.startDestination_isOnlyCorrectOnceRestoreHasRun` fails on the
   committed baseline, independent of Task-6.** The test seeds `Running(SUN_TUE_WED, 1)` for today,
   builds one orchestrator on a dispatcher that never runs work, then a second on
   `Dispatchers.Unconfined`; the second one restores `NotStarted`, so `resolveStartDestination`
   returns `TEMPLATE_PICKER` instead of `HOME`.
   Evidence: reproduced with Task-6's work removed entirely — `git stash push -u`, then
   `:app:testDebugUnitTest --tests "...DayOrchestratorRestoreTest"` gives `4 tests completed,
   1 failed`, same assertion. The companion `legacyInterrupted_restoresAsRunning` in the same class
   passes alone and only failed when it inherited an orphan write; making `PrefsStorage`'s IO
   dispatcher injectable and synchronous in tests fixed that one, leaving only this failure. It is
   not caused by Task-6, which does not change restore's state logic. The fix belongs in this test
   or in `DayOrchestrator`'s single-flight restore, not in Task-6.

Nothing else is open: every slice's compile gate passes on this machine, and the unit gate is green
except for the two pre-existing failures recorded above.
