# Notes - decisions Task-5 left unspecified, and what the code does instead

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
