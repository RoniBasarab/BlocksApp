# CODEMAP.md

One row per Kotlin file under `app/src/main`, grouped by package, so the next person can find its way
without reading everything.

Paths are relative to `app/src/main/java/com/roni/blocks/`. `src/debug` holds
`debugtools/TimeTravelReceiver.kt` (the adb front door for the debug clock); `src/main` files are
shared with release, so anything debug-only must stay behind `BuildConfig.DEBUG`.

Rules that hold everywhere:

- `feature/*` may read core and publish state; core never imports a feature.
- Only `DayOrchestrator` writes the persisted day. Everything else reads or requests.
- A ViewModel publishes plain values only — no Android or Compose types.
- A commit that adds, removes or repurposes a file updates its row here.

## Root

| path | what it does | talks to |
| --- | --- | --- |
| `App.kt` | Hilt application; warms the orchestrator and registers a `ProcessLifecycleOwner` foreground observer (2.2.0) | DayOrchestrator, Hilt |
| `MainActivity.kt` | The single activity; hosts the Compose graph; asks for notifications and physical-activity permission in one request at launch, then re-reads the day (2.6.0) | AppNav, NotificationPort, DayOrchestrator |
| `navigation/AppNav.kt` | Serializable nav keys and the top-level graph | feature routes |
| `app/src/main/res/layout/widget_now.xml` | The widget layout (`<ImageView>` accent — RemoteViews can't inflate a plain `<View>`) | R.layout.widget_now |

## core/alarms

| path | what it does | talks to |
| --- | --- | --- |
| `core/alarms/AlarmScheduler.kt` | Owns the single exact alarm that ends the current block | AlarmManager, SystemTimeMapper, AlarmReceiver |
| `core/alarms/Receivers.kt` | `AlarmReceiver` (a block ended) and `SystemEventsReceiver` (boot, app replaced, clock/timezone moved) | DayOrchestrator, AppLogger |

## core/daystate

| path | what it does | talks to |
| --- | --- | --- |
| `core/daystate/DayOrchestrator.kt` | Single writer of the day's state: owns today's plan, applies events and plan edits, persists, pushes alarm/notification/history; `liveRecord()`/`correctPast()`/`onAppForeground()` (2.2.0); standalone walks `walkSession`/`walkPreview`/`startWalk`/`turnBack`/`endWalk` (2.4.0); step checkpoints and the evening flow `pendingEvening`/`switchEvening`/`skipDishes`/`resizeDishesTo` (2.5.0); a day picked before its first block waits: `startsAt`, `startNow()`, quiet until that block starts by itself (2.7.0) | DayStateMachine, ScheduleEngine, HistoryManager, PrefsStorage |
| `core/daystate/DayState.kt` | `DayState` and `DayEvent` — the persisted vocabulary of a day | Model, ScheduleEngine |
| `core/daystate/DayStateMachine.kt` | Every legal transition as one pure function | DayState, DayEvent |
| `core/daystate/NotificationActionMapping.kt` | Maps notification buttons onto day events; new actions fail to compile unmapped | NotificationModel, DayStateMachine |
| `core/daystate/WalkPreview.kt` | What the walk start sheet shows before it is committed (2.3.0): turn-back, home, and what runs after; the standalone form has no "then" (2.4.0) | DayOrchestrator |

## core/debug

| path | what it does | talks to |
| --- | --- | --- |
| `core/debug/RecompositionCounter.kt` | Counts recompositions per composable tag (debug aid) | Compose runtime |

## core/demo

| path | what it does | talks to |
| --- | --- | --- |
| `core/demo/DemoDataGenerator.kt` | Seeds or clears weeks of realistic history | TemplateManager, TemplateStore, HistoryManager, Clock |

## core/di

| path | what it does | talks to |
| --- | --- | --- |
| `core/di/AppModule.kt` | The `CoroutineExceptionHandler` binding | Hilt |
| `core/di/ClockModule.kt` | Binds `Clock`: debug gets the jumpable clock, release the system clock | ClockModule, SystemTimeMapper |
| `core/di/CoroutinesModule.kt` | The `@IoDispatcher` and `@ApplicationScope` qualifiers: the two ways production code gets a dispatcher or scope | Hilt |
| `core/di/NotificationModule.kt` | Binds `NotificationPort` and `WidgetPort` to their real renderers | NotificationRenderer, WidgetUpdater |
| `core/di/StepModule.kt` | Binds `StepSensor` to `AndroidStepSensor` (2.5.0) | AndroidStepSensor |

## core/logging

| path | what it does | talks to |
| --- | --- | --- |
| `core/logging/AppLogger.kt` | The single logging entry point | android.util.Log |

## core/model

| path | what it does | talks to |
| --- | --- | --- |
| `core/model/Model.kt` | `Template`, `TemplateId`, `BlockSpec`, `TodayPlan`, `Segment`, `DayRecord` — the persisted shapes; `WalkSession`/`WalkLog` for standalone walks (2.4.0); `TemplateId` is a `value class` over a String and `BlockType.Custom` carries your own kinds (3.0.0) | kotlinx.serialization |
| `core/model/Categories.kt` | `CategoryInfo`/`CategorySet` and the `Categories.current` snapshot the pure mappers read; CV/Coding/Gym/Free defaults (3.0.0) | Model |
| `core/model/Templates.kt` | `StoredTemplate`, `Profile` and the built-in seed texts/icons/accents/weekdays (3.0.0) | Model |

## core/notifications

| path | what it does | talks to |
| --- | --- | --- |
| `core/notifications/AppVisibility.kt` | Whether any part of the app is on screen, so notices stay quiet when already visible | ActivityLifecycleCallbacks |
| `core/notifications/DismissalStore.kt` | Remembers swiped-away notices; a real transition clears the memory | PrefsStorage |
| `core/notifications/NotificationActionReceiver.kt` | Pause/Next buttons on a notification | DayOrchestrator, NotificationActionMapping |
| `core/notifications/NotificationModel.kt` | Channel/action enums and `NotificationModelMapper` (block → notification text); `walk()` builds the standalone walk notification (2.4.0); the "Dishes done · pick your evening" evening alert (2.5.0) | ScheduleEngine, DayState, TimeFormatter |
| `core/notifications/NotificationRenderer.kt` | `NotificationPort` plus the real renderer; the port exists so tests can fake it | NotificationModel, AppVisibility, DismissalStore |
| `core/notifications/WalkRinger.kt` | `WalkRinger` plus the alarm-clock walk ringer (2.3.0): half-time and home rings, even on silent | NotificationRenderer, TimeFormatter |

## core/schedule

| path | what it does | talks to |
| --- | --- | --- |
| `core/schedule/AlertPlanner.kt` | The pure rules for when an alert fires (5-min warning, Pomodoro breaks/starts, nags until the user reacts); walk rings at half time and the end (2.3.0) | Model |
| `core/schedule/PlanConversion.kt` | Converts `BlockSpec` to and from the editor's `PlanBlock`; preview and commit share it | Model, PlanEditor |
| `core/schedule/PlanEditor.kt` | The pure rules for reshaping today's plan (add, resize, retype, insert); free time absorbs, work blocks push, kid time stays; `END_AND_TYPE` lets a running work block also become another work type (2.2.0); walks: `walkEnd`/`insertWalk`/`shortenWalk` (2.3.0); `startEarly`/`nextStop` pick the very next block, no free-time skipping (2.6.0); `startFirstNow` moves the first block to now, its gap becomes free time (2.7.0) | Model |
| `core/schedule/RecordConversion.kt` | `toRecordRows`/`withRows`: day record ↔ editable rows for correcting a past day | Model, RecordEditor |
| `core/schedule/RecordEditor.kt` | The pure rules for editing a past day's rows: `fixMode`, `apply` (SetEnd/Retype/MarkDone, `MIN_LENGTH`/`LAST_MINUTE` limits), `snap` | Model |
| `core/schedule/ScheduleEngine.kt` | Resolves template + date into concrete blocks and segments; `nextWakeUp(anchor)` anchors the Pomodoro cursor on the open segment's actual start (2.2.0) | Model, Clock, TimeZone |
| `core/schedule/SegmentBook.kt` | Builds the segment list History renders; rehydrates it after process death; `kidAsleep` opens Dishes then the choice, `eveningChoice` switches it (2.5.0); `startDay` returns an empty book while the clock is before the first block (2.7.0) | ScheduleEngine, Model, PrefsStorage |
| `core/schedule/TemplateRules.kt` | `problems`/`normalize`: the rules every template follows (no overlap, kid time to 22:30, unique ids) (3.0.0) | Model |
| `core/schedule/TimelineQueries.kt` | `ScheduleEngine` extensions that read the published plan: `timelineAt`, `currentBlock`, `pomodoroStatus`, `pomodoroPhases` | ScheduleEngine, Model |

## core/steps

| path | what it does | talks to |
| --- | --- | --- |
| `core/steps/StepBook.kt` | The pure step arithmetic and `StepState` (2.5.0): reads the cumulative sensor, keeps the day's baseline, computes today's steps and a walk's steps | PrefsStorage |
| `core/steps/StepStateSerializer.kt` | Stores `StepState` in prefs (2.5.0) | PrefsStorage, StepState |
| `core/steps/StepSensor.kt` | `StepSensor` port plus `AndroidStepSensor`, the only reader of `Sensor.TYPE_STEP_COUNTER` (2.5.0) | Android SensorManager |

## core/storage

| path | what it does | talks to |
| --- | --- | --- |
| `core/storage/HistoryManager.kt` | The only reader/writer of days and week aggregates; a version flow lets Home observe writes; `addWalk`/`walks`/`observeWalks` for standalone walks (2.4.0); each day's step total (2.5.0) | PrefsStorage, Model |
| `core/storage/PrefsStorage.kt` | SharedPreferences wrapper split into DAY / SETTINGS / HISTORY files | android.content.SharedPreferences |
| `core/storage/CategoryStore.kt` | Loads/saves `CategorySet` from prefs and sets `Categories.current` (3.0.0) | PrefsStorage, Categories |
| `core/storage/TemplateStore.kt` | The stored templates and the one profile; seeds from assets, validates on load, `all`/`get`/`suggestedFor` (3.0.0) | PrefsStorage, assets, TemplateRules |
| `core/storage/TemplateManager.kt` | A thin façade over `TemplateStore`, so `get`/`all` keep their call sites (3.0.0) | TemplateStore |

## core/widget

| path | what it does | talks to |
| --- | --- | --- |
| `core/widget/BlocksWidget.kt` | The home-screen widget receiver; restores the day, then asks the orchestrator to redraw it | DayOrchestrator, AppLogger |
| `core/widget/WidgetPort.kt` | The tiny interface the orchestrator paints through; a no-op default keeps hand-built orchestrator tests unchanged | NotificationModel |
| `core/widget/WidgetUpdater.kt` | The real renderer: `RemoteViews` from the same `NotificationModel` the notification uses; ids come fresh from `AppWidgetManager` | NotificationModel, SystemTimeMapper |

## core/system

| path | what it does | talks to |
| --- | --- | --- |
| `core/system/SystemStatuses.kt` | Notification and exact-alarm permission status as the system reports it; step-counter presence and activity-recognition permission (2.5.0) | AlarmManager, NotificationManager, SensorManager |

## core/time

| path | what it does | talks to |
| --- | --- | --- |
| `core/time/DebugClock.kt` | System time plus a stored offset — the clock the debug build jumps | PrefsStorage, SystemTimeMapper |
| `core/time/DebugTimeController.kt` | Moves the debug clock and catches the day up to where it now is | DebugClock, DayOrchestrator |
| `core/time/SystemTimeMapper.kt` | Converts an app instant to wall-clock millis for Android | AlarmManager, PendingIntent |
| `core/time/Time.kt` | `LocalTime.at()` helpers and `TimeModule` | Clock, SystemTimeMapper |
| `core/time/TimeFormatter.kt` | The one place time labels are formatted; watches the 12/24h toggle | PrefsStorage, DayState |

## core/walk

| path | what it does | talks to |
| --- | --- | --- |
| `core/walk/WalkClock.kt` | The only place walk time is computed (2.6.0): `view`/`turnSec`/`turnBackEnd`/`format` over epoch seconds | — |

## designsystem

| path | what it does | talks to |
| --- | --- | --- |
| `designsystem/Background.kt` | Page backdrop: gradient plus two soft glows, cached | Color |
| `designsystem/Color.kt` | Raw color values | — |
| `designsystem/Components.kt` | Shared composables: `ScreenScaffold`, trend arrows, stat rows | Tokens, Type, Color |
| `designsystem/Format.kt` | `formatSteps`: the one place step counts get thousands separators (2.5.0) | — |
| `designsystem/Motion.kt` | The app's only motion token | Compose animation |
| `designsystem/Primitives.kt` | Row-level primitives: `SettingsRow`, tonal chips, `ScreenColumn` | Tokens, Components, Color |
| `designsystem/Shell.kt` | `AppTab` and the bottom-bar / top-bar shell | Color, Type, Tokens |
| `designsystem/TemplateDisplay.kt` | Title, subtitle and block preview for one template, read from `TemplateStore` (3.0.0) | Model, TemplateStore |
| `designsystem/Theme.kt` | Material color scheme and typography wiring | Color, Type |
| `designsystem/Tokens.kt` | `Spacing`, `AppShapes`, `Metrics` | Compose units |
| `designsystem/Type.kt` | Corner radii and the type scale | Compose units |

## feature

| path | what it does | talks to |
| --- | --- | --- |
| `feature/home/Home.kt` | Home screen, `HomeViewModel` (publishes the calendar, the now/walk cards, `walkView`, `calendarFit`/`pillLabels`, and previews plan edits), top bar, now-card dialogs, edit card; walk icon in top bar, standalone walk card support (2.4.0); the top-bar step count, evening built from segments with Dishes/switch/skip (2.5.0); the waiting card ("Day starts at HH:MM · in 1h 20m", Start now / Change day), the "Starts at HH:MM" status and `HomeState.calendarStartMin` (2.7.0) | DayOrchestrator, HistoryManager, TemplateManager, DebugTimeController |
| `feature/home/HomeUi.kt` | `HomeState` and the calendar/block/now-card/walk UI models (`WalkCardUi.standalone`, `walkEnabled`, `calendarFit`, `pillLabels`) | Compose, designsystem |
| `feature/home/HomeSheets.kt` | Bottom sheets: evening choice (dismissible, 2.5.0), walk start (`WalkSheet`, 2.3.0), template swap, the recap's `CorrectBlockSheet` | DayOrchestrator, TemplateManager |
| `feature/home/NowCardDialogs.kt` | The now-card's dialog states and confirmations | DayOrchestrator |
| `feature/home/DayRecap.kt` | A finished day as a readable recap: summary card (+ check-in card until it's answered), one row per block | designsystem, Compose |
| `feature/home/CalendarMapper.kt` | Pure model→view mapping for the calendar; no clock, no context | DayCalendar |
| `feature/home/CalendarLayout.kt` | Pure geometry (2.6.0): `fit(blocks, ticks, dayStartMin)` returns each block's `TextFit`/sliver and the pill rows, so no text overlaps | — |
| `feature/home/DayCalendar.kt` | The Home calendar grid; draws exactly what `CalendarLayout.fit` returns — slivers, block text, walk ticks and the pill rows (2.6.0) | designsystem, CalendarLayout |
| `feature/history/History.kt` | History list and the day-detail recap, plus both ViewModels; day detail shows "Walks" section for standalone walks (2.4.0) and each day's steps (2.5.0) | HistoryManager, DayRecapMapper |
| `feature/weekly/Weekly.kt` | Weekly screen: planned vs actual per type as bars, week navigation; the week's step total tile (2.5.0) | HistoryManager |
| `feature/insights/Insights.kt` | Insights screen: last complete week vs the one before | HistoryManager |
| `feature/templatepicker/TemplatePicker.kt` | Template picker with a live preview of the rest of today | DayOrchestrator, TemplateStore |
| `feature/settings/Settings.kt` | Settings screen, `SettingsViewModel`, developer tools in one dialog (test a day, debug clock, demo data); the step-counting row and permission state (2.5.0) | DayOrchestrator, DemoDataGenerator, SystemStatuses, DebugTimeController |