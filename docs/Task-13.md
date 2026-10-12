# TASK 13: A walk that works, a calendar that never overlaps (2.6.0)

**Target version: 2.6.0** (versionCode 20600). Paths below are under `app/src/main/java/com/roni/blocks/` unless they start with `app/`, `docs/` or a root file name.

## Slice map

Read lines 1–43 once, then each slice's lines when you start it.

| Slice | What | Lines | Start in | Tests |
|---|---|---|---|---|
| 1 | The walk, one clock | 45–69 | feature/home/Home.kt, core/daystate/DayOrchestrator.kt, NotificationModel.kt | DayOrchestratorTest, HomeViewModelTest |
| 2 | Calendar never overlaps | 70–103 | feature/home/Home.kt, HomeUi.kt, DayCalendar.kt | HomeViewModelTest |
| 3 | Next; permissions at launch | 104–119 | MainActivity.kt, feature/home/Home.kt, feature/settings/Settings.kt | existing tests updated |
| 4 | Docs and release | 120–145 | docs/, CHANGELOG.md, app/build.gradle.kts | final gate |

## What Roni found on 2.5.0 (12 Oct)

1. Start walk: the timer shows `29862554:24`.
2. Turn back now a few seconds later: the card doesn't change (the calendar does). Turn back again: nothing.
3. End walk: the walk becomes a line on the calendar, and its text lands on top of the block's text.
4. The walk icon in the top bar does nothing.
5. Dishes and other short blocks overlap and get cut; a block ended after a minute almost disappears and should still show that it happened.
6. Next on Coding "deletes" blocks and jumps to the next Coding instead of the next block.
7. No permission card on Home: ask at launch, like notifications.

## Causes (checked in the code)

| # | Cause |
|---|---|
| 1, 2 | `HomeViewModel.walkCountdownText` / `walkProgressOf` / `walkHeadingHomeOf` compare `WalkCardUi.turnSec`/`endSec` (**epoch** seconds) with `minute * 60 + second` (seconds **of the day**). The countdown is ~1.8 billion seconds, and "heading home" is never true, so Turn back looked dead. The orchestrator did turn back; a second tap is correctly refused |
| 4 | `HomeViewModel.publish()` runs `if (walkCard != null \|\| dayState !is DayState.Running) walkSheet = null` (left over from 2.3.0). Outside a day block the sheet opens and is cleared in the same publish |
| 3, 5 | `CalendarBlock` always tries to fit its title (a 1-minute block still gets text); `WalkMarkersOverlay` draws its label at the block's top-left, on the block's own title; nothing coordinates the two |
| 6 | Task-10's design: Next from free time or Done early skipped the free time ahead and removed it from the plan. Roni wants the very next block. Reverted in `PlanEditor.startEarly` / `nextStop` (*verified*) |

## Rules

1. Branch `task13` from `master`. First `git add docs app ; git rm docs/Task-12.md ; git commit -m "task13 prep: docs and verified walk clock, calendar layout, Next"`, then one commit per slice: `task13 slice N: <title>`.
2. `docs/AGENT.md` has the shell, Gradle and flow rules; follow them.
3. Add only the tests named here. List every existing test you update or delete.
4. *Verified*, don't edit: `core/walk/WalkClock.kt`, `feature/home/CalendarLayout.kt`, `core/schedule/PlanEditor.kt` and their tests (`WalkClockTest`, `CalendarLayoutTest`, `StartEarlyWalkTest`, `PlanEditorTest`), plus `AlertPlanner.kt`, `RecordEditor.kt`, `StepBook.kt` and their tests.
5. Real names win: if a name here doesn't exist, use the real one and note it in `docs/NOTES.md`.

---

## Slice 1: The walk, with one clock

`WalkClock` (*verified*) is the only place walk time is computed. Every input is **epoch seconds** (`Instant.epochSeconds`); never minutes of the day.

**Home:**
- `walkCountdown`, `walkProgress` and `walkHeadingHome` become one `walkView: StateFlow<WalkView?>`: `WalkClock.view(card.startSec, card.endSec, clock.now().epochSeconds)` on every second tick. The card reads `text`, `progress` and `phase` from it.
- Delete `walkCountdownText`, `walkProgressOf` and `walkHeadingHomeOf`.
- The chip, "to turn" / "to home" and the Turn back button follow `phase`: Turn back only while `OUT`.
- `publish()`: replace the line that clears `walkSheet` with: clear it only when a walk is running (`walkCard != null`). The top-bar icon works in every state again.

**DayOrchestrator:** `turnBack()` uses `WalkClock.turnBackEnd(start, end, now)` for both walk kinds (day-block and standalone). Anything that needs the turn-back moment uses `WalkClock.turnSec`.

**NotificationModel:** the walk texts take their times from `WalkClock` too (turn-back = `turnSec`).

**Tests:** cover every sequence Roni can tap. Use `DayOrchestratorTest` with the fake clock, `WalkRinger` and `StepSensor`, unless noted.
1. `standaloneWalk_turnBackAfterFiveMinutes_isHomeFiveMinutesLater`: evening, start 21:00:00, `turnBack()` 21:05:00 → `walkSession.end` 21:10:00; `onAlarm()` 21:10:05 → `ring(WALK_DONE)`, the walk is logged 21:00–21:10, and `walkSession` is null.
2. `standaloneWalk_secondTurnBack_changesNothing`: after (1)'s turn back, `turnBack()` at 21:06 → false, and the end is still 21:10.
3. `standaloneWalk_endThenStartAgain_isAFreshWalk`: start 21:00, `endWalk()` 21:02 → logged 2 min; `startWalk(30)` 21:02:10 → session 21:02:10–21:32:10.
4. `dayBlockWalk_endThenStartAgain_isAFreshWalk`: `coding_am` 10:00:05, `startWalk(30)`, `turnBack()` 10:01:00, Next 10:02:00, `startWalk(30)` 10:02:30 → the open segment is the new walk (`walk-602`) ending 10:32, and `walkSession` matches it.
5. `walk_survivesProcessDeath`: start a standalone walk, build a new orchestrator on the same prefs, restore → the same `walkSession`.
6. `HomeViewModelTest.walkIcon_opensTheSheet_inTheEvening`: the day in `Evening(1)`, `onAction(HomeAction.Walk)` → `state.walkSheet != null` after the publish.
7. `HomeViewModelTest.walkCard_countsInMinutesAndSeconds`: a standalone walk started 65 s ago (30 min) → `walkView` text "13:55", phase `OUT`. Collect in `backgroundScope` (the flow is `WhileSubscribed`).

---

## Slice 2: The calendar never overlaps

`CalendarLayout.fit(blocks, ticks, DAY_START_MIN)` (*verified*) decides everything. `DayCalendar` only draws what it returns.

**Home:**
- `HomeState.calendarFit: CalendarFit`, computed in `publish()` from `blocks` (id, startMin, endMin) and ticks.
- Ticks are the walk markers:
  - day-block Walk blocks shorter than 10 min, keeping their block id;
  - standalone walks from today's `walks_<date>`, with the id `walk@<startMin>`.
- Pill labels, in `HomeState.pillLabels: Map<String, String>`:
  - a sliver block: "<Title> · N min" ("CV · 1 min");
  - a short walk: "Walked N min";
  - a standalone walk: "Walk · N min".

**DayCalendar:**
- **Slivers:** a bar of the block's accent at 60% alpha, at least 3 dp tall, full block width, no text. Still clickable: it opens what a tap on that block opens.
- **Other blocks:** draw the text at `textTopDp` (relative to the block's top), by `fit`:
  - `TWO_LINES`: title, then the time;
  - `ONE_LINE`: "Title · 20:12";
  - `NONE`: no text.

  Drop the old `blockOneLineMax` logic. The Walk block's "turn" label is drawn only when `fit == TWO_LINES`.
- **Ticks:** a 1 dp line across the block width at the tick's minute, in the walk accent `0xFF8FC7C9`.
- **Pill rows:** for each `PillRow`, a `Row` at `topDp`, starting at the blocks' left edge + 8 dp, 4 dp apart. Each pill is 18 dp tall with fully rounded corners:
  - background `Surface`, a 1 dp border in the item's accent (the block's, or the walk accent), a 6 dp dot of the accent, then the label (`labelSmall`, `textPrimary`, one line, ellipsis);
  - at most 160 dp wide;
  - it sits above the blocks (`zIndex`), and tapping it does what tapping its block does.
- Delete `WalkMarkersOverlay`'s own text.
- History's day recap is a list, not a calendar, so no change there.

**Tests:** `CalendarLayoutTest` (verified) covers the geometry. Add `HomeViewModelTest.oneMinuteBlock_isASliverWithAPill`: CV started 9:00, Next twice at 9:01 (Done early, then Next) → `calendarFit` marks `cv` as a sliver, a pill row holds `cv`, and the label is "CV · 1 min".

---

## Slice 3: Next is the next block; permissions at launch

**Next:** `PlanEditor.startEarly` / `nextStop` (*verified*) no longer skip free time. The orchestrator code from Task-10 stays as it is: it finds the landing block that starts now. The Next labels follow `nextStop`, so they name the very next block ("Next: Free").
- Update the Task-10/12 tests that expected skipping:
  - `next_beforeTheNextBlock_startsItNowWithItsOwnLength` now lands on `free_midday` from 10:02 (until 14:00);
  - `doneEarly_saysFreeTimeAndNamesTheNextBlock` says "Next: Free".
  - List them in the report.

**Permissions:**
- `MainActivity` asks at launch with one `RequestMultiplePermissions`: `POST_NOTIFICATIONS` (API 33+) and `ACTIVITY_RECOGNITION` (API 29+), only the ones not yet granted.
- After the result, call `orchestrator.onAppForeground()` so steps start right away.
- Delete `StepPermissionCard`, `HomeAction.AllowSteps` / `DeclineSteps`, the `step_declined` flag and its reads.
- Settings keeps "Step counting": it asks, or opens the app's system settings once Android stops showing the dialog.

---

## Slice 4: Docs and release 2.6.0

1. `docs/CODEMAP.md` (WalkClock, CalendarLayout, pills), and `docs/NOTES.md` (one line per decision, under 20 KB).
2. `docs/MANUAL_TESTS.md`, "Check on the phone (2.6.0)":
   1. Evening: the top-bar walk icon opens the sheet; Start: the card counts 15:00 down in seconds.
   2. Turn back after 1 minute: "Heading home", counting down from about 1:00.
   3. End walk, then a new walk at once: a fresh "Heading out" from the full length.
   4. A 3-minute walk during Coding: a thin line and a "Walked 3 min" pill; Coding's title isn't covered.
   5. Start CV, after a minute Done early then Next: a thin CV bar with a "CV · 1 min" pill; Coding's text starts below it.
   6. Dishes (20 min) reads "Dishes · 20:12" on one line, nothing cut.
   7. On Coding: Done early, then Next → the free time after Coding starts, not the next Coding; nothing disappears.
   8. Fresh install: notifications and physical activity are asked at launch; no card on Home.
3. `versionName = "2.6.0"`, `versionCode = 20600`. `CHANGELOG.md`:

```markdown
## 2.6.0
- Fixed: the walk timer (it showed 29862554:24), Turn back, and the walk icon in the top bar.
- The calendar never overlaps: very short blocks become a thin bar with a label pill ("CV · 1 min"), short walks a "Walked 3 min" pill, and block titles move out of their way.
- Next goes to the very next block again; nothing is skipped or removed.
- Permissions are asked at launch; the step card on Home is gone.
```

4. Final gate (the `cmd /c` form), merge `task13` into `master`, `git tag v2.6.0`, push `master` and the tag.

## Final report (under 20 lines), then stop.
