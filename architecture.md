# Daily Block Scheduler — Architecture Decisions

Version: v1.0.0 (architecture) · Companion to `schedule-spec.md`

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
│   ├── schedule/                  ScheduleEngine (pure time math), timeline building
│   ├── model/                     BlockType, Template, BlockSpec, Segment, DayRecord
│   ├── time/                      Clock + TimeZone providers, Ticker
│   ├── storage/                   PrefsStorage, TemplateManager, HistoryManager
│   ├── alarms/                    AlarmScheduler, AlarmReceiver, SystemEventsReceiver
│   ├── notifications/             AppNotifier, channels, NotificationActionReceiver
│   ├── insights/                  InsightsManager (aggregates + analysis)
│   ├── logging/                   Logger, RollingFileLogger
│   └── di/                        Hilt modules
├── designsystem/                  Theme, color scheme, BlockColors, typography
├── navigation/                    NavKeys, AppNavDisplay, top-right menu
└── feature/
    ├── templatepicker/
    ├── home/                      HomeRoute, HomeScreen, HomeState, HomeAction, HomeEffect, HomeViewModel, components/
    ├── weekly/
    ├── insights/
    ├── history/
    ├── timedebt/
    └── settings/
```

**Why single module:** one developer, one device, fast builds. Package-by-feature keeps each screen's five files together; `core/` holds everything shared. Splitting into Gradle modules later is mechanical because feature packages only depend on `core` and `designsystem`.

---

## 4. Day state machine

**Location:** `core/daystate/` — app level, never inside a ViewModel.

```kotlin
sealed interface DayState {
    data object NotStarted : DayState
    data class Running(val template: TemplateId, val blockIndex: Int) : DayState
    data class Paused(val running: Running, val since: Instant) : DayState
    data class Interrupted(val running: Running, val since: Instant) : DayState
    data object KidTime : DayState
    data class Evening(val blockIndex: Int) : DayState
    data object Ended : DayState
    data object Off : DayState
}

sealed interface DayEvent {
    data class StartDay(val template: TemplateId) : DayEvent
    data object TakeOffDay : DayEvent
    data object Next : DayEvent
    data object EndBlockEarly : DayEvent
    data object Pause : DayEvent
    data object Interrupt : DayEvent
    data class Resume(val mode: ResumeMode) : DayEvent   // RESUME_WHERE_I_WAS / JUMP_TO_NOW
    data object KidTimeStarted : DayEvent
    data object KidAsleep : DayEvent
    data class EveningChoice(val choice: EveningChoice) : DayEvent
    data object EndDay : DayEvent
}
```

Split into two small pieces:

| Piece | Responsibility | Why separate |
|---|---|---|
| `DayStateMachine` | Pure function `transition(state, event, now): DayState?`. Illegal transitions return `null` and are logged. | No Android, no coroutines → trivially unit-tested. This is where bugs would hurt most. |
| `DayOrchestrator` (`@Singleton`) | Holds `StateFlow<DayState>`, accepts events from anywhere, persists every transition with `commit()`, then runs side effects: reschedule the next alarm, update notifications, flush on `Ended`. | ViewModels, `AlarmReceiver` and notification action buttons all send events here. It's the single place that decides whether anything may make noise. |

**Silence guarantee:** `AppNotifier` refuses to post unless the current `DayState` allows it (nothing in `Ended`, `Off`, or before the 9:00 prompt). The rule lives in one function, not scattered across callers.

**Persistence:** saved synchronously (`commit()` on `Dispatchers.IO`) before side effects run. An alarm may fire after the process was killed; the receiver restores the true state from prefs before acting.

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
        HomeAction.Next -> day.send(DayEvent.Next)
        HomeAction.Pause -> day.send(DayEvent.Pause)
        HomeAction.SwapTemplate -> emit(HomeEffect.OpenTemplatePicker)
        // ...
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
| `DayOrchestrator` | Day state, transitions, persistence, side-effect dispatch |
| `ScheduleEngine` | Pure time math: template + actual segments → timeline, leak, shrink, synthetic Free blocks, next wake-up time |
| `TemplateManager` | Loads templates from the bundled JSON asset once, caches in memory |
| `PrefsStorage` | Low-level typed read / write / observe over SharedPreferences files |
| `HistoryManager` | Day records, time debt records, 1-year pruning, flush |
| `InsightsManager` | Weekly aggregates written at flush, analysis on read |
| `AlarmScheduler` | The single pending exact alarm |
| `AppNotifier` | Channels, posting, the silence gate |
| `Ticker` | Minute-aligned tick flow; seconds flow on demand |
| `Logger` | Log wrapper + rolling file |

`Clock` and `TimeZone` are provided by a Hilt module (`Clock.System`, `TimeZone.currentSystemDefault()`), replaced by fakes in tests.

Receivers (`AlarmReceiver`, `NotificationActionReceiver`, `SystemEventsReceiver`) are `@AndroidEntryPoint` and use `goAsync()` for suspend work.

---

## 7. Storage

**Files:**

| Prefs file | Keys |
|---|---|
| `day` | `day_state` (current `DayState`), `today_segments` (actual segments so far), `today_date` |
| `settings` | user settings |
| `history` | `record_YYYY-MM-DD` (one `DayRecord` per day), `debt_YYYY-MM-DD`, `agg_week_YYYY-MM-DD` (week start date) |

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
    val type: BlockType,
    val start: LocalTime,
    val end: LocalTime,
    val pomodoro: Pomodoro? = null,     // 50/10 for Coding
    val quiet: Boolean = false,         // KidTime: no alerts
)

@Serializable data class Template(val id: TemplateId, val name: String, val blocks: List<BlockSpec>)

@Serializable data class Segment(       // what actually happened
    val type: BlockType,
    val kind: SegmentKind,              // PLANNED, SYNTHETIC_FREE, PAUSE, INTERRUPTION, CONSUMED
    val plannedStart: Instant?, val plannedEnd: Instant?,
    val actualStart: Instant, val actualEnd: Instant?,
)

@Serializable data class DayRecord(val date: LocalDate, val template: TemplateId?, val segments: List<Segment>, val flags: Set<DayFlag>)
// DayFlag: OFF_DAY, EVENING_SKIPPED, GYM_MISSED, AUTO_ENDED
```

**Why templates use `LocalTime` and runtime uses `Instant`:** a template is "9:30 every day"; what happened is a point in time. Leak = `now - plannedEnd`, computed on `Instant`s, never on wall-clock strings.

---

## 9. Time accounting

All rules from the schedule spec live in `ScheduleEngine` as pure functions:

- **Overrun:** next block keeps its planned end; its effective start = actual end of the previous one. Fully eaten → `CONSUMED` segment.
- **Early end:** under-time recorded + a `SYNTHETIC_FREE` segment until the planned end.
- **Pause:** recorded as a `PAUSE` segment; the current block's effective end = planned end + total paused time, and the next block keeps its planned end, so the pause comes out of the following block. Leak = now − effective end.
- **Interrupt:** freezes the day; `Resume(RESUME_WHERE_I_WAS)` shifts remaining planned times, `Resume(JUMP_TO_NOW)` marks skipped blocks `INTERRUPTION`. Not counted as debt.
- **Pomodoro:** focus/break phases derived from the block's start; phase switches need no user action and never leak.

These are the unit-tested core (§15).

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

- Exactly one notification ID. At most one notification is ever visible; it is updated in place.
- The countdown is the system chronometer (`setUsesChronometer` + `setChronometerCountDown`, `when` = effective end). Android draws it; the app never wakes up to update it. Past the end it shows negative time (the leak).
- Transitions post with `setOnlyAlertOnce(false)` (sound on `day`); refreshes and anything while the app is visible post with `setOnlyAlertOnce(true)`.
- Moving from `day` to `evening` cancels and re-posts the same ID on the other channel.
- Tapping the notification opens the app like the launcher icon does: it resumes the existing task exactly as it was, and does nothing if the app is already in front.
- Action buttons go through `NotificationActionReceiver` → `DayOrchestrator.send(...)` and carry the block id they were created for; stale actions are ignored. The app never opens from an action.
- Old channels `status` and `day_alerts` are deleted on startup. Channel sound/importance is locked by Android once created; behavior changes need a new channel ID.

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

Current block: elevated (`graphicsLayer { shadowElevation; scale }`), others slightly reduced alpha. Emphasis changes happen via `graphicsLayer` lambdas, i.e. draw phase, not recomposition.

**Why not LazyColumn:** the day is a fixed, short range; lazy lists add item bookkeeping and recycling for no benefit, and positioning blocks by minute is awkward in a row-based list.

---

## 13. Navigation

- Navigation 3: the back stack is a Compose-owned list of `@Serializable` `NavKey`s rendered by `NavDisplay`, with ViewModels scoped per entry.
- Launch flow: `NotStarted` → **Template picker** (full screen); after `StartDay` / `TakeOffDay` the stack is replaced with **Home**.
- **Bottom bar (3):** Home · Weekly · Insights.
- **Top-right menu:** History · Time Debt · Settings (pushed onto the stack).
- Swap template: icon on Home → template picker → `StartDay` with the new template.

---

## 14. Errors and logging

- Every `viewModelScope` / app scope launch uses a shared `CoroutineExceptionHandler` that logs and, for UI scopes, pushes an error slice into state.
- Caveat: a handler catches the failure but the failed coroutine is still dead. The `DayOrchestrator` state collection and the tick flows must survive, so those also use `.catch { log; emit(fallback) }` so one bad read doesn't stop the app from firing alarms.
- Logger: thin wrapper over `android.util.Log` + a rolling file in `filesDir` (a few files × ~1 MB). Every transition, alarm schedule/fire and notification post is logged. Settings can share the log file.

---

## 15. Analytics

- **At flush (day end):** write the `DayRecord`, write time debt, update that week's aggregate (minutes per block type, leak, under-time, pauses, interruptions, flags).
- **On open (Weekly / Insights):** read aggregates only, never scan raw history. Insights computed on read: per-block average overrun and trend, missed-gym count, evenings skipped, interruption frequency, Chill share of evenings, off days.
- Week starts Sunday.

---

## 16. Testing and verification

- Unit tests: `DayStateMachine` (every legal/illegal transition), `ScheduleEngine` (leak, shrink, consumed, pause, interrupt both modes, early end, Pomodoro phases, reboot replay), aggregate math. Driven by a fake `Clock`.
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

---

## 18. Settings

- Version shown bottom corner as `V 1.0.0`, read from `BuildConfig.VERSION_NAME` (`buildConfig = true` in the build file).
- Planned entries: notification permission status, exact-alarm status, share log file, clear history, "All data stored locally" note.

---

## 19. Visuals

Palette, block colors, calendar range (08:00–23:00) and all screen layouts are defined in `visuals.md`.
