# Daily Block Scheduler — Architecture Decisions

Describes app version **2.1.0** (the target of `Task-8.md`) · Companion to `schedule-spec.md`

Each section states the decision, then why. Rejected alternatives are collected in §17.

---

## 1. Principles

1. **Performance first.** Recompose only what changed; the per-minute and per-second ticks must never recompose the screen.
2. **No over-engineering.** No reducers, no use-case layer, no domain/data/presentation split. State + `onAction`, one ViewModel per screen, managers injected.
3. **Compose shows, never decides.** Composables render state and forward actions. All logic lives in ViewModels (bridge) and managers (work).
4. **One source of truth for the day.** The day state machine is app-level and shared by UI, alarms and notifications.
5. **Local only, silent by default.** No network, no vibration, no sound outside the day segment.

---

## 2. Stack

| Concern | Choice | Why |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3, dark only | Given. M3 gives accessible components; we only override the color scheme. |
| Min / target SDK | 34 / latest | Personal device only; no compat branches. |
| DI | Hilt | Compile-time graph, first-class support for ViewModels **and** `BroadcastReceiver`s (needed for alarms). |
| Navigation | Navigation 3 | Back stack is plain Compose state we own; fits MVI naturally. |
| Storage | SharedPreferences | Small data, simple, no schema migrations. |
| Serialization | kotlinx.serialization (JSON) | Compile-time, respects nullability + defaults, sealed classes out of the box, R8-safe without reflection keep rules. |
| Time | kotlinx-datetime + injected `Clock` | Testable time; clean `LocalTime` / `Instant` split. |
| Scheduling | AlarmManager exact alarms | Block ends must fire on the minute. |
| Collections in state | kotlinx.collections.immutable | Makes list-typed state stable for Compose. |
| Logging | Thin wrapper + rolling file on device | Post-mortem of "why didn't it fire at 14:00" without a cable attached. |
| Build | Version catalog, R8 minify on release | Given. |

---

## 3. Package layout (single module, by feature)

```
com.roni.blocks
├── App.kt                         @HiltAndroidApp
├── MainActivity.kt                single activity, hosts NavDisplay
├── core/
│   ├── daystate/                  DayState, DayEvent, DayStateMachine, DayOrchestrator
│   ├── schedule/                  ScheduleEngine (pure time math), SegmentBook, TimelineQueries, PlanEditor, DayTimeline, TodayEditor
│   ├── model/                     BlockType, Template, BlockSpec, Segment, DayRecord
│   ├── time/                      Clock + TimeZone providers, Ticker
│   ├── storage/                   PrefsStorage, TemplateManager, HistoryManager
│   ├── alarms/                    AlarmScheduler, AlarmReceiver, SystemEventsReceiver
│   ├── notifications/             AppNotifier, channels, NotificationActionReceiver
│   ├── logging/                   Logger, RollingFileLogger
│   └── di/                        Hilt modules
├── designsystem/                  Theme, color scheme, BlockColors, typography
├── navigation/                    NavKeys, AppNavDisplay, top-right menu
└── feature/
    ├── templatepicker/
    ├── mainmenu/                  the first-run main menu (3.3.0)
    ├── home/                      HomeRoute, HomeScreen, HomeState, HomeAction, HomeEffect, HomeViewModel, components/
    ├── weekly/
    ├── insights/
    ├── history/
    └── settings/
```

This is the shape; `CODEMAP.md` in the repo lists every real file.

**Why single module:** one developer, one device, fast builds. Package-by-feature keeps each screen's five files together; `core/` holds everything shared. Splitting into Gradle modules later is mechanical because feature packages only depend on `core` and `designsystem`.

---

## 4. Day state machine

**Location:** `core/daystate/` — app level, never inside a ViewModel.

```kotlin
sealed interface DayState {
    data object NotStarted : DayState
    data class Running(val template: TemplateId, val blockIndex: Int) : DayState
    data class Paused(val running: Running, val since: Instant) : DayState
    data object KidTime : DayState
    data class Evening(val blockIndex: Int) : DayState
    data object Ended : DayState
    data object Off : DayState
}

sealed interface DayEvent {
    data class StartDay(val template: TemplateId) : DayEvent
    data object TakeOffDay : DayEvent
    data class Next(val expectedBlockId: String) : DayEvent   // "Next slot": any time; stale ids ignored
    data object Pause : DayEvent
    data object Resume : DayEvent
    data object KidTimeStarted : DayEvent
    data object KidAsleep : DayEvent
    data class EveningChoice(val choice: EveningChoice) : DayEvent
    data class EndDay(val reason: EndReason) : DayEvent   // NORMAL, EMERGENCY
}
// Legacy: a persisted `Interrupted` state restores as `Running` on the same block (Task-5 §2).
```

**Today's plan (2.0.0):** the orchestrator also owns `TodayPlan` (§8), saved with `day_state` in one commit and exposed as `todayPlan: StateFlow<TodayPlan?>`. `Running.blockIndex` indexes its blocks and is re-pointed by id after every change. Edits don't go through the state machine: `suspend fun editPlan(edit: PlanEdit): Boolean` applies the pure `PlanEditor` rules (§9), saves, moves the open segment's planned end to the current block's new end, reschedules the alarm and re-renders the notification silently. Home calls the same `PlanEditor` for its live preview, with the same `currentForEditing()` and clock, so preview and commit agree.

Split into two small pieces:

| Piece | Responsibility | Why separate |
|---|---|---|
| `DayStateMachine` | Pure function `transition(state, event, now): DayState?`. Illegal transitions return `null` and are logged. | No Android, no coroutines → trivially unit-tested. This is where bugs would hurt most. |
| `DayOrchestrator` (`@Singleton`) | Holds `StateFlow<DayState>`, accepts events from anywhere, persists every transition with `commit()`, then runs side effects: reschedule the next alarm, update notifications, flush on `Ended`. | ViewModels, `AlarmReceiver` and notification action buttons all send events here. It's the single place that decides whether anything may make noise. |

**Silence guarantee:** `AppNotifier` refuses to post unless the current `DayState` allows it (nothing in `Ended`, `Off`, or before the 9:00 prompt). The rule lives in one function, not scattered across callers.

**Persistence:** saved synchronously (`commit()` on `Dispatchers.IO`) before side effects run. An alarm or a notification action may arrive after Android killed the process, so every entry point (`Application.onCreate`, `send()`, each receiver, the UI gate) first calls `ensureRestored()`: single-flight, a bounded replay of missed transitions, `isRestored` set in a `finally`, errors logged and the safest state kept. The splash waits for `isRestored` at most 3 seconds (`Task-6.md` Slice 1).

---

## 5. Screen pattern (MVI, your variant)

No reducer, no use cases. Per screen:

| File | Content |
|---|---|
| `XState` | `@Immutable data class`, all fields `val`, lists as `ImmutableList`. |
| `XAction` | `sealed interface` of everything the user can do. |
| `XEffect` | `sealed interface` of one-off events (navigate, show dialog). |
| `XViewModel` | `MutableStateFlow<XState>` updated only via `_state.update { it.copy(...) }`; `fun onAction(action: XAction)`; `MutableSharedFlow<XEffect>`; managers injected. |
| `XRoute` | Gets the ViewModel, collects state/effects, passes slices + `viewModel::onAction` down. The only composable that knows the ViewModel exists. |
| `XScreen` + components | Stateless. Parameters in, `onAction(...)` out. |

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val day: DayOrchestrator,
    private val schedule: ScheduleEngine,
    private val ticker: Ticker,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    val minuteOfDay: StateFlow<Int> = ticker.minutes.stateIn(viewModelScope, WhileSubscribed(5_000), 0)
    val leakSeconds: StateFlow<Int?> = /* seconds flow, only active while leaking */

    private val _effects = MutableSharedFlow<HomeEffect>()
    val effects: SharedFlow<HomeEffect> = _effects.asSharedFlow()

    fun onAction(action: HomeAction) = when (action) {
        is HomeAction.Next -> day.send(DayEvent.Next(expectedBlockId = action.blockId))
        HomeAction.Pause -> day.send(DayEvent.Pause)
        HomeAction.Emergency -> day.send(DayEvent.EndDay(EndReason.EMERGENCY))
        HomeAction.SwapTemplate -> emit(HomeEffect.OpenTemplatePicker)
        // ... (action names are illustrative)
    }
}
```

**Why state comes from the ViewModel and not `remember`:**

| | ViewModel `StateFlow` | `remember` |
|---|---|---|
| Survives rotation / config change | Yes | No (`rememberSaveable` only for primitives/parcelables) |
| Reachable by alarms and notifications | Yes, via `DayOrchestrator` | No |
| Unit-testable without Compose | Yes | No |
| Single source of truth | Yes | Creates a second copy of the truth |

`remember` is used only for UI-local objects with no business meaning: `ScrollState`, `TextMeasurer`, animation state. `derivedStateOf` only where an input changes more often than the output (e.g. "is the header collapsed" from scroll offset).

**Collection:** `collectAsStateWithLifecycle()` everywhere — collection stops when the app is backgrounded, so tick flows stop costing anything off-screen.

**Effects:** `SharedFlow<Effect>` collected in a `LaunchedEffect` inside `XRoute`, using `repeatOnLifecycle(STARTED)` semantics so an effect is never handled while the screen is stopped.

---

## 6. Dependency injection

All managers are `@Singleton`, grouped by responsibility:

| Manager | Owns |
|---|---|
| `DayOrchestrator` | Day state, today's plan, transitions, edits, persistence, side-effect dispatch |
| `ScheduleEngine` | Pure time math: template + actual segments → timeline, leak, shrink, synthetic Free blocks, next wake-up time |
| `TemplateManager` | Loads templates from the bundled JSON asset once, caches in memory |
| `PrefsStorage` | Low-level typed read / write / observe over SharedPreferences files |
| `HistoryManager` | Day records, week aggregates, 1-year pruning, flush; `observeDay(date)` re-reads after every write (2.0.0) |
| `PlanEditor` (pure `object`) | The editing rules (2.0.0); used by Home's preview and the orchestrator's commit |
| `AlarmScheduler` | The single pending exact alarm |
| `AppNotifier` | Channels, posting, the silence gate |
| `Ticker` | Minute-aligned tick flow; seconds flow on demand |
| `Logger` | Log wrapper + rolling file |
| `DebugTimeController` | Debug builds only: shifts the debug clock and re-evaluates the day (Task-6) |

`Clock` and `TimeZone` are provided by a Hilt module (`Clock.System`, `TimeZone.currentSystemDefault()`), replaced by fakes in tests.

Receivers (`AlarmReceiver`, `NotificationActionReceiver`, `SystemEventsReceiver`) are `@AndroidEntryPoint` and use `goAsync()` for suspend work.

---

## 7. Storage

**Files:**

| Prefs file | Keys |
|---|---|
| `day` | `day_state` (current `DayState`), `today_plan` (`TodayPlan`, 2.0.0), `today_segments` (actual segments so far), `today_date`, `profile_name` (the profile the Off day started under, 3.2.0) |
| `settings` | user settings; 3.2.0 also `profiles` (the `ProfileBook`'s profiles), `active_profile`, `how_to_seen` |
| `history` | `record_YYYY-MM-DD` (one `DayRecord` per day), `agg_week_YYYY-MM-DD` (week start date). Old `debt_*` keys are ignored (Time Debt removed in 2.0.0). |

**Why history is its own file:** SharedPreferences loads a whole file into memory on first access. Keeping a year of history out of `day` means the hot path (alarm fires → read state → post) never pays for history. The history file is touched only at flush and when history screens open.

**API:**

```kotlin
class PrefsStorage {
    suspend fun <T> write(file: PrefsFile, key: String, value: T, serializer: KSerializer<T>)  // commit() on IO
    suspend fun <T> read(file: PrefsFile, key: String, serializer: KSerializer<T>): T?
    fun <T> observe(file: PrefsFile, key: String, serializer: KSerializer<T>): Flow<T?>      // callbackFlow + change listener
}
```

- Writes use `commit()` inside a suspend function on `Dispatchers.IO`, so data is on disk before the caller continues (matters for state persisted right before an alarm is scheduled).
- `observe` wraps `OnSharedPreferenceChangeListener` in a `callbackFlow`. Android holds that listener **weakly**; the flow's closure holds it strongly for as long as it's collected, and `awaitClose` unregisters it.
- Retention: records older than 1 year are removed during the day-end flush.

**Templates** ship as `assets/templates.json`, parsed once by `TemplateManager`. Changing a template = edit JSON + rebuild.

---

## 8. Data model

```kotlin
@Serializable sealed interface BlockType {
    @Serializable data object Cv : BlockType
    @Serializable data object Coding : BlockType
    @Serializable data object Gym : BlockType
    @Serializable data object Free : BlockType
    @Serializable data object KidTime : BlockType
    @Serializable data object Dishes : BlockType
    @Serializable data class EveningChoice(val choice: Choice) : BlockType  // GAME, PROJECT, READ, CHILL
}

@Serializable data class BlockSpec(
    val id: String,                     // stable, unique across templates, e.g. "mon_thu.gym" (Task-5)
    val type: BlockType,
    val start: LocalTime,
    val end: LocalTime,
    val pomodoro: Pomodoro? = null,     // 50/10 for Coding
    val quiet: Boolean = false,         // KidTime: no alerts
)

@Serializable data class Template(val id: TemplateId, val name: String, val blocks: List<BlockSpec>)

@Serializable data class Profile(           // 3.2.0: one profile is a name, the library templates it uses, and which one each weekday suggests
    val id: String,
    val name: String,
    val templateIds: List<TemplateId>,      // picker order, Off always last
    val days: Map<Int, TemplateId>? = null, // ISO day (Mon = 1 … Sun = 7) → the template; null in 3.1 data until Profiles.repair
)

@Serializable data class Segment(       // what actually happened
    val type: BlockType,
    val kind: SegmentKind,              // PLANNED, SYNTHETIC_FREE, PAUSE, INTERRUPTION, CONSUMED
    val plannedStart: Instant?, val plannedEnd: Instant?,
    val actualStart: Instant, val actualEnd: Instant?,
    val blockId: String? = null,        // the plan block (2.0.0); synthetic free: the work block it came from
    val missReason: MissReason? = null, // CONSUMED only: OVERRUN or LATE_START; null = OVERRUN (older records)
)

@Serializable data class TodayPlan(     // 2.0.0: today's own blocks, saved with the day state
    val templateId: TemplateId,
    val blocks: List<BlockSpec>,        // sorted, ids unique; past blocks stay
    val edited: Boolean = false,
    val profileName: String? = null,    // 3.2.0: the profile the day started under
)

@Serializable data class DayRecord(val date: LocalDate, val template: TemplateId?, val segments: List<Segment>, val flags: Set<DayFlag>, val plannedBlocks: List<BlockSpec>? = null, val demo: Boolean = false, val profileName: String? = null)
// DayFlag: OFF_DAY, EVENING_SKIPPED, GYM_MISSED, AUTO_ENDED, EMERGENCY_STOP, TEMPLATE_SWAPPED, PLAN_EDITED. `demo` marks debug-generated records (Task-5).
// plannedBlocks = the day's final plan. `planStartsAt` (Task-6) is retired in 2.0.0; a restore migrates it into `today_plan`.
```

**Why templates use `LocalTime` and runtime uses `Instant`:** a template is "9:30 every day"; what happened is a point in time. Leak = `now - plannedEnd`, computed on `Instant`s, never on wall-clock strings.

---

## 9. Time accounting

All rules from the schedule spec live in `ScheduleEngine` as pure functions:

- **Overrun:** next block keeps its planned end; its effective start = actual end of the previous one. Fully eaten → `CONSUMED` segment (`missReason = OVERRUN`), shown as **missed**.
- **Late start:** `StartDay` at time T records the blocks that ended by T as `CONSUMED` with `LATE_START` and lands on the block containing T, exactly as if the clock had reached it.
- **Next slot early:** on a work block (CV, Coding, Gym) the block closes now (under-time recorded) and a `SYNTHETIC_FREE` segment runs until its planned end, then the next block starts on schedule. On a Free block (planned or synthetic) the next block starts now and keeps its planned end.
- **Pause:** work blocks only; recorded as a `PAUSE` segment. No end time moves: the pause shortens the current block only and auto-closes at its end. Pause time is tracked separately and is not debt. Leak = now − planned end.
- **Emergency:** `EndDay(EMERGENCY)` ends the day with flag `EMERGENCY_STOP`; nothing after it counts as planned time or debt.
- **Pomodoro:** focus/break phases derived from the block's **actual** start (50/10 repeating, last phase cut at the end); phase switches need no user action and never leak.
- `SYNTHETIC_FREE` auto-ends and never leaks; `INTERRUPTION` now only means device-off downtime.
- **Identity:** blocks are identified by id and type, never by position or template id; segments carry their block's id (2.0.0). Day records store the final plan.
- **Swap template** (`DayEvent.SwapTemplate`, from any started state, refused after 22:30): recorded segments stay and the open one closes at now. Today's plan keeps its blocks up to now (up to the end, if the day had ended), the last one cut there, then takes the new template's blocks from now: the one containing now starts now, earlier ones are left out (not missed). A new block whose id is already kept (a swap to the same template) gets `<id>@<startMin>`. Flag `TEMPLATE_SWAPPED`.
- **Editing** (`PlanEditor`, pure, 2.0.0): `editMode()` says what a block offers (`FULL`, `END_ONLY`, `TYPE_ONLY`, `NONE`) and `apply()` returns the new plan or null. Work blocks keep their length when pushed; free time and gaps absorb; kid time is a fixed point; nothing before the current block changes; the current block's end only, never into the next 5 minutes; 5-minute steps, 10-minute minimum; new free blocks get deterministic ids (`edit-1`, `edit-2`, …). On today (`TodayEditor`, pure, 3.3.0) the same push rule applies with three extras: nothing up to the running block moves, nothing starts in the next 5 minutes, and kid time and walks are walls (no room before one → the sheet says why, Save off); Delete is `PlanEdit.Retype(id, Free)`.
- **Home's real day** (`DayTimeline`, pure, 3.3.0): the calendar draws what happened, not only the plan — past blocks at their recorded times, pauses as grey stripes, a never-started block MISSED, the running block growing live with its overrun as a red tail (`overrunFromMin`) and the next block shrinking from the top; after Next the tail stays and the next block shows its `lateMin`. Blocks ahead are still the plan and edit as before.

These are the unit-tested core (§16).

---

## 10. Alarms and notifications

**Exact alarms:** `USE_EXACT_ALARM` permission (granted automatically for a sideloaded app; `SCHEDULE_EXACT_ALARM` is denied by default on Android 14). `POST_NOTIFICATIONS` is requested once on first launch.

**One pending alarm at a time.** `ScheduleEngine.nextWakeUp(state, now)` returns the earliest of: next block end, next Pomodoro switch, next 9:00 re-prompt, the 20:00 prompt, 22:30 day end, midnight fallback. `AlarmScheduler` replaces the single `PendingIntent` every time the state changes.

Why: no bookkeeping of many alarms, cancelling everything on `EndDay` is one call, and the next alarm is always derived from the true state.

API: `setExactAndAllowWhileIdle`. Note that in deep Doze Android throttles allow-while-idle alarms to roughly one per ~9 minutes per app; our alarms are 10+ minutes apart, but if the logs ever show late firings, the fallback is `setAlarmClock` (most reliable, shows an alarm icon in the status bar).

**No foreground service.** Alarms fire a receiver, the receiver posts a normal notification. On Android 14, non-FGS "ongoing" notifications can be swiped away; that's acceptable (the day still ends at 22:30).

**One notification, always the same ID** (redesigned in `Task-4.md` §1):

| Channel | Importance | Sound | Vibration | Used for |
|---|---|---|---|---|
| `day` | Default | Yes (on transitions only) | No | 9:00 prompt, current block, block ended, Pomodoro break/focus |
| `evening` | Low | No | No | 20:00 **Kid asleep / End day**, dishes, evening choice |
| `dishes` | Default | No | Yes (pattern `0,400,200,400`) | Dishes' planned end and a 5-minute reminder while it runs over (3.3.0) |

- Exactly one notification ID. At most one notification is ever visible; it is updated in place.
- The countdown is the system chronometer (`setUsesChronometer` + `setChronometerCountDown`, `when` = effective end). Android draws it; the app never wakes up to update it. Past the end it shows negative time (the leak).
- Transitions post with `setOnlyAlertOnce(false)` (sound on `day`); refreshes and anything while the app is visible post with `setOnlyAlertOnce(true)`.
- Moving from `day` to `evening` cancels and re-posts the same ID on the other channel.
- Tapping the notification opens the app like the launcher icon does: it resumes the existing task exactly as it was, and does nothing if the app is already in front.
- Action buttons go through `NotificationActionReceiver` → `DayOrchestrator.send(...)` and carry the block id they were created for; stale actions are ignored. The app never opens from an action.
- Old channels `status` and `day_alerts` are deleted on startup. Channel sound/importance is locked by Android once created; behavior changes need a new channel ID.
- **Dishes never ends by itself (3.3.0).** It runs until *Done dishes* or Next; at its planned end and every 5 minutes after, the `dishes` reminder (`AlertPlanner.next(dishesStart, dishesEnd, …, warn = false)`) vibrates only — no sound, no 5-minute warning. The picked activity starts when the user finishes. History shows "Dishes 20:30–20:57 · 27 min (+7)" in red; Insights has a Dishes row.

---

## 11. Reboot, update, time change

`SystemEventsReceiver` handles `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`:

1. Restore `DayState` + today's segments from prefs.
2. Replay missed transitions against `Clock.now()`.
3. Record device downtime as an `INTERRUPTION` segment (not leak — the phone being off isn't on you).
4. If 22:30 already passed → silent end-of-day flush.
5. Schedule the next alarm. Post nothing.

---

## 12. Compose performance

**Stability**
- Every state class is `@Immutable` (not `@Stable`: `@Stable` is for types holding observable mutable state; ours never change after construction).
- Lists are `ImmutableList` / `PersistentList`. Plain `List<T>` is inferred unstable because it could be mutable underneath.
- Strong skipping is on by default (Kotlin 2.0.20+), which also auto-remembers lambdas, so `viewModel::onAction` passed down doesn't break skipping.

**Flat hierarchy, sliced state**
`HomeScreen` receives `HomeState` and hands each child only the fields it reads:

```
HomeRoute (collects; does NOT read tick values)
└── HomeScreen(state, minuteOfDay: () -> Int, leakSeconds: () -> Int?, onAction)
    ├── TopBar(templateName, onAction)
    ├── DayCalendar(blocks, currentBlockIndex, minuteOfDay)
    │   ├── grid          — drawBehind, no composables
    │   ├── BlocksLayout  — recomposes only when blocks / currentBlockIndex change
    │   └── NowLine       — reads minuteOfDay() in draw phase only
    └── ControlBar(blockTitle, countdown slice, isPaused, canNext, onAction)
        └── Countdown(leakSeconds)   — the only composable that recomposes per second, and only while leaking
```

A composable recomposes only when a parameter it received changed. Nothing receives more than it needs.

**Ticks**
- `Ticker.minutes` emits aligned to the minute boundary (delay until next `:00`), not every 60 s from app start.
- High-frequency values are passed as **lambdas** (`() -> Int`) created from the `State` returned by `collectAsStateWithLifecycle`. The value is read where it's used, so `HomeRoute` and `HomeScreen` never recompose on a tick.
- Countdown: minutes normally ("12 min left"); `mm:ss` only while leaking. The seconds flow exists only while leaking and the screen is visible.

**Calendar rendering (three layers)**

| Layer | Technique | Recomposes on |
|---|---|---|
| Hour grid + labels (8:00–23:00) | `Modifier.drawBehind` + `TextMeasurer` (remembered) | Never |
| Blocks | Composables in a custom `Layout`, `y = minutesSince8 × pxPerMinute` | Block list or current-block change |
| Now line | `drawWithContent`, reads `minuteOfDay()` inside the draw lambda | Never — redraw only |
| Pomodoro bands and labels (2.0.0) | `drawWithContent` on Coding blocks; labels measured in the draw lambda | Never — redraw only |
| Edit slides (2.0.0) | One `Animatable` pair per block, read in the blocks `Layout`'s measure block | Never — relayout only; a drag preview recomposes at most once per 5-minute step |

Hour height: 80dp (2.0.0, room for break labels and handles). Current block: elevated (`graphicsLayer { shadowElevation; scale }`), others slightly reduced alpha. Emphasis changes happen via `graphicsLayer` lambdas, i.e. draw phase, not recomposition.

**Why not LazyColumn:** the day is a fixed, short range; lazy lists add item bookkeeping and recycling for no benefit, and positioning blocks by minute is awkward in a row-based list.

---

## 13. Navigation

- Navigation 3: the back stack is a Compose-owned list of `@Serializable` `NavKey`s rendered by `NavDisplay`, with ViewModels scoped per entry.
- Launch flow: the start screen is chosen once, after the saved state is restored (the splash stays up until then). `NotStarted` → **Main menu** when no profile was ever chosen, otherwise **Template picker** (full screen); any other state → **Home**. After `StartDay` / `TakeOffDay` the stack is replaced with **Home**. A started day never reopens the picker; only the swap button and Settings → Reset today go back to it.
- **First-run main menu (3.3.0):** shown only until a profile was chosen once (`TemplateStore.profileChosen()`); big profile cards, + New profile, Categories explained, "Use this profile" → the picker. Existing installs count as chosen; Developer tools → Show main menu brings it back.
- **Bottom bar (3):** Home · Weekly · Insights.
- **Top-right menu:** History · Profiles · Settings (pushed onto the stack). Profiles lists the profiles and reaches one profile (its templates and days) and the shared Categories. Time Debt was removed in 2.0.0.
- Swap template: icon on Home → (a confirm dialog if the day has ended; refused after 22:30) → template picker → `SwapTemplate`, then the picker is replaced with Home.
- **Structure and insets:** the tab screens share one `Scaffold` with the bottom bar; menu screens are their own entries outside it. Every `Scaffold` uses `contentWindowInsets = WindowInsets(0, 0, 0, 0)`. The status bar inset is applied only by each screen's top bar; the navigation bar inset only by `NavigationBar` (tabs) or the scrolling content's bottom padding (menu screens); the template picker pads with `safeDrawing` once.
- **Transitions:** pushing, popping, the predictive back gesture and switching tabs all use one 250 ms crossfade token.

---

## 14. Errors and logging

- Every `viewModelScope` / app scope launch uses a shared `CoroutineExceptionHandler` that logs and, for UI scopes, pushes an error slice into state.
- Caveat: a handler catches the failure but the failed coroutine is still dead. The `DayOrchestrator` state collection and the tick flows must survive, so those also use `.catch { log; emit(fallback) }` so one bad read doesn't stop the app from firing alarms.
- Logger: thin wrapper over `android.util.Log` + a rolling file in `filesDir` (a few files × ~1 MB). Every transition, alarm schedule/fire and notification post is logged. Settings can share the log file.

---

## 15. Analytics

- **At flush (day end):** write the `DayRecord` (its `plannedBlocks` = the final plan) and update that week's aggregate (minutes per block type, ran over, missed, under-time, pause time, flags incl. emergency stops and edits).
- **On open (Weekly / Insights):** read aggregates, except Weekly's plan-vs-actual card, which reads the week's seven day records (2.0.0: it needs each day's final plan). Insights computed on read: per-block average overrun and trend, missed-gym count, evenings skipped, pause time, Chill share of evenings, off days.
- Week starts Sunday.

---

## 16. Testing and verification

- Unit tests: `DayStateMachine` (every legal/illegal transition), `PlanEditor` (the editing rules; compiled and checked by the planning agent before handover), `ScheduleEngine` (leak, shrink, consumed, Next slot early on work vs free blocks, pause, emergency stop, Pomodoro phases from the actual start, reboot replay), aggregate math, and ViewModels driven through `onAction`. Driven by a fake `Clock`.
- Testing policy (`SUMMARY.md` §15): new tests are logic-level JVM tests that the task names, including one regression test per logic bug. The existing UI expectation and recomposition tests stay green; no new screenshot or geometry tests; Roni runs the device tests.
- Performance: Macrobenchmark on Home (startup + scrolling + a minute tick), plus Compose compiler stability reports as a build-time sanity check.

---

## 17. Rejected alternatives

| Rejected | Reason |
|---|---|
| Room | Data is small and document-shaped (one record per day). Revisit if history queries get complex. |
| DataStore | Would work, but SharedPreferences with a suspend/Flow wrapper covers the same needs with less setup. |
| Gson | Reflection-based: ignores Kotlin nullability/defaults, no sealed class support without adapters, fragile under R8. |
| WorkManager | Not exact; block ends must fire on time. |
| Foreground service | Not needed for alarm-driven notifications; costs battery and a permanent notification. |
| Per-second notification updates | Each update is an IPC to the system; transitions only. |
| Reducer / use-case layers | Boilerplate without benefit at this size. |
| `remember` for business state | Lost on config change, invisible to alarms, untestable. |
| LazyColumn calendar | Wrong tool for a fixed, short, minute-positioned layout. |
| Tick inside screen state | Would recompose the whole screen every tick. |
| Tap-to-edit sheet only, no dragging | Roni chose drag handles (7 Oct); the edit card's ±5-minute steppers cover precise and long moves |
| A separate Time Debt screen | Removed in 2.0.0: it duplicated Weekly, Insights and History, and the "debt" was never paid back |
| Change-history comments in code files | Go stale and duplicate git; history lives in commit messages, `NOTES.md` and `CHANGELOG.md` (§20) |

---

## 18. Settings

- Version label `V {versionName}` bottom-right, read from `BuildConfig.VERSION_NAME` (`buildConfig = true`). Versioning policy: `SUMMARY.md` §14.
- Entries: notification permission, exact alarms, test notification, alert sound (opens Android's settings for the `day` channel), 24-hour / 12-hour, share log file, reset today, clear history, "All data stays on this device". Every action answers with a snackbar. Debug builds add a Developer section: the debug clock card (jump to, move forward, evening) and demo data. Behavior: `Task-5.md` Slice 3 and `Task-6.md` Slices 7–8.

---

## 19. Visuals

Palette, block colors, calendar range (08:00–23:00) and all screen layouts are defined in `visuals.md`.

## 20. Code map and comments

- `CODEMAP.md` (repo root): one line per source file: what it does, what it talks to. Updated in the same commit as any file that's added, removed or repurposed.
- Core classes carry a short KDoc header: the responsibility, the 1–3 rules that must stay true, and "Talks to: …".
- No change history in code comments; see `SUMMARY.md` §16.

## 21. 2.1.0 additions

- Docs live in `docs/`; the working agent's instructions are `docs/AGENT.md`.
- `PlanEditor` gains `PlanEdit.Insert(freeBlockId, type)` (verified; 17 tests plus the randomized invariant check).
- `RecordEditor` (pure, verified) corrects a stored day on rows in minutes; `RecordConversion.kt` maps segments to rows and back; `HistoryManager.correctDay()` writes the record, adds `CORRECTED` and rebuilds the week aggregate. `HistoryDetailViewModel` observes the day, so corrections and check-ins show at once.
- `DayRecord.checkIn: CheckIn? = null` (GOOD, OK, ROUGH, SKIPPED).
- Home builds the evening blocks from the day's Dishes and evening-choice segments, and finds the current evening block by id (`EVENING_DISHES_ID`, `EVENING_CHOICE_ID`), never by `Evening.blockIndex`.
- Widget: `core/widget/BlocksWidget` (AppWidgetProvider, Hilt entry point) and `WidgetUpdater`, fed by the same `NotificationModel` as the notification wherever the orchestrator renders it. RemoteViews with a `Chronometer`; no timer, no new dependency.
- Roborazzi is removed; UI tests only where they check behavior.
