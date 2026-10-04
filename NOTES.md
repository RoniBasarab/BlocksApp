# Notes - decisions the task files left unspecified, and what the code does instead

- `ActionEvent`: the frozen-notification events (`CONTINUE_WHERE_I_WAS`, `JUMP_TO_NOW`) are gone with the frozen state; `START_NEXT` survives as the one "move on" action, so old notification buttons that carry it keep working after an update (Task-5.md:97).
- `NotificationModelMapper` names every block by its real stable `BlockSpec.id`; buttons posted before the update count as stale and are ignored (Task-5.md:97 - fine by design).
- Debt is `PLANNED` overruns only; pause time is logged as `PAUSE` segments and shown as Pause time, never debt. `debtMinutes` moved to a top-level function in `HistoryManager.kt` because it is pure and needed by JVM tests.
- `TimelineQueries.effectiveEnd` returns the open block's planned end unchanged: a pause shortens a block, it never moves an end (Task-5.md:84).
- `DayRecord.plannedBlocks` snapshots the template at StartDay; old records without one fall back to the current template (Task-5.md:98).
- `BlockSpec` persists nowhere except templates.json (bundled asset, no migration needed, per Task-5.md:97).
- The compose-side next-button label rule: a work block offers "Next: Free" (finishing early -> the rest becomes free); a free block offers "Next: {next block name}" for planned and synthetic free alike. Pomodoro breaks are still Pause territory (Task-5.md table, Slice 9).
- `NowButtonId.EMERGENCY` renders as its own centered TextButton (`now_emergency`) instead of living in the side-by-side row, so Pause · Next stay equal-width 48dp buttons (Task-5 layout).
- Emergency confirmation is always shown ("Stop today", red); only Pause supports "Don't ask again".
- `TimeFormatter.label/hourGutter` companion statics are the single 24h/12h source of truth; screens pass the flag from the `use24` prefs-backed flow.
- `BlockType.isWork` (CV/Coding/Gym) lives in `Model.kt` next to the sealed interface, shared by the ledger, notifications and Home.
- The renderer-test recording sink uses `CopyOnWriteArrayList` because notification renders resume on IO workers (prefs writes) and would race an `ArrayList`.
- History groups weeks by the `weekStart` walk-back-to-Sunday rule from Task-5; older weeks label themselves "20 – 26 Sep" style.

## Task-6 decisions the task file left unspecified

- **Restore is idempotent, single-flight, and always completes.** `DayOrchestrator.ensureRestored()` takes a mutex, and sets `_isRestored` in a `finally`, so a `restoreFromStorage()` that throws can no longer strand the app on the splash. Every entry point - the UI gate, `send`, and each receiver - calls it before touching the day. Before this, `handle()` was `= apply(event)` with no restore at all, which is why notification buttons did nothing after a kill.
- **`DayOrchestrator.onTimeTravelled()` is new, and deliberately not `onTimeChanged()`.** Task-6.md:366 says the debug controller should call `onTimeChanged()`, but :376 expects 20:05 to reach `KidTime`, and `onTimeChanged()` never applies missed transitions (only `onAlarm()` and restore did). The literal wiring left the evening unreachable, defeating the slice's own stated purpose. `onTimeTravelled()` replays the skipped transitions exactly as a cold start does. Proof it was the real gap: `DebugTimeControllerTest.evening_canBeReachedWithoutWaiting` failed with `expected:<KidTime> but was:<Running(template=SUN_TUE_WED, blockIndex=0)>` before the change.
- `SettingsRow.enabled` gates the Developer rows off in release builds while leaving them visible in debug, instead of hiding them with a build check at each call site.
- Settings copy stays as inline literals rather than moving to `strings.xml`, matching the rest of the codebase (roni's call). This is a deliberate deviation from Task-6.md's wording.
- `UiExpectationsTest.assertGutters` measures with `getUnclippedBoundsInRoot()`, because the last Settings card is now legitimately scrolled out of the viewport and a clipped-bounds check would fail on correct layout.
- `HomeViewModel` and `SettingsViewModel` take a `DebugTimeController`; their tests build a real `DebugClock` with offset 0, since the controller sets the offset on the concrete clock rather than an interface.
- Two names in Task-6.md Slice 9 do not exist in the code: `TimelineQueries` is a file of `ScheduleEngine` extension functions, not a class, and there is no `InsightsManager` - that logic is `InsightsViewModel`. The headers went on the real code; see `issues.md` item 4.