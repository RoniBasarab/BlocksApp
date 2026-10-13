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
| `navigation/AppNav.kt` | Serializable nav keys and the top-level graph; `StartDestination` (3.3.0) picks from the day state and `TemplateStore.profileChosen()` (NotStarted → MAIN_MENU or TEMPLATE_PICKER, else HOME) and the graph wraps the Profiles/Profile/Categories/template-editor screens in `FirstVisit` explainers | feature routes |
| `app/src/main/res/layout/widget_now.xml` | The widget layout (`<ImageView>` accent — RemoteViews can't inflate a plain `<View>`) | R.layout.widget_now |

## core/alarms

| path | what it does | talks to |
| --- | --- | --- |
| `core/alarms/AlarmScheduler.kt` | Owns the single exact alarm that ends the current block | AlarmManager, SystemTimeMapper, AlarmReceiver |
| `core/alarms/Receivers.kt` | `AlarmReceiver` (a block ended) and `SystemEventsReceiver` (boot, app replaced, clock/timezone moved) | DayOrchestrator, AppLogger |

## core/daystate

| path | what it does | talks to |
| --- | --- | --- |
| `core/daystate/DayOrchestrator.kt` | Single writer of the day's state: owns today's plan, applies events and plan edits, persists, pushes alarm/notification/history; `liveRecord()`/`correctPast()`/`onAppForeground()` (2.2.0); standalone walks `walkSession`/`walkPreview`/`startWalk`/`turnBack`/`endWalk` (2.4.0); step checkpoints and the evening flow `pendingEvening`/`switchEvening`/`skipDishes`/`resizeDishesTo` (2.5.0); a day picked before its first block waits: `startsAt`, `startNow()`, quiet until that block starts by itself (2.7.0); `placeBlock(id?, type, startMin, lengthMin)` for Home's sheet and the shared `commitPlan` (3.3.0); Dishes no longer ends itself (it ends on `skipDishes`/Next) and its reminders are quiet | DayStateMachine, ScheduleEngine, HistoryManager, PrefsStorage |
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
| `core/model/Categories.kt` | `CategoryInfo`/`CategorySet` and the `Categories.current` snapshot the pure mappers read; CV/Coding/Gym/Free defaults (3.0.0); `CategoryPalette`, the 12 colours a category can take (3.1.0) | Model |
| `core/model/Templates.kt` | `StoredTemplate`, `Profile` (`days: Map<Int, TemplateId>?`, 3.2.0) and the built-in seed texts/icons/accents/weekdays (3.0.0); `archived`, `shortFor`/`subtitleFor`/`accentFor` and `OWN_TEMPLATE_ICONS` for your own templates (3.1.0) | Model |

## core/notifications

| path | what it does | talks to |
| --- | --- | --- |
| `core/notifications/AppVisibility.kt` | Whether any part of the app is on screen, so notices stay quiet when already visible | ActivityLifecycleCallbacks |
| `core/notifications/DismissalStore.kt` | Remembers swiped-away notices; a real transition clears the memory | PrefsStorage |
| `core/notifications/NotificationActionReceiver.kt` | Pause/Next buttons on a notification | DayOrchestrator, NotificationActionMapping |
| `core/notifications/NotificationModel.kt` | Channel/action enums and `NotificationModelMapper` (block → notification text); `walk()` builds the standalone walk notification (2.4.0); the "Dishes done · pick your evening" evening alert (2.5.0) | ScheduleEngine, DayState, TimeFormatter |
| `core/notifications/NotificationRenderer.kt` | `NotificationPort` plus the real renderer; the port exists so tests can fake it; the quiet `dishes` channel ("Dishes reminders", vibration only) for Dishes' end and 5-min reminders (3.3.0) | NotificationModel, AppVisibility, DismissalStore |
| `core/notifications/WalkRinger.kt` | `WalkRinger` plus the alarm-clock walk ringer (2.3.0): half-time and home rings, even on silent | NotificationRenderer, TimeFormatter |

## core/profiles

| path | what it does | talks to |
| --- | --- | --- |
| `core/profiles/Profiles.kt` | Profiles (3.2.0), pure: `ProfileBook`, `ProfileEdit`/`ProfileRefusal`, and `repair` (the 3.1 → 3.2 weekday move), `create`/`rename`/`delete`/`switchTo`, `addTemplate`/`removeTemplate`/`setDay`/`dropTemplate`, `suggestedFor`/`daysOf` | Model |

## core/schedule

| path | what it does | talks to |
| --- | --- | --- |
| `core/schedule/AlertPlanner.kt` | The pure rules for when an alert fires (5-min warning, Pomodoro breaks/starts, nags until the user reacts); walk rings at half time and the end (2.3.0); `warn` can be turned off for Dishes' quiet reminders (3.3.0) | Model |
| `core/schedule/DayTimeline.kt` | The real day on the calendar, pure (3.3.0): turns the plan and today's recorded `SegmentSpan`s into `TimelineBlock`s (phase DONE/NOW/AHEAD/MISSED, `overrunFromMin` the red tail, `lateMin`, pauses); a never-started block is MISSED, the running one grows live | Model |
| `core/schedule/PlanConversion.kt` | Converts `BlockSpec` to and from the editor's `PlanBlock`; preview and commit share it | Model, PlanEditor |
| `core/schedule/PlanEditor.kt` | The pure rules for reshaping today's plan (add, resize, retype, insert); free time absorbs, work blocks push, kid time stays; `END_AND_TYPE` lets a running work block also become another work type (2.2.0); walks: `walkEnd`/`insertWalk`/`shortenWalk` (2.3.0); `startEarly`/`nextStop` pick the very next block, no free-time skipping (2.6.0); `startFirstNow` moves the first block to now, its gap becomes free time (2.7.0) | Model |
| `core/schedule/RecordConversion.kt` | `toRecordRows`/`withRows`: day record ↔ editable rows for correcting a past day | Model, RecordEditor |
| `core/schedule/RecordEditor.kt` | The pure rules for editing a past day's rows: `fixMode`, `apply` (SetEnd/Retype/MarkDone, `MIN_LENGTH`/`LAST_MINUTE` limits), `snap` | Model |
| `core/schedule/ScheduleEngine.kt` | Resolves template + date into concrete blocks and segments; `nextWakeUp(anchor)` anchors the Pomodoro cursor on the open segment's actual start (2.2.0) | Model, Clock, TimeZone |
| `core/schedule/SegmentBook.kt` | Builds the segment list History renders; rehydrates it after process death; `kidAsleep` opens Dishes then the choice, `eveningChoice` switches it (2.5.0); `startDay` returns an empty book while the clock is before the first block (2.7.0) | ScheduleEngine, Model, PrefsStorage |
| `core/schedule/TemplateEditor.kt` | Editing one template's blocks in minutes (3.1.0): add/retype/start/length, later blocks pushed and kid time shrunk, a start inside an earlier block refused | Model, TemplateRules |
| `core/schedule/TemplateRules.kt` | `problems`/`normalize`: the rules every template follows (no overlap, kid time to 22:30, unique ids) (3.0.0); free time may be shorter than 10 min (3.1.0) | Model |
| `core/schedule/TimelineQueries.kt` | `ScheduleEngine` extensions that read the published plan: `timelineAt`, `currentBlock`, `pomodoroStatus`, `pomodoroPhases` | ScheduleEngine, Model |
| `core/schedule/TodayEditor.kt` | Home's block sheet on today's plan, pure (3.3.0): `TodayEdit`/`TodayRefusal`, `place`, with the template editor's push rule plus today's floors (nothing before the running block, nothing in the next 5 min, kid time and walks are walls); Delete is a `PlanEdit.Retype(id, Free)` | Model, PlanEditor |

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
| `core/storage/Actions.kt` | The use cases the profile screens write through: `TemplateActions` (`createIn`/`save`/`delete`; a save tells a waiting day) and `CategoryActions` (update/add/delete, rewriting using blocks) (3.1.0); `ProfileActions` (create/rename/delete/switchTo/addTemplate/removeTemplate/setDay) (3.2.0) | TemplateStore, CategoryStore, DayOrchestrator |
| `core/storage/CategoryStore.kt` | Loads/saves `CategorySet` from prefs and sets `Categories.current` (3.0.0); `update`/`add`/`delete` with `CategoryResult`, built-ins never deleted (3.1.0) | PrefsStorage, Categories |
| `core/storage/ExplainerStore.kt` | Remembers which first-visit explainer cards were seen (3.3.0): `ExplainerKey` (Profiles, Templates, Days, Blocks, Categories), `seen`/`markSeen`, `resetAll` (also the Profiles "New here?" hint) | PrefsStorage |
| `core/storage/TemplateStore.kt` | The stored templates and the `ProfileBook`; seeds from assets, validates on load, `all`/`get`/`suggestedFor` (3.0.0); `find`/`isBuiltIn`, the writes (`createIn`/`save`/`delete`/`replaceCategory`), broken own templates archived not crashing (3.1.0); `templates` StateFlow republished on every write and load (3.1.1); `profiles` StateFlow, `library`/`profileTemplates`/`edit`/`activeProfileName`, `Profiles.repair` on load and seed (3.2.0); `profileChosen`/`markProfileChosen` for the first-run main menu (3.3.0) | PrefsStorage, assets, TemplateRules, TemplateEditor, Profiles |
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
| `designsystem/Explainer.kt` | The first-visit explainer cards (3.3.0): `ExplainerKey`→`ExplainerCard` texts (moved from the tour), the non-dismissable `ExplainerDialog` ("Got it" only) and the shared texts the `HowToUse` tour reads | Compose, Tokens |
| `designsystem/Format.kt` | `formatSteps`: the one place step counts get thousands separators (2.5.0) | — |
| `designsystem/Motion.kt` | The app's only motion token | Compose animation |
| `designsystem/Primitives.kt` | Row-level primitives: `SettingsRow`, tonal chips, `ScreenColumn` | Tokens, Components, Color |
| `designsystem/Shell.kt` | `AppTab` (Home/Weekly/Insights) and the bottom-bar / top-bar shell; the ≡ menu carries History/Profiles/Settings (3.2.0) | Color, Type, Tokens |
| `designsystem/TemplateDisplay.kt` | Title, subtitle and block preview for one template, read from `TemplateStore` (3.0.0); `iconFor` gains work/school/home icons (3.1.0) | Model, TemplateStore |
| `designsystem/Theme.kt` | Material color scheme and typography wiring | Color, Type |
| `designsystem/Tokens.kt` | `Spacing`, `AppShapes`, `Metrics` | Compose units |
| `designsystem/Type.kt` | Corner radii and the type scale | Compose units |

## feature

| path | what it does | talks to |
| --- | --- | --- |
| `feature/home/Home.kt` | Home screen, `HomeViewModel` (publishes the calendar, the now/walk cards, `walkView`, `calendarFit`/`pillLabels`, and previews plan edits), top bar, now-card dialogs, edit card; walk icon in top bar, standalone walk card support (2.4.0); the top-bar step count, evening built from segments with Dishes/switch/skip (2.5.0); the waiting card ("Day starts at HH:MM · in 1h 20m", Start now / Change day), the "Starts at HH:MM" status and `HomeState.calendarStartMin` (2.7.0); the real day (3.3.0): `HomeViewModel.publish` feeds `DayTimeline` the plan and today's segments, blocks carry phase/overrun/late/pauses, the minute ticker re-publishes so the tail grows live, and the edit card is the shared `BlockSheet` in `HomeState.sheet` | DayOrchestrator, HistoryManager, TemplateManager, DebugTimeController |
| `feature/home/HomeUi.kt` | `HomeState` and the calendar/block/now-card/walk UI models (`WalkCardUi.standalone`, `walkEnabled`, `calendarFit`, `pillLabels`) | Compose, designsystem |
| `feature/home/HomeSheets.kt` | Bottom sheets: evening choice (dismissible, 2.5.0), walk start (`WalkSheet`, 2.3.0), template swap, the recap's `CorrectBlockSheet` | DayOrchestrator, TemplateManager |
| `feature/home/NowCardDialogs.kt` | The now-card's dialog states and confirmations | DayOrchestrator |
| `feature/home/DayRecap.kt` | A finished day as a readable recap: summary card (+ check-in card until it's answered), one row per block | designsystem, Compose |
| `feature/home/CalendarMapper.kt` | Pure model→view mapping for the calendar; no clock, no context | DayCalendar |
| `feature/home/CalendarLayout.kt` | Pure geometry (2.6.0): `fit(blocks, ticks, dayStartMin)` returns each block's `TextFit`/sliver and the pill rows, so no text overlaps | — |
| `feature/home/DayCalendar.kt` | The Home calendar grid; draws exactly what `CalendarLayout.fit` returns — slivers, block text, walk ticks and the pill rows (2.6.0); the real day (3.3.0): a red striped overrun tail, a next block shrinking from the top, grey pause stripes, faded "Missed" blocks and the "Coding · missed" pill | designsystem, CalendarLayout |
| `feature/history/History.kt` | History list and the day-detail recap, plus both ViewModels; day detail shows "Walks" section for standalone walks (2.4.0) and each day's steps (2.5.0); each day shows the profile it started under (3.2.0) | HistoryManager, DayRecapMapper |
| `feature/weekly/Weekly.kt` | Weekly screen: planned vs actual per type as bars, week navigation; the week's step total tile (2.5.0) | HistoryManager |
| `feature/insights/Insights.kt` | Insights screen: last complete week vs the one before; the `Dishes` row ("N of M evenings over 20 min", average overrun) when any evening ran over (3.3.0) | HistoryManager |
| `feature/mainmenu/MainMenuScreen.kt` | The first-run main menu (3.3.0): `MainMenuViewModel` and big profile cards (templates and their days), + New profile, Categories explained, "Use this profile" → the picker; shown only until a profile was chosen and re-openable from Developer tools | TemplateStore, ProfileActions, DayOrchestrator |
| `feature/profiles/ProfilesScreen.kt` | The Profiles list (3.2.0): `ProfilesViewModel` (rows follow `store.profiles`, the `how_to_seen` hint, New profile with Start empty / Copy, Categories entry) and `ProfilesRoute`/`ProfilesScreen`; "+ New profile" is now a full-width button on top, the Categories row explains itself, and the first visit shows its explainer card (3.3.0) | TemplateStore, ProfileActions, PrefsStorage, ExplainerStore |
| `feature/profiles/ProfileScreen.kt` | One profile (3.2.0): `ProfileViewModel` (rename, Use this, the 7 day rows, the profile's templates with remove, + New template / + Add existing, Delete profile) and the stateless `ProfileScreen`; Off day is no longer clickable, "Built-in" stays on one line, and a first visit to a weekday shows its card (3.3.0) | TemplateStore, ProfileActions, TemplateActions, ExplainerStore |
| `feature/profiles/CategoriesScreen.kt` | The shared Categories screen (3.2.0): a back/"Categories"/? top bar around the unchanged `CategorySection` | CategorySection |
| `feature/profiles/HowToUse.kt` | The 5-card "How to use" tour (3.2.0): a full-height sheet with a `HorizontalPager`, dots, Back/Next ("Done" last); `HowToAction.Page`/`Close`; its card texts now come from `designsystem/Explainer.kt` (3.3.0) | designsystem |
| `feature/profiles/FirstVisit.kt` | The per-place first-visit explainer (3.3.0): `FirstVisitViewModel` (loads one `ExplainerKey`, marks it seen on "Got it") and `FirstVisit` wrapping the non-dismissable `ExplainerDialog`; also gates Days/Blocks/Categories | ExplainerStore, designsystem |
| `feature/templatepicker/TemplatePicker.kt` | Template picker with a live preview of the rest of today; a scrolling `LazyVerticalGrid` of cards, effects via `PickerEffect.Started` (3.1.1); the top profile switcher ("Unemployed ▾"), the ? tour button and the suggestion/days from the active profile (3.2.0) | DayOrchestrator, TemplateStore, ProfileActions |
| `feature/templates/TemplateEditorScreen.kt` | The template editor: the block list and preview strip; every accepted edit saved through `TemplateActions`, built-ins read-only (3.1.0); no weekday chips (days live on the profile page, 3.2.0); a blockless template (Off day) says so instead of computing a day end (3.3.0) | TemplateEditor, TemplateActions, TemplateStore |
| `feature/templates/BlockSheet.kt` | The one block sheet (3.1.0; generic `BlockSheetUi`/`BlockSheetAction` with `AddAfter`, rows shown only when set, 3.3.0): category, start, length, Add after, Delete/Save; the template editor maps its own `EditorSheet` through it and Home's today editor uses it for a block ahead or the running block | TemplateEditor |
| `feature/templates/CategorySection.kt` | `CategoryListViewModel` + the categories section: rows (built-ins, then yours) and the sheet that edits one (3.1.0) | CategoryStore, CategoryActions |
| `feature/templates/CategorySheet.kt` | The category sheet: name, `CategoryPalette`, Pomodoro, "counts as work" and Delete (yours only) (3.1.0) | CategoryInfo |
| `feature/settings/Settings.kt` | Settings screen, `SettingsViewModel`, developer tools in one dialog (test a day, debug clock, demo data); the step-counting row and permission state (2.5.0); "Show main menu" and "Reset explainers" (3.3.0) | DayOrchestrator, DemoDataGenerator, SystemStatuses, DebugTimeController |