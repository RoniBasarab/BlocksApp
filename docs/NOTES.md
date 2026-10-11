# Notes - decisions the task files left unspecified, and what the code does instead

## Earlier tasks (condensed)

NOTES.md crossed 20 KB, so the Task-1..7 entries were condensed here. Still live rules first,
then the decisions a future agent needs.

- **Restore is idempotent, single-flight, and always completes.** `DayOrchestrator.ensureRestored()`
  takes a mutex and sets `_isRestored` in a `finally`; every entry point (the UI gate, `send`, each
  receiver) awaits it first, so a throwing restore can't strand the app.
- **`onTimeTravelled()` is new, and deliberately not `onTimeChanged()`** (Task-6.md:376 expects a
  20:05 jump to reach `KidTime`; `onTimeChanged()` never applied missed transitions). It replays the
  skipped transitions exactly like a cold start and also applies the date rollover (extracted
  `applyRollover()`); `SystemEventsReceiver` calls it, `AlarmReceiver` keeps `onAlarm()` (exactly one
  transition, late alarms still apply it). All replay loops are bounded by `MAX_REPLAY_STEPS`.
- **The old splash bug was a write/read form mismatch in `DayStateSerializer`**, not a race: direct
  `serialize` calls produced the POLY_OBJ array form while reads expected the object form. The
  wrapper now delegates via `encodeSerializableValue`/`decodeSerializableValue` and decodes legacy
  array payloads with a private `arrayJson` (`ignoreUnknownKeys`, `useArrayPolymorphism`) before
  throwing `SerializationException`.
- **Test harness:** `PrefsStorage` takes `@IoDispatcher`; tests pass `UnconfinedTestDispatcher()` so
  every write commits before the assert. `app/src/test/resources/robolectric.properties` sets
  `application=android.app.Application`: unit tests get a plain Application, no production Hilt
  graph, no background restore racing the seed. Race tests use `Dispatchers.Default` +
  `withTimeout(5_000) { .first { } }` (a test dispatcher serializes the race away).
  `@IoDispatcher`/`@ApplicationScope` qualifiers live in `core/di/CoroutinesModule.kt`.
- **Today's plan (`TodayPlan(templateId, blocks, edited)`) is the 2.0.0 data model**: the
  orchestrator owns it (`todayPlan` StateFlow), `timelineAt` reads it, swap rebuilds via
  `SegmentBook.swapPlan` (old plan kept up to the cutoff with its last block cut there; the new
  template's block containing `now` restarts at `now`; `<id>@<startMin>` suffixes keep ids unique).
  1.5.1 saves migrate via `migratePlan` + `repointBlockIndex`; `PLAN_STARTS_AT` pref is gone.
  `HistoryManager.observeDay` is version-driven (`version.map { readDay(date) }.distinctUntilChanged()`),
  so callers must share the singleton `HistoryManager` (`HomeViewModel` dropped its `delay(10)`
  retry poll for it).
- **Weekly** renders `rows: List<PlanProgressUi>` (planned minutes from `day.plannedBlocks ?:
  template.blocks`, clipped at the emergency stop; actual from PLANNED + SYNTHETIC_FREE segments)
  with a `missedSummary` line; the chart card uses `Spacing.l` (16dp), not `m` — too tight once each
  row carries a label, bar and value (real-name deviation from the obvious token).
- **Time Debt is gone** (Task-7 Slice 4): `DebtRecord`, `writeDebt/readDebt` and the `debts`
  aggregate parameter were deleted; Weekly/Insights/History compute `debtMinutes(segments)` directly
  (a top-level pure function in `HistoryManager.kt`). Explainer term "Ran over (time debt)" is plain
  "Ran over". `debtMinutes` stays.
- **DayRecap** rows anchor on the record's `plannedBlocks` (older records fall back to their own
  PLANNED segments); a plan block with no segment isn't listed; summary = worked(non-paused)
  CV/Coding/Gym ÷ planned, capped 100%, planned clipped at the emergency stop; chips Ran over /
  Paused / Missed n block(s) / Plan changed / Template swapped.
- **Pomodoro**: the spec's new `PomodoroPhase` collides with the engine's existing
  `PomodoroPhase(focusing, index, total, minutesLeft)`, so the new type is `PomodoroSlot`.
  `HourHeight` 64→80dp (`DayCalendar.kt`); `phaseLabel`/`duration` are pure top-level helpers in
  `CalendarMapper.kt`; break bands draw in `drawWithContent` and read `minuteOfDay()` in the draw
  phase only, so `HomeRecompositionTest.minuteTick_recomposesNothing` stays green.
- **Plan editing** (`core/schedule/PlanEditor.kt` + `PlanConversion.kt`): `DayOrchestrator.editPlan(PlanEdit)`
  is Running/Paused-only, edits in memory then `persist()` once + silent `sideEffects()`
  (`RecordingNotificationPort` also records each render's `transition` flag);
  `PLAN_EDITED` comes from `todayPlan.edited` at flush, never saved separately;
  `currentForEditing()` reports `endEditable = false` for `SYNTHETIC_FREE`. Calendar editing: tap
  selects, five `HomeAction` editing messages (`DragEdge/DragDone/DragCancelled/StepEdge/Retype`),
  the handles are separate children of the block's `Layout` at `zIndex(2f)` so the selected block
  can't steal the drag, `EdgeHandle` is byte-for-byte the spec's sample, springs use a
  `mutableStateMapOf<String, Pair<Animatable,Animatable>>`; the edit card is its own file
  `EditCard.kt`; type tags use the data-object names (`Cv`/`Coding`/`Gym`/`Free`) because
  `BlockType` is a sealed interface with no `.name`; auto-scroll while dragging is skipped (the
  steppers cover long moves).
- **Demo data** (28 days, count unchanged): a late-start day (replays from 10:40 →
  `MissReason.LATE_START` on CV) and an edited day built through the same pure pieces
  (`editedTemplate` → `PlanEditor.apply` → `toBlockSpecs`); both are Sun/Tue/Wed so the gym-missed
  cadence is untouched (deviation: the spec never names the edited day's template). `PLAN_EDITED` is
  added only when `PlanEditor` actually returned a plan, so a refused edit can't mislabel a record.
- **Naming/doc facts**: `TimelineQueries.kt` is a file of `ScheduleEngine` extension functions, not
  a class; there is no `InsightsManager` (it's `InsightsViewModel`); Settings copy stays as inline
  literals rather than `strings.xml` (roni's call — deliberate deviation from Task-6 wording);
  `SettingsRow.enabled` gates the developer rows off in release while leaving them visible in debug;
  the emergency "Stop today" confirmation is always shown and only Pause supports "Don't ask again";
  `NowButtonId.EMERGENCY` renders as its own centered `now_emergency` TextButton so Pause · Next stay
  equal-width 48dp; `TimeFormatter.label/hourGutter` are the single 12/24h source of truth;
  `BlockType.isWork` (CV/Coding/Gym) lives in `Model.kt`; the renderer-test recording sink uses
  `CopyOnWriteArrayList` (renders resume on IO workers).
- **Test count baseline**: Task-7 ended at 211 tests. Task-8 Slice 1 removed Roborazzi and the
  screenshot/layout-only tests (`ScreenshotsTest`, `UiExpectationsTest`, `ExampleUnitTest`,
  `ExampleInstrumentedTest`); `DebugTimeControllerTest.evening_canBeReachedWithoutWaiting` is a
  pre-existing flaky test owned by the evening slices — passing in every recent full-suite run.

## Slice 3: weekly keeps its arrows

- `WeeklyUiState.canGoPrevious` is `selected > earliest week of any stored day` (none stored → false); the previous arrow is disabled with it and `onPreviousWeek()` returns early, so the empty-week screen can no longer walk back past the first recorded week.
- The `!hasData` branch now renders `WeekSelector` above `EmptyState` inside a plain `Column(fillMaxSize())` (not `ScreenColumn`) because `EmptyState` itself calls `fillMaxSize`, and its body copy became "Use the arrows to see other weeks." (the old "Finish a day and it shows up here." was wrong once the arrows stay).

## Slice 4: developer tools in one dialog

- **Moving the clock back does not rewind what the day already recorded** (spec-mandated note): `DebugClockShift`/`DebugClockTo` only move the clock; blocks that already completed are not deleted, and the state machine does not walk back a step, so the run board keeps showing what happened.
- All six debug groups moved off the Settings list into one `DeveloperToolsDialog`, opened from a single "Developer tools" row. It renders in `SettingsRoute` next to `ConfirmDialog`; "Start test day" closes it, and the ViewModel's `SettingsEffect.StartedTestDay` is turned into `onOpenHome()` at the route (AppNav passes `openTab(HomeKey)`), so you land on the running day.
- `DebugTimeController.testDay(template, hhmm)` = `setTo(hhmm)` then `resetToday()` then `StartDay(template)`; returns false if the time is outside the kid-day window.
- `formatShift` now renders negatives with a real minus: `"−1h 30m"`, `"−10m"` (was `"-1h 30m"`); under an hour omits the hours part.
- Deviation from the file's sketch: the preset chip is `DebugChip` (was `ClockChip`), and the already-dead `ClockChips`/`DemoBusyTrailing` were deleted (orphaned when the demo rows left the list); a raw `DebugButton` overload was added so the ±15 min stepper reuses the same button style.
- Test: `DebugTimeControllerTest.testDay_startsTheTemplateAtTheChosenTime` (Running on `SUN_TUE_WED`, on `sun_tue_wed.coding_am`, clock within a minute of 10:40). Full suite 197 tests, 1 failed — still the pre-existing `evening_canBeReachedWithoutWaiting`.

## Slice 5: add a block, and say why a block is locked

- The `TYPE_ONLY` edit card (a later free block) gained an "Add a block:" row with CV · Coding · Gym; it sends `HomeAction.Insert(id, type)` → `orchestrator.editPlan(PlanEdit.Insert(id, type))`. The caption is now "Pick a type to turn this free time into a block, or add a 30-minute block at its start."
- After an insert, the new block is selected so its handles show. The id travels back on `HomeState.selectId`; `HomeScreen` consumes it in a `LaunchedEffect(state.selectId)`. The value is found by matching the inserted type at the block's computed start, which covers both `edit-N` (a piece was carved off) and the free block's own id (the whole block was retyped).
- Locked taps: a block whose `edit == NONE` no longer just fails to select — `HomeScreen` sends `HomeAction.LockedBlockTapped(id)`. `lockedReason()` (a pure private function beside `currentForEditing()`'s callers, not in a composable) mirrors `PlanEditor.editMode`'s refusals, most specific first, and returns one of the seven messages. `HomeViewModel` exposes them as a `SharedFlow<String>`; `HomeRoute` shows each as a short `Toast`.
- The "once kid time or the evening started on a Sun–Thu day" vs "while the day is in kid time (Fri/Sat morning)" split is decided by the template (`todayPlan.templateId`), because `DayState` alone can't tell morning kid time from evening kid time.
- Tests: `HomeViewModelTest.insertIntoFreeTime_selectsTheNewBlock`, `lockedTap_onAPastBlock_saysItsOver`, `lockedTap_onKidTime_saysKidTimeCannotMove`.

## Slice 6: correct a past day

- `DayFlag.CORRECTED` added; `core/schedule/RecordConversion.kt` is the spec's `toRecordRows`/`withRows` verbatim; `HistoryManager.correctDay(date, edit, zone)` reads the day, runs `RecordEditor.apply` (default `LAST_MINUTE` limit), writes the corrected record, then rebuilds that week's aggregate from the stored days of the week. False when the editor returns null.
- **Deviation, one extra field:** `RecapRow` carries `segmentIndex` and `fix` as specified, plus `endMin: Int?` — the sheet needs the numeric end to render "Ended at HH:MM" and to step it by ±5; the spec names only the two fields. Null for stop/evening-skipped rows and missed rows.
- `DayRecapMapper` now takes a happened row's type, title and worked minutes from the segment (`happened.type`), not the plan block, so a "What did you do?" retype shows in the recap; fix comes from `RecordEditor.fixMode` on the record's own rows. "Corrected" (NEUTRAL) chip added when the day carries the flag.
- Both recap screens refresh on their own: `HistoryDetailViewModel` switched from one-shot `readDay` to `observeDay(date)` (a `Job` is cancelled on reload); Home's ended recap already observed today's record. Both routes own a `ModalBottomSheet` `CorrectBlockSheet` (HomeSheets.kt) opened by tapping a fixable row; `RecapRowCard` is tappable exactly when `fix != NONE`, `RecapList` forwards taps.
- Sheet interpretation of "each tap commits at once": the ±5 stepper and the type chips call `onEdit(...)` immediately (SetEnd / Retype), keeping a local end value so the label updates without waiting for the re-emit; "Done" only dismisses. `MARK_DONE` commits and closes. Home commits via `HomeViewModel.correctRecord` (date = today); History via `HistoryDetailViewModel.correct`.
- Spec-mandated note: if today's record is corrected and the day is then restarted with a swap afterwards, the next flush rewrites the record — acceptable, the correction is a record edit, not a plan edit.
- Test: `CorrectDayTest.correctDay_marksAMissedBlockDone_andFlagsTheDay` (Monday record: Coding 09:30–12:17, `free_pre_gym` consumed 11:30–12:00, Gym 12:17–14:01; MarkDone → free is PLANNED 11:30–12:00 with no miss reason, Coding ends 11:30, `CORRECTED` in flags, week aggregate rewritten with Coding = 120m). Full suite green (`exit=0`, includes the previously-flaky evening test).

## Slice 7: evening check-in

- `CheckIn.GOOD/OK/ROUGH/SKIPPED` in `Model.kt`; `DayRecord.checkIn: CheckIn? = null` (serialized with a default, so old stored records read back with null). `HistoryManager.setCheckIn` is read-copy-writeDay only — no week rebuild, check-in isn't aggregated.
- The check-in card lives inside `RecapList` as the first item (`recap_checkin` tag) when the mapped day isn't an Off day and hasn't answered; heading comes from a new `checkInTitle` param on `DayRecapMapper.map` (default "How was today?", History detail passes "How was this day?"). One tap stores it and the card disappears on the observeDay re-emit.
- Chips after answering: "Felt good" (GOOD), "Felt OK" (NEUTRAL), "Felt rough" (MUTED); `SKIPPED` shows nothing, and the History list row suffix (a `checkInWord` helper) skips it too — "· Good" / "· OK" / "· Rough" after the template name.
- Positional-args casualty: `DayRecord` gained `checkIn` between `flags` and `plannedBlocks`, so `DayOrchestrator.flush`'s positional `DayRecord(...)` call had to name `plannedBlocks`.
- Test: `CorrectDayTest.checkIn_isStoredOnTheDay` (`setCheckIn(monday, GOOD)` → `readDay.checkIn == GOOD` and the recap's chips contain "Felt good"). Full suite green.

## Slice 8: home-screen widget

- `core/widget/BlocksWidget.kt`: `@AndroidEntryPoint` AppWidgetProvider following the alarm-receiver shape (`goAsync` + `withTimeout(9_000)` + `ensureRestored()` then `orchestrator.refreshWidget()`; never paints itself).
- `core/widget/WidgetUpdater.kt`: `@Singleton`, `@ApplicationContext` + `SystemTimeMapper`; `update(model)` is the spec verbatim — ids come fresh from `AppWidgetManager.getAppWidgetIds(ComponentName(...))` each time (no widget ids in prefs), `model == null` shows the static "Blocks / No day running" state, chronometer base = `elapsedRealtime() + (systemMillis(end) - now)` so the debug clock's countdown stays right.
- `WidgetPort` interface injected into `DayOrchestrator` with a no-op default in the constructor (so the six hand-built orchestrator tests compile unchanged); bound in `NotificationModule` like `NotificationPort`. `sideEffects()` and `flush()` (null) now also paint the widget; new `refreshWidget()` computes the model the `sideEffects()` way and calls only the widget.
- Shared `openAppIntent(context)` extracted to `core/notifications/AppIntent.kt` (launcher-style MainActivity intent); `NotificationRenderer.openApp()` delegates to it and the redundant `REQUEST_OPEN` const is gone.
- Resources: `widget_now.xml` (root/accent/title/text/chronometer per spec), `drawable/widget_bg.xml` (`#312F2C`, 16dp corners), `xml/widget_info.xml` (min 180×40dp, 3×1 cells, horizontal resize, updatePeriod 0, description @string), manifest receiver with `APPWIDGET_UPDATE` filter + provider meta-data (exported=false, system delivers the broadcast).
- No named test (Android glue); full suite green (`exit=0`).