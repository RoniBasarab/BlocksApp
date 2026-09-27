# TASK 4: One notification, clear controls, polished screens, behavior tests

Read `SUMMARY.md` first if you have no context. This task overrides every earlier document where they differ.

## Rules

1. Phases in order. A phase is done only when every checkbox is ticked. "Implemented but unverified" is not done if a test can verify it.
2. **The tests in Phase 4 are the spec.** You may change them only to make them compile (imports, real class/function names, constructor shapes of fake states). Never change an assertion, a string, a tag or a tolerance. If a test fails, fix the app.
3. Add the Phase 4 test files **first** (they will fail), then implement Phases 1–3 until they pass.
4. After each phase: `./gradlew assembleDebug testDebugUnitTest`, then commit `task4 phase N: <title>`.
5. All user-facing strings below are exact. Put them in `strings.xml`.

---

## Phase 1: One notification + clean app entry

### 1.1 Build setup

- `debug` build type: `applicationIdSuffix = ".debug"`. Roni's daily app is the **release** build (`com.roni.blocks`); tests and experiments never touch it.
- Add `androidx.lifecycle:lifecycle-process` (check docs for the version matching the project's lifecycle libs).

### 1.2 The notification model (pure, JVM-testable)

```kotlin
enum class NotificationChannelKind { DAY, EVENING }

data class NotificationActionModel(
    val label: String,
    val event: ActionEvent,      // PAUSE, RESUME, START_NEXT, CONTINUE_WHERE_I_WAS, JUMP_TO_NOW, KID_ASLEEP, END_DAY
    val forBlockId: String?,     // the block this action was created for; stale → ignored
)

data class NotificationModel(
    val key: String,             // identity of what's shown, e.g. "b3:running", "b3:break", "b1:leak", "kid"
    val channel: NotificationChannelKind,
    val title: String,
    val text: String,
    val countdownTo: Instant?,   // null = no chronometer
    val actions: List<NotificationActionModel>,   // max 2
    val accentArgb: Int,
)

object NotificationModelMapper {
    fun from(state: DayState, timeline: /* engine timeline type */, now: Instant): NotificationModel?   // null = no notification
}
```

Exact content per state (times are examples):

| State | Channel | Title | Text | Countdown to | Actions |
|---|---|---|---|---|---|
| NotStarted, 09:00–10:00 | DAY | Start your day | Pick today's template | — | — |
| NotStarted, after 10:00 | — | *(no notification)* | | | |
| Running, focus | DAY | Coding · Focus 2/2 | until 11:30 | block effective end | Pause |
| Running, Pomodoro break | DAY | Coding · Break | focus resumes at 10:30 | break end | Pause |
| Running, block without phases | DAY | CV | until 09:30 | effective end | Pause |
| Block ended (leaking) | DAY | CV ran over | Start Coding when you're ready | effective end (shows negative) | Start Coding |
| Paused | DAY | Paused · Coding | Resume when you're back | — | Resume |
| Interrupted | DAY | Schedule frozen | Emergency since 10:05 | — | Continue where I was, Jump to now |
| KidTime before 20:00 | — | *(no notification)* | | | |
| KidTime from 20:00 | EVENING | Kid time | Tap Kid asleep to start your evening | — | Kid asleep, End day |
| Dishes | EVENING | Dishes | until 20:25 | dishes end | End day |
| Evening choice pending | EVENING | Pick your evening | Open the app to choose | — | End day |
| Evening (e.g. Game) | EVENING | Evening · Game | until 22:30 | 22:30 | End day |
| Ended / Off | — | *(no notification)* | | | |

"Start X" always names the block you will actually land in (if the clock has passed the next block too, it names the one the clock is in).

### 1.3 The renderer

```kotlin
class NotificationRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val visibility: AppVisibility,          // ProcessLifecycleOwner-backed; fake in tests
    private val dismissals: DismissalStore,         // remembers the dismissed model key (prefs)
) {
    fun ensureChannels()                             // create "day" + "evening"; delete "status" + "day_alerts"
    fun render(model: NotificationModel?, transition: Boolean)
    fun onDismissed(key: String)                     // called from the notification's deleteIntent
}
```

Rules:
- One constant ID (`NOTIFICATION_ID = 1`). `model == null` → cancel it.
- Channel changes (DAY ↔ EVENING): `cancel(ID)` then post on the new channel.
- `setOnlyAlertOnce(!transition || visibility.isForeground)`: transitions alert (sound on `day`) unless the app is on screen; refreshes never alert.
- If `dismissals` holds `model.key` and `transition == false` → don't post. A transition clears the dismissal.
- Builder: `setSmallIcon` (monochrome app icon), `setContentTitle`, `setContentText`, `setColor(accentArgb)`, `setOngoing(true)`, `setCategory(Notification.CATEGORY_STOPWATCH)`, `setVisibility(VISIBILITY_PUBLIC)`, `setDeleteIntent(→ onDismissed(key))`. If `countdownTo != null`: `setWhen(systemMillis(countdownTo))`, `setShowWhen(true)`, `setUsesChronometer(true)`, `setChronometerCountDown(true)`.
- **Content intent = launcher intent**, which resumes the existing task exactly as it was, and does nothing if the app is already in front:

```kotlin
val open = Intent(context, MainActivity::class.java)
    .setAction(Intent.ACTION_MAIN)
    .addCategory(Intent.CATEGORY_LAUNCHER)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
val contentIntent = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
```

- `MainActivity` keeps the default `launchMode`. `onNewIntent` does nothing (no navigation reset).
- Action buttons → `NotificationActionReceiver` (explicit broadcast) with extras `event` + `forBlockId`. Never start an activity from an action.
- Delete the old two-notification code paths entirely.

### 1.4 Stale / double actions

`DayEvent`s that come from buttons carry `expectedBlockId`. `DayOrchestrator` ignores an event whose `expectedBlockId` doesn't match the current block (double taps, old notifications). After ignoring, it re-renders the notification (refresh, no sound).

### 1.5 App entry rules (what the user sees)

| Situation | Result |
|---|---|
| Launch, day not started | Template picker |
| Launch, day running / kid time / evening / ended / off | Home |
| Notification tapped, app in foreground (any screen) | Nothing changes; the shade just closes |
| Notification tapped, app in background | App returns to the exact screen it was on |
| Notification tapped after the app was swiped from recents | Fresh start → Home (or picker if not started). No extra screens |
| Action button tapped (any app state) | State changes, notification updates in place, app does not open |
| Back on Home | Leaves the app (never back to the picker) |
| Evening choice pending | Sheet shows **on Home only**; other screens are never interrupted |

### 1.6 Receivers

Confirm every receiver calls `pending.finish()` in `finally` **after** the suspend work (Task-2 §A1). `NOTES.md`'s last bullet says otherwise. Fix the code or the note, whichever is wrong.

### 1.7 Debug time travel (debug build only, `src/debug/`)

- `DebugClock`: system time + offset minutes (stored in prefs). Hilt binds it as the `Clock` in debug; release keeps the system clock.
- Everything that talks to Android in wall time (`AlarmManager`, notification `when`) converts through one `SystemTimeMapper` (identity in release, `− offset` in debug).
- `TimeTravelReceiver` (exported in the debug manifest only). It updates the offset, then calls `orchestrator.onTimeChanged()` (re-evaluate + reschedule; a time change is **not** an interruption; only reboot downtime is):

```
adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --es to 09:31
adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --ei plus 30
adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --ez reset true
```

### Phase 1 done when

- [ ] `NotificationModelMapperTest` and `NotificationRendererTest` pass.
- [ ] `DayOrchestratorTest.staleAction_isIgnored` passes.
- [ ] Old notification IDs/channels removed from code.

---

## Phase 2: NowCard that explains itself

### 2.1 Layout

```
┌──────────────────────────────────────────────┐
│ ● Coding                    [Focus 2/2]   ⓘ  │  ⓘ = IconButton, contentDescription "What the buttons do"
│ 42 min left                     until 11:30  │
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬──────────────────             │
│ [          Start Free          ]             │  primary: full width, 48dp, only when the block has ended
│ [⏸ Pause] [⚠ Emergency] [✓ Finish early]     │  secondary: 3 equal-width FilledTonalButtons, icon + label
└──────────────────────────────────────────────┘
```

- Card `testTag("now_card")`. Primary button `testTag("now_primary")`.
- Secondary buttons: `Row` with `Arrangement.spacedBy(Spacing.s)`, each `Modifier.weight(1f)`, icon 18dp + label `labelLarge`, `maxLines = 1`. Button container `surfaceRaised`, content `textPrimary`.
- No icon-only buttons anywhere on the card.

### 2.2 Buttons per state (exact labels)

| State | Primary (full width) | Secondary row |
|---|---|---|
| Running / break (block not over) | — | Pause · Emergency · Finish early |
| Block ended (leaking) | Start *Next* | Emergency |
| Paused | Resume | Emergency |
| Interrupted | I'm back | — |
| Kid time | Kid asleep | End day |
| Dishes | Choose evening | End day |
| Evening | — | End day (single, full width) |
| Ended / Off | — | — |

Leaking chip: `Ran over`. Leaking subtitle: `CV ended at 09:30`.

### 2.3 Explanations (one sentence each, exact text)

| Button | Dialog title | Body | Confirm | Shown |
|---|---|---|---|---|
| Pause | Pause Coding? | For short breaks like the bathroom or a call: Coding keeps its remaining time, and the paused minutes come out of the next block. | Pause | Every time, until "Don't ask again" is checked |
| Emergency | Freeze the schedule? | For something urgent: everything stops, nothing counts as debt, and when you're back you choose to continue where you left off or jump to the current time. | Freeze | Always |
| Finish early | Finish Coding early? | Coding ends now and the minutes left become free time. | Finish early | Every time, until "Don't ask again" is checked |
| I'm back | Welcome back | Continue where you left off (the rest of the day moves later) or jump to where the clock is now (missed blocks are marked as skipped). | Continue where I was / Jump to now | Always |

- Dismiss button on every dialog: `Cancel` (except "I'm back", which has the two choices).
- "Don't ask again" = checkbox row in the dialog; stored in `settings` prefs per button.
- The block name in titles/bodies is the current block's title.
- **ⓘ info dialog** (`AlertDialog`, title `What the buttons do`), four rows, label in `titleSmall` + sentence in `bodyMedium textSecondary`:
  - **Pause:** the Pause sentence above.
  - **Emergency:** the Emergency sentence above.
  - **Finish early:** the Finish early sentence above.
  - **Start next:** `Ends the current block and starts the next one; if you're late, the time you ran over counts as debt.`
- Primary "Start X" has no confirmation.

### Phase 2 done when

- [ ] `NowCardExpectationsTest` passes.
- [ ] The word "Interrupt" no longer appears in any user-facing string (code names may keep it).

---

## Phase 3: Screen polish (Weekly, Insights, History, Time Debt, Settings)

### 3.1 Shared primitives (`designsystem/`)

```kotlin
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
        content = content,
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, extra: @Composable (() -> Unit)? = null) {
    val c = AppTheme.colors
    Box(modifier.fillMaxSize().padding(horizontal = Spacing.xxl), contentAlignment = Alignment.Center) {
        Column(Modifier.testTag("empty_state"), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).background(c.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = c.primary)
            }
            Spacer(Modifier.height(Spacing.l))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.s))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
            extra?.let { Spacer(Modifier.height(Spacing.l)); it() }
        }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AppCard(modifier.testTag("section_card").fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {   // add contentPadding param to AppCard
        Column(content = content)
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = AppTheme.colors.textPrimary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .testTag("settings_row")                 // tag FIRST so bounds are the full row
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon)                             // 36dp circle, primary @12%, 20dp icon in primary
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary) }
        }
        trailing?.invoke()
    }
}
```

Dividers inside a `SectionCard`: `HorizontalDivider(Modifier.padding(start = 64.dp), color = outline)`.

### 3.2 Top bars

- Weekly, Insights: `CenterAlignedTopAppBar`, title centered (`titleLarge`), burger menu on the right, transparent container.
- History, Time Debt, Settings: `CenterAlignedTopAppBar`, back arrow left, title centered.
- Each `XScreen(state, onAction)` is **content only** (no bars). The bars live in the shell. The tests render `XScreen` alone.

### 3.3 Screens (layouts from `Task-2.md` §F/§G, built with the primitives)

| Screen | Tags | Empty state (icon · title · body) |
|---|---|---|
| Weekly | `week_selector` (centered pill), `weekly_hero`, `weekly_chart`, `stat_tile` ×4 (2×2, 12dp gaps) | `bar_chart` · No data for this week yet · Finish a day and it shows up here. |
| Insights | `insights_headline`, `insights_scorecard`, `scorecard_row` ×8 (≥ 56dp) | `insights` · Insights need a full week · {n} of 7 days logged. (+ progress bar as `extra`) |
| History | `LazyColumn`, `contentPadding = PaddingValues(Spacing.l)`, `history_day_row`, week headers "This week" / "Last week" / "13 – 19 Sep" | `history` · No history yet · Finished days are listed here. |
| Time Debt | `debt_hero`, `debt_chart`, `debt_day_row` | `hourglass_empty` · No time debt yet · When a block runs over, it shows up here. |

All cards span the full width minus the 16dp gutters. Nothing floats left-aligned on its own.

### 3.4 Settings (grouped cards)

```
Notifications                       ← SectionHeader (labelLarge, primary), 24dp top, 8dp bottom, 4dp start
┌─────────────────────────────────┐
│ (🔔) Notification permission   Allowed › │
│ (⏰) Exact alarms              Allowed › │
│ (✉) Test notification            Send   │
└─────────────────────────────────┘
Display
┌─────────────────────────────────┐
│ (◉) 24-hour (09:00)                     │  tag hour_format_24
│ (○) 12-hour (9 AM)                      │  tag hour_format_12
└─────────────────────────────────┘
Data
┌─────────────────────────────────┐
│ (📄) Share log file                    › │
│ (↺) Reset today                       › │
│ (🗑) Clear history  (leak color)       › │
└─────────────────────────────────┘
About
┌─────────────────────────────────┐
│ (🔒) All data stays on this device       │
└─────────────────────────────────┘
                                V 1.0.0
```

- Radio rows: `Modifier.testTag(...).selectable(selected, onClick, role = Role.RadioButton)`, `heightIn(min = 56.dp)`, `RadioButton(selected, onClick = null)` leading instead of the icon circle.
- Trailing status: `labelLarge textSecondary` + chevron `textMuted`. "Send" is a `TextButton`.
- Version pinned to the bottom-right when content is short, after the content when it's long:

```kotlin
BoxWithConstraints(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight)
            .padding(horizontal = Spacing.l),
    ) {
        /* sections */
        Spacer(Modifier.weight(1f))
        Text(
            "V ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textMuted,
            modifier = Modifier.align(Alignment.End).padding(vertical = Spacing.l),
        )
    }
}
```

### Phase 3 done when

- [ ] `UiExpectationsTest` passes.

---

## Phase 4: Tests

Put these in `app/src/test/java/com/roni/blocks/expectations/`. Create `FakeStates` / `FakeNotificationModels` in the same package with realistic Sun/Tue/Wed data.

### 4.1 `UiExpectationsTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class UiExpectationsTest {
    @get:Rule val compose = createComposeRule()

    private val gutter = 16.dp
    private val tol = 1.dp

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { BlocksTheme { content() } }
        compose.waitForIdle()
    }

    private fun root(): DpRect = compose.onRoot().getBoundsInRoot()

    private fun assertNear(expected: Dp, actual: Dp, what: String, tolerance: Dp = tol) =
        assertTrue("$what: expected $expected ± $tolerance but was $actual", abs((expected - actual).value) <= tolerance.value)

    private fun assertCenteredX(node: DpRect, what: String) {
        val r = root()
        assertNear((r.left + r.right) / 2, (node.left + node.right) / 2, "$what horizontal center")
    }

    private fun assertGutters(tag: String) {
        val r = root()
        val n = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size
        assertTrue("expected at least one '$tag'", n > 0)
        repeat(n) { i ->
            val b = compose.onAllNodesWithTag(tag)[i].getBoundsInRoot()
            assertNear(r.left + gutter, b.left, "$tag[$i] left edge")
            assertNear(r.right - gutter, b.right, "$tag[$i] right edge")
        }
    }

    private fun checkEmptyState(title: String, content: @Composable () -> Unit) {
        show(content)
        val r = root()
        val box = compose.onNodeWithTag("empty_state").assertIsDisplayed().getBoundsInRoot()
        assertCenteredX(box, "empty_state")
        assertNear((r.top + r.bottom) / 2, (box.top + box.bottom) / 2, "empty_state vertical center", tolerance = 48.dp)
        assertCenteredX(compose.onNodeWithText(title).assertIsDisplayed().getBoundsInRoot(), "empty_state title")
    }

    @Test fun weekly_empty_isCentered() = checkEmptyState("No data for this week yet") { WeeklyScreen(FakeStates.weeklyEmpty(), {}) }
    @Test fun insights_empty_isCentered() = checkEmptyState("Insights need a full week") { InsightsScreen(FakeStates.insightsEmpty(daysLogged = 3), {}) }
    @Test fun history_empty_isCentered() = checkEmptyState("No history yet") { HistoryScreen(FakeStates.historyEmpty(), {}) }
    @Test fun timeDebt_empty_isCentered() = checkEmptyState("No time debt yet") { TimeDebtScreen(FakeStates.timeDebtEmpty(), {}) }

    @Test fun weekly_layout() {
        show { WeeklyScreen(FakeStates.weeklyWithData(), {}) }
        assertCenteredX(compose.onNodeWithTag("week_selector").getBoundsInRoot(), "week_selector")
        assertGutters("weekly_hero")
        assertGutters("weekly_chart")
        compose.onAllNodesWithTag("stat_tile").assertCountEquals(4)
        val t = (0 until 4).map { compose.onAllNodesWithTag("stat_tile")[it].getBoundsInRoot() }
        val r = root()
        assertNear(r.left + gutter, t[0].left, "tile 0 left")
        assertNear(r.right - gutter, t[1].right, "tile 1 right")
        assertNear(t[0].width, t[1].width, "tile widths")
        assertNear(12.dp, t[1].left - t[0].right, "tile column gap")
        assertNear(t[0].top, t[1].top, "tiles 0/1 share a row")
        assertNear(12.dp, t[2].top - t[0].bottom, "tile row gap")
    }

    @Test fun insights_layout() {
        show { InsightsScreen(FakeStates.insightsWithData(), {}) }
        assertGutters("insights_headline")
        assertGutters("insights_scorecard")
        val rows = compose.onAllNodesWithTag("scorecard_row")
        rows.assertCountEquals(8)
        repeat(8) { assertTrue("scorecard_row $it ≥ 56dp", rows[it].getBoundsInRoot().height >= 56.dp - tol) }
    }

    @Test fun history_layout() {
        show { HistoryScreen(FakeStates.historyWithDays(), {}) }
        compose.onNodeWithText("This week").assertIsDisplayed()
        assertGutters("history_day_row")
    }

    @Test fun settings_layout() {
        show { SettingsScreen(FakeStates.settingsDefault(), {}) }
        listOf("Notifications", "Display", "Data", "About").forEach { compose.onNodeWithText(it).assertExists() }
        assertGutters("section_card")
        val rows = compose.onAllNodesWithTag("settings_row")
        val n = rows.fetchSemanticsNodes().size
        assertTrue("expected ≥ 7 settings rows, found $n", n >= 7)
        repeat(n) { assertTrue("settings_row $it ≥ 56dp", rows[it].getBoundsInRoot().height >= 56.dp - tol) }
        compose.onNodeWithTag("hour_format_24").assertIsSelected()
        compose.onNodeWithTag("hour_format_12").assertIsNotSelected()
        assertTrue(compose.onNodeWithTag("hour_format_24").getBoundsInRoot().height >= 56.dp - tol)
        val version = compose.onNodeWithText("V 1.0.0").performScrollTo().getBoundsInRoot()
        assertNear(root().right - gutter, version.right, "version right edge")
    }

    @Test fun templatePicker_gridIsEvenAndGuttered() {
        show { TemplatePickerScreen(FakeStates.pickerOnSunday(), {}) }
        val ids = listOf("MON_THU", "SUN_TUE_WED", "FRI_SAT", "OFF")   // use the real template ids
        val b = ids.map { compose.onNodeWithTag("template_card_$it").getBoundsInRoot() }
        val r = root()
        assertNear(r.left + gutter, b[0].left, "left column")
        assertNear(r.right - gutter, b[1].right, "right column")
        assertNear(b[0].width, b[1].width, "card widths")
        assertNear(12.dp, b[1].left - b[0].right, "column gap")
        assertNear(b[0].top, b[1].top, "row 1 aligned")
        assertNear(12.dp, b[2].top - b[0].bottom, "row gap")
    }
}
```

### 4.2 `NowCardExpectationsTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class NowCardExpectationsTest {
    @get:Rule val compose = createComposeRule()
    private val actions = mutableListOf<HomeAction>()

    private fun show(ui: NowCardUi) {
        compose.setContent { BlocksTheme { NowCard(ui, { "42 min left" }, { 0.3f }, { actions += it }) } }
        compose.waitForIdle()
    }

    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isDialog()))

    @Test fun running_hasThreeLabeledEqualButtons_andNoStart() {
        show(FakeStates.nowRunningCoding())
        val labels = listOf("Pause", "Emergency", "Finish early")
        labels.forEach { compose.onNodeWithText(it).assertIsDisplayed().assertHasClickAction() }
        val widths = labels.map { compose.onNodeWithText(it).getBoundsInRoot().width }
        assertTrue("equal widths: $widths", widths.max() - widths.min() <= 1.dp)
        compose.onAllNodesWithText("Start", substring = true).assertCountEquals(0)
    }

    @Test fun endedBlock_showsFullWidthStartNamingTheNextBlock() {
        show(FakeStates.nowLeakingCv())                       // CV 09:00–09:30 ended, next = Coding
        val card = compose.onNodeWithTag("now_card").getBoundsInRoot()
        val primary = compose.onNodeWithTag("now_primary").assertTextEquals("Start Coding").getBoundsInRoot()
        assertTrue("primary spans the card", primary.width >= card.width - 32.dp - 1.dp)
        compose.onNodeWithText("Ran over").assertIsDisplayed()
        compose.onNodeWithText("CV ended at 09:30").assertIsDisplayed()
    }

    @Test fun pause_explainsBeforeActing() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithText("Pause").performClick()
        compose.onNodeWithText("the paused minutes come out of the next block", substring = true).assertIsDisplayed()
        assertTrue("nothing happens before confirming", actions.isEmpty())
        inDialog("Pause").performClick()
        assertEquals(1, actions.size)                           // and it is the pause action
    }

    @Test fun emergency_alwaysExplains_andCancelDoesNothing() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithText("Emergency").performClick()
        compose.onNodeWithText("nothing counts as debt", substring = true).assertIsDisplayed()
        inDialog("Cancel").performClick()
        assertTrue(actions.isEmpty())
    }

    @Test fun finishEarly_mentionsFreeTime() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithText("Finish early").performClick()
        compose.onNodeWithText("the minutes left become free time", substring = true).assertIsDisplayed()
    }

    @Test fun infoButton_explainsEveryAction() {
        show(FakeStates.nowRunningCoding())
        compose.onNodeWithContentDescription("What the buttons do").performClick()
        listOf("next block", "nothing counts as debt", "free time", "ran over counts as debt").forEach {
            compose.onNodeWithText(it, substring = true).assertExists()
        }
    }
}
```

### 4.3 `NotificationRendererTest.kt` (Robolectric)

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val visibility = FakeAppVisibility(foreground = false)
    private val renderer = NotificationRenderer(context, visibility, InMemoryDismissalStore())

    @Before fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        renderer.ensureChannels()
    }

    private fun only() = nm.activeNotifications.single().notification

    @Test fun channels() {
        val ids = nm.notificationChannels.map { it.id }
        assertTrue("day" in ids && "evening" in ids)
        assertFalse("status" in ids || "day_alerts" in ids)
        assertEquals(NotificationManager.IMPORTANCE_LOW, nm.getNotificationChannel("evening").importance)
        assertFalse(nm.getNotificationChannel("day").shouldVibrate())
    }

    @Test fun aWholeDay_neverShowsMoreThanOne() {
        FakeNotificationModels.wholeSundayInOrder().forEach { (model, transition) ->   // ends with (null, true)
            renderer.render(model, transition)
            assertTrue("at most one notification", nm.activeNotifications.size <= 1)
        }
        assertEquals(0, nm.activeNotifications.size)
    }

    @Test fun running_usesSystemCountdown_withPauseOnly() {
        renderer.render(FakeNotificationModels.runningCoding(), transition = true)
        val n = only()
        assertEquals("day", n.channelId)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean("android.chronometerCountDown"))   // set by setChronometerCountDown(true)
        assertEquals(listOf("Pause"), n.actions.map { it.title.toString() })
        assertNotEquals(0, n.flags and Notification.FLAG_ONGOING_EVENT)
    }

    @Test fun transitionAlerts_refreshIsSilent() {
        renderer.render(FakeNotificationModels.runningCoding(), transition = true)
        assertEquals(0, only().flags and Notification.FLAG_ONLY_ALERT_ONCE)
        renderer.render(FakeNotificationModels.runningCoding(), transition = false)
        assertNotEquals(0, only().flags and Notification.FLAG_ONLY_ALERT_ONCE)
    }

    @Test fun silentWhileAppIsVisible() {
        visibility.foreground = true
        renderer.render(FakeNotificationModels.leakingCv(), transition = true)
        assertNotEquals(0, only().flags and Notification.FLAG_ONLY_ALERT_ONCE)
    }

    @Test fun tap_resumesLikeTheLauncher() {
        renderer.render(FakeNotificationModels.runningCoding(), transition = true)
        val intent = shadowOf(only().contentIntent).savedIntent
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertNotEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test fun swipedAway_staysGoneUntilTheNextTransition() {
        val running = FakeNotificationModels.runningCoding()
        renderer.render(running, transition = true)
        renderer.onDismissed(running.key)
        nm.cancelAll()
        renderer.render(running, transition = false)
        assertEquals(0, nm.activeNotifications.size)
        renderer.render(FakeNotificationModels.breakCoding(), transition = true)
        assertEquals(1, nm.activeNotifications.size)
    }

    @Test fun dayToEvening_switchesChannel_keepsOne() {
        renderer.render(FakeNotificationModels.leakingCv(), transition = true)
        renderer.render(FakeNotificationModels.kidTimePrompt(), transition = true)
        assertEquals("evening", only().channelId)
        assertEquals(listOf("Kid asleep", "End day"), only().actions.map { it.title.toString() })
    }

    @Test fun ended_removesIt() {
        renderer.render(FakeNotificationModels.runningCoding(), transition = true)
        renderer.render(null, transition = true)
        assertEquals(0, nm.activeNotifications.size)
    }
}
```

### 4.4 `NotificationModelMapperTest.kt` (pure JVM)

One test per row of the §1.2 table: build the state + timeline + `now`, call `NotificationModelMapper.from(...)`, and assert channel, title, text, `countdownTo` and action labels exactly as written in the table. Include the two "no notification" rows (NotStarted after 10:00, KidTime before 20:00) and Ended/Off.

### 4.5 `DayOrchestratorTest` additions

- `staleAction_isIgnored`: CV leaking → `Next(expectedBlockId = cv)` → Coding. Send the same event again → state unchanged, no extra segment.
- `doubleTap_advancesOnce`: two identical events in a row → exactly one transition.
- `startNext_whenClockPassedNextBlock_landsOnBlockContainingNow`: the skipped ones become `CONSUMED`.
- `timeTravel_isNotAnInterruption`: `onTimeChanged()` after moving the clock → no `INTERRUPTION` segment.

### 4.6 Device tests (`app/src/androidTest/`, Roni runs them)

Use UiAutomator. Before each test: clear the debug app's prefs, cancel notifications, set the time with `TimeTravelReceiver` (send it as an explicit broadcast from the test). Command: `./gradlew connectedDebugAndroidTest` with the phone connected.

| # | Scenario | Steps | Expected |
|---|---|---|---|
| D1 | Tap notification while app is open | Start day (Sun/Tue/Wed at 09:40) → open Settings → open shade → tap notification | Shade closes, Settings still shown, Back returns to Home |
| D2 | Tap notification from background | Home → press Home → shade → tap notification | App returns to Home, same state; no picker, no second instance |
| D3 | Action with app in background | Travel to 09:31 (CV ended) → press Home → shade → tap "Start Coding" | Notification title becomes "Coding · Focus 1/2"; the app does **not** come to front |
| D4 | Action with app open | Same as D3 but stay on Home | NowCard switches to Coding immediately; no navigation |
| D5 | Double tap | Tap "Start Coding" twice quickly | Lands on Coding (not Free) |
| D6 | Swipe notification away | Swipe it → open and close the app | It stays gone; travel forward to the next Pomodoro switch or block end → it comes back |
| D7 | Process killed | Press Home → `am kill` the package → travel past the block end | Notification updates to the leak state |
| D8 | Evening from Settings | Kid time, 20:05 → open Settings → tap notification | Settings stays; the choice sheet appears only when you open Home |
| D9 | Relaunch from launcher after swipe from recents | (manual) swipe the app from recents → tap the notification | Fresh start straight to Home |
| D10 | Reboot mid-block | (manual) reboot at 10:10 | Silent notification for Coding with the correct countdown |
| D11 | Lock screen | (manual) lock the phone during a block | Lock screen shows the block and a live countdown |
| D12 | Do Not Disturb | (manual) DND on → block ends | No sound; notification still updates |

Automate D1–D8. List D9–D12 in `MANUAL_TESTS.md` as a checklist for Roni.

---

## Phase 5: Screenshots + report

Re-record Roborazzi screenshots into `screenshots/`: all previous ones plus `now_card_running.png`, `now_card_leaking.png`, `dialog_pause.png`, `dialog_info.png`, `weekly.png`, `weekly_empty.png`, `insights.png`, `insights_empty.png`, `history.png`, `history_empty.png`, `time_debt.png`, `time_debt_empty.png`, `settings.png`.

## Final report

1. Every checkbox, ticked or not.
2. Test results per class (JVM). Say explicitly that device tests (D1–D8) were written but must be run by Roni.
3. Any test you had to touch, and exactly what you changed (compile-only changes are the only allowed ones).
4. Screenshot list.
5. Updated `NOTES.md`, with contradictions removed.
