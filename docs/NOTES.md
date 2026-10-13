# Notes - decisions the task files left unspecified, and what the code does instead

Still-live rules first (decisions a future agent needs), then one section per version. Only what is still true in the code.

- **Restore is idempotent, single-flight, and always completes.** `DayOrchestrator.ensureRestored()` takes a mutex and sets `_isRestored` in a `finally`; every entry point awaits it first, so a throwing restore can't strand the app.
- **`onTimeTravelled()` is new, and deliberately not `onTimeChanged()`**: it replays the skipped transitions exactly like a cold start and also applies the date rollover (`applyRollover()`); `SystemEventsReceiver` calls it, `AlarmReceiver` keeps `onAlarm()`. All replay loops are bounded by `MAX_REPLAY_STEPS`.
- **The old splash bug was a write/read form mismatch in `DayStateSerializer`**, not a race. The wrapper delegates via `encodeSerializableValue`/`decodeSerializableValue` and decodes legacy array payloads with a private `arrayJson` (`ignoreUnknownKeys`, `useArrayPolymorphism`).
- **Test harness:** `PrefsStorage` takes `@IoDispatcher`; tests pass `UnconfinedTestDispatcher()` so every write commits before the assert. `robolectric.properties` sets `application=android.app.Application`: unit tests get a plain Application, no Hilt graph, no background restore racing the seed. Race tests use `Dispatchers.Default` + `withTimeout(5_000) { .first { } }`. Qualifiers live in `core/di/CoroutinesModule.kt`.
- **Today's plan (`TodayPlan(templateId, blocks, edited)`) is the 2.0.0 data model**: the orchestrator owns it (`todayPlan` StateFlow), `timelineAt` reads it, swap rebuilds via `SegmentBook.swapPlan`. 1.5.1 saves migrate via `migratePlan` + `repointBlockIndex`; `PLAN_STARTS_AT` pref is gone. `HistoryManager.observeDay` is version-driven.
- **Weekly** renders `rows: List<PlanProgressUi>` with a `missedSummary` line; chart card uses `Spacing.l` (16dp), not `m`.
- **Time Debt is gone**: `DebtRecord`/`writeDebt`/`readDebt` deleted; Weekly/Insights/History compute `debtMinutes(segments)` directly. `debtMinutes` stays.
- **DayRecap** rows anchor on the record's `plannedBlocks`; summary = worked(non-paused) CV/Coding/Gym ÷ planned, capped 100%.
- **Pomodoro**: the spec's new `PomodoroPhase` collides with the engine's existing one, so the new type is `PomodoroSlot`. `HourHeight` 64→80dp.
- **Plan editing**: `DayOrchestrator.editPlan(PlanEdit)` is Running/Paused-only, edits in memory then `persist()` once + silent `sideEffects()`. Calendar editing: tap selects, five `HomeAction` editing messages, `EdgeHandle` at `zIndex(2f)`. Type tags use the data-object names because `BlockType` is a sealed interface.
- **Demo data** (28 days): a late-start day and an edited day built through the same pure pieces; both Sun/Tue/Wed. `PLAN_EDITED` only added when `PlanEditor` actually returned a plan.
- **Naming/doc facts**: `TimelineQueries.kt` is a file of `ScheduleEngine` extensions, not a class; there is no `InsightsManager` (it's `InsightsViewModel`); Settings copy stays as inline literals; `SettingsRow.enabled` gates the developer rows off in release; the emergency "Stop today" confirmation is always shown and only Pause supports "Don't ask again"; `NowButtonId.EMERGENCY` renders as its own centered `now_emergency` TextButton so Pause · Next stay equal-width 48dp; `TimeFormatter.label/hourGutter` are the single 12/24h source of truth; `BlockType.isWork` lives in `Model.kt`.

## 2.1.0 (Task-8)

- Weekly: `canGoPrevious` = `selected > earliest stored week`; empty copy "Use the arrows to see other weeks." Developer tools: one `DeveloperToolsDialog`; `DebugTimeController.testDay(template, hhmm)` = `setTo` + `resetToday()` + `StartDay`.
- Editing: `TYPE_ONLY` "Add a block:" (CV/Coding/Gym to `PlanEdit.Insert`, selected via `HomeState.selectId`); locked taps toast via `lockedReason()`. `DayFlag.CORRECTED`; `HistoryManager.correctDay` runs `RecordEditor.apply` (LAST_MINUTE) and rebuilds the week; recap screens own `CorrectBlockSheet`.
- Evening check-in (`CheckIn`, `HistoryManager.setCheckIn`, card in `RecapList`); widget (`BlocksWidget` + `WidgetUpdater`, `WidgetPort` no-op default, accent an `<ImageView>`).

## 2.2.0 (Task-9)

- `reactedAt: Instant?` lives on the orchestrator (private, persisted, reset on rollover); user actions set it, alarms and `applyRollover` don't. `AlertPlanner.next` takes `reactedSec` so nags stop once Roni reacted. `App.onCreate` observes `ProcessLifecycleOwner` and calls `onAppForeground()` on `onStart`.
- `ScheduleEngine.nextWakeUp(state, date, now, template, index, anchor?)` — `anchor` defaults to null and `nextBoundary` starts its Pomodoro cursor there (Done early parks it at the free time's planned end).
- `NotificationRenderer.render(model, transition, alert=false)`: alerts always sound, transitions only when not foreground, refreshes stay silent; `dismissals.forget(key)` on transition or alert. `NotificationModelMapper.running` branches done-early / Pomodoro / non-Pomodoro by key.
- `DayOrchestrator.sideEffects(transition, alert)` schedules `min(nextWakeUp, AlertPlanner.next(..., afterSec=now))`; `onAlarm` adds a 90s grace (`AlertPlanner.due`) and forwards `alert != null`. `Ticker.minutes` polls every 1s with `distinctUntilChanged()`.
- `PlanEditor.editMode` for a running unfinished work block is `END_AND_TYPE`; `editPlan` follows the open segment: PLANNED with `blockId == current.id` takes the new `plannedEnd`/`type`; SYNTHETIC_FREE takes the current block's new end.
- `liveRecord()`/`correctPast(edit)` use `RecordEditor.apply(liveRecord().toRecordRows(zone), edit, limitMin)` + `withRows(...)`; works in every state with segments. A past-block tap opens `CorrectBlockSheet` from `DayRecapMapper.map(day.liveRecord(), use24)` by id; `fix == NONE` shows the kid-time message.
- `EditCard` steppers are −15 / −5 / +5 / +15; captions per edit mode. `com.roni.blocks.debug` is the only phone ID.
- In deep Doze Android allows an exact alarm about every 9 minutes, so a nag can come late; if Roni finds them late, the next step is `setAlarmClock` for alerts or a foreground service.

## 2.3.0 (Task-10)

- New `BlockType.Walk` (name "Walk", colors `2B3A3F`/`8FC7C9`, not work/not editable); the calendar draws its block with a thin "turn" line only when it fits two lines.
- `SegmentBook.enterKidTime` closes every open segment, `SYNTHETIC_FREE` included. `PlanConversion.toBlockSpec` falls back to the id before `@`, so a resumed piece keeps its original's `quiet` flag.
- `AlarmScheduler.schedule(at, clock = false)`; with `clock = true` it uses `setAlarmClock(AlarmClockInfo(millis, openAppIntent(context)))` so Doze can't push a walk ring late. The orchestrator passes `clock = true` while a walk runs.
- New `WalkRinger` interface (`ring(kind, homeAt, nextLabel, nextEnd)`, `stop()`), no-op default in the orchestrator, `AndroidWalkRinger` bound in `NotificationModule`. Channel `walk_alarm` (IMPORTANCE_HIGH, alarm sound at `USAGE_ALARM`). Notification id 3, `FLAG_INSISTENT`, `setTimeoutAfter(60_000)`, a "Stop" action → `WalkAlarmReceiver` in `src/main` (manifest `exported=false`). The ringer formats its own HH:MM from the injected `TimeFormatter`/`TimeZone`.
- `DayOrchestrator.walkPreview/startWalk/turnBack` + `WalkPreview(turnBackAt, homeAt, afterTitle, afterEnd)`. `startWalk` commits `PlanEditor.insertWalk(...)`, closes the open segment at now and opens a PLANNED `Walk` segment with the walk's id. `turnBack` only before half time: the open segment's `plannedEnd` is `now + (now - start)` to the second, the plan's end rounded up to the minute.
- No new `DayEvent`: `TURN_BACK` is handled directly in `handleNotificationAction` (the walk's block id is the staleness guard); `NotificationActionMapping.toDayEvent` has an unreachable `TURN_BACK` branch so the `when` stays exhaustive. `DayStateMachine` untouched.
- `onAlarm` works out `AlertPlanner.dueWalk` before the missed transitions and rings the `WalkRinger`; a walk still running refreshes quietly. `sideEffects` schedules `min(nextWakeUp, AlertPlanner.nextWalk)` with `clock = true` while a walk runs. `onAppForeground` calls `walkRinger.stop()`.
- `NowButtonId.WALK` + `HomeAction.{Walk, WalkStep(byMin), StartWalk, DismissWalkSheet, TurnBack}`. The NowCard gets a secondary "Walk" button for every `Running` card (day block or Done early), never during kid time, the evening, a pause or a walk.
- `HomeViewModel` builds `WalkSheetUi` from `DayOrchestrator.walkPreview`; -5/+5 stay inside `WALK_MIN`/`WALK_MAX`; "before kid time" shows when `now + minutes` would pass the wall. `WalkCard` (Canvas ring, 120 dp): out leg in the walk accent, home leg in `Secondary`, elapsed solid / rest at 35%, a notch at the turn-back and a dot for now.

## 2.4.0 (Task-11)

- Walks at any time: the walk icon in the top bar starts one in kid time, the evening, after the day ends, or after midnight. Same rings and walk card; the day itself isn't touched. History lists each day's walks. `NowButtonId.WALK` is available for every Running card (day block or Done early), never during kid time, the evening, a pause or a walk.

## 2.5.0 (Task-12)

- Steps: Blocks reads the phone's step counter and shows today's steps on Home (top-bar "N steps" line under the title/status row), each day's total in History, each walk's steps, and the week's total in Weekly (leading `StatTile` "Steps", only when some day has one). Shared `designsystem.formatSteps`.
- `DayOrchestrator.liveSteps` maps the raw `stepSensor.live()` past `steps.baseline` and emits null first; nothing is written from it. Settings "Step counting" owes NOT_SET/BLOCKED to prefs `step_requested`: NOT_SET launches the system dialog (then `markStepRequested()`), ALLOWED/BLOCKED open the app's `ACTION_APPLICATION_DETAILS_SETTINGS` page.
- `pendingEvening` lives on the orchestrator, persisted under `pending_evening` in `PrefsFile.DAY`; cleared on KidAsleep, rollover and reset. `DayEvent.EveningChoice` no longer goes through `DayStateMachine`: `apply()` intercepts it — during Dishes (now < plannedEnd) it only remembers the pick; once Dishes is over it calls `startEvening(choice, at)`.
- Dishes ends by itself at its `plannedEnd`; with nothing picked, `onAlarm` alerts "Dishes done · pick your evening" and Dishes runs over until the pick. New `DayOrchestrator.{switchEvening(choice), skipDishes(), resizeDishesTo(min)}` — side doors that aren't state-machine events. `resizeDishesTo` clamps to now+5..22:30.
- `Home.withEvening(specs, segments, pending)` cuts only the kid-time block, gives closed activities a `@startMin` id, adds the pending activity as a planned block from Dishes' end to 22:30. `HomeViewModel` collects `day.pendingEvening` and auto-opens the sheet once per Dishes. The Dishes calendar block uses `BlockEdit.END_ONLY`; its steppers go to `resizeDishesTo`, not the plan editor.

## 2.6.0 (Task-13)

- One clock for the walk: `HomeViewModel.walkView: StateFlow<WalkView?>` = `combine(_state, secondOfDay) { WalkClock.view(card.startSec, card.endSec, now.epochSeconds) }`; `WalkCard` reads text/progress/phase and shows "Turn back now" only while `OUT`. `DayOrchestrator.turnBack`/`walkPreview` and `NotificationModel.walk` use `WalkClock.turnBackEnd`/`turnSec`.
- `HomeState.calendarFit` (`CalendarLayout.fit`) + `pillLabels`; `DayCalendar` draws exactly the fit. Slivers = 60% accent bar (≥3 dp, clickable); other blocks draw at `textTopDp` by `TextFit`. Pills from `CalendarFit.rows` (18 dp, accent border + dot, ≤160 dp). A day-block walk < 10 min leaves `blocks` and becomes a tick keeping its block id; a standalone walk is tick id `walk@<startMin>`.
- Next is the very next block, free time included (2.5.0's skip-free is gone); `PlanEditor` is prep-verified as such. Permissions: `MainActivity` asks at launch in one `RequestMultiplePermissions` for `POST_NOTIFICATIONS` (API 33+) and `ACTIVITY_RECOGNITION` (API 29+), ungranted only, then calls `orchestrator.onAppForeground()`. The step permission card is gone.

## 2.6.1 (Task-14)

- `CalendarLayout` (`verified`) now takes measured `TextMetrics(oneLineDp, twoLinesDp)` and counts the 1 dp gap (`GAP_DP == Metrics.calendarGap`); a block too short for one line gets a pill instead of clipped text. `HomeScreen` measures once per font scale (`rememberTextMeasurer` + `LocalDensity`, in the exact styles `CalendarBlock` draws) and sends `HomeAction.TextMeasured`; `HomeViewModel` keeps `textMetrics` and passes it to `fit`, re-publishing on change. `CalendarBlock`'s text `Column` has no bottom padding (the layout reserved it).
- `AppCard` gained `elevated: Boolean = true`; with `false` it skips `.shadow(...)`. `TemplateCard` passes `elevated = !card.outlined` (the Off day card's stray shadow) — the only other transparent `AppCard` is the Scaffold in `ScreenScaffold`.
- `DebugTimeControllerTest.evening_canBeReachedWithoutWaiting` sets the clock to 10:00 before `StartDay`, so it passes at any hour.

## 2.6.2 (Task-15)
- `HomeViewModel.statusLine` (replaces `status`) drives the top bar: Off day, Pick a template, Last block / "N blocks left" + "ran over M min", or "D done · M missed" once no day block is ahead (uses `endedRecord` when available, else `day.liveRecord()`; only `isWork`/`Free` rows count). `ScreenScaffold` passes `PaddingValues(0.dp)` to `content` (screens kept their `.padding(padding)`), and screens without a bottom bar (History, History day, Settings) bottom out at `WindowInsets.navigationBars + Spacing.l`. `RecapList` gained `header: (@Composable () -> Unit)?`; History day puts `d.steps` + `walks` (with a `0xFF8FC7C9` dot) as item 0 and dropped the old `WalksSection`.
- `versionName` 2.6.1 / `versionCode` 20601.

## 2.7.0 (Task-17)
- A day picked before its first block waits: `SegmentBook.startDay` returns `DayBook(index = 0)` (no segments) while `now <` the first block's start; `DayOrchestrator.startsAt` is that first start (else null) and `sideEffects()` keeps the day quiet (no `transition`/`alert`) until it begins.
- `applyOneMissedTransition()` starts the day from the first start when the clock reaches it (kid time included), with the normal transition; `DayOrchestrator.startNow()` runs `PlanEditor.startFirstNow`, commits the edited plan (`edited = true`) and starts the day at now, so the moved block keeps its length and the gap becomes free time.
- `planFirstStart()` only counts as waiting when the state is `Running`, `blockIndex == 0` and there is no segment yet, so a legacy restored day (old "Interrupted" with a higher index and no segments) is not re-derived as waiting. `apply()`'s `StartDay` branch maps to kid time only when the book actually opened a segment (`book.segments.isNotEmpty()`), so a wait on a kid-time-first template stays `Running`.
- Waiting notification: key `waiting`, title "Day starts at HH:MM", text = the template's name, `countdownTo` = the first start, no actions; `NotificationModel.from` takes `templateName`; the widget shows the same model.
- Home: the waiting card ("Day starts at HH:MM · in 1h 20m", PRIMARY Start now → `HomeAction.StartNow`, SECONDARY Change day → `SwapTemplate`), the "Starts at HH:MM" status, and `HomeState.calendarStartMin` = `min(08:00, first start rounded down to the hour)` used by `DayCalendar`/`CalendarLayout.fit`; `HomeViewModel` collects `day.startsAt` so the card appears even though `persist()` sets it after the state/segments.
- Change day while waiting is the existing `SwapTemplate` (per the task): it replans and starts the new template from now, it does not re-apply the wait.
- `versionName` 2.7.0 / `versionCode` 20700.
## 3.0.0 (Task-18)
- Slice 2: `recapTitle` (DayRecap) already delegates to `blockTitle`, so a category rename reaches it unchanged; `EditCard.typeKey` now returns `Categories.infoFor(type)?.name ?: "Other"` (tags change e.g. `edit_type_Cv` -> `edit_type_CV`; no test used them); `accent(BlockType)` in NotificationModel gained a `Custom` branch using the category colour.
- Slice 2: `CategoryStore` is an optional last `DayOrchestrator` constructor param (default null) so hand-built tests are untouched; production injects it and `ensureRestored()` calls `load()` before `restoreFromStorage()`.
- Slice 3: `TemplateManager` keeps a `context` secondary constructor (builds a seed-only `TemplateStore`) so existing tests/previews are unchanged; production injects the shared `TemplateStore`. `Template.name` now comes from `StoredTemplate.title` (was the asset name "Mon/Thu").
- Slice 3: `todayTemplate` (picker) and `DemoDataGenerator.templateFor` call `TemplateStore.suggestedFor` when a store is up, else the 2.x weekday map. `DayOrchestrator` got optional `templateStore` next to `categories`; `DemoDataGenerator` got an optional `templateStore`.
- 3.0.0 exhaustive `when`s that gained a branch for `BlockType.Custom` (or an `else`): `blockName` and `accent` (NotificationModel), `blockTitle` (CalendarMapper), `blockColors` (Color), `isWork` (Model). Slice 1 needed none.
## 3.1.0 (Task-19)
- Templates tab (Home · Weekly · Insights · Templates): templates section (one card per stored template, tap opens the editor) then categories section (a row per category).
- Editor is list + sheet: the template's blocks as a list with a preview strip on top; a row opens a sheet with category, start, length, Delete and Save. `TemplateEditor`/`TemplateRules` are verified, not edited.
- Overlaps push later blocks: free time first, then later blocks shift later keeping length, kid time at the end shrinks (refused under 10 min); a start inside an earlier block is refused with a reason (blocks before the edited one never move).
- Your own templates start blank (kid time 17:00–22:30) and can be deleted (`archived = true`, still resolve in History); built-ins are read-only in the editor.
- Weekdays: every template can claim days for the picker's Suggested; claiming moves the day from the other template.
- Categories: built-ins (CV, Coding, Gym, Free) rename/recolour/Pomodoro, never deleted; your own also get "counts as work" and Delete (their blocks become free time). `CategoryPalette` is the fixed 12 colours, no free picker.
- Editing today's template: a save makes today follow too while the day is still waiting for its first block; a started day keeps its plan.
- Real names: `TemplateActions`/`CategoryActions` live together in `core/storage/Actions.kt`; Track B's editor is `TemplateEditorScreen.kt` with `BlockSheet.kt`; Track A's editor is `CategorySection.kt` (holds `CategoryListViewModel`) with `CategorySheet.kt`. Track B changed the Base test helper: `testActions(context)` now returns a `TestRig` (templates, categories, store, day, clock) instead of a `Pair`, so the editor tests read the very store the actions write through; `TemplatesScreenTest` passes a real `CategoryListViewModel` (built from `testActions`).
## 3.1.1 (Task-20)
- Cause 1: the picker stacked `cards.chunked(2)` rows in a non-scrolling centred column, so five templates overflowed and the odd card took a full row; now a `LazyVerticalGrid` of 2 scrolls and every card stays half width.
- Cause 2: `TemplateListViewModel` read `TemplateStore.instance` once, so it never saw editor writes; now `TemplateStore.templates` publishes a StateFlow on every write/load and `TemplateListViewModel` collects it (`@HiltViewModel`, New through `TemplateActions`; `NewTemplateViewModel` and `refresh()` deleted).
