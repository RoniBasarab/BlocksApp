# TASK 6: Fixes from the phone, clearer wording, debug clock controls

Read `SUMMARY.md` first if you have no context. This task overrides every earlier document where they differ; `schedule-spec.md`, `architecture.md`, `visuals.md` and `SUMMARY.md` already describe the result.

**Target version: 1.5.0** (Slice 10; versioning policy in `SUMMARY.md` §14).

Everything here comes from Roni using 1.4.0 on his phone. Slices are ordered by how much they hurt him: Slice 1 is a blocker.

## Rules

1. Work on a branch `task6` from `master`, in **slices**: change at most ~3 files, `./gradlew :app:compileDebugKotlin`, then `./gradlew :app:testDebugUnitTest`, then commit `task6 slice N: <title>`. Never start a slice on a red build.
2. Work in the foreground. Don't delegate anything to background tasks or sub-agents.
3. **Lighter testing** (new policy, `SUMMARY.md` §15):
   - Add only the tests this file names. Each one guards a real bug or a logic rule and must fail before your fix.
   - No new screenshot tests, UI geometry tests or device tests. Don't re-record Roborazzi screenshots.
   - Existing tests must keep passing. Change one only to make it compile or where this file changes its expected text, and list every test you touched in the report.
4. **Roni's phone:** if `adb devices` lists a device, use it to reproduce bugs and check fixes, with the **debug app only** (`com.roni.blocks.debug`). `adb` lives in `<sdk.dir from local.properties>\platform-tools` if it isn't on the PATH.
   - Allowed: `.\gradlew installDebug`, `adb shell am kill com.roni.blocks.debug`, `adb logcat`, `adb shell run-as com.roni.blocks.debug ...`, screenshots (`adb shell screencap -p /sdcard/blocks_shot.png`, then `adb pull /sdcard/blocks_shot.png screenshots\device\<name>.png`, then `adb shell rm /sdcard/blocks_shot.png`; add `screenshots/device/` to `.gitignore`).
   - Never install over, clear, force-stop or uninstall `com.roni.blocks`: it's Roni's real app with his real days.
5. All user-facing strings are exact and go in `strings.xml`.
6. Don't refactor beyond what a slice asks. Engine and orchestrator changes stay within the rule the slice describes.

---

## Slice 0: Put the docs under version control

The spec and task files in the repo root were never committed. Commit every spec and task file present there as it is, in a commit of its own: `SUMMARY.md`, `schedule-spec.md`, `architecture.md`, `visuals.md` and the `Task*.md` files. Message: `docs: specs and tasks up to Task-6`. If `.gitignore` excludes any of them, remove that rule.

---

## Slice 1: Stuck on the splash after Android kills the app (blocker)

**Bug (Roni, 1.4.0):** pick a template, kill the app, open it again: the splash never goes away. In the same state, the notification's Pause and Next buttons do nothing.

Both symptoms point at the restore path: the UI waits for `isRestored`, and notification actions go through the same orchestrator. `DayOrchestratorRestoreTest` passed because it calls the restore directly; a real cold start (launcher, notification button, alarm) goes through different entry points. D13/D14, the device tests for exactly this, were never run.

### 1.1 Reproduce, then find the cause

- **JVM:** build a fresh `DayOrchestrator` over storage that already holds a started day (that's what a process death looks like). Don't call restore yourself; go only through the real entry points (what `Application`, `MainActivity` and the receivers call).
- **Phone (if connected):** `.\gradlew installDebug`, pick a template, press Home, `adb shell am kill com.roni.blocks.debug`, open the app. Read `adb logcat` and the app's own log (`adb shell run-as com.roni.blocks.debug ls -R files` to find it, then `cat` it).
- Write the root cause in one or two sentences in the report.

### 1.2 Fix the cause, and add these guards whatever it was

Adapt the names to the project; keep the behavior.

```kotlin
// DayOrchestrator
private val restoreMutex = Mutex()
private val _isRestored = MutableStateFlow(false)
val isRestored: StateFlow<Boolean> = _isRestored.asStateFlow()

/** Idempotent and single-flight. Every entry point calls this before touching the day. */
suspend fun ensureRestored() {
    if (_isRestored.value) return
    restoreMutex.withLock {
        if (_isRestored.value) return
        try {
            restoreFromStorage()   // read state + segments, date rollover, bounded replay of missed transitions
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Restore failed; continuing with the safest state", e)
            // keep whatever could be read; if nothing could, the state stays NotStarted
        } finally {
            _isRestored.value = true
        }
    }
}
```

- `restoreFromStorage()` never calls `send()`, `ensureRestored()` or anything else that takes `restoreMutex` or waits for `isRestored`: that's a deadlock. Replayed transitions go through the internal function that `send()` uses.
- The replay of missed transitions is bounded: stop after 500 steps and log an error.
- Call sites, each before any other work: `Application.onCreate` (launch on the app scope), the first line of `send()`, and `AlarmReceiver`, `NotificationActionReceiver` and `SystemEventsReceiver` (inside their `goAsync` coroutine).
- The splash never stays longer than 3 seconds:

```kotlin
// MainActivity.onCreate
val shownAt = SystemClock.uptimeMillis()
splash.setKeepOnScreenCondition {
    !orchestrator.isRestored.value && SystemClock.uptimeMillis() - shownAt < 3_000
}
setContent {
    BlocksTheme {
        var ready by remember { mutableStateOf(orchestrator.isRestored.value) }
        LaunchedEffect(Unit) {
            if (withTimeoutOrNull(3_000) { orchestrator.isRestored.first { it } } == null) {
                logger.e("Restore took over 3 s; opening with the current state")
            }
            ready = true
        }
        if (ready) {
            val start = remember { resolveStartDestination(orchestrator.state.value) }
            AppRoot(start)
        }
    }
}
```

- The one allowed re-route: if the app opened on the template picker because restore was late, and the restored state turns out to be a started day, replace the picker with Home. Nothing else ever re-routes.

### Tests (`DayOrchestratorProcessDeathTest`; fake clock, in-memory storage, a fresh orchestrator over saved data in each test)

- `coldStart_restoresWithinTwoSeconds`: one case per saved state: Running mid-block, Running after its end (ran over), Paused, synthetic Free, KidTime, Evening, Ended, Off. Assert `withTimeout(2_000) { isRestored.first { it } }` succeeds and the restored state is the saved one (after the date-rollover rule).
- `notificationAction_onFreshProcess_isApplied`: saved Running on `sun_tue_wed.cv`, clock 09:40. Call `send(Next(expectedBlockId = "sun_tue_wed.cv"))` without calling restore first → Running on `sun_tue_wed.coding_am`.
- `restoreFailure_stillMarksRestored`: storage that throws on read → `isRestored` becomes true, state `NotStarted`, error logged.

---

## Slice 2: The ended day and swapping templates

**Bugs (Roni):** after Emergency, Home's top bar still says "Pick a template". After the day ends, swap opens the picker, but picking a template does nothing.

### 2.1 Home after the day ends

- Home's title is today's template title in every started state (Running, Paused, KidTime, Evening, Ended). On an Off day it's "Off day". The text "Pick a template" never appears on Home.
- The status line under the title: ended normally → "Day done"; ended by Emergency → "Stopped early (emergency)"; Off → "No schedule today".
- NowCard: ended normally → title "Day done", line "Ran over {x} · {done}/{total} blocks"; ended by Emergency → title "Stopped early", line "Emergency at {time}". No buttons.
- Home's calendar after the day ends: see Slice 4.3.

### 2.2 Swap = re-plan the rest of today from now

One new event, `DayEvent.SwapTemplate(templateId)`, accepted in every started state (Running, Paused, KidTime, Evening, Ended, Off). It replaces whatever swap did before; delete or rewrite the old swap tests that conflict and list them.

- Segments already recorded stay exactly as they are. An open segment (the current block, a pause) closes at now.
- From now on the day follows the new template:
  - its blocks that end at or before now are left out. They are **not** `CONSUMED`: they were never part of the day.
  - The block that contains now starts now and keeps its planned end.
  - Store this as `planStartsAt: Instant? = null` in the persisted day data. The engine leaves out template blocks ending at or before `planStartsAt`, starts the block containing it at `planStartsAt`, and matches only segments that start at or after `planStartsAt` when it builds today's timeline. Earlier segments are history: they still count in stats and show on the actual timeline. Old saved data has no field, so it reads as null and nothing changes.
- Today's record: `template` becomes the new one; `plannedBlocks` = the previous snapshot's blocks that started before now + the new template's blocks from now on. Add flag `TEMPLATE_SWAPPED`, remove `OFF_DAY` when swapping away from Off, and keep `EMERGENCY_STOP` if it's there.
- Resulting state: Running on the block that contains now; KidTime if that block is kid time; Off if the new template is Off.
- After 22:30 a swap is refused and nothing changes.
- A swap is a transition like any other: the notification is re-rendered and the alarm rescheduled.

UI flow:

| From | Tapping swap | Then |
|---|---|---|
| Running, Paused, KidTime, Evening, Off | Opens the picker | Picking sends `SwapTemplate` and replaces the picker with Home |
| Ended (normally or by Emergency) | A dialog first. Title "Today is already finished". Body "Start tracking again from now with another template? What you did earlier today stays in your history." Confirm "Choose template", dismiss "Cancel" | Confirm → picker → as above |
| Any state after 22:30 | A dialog. Title "Too late to restart". Body "It's past 22:30, so there's nothing left to track today." Button "OK" | Nothing else |

Back from the picker returns to Home unchanged.

### Tests (JVM)

- `swapAfterEmergency_restartsFromNow_keepsEarlierSegments`: Sun/Tue/Wed, Emergency at 10:00, swap to Mon/Thu at 12:10 → Running on `mon_thu.gym` from 12:10, ending 14:00. Segments before 10:00 unchanged; no segments at all for `mon_thu.cv`, `mon_thu.coding_am` or `mon_thu.free_pre_gym`; flags contain `EMERGENCY_STOP` and `TEMPLATE_SWAPPED`.
- `swapMidDay_replansFromNow`: Running on `sun_tue_wed.coding_am` at 10:15, swap to Fri/Sat → the Coding segment closes at 10:15, state KidTime on `fri_sat.kid_time_am` from 10:15.
- `swapAfter2230_isRefused`: ended at 22:30, swap at 22:40 → state and segments unchanged.
- `homeTitle_afterEmergency_isTheTemplateTitle`: `HomeViewModel` after Emergency on Sun/Tue/Wed → title "Sunday / Tuesday / Wednesday", status "Stopped early (emergency)".

---

## Slice 3: Insets and spacing

**Bugs (Roni):** History has a large gap between the top bar and the list, and the list fills only about 75% of the screen. Weekly has the same problem.

The cause is almost certainly window insets applied twice, or space kept for a bottom bar that isn't shown. JVM tests can't see this: Robolectric has no insets.

**Rule: each inset is applied exactly once.**

| Inset | Applied by (and nothing else) |
|---|---|
| Status bar | The screen's top bar: M3 `TopAppBar` / `CenterAlignedTopAppBar` with its default `windowInsets`, or Home's custom bar with `Modifier.windowInsetsPadding(WindowInsets.statusBars)` |
| Navigation (gesture) bar | Tab screens (Home, Weekly, Insights): `NavigationBar` with its default insets. Menu screens (History, Time Debt, Settings, History day detail): the scrolling content's bottom padding, `navigationBars` inset + 16dp, so content still scrolls under the transparent bar |
| Template picker | `WindowInsets.safeDrawing` once on its content; its background draws behind the bars |

- Every `Scaffold`: `contentWindowInsets = WindowInsets(0, 0, 0, 0)`.
- Menu screens are their own navigation entries, **outside** the tab host's `Scaffold`, so no space is kept for a hidden bottom bar.
- Search `feature/` and `designsystem/` for `safeDrawing`, `systemBars`, `statusBarsPadding`, `navigationBarsPadding`, `windowInsetsPadding` and `contentWindowInsets`, and make every screen follow the table.
- `ScreenColumn`: top padding 8dp (was 16dp), bottom 16dp (plus the navigation bar inset on menu screens), sides 16dp.
- History's `LazyColumn`: `contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp + navBarBottom)`.
- Result: the first card sits 8dp below the top bar, and lists run down to the bottom bar (tabs) or the screen's bottom edge (menu screens).
- No new tests. If the phone is connected, save screenshots of Weekly, History and Settings to `screenshots\device\`.

---

## Slice 4: History headers and the day detail

**Bugs (Roni):** the week headers ("Last week", the dates) sit too far left and overlap their dark band. Inside a day, blocks overlap and get cut off.

### 4.1 Week headers

- Regular `item`s, not `stickyHeader`, so there's no background band.
- Text `labelLarge`, `textSecondary`; start padding 4dp inside the list's 16dp gutter (20dp from the screen edge); top padding 16dp (0 for the first header); bottom padding 8dp.

### 4.2 The day detail calendar

Build it from one pure mapper and draw only what it returns.

```kotlin
@Immutable data class ActualBlockUi(
    val id: String,                        // "<blockId>@<startMin>", unique within the day
    val type: BlockType,
    val title: String,
    val startMin: Int,                     // minute of day, clipped to 08:00–23:00
    val endMin: Int,
    val plannedEndMin: Int?,               // where the "ran over" tint starts; null if not a planned block
    val timeLabel: String,
    val pauses: ImmutableList<IntRange>,   // minute ranges, drawn as hatching inside this block
)

@Immutable data class ActualDayUi(
    val blocks: ImmutableList<ActualBlockUi>,
    val downtime: ImmutableList<IntRange>, // INTERRUPTION (phone off): hatching across the full width
    val emergencyAtMin: Int?,              // a line across the calendar labeled "Emergency stop"
    val summary: String,                   // "6/7 blocks · ran over 18m · paused 12m · skipped: Free 11:30–14:00"
)

object ActualDayMapper {
    fun map(record: DayRecord, formatter: TimeFormatter): ActualDayUi
}
```

Rules:
- Blocks come from `PLANNED` (their actual range), `SYNTHETIC_FREE`, kid time, Dishes and evening segments. `PAUSE` segments become `pauses` of the block they fall in; `INTERRUPTION` goes to `downtime`; `CONSUMED` blocks are not drawn, and are listed in `summary` after "skipped:".
- Sorted by start. If a block starts before the previous one ends, it starts at the previous one's end. Blocks left with no length are dropped. Log both cases.
- Drawing: the block fill as on Home. The part after `plannedEndMin` gets the ran-over tint (`leakContainer`, a 1dp `leak` line at the planned end, and "+12m" in `labelSmall` if that part is at least 18dp tall). Pauses and downtime are hatched:

```kotlin
fun DrawScope.hatch(rect: Rect, color: Color, gap: Dp = 6.dp) {
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        val step = gap.toPx()
        var x = rect.left - rect.height
        while (x < rect.right) {
            drawLine(color, Offset(x, rect.bottom), Offset(x + rect.height, rect.top), strokeWidth = 1.dp.toPx())
            x += step
        }
    }
}
```

- Text by block height: under 18dp, none; 18–35dp, one line in `labelSmall` ("Coding · 09:30–11:42"); 36dp and up, title and time as on Home.
- No now line, no NowCard, no auto-scroll.

### 4.3 Home after the day ends

After the day ends, normally or by Emergency, Home's calendar shows today's actual timeline with the same mapper and drawing. Slice 2 handles the title and the NowCard.

### Tests (`ActualDayMapperTest`)

- `messyDay_mapsToCleanBlocks`: a hand-built Sun/Tue/Wed record: CV 09:00–09:42 (ran over 12 min); Coding 09:42–14:05 with a pause 10:20–10:30; Free 11:30–14:00 consumed; Coding 14:05–15:10, then synthetic Free from 15:10; Emergency at 15:20. Assert: blocks sorted, non-overlapping and with positive length; the pause is in the first Coding block's `pauses`; `summary` contains "skipped: Free 11:30–14:00"; `emergencyAtMin` = 920.
- `demoDays_mapWithoutOverlap`: every record from `DemoDataGenerator` (seed 42) maps to sorted, non-overlapping, positive-length blocks. If this fails because the generator writes bad segments, fix the generator and say so in the report.

---

## Slice 5: Back gestures fade like the tabs

**Bug (Roni):** swiping back "destroys" the screen instead of fading the way switching tabs does.

- One motion token in `designsystem/`: a 250 ms crossfade, `fadeIn(tween(250)) togetherWith fadeOut(tween(250))`.
- `NavDisplay`'s `transitionSpec`, `popTransitionSpec` and `predictivePopTransitionSpec` all return it. Check the exact parameter names and lambda signatures in the Navigation 3 version this project uses (the predictive one may take a swipe-edge argument).
- The bottom-bar tab switch uses the same token.
- Manifest: `android:enableOnBackInvokedCallback="true"` on `<application>`, if it isn't there.
- No new tests. If the phone is connected, check that back from Settings, History and the day detail all fade.

---

## Slice 6: Wording a stranger would understand

Roni's test: someone who isn't him opens the screen. Would they understand it? User-facing text says **"ran over"**, never "leak". Code names keep `leak`.

### 6.1 Weekly

| Where | Old | New |
|---|---|---|
| Hero label | Planned work done | Plan followed |
| Hero caption | 16h 05m of 18h 30m | 16h 05m of the 18h 30m planned for CV, Coding and Gym |
| Chart title | Planned vs actual | Planned vs actual time |
| Tile | Leak total | Ran over |
| Tile | Gym | Gym sessions |

Each stat tile gets a hint line under its label (`labelSmall`, `textMuted`):

| Tile | Hint |
|---|---|
| Ran over | Time past block ends |
| Gym sessions | Done of planned |
| Off days | Days with no schedule |
| Evenings skipped | Kid asleep never tapped |

### 6.2 Insights

- Card title "vs last week" → "Compared with last week".
- Each scorecard row gets a hint line under its name (`bodySmall`, `textSecondary`). Rows stay at least 56dp tall.

| Old name | New name | Hint |
|---|---|---|
| Total leak | Ran over | Time past block ends |
| Coding hours | Coding time | Done of planned |
| Gym sessions | Gym sessions | Done of planned |
| CV days | Job-search days | Days the CV block happened |
| Evenings skipped | Evenings skipped | Kid asleep never tapped |
| Chill share of evenings | Chill evenings | Share of evenings spent on Chill |
| Pause time | Pause time | Breaks taken during blocks |
| Off days | Off days | Days with no schedule |

- The headline card becomes a title (`titleMedium`) plus a body (`bodyMedium`, `textSecondary`). It counts **blocks, not days**: Coding has two blocks a day, which is how "8 of 10 days" happened.
  1. A block type ran over in at least 3 of its blocks this week → title "{Type} tends to run long"; body "{n} of {m} {Type} blocks this week went past their planned end, by {avg} min on average."
  2. Otherwise, the metric with the largest relative change → title "{Metric} improved" or "{Metric} got worse"; body "Last week: {old}. This week: {new}."
  3. Otherwise → title "A steady week"; body "Nothing changed much compared with last week."
- "Worst overruns" card → title "Blocks that run long"; each row's "+12 min avg" → "12 min over on average".

### 6.3 Time Debt

- Hero label "This week" → "Time debt this week".
- Under the hero value, one line (`bodySmall`, `textSecondary`): "Time debt is the time blocks ran past their planned end. Pauses don't count."

### 6.4 Elsewhere

- Home's status line and the NowCard: wherever "leak" shows (e.g. "leak 12 min"), it becomes "ran over" ("ran over 12 min").
- History's day chip stays "+18m"; the day detail summary already says "ran over" (Slice 4).

### 6.5 "What these mean"

An ⓘ icon button (contentDescription "What these mean") in the top bars of Weekly, Insights and Time Debt opens an `AlertDialog` titled "What these mean". Each row is a label (`titleSmall`) plus a sentence (`bodyMedium`, `textSecondary`). Button "Got it".

- **Ran over (time debt):** "Time a block kept going after its planned end, until you tapped Next."
- **Plan followed:** "Time you actually spent in CV, Coding and Gym, out of the time planned for them. Paused time doesn't count."
- **Pause time:** "Short breaks you took inside a block with Pause."
- **Evenings skipped:** "Days the evening never started because Kid asleep wasn't tapped before 22:30."
- **Off days:** "Days you picked the Off template."
- **Skipped blocks:** "Blocks that never happened because an earlier block ran past their end."

### Tests

- Update the existing tests that assert the old strings, and list them in the report.
- Add `insightsHeadline_countsBlocks_notDays`: a week with two Coding blocks a day over five days, 8 of the 10 ran over → title "Coding tends to run long", body starting "8 of 10 Coding blocks this week".

---

## Slice 7: Feedback in Settings, and the alert sound

**Bug (Roni):** "Load demo data" gives no feedback at all.

- Settings gets a `SnackbarHost` (container `surfaceRaised`, text `textPrimary`, shape `AppShapes.card`). The ViewModel emits a `ShowMessage(text)` effect for each action:

| Action | While it runs | Message |
|---|---|---|
| Load demo data | A 20dp progress indicator in place of the row's trailing element | "Demo data loaded: {n} days", or "Couldn't load demo data. Details are in the log." |
| Clear demo data | Same | "Demo data cleared" |
| Test notification | — | "Test notification sent" |
| Reset today | — | "Today was reset" (then the picker, as now) |
| Clear history | — | "History cleared" |

- A new row at the end of the Notifications section: title "Alert sound", subtitle "Choose the sound for block changes", chevron. Tapping it opens Android's settings for the `day` channel:

```kotlin
val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    .putExtra(Settings.EXTRA_CHANNEL_ID, "day")
```

Android owns a channel's sound once the channel exists, so this is the supported way to change it. Nothing else in the app changes.

### Tests

- `SettingsViewModelTest`: demo load success → "Demo data loaded: 28 days"; failure → "Couldn't load demo data. Details are in the log."

---

## Slice 8: Debug clock controls (debug builds only)

**Problem (Roni):** there's no way to try the evening without waiting until evening.

- `DebugTimeController` (`src/main`, used only when `BuildConfig.DEBUG`) takes over what `TimeTravelReceiver` does today: set the clock offset, then `orchestrator.onTimeChanged()`. The receiver delegates to it.
- Settings → Developer (debug builds only) gets these, above the demo data rows:
  - A "Clock" row showing "20:05 · shifted +11h 02m", or "Real time".
  - Tonal chips in a wrapping row (`FlowRow`; opt in if the API requires it): "09:31 · CV ran over", "12:00 · Gym", "16:20 · Kid time", "20:05 · Evening prompt", "+10 min", "+30 min", "Real time".
  - "Kid asleep now" (sends `KidAsleep`; disabled unless in kid time) and "Skip dishes" (moves the clock +20 min while in Dishes). After these, the evening choice sheet appears on Home.
  - Each action shows a snackbar: "Clock set to 20:05", "Kid asleep sent", "Dishes skipped".
- Home, debug builds only, when the offset isn't zero: a small `secondary` chip "Debug clock 20:05" at the end of the top bar's status line, so a shifted clock is never forgotten.

### Test

- `DebugTimeControllerTest.evening_canBeReachedWithoutWaiting`: Sun/Tue/Wed started; set 20:05 → KidTime; Kid asleep → Dishes; +20 min → the evening choice is pending.

---

## Slice 9: Code map and class headers

So that the next agent can find its way without reading every file.

- `CODEMAP.md` in the repo root: one row per Kotlin file under `app/src/main`, grouped by package: `path | what it does (one line) | talks to`. Keep it under ~250 lines.
- A short KDoc header (3–6 lines) on the core classes only: `DayOrchestrator`, `DayStateMachine`, `ScheduleEngine`, `SegmentBook`, `TimelineQueries`, `NotificationModelMapper`, `NotificationRenderer`, `AlarmScheduler`, the three receivers, `HistoryManager`, `InsightsManager`, `DemoDataGenerator`, `DebugTimeController`, `ActualDayMapper` and every ViewModel. Each says what the class is responsible for, the 1–3 rules that must stay true, and "Talks to: …".
- No change history in code comments. History lives in commit messages (say *why* in the body), `NOTES.md` and `CHANGELOG.md`.
- From now on, a commit that adds, removes or repurposes a file updates its `CODEMAP.md` row, and its header if it has one.

---

## Slice 10: Release 1.5.0

1. `versionName = "1.5.0"`, `versionCode = 10500`.
2. `CHANGELOG.md`, newest first:

```markdown
## 1.5.0
- Fixed: the app could stay on the splash screen after Android killed it mid-day, and the notification buttons did nothing then.
- Swap template works in every state, including after the day ended; it re-plans the rest of today from now.
- After the day ends, Home keeps the template title and shows what happened today.
- Lists use the full screen; back gestures fade like tab switches.
- History: cleaner week headers; the day detail no longer overlaps blocks.
- Clearer wording on Weekly, Insights and Time Debt, with a "What these mean" explainer.
- Settings: feedback for every action, and an Alert sound setting.
- Debug builds: clock controls to try any time of day, including the evening.
- CODEMAP.md and class headers for faster onboarding.
```

3. `issues.md`: remove the screenshot item (no longer required); keep the device-test item if Roni hasn't run them; add anything new that's still open, with evidence.
4. `MANUAL_TESTS.md`: put this list at the top, under "Check on the phone (1.5.0)":
   1. Pick a template, swipe the app away from recents, open it: Home appears within 3 seconds.
   2. Same, but tap Pause and then Next on the notification instead: both work.
   3. Emergency: the title stays, the status says "Stopped early (emergency)", and the calendar shows what happened today.
   4. After the day ends, tap swap: the dialog, then the picker, then tracking restarts from now.
   5. Swap mid-day: the new template takes over from now.
   6. History and Weekly fill the screen, with no big gap under the top bar.
   7. History's week headers line up with the cards.
   8. A day in History: no overlapping blocks, pauses hatched, skipped blocks listed in the summary.
   9. Swipe back from Settings: it fades.
   10. Load demo data: a spinner, then "Demo data loaded: 28 days".
   11. Settings → Alert sound opens Android's sound settings for the app.
   12. Debug build: Developer → "20:05 · Evening prompt" → "Kid asleep now" → "Skip dishes" → the evening choice sheet appears on Home.
   13. Weekly, Insights and Time Debt: every number has a short explanation, and ⓘ explains the terms.
5. Final gate: `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin assembleDebug assembleRelease :app:assembleBenchmark :macrobenchmark:assembleBenchmark`.
6. Merge `task6` into `master` and `git tag v1.5.0`.

---

## Final report

1. Each slice: done or not done, and the test that proves it, if it has one.
2. The splash bug's root cause, in one or two sentences.
3. Tests added, tests changed (and why) and tests deleted.
4. What you checked on the phone, if one was connected, and the screenshot files.
5. `versionName` / `versionCode`, the `CHANGELOG.md` entry, and the tag.
6. The updated `NOTES.md` and `issues.md`.
