# TASK 10: Next that starts things now, and walks (2.3.0)

**Target version: 2.3.0** (versionCode 20300). A new feature on top of 2.2.1, nothing removed: a minor bump. This file overrides older docs where they differ. Paths below are under `app/src/main/java/com/roni/blocks/` unless they start with `app/`, `docs/` or a root file name.

## Slice map

Read lines 1–53 once (map, findings, decisions, rules), then each slice's lines when you start it.

| Slice | What | Lines | Start in | Tests |
|---|---|---|---|---|
| 0 | Prep | 55–67 | git, core/model/Model.kt, designsystem/Color.kt | - |
| 1 | Next starts things now | 68–87 | core/daystate/DayOrchestrator.kt, core/schedule/SegmentBook.kt, feature/home/Home.kt, NotificationModel.kt | DayOrchestratorTest |
| 2 | Walks: the day side | 88–129 | DayOrchestrator.kt, core/alarms/AlarmScheduler.kt, new core/notifications/WalkRinger.kt, NotificationModel.kt | DayOrchestratorTest |
| 3 | Walks: the screens | 130–175 | feature/home/Home.kt, HomeSheets.kt, DayCalendar.kt | - |
| 4 | Docs and release | 176–205 | docs/, CHANGELOG.md, app/build.gradle.kts | final gate |

## What Roni found on 2.2.1 (11 Oct)

1. Coding → Done early → Next → Next to the next Coding: Home said 327 min left, and the widget said "Break at 10:50" at 14:05.
2. Pressing Next through to kid time: the times piled up, kid time showed as a normal block with "Next: Free", and that button did nothing. No "Kid asleep".

## Causes (checked in the code)

| # | Cause |
|---|---|
| 1 | `SegmentBook.advanceAfter` opens the next block at the tap but keeps its planned times. Coding PM (14:00–15:30) opened at 10:03, so the countdown ran to 15:30 (327 min) and the Pomodoro counted from 10:03 |
| 2 | `DayStateMachine` turns Next into `blockIndex + 1` even when that block is kid time, so the day stays `Running` on the kid-time block instead of entering `KidTime` |

## New: walks (Roni, 11 Oct)

A "Walk" button stops what he's doing and starts a walk (30 min by default). Half the time he walks away from home; at half time the phone rings like a timer: "Turn back now". The way back takes as long as the way out, so he's home at the end, when it rings again. Then he's back to what he was doing.

## Decisions (Roni, 11 Oct)

| Topic | Decision |
|---|---|
| Next before the next block's start | The next block that isn't free time starts now and keeps its length; free time in between is skipped; what it leaves behind becomes free time. The Next label names that block |
| Next into kid time | Kid time starts now (the real kid-time state, with Kid asleep) |
| After a walk | Back to what he was doing: the current block resumes after the walk. A longer walk eats the start of the blocks after it. Kid time is a wall |
| Walk length | 30 min, −5/+5 on the start sheet (10–60) |
| Ringing | Like a timer: alarm sound at alarm volume (even on silent), repeating until he taps Stop, or one minute. With vibration: the one exception to the no-vibration rule, walks only |
| Turn back now | Before half time, a button ends the way out now; the way home takes as long as he has walked |
| Version | 2.3.0 |

## Rules

1. Branch `task10` from `master`. One commit per slice: `task10 slice N: <title>` (plus one `task10 prep` commit in Slice 0).
2. `docs/AGENT.md` has the shell, Gradle and flow rules; follow them.
3. Add only the tests named here. When an existing test checks something this task changes on purpose, update or delete it and list it in the report.
4. *Verified* (compiled, tested and fuzzed; commit, don't edit): `core/schedule/PlanEditor.kt` (`startEarly`, `nextStop`, `walkEnd`, `insertWalk`, `shortenWalk` added; nothing else changed), `core/schedule/AlertPlanner.kt` (`walkAlerts`, `dueWalk`, `nextWalk`, two new `AlertKind`s) and their tests, including the new `StartEarlyWalkTest.kt`.
5. Real names win: if a name here doesn't exist, use the real one and note it in `docs/NOTES.md`.

---

## Slice 0: Prep

1. `git add docs app/src/main/java/com/roni/blocks/core/schedule app/src/test/java/com/roni/blocks/core/schedule ; git rm docs/Task-9.md ; git commit -m "task10 prep: docs and verified planners"`
2. Make it compile: add `@Serializable data object Walk : BlockType` (`core/model/Model.kt`), and give it a branch wherever the compiler asks:
   - name "Walk";
   - colors `BlockColors(Color(0xFF2B3A3F), Color(0xFF8FC7C9))`;
   - not work, not editable (it already isn't: `isEditable()` is work or Free).

   Add `AlertKind.TURN_BACK` and `WALK_DONE` branches the same way.
3. Full suite, then commit `task10 slice 0: prep`.

---

## Slice 1: Next starts the next thing now

**DayOrchestrator**, in the Next path, before `SegmentBook.next`:
- Skip all of this when Next is a Done early (the open segment is PLANNED, a work type, and before its `plannedEnd`).
- Otherwise call `PlanEditor.startEarly(plan.toPlanBlocks(), currentId, nowMin)`. If it returns a plan:
  - Set `_todayPlan` to it (`toBlockSpecs(plan.blocks)`, `edited = true`).
  - Find the landing block, the one starting at `nowMin` that isn't the current block.
  - If the landing block is kid time: run the `KidTimeStarted` path instead of Next. The state becomes `KidTime`, and `SegmentBook.enterKidTime` uses the new kid-time start.
  - Otherwise set `book.index` to the landing index − 1 and continue with the normal Next. `advanceAfter` opens the landing block with its new times. The current block can leave the plan when it never ran (tapped in the minute it started), which is why the index is recomputed.
- `SegmentBook.enterKidTime` must close every open segment, `SYNTHETIC_FREE` included. Today it only closes PLANNED ones, so a block finished early just before kid time would stay open.
- A resumed piece (`<id>@<min>`, Slice 2) keeps its original's `quiet` flag. In `PlanConversion.toBlockSpec`, look the original up by the id before `@` when there's no exact match.

**Next labels** (Home NowCard and the notification): from free time or Done early, the label names `PlanEditor.nextStop(...)` ("Next: Coding", "Next: Kid time"), not the free block in between.

**Tests** (`DayOrchestratorTest`):
- `next_beforeTheNextBlock_startsItNowWithItsOwnLength`: Sun/Tue/Wed started at 10:00, Done early at 10:01, Next at 10:02 → `Running` on `coding_pm`, its open segment ends 11:32, and the plan has `coding_pm` 10:02–11:32 and no `free_midday`.
- `next_intoKidTime_startsKidTime`: the same day, Next through to kid time at 15:40 → state `KidTime`, kid time's segment starts 15:40, and no segment is left open except kid time's.

---

## Slice 2: Walks: the day side

**AlarmScheduler:** `schedule(at, clock: Boolean = false)`. With `clock = true` use `setAlarmClock(AlarmClockInfo(millis, openAppIntent(context)), pending())`, which Doze can't delay. The orchestrator passes `true` while a walk runs.

**DayOrchestrator:**
- `walkPreview(minutes): WalkPreview?` for the start sheet:
  - turn-back time and home time (`PlanEditor.walkEnd`);
  - what comes after, from `insertWalk`'s block after the walk: title and end.
  - Null when no walk fits: not `Running`, a walk already running, or under 10 minutes before kid time.
- `startWalk(minutes): Boolean`, a user action (sets `reactedAt`):
  - `insertWalk(plan, currentId, nowMin, minutes, doneEarly = open is SYNTHETIC_FREE)`, then commit the plan like `editPlan` does.
  - Close the open segment at now, and open a PLANNED segment of type `Walk` with `blockId` = the walk's id, `plannedStart` = now, `plannedEnd` = the walk's end.
  - State `Running` on the walk's index. `persist()`, then `sideEffects()`.
- `turnBack(): Boolean`: only on a walk before half time. The walk's end becomes now + (now − its start), on both the open segment (to the second) and the plan (`shortenWalk`, minute rounded up). Then `persist()` and `sideEffects()`.
- "End walk" is Next. With Slice 1, what came after the walk starts now.
- `applyOneMissedTransition`: an open `Walk` segment whose `plannedEnd` has passed ends by itself (Next, as for free time after Done early). A walk has no warning and no nags.
- Rings, in `onAlarm()`:
  - Work out `AlertPlanner.dueWalk(start, end, now)` before the missed transitions. The end of the walk is also a transition, so it has to be known first.
  - If one is due, call `walkRinger.ring(kind, ...)`.
  - In `sideEffects()`, while a walk runs, the alarm is the earlier of the engine's wake-up and `AlertPlanner.nextWalk(...)`, scheduled with `clock = true`.
- `onAppForeground()` also stops a ring.

**WalkRinger** (new, `core/notifications/WalkRinger.kt`; a small interface with a no-op default in the orchestrator constructor, like `WidgetPort`, so hand-built orchestrators in tests compile):
- Channel `walk_alarm`, `IMPORTANCE_HIGH`:
  - sound `RingtoneManager.getDefaultUri(TYPE_ALARM)` with `AudioAttributes.USAGE_ALARM`;
  - `enableVibration(true)`, pattern `[0, 800, 400, 800]`.
- `ring(kind)` posts notification id 3, category `ALARM`, `FLAG_INSISTENT` (repeats until dismissed), `setTimeoutAfter(60_000)`, auto-cancel, a "Stop" action that cancels it, and a tap that opens the app.
  - `TURN_BACK`: "Turn back now", text "Home by HH:MM".
  - `WALK_DONE`: "Walk done", text "Welcome back. <next> until HH:MM."
- `stop()` cancels id 3.

**Notification** (`NotificationModel.kt`, running on a Walk segment):
- Way out: title "Walk · heading out", text "Turn back at HH:MM · home by HH:MM", countdown to the turn-back time. Actions "Turn back now" (new `ActionEvent.TURN_BACK` → `turnBack()`) and "End walk" (`START_NEXT`).
- After the turn: title "Walk · heading home", text "Home by HH:MM · then <next>", countdown to the end. Action "End walk".

**Tests** (`DayOrchestratorTest`, with a fake `WalkRinger`):
- `walk_onCoding_resumesCodingAfterwards`: `coding_am` at 10:00, `startWalk(30)` → `Running` on `walk-600`, the open segment is a Walk ending 10:30, and the plan has `coding_am@630` 10:30–11:30.
- `walk_ringsAtHalfTimeAndAtTheEnd`: `onAlarm()` at 10:15:05 → `ring(TURN_BACK)`; at 10:30:05 → `ring(WALK_DONE)`, and the day is `Running` on `coding_am@630`.
- `turnBack_bringsTheEndCloser`: walk started 10:00, `turnBack()` at 10:08 → the walk ends 10:16, `coding_am@630` starts 10:16, and the next alarm is 10:16.

---

## Slice 3: Walks: the screens

**Where:**
- The NowCard gets a "Walk" button (`NowButtonId.WALK`, secondary) whenever a day block or Done early is running: `[Pause] [Walk] [Emergency]` under the primary button. Not during kid time, the evening, a pause or a walk.
- The walk shows on Home in place of the NowCard.

**Start sheet** (`WalkSheet`, a `ModalBottomSheet` in `HomeSheets.kt`):

```
Take a walk
      −5    30 min    +5
 ◐  Turn back at 10:15
 ⌂  Home by 10:30
    Then Coding until 11:30
 Rings like a timer at half time and at the end, even on silent.
 [ Start walk ]
```

The −5/+5 limits come from `walkPreview`. When kid time cuts it short: "Home by 16:20, before kid time".

**WalkCard** (Home, while the current block is a Walk):

```
 ╭──────────╮   Walk                    [Heading out]
 │  07:42   │   Turn back at 10:15
 │ to turn  │   Home by 10:30 · then Coding
 ╰──────────╯   [Turn back now]  [End walk]
```

- The ring (Canvas, about 120 dp):
  - the first half (the way out) in the walk accent `0xFF8FC7C9`, the second half (the way home) in `Secondary` `0xFFD9C38A` (cool going out, warm coming home);
  - a notch at the turn-back point;
  - a dot on the ring for now;
  - the elapsed part drawn solid, the rest at 35% alpha.
- In the middle, the countdown to the next ring (mm:ss), and under it "to turn" or "to home".
- After half time: the chip is "Heading home", "Turn back now" goes, and the countdown is to the end.
- Read the seconds ticker in the draw phase, like the NowCard's countdown.

**Calendar:** a Walk block in its colors, with a thin line at its middle labelled "turn".

**History recap:** a Walk row like any other ("Walk · 10:00–10:30"); no work minutes.

No new tests in this slice (UI); the orchestrator tests cover the behavior.

---

## Slice 4: Docs and release 2.3.0

1. `docs/CODEMAP.md`: `WalkRinger.kt`, the new `PlanEditor`/`AlertPlanner` functions, `WalkSheet`, `WalkCard`.
2. `docs/NOTES.md`: your decisions, one line each; keep it under 20 KB.
3. `docs/MANUAL_TESTS.md`, "Check on the phone (2.3.0)":
   1. On Coding: Done early, then Next: the next Coding block starts now with its own length (90 min), and the widget's break time follows.
   2. Next through to kid time: Kid time starts, with Kid asleep.
   3. On Coding, tap Walk: the sheet shows the turn-back and home times; Start.
   4. The phone on silent, screen off: at half time it rings and vibrates until Stop; "Turn back now" on the lock screen.
   5. Turn back now early: home time moves closer; the ring comes then.
   6. At the end it rings, and Coding resumes for the rest of its time.
   7. End walk early: what came next starts now.
   8. History: the walk is a row in the day.
4. `versionName = "2.3.0"`, `versionCode = 20300`. `CHANGELOG.md`:

```markdown
## 2.3.0
- Walks: tap Walk to step out for 30 minutes (adjustable). It rings like a timer at half time ("Turn back now") and when you should be home, then you're back to what you were doing.
- Next before the next block's time starts it now with its own length; free time in between is skipped.
- Next into kid time starts kid time (with Kid asleep).
- Fixed: a block started early counted down to its planned end (327 min left) and its Pomodoro to the wrong time.
```

5. Final gate, in the `cmd /c` form from AGENT.md: `gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin assembleDebug assembleRelease :app:assembleBenchmark :macrobenchmark:assembleBenchmark`.
6. Merge `task10` into `master`, `git tag v2.3.0`, push `master` and the tag.

## Final report (under 30 lines), then stop

Each slice: done or not. Tests added, changed, deleted. Names you adapted. Version, tag, push result. After the report, stop.
