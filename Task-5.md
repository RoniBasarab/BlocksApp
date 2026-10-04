# TASK 5: Simpler controls, correct cold start, real Settings, demo data, finished screens

Read `SUMMARY.md` first if you have no context. This task overrides every earlier document where they differ; `schedule-spec.md`, `architecture.md` and `SUMMARY.md` have already been updated to match it.

**Target version: 1.4.0** (see Slice 11 and the versioning policy in `SUMMARY.md` §14).

## Rules

1. Work in **slices**, exactly like `Task-4-recovery.md`: change at most ~3 files, `./gradlew :app:compileDebugKotlin`, then `testDebugUnitTest`, then commit `task5 slice N: <title>`. Never start a slice on a red build.
2. **Work in the foreground.** Do not delegate screens to background tasks; the last two timed out and produced nothing.
3. Tests written in this file are the spec. You may only make compile-only changes to them. Tests for behavior this task **removes** (listed per slice) must be deleted, not kept failing.
4. All user-facing strings are exact and go in `strings.xml`.

## Answers to `issues.md`

| # | Answer |
|---|---|
| 1 Ongoing vs swipe | There is no contradiction on this app's devices. Since Android 14 (our minSdk is 34), users **can** swipe away `FLAG_ONGOING_EVENT` notifications when the phone is unlocked; ongoing only protects against "Clear all" and the lock screen. **Keep `setOngoing(true)` and keep D6.** If D6 fails on Roni's phone (some manufacturer skins block it), report it and Roni will decide. |
| 2 Device tests | Roni runs them after this task. Update them as listed in Slice 9. |
| 3 D9–D12 | Roni runs them by hand. Keep `MANUAL_TESTS.md` current. |
| 4 Screens | This task includes complete, self-contained specs and chart code for all four (Slices 5–8). Also, Roni will copy `visuals.md` and `architecture.md` into the repo root. |
| 5 Screenshots | Roni reviewed the app on the phone: it looks clean. New screenshots are required at the end (Slice 10). |
| 6 Settings stubs | Wired in Slice 3. |
| 7 `getTitle()` | Keep reading `extras[EXTRA_TITLE]` in main code too, never `getTitle()`. |

---

## Slice 1: Cold start must never show the Template picker for a started day

**Bug (Roni):** kill the app, tap the notification → Template picker, although a template was already chosen today.

Rule: the picker appears only if **today's** state is `NotStarted`. Every other state (Running, Paused, KidTime, Evening, Ended, Off) opens Home. Changing the template is only ever deliberate, via the swap button.

1. **Find the real cause first**, then write a failing regression test. Likely candidates:
   - The start destination is computed from `DayOrchestrator.state` before the persisted state is restored, so it reads the initial `NotStarted`.
   - The nav back stack's default key is the picker and is never replaced.
   - The rollover check compares dates from different clocks (`DebugClock` vs system).
2. Fix pattern:
   - `DayOrchestrator` exposes `isRestored: StateFlow<Boolean>`, set only after prefs are read and date rollover is applied.
   - The start destination is a pure function, computed **once**, after restore:

```kotlin
enum class StartDestination { TEMPLATE_PICKER, HOME }

fun resolveStartDestination(state: DayState): StartDestination =
    when (state) {
        is DayState.NotStarted -> StartDestination.TEMPLATE_PICKER
        else -> StartDestination.HOME        // exhaustive list preferred if the compiler allows; no silent defaults elsewhere
    }
```

```kotlin
// MainActivity.onCreate
val splash = installSplashScreen()
super.onCreate(savedInstanceState)
splash.setKeepOnScreenCondition { !orchestrator.isRestored.value }
setContent {
    BlocksTheme {
        val restored by orchestrator.isRestored.collectAsStateWithLifecycle()
        if (restored) {
            val start = remember { resolveStartDestination(orchestrator.state.value) }
            AppRoot(start)                   // back stack starts at `start`; nothing re-routes it later
        }
    }
}
```

3. State changes while the app is open (e.g. midnight rollover) never re-route. Only a cold start or the swap button does.

**Tests:**
- `StartDestinationTest`: every `DayState` maps correctly.
- `DayOrchestratorRestoreTest`: prefs hold `Running` for today → after restore, `isRestored == true` and `state` is `Running`. Prefs hold state from **yesterday** → `NotStarted`.
- The regression test for the root cause you found. It must fail before the fix.

---

## Slice 2: New control model (behavior)

Roni's decision: fewer choices. **Finish early** and **Interrupt/Emergency-freeze** are removed. What's left:

| Control | Behavior |
|---|---|
| **Next slot** (any time) | **Work block (CV, Coding, Gym) before its end:** the block ends now (under-time logged) and the rest becomes a `SYNTHETIC_FREE` segment until the planned end; the next block then starts on schedule, automatically. **Free block (planned or synthetic) before its end:** the next block starts **now** and keeps its planned end. **After the end (any block):** the next block starts now and keeps its planned end; the overrun is leak (debt). If `now` is already past the next block's end, land in the block containing `now`; skipped blocks become `CONSUMED`. |
| **Pause** | Work blocks only (CV, Coding, Gym). Stops counting worked time for the current block. **No end time moves**: the current block still ends at its planned end, so the pause shortens the **current** block. The next block is untouched. A pause auto-closes at the block's end (then the normal "block ended" alert fires). Pause time is logged as `PAUSE` and shown as "Pause time"; it is **not** time debt. |
| **Emergency** | Ends the day immediately (`EndDay(reason = EMERGENCY)`). Flag `EMERGENCY_STOP`. Nothing after that moment counts as planned time or debt. Normal day-end flush and silence. |

Consequences:
- **Pomodoro** phases are derived from the block's **actual start**, repeating 50/10 until its end; the last phase is cut at the end. Example: Coding starting 09:20 and ending 11:30 → focus 09:20–10:10, break 10:10–10:20, focus 10:20–11:10, break 11:10–11:20, focus 11:20–11:30 ("Focus 3/3").
- `SYNTHETIC_FREE` auto-ends at its planned end (no Next needed, never leaks). Tapping Next slot during it starts the next block now.
- `INTERRUPTION` segments now mean **device-off downtime only** (reboot recovery). Keep the kind.
- Remove the `Interrupted` state, `Interrupt` / `Resume(mode)` events, the "I'm back" dialog and the "Schedule frozen" notification. `Resume` (from pause) stays.
- **Legacy data:** if persisted state decodes as the old `Interrupted` (or fails to decode because of it), restore it as `Running` on the same block. Never fall back to `NotStarted`, because that would re-open the picker (Slice 1).
- `DayEvent.Next(expectedBlockId)` keeps stale/double-tap protection.
- Gym missed = Gym `CONSUMED`, cut by `EMERGENCY_STOP`, or worked (non-paused) time under 30 min.
- **Future-proofing.** Roni plans live slot editing and re-classifying slots later (`SUMMARY.md` §10). Do exactly this and nothing more:
  - Add `val id: String` to `BlockSpec` and give every block in `assets/templates.json` the stable id from the table below. Anything that identifies a block (`expectedBlockId`, notification keys, `CalendarBlockUi.id`) uses it instead of an id built from the block's position (like `"b3"`). Evening blocks created at runtime use `evening.dishes` and `evening.choice`. Actions on notifications posted before the update then count as stale and are ignored, which is fine.
  - Templates come from the bundled asset, so no stored data needs migrating. If you find `BlockSpec` persisted anywhere else, log it in `NOTES.md`.
  - At `StartDay`, store a snapshot of the day's planned blocks in the day record (`DayRecord.plannedBlocks: List<BlockSpec>? = null`). History and the day detail render from the snapshot, falling back to the current template only for old records without one.
  - New code (mappers, insights, demo data) never assumes positions ("CV is first", "Gym is index 3") or template ids.
  - Leave `DayState`'s internal block index as it is; it gets replaced when editing is built (2.0.0).

| Template | Block ids, in time order |
|---|---|
| `MON_THU` | `mon_thu.cv`, `mon_thu.coding_am`, `mon_thu.free_pre_gym`, `mon_thu.gym`, `mon_thu.free_post_gym`, `mon_thu.coding_pm`, `mon_thu.free_pm`, `mon_thu.kid_time` |
| `SUN_TUE_WED` | `sun_tue_wed.cv`, `sun_tue_wed.coding_am`, `sun_tue_wed.free_midday`, `sun_tue_wed.coding_pm`, `sun_tue_wed.free_pm`, `sun_tue_wed.kid_time` |
| `FRI_SAT` | `fri_sat.kid_time_am`, `fri_sat.free`, `fri_sat.kid_time_pm` |

The first column shows the template ids the tests already use. If the project's ids differ, use the project's and keep the block names after the dot.

**Delete these tests (the behavior is gone):** interrupt resume modes, pause extends the current block, and the Interrupted rows of the notification mapper test. **Rewrite** the old early-end → synthetic Free tests to trigger via `Next` (the `EndBlockEarly` event is removed; the synthetic Free behavior stays for work blocks).

**Add these tests (engine / orchestrator):**
- `nextSlot_earlyOnWorkBlock_restBecomesFree`: CV at 09:20 → `SYNTHETIC_FREE` 09:20–09:30 (CV under-time 10 min), then Coding starts automatically at 09:30.
- `nextSlot_duringFree_startsNextNow_keepsItsEnd`: Free 11:30–14:00, Next at 12:30 → Coding 12:30–15:30.
- `nextSlot_duringSyntheticFree_startsNextNow`: continuing the CV case, Next at 09:25 → Coding starts 09:25, ends 11:30.
- `nextSlot_late_countsLeak`
- `nextSlot_pastNextBlockEnd_landsWhereTheClockIs`: skipped blocks are `CONSUMED`.
- `pause_movesNoEnds`
- `pause_autoClosesAtBlockEnd`
- `pause_isNotDebt_butIsLoggedAsPauseTime`
- `emergency_endsDay_flagged_noDebtAfter_alarmCancelled_notificationRemoved`
- `pomodoro_phasesFollowActualStart` (the example above)
- `legacyInterrupted_restoresAsRunning`
- `doubleTap_nextSlot_advancesOnce`
- `templateBlockIds_areUniqueAndStable`: the block ids in `templates.json` match the table above, in time order, and none repeats.

**`HomeViewModelTest`** (drive `onAction`, assert on the real orchestrator's state; fake clock + in-memory storage). This is what would have caught "Finish early does nothing":
- Pause confirmed → state `Paused`.
- Next during Coding before 11:30 → running a synthetic Free until 11:30. Next during that Free → `Running` on the next block.
- Emergency confirmed → `Ended` with `EMERGENCY_STOP`.
- Emergency cancelled → unchanged.

---

## Slice 3: Settings, wired for real

`SettingsViewModel` reads real state; no hardcoded `SettingsUiState()`.

| Row | Behavior |
|---|---|
| Notification permission | Status "Allowed"/"Blocked" from `NotificationManagerCompat.areNotificationsEnabled()`. Tap → `Settings.ACTION_APP_NOTIFICATION_SETTINGS` (with `EXTRA_APP_PACKAGE`). |
| Exact alarms | Status from `AlarmManager.canScheduleExactAlarms()`. Tap → app details settings. |
| Test notification | "Send" posts a sample on the `day` channel with a **separate ID (99)** and `setTimeoutAfter(10_000)`: title "Test notification", text "If you heard a sound, alerts work." This is the only allowed second notification. |
| 24-hour / 12-hour | Persisted in `settings` prefs. One `TimeFormatter` manager formats every time in the app: calendar hour labels, NowCard, notifications, History, Time Debt. Switching updates Home immediately. |
| Share log file | `FileProvider` (authority `${applicationId}.logs`, `res/xml/file_paths.xml` for the log dir) → `ACTION_SEND` chooser with `EXTRA_STREAM` + `FLAG_GRANT_READ_URI_PERMISSION`. |
| Reset today | Dialog title "Reset today?", body "Today's blocks and progress are cleared and you'll pick a template again.", confirm "Reset" (red), dismiss "Cancel". Effect: state → `NotStarted`, today's segments removed, alarm cancelled, notification re-rendered, navigate to the picker (deliberate). |
| Clear history | Dialog title "Clear all history?", body "Every past day, time debt and insight is deleted. Today is kept.", confirm "Clear" (red), dismiss "Cancel". |

- Status rows refresh on `ON_RESUME` (the user may have changed permissions in system settings).
- **Debug builds only** (`BuildConfig.DEBUG`): an extra section **Developer** with rows "Load demo data (4 weeks)" and "Clear demo data" (Slice 4).
- Red confirm buttons: `ButtonDefaults.buttonColors(containerColor = c.leak, contentColor = Color(0xFF1C1B19))`.
- **Tests:** `SettingsViewModelTest` for each action's effect (fakes for permission/alarm status). The existing `UiExpectationsTest.settings_layout` must still pass.

---

## Slice 4: Demo data (debug only)

Goal: see non-empty History, Weekly, Insights and Time Debt.

```kotlin
class DemoDataGenerator @Inject constructor(
    private val templates: TemplateManager,
    private val history: HistoryManager,
    private val insights: InsightsManager,
    private val clock: Clock,
) {
    suspend fun seed(weeks: Int = 4, seed: Int = 42)   // no-op unless BuildConfig.DEBUG
    suspend fun clear()                                  // removes only records with demo = true
}
```

- Add `val demo: Boolean = false` to `DayRecord` (default keeps old data valid).
- Generate the `weeks × 7` days **before today** with a seeded `Random`. Write each day through the same `HistoryManager` APIs the real flush uses, then rebuild weekly aggregates with **the same function the day-end flush calls**, so Insights read real aggregates.
- Build each day by replaying its events (`StartDay`, `Next`, `Pause` / `Resume`, `KidAsleep`, `EveningChoice`, `EndDay`) at the chosen times through the pure `DayStateMachine`, `ScheduleEngine` and `SegmentBook`, with a fake clock. Never hand-write segments, and don't go through `DayOrchestrator` (it schedules alarms and posts notifications). That way demo days follow the same rules as real ones: an early Next on CV really produces synthetic Free.
- Rules per day:
  - Template by weekday (Sun/Tue/Wed Focus, Mon/Thu Gym, Fri/Sat Family). Exactly one weekday is an **Off day**. Exactly one day ends by **Emergency** around 13:00.
  - Day start: 09:00–09:25 (late starts sometimes consume part of CV).
  - Next slot tapped at the planned end ± variance:
    - CV: 30% early by 5–15 min; otherwise 0–15 min late.
    - Coding: 50% 0–5 min late, 35% 5–20, 15% 25–45.
    - Free: 60% 0–10 min late, 40% 10–30.
    - Gym: 80% 0–5 min late. One Gym day in eight is missed (ends after 10 min).
  - Pauses: 0–2 per day, 3–12 min, mostly in Coding.
  - Evening: 70% "Kid asleep" between 20:05 and 21:30, then choice Game 40% / Chill 30% / Project 20% / Read 10%. 30% evening skipped.
- Triggers:
  - Settings → Developer (Slice 3).
  - adb: `adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --ez seed_demo true` (and `--ez clear_demo true`).
- **Tests** (`DemoDataGeneratorTest`):
  - `seed()` writes 28 records, none dated today, all `demo = true`.
  - Exactly one Off day and one `EMERGENCY_STOP`.
  - Weekly aggregates exist for every seeded week.
  - Same seed → identical output.
  - `clear()` keeps a non-demo record.

---

## Slice 5: Weekly (finish)

```
        [‹  20 – 26 Sep  ›]            week_selector: centered pill, surface, 40dp tall; › disabled on current week
┌ weekly_hero ──────────────────┐
│ Planned work done              │ labelLarge textSecondary
│ 87%                            │ displaySmall + numbers
│ 16h 05m of 18h 30m             │ bodyMedium textSecondary
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬───           │ 6dp bar: primary on outline, rounded
└────────────────────────────────┘
┌ weekly_chart ─────────────────┐
│ Planned vs actual              │ titleMedium
│   ▯▮     ▯▮     ▯▮     ▯▮      │ PairedBarChart, 200dp
│   CV   Coding   Gym    Free    │
│ ▯ Planned   ▮ Actual           │ legend row, labelSmall
└────────────────────────────────┘
[stat_tile][stat_tile]              Leak total · Gym x/2
[stat_tile][stat_tile]              Off days · Evenings skipped
```

"Planned work done" = actual worked (non-paused) minutes of CV + Coding + Gym ÷ their planned minutes, capped at 100%. Planned minutes after an `EMERGENCY_STOP` don't count.

```kotlin
@Immutable data class PairedBar(val label: String, val plannedMin: Int, val actualMin: Int, val accent: Color)

@Composable
fun PairedBarChart(bars: ImmutableList<PairedBar>, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.labelSmall.merge(AppType.numbers).copy(color = c.textPrimary)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = c.textSecondary)
    Spacer(
        modifier.fillMaxWidth().height(200.dp).drawWithCache {
            val barW = 18.dp.toPx()
            val gap = 4.dp.toPx()
            val r = CornerRadius(6.dp.toPx())
            val valueH = 18.dp.toPx()
            val labelH = 22.dp.toPx()
            val chartH = size.height - valueH - labelH
            val maxMin = bars.maxOf { maxOf(it.plannedMin, it.actualMin) }.coerceAtLeast(1)
            val groupW = size.width / bars.size
            val labels = bars.map { measurer.measure(it.label, labelStyle) }
            val values = bars.map { measurer.measure(formatHm(it.actualMin), valueStyle) }   // "5h 40m"
            onDrawBehind {
                val baseY = valueH + chartH
                bars.forEachIndexed { i, b ->
                    val cx = groupW * i + groupW / 2
                    val pH = chartH * b.plannedMin / maxMin
                    val aH = chartH * b.actualMin / maxMin
                    drawRoundRect(b.accent.copy(alpha = 0.35f), Offset(cx - gap / 2 - barW, baseY - pH), Size(barW, pH), r)
                    drawRoundRect(b.accent, Offset(cx + gap / 2, baseY - aH), Size(barW, aH), r)
                    val v = values[i]
                    drawText(v, topLeft = Offset(cx + gap / 2 + barW / 2 - v.size.width / 2, baseY - aH - v.size.height - 2.dp.toPx()))
                    val l = labels[i]
                    drawText(l, topLeft = Offset(cx - l.size.width / 2, baseY + 4.dp.toPx()))
                }
            }
        },
    )
}
```

Only block types with planned > 0 that week, in order CV, Coding, Gym, Free.

---

## Slice 6: Insights (finish)

```
┌ insights_headline (AppShapes.hero, linear gradient accent@14% → surface) ┐
│ (icon)  Coding ran over on 4 of 5 days, +12 min on average.              │  titleMedium
└──────────────────────────────────────────────────────────────────────────┘
┌ insights_scorecard ─────────────────────────┐
│ vs last week                                  │  labelLarge textSecondary
│ (◷) Total leak           2h 10m     [↓ 25m]   │  scorecard_row ×8, ≥ 56dp, dividers inset 56dp
│ ...                                           │
└───────────────────────────────────────────────┘
┌ Worst overruns ─────────────────────────────┐
│ ● Coding   +12 min avg   ▬▬▬▬▬▬▬▬▬▬           │  bar width ∝ worst; accent color
│ ● Free     +8 min avg    ▬▬▬▬▬▬               │
│ ● CV       +3 min avg    ▬▬                   │
└───────────────────────────────────────────────┘
```

| Metric (row order) | Value format | Good when |
|---|---|---|
| Total leak | 2h 10m | ↓ |
| Coding hours | 11h / 13h | ↑ |
| Gym sessions | 1 / 2 | ↑ |
| CV days | 4 / 5 | ↑ |
| Evenings skipped | 2 | ↓ |
| Chill share of evenings | 40% | ↓ |
| Pause time | 45m | ↓ |
| Off days | 1 | neutral (gray pill) |

`TrendPill`: `Icons.Outlined.ArrowDownward` / `ArrowUpward` / `Remove` (14dp) + delta (e.g. "25m", "1", "10%"). Colors: `good` / `leak` / `neutralTrend` by the table's direction.

Headline rule:
1. If some block type overran on ≥ 3 days this week: "{Type} ran over on {n} of {m} days, +{avg} min on average."
2. Otherwise, the metric with the largest relative change vs last week: "{Metric} {improved/got worse} vs last week: {old} → {new}."
3. Otherwise: "A steady week. Nothing stood out."

Empty (no previous full week): `EmptyState(insights, "Insights need a full week", "{n} of 7 days logged.")` + progress bar as `extra`.

---

## Slice 7: History (finish)

- `LazyColumn`, `contentPadding = PaddingValues(Spacing.l)`, `verticalArrangement = spacedBy(Spacing.m)`, stable keys = dates.
- Sticky headers "This week" / "Last week" / "13 – 19 Sep": `labelLarge textSecondary`, 8dp vertical padding. Background `backgroundBottom` at 92% alpha so rows scroll under it cleanly.
- `history_day_row` (`AppCard`, clickable, min 72dp):

```
┌──────────────────────────────────────────────────────┐
│  TUE   Sunday / Tuesday / Wednesday          [+18m]  │  weekday labelSmall textSecondary / chip
│  22    ▬▬▬|▬▬▬▬▬▬|▬▬▬|▬▬▬▬|▬▬                         │  day number titleLarge + numbers │ BlockStrip 8dp (actual segments)
│        6/7 blocks · 1 pause                          │  labelSmall textSecondary
└──────────────────────────────────────────────────────┘
```

- Chip: "+18m" (`leak`), "On time" (`good`), "Emergency" (`secondary`), "Off day" (`textSecondary`; outlined card, no strip).
- Tap → day detail: `BackTopBar("Tuesday 22 Sep")`, a summary row ("6/7 blocks · leak 18m · pause 12m"), then the read-only `DayCalendar` showing **actual** segments (no now line, no NowCard, no auto-scroll).

---

## Slice 8: Time Debt (finish)

```
┌ debt_hero ─────────────────────┐
│ This week                        │ labelLarge textSecondary
│ 1h 12m                           │ displaySmall, leak ("0 min" in good)
│ Today 18 min · avg 14 min/day    │ bodyMedium textSecondary
└──────────────────────────────────┘
┌ debt_chart ────────────────────┐
│ Last 14 days                     │ titleMedium
│ ▮ ▮   ▮ ▮ ▮   ▮ ▮ ▮ ▮   ▮ ▮ ▮    │ 160dp Canvas: leak bars, today 100% alpha, others 60%,
│ - - - - - - - - - - - - avg      │ dashed textMuted average line (PathEffect.dashPathEffect)
│ S M T W T F S S M T W T F S      │ weekday initials, labelSmall textSecondary
└──────────────────────────────────┘
┌ list card ──────────────────────┐
│ Tue 22 Sep                 25m   │ debt_day_row: titleSmall │ titleSmall + numbers, leak
│ ● Coding 12m  ● Free 8m  ● CV 5m │ labelSmall, dots in block accents
└──────────────────────────────────┘
```

Empty: `EmptyState(hourglass_empty, "No time debt yet", "When a block runs over, it shows up here.")`. Debt = leak only (pauses excluded).

---

## Slice 9: NowCard + notification for the new controls

### NowCard layout

```
┌───────────────────────────────────────────────┐
│ ● Coding                    [Focus 2/2]    ⓘ  │
│ 42 min left                       until 11:30  │
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬────────────────               │
│ [   ⏸  Pause   ]    [   Next: Free  →   ]      │  one Row, equal weights, 48dp; Pause = FilledTonal (surfaceRaised), Next = filled primary
│                  ⚠ Emergency                   │  centered TextButton, content color leak, 40dp
└───────────────────────────────────────────────┘
```

Tags: `now_card`, `now_pause`, `now_primary` (Next / Resume / Kid asleep / Choose evening), `now_emergency`.

| State | Button row | Emergency |
|---|---|---|
| Running work block (CV, Coding, Gym) / Pomodoro break, before its end | Pause · **Next: Free** | yes |
| Running Free block (planned or synthetic) | **Next: {next block}** (full width) | yes |
| Block ended (leaking) | **Next: {landing block}** (full width) | yes |
| Paused | **Resume** (full width) | yes |
| Kid time | **Kid asleep** · End day | no |
| Dishes | **Choose evening** · End day | no |
| Evening | End day (full width, tonal) | no |
| Ended / Off | none | no |

Leaking: chip "Ran over", subtitle "CV ended at 09:30". Paused: title "Paused · Coding", countdown line "4 min" (per-minute).

### Dialogs (exact text)

| Trigger | Title | Body | Confirm | Dismiss | Shown |
|---|---|---|---|---|---|
| Pause | Pause Coding? | For short breaks like the bathroom or a call: the timer stops counting your Coding time, and Coding still ends at 11:30. | Pause | Cancel | Until "Don't ask again" |
| Emergency | Emergency? | The day has been disrupted by an emergency. Stop all schedules for today? | Stop today (red) | Cancel | Always |

Next has **no** dialog. Block names and times are filled in from the current block.

**ⓘ "What the buttons do"** (`AlertDialog`), three rows:
- **Pause:** "Stops counting your time for a short break; the block still ends on time."
- **Next:** "Moves on now: finish a work block early and the rest becomes free time, end free time early and the next block starts right away, and any time you ran over counts as debt."
- **Emergency:** "Stops all schedules for today. Nothing after it counts as debt."

### Replace `NowCardExpectationsTest` with this

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class NowCardExpectationsTest {
    @get:Rule val compose = createComposeRule()
    private val actions = mutableListOf<HomeAction>()
    private val tol = 1.dp

    private fun show(ui: NowCardUi) {
        compose.setContent { BlocksTheme { NowCard(ui, { "42 min left" }, { 0.3f }, { actions += it }) } }
        compose.waitForIdle()
    }

    private fun near(a: Dp, b: Dp) = abs((a - b).value) <= tol.value
    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isDialog()))

    @Test fun running_pauseAndNext_sideBySide_plusEmergency() {
        show(FakeStates.nowRunningCoding())                     // Coding 09:30–11:30, next = Free
        val pause = compose.onNodeWithTag("now_pause").assertTextEquals("Pause").getBoundsInRoot()
        val next = compose.onNodeWithTag("now_primary").assertTextEquals("Next: Free").getBoundsInRoot()
        assertTrue("same row", near(pause.top, next.top))
        assertTrue("equal widths", near(pause.width, next.width))
        compose.onNodeWithTag("now_emergency").assertTextEquals("Emergency").assertHasClickAction()
        compose.onAllNodesWithText("Finish early").assertCountEquals(0)
        compose.onAllNodesWithText("Interrupt", substring = true).assertCountEquals(0)
    }

    @Test fun leaking_fullWidthNext_namesTheLandingBlock() {
        show(FakeStates.nowLeakingCv())                         // CV ended 09:30, now 09:42 → lands on Coding
        val card = compose.onNodeWithTag("now_card").getBoundsInRoot()
        val next = compose.onNodeWithTag("now_primary").assertTextEquals("Next: Coding").getBoundsInRoot()
        assertTrue("spans the card", next.width >= card.width - 32.dp - tol)
        compose.onNodeWithTag("now_pause").assertDoesNotExist()
        compose.onNodeWithText("Ran over").assertIsDisplayed()
        compose.onNodeWithText("CV ended at 09:30").assertIsDisplayed()
    }

    @Test fun runningWorkBlockEarly_nextOffersFreeTime() {
        show(FakeStates.nowRunningCv())                         // CV 09:00–09:30 at 09:10; next planned block is Coding
        compose.onNodeWithTag("now_primary").assertTextEquals("Next: Free")
        compose.onNodeWithTag("now_pause").assertExists()
    }

    @Test fun runningFree_nextStartsTheNextBlock_noPause() {
        show(FakeStates.nowRunningFree())                       // Free 11:30–14:00 at 12:30; next = Coding
        compose.onNodeWithTag("now_primary").assertTextEquals("Next: Coding")
        compose.onNodeWithTag("now_pause").assertDoesNotExist()
    }

    @Test fun next_actsImmediately_withoutDialog() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithTag("now_primary").performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        assertEquals(1, actions.size)
    }

    @Test fun pause_explainsFirst_thenActs() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithTag("now_pause").performClick()
        compose.onNodeWithText("Coding still ends at 11:30", substring = true).assertIsDisplayed()
        assertTrue(actions.isEmpty())
        inDialog("Pause").performClick()
        assertEquals(1, actions.size)
    }

    @Test fun emergency_asksToStopTheDay() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithTag("now_emergency").performClick()
        compose.onNodeWithText("Stop all schedules for today?", substring = true).assertIsDisplayed()
        inDialog("Cancel").performClick()
        assertTrue(actions.isEmpty())
        compose.onNodeWithTag("now_emergency").performClick()
        inDialog("Stop today").performClick()
        assertEquals(1, actions.size)
    }

    @Test fun paused_showsResumeOnly() {
        show(FakeStates.nowPausedCoding())
        compose.onNodeWithTag("now_primary").assertTextEquals("Resume")
        compose.onNodeWithTag("now_pause").assertDoesNotExist()
    }

    @Test fun info_explainsThreeActions() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithContentDescription("What the buttons do").performClick()
        listOf("the block still ends on time", "ran over counts as debt", "Stops all schedules for today").forEach {
            compose.onNodeWithText(it, substring = true).assertExists()
        }
    }
}
```

### Notification changes (update `NotificationModelMapper` + tests)

| State | Actions (exact) |
|---|---|
| Running work block, before its end (focus or break) | Pause · Next: Free |
| Running Free block (planned or synthetic) | Next: {next block} |
| Block ended (leaking) | Next: {landing block} |
| Paused | Resume |
| Interrupted | *row removed* |
| Everything else | unchanged from Task-4 §1.2 |

- `NotificationRendererTest.running_usesSystemCountdown_withPauseOnly` becomes `running_usesSystemCountdown_withPauseAndNext`, asserting `listOf("Pause", "Next: Free")`.
- Replace "Start X" with "Next: X" everywhere, including the Task-4 mapper rows.

---

## Slice 10: Device tests, manual checklist, screenshots, report

- In `NotificationDeviceTest`, replace "Start Coding" with "Next: Coding".
- Add two device tests:
  - **D13:** pick a template → Home → press Home → `am kill` the package → tap the notification → **Home**, not the picker.
  - **D14:** same, but relaunch from the launcher icon → **Home**.
- Keep D6. Gate: `./gradlew :app:compileDebugAndroidTestKotlin`.
- `MANUAL_TESTS.md`: add "Emergency ends the day and the notification disappears" and "Load demo data, then open History, Weekly, Insights and Time Debt".
- Rewrite `issues.md` so it lists only what is still open after this task, each item with its evidence; delete the file if nothing is.
- Screenshots (`./gradlew recordRoborazziDebug`): everything from Task-4, plus `now_card_paused.png`, `dialog_emergency.png`, `settings_debug.png`. The data screens (`weekly.png`, `insights.png`, `history.png`, `history_day.png`, `time_debt.png`) must be rendered from **`DemoDataGenerator` output** (seed 42) mapped through the real screen mappers, not hand-made fakes.
- Final gate: `./gradlew assembleDebug assembleRelease :app:assembleBenchmark :macrobenchmark:assembleBenchmark testDebugUnitTest :app:compileDebugAndroidTestKotlin`.

---

## Slice 11: Version 1.4.0

1. In `app/build.gradle.kts`: `versionName = "1.4.0"`, `versionCode = 10400` (rule: `major * 10000 + minor * 100 + patch`; it must always increase).
2. `UiExpectationsTest.settings_layout`: replace the literal `"V 1.0.0"` with `"V ${BuildConfig.VERSION_NAME}"`. This is the one allowed non-compile edit to a spec test; the version is data, not layout.
3. Create `CHANGELOG.md` in the repo root, newest first. Earlier versions are retroactive (they were never bumped at the time):

```markdown
# Changelog

## 1.4.0
- Controls simplified: Next slot (finishing a work block early gives free time), Pause (shortens the current block), red Emergency (ends the day). Finish early and freeze/resume removed.
- Cold start never reopens the template picker for a started day.
- Settings fully wired (notification status, test notification, 12/24-hour, reset today, clear history, share log).
- Weekly, Insights, History and Time Debt finished; debug-only demo data.
- Day records keep a snapshot of the planned blocks.

## 1.3.0 (retroactive, Task-4)
- Single notification with system countdown; launcher-style tap; stale-action protection.
- Labeled NowCard with explanations; screen primitives; UI + behavior tests; `.debug` app ID suffix; debug time travel.

## 1.2.0 (retroactive, Task-3)
- Home calendar (grid, blocks, now line, leak overflow), template picker aurora, theme fixes.
- Performance harness: stability reports, recomposition tests, Macrobenchmark module, Roborazzi.

## 1.1.0 (retroactive, Task-2)
- Design system, edge-to-edge, splash, app icon, segment recording, receiver fixes.

## 1.0.0 (Task-1)
- Initial build: state machine, schedule engine, alarms, notifications, storage, first screens.
```

4. After merging to `master`: `git tag v1.4.0`.

## Final report

1. Each slice: done / not done, with the tests that prove it.
2. The root cause of the cold-start bug, in one or two sentences.
3. Deleted tests (names), and why.
4. Any test edited, with the compile-only change.
5. Screenshot list.
6. Updated `NOTES.md` (no contradictions with the code).
7. `versionName` / `versionCode`, the `CHANGELOG.md` diff, and confirmation of the `v1.4.0` tag.
