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

Nothing else is open: every slice's unit gate (`:app:testDebugUnitTest`) and its compile gates
pass on this machine.
