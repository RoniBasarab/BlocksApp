# TASK 9: Presence, Done early and editing every block (2.2.0)

**Target version: 2.2.0** (versionCode 20200). This file overrides older docs where they differ. Paths below are under `app/src/main/java/com/roni/blocks/` unless they start with `app/`, `docs/` or a root file name.

## Slice map

Read lines 1–64 once (map, findings, decisions, rules), then each slice's lines when you start it.

| Slice | What | Lines | Start in | Tests |
|---|---|---|---|---|
| 0 | Prep | 66–76 | git, DayCalendar.kt, EditCard.kt | - |
| 1 | Quick fixes | 77–90 | res/layout/widget_now.xml, core/time/Ticker.kt, feature/home/Home.kt, HomeSheets.kt | WidgetLayoutTest |
| 2 | Done early | 91–117 | feature/home/Home.kt, core/notifications/NotificationModel.kt, core/schedule/ScheduleEngine.kt | NotificationModelMapperTest, HomeViewModelTest, ScheduleEngineTest |
| 3 | Sounds | 118–144 | core/daystate/DayOrchestrator.kt, core/notifications/NotificationRenderer.kt | DayOrchestratorTest, NotificationRendererTest |
| 4 | Tap any block | 145–175 | DayOrchestrator.kt, feature/home/Home.kt, EditCard.kt, HomeSheets.kt | DayOrchestratorTest |
| 5 | Docs and release | 176–211 | docs/, CHANGELOG.md, app/build.gradle.kts | final gate |

## What Roni found on 2.1.0 (11 Oct, on the phone)

1. The widget says "Can't load widget".
2. The Next button always says "Next: Free", whatever comes next.
3. Next on Coding sometimes jumped inside the Pomodoro, or back to the first focus round.
4. The app is too quiet: block changes go unnoticed, and the notification seems to update only when he opens the app.
5. The notification should show the Pomodoro round and how long until the break.
6. After "Real time" in Settings, a free block showed 139 min left for a few seconds before 79.
7. Tapping a past block shows a Toast that's cut off. Use a dialog.
8. He can't change the current block or past blocks at all, only from History.
9. History corrections need −15/+15 as well as ±5.
10. Changing the alert sound doesn't seem to change anything.

## Causes (checked in the code, 11 Oct)

| # | Cause |
|---|---|
| 1 | `res/layout/widget_now.xml` uses a plain `<View>` for the accent strip. Widgets accept only RemoteViews classes, so the whole layout fails to load |
| 2, 3 | Next on a work block before its end opens free time for the rest of the block (`SegmentBook.next` → `SYNTHETIC_FREE`) but keeps the same block index, so Home still shows the work block. The notification anchors the Pomodoro on the open segment, which is now the free time, so it restarts at Focus 1; Home's calendar loses the real start and redraws the phases from the planned start |
| 4 | The block-end alarm calls `sideEffects()` with `transition = false`, so the notification updates silently: nothing ever sounds unless Roni taps Next himself while the app is in the background. Pomodoro wake-ups (`ScheduleEngine.nextBoundary`) count from the planned start while the notification counts from the real start, so after a late start they wake at the wrong times |
| 5 | The running notification shows only "until HH:MM" |
| 6 | `Ticker.minutes` sleeps until the next whole minute and doesn't see the debug clock move |
| 7 | `HomeScreen` shows `HomeViewModel.messages` as a Toast |
| 8 | `PlanEditor.editMode` makes past blocks `NONE`, and the current block `NONE` unless it's an unfinished work block (then end only) |
| 9 | `CorrectBlockSheet` only has ±5 |
| 10 | Nothing ever made a sound (cause 4). The picker does change the `day` channel; the debug and release apps each have their own |

## Decisions (Roni, 11 Oct)

| Topic | Decision |
|---|---|
| Next before the end of a work block | Unchanged: free time until the block's planned end. Shown clearly: Home and the notification say "Free time · Done early", name the real next block, and the Pomodoro stops. The button on a running work block says "Done early" |
| Editing | Tap any block of today. Past block: correct what happened right away (the 2.1.0 correction sheet). Running block: its end (−15/−5/+5/+15), and its type if it's an unfinished work block. Later blocks: as in 2.1.0, with ±15 too |
| Running free time | Ending it sooner starts the next work block sooner (it grows); ending it later pushes the next blocks |
| Sounds | One sound 5 minutes before a running block ends, at the end, then every 5 minutes until Roni reacts (taps anything in the app or the notification, or opens the app). One sound at each Pomodoro break and focus start. Alerts sound even with the app open; edits stay silent |
| No foreground service | Not now. Fix the silent alarms first; revisit only if alerts still come late |
| No banner, no vibration | Banners weren't picked; the no-vibration rule stands |

## Rules

1. Branch `task9` from `master`. One commit per slice: `task9 slice N: <title>` (plus one `task9 prep` commit in Slice 0).
2. `docs/AGENT.md` has the shell, Gradle and flow rules; follow them.
3. Add only the tests named here. When an existing test checks something this task changes on purpose, update or delete it and list it in the report.
4. Code marked *verified* is already in the repo, compiled and tested: commit it, don't edit it. Verified: `core/schedule/PlanEditor.kt`, `core/schedule/AlertPlanner.kt` and their tests.
5. Real names win: if a name here doesn't exist, use the real one and note it in `docs/NOTES.md`.

---

## Slice 0: Prep

Roni has put the new docs into `docs/` and the four verified files into `app/`.

1. `git add docs app/src/main/java/com/roni/blocks/core/schedule app/src/test/java/com/roni/blocks/core/schedule ; git rm docs/Task-8.md ; git commit -m "task9 prep: docs and verified planners"` (Task-8 is done; git keeps it).
2. `PlanEditor` now has `BlockEdit.END_AND_TYPE`. Make the app compile: in `feature/home/DayCalendar.kt` (two `when`s) and `feature/home/EditCard.kt`, treat `END_AND_TYPE` exactly like `END_ONLY` for now. Slice 4 gives it the type chips.
3. Run the full suite. Tests that assert the old rules (the running block's type was locked; running free time and a block finished early had no edits) are expected to fail: update them to the new rules or delete them, and list them.
4. Commit `task9 slice 0: prep`.

---

## Slice 1: Quick fixes

**Widget.** In `res/layout/widget_now.xml` replace the `<View android:id="@+id/widget_accent" …/>` with an `<ImageView>` with the same id, size and background. `setInt(…, "setBackgroundColor", …)` keeps working.

**Test** (`app/src/test/.../core/widget/WidgetLayoutTest.kt`, Robolectric like `ComposeSmokeTest`): `widgetLayout_inflatesAsRemoteViews`: `RemoteViews(context.packageName, R.layout.widget_now).apply(context, FrameLayout(context))` doesn't throw. If Robolectric can't apply RemoteViews at all, delete the test and note it.

**Clock.** `core/time/Ticker.kt`: `minutes` checks the clock every second and emits only when the minute changes (`distinctUntilChanged()`), so a debug-clock jump shows within a second. Keep `seconds()` as is.

**Locked taps.** `HomeScreen` collects `viewModel.messages` into an `AlertDialog` (the message, one "OK" button) instead of a Toast. Remove `android.widget.Toast` from `Home.kt`.

**Correction steps.** `CorrectBlockSheet` (`feature/home/HomeSheets.kt`): four buttons −15, −5, +5, +15 (one row, equal weight). Same `RecordEdit.SetEnd` calls.

---

## Slice 2: Done early, shown clearly

"Done early" means: the open segment is `SYNTHETIC_FREE` while the state is `Running`. The block index doesn't change; that stays.

**Home** (`feature/home/Home.kt`, `publish()` and `nowCard()`):
- `val openAny = day.segments.value.lastOrNull { it.actualEnd == null }`; `doneEarly = dayState is DayState.Running && openAny?.kind == SegmentKind.SYNTHETIC_FREE`.
- Done early: NowCard title "Free time", type `BlockType.Free`, chip "Done early" (`ChipTone.NEUTRAL`), subtitle "until HH:MM · then <next title>" (the free time's `plannedEnd`; drop "· then …" when there's no next block), no progress bar. Buttons: `NEXT` "Next: <next title>" (just "Next" when there is none), `EMERGENCY`.
- Done early: the current calendar block gets no Pomodoro phases and the subtitle "Done early · free until HH:MM".
- A running work block that hasn't run over: the `NEXT` button says "Done early" (it was "Next: Free"). Once it has run over, it stays "Next: <next title>".
- Non-work blocks keep "Next: <next title>".

**Notification** (`core/notifications/NotificationModel.kt`, the running case):
- Done early (`timeline.open?.kind == SegmentKind.SYNTHETIC_FREE`): key `"$id:early"`, title "Free time · <block name> done early", text "until HH:MM · then <next name>", `countdownTo` = the free time's `plannedEnd`, actions: "Next: <next name>" (`START_NEXT`). No Pomodoro.
- Work block, not over, not on a break: actions "Pause", "Done early" (`START_NEXT`). The break case gets the same "Done early".
- Pomodoro focus: title "<name> · Focus i/T", text "Break at HH:MM · ends HH:MM", `countdownTo` = the break start. Pomodoro break: title "<name> · Break", text "Focus i+1/T at HH:MM · ends HH:MM", `countdownTo` = the focus start. Take the times from `AlertPlanner.pomodoroBoundaries(open.actualStart, end, focus, break)`: the first boundary after now. When none is left before the end, text "until HH:MM · then <next name>" and `countdownTo` = end.
- Any other running block: text "until HH:MM · then <next name>" (just "until HH:MM" without a next block).

**Wake-ups.** `ScheduleEngine.nextWakeUp` gets `anchor: Instant? = null`; `nextBoundary` starts its Pomodoro cursor at `anchor ?: block.plannedStart`. `DayOrchestrator.sideEffects()` passes the open PLANNED segment's `actualStart`. When done early, pass the Pomodoro-free path: wake at the free time's `plannedEnd`.

**Tests:**
- `NotificationModelMapperTest.doneEarly_showsFreeTimeAndTheRealNextBlock`: Sun/Tue/Wed, CV started 9:00, Next at 9:20, now 9:21 → title starts "Free time", text "until 09:30 · then Coding", one action "Next: Coding".
- `NotificationModelMapperTest.pomodoro_showsTheRoundAndTimeToBreak`: `coding_am` started 9:40, now 10:00 → title "Coding · Focus 1/2", text "Break at 10:30 · ends 11:30", `countdownTo` 10:30.
- `HomeViewModelTest.doneEarly_saysFreeTimeAndNamesTheNextBlock`: CV, Next at 9:20 → NowCard title "Free time", chip "Done early", primary button "Next: Coding"; the CV calendar block has no phases.
- `ScheduleEngineTest.nextWakeUp_followsTheRealStartOfAPomodoroBlock`: `coding_am` with anchor 9:40, now 10:00 → 10:30.

---

## Slice 3: Sounds that get noticed

`core/schedule/AlertPlanner.kt` (*verified*) decides when: `next(...)` gives the next alert after a time, `due(...)` the one that just fell, and `pomodoroBoundaries(...)` the break and focus starts. Times are epoch seconds (`Instant.epochSeconds`).

**DayOrchestrator:**
- `reactedAt: Instant?`, saved with the day (`persist()` and restore, like the state). It's reset on rollover and set to now on every user action: `send`, `handle`, `handleNotificationAction`, `editPlan`, `correctPast` (Slice 4), and a new `onAppForeground()`. Internal transitions (alarms, catch-up) never set it.
- `onAppForeground()`: called when the app comes to the foreground (one `ProcessLifecycleOwner` observer, e.g. in the Application class, the same signal `ProcessAppVisibility` reads). It sets `reactedAt`, persists, and calls `sideEffects()` so a pending nag is dropped.
- Alert inputs exist only in `Running` with an open PLANNED or `SYNTHETIC_FREE` segment: start = its `actualStart`, end = its `plannedEnd`, boundaries = `pomodoroBoundaries(...)` when the block has a Pomodoro and the segment is PLANNED, else empty. Paused, kid time, the evening and the day's end: no alerts.
- `sideEffects()`: the alarm goes off at the earlier of `engine.nextWakeUp(...)` and `AlertPlanner.next(..., afterSec = now)`.
- `onAlarm()`: after the missed transitions (unchanged), `val due = AlertPlanner.due(..., nowSec = now)`, then `sideEffects(alert = due != null)`.
- Nags repeat only while the block has run over and `reactedAt` is before its end. The day's own transitions (kid time, 22:30) end them anyway.

**Notification:**
- `NotificationPort.render(model, transition, alert: Boolean = false)`; `FakeNotifications` records it.
- `NotificationRenderer`: `dismissals.forget(model.key)` when `transition || alert`; `.setOnlyAlertOnce(!(alert || (transition && !visibility.isForeground)))`, so an alert always makes a sound, even with the app open. Edits and refreshes stay silent.
- The 5-minute warning needs no special text: the running notification already says "until HH:MM · then <next>" and counts down. Nags show the existing "ran over" notification.
- Settings → "Alert sound": subtitle "Block changes, the 5-minute warning and reminders".

**Tests:**
- `DayOrchestratorTest.alarm_fiveMinutesBeforeTheEnd_alerts`: `coding_am` running, clock 11:25:10, `onAlarm()` → the last render has `alert = true`.
- `DayOrchestratorTest.nags_stopOnceRoniReacts`: run over; `onAlarm()` at 11:35:05 → alert; `onAppForeground()` at 11:36; `onAlarm()` at 11:40:05 → no alert, and the scheduled alarm isn't 11:45.
- `NotificationRendererTest.alert_makesASound_evenInTheForeground`: foreground, `render(model, transition = false, alert = true)` → the posted notification doesn't have `FLAG_ONLY_ALERT_ONCE`.

Note in `docs/NOTES.md`: in deep Doze Android allows an exact alarm about every 9 minutes, so a nag can come late while the phone lies still. If Roni finds them late, the next step is `setAlarmClock` for alerts or a foreground service.

---

## Slice 4: Tap any block of today

**Rules** (`PlanEditor`, *verified*):
- The running unfinished work block: `END_AND_TYPE`, meaning end only (`Resize` BOTTOM) plus `Retype` to another work type. Retyping it to Free is refused: that's "Done early".
- The running block in any other case (free time, done early, other editable types): `END_ONLY`. For running free time, ending it sooner starts the next work block sooner (the block grows); later pushes the next blocks.
- Later blocks: unchanged. Blocks before the running one: still `NONE` in `PlanEditor`; they're corrected instead (below).

**DayOrchestrator:**
- `editPlan`: after a commit, the running block's open segment follows the new plan:
  - an open PLANNED segment with `blockId == current.id` takes the new `plannedEnd` (as now) and the new `type` when the block was retyped;
  - an open `SYNTHETIC_FREE` segment takes the running block's new end as its `plannedEnd`.
- New `liveRecord(): DayRecord`: today's date, template, `book.segments`, `book.flags`, `plannedBlocks` = today's plan.
- New `correctPast(edit: RecordEdit): Boolean`: `RecordEditor.apply(liveRecord().toRecordRows(zone), edit, limitMin)`, where `limitMin` is the open segment's start minute (now when nothing is open). Fold it back with `withRows(...)`, set `book` and `_segments` (the flags get `CORRECTED`), set `reactedAt`, persist, `sideEffects()`. It works in every state that has segments, kid time and the evening included.

**Home:**
- A tap on a block before the running one (or any day block once kid time or the evening started on a Sun–Thu day) opens `CorrectBlockSheet` for that block: the row comes from `DayRecapMapper.map(day.liveRecord(), use24)`, matched by block id; `onEdit` calls `day.correctPast`. A row whose `fix` is `NONE` shows the kid-time message instead.
- Past blocks show what happened as their calendar subtitle, from the same live rows: "Done HH:MM–HH:MM" or "Missed".
- `EditCard`:
  - `END_AND_TYPE`: work-type chips (CV, Coding, Gym; no Free) plus the end stepper. Caption "This block is running. Change when it ends or what it is; Done early ends it now."
  - `END_ONLY`: the end stepper. Caption for free time: "End free time sooner to start <next> sooner." Done early: "<Type> finished early; this is free time until HH:MM."
  - Every stepper steps −15, −5, +5, +15 (`HomeAction.StepEdge` with ±15 too).
- Delete the messages nothing triggers any more (`LOCKED_OVER`, `LOCKED_FINISHED_EARLY`, `LOCKED_FREE_RUNNING`). The kid-time, pick-a-template, will-miss and Fri/Sat kid-time ones stay.

**Tests** (`DayOrchestratorTest`):
- `correctPast_fixesAnEarlierBlockWhileTheDayRuns`: Sun/Tue/Wed, CV 9:00, Next at 9:35, then correct CV's end to 9:25 → CV's segment ends 9:25, the flags contain `CORRECTED`, the state is still `Running` on `coding_am`.
- `correctPast_neverReachesTheRunningBlock`: the same day, correct CV's end to 9:50 → it ends at 9:35 at most.
- `editPlan_retypesTheRunningBlock`: `coding_am` at 10:00, `Retype` to CV → the plan block and the open segment are CV, the index is unchanged.
- `editPlan_doneEarly_movesTheFreeTimesEnd`: CV, Next at 9:20, `Resize` CV BOTTOM to 9:45 → the open free time's `plannedEnd` is 9:45, and `coding_am` is 9:45–11:45.

---

## Slice 5: Docs and release 2.2.0

1. `docs/CODEMAP.md`: rows for `AlertPlanner.kt` and the new orchestrator functions; update `PlanEditor` and `widget_now.xml`.
2. `docs/NOTES.md`: your decisions in one line each. Keep it under 20 KB (condense older entries if needed).
3. `docs/MANUAL_TESTS.md`: replace the top checklist with "Check on the phone (2.2.0)":
   1. Add the widget: it shows the block and a countdown.
   2. Developer tools: move the clock +1 hour, then Real time: Home's minutes left correct themselves within a second.
   3. Tap kid time: a dialog says why, in full.
   4. On Coding, tap Done early: the card and the notification say "Free time · Done early … then <next>"; the Pomodoro doesn't restart.
   5. Coding's notification: "Focus 1/2", "Break at …", counting down to the break.
   6. Lock the phone 6 minutes before a block ends: a sound at 5 minutes left, at the end, and every 5 minutes until you open the app.
   7. Settings → Alert sound: pick another sound; the next alert uses it.
   8. Tap the running block: change its type and its end (±15).
   9. Tap a past block today: move its end; the calendar shows "Done HH:MM–HH:MM".
   10. History: correcting has −15 and +15.
   11. Release app (`com.roni.blocks`): pick a template, swipe the app away, reopen: the day is still there.
4. `versionName = "2.2.0"`, `versionCode = 20200`. `CHANGELOG.md`:

```markdown
## 2.2.0
- Sounds that get noticed: 5 minutes before a block ends, at the end, and every 5 minutes until you react; Pomodoro breaks too.
- Done early: the screen and the notification show your free time and the real next block; the Pomodoro no longer restarts.
- The notification shows the Pomodoro round and the time to the break.
- Tap any block of today: fix past blocks right away, change the running block's end and type, ±15 steps.
- Fixed: the widget wouldn't load.
- Fixed: after moving the debug clock, minutes left were wrong for up to a minute.
- Locked blocks explain why in a dialog instead of a cut-off message.
```

5. Final gate, in the `cmd /c` form from AGENT.md: `gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin assembleDebug assembleRelease :app:assembleBenchmark :macrobenchmark:assembleBenchmark`.
6. Merge `task9` into `master`, `git tag v2.2.0`, push `master` and the tag.

## Final report (under 30 lines), then stop

Each slice: done or not. Tests added, changed, deleted. Names you adapted. Version, tag, push result. After the report, stop: don't re-check finished slices or old todo lists.
