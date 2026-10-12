# TASK 12: Walk fixes, a step counter and the evening (2.5.0)

**Target version: 2.5.0** (versionCode 20500). Paths below are under `app/src/main/java/com/roni/blocks/` unless they start with `app/`, `docs/` or a root file name.

## Slice map

Read lines 1–49 once, then each slice's lines when you start it.

| Slice | What | Lines | Start in | Tests |
|---|---|---|---|---|
| 1 | Walk fixes | 51–75 | core/daystate/DayOrchestrator.kt, feature/home/Home.kt, HomeUi.kt, DayCalendar.kt, DayRecap.kt | DayOrchestratorTest |
| 2 | Steps: the day side | 76–105 | new core/steps/StepSensor.kt, DayOrchestrator.kt, core/storage/HistoryManager.kt, AndroidManifest.xml | DayOrchestratorTest |
| 3 | Steps: the screens | 106–127 | feature/home/Home.kt, feature/history/History.kt, feature/weekly/Weekly.kt, feature/settings/Settings.kt | - |
| 4 | The evening | 128–162 | DayOrchestrator.kt, core/schedule/SegmentBook.kt, feature/home/Home.kt, EditCard.kt, HomeSheets.kt | HomeViewModelTest, DayOrchestratorTest |
| 5 | Docs and release | 163–190 | docs/, CHANGELOG.md, app/build.gradle.kts | final gate |

## What Roni found on 2.4.0 (12 Oct)

1. Walk, then "Turn back now" a few seconds later: the card showed 00:00 instead of counting the way home.
2. End walk, then Walk → Start walk again: the card showed 00:00, the ring ticked fast, and it said "Heading home". End walk then started *another* walk at 14:10, "Heading out". A third End walk finally went back to Home.
3. Wish: a walk too short to be a block on the calendar should be a line inside the block it happened in: "Walked 4 min".
4. Wish: count steps all day, every day (not only on walks): a total per day in History, a weekly total in Weekly.
5. Evening: after Kid asleep and picking an activity, there's no Dishes block, and the activity seems to replace the whole kid-time block. He wants: kid time kept as it really was (16:20 → the Kid asleep tap), then Dishes (~20 min, editable, skippable), then the activity, which he can switch mid-evening (an hour of reading, then gaming).

## Causes (checked in the code)

| # | Cause |
|---|---|
| 2 | `DayOrchestrator.startWalk` finds the walk with `blocks.indexOfFirst { it.type == BlockType.Walk }`, which is the **first** walk in today's plan: the one that already ended. The new walk's segment took the old walk's times (already over: 00:00, "Heading home") and index. End walk (Next) then advanced to the next block, which was the new walk |
| 5 | Picking the activity ends Dishes the same second (`SegmentBook.eveningChoice` closes the open segment at now), so picked right away Dishes is 0 minutes and never shows. The activity can't be changed: `DayStateMachine` refuses `EveningChoice` once the choice is made. The kid-time takeover wasn't found by reading the code; Slice 4's first test reproduces his case |
| 1 | `WalkCardUi` keeps whole minutes (`startMin`, `turnBackMin`, `endMin`) and `headingHome` is fixed when Home publishes. A walk turned back within the same minute ends "this minute": 00:00. Countdowns are off by up to 59 s, and the card only switches to "Heading home" on the next publish |

## Steps: how it works (decided with Roni)

- Android's hardware step counter (`Sensor.TYPE_STEP_COUNTER`) counts all the time on a low-power chip, whether the app runs or not. It reports one number: steps since the phone booted. The app doesn't need to stay alive and doesn't count anything itself.
- The app *reads* the number at checkpoints: when it opens, at every alarm (including the midnight one), at boot, and when a walk starts or ends. The difference since the last reading goes to that reading's day. `StepBook` (*verified*) does the arithmetic, restarts included.
- Battery: about zero. No service, no GPS, no listener in the background. While Home is on screen it listens live, for the number in the top bar.
- A restart loses only the steps since the last reading; everything saved stays.
- Permission: "Physical activity" (`ACTIVITY_RECOGNITION`), asked once. A phone without the sensor shows no steps anywhere.

## Rules

1. Branch `task12` from `master`. First `git add docs app ; git rm docs/Task-11.md ; git commit -m "task12 prep: docs and verified StepBook"`, then one commit per slice: `task12 slice N: <title>`.
2. `docs/AGENT.md` has the shell, Gradle and flow rules; follow them.
3. Add only the tests named here. List any existing test you update.
4. *Verified*, don't edit: `core/steps/StepBook.kt` and its test, plus `PlanEditor.kt`, `AlertPlanner.kt`, `RecordEditor.kt` and their tests.
5. Real names win: if a name here doesn't exist, use the real one and note it in `docs/NOTES.md`.

---

## Slice 1: Walk fixes

**The new walk** (`DayOrchestrator.startWalk`): the walk is the block of type `Walk` in the edited plan whose id wasn't in the plan before (`edited.first { it.type == BlockType.Walk && plan.blocks.none { old -> old.id == it.id } }`), and the index is that block's index. Check `walkPreview` and `walkNext` for the same "first walk by type" mistake.

**The walk card counts in seconds:**
- `WalkCardUi` holds `startSec`, `turnSec`, `endSec` (epoch seconds) instead of minutes. Keep the labels (`turnBackAt`, `homeAt`) as they are.
- `walkCountdown` and `walkProgress` compute from `clock.now()` on every second tick:
  - heading out while now < `turnSec`, counting down to `turnSec`;
  - then heading home, counting down to `endSec`;
  - progress = (now − start) / (end − start).
- The chip, the hint ("to turn" / "to home") and the "Turn back now" button follow the same live value. Expose a `walkHeadingHome: StateFlow<Boolean>` next to the countdown, and drop `headingHome` from `WalkCardUi`.
- `HomeViewModel` re-publishes when `day.walkSession` changes (collect it in `init`, like `day.state`), so Turn back, End walk and the walk's own end show at once.

**Short walks on the calendar:**
- A Walk block shorter than 10 minutes isn't drawn as a block. Standalone walks of any length aren't blocks at all.
- Both are drawn as a marker inside whatever block they happened in: a 1 dp line across the block at the walk's start minute, in the walk accent `0xFF8FC7C9`, with a small label "Walked N min" (labelSmall).
- Put the markers in `HomeState.walkMarkers: List<WalkMarkerUi(startMin, label)>`. They come from today's short Walk segments and today's `walks_<date>` log. `DayCalendar` draws them after the blocks.
- History's day recap does the same: a short day-block walk is a one-line note "Walked N min at HH:MM" under the row it interrupted, not a row of its own.

**Tests** (`DayOrchestratorTest`):
- `secondWalk_startsTheNewWalk`: `coding_am` at 10:00:05, `startWalk(30)`; `turnBack()` at 10:01:00 (home at 10:01:55); Next (End walk) at 10:02:00; `startWalk(30)` at 10:02:30 → the open segment is a Walk with `blockId` `walk-602` ending 10:32, and the state's block is `walk-602` (the first walk, `walk-600`, is still in the plan before it).
- `turnBack_afterFiveMinutes_takesFiveMinutesHome`: standalone walk at 21:00:00, `turnBack()` at 21:05:00 → `walkSession.end` is 21:10:00.

---

## Slice 2: Step counting, the day side

**`core/steps/StepSensor.kt`** (new):
- An interface `StepSensor`:
  - `suspend fun read(): Long?`: register a `TYPE_STEP_COUNTER` listener, take the first value, unregister. It returns null after 2 s, with no sensor, or without the permission.
  - `fun live(): Flow<Long>`: a `callbackFlow` that unregisters on close.
- The Android implementation is bound in a Hilt module. The orchestrator takes `stepSensor: StepSensor` with a no-op default (null / emptyFlow) so hand-built orchestrators in tests still compile.

**DayOrchestrator:**
- `private var steps: StepState`, persisted under `step_state` in `PrefsFile.DAY` (never cleared by rollover).
- `suspend fun stepCheckpoint()`:
  - `stepSensor.read()`, then `StepBook.read(steps, counter, today().toEpochDays())`;
  - each `closed` (day, total) goes to `history.setSteps(LocalDate.fromEpochDays(day), total)`;
  - persist; then publish `todaySteps: StateFlow<Long?>` (null while no reading has ever worked).
- Call it from `onAppForeground()`, `onAlarm()` (first thing after restore), `onTimeTravelled()` (boot, clock changes), `startWalk`, `turnBack`, `endWalk` and a walk's own end.
- Walk steps: keep the counter reading at the walk's start (`walkStartCounter`, persisted with the walk). At its end, `StepBook.between(start, end)` goes into the walk's log.

**HistoryManager:**
- `setSteps(date, total)` / `steps(date): Long?` under `steps_<date>`, and `observeSteps(date)`.
- `WalkLog` gains `steps: Long? = null` (the default keeps old logs readable).
- Day-block walks are logged in `walks_<date>` too when they end (start, end, steps), so History lists every walk.

**Manifest:** `<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />`.

**Tests** (`DayOrchestratorTest`, with a fake `StepSensor` whose next value the test sets):
- `steps_addUpThroughTheDay_andCloseAtMidnight`: readings 52,000 at 09:00 (`onAppForeground`), 53,500 at 12:00 (`onAlarm`), 54,000 at 00:00:05 the next day (`onAlarm`) → `history.steps(day 1)` = 2,000 and `todaySteps` = 0.
- `walk_logsItsSteps`: standalone walk at 21:00 (counter 60,000), ended at 21:30 (counter 63,200) → today's walk log has `steps = 3200`.

---

## Slice 3: Step counting, the screens

**Permission, once:**
- On Home, when the sensor exists, the permission isn't granted, and it wasn't declined before (a flag in prefs), show a small card above the calendar:

  > **Count your steps?** The phone already counts them on a low-power chip; Blocks just reads the number. No extra battery.
  > [Allow] [Not now]

- Allow → the system permission dialog. Not now → set the flag; Settings → "Step counting" can ask again later. When the permission is denied for good, that row opens the app's system settings.

**Where steps show:**
- **Home top bar:** under the date, small (`labelSmall`, `textSecondary`): "5,240 steps". It's live while Home is on screen: today's saved total plus `live() − baseline`. Nothing is written from the live value.
- **History day:**
  - the header line: "6,240 steps";
  - each walk line: "Walk · 21:00–21:30 · 30 min · 3,200 steps";
  - a day without a total shows nothing.
- **Weekly:** a `StatTile` "Steps" with "41,300" and the hint "About 5,900 a day" (average over days that have a total). Hidden when the week has none.

No new tests (UI).

---

## Slice 4: The evening, as it really happens

**What it should look like** (Sun/Tue/Wed, Kid asleep tapped at 20:12, Reading picked, switched to Game at 21:32):

```
16:20 ─ Kid time ───────── 20:12
20:12 ─ Dishes ─ 20:32
20:32 ─ Reading ── 21:32
21:32 ─ Game ───── 22:30
```

**Rules:**
- **Kid asleep at T:** kid time ends at T, and Dishes starts with its planned end at T + 20 min (cut at 22:30). The evening sheet opens at once to pick tonight's activity. Dismissing it is fine; the card's "Choose evening" opens it again.
- **Picking an activity during Dishes no longer ends Dishes.** It's stored as `pendingEvening: Choice?` (persisted with the day). The calendar shows it as the planned block from Dishes' end to 22:30.
- **Dishes ends at its planned end:** the picked activity starts by itself, with the normal transition sound. With nothing picked, it's an alert ("Dishes done · pick your evening"), and Dishes runs over like any block until he picks; the activity then starts at the pick.
- **Dishes card buttons:**
  - "Skip dishes": Dishes ends now, and the picked activity starts now (or the sheet opens if none is picked).
  - "Choose evening" / "Change evening": the sheet.
  - "End day".
- **Dishes is editable:** tap its block → its end with −15/−5/+5/+15 (not before now + 5, not past 22:30). The open Dishes segment's `plannedEnd` follows, and so does the alarm.
- **The activity can be switched:** the activity card gets "Switch activity" → the sheet → the current activity's segment ends now and a new `EveningChoice` segment starts now, ending 22:30. In the orchestrator this is `switchEvening(choice)`; don't route it through the state machine. Each activity segment is its own block on the calendar:
  - the open one keeps the id `EVENING_CHOICE_ID`;
  - closed ones are `evening.choice@<startMin>`.

  History shows one row each.
- **Correcting:** past evening rows (Dishes, each activity) can be corrected like other past blocks (end, mark done); the type stays.
- `Home.withEvening` builds the evening from these segments plus the pending activity, and **only cuts the kid-time block, never replaces it**. The first test below reproduces Roni's case. If kid time comes out wrong, find out why there and fix it; don't paper over it in the UI.

**Tests:**
- `HomeViewModelTest.evening_keepsKidTime_thenDishes_thenTheActivity`: Sun/Tue/Wed driven through `onAction`. Kid time from 16:20, Kid asleep at 20:12, Read picked at 20:13 → blocks contain `kid_time` 16:20–20:12, `evening.dishes` 20:12–20:32 (the current block) and the Read block 20:32–22:30.
- `DayOrchestratorTest.dishesEnd_startsThePickedActivity`: the same day, `onAlarm()` at 20:32:05 → `Evening(1)` with an open Read segment from 20:32.
- `DayOrchestratorTest.switchEvening_closesReadingAndStartsGame`: at 21:32 `switchEvening(GAME)` → the Read segment ends 21:32, and an open Game segment ends 22:30.

---

## Slice 5: Docs and release 2.5.0

1. `docs/CODEMAP.md`, and `docs/NOTES.md` (one line per decision, under 20 KB).
2. `docs/MANUAL_TESTS.md`, "Check on the phone (2.5.0)":
   1. Walk, Turn back after 1 minute: the card counts 1 minute home, to the second.
   2. End walk, Walk again at once: a fresh walk "Heading out", 30:00 counting down.
   3. A 3-minute walk during Coding: the calendar shows a "Walked 3 min" line in Coding, not a tiny block.
   4. First launch: the step card; Allow. Walk around the flat: the top bar's number goes up while you watch.
   5. Next morning: History → yesterday shows its steps; Weekly shows the week's total.
   6. Restart the phone, open the app: today's steps are still there (only the steps since the last reading before the restart are missing).
   7. Evening: Kid asleep → pick Read → the calendar shows kid time until the tap, Dishes 20 min, then Reading; Reading starts by itself when Dishes ends.
   8. +15 on Dishes moves Reading later; Skip dishes starts Reading now.
   9. Switch activity to Game after a while: Reading and Game are two blocks, and two rows in History.
3. `versionName = "2.5.0"`, `versionCode = 20500`. `CHANGELOG.md`:

```markdown
## 2.5.0
- Steps: Blocks reads the phone's step counter (no extra battery) and shows today's steps on Home, each day's total in History, each walk's steps, and the week's total in Weekly.
- Fixed: a second walk right after the first showed the old walk's times, and End walk started another walk.
- Fixed: the walk card counts to the second; Turn back counts the real way home.
- Short walks show as a "Walked N min" line inside the block, not as a tiny block.
- Evening: kid time stays as it was, then a 20-minute Dishes block (editable, skippable), then your activity, which starts by itself and can be switched during the evening.
```

4. Final gate (the `cmd /c` form), merge `task12` into `master`, `git tag v2.5.0`, push `master` and the tag.

## Final report (under 20 lines), then stop.
