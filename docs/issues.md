# Outstanding after Task-8, each with evidence

1. **Connected device tests have not been executed on hardware or emulator.**
   The D-series (D9–D12: relaunch, reboot, lock screen, DND) and the 12-item
   "Check on the phone (2.1.0)" list at the top of `MANUAL_TESTS.md` (widget follows Next
   included) have not been run.
   Evidence: `:app:compileDebugAndroidTestKotlin` compiles and
   `:app:assembleBenchmark`/`:macrobenchmark:assembleBenchmark` build, but
   `connectedDebugAndroidTest` needs a device and none was attached during this task.

2. **Two copies of a block's planned end.** `TodayPlan` (the plan) and `Segment.plannedEnd` (the copy taken when the block opened) can disagree; Task-26 made Home and the notification read the plan, with a `plan/segment end drift` tripwire in the log. Remove one copy in the architecture round.
   Evidence: `docs/Task-26.md`; `HomeViewModel.publish`, `NotificationModelMapper.running`, `DayOrchestrator.warnOnPlanSegmentDrift`.