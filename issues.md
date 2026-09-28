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

Nothing else is open: every slice's unit gate (`:app:testDebugUnitTest`) and its compile gates
pass on this machine.
