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
| `App.kt` | Hilt application; warms the orchestrator so Home never waits on restore | DayOrchestrator, Hilt |
| `MainActivity.kt` | The single activity; hosts the Compose graph, asks for notification permission | AppNav, NotificationPort |
| `navigation/AppNav.kt` | Serializable nav keys and the top-level graph | feature routes |

## core/alarms

| path | what it does | talks to |
| --- | --- | --- |
| `core/alarms/AlarmScheduler.kt` | Owns the single exact alarm that ends the current block | AlarmManager, SystemTimeMapper, AlarmReceiver |
| `core/alarms/Receivers.kt` | `AlarmReceiver` (a block ended) and `SystemEventsReceiver` (boot, app replaced, clock/timezone moved) | DayOrchestrator, AppLogger |

## core/daystate

| path | what it does | talks to |
| --- | --- | --- |
| `core/daystate/DayOrchestrator.kt` | Single writer of the day's state: owns today's plan, applies events and plan edits, persists, pushes alarm/notification/history | DayStateMachine, ScheduleEngine, HistoryManager, PrefsStorage |
| `core/daystate/DayState.kt` | `DayState` and `DayEvent` — the persisted vocabulary of a day | Model, ScheduleEngine |
| `core/daystate/DayStateMachine.kt` | Every legal transition as one pure function | DayState, DayEvent |
| `core/daystate/NotificationActionMapping.kt` | Maps notification buttons onto day events; new actions fail to compile unmapped | NotificationModel, DayStateMachine |

## core/debug

| path | what it does | talks to |
| --- | --- | --- |
| `core/debug/RecompositionCounter.kt` | Counts recompositions per composable tag (debug aid) | Compose runtime |

## core/demo

| path | what it does | talks to |
| --- | --- | --- |
| `core/demo/DemoDataGenerator.kt` | Seeds or clears weeks of realistic history | TemplateManager, HistoryManager, Clock |

## core/di

| path | what it does | talks to |
| --- | --- | --- |
| `core/di/AppModule.kt` | The `CoroutineExceptionHandler` binding | Hilt |
| `core/di/ClockModule.kt` | Binds `Clock`: debug gets the jumpable clock, release the system clock | ClockModule, SystemTimeMapper |
| `core/di/CoroutinesModule.kt` | The `@IoDispatcher` and `@ApplicationScope` qualifiers: the two ways production code gets a dispatcher or scope | Hilt |
| `core/di/NotificationModule.kt` | Binds `NotificationPort` to the real renderer | NotificationRenderer |

## core/logging

| path | what it does | talks to |
| --- | --- | --- |
| `core/logging/AppLogger.kt` | The single logging entry point | android.util.Log |

## core/model

| path | what it does | talks to |
| --- | --- | --- |
| `core/model/Model.kt` | `Template`, `TemplateId`, `BlockSpec`, `TodayPlan`, `Segment`, `DayRecord` — the persisted shapes | kotlinx.serialization |

## core/notifications

| path | what it does | talks to |
| --- | --- | --- |
| `core/notifications/AppVisibility.kt` | Whether any part of the app is on screen, so notices stay quiet when already visible | ActivityLifecycleCallbacks |
| `core/notifications/DismissalStore.kt` | Remembers swiped-away notices; a real transition clears the memory | PrefsStorage |
| `core/notifications/NotificationActionReceiver.kt` | Pause/Next buttons on a notification | DayOrchestrator, NotificationActionMapping |
| `core/notifications/NotificationModel.kt` | Channel/action enums and `NotificationModelMapper` (block → notification text) | ScheduleEngine, DayState, TimeFormatter |
| `core/notifications/NotificationRenderer.kt` | `NotificationPort` plus the real renderer; the port exists so tests can fake it | NotificationModel, AppVisibility, DismissalStore |

## core/schedule

| path | what it does | talks to |
| --- | --- | --- |
| `core/schedule/PlanConversion.kt` | Converts `BlockSpec` to and from the editor's `PlanBlock`; preview and commit share it | Model, PlanEditor |
| `core/schedule/PlanEditor.kt` | The pure rules for reshaping today's plan (resize, retype); free time absorbs, work blocks push, kid time stays | Model |
| `core/schedule/ScheduleEngine.kt` | Resolves template + date into concrete blocks and segments | Model, Clock, TimeZone |
| `core/schedule/SegmentBook.kt` | Builds the segment list History renders; rehydrates it after process death | ScheduleEngine, Model, PrefsStorage |
| `core/schedule/TimelineQueries.kt` | `ScheduleEngine` extensions that read the published plan: `timelineAt`, `currentBlock`, `pomodoroStatus`, `pomodoroPhases` | ScheduleEngine, Model |

## core/storage

| path | what it does | talks to |
| --- | --- | --- |
| `core/storage/HistoryManager.kt` | The only reader/writer of days and week aggregates; a version flow lets Home observe writes | PrefsStorage, Model |
| `core/storage/PrefsStorage.kt` | SharedPreferences wrapper split into DAY / SETTINGS / HISTORY files | android.content.SharedPreferences |
| `core/storage/TemplateManager.kt` | Loads and saves the built-in templates | PrefsStorage, assets |

## core/system

| path | what it does | talks to |
| --- | --- | --- |
| `core/system/SystemStatuses.kt` | Notification and exact-alarm permission status as the system reports it | AlarmManager, NotificationManager |

## core/time

| path | what it does | talks to |
| --- | --- | --- |
| `core/time/DebugClock.kt` | System time plus a stored offset — the clock the debug build jumps | PrefsStorage, SystemTimeMapper |
| `core/time/DebugTimeController.kt` | Moves the debug clock and catches the day up to where it now is | DebugClock, DayOrchestrator |
| `core/time/SystemTimeMapper.kt` | Converts an app instant to wall-clock millis for Android | AlarmManager, PendingIntent |
| `core/time/Time.kt` | `LocalTime.at()` helpers and `TimeModule` | Clock, SystemTimeMapper |
| `core/time/TimeFormatter.kt` | The one place time labels are formatted; watches the 12/24h toggle | PrefsStorage, DayState |

## designsystem

| path | what it does | talks to |
| --- | --- | --- |
| `designsystem/Background.kt` | Page backdrop: gradient plus two soft glows, cached | Color |
| `designsystem/Color.kt` | Raw color values | — |
| `designsystem/Components.kt` | Shared composables: `ScreenScaffold`, trend arrows, stat rows | Tokens, Type, Color |
| `designsystem/Motion.kt` | The app's only motion token | Compose animation |
| `designsystem/Primitives.kt` | Row-level primitives: `SettingsRow`, tonal chips, `ScreenColumn` | Tokens, Components, Color |
| `designsystem/Shell.kt` | `AppTab` and the bottom-bar / top-bar shell | Color, Type, Tokens |
| `designsystem/TemplateDisplay.kt` | Title, subtitle and block preview for one template | Model, Color |
| `designsystem/Theme.kt` | Material color scheme and typography wiring | Color, Type |
| `designsystem/Tokens.kt` | `Spacing`, `AppShapes`, `Metrics` | Compose units |
| `designsystem/Type.kt` | Corner radii and the type scale | Compose units |

## feature

| path | what it does | talks to |
| --- | --- | --- |
| `feature/home/Home.kt` | Home screen, `HomeViewModel` (publishes the calendar and previews plan edits), top bar, now-card dialogs, edit card | DayOrchestrator, HistoryManager, TemplateManager, DebugTimeController |
| `feature/home/HomeUi.kt` | `HomeState` and the calendar/block UI models | Compose, designsystem |
| `feature/home/HomeSheets.kt` | Bottom sheets: evening choice, template swap | DayOrchestrator, TemplateManager |
| `feature/home/NowCardDialogs.kt` | The now-card's dialog states and confirmations | DayOrchestrator |
| `feature/home/DayRecap.kt` | A finished day as a readable recap: summary card and one row per block | designsystem, Compose |
| `feature/home/CalendarMapper.kt` | Pure model→view mapping for the calendar; no clock, no context | DayCalendar |
| `feature/home/DayCalendar.kt` | The Home calendar grid and its hour geometry | designsystem |
| `feature/history/History.kt` | History list and the day-detail recap, plus both ViewModels | HistoryManager, DayRecapMapper |
| `feature/weekly/Weekly.kt` | Weekly screen: planned vs actual per type as bars, week navigation | HistoryManager |
| `feature/insights/Insights.kt` | Insights screen: last complete week vs the one before | HistoryManager |
| `feature/templatepicker/TemplatePicker.kt` | Template picker with a live preview of the rest of today | DayOrchestrator, TemplateManager |
| `feature/settings/Settings.kt` | Settings screen, `SettingsViewModel`, redesigned developer clock and demo controls | DayOrchestrator, DemoDataGenerator, SystemStatuses, DebugTimeController |