# TASK 17: Days that start on time, steps as an estimate (2.7.0)

**Target version: 2.7.0** (versionCode 20700). It includes the never-run Task-16 (steps as an estimate). Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/` or `docs/`.

## What Roni found (12–13 Oct)

1. Just after midnight he picked a template, and the first block started *at once*, counting down 9+ hours to its planned end. He wants the day to wait: the calendar plus a "Day starts at 09:00 · in 1h 20m" card, with the first block starting by itself at its time.
2. The step count rose while he was driving (the sensor counts bumps). Steps should read as an estimate.

## Decisions (Roni, 13 Oct)

| Topic | Decision |
|---|---|
| Picking a template | Possible from midnight. If the first block's time hasn't come, the day **waits** |
| While waiting | Home shows the day's calendar and a card "Day starts at 09:00 · in 1h 20m", with [Start now] and [Change day]. No block runs, nothing is counted, no sounds |
| At the first block's time | It starts by itself, with the normal transition sound and notification |
| Start now | The first block starts now and keeps its length; the gap it leaves becomes free time (Fri/Sat: kid time just starts now) |
| Where the start comes from | Each template's first block. There's no global start setting; Settings doesn't change |
| Day end | Stays 22:30 (not dynamic for now) |
| History | Nothing new to store: the day's plan already holds its start; a day started early shows its moved blocks |

## Rules

1. Branch `task17` from `master`. First `git add docs app ; git rm docs/Task-15.md ; git commit -m "task17 prep: docs and verified startFirstNow"`, then `task17 slice N: <title>`.
2. `docs/AGENT.md` has the shell, Gradle and flow rules. Read each file once, around the lines named here.
3. *Verified*, don't edit: `core/schedule/PlanEditor.kt` (new `startFirstNow`) and `StartEarlyWalkTest.kt` (new `startNow_beforeTheDay_movesTheFirstBlockToNow`), plus every file named verified before.
4. Add only the tests named here; list any existing test you change.

---

## Slice 1: Steps as an estimate

1. Next to `formatSteps` (`git grep -n "fun formatSteps"`):
   ```kotlin
   /** Steps as the estimate they are (2.7.0): rounded to tens, e.g. "2,050". */
   fun formatStepsApprox(steps: Long): String = formatSteps(((steps + 5) / 10) * 10)
   ```
2. Use it everywhere a step count is shown:

   | Where | Line (approx.) | New text |
   |---|---|---|
   | Home top bar | `feature/home/Home.kt` ~234, `stepsLabel` | `"about ${formatStepsApprox(it)} steps"` |
   | History day header | `feature/history/History.kt` ~336 | `"About ${formatStepsApprox(value)} steps"` |
   | Each walk line | `feature/history/History.kt` ~346 | `"$line · about ${formatStepsApprox(it)} steps"` |
   | Weekly tile | `feature/weekly/Weekly.kt` ~302 | value `"≈ ${formatStepsApprox(total)}"`, hint `"About ${formatStepsApprox(total / stepTotals.size)} a day"` |

3. Add to `ExplainerTerms` in `designsystem/Shell.kt`: `"Steps" to "Estimated by the phone's step sensor. Bumps while driving can add a few; steps walked without the phone don't count."`
4. Test `StepsFormatTest.approx_roundsToTens`: `formatStepsApprox(2047) == "2,050"`, `formatStepsApprox(0) == "0"`. Update tests asserting the old strings and list them.

---

## Slice 2: The day waits for its first block

**SegmentBook.startDay:** before the first block, nothing opens. Add at the top:
```kotlin
val first = blocks.firstOrNull() ?: return DayBook()
if (now < first.plannedStart) return DayBook(index = 0)        // 2.7.0: the day waits for its first block
```

**DayOrchestrator:**
- **Waiting** means `Running` with no segments yet and `now <` the plan's first block start. Expose `val startsAt: StateFlow<Instant?>`: the first block's start while waiting, otherwise null. Keep it updated wherever the segments change.
- **`sideEffects()`:** while waiting, the alarm candidates include the first block's start (`engine.nextWakeUp` returns that block's *end*, which is wrong here). No alerts while waiting: `AlertPlanner` already needs an open segment.
- **`applyOneMissedTransition()`:** waiting and `now >=` the first start → `book = SegmentBook.startDay(currentTemplate, today, zone, now)`. If the first block is kid time, the state becomes `KidTime` (the same as `StartDay` does today). Then `sideEffects(transition = true)`.
- **New `startNow(): Boolean`**, a user action (sets `reactedAt`):
  - only while waiting;
  - `PlanEditor.startFirstNow(plan.blocks.toPlanBlocks(), nowMin)` → commit the plan (`edited = true`);
  - `book = SegmentBook.startDay(newPlan, …, now)`;
  - kid time first → `KidTime`;
  - persist, then `sideEffects(transition = true)`.
- **"Change day"** is the existing template swap (`HomeAction.SwapTemplate`).

**Home:**
- **The waiting card:** `NowCardUi(title = "Day starts at HH:MM", subtitle = "in 1h 20m"` (or "in 45 min"), with no chip and no progress. Buttons: PRIMARY "Start now" (new `NowButtonId.START_NOW` → `day.startNow()`) and SECONDARY "Change day" (new `NowButtonId.CHANGE_DAY` → `HomeAction.SwapTemplate`).
- **Live countdown:** the "in …" text updates every minute (the minute ticker).
- **While waiting:** the calendar shows the plan with no current block, and the status line says "Starts at HH:MM".
- **Calendar start:** the calendar starts at `min(08:00, the plan's first start rounded down to the hour)`. Add `HomeState.calendarStartMin` and use it in `DayCalendar` and `CalendarLayout.fit(..., dayStartMin = calendarStartMin)` instead of the `DAY_START_MIN` constant. A day started at 07:40 then draws from 07:00.

**Notification while waiting:**
- title "Day starts at HH:MM", text = the template's title, `countdownTo` = the first start, no actions;
- quiet (no `transition`/`alert`);
- the widget follows the same model.

**Tests:**
- `DayOrchestratorTest.startDay_beforeTheFirstBlock_waits`: Sun/Tue/Wed `StartDay` at 00:30 → `Running`, no segments, `startsAt` 09:00, next alarm 09:00.
- `DayOrchestratorTest.firstBlock_startsByItselfAtItsTime`: then `onAlarm()` at 09:00:05 → an open `cv` segment, and the last render was a transition.
- `DayOrchestratorTest.startNow_movesTheFirstBlockToNow`: waiting, clock 07:40, `startNow()` → an open `cv` segment from 07:40; the plan has `cv` 07:40–08:10 and free time 08:10–09:30.
- `DayOrchestratorTest.friSat_startNow_startsKidTime`: Fri/Sat waiting at 08:15, `startNow()` → `KidTime`.
- `HomeViewModelTest.waiting_showsTheStartsAtCard`: waiting at 07:40 → NowCard title "Day starts at 09:00", buttons `[START_NOW, CHANGE_DAY]`, status "Starts at 09:00".

---

## Slice 3: Docs and release 2.7.0

1. `docs/CODEMAP.md` (`startFirstNow`, `startNow`, `startsAt`, the waiting card), `docs/NOTES.md` (one line per decision).
2. `docs/MANUAL_TESTS.md`, "Check on the phone (2.7.0)":
   1. After midnight (or with the debug clock at 07:00), pick a template: the calendar plus "Day starts at 09:00 · in 2h 0m", nothing counting.
   2. At 09:00 (debug clock) the first block starts by itself with a sound.
   3. Start now at 07:40: CV 07:40–08:10, free time until 09:30, and the calendar now begins at 07:00.
   4. Change day opens the picker.
   5. Steps read "about 2,050 steps"; Weekly shows "≈".
3. `versionName = "2.7.0"`, `versionCode = 20700`. `CHANGELOG.md`:
   ```markdown
   ## 2.7.0
   - A day picked before its first block waits: "Day starts at 09:00 · in 1h 20m", with Start now and Change day. The first block starts by itself at its time.
   - Steps read as an estimate ("about 2,050 steps"), rounded to tens.
   ```
4. Final gate (the `cmd /c` form), merge `task17` into `master`, tag `v2.7.0`, push. A report of at most 15 lines, then stop.
