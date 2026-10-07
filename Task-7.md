# TASK 7: Editable days (2.0.0)

Read `SUMMARY.md` first, then this file, then `CODEMAP.md` (the real files and what they talk to) and `NOTES.md` (what earlier tasks decided). This task overrides earlier documents where they differ; `schedule-spec.md`, `architecture.md`, `visuals.md` and `SUMMARY.md` already describe the result.

**Target version: 2.0.0.** Roni can now reshape today's plan during the day, and the stored day changes shape (migrated automatically). Versioning policy: `SUMMARY.md` §14.

## What Roni asked for (7 Oct, after using 1.5.1)

1. Picking a template after 9:00 should land on the block the clock is in, not on CV followed by a string of Next taps. Earlier blocks count as **missed**.
2. Settings → Developer: the debug clock buttons spill over three rows and overlap the rows around them.
3. History's day view is unreadable (`SUMMARY.md` §9): two colored slivers with no names, unexplained diagonal lines, a "+47m" with no context, red lines, and a big empty gap under the title. It must read at a glance.
4. Weekly's "Planned vs actual" shows only the actual bars, and its legend's two colors don't match the bars, which use one color per block type.
5. Time Debt: he asked whether it's needed. It's being removed (`SUMMARY.md` §10 has the reasoning).
6. Pomodoro: show the 50/10 focus and break parts inside Coding blocks, with the time left.
7. **The 2.0.0 feature:** drag a block's top and bottom edges to change its times, with the blocks after it pushed along; change a block's type ("10–11 was Free, make it Coding"). "Days are dynamic."

## Decisions for this task

| Topic | Decision |
|---|---|
| Scope | **Today only.** Templates stay as they are for future days. |
| What has handles | Work blocks (CV, Coding, Gym). Free time has none: it's the slack around the work blocks. To shape free time, change its type or drag the work blocks next to it. |
| Pushing | A block that grows takes the free time next to it first. Work blocks in the way move along, keeping their length, until free time absorbs the change. |
| Fixed points | Kid time never moves; a push stops there. Nothing before the current block changes. |
| The current block | Only its end moves (not into the next 5 minutes). Its start and type are fixed; Next covers "switch to something else". After Next ended it early, it's fixed. |
| Grid | 5-minute steps. Work blocks are at least 10 minutes long. |
| Sound | Edits are silent. The alarm and the notification's countdown follow them. |
| Stats | Compare against the day's final plan; the day is flagged `PLAN_EDITED`. |
| Missed | One word for every planned block that never happened, with its reason (a late start, or an earlier block ran over). It replaces "skipped". |
| Undo, haptics | No undo (drag it back). No haptics: the app never vibrates. |

## Rules

1. Branch `task7` from `master`. Work in slices: change a few files, run `./gradlew :app:compileDebugKotlin`, then `./gradlew :app:testDebugUnitTest`, then commit `task7 slice N: <title>`. Never start a slice on a red build.
2. Work in the foreground. Don't delegate to background tasks or sub-agents.
3. **Time-box.** If a question takes more than ~20 minutes of analysis, write what you know in `NOTES.md`, apply the simplest fix that makes the named tests pass, and keep going.
4. **Tests** (`SUMMARY.md` §15): add the tests this file names; they're logic-level JVM tests. Existing tests stay green. Change an existing test only where this task changes the behavior it checks, and delete the tests of code this task removes. List every test you touched in the report.
5. **Roni's phone:** if `adb devices` lists it, check your work with the **debug app only** (`com.roni.blocks.debug`). Never install over, clear, force-stop or uninstall `com.roni.blocks`.
6. **Strings** are exact. Keep them where the codebase keeps them: inline literals, per `NOTES.md`.
7. **Real names win.** The sketches below use the names in `CODEMAP.md` and earlier tasks. Where one doesn't match the code (a color token, a function name), use the real one and note it in `NOTES.md`; don't build a parallel copy. Code marked *verified* compiles and passes its tests as given: copy it as it is.
8. Don't refactor beyond what a slice asks.
9. Slices 1–6 don't depend on 7–9. If you must stop early, stop at a slice boundary and report where.

---

## Slice 0: Commit the docs

Commit the spec and task files in the repo root as they are, on their own: `docs: specs and Task-7`.

---

## Slice 1: A late start lands on the current block

**Now:** picking a template at 10:40 starts the day on CV, already running over; Roni taps Next to catch up, and the overrun counts as ran over.

**Two new optional fields on `Segment`** (old records read as before):

```kotlin
val blockId: String? = null,          // the plan block this segment belongs to
val missReason: MissReason? = null,   // CONSUMED only; null means OVERRUN (older records)
```

Add `enum class MissReason { OVERRUN, LATE_START }` to `Model.kt`. From now on, `PLANNED`, `CONSUMED` and `SYNTHETIC_FREE` segments carry their block's id. A synthetic free segment carries the id of the work block it came from. `PAUSE` and `INTERRUPTION` segments may leave it null. Consumed segments recorded because a block ran over get `OVERRUN`.

**Rule:** `StartDay(template)` at time T lands exactly where a day started at its first block would be at T if the blocks that ended before T had never happened:
- Blocks that end at or before T are recorded the way consumed blocks are, with `missReason = LATE_START`. They're **missed**, not ran over.
- The block containing T is current from T and keeps its planned end. A Coding block's Pomodoro counts from T. The state, alarm and notification are what they'd be if the clock had reached that block normally; for the evening's kid time, the state is `KidTime`.
- If T is before the first block, nothing changes.
- At 22:30 or later, nothing starts. The picker checks first and shows a dialog: title "Too late to start", body "It's past 22:30, so there's nothing left to plan today.", button "OK". The orchestrator refuses too, so a late `StartDay` from anywhere changes nothing.

**Tests** (`DayOrchestratorTest`):
- `startDay_late_landsOnTheCurrentBlock`: Sun/Tue/Wed at 10:40 → Running on `sun_tue_wed.coding_am`, with its segment starting at 10:40. `sun_tue_wed.cv` is recorded as CONSUMED with `LATE_START` and its block id. The day's ran-over minutes are 0.
- `startDay_inKidTime_isKidTime`: Sun/Tue/Wed at 16:30 → `KidTime`; the five earlier blocks are recorded as CONSUMED with `LATE_START`.
- `startDay_after2230_isRefused`: at 22:40 the state stays `NotStarted`.

---

## Slice 2: A tidy debug clock card

**Bug (Roni):** in Settings → Developer, the time buttons spill over three rows and overlap the "Clock" row and "Kid asleep now".

**Likely cause:** a `FlowRow` inside a `SettingsRow`, which lays out one line at a fixed minimum height. Whatever the cause, replace the Developer section's "Clock" row, its chip row and the "Kid asleep now" / "Skip dishes" rows with one card that sizes to its content (no `FlowRow`). The demo data rows stay below it as `SettingsRow`s, and the existing snackbars stay. The card appears only in debug builds (`BuildConfig.DEBUG`): release has no debug clock, so it must never show or act there.

```kotlin
@Immutable data class DebugButtonUi(val top: String, val bottom: String?, val action: SettingsAction, val enabled: Boolean = true)
@Immutable data class DebugGroupUi(val title: String, val columns: Int, val buttons: ImmutableList<DebugButtonUi>)

@Composable
fun DebugClockCard(time: String, offset: String, groups: ImmutableList<DebugGroupUi>, onAction: (SettingsAction) -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth().testTag("debug_clock_card")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Debug clock", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                    Text(time, style = MaterialTheme.typography.headlineSmall.merge(AppType.numbers))
                    Text(offset, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)   // "Shifted +11h 02m" or "Real time"
                }
                OutlinedButton(onClick = { onAction(SettingsAction.ClockRealTime) }) { Text("Real time") }
            }
            groups.forEach { g ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(g.title, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    g.buttons.chunked(g.columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            row.forEach { DebugButton(it, onAction, Modifier.weight(1f)) }
                            repeat(g.columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugButton(b: DebugButtonUi, onAction: (SettingsAction) -> Unit, modifier: Modifier) {
    val c = AppTheme.colors
    FilledTonalButton(
        onClick = { onAction(b.action) },
        enabled = b.enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = AppShapes.card,
        contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xs),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.surfaceRaised, contentColor = c.textPrimary),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(b.top, style = MaterialTheme.typography.labelLarge.merge(AppType.numbers), maxLines = 1)
            b.bottom?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
```

Groups, in order:
- **"Jump to"** (2 columns): "09:31" / "CV ran over"; "12:00" / "Gym"; "16:20" / "Kid time"; "20:05" / "Evening prompt".
- **"Move forward"** (3 columns): "+10 min", "+30 min", "+1 hour". "+1 hour" is new: the same controller call with 60.
- **"Evening"** (2 columns): "Kid asleep now" (enabled only during kid time) and "Skip dishes" (enabled only while Dishes runs).

No new tests. If the phone is connected, take a screenshot of the card.

---

## Slice 3: Weekly shows plan vs actual per type as progress bars

**Bugs (Roni):** only the actual bars show, and the legend's colors don't match the per-type bars.

Replace the paired bar chart and its legend with one progress row per block type. The track is the plan, the fill is what got done, and the words say both, so no legend is needed.

```kotlin
@Immutable data class PlanProgressUi(
    val type: BlockType,
    val name: String,            // "Coding"
    val actual: String,          // "11h 05m"
    val planned: String,         // "13h"
    val fraction: Float,         // actual / planned
    val overText: String?,       // "1h 30m more than planned", only when actual > planned
)

@Composable
fun PlanProgressRow(p: PlanProgressUi) {
    val c = AppTheme.colors
    val accent = blockColors(p.type).accent        // the design system's BlockType → BlockColors mapping
    Column(Modifier.fillMaxWidth().testTag("plan_progress_row"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
            Spacer(Modifier.width(Spacing.s))
            Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(p.actual, style = MaterialTheme.typography.titleSmall.merge(AppType.numbers))
            Text(" of ${p.planned}", style = MaterialTheme.typography.bodyMedium.merge(AppType.numbers), color = c.textSecondary)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(p.fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(accent))
        }
        p.overText?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.textSecondary) }
    }
}
```

- The card's title stays "Planned vs actual time". Under it go rows 16dp apart for CV, Coding, Gym and Free (in that order, only those four), each one only if it had planned time that week.
- Compute both numbers from the week's day records when Weekly opens (seven small reads). If Weekly reads them from the `agg_week_*` aggregates today, stop using the aggregate for this card: that also fixes Roni's existing weeks without rebuilding anything.
- **Planned minutes** for a type: the sum, over the week's days, of that type's blocks in each day's `plannedBlocks` (records without them fall back to the template, as elsewhere). Leave out Off days and planned time after an emergency stop. **Actual minutes:** the time spent in that type's segments, with pauses excluded; synthetic free counts as Free. If the old code took planned time from anywhere else, that was the bug: say in the report what it was.
- When time was missed, the hero card's caption gets a second line (`bodySmall`, `textSecondary`): "Missed: 2h 10m of planned blocks that never happened".

**Test:**
- `weekly_plannedTime_comesFromTheDaysPlans`: a seeded demo week (seed 42) → the Coding row exists, and its planned minutes equal the sum of the Coding blocks in those days' `plannedBlocks`.

---

## Slice 4: Remove Time Debt

Why: everything it shows already appears elsewhere: on Weekly (the "Ran over" tile), in Insights ("Ran over", "Blocks that run long") and in History (each day's chip and recap). With editable days, a block that runs long is usually fixed by dragging its end, not paid back.

- Delete `feature/timedebt/TimeDebt.kt`, its ViewModel, its nav key and its menu entry, with its tests (such as `TimeDebtViewModelTest`, and any Time Debt screenshot or UI expectation tests). The burger menu becomes **History · Settings**.
- In `HistoryManager`, delete the debt code nothing else uses once the screen is gone; keep anything Weekly and Insights use (such as `debtMinutes`). If the day-end flush writes a separate debt record that only Time Debt read, stop writing it. Old stored debt records are ignored, with no migration.
- In "What these mean" (now on Weekly and Insights only), "Ran over (time debt)" becomes **"Ran over"**, with the same sentence.
- List the deleted tests in the report.

---

## Slice 5: History's day view becomes a recap you can read at a glance

**Bug (Roni, screenshot):** "This entire thing reads like an incoherent mess." Two colored slivers with no names, diagonal lines nobody explained, "+47m" without context, red lines, and an empty band of ~120dp under the top bar.

Replace the day view's calendar with a **recap**: a summary card, then one row per block in time order, with everything said in words. Home shows the same recap after the day ends, in place of the calendar and the NowCard. Put the mapper and the composables in `feature/home/DayRecap.kt`. Delete `ActualDayMapper.kt`, `ActualDayMapperTest` and the day view's hatching and overlay drawing, and update `CODEMAP.md`.

### 5.1 Models

```kotlin
enum class RecapTone { OVER, NEUTRAL, GOOD, MUTED }
@Immutable data class RecapBadge(val text: String, val tone: RecapTone)
@Immutable data class RecapRow(
    val id: String,                 // "<blockId>@<startMin>" (unique even in older records); "stop" and "evening_skipped" for those rows
    val type: BlockType?,           // null for the stop and evening-skipped rows
    val title: String,              // "Coding", "Evening · Game", "Day stopped (emergency)"
    val range: String,              // "09:30–12:17"
    val duration: String,           // "2h 47m" (end − start); "" for the stop row
    val badges: ImmutableList<RecapBadge>,
    val note: String?,
    val missed: Boolean,
)
@Immutable data class DayRecapUi(
    val headlineLabel: String?,     // "Plan followed"
    val headline: String,           // "82%"
    val caption: String,            // "5h 10m of the 6h 20m planned for CV, Coding and Gym"
    val chips: ImmutableList<RecapBadge>,
    val rows: ImmutableList<RecapRow>,
)
object DayRecapMapper { fun map(record: DayRecord, use24: Boolean): DayRecapUi }
```

Add `PLAN_EDITED` to `DayFlag` now (Slice 8 sets it).

### 5.2 Mapping

Rows come from the record's `plannedBlocks`, in time order (records without them fall back to the template, as today). Roni's real history is all older records, and the day he installs 2.0.0 mixes both kinds, so match like this:
- `PLANNED` and `CONSUMED` segments belong to a plan block by `blockId` when it's set, otherwise by planned start.
- `SYNTHETIC_FREE` belongs to the work block just before it; `PAUSE` and `INTERRUPTION` belong to the row they fall in, by time.
- Only `PLANNED` segments that match no plan block (the evening's Dishes and choice; older records) get rows of their own, built from their type and times.

| What happened | Row |
|---|---|
| A work block that happened | Actual start to actual end. Badges: "Ran over {d}" (OVER) if it ended after its planned end; "Paused {p}" (NEUTRAL) if it had pauses; "Done early" (GOOD) if Next ended it before its planned end. Note when done early: "The last {m} became free time." |
| The free time made by finishing early (`SYNTHETIC_FREE`) | No row; it's the note above |
| A Free block that happened | A plain row, with "Ran over {d}" if Next came after its end |
| Any block that started 5+ minutes after its planned start because the block that happened just before it ran over | Note: "Started {d} late ({that block's title} ran over)." |
| Missed (`CONSUMED`) | Planned times and length; `missed = true`; badge "Missed" (MUTED). Note: "The day started at {time}." for `LATE_START`; "{title of the last block that happened before it} ran over." for `OVERRUN` |
| Kid time, Dishes, the evening choice | Plain rows; the choice's title is "Evening · Game" (or Project, Read, Chill) |
| Emergency stop | A row at the stop time: title "Day stopped (emergency)", type null, no duration, no badges |
| `EVENING_SKIPPED` | The last row: title "Evening skipped", type null, note "Kid asleep wasn't tapped before 22:30." |
| Phone off (`INTERRUPTION`) | A note on the row it falls in: "The phone was off {from}–{to}." |
| Off day | No rows |

- Plan blocks after the day's last segment that have no segment of their own aren't listed: the day ended before them.
- Badge order: Ran over, Paused, Done early, Missed.

**Summary card:**
- `headlineLabel` "Plan followed". `headline` is the percentage, using Weekly's formula for this one day: worked (non-paused) minutes of CV, Coding and Gym ÷ their planned minutes, capped at 100%; planned time after an emergency stop doesn't count. `caption` is "{worked} of the {planned} planned for CV, Coding and Gym".
- Nothing planned for CV, Coding or Gym (a Family day): headline "—", caption "No CV, Coding or Gym was planned."
- Off day: no label, headline "Off day", caption "Nothing was planned."
- Chips, each only when it applies: "Ran over {total}" (OVER), "Paused {total}" (NEUTRAL), "Missed 1 block" / "Missed {n} blocks" (MUTED), "Plan changed" (NEUTRAL, `PLAN_EDITED`), "Template swapped" (NEUTRAL).

### 5.3 Layout

- The day view uses `ScreenScaffold` like the History list, applying each inset once. The ~120dp empty band in Roni's screenshot is the top bar's height added twice. The summary card's top sits 8dp below the top bar.
- `LazyColumn`, `contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp + navBarBottom)`, `verticalArrangement = spacedBy(8.dp)`, keyed by row id. On Home (inside the tab scaffold) the bottom padding is just 16dp: the navigation bar already applies its inset. No hatching, no red lines, nothing unlabeled.

```kotlin
@Composable
fun RecapSummaryCard(s: DayRecapUi) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth().testTag("recap_summary")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            s.headlineLabel?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = c.textSecondary) }
            Text(s.headline, style = MaterialTheme.typography.displaySmall.merge(AppType.numbers))
            Text(s.caption, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            if (s.chips.isNotEmpty()) FlowRow(Modifier.padding(top = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                s.chips.forEach { RecapPill(it) }
            }
        }
    }
}

@Composable
fun RecapRowCard(r: RecapRow) {
    val c = AppTheme.colors
    val accent = r.type?.let { blockColors(it).accent } ?: c.textMuted
    AppCard(Modifier.fillMaxWidth().alpha(if (r.missed) 0.55f else 1f).testTag("recap_row")) {
        Column(
            Modifier
                .drawBehind { drawRoundRect(accent, size = Size(4.dp.toPx(), size.height), cornerRadius = CornerRadius(2.dp.toPx())) }
                .padding(start = 4.dp + Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(r.duration, style = MaterialTheme.typography.bodyMedium.merge(AppType.numbers), color = c.textSecondary)
            }
            Text(r.range, style = MaterialTheme.typography.bodySmall.merge(AppType.numbers), color = c.textSecondary)
            if (r.badges.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                r.badges.forEach { RecapPill(it) }
            }
            r.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textMuted) }
        }
    }
}

@Composable
fun RecapPill(b: RecapBadge) {
    val c = AppTheme.colors
    val outline = MaterialTheme.colorScheme.outline
    val (fg, bg, border) = when (b.tone) {
        RecapTone.OVER -> Triple(c.leak, c.leakContainer, null)
        RecapTone.GOOD -> Triple(c.good, c.good.copy(alpha = 0.16f), null)
        RecapTone.NEUTRAL -> Triple(c.textSecondary, Color.Transparent, outline)
        RecapTone.MUTED -> Triple(c.textMuted, Color.Transparent, outline)
    }
    Text(
        b.text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .background(bg, CircleShape)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
```

### 5.4 Wording elsewhere

- The History list's day chip: "+18m" → "Ran over 18m".
- Wherever the UI calls a consumed block "skipped", it now says "missed". The "Evenings skipped" stat keeps its name.
- "What these mean": "Skipped blocks" → **"Missed blocks"**: "Planned blocks that never happened: the day started after them, or an earlier block ran past their end."

### Tests (`DayRecapMapperTest`)

- `recap_readsInPlainWords`: the Monday from Roni's screenshot, as a Mon/Thu record:
  - CV 09:00–09:20, ended by Next, then synthetic Free to 09:30.
  - Coding 09:30–12:17 (planned end 11:30), with a 15-minute pause.
  - `free_pre_gym` CONSUMED (`OVERRUN`).
  - Gym 12:17–14:01.

  Expect:
  - CV: "Done early", note "The last 10m became free time."
  - Coding: "Ran over 47m" and "Paused 15m".
  - The Free row: missed, note "Coding ran over."
  - Gym: note "Started 17m late (Coding ran over)."
  - The chips include "Missed 1 block".
- `recap_oldRecordWithoutBlockIds_readsTheSame`: the same record with every `blockId` null → the same rows.
- `recap_lateStart_saysWhenTheDayStarted`: Sun/Tue/Wed started at 10:40 → the CV row is missed, note "The day started at 10:40."
- `recap_emergency_endsTheList`: an emergency at 13:05 → the last row is "Day stopped (emergency)", with nothing after it and nothing after it counted as missed.

---

## Slice 6: Pomodoro inside Coding blocks, and taller hours

- `HourHeight` goes from 64dp to **80dp**. That's room for a 10-minute break's label and for dragging. If a geometry test hard-codes 64, update it and list it.
- Next to `pomodoroStatus` in `TimelineQueries.kt` (*verified*):

```kotlin
data class PomodoroPhase(val number: Int, val isBreak: Boolean, val startMin: Int, val endMin: Int)

/** 50/10 from `fromMin` until `endMin`; the last phase is cut at the end. `number` counts focus phases from 1. */
fun pomodoroPhases(fromMin: Int, endMin: Int, focus: Int = 50, rest: Int = 10): List<PomodoroPhase> {
    val phases = mutableListOf<PomodoroPhase>()
    var t = fromMin
    var n = 1
    while (t < endMin) {
        val focusEnd = minOf(t + focus, endMin)
        phases += PomodoroPhase(n, isBreak = false, startMin = t, endMin = focusEnd)
        t = focusEnd
        if (t >= endMin) break
        val restEnd = minOf(t + rest, endMin)
        phases += PomodoroPhase(n, isBreak = true, startMin = t, endMin = restEnd)
        t = restEnd
        n++
    }
    return phases
}
```

- `CalendarBlockUi` gets `phases: ImmutableList<PomodoroPhase> = persistentListOf()` (the default keeps the test fakes compiling). For the current Coding block they run from its actual start; for later Coding blocks, from their planned start; past blocks and other types get none. `CalendarMapper` fills it; no clock is involved.
- A pure label function in `CalendarMapper.kt` (durations in `TimeFormatter`'s style, e.g. "32m", "1h 05m"):

```kotlin
/** The label for a phase of the current Coding block, or null when there's nothing to say. */
fun phaseLabel(p: PomodoroPhase, nowMin: Int): String? = when {
    nowMin >= p.endMin -> if (p.isBreak) null else "Focus ${p.number} ✓"
    nowMin >= p.startMin -> if (p.isBreak) "Break · ${duration(p.endMin - nowMin)} left" else "Focus ${p.number} · ${duration(p.endMin - nowMin)} left"
    else -> if (p.isBreak) "Break · ${duration(p.endMin - p.startMin)}" else "Focus ${p.number} · ${duration(p.endMin - p.startMin)}"
}
```

- It's drawn in the block's draw phase, reading the tick only there, so ticks still recompose nothing. Attach it to the block's outermost box, before any padding, so `size` is the block's full height:

```kotlin
// in the block composable (DayCalendar.kt); minuteOfDay is the tick lambda the calendar already passes down
val measurer = rememberTextMeasurer()
val sand = MaterialTheme.colorScheme.secondary
val small = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp)
val c = AppTheme.colors
Modifier.drawWithContent {
    drawContent()
    if (block.phases.isEmpty()) return@drawWithContent
    val pxPerMin = size.height / (block.endMin - block.startMin)
    val now = minuteOfDay()
    block.phases.forEach { p ->
        val top = (p.startMin - block.startMin) * pxPerMin
        val bottom = (p.endMin - block.startMin) * pxPerMin
        if (p.isBreak) {
            drawRect(sand.copy(alpha = 0.16f), Offset(0f, top), Size(size.width, bottom - top))
            drawLine(sand.copy(alpha = 0.45f), Offset(0f, top), Offset(size.width, top), 1.dp.toPx())
            drawLine(sand.copy(alpha = 0.45f), Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        }
        if (!block.isCurrent) return@forEach                      // labels only on the current block (use the model's real flag)
        val text = phaseLabel(p, now) ?: return@forEach
        val color = when { p.isBreak -> sand; now >= p.endMin -> c.textMuted; now >= p.startMin -> c.textPrimary; else -> c.textSecondary }
        val layout = measurer.measure(text, small.copy(color = color))
        if (layout.size.height > bottom - top) return@forEach      // only when it fits
        val x = size.width - 4.dp.toPx() - layout.size.width
        val y = if (p.isBreak) top + (bottom - top - layout.size.height) / 2 else top + 4.dp.toPx()
        drawText(layout, topLeft = Offset(x, y))
    }
}
```

- `HomeRecompositionTest` stays green as it is.

**Tests:**
- `pomodoroPhases_cutTheLastPhaseAtTheEnd`:
  - 09:30–11:30 → Focus 1 09:30–10:20, Break 10:20–10:30, Focus 2 10:30–11:20, Break 11:20–11:30.
  - 14:00–15:30 → Focus 1 14:00–14:50, Break 14:50–15:00, Focus 2 15:00–15:30.
  - From 09:40 to 11:30 → Focus 1 09:40–10:30, Break 10:30–10:40, Focus 2 10:40–11:30.
- `phaseLabel_saysWhatsLeft`: at 10:48 in a 09:30–11:30 block → Focus 1 "Focus 1 ✓", Focus 2 "Focus 2 · 32m left", the last break "Break · 10m". At 11:24 → the last break "Break · 6m left".

---

## Slice 7: Today's plan (the 2.0.0 data model)

Until now, today's blocks came from the template. From now on they come from **today's plan**: a stored list that starts as a copy of the template and can then change.

```kotlin
// core/model/Model.kt
@Serializable
data class TodayPlan(
    val templateId: TemplateId,
    val blocks: List<BlockSpec>,   // sorted by start, ids unique; past blocks stay in it
    val edited: Boolean = false,
)
```

- It's saved under the key `today_plan` in the DAY prefs file by the same `persist()` that writes `day_state`, before any side effect. `DayOrchestrator` exposes it as `todayPlan: StateFlow<TodayPlan?>` and is its only writer.
- **Everything that needs today's blocks reads the plan:** Home, the notification text, the next alarm and `SegmentBook`. The template is read only to create a plan. Find the readers with `grep -rn "TemplateManager\|templateManager" app/src/main`; the picker keeps reading templates for its preview. Where `ScheduleEngine` takes a `Template`, pass it `Template(plan.templateId, <that template's name>, plan.blocks)`.
- `Running.blockIndex` (also the one inside `Paused`) now indexes `todayPlan.blocks`. Whenever the plan changes, point it back at the current block's id; the current block never changes id.
- The evening (Dishes, the choice) stays outside the plan, as now, and `Evening(blockIndex)` keeps its meaning.

| Event | Today's plan |
|---|---|
| `StartDay` | The template's blocks. Missed ones stay in it (Slice 1 records them) |
| Swap template | The old plan's blocks up to now (or, if the day had ended, up to when it ended), with the last one cut there. Then the new template's blocks from now: the one containing now starts now, and earlier ones are left out. A new block whose id is already in the kept part gets the id `<id>@<startMin>` (a swap to the same template), so ids stay unique. After an ended day there's a gap between the end and now; that's fine |
| An edit | Slice 8 |
| Midnight rollover, Settings → Reset today | Cleared |

- `planStartsAt` is retired. **Migration**, on restore, for a started day (not `Ended`, not `Off`) with no `today_plan`:
  1. Build the plan from today's planned-blocks snapshot (wherever 1.5.1 keeps it until the flush; after a swap it already holds the old blocks and the new ones). Without a snapshot, use today's template, leaving out the blocks that end at or before `planStartsAt` if it's set. Make the ids unique as a swap does.
  2. `Running.blockIndex` (also inside `Paused`) pointed at a block in 1.5.1's list: point it at the same block id in the new plan.
  3. `KidTime` and `Evening` carry no template id: take it from wherever 1.5.1 gets Home's title. If there's neither a snapshot nor a template id, leave the plan null; the flush then keeps writing what 1.5.1 wrote.
  4. Save, then delete `planStartsAt`.
- The day record's `plannedBlocks` = `todayPlan.blocks`. Write it wherever the snapshot is written today and again at flush, so the stored day always holds the final plan. At flush, `edited = true` adds `DayFlag.PLAN_EDITED`.
- Home's title comes from `todayPlan.templateId`. After the day ends, it comes from the plan, or from today's record if there's no plan.
- **Delete the record-read retry loop** in `HomeViewModel` (NOTES.md, "Production gap 2"). Instead, `HistoryManager` gets a flow that re-reads after every write, and Home collects it for today. The ended day's recap and title then appear as soon as the flush writes the record:

```kotlin
private val version = MutableStateFlow(0L)            // bump after every committed write
fun observeDay(date: LocalDate): Flow<DayRecord?> = version.map { day(date) }.distinctUntilChanged()
```

**Tests:**
- `todayPlan_startsAsTheTemplate`: after `StartDay`, the plan's block ids equal the template's.
- `todayPlan_swapKeepsThePast_andTakesTheNewTemplateFromNow`: Sun/Tue/Wed started at 09:00, Next at 09:30 and 11:30, swap to Mon/Thu at 12:10. The plan is `sun_tue_wed.cv`, `sun_tue_wed.coding_am`, `sun_tue_wed.free_midday` (11:30–12:10), `mon_thu.gym` (12:10–14:00), `mon_thu.free_post_gym`, `mon_thu.coding_pm`, `mon_thu.free_pm`, `mon_thu.kid_time`, and the day is Running on `mon_thu.gym`.
- `todayPlan_swapToTheSameTemplate_keepsIdsUnique`: Sun/Tue/Wed started at 09:00, Next at 09:30, Emergency at 10:00, swap to Sun/Tue/Wed at 10:30 → the plan holds `sun_tue_wed.coding_am` (09:30–10:00) and `sun_tue_wed.coding_am@630` (10:30–11:30), no id twice, and the day is Running on `sun_tue_wed.coding_am@630`.
- `todayPlan_migratesAnOldSave`: a 1.5.1 save of a swapped day (Running on `mon_thu.gym`, `planStartsAt` 12:10, no `today_plan`; build it the way the existing swap tests do) → after restore: a plan exists, the day is still Running on `mon_thu.gym`, and `planStartsAt` is gone.
- The existing `homeTitle_afterEmergency_isTheTemplateTitle` passes without the retry loop. The existing swap tests pass, rewritten wherever they read `planStartsAt`.

---

## Slice 8: The editing rules (pure) and the orchestrator

### 8.1 `PlanEditor` (new file `core/schedule/PlanEditor.kt`, *verified*)

```kotlin
package com.roni.blocks.core.schedule

import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.isWork

/** A block of today's plan in minutes of the day. `BlockSpec` converts to and from it. */
data class PlanBlock(val id: String, val type: BlockType, val startMin: Int, val endMin: Int) {
    val length: Int get() = endMin - startMin
}

enum class Edge { TOP, BOTTOM }

sealed interface PlanEdit {
    val blockId: String
    data class Resize(override val blockId: String, val edge: Edge, val toMin: Int) : PlanEdit
    data class Retype(override val blockId: String, val type: BlockType) : PlanEdit
}

/** What a block offers in the UI. */
enum class BlockEdit { NONE, END_ONLY, FULL, TYPE_ONLY }

/** The block the day is on. `endEditable` is false once Next ended it early (its leftover is free time). */
data class CurrentBlock(val id: String, val endEditable: Boolean)

/**
 * The rules for reshaping today's plan. Work blocks keep their length when pushed; free time absorbs the change.
 * Kid time (anything that isn't CV, Coding, Gym or Free) never moves, and nothing before the current block changes.
 * Returns the new plan, or null when the edit isn't allowed or changes nothing.
 * Talks to: nothing (pure). HomeViewModel uses it for the live preview and DayOrchestrator for the commit, so both agree.
 */
object PlanEditor {
    const val SNAP = 5
    const val MIN_WORK = 10
    const val DAY_END = 22 * 60 + 30

    fun editMode(plan: List<PlanBlock>, blockId: String, nowMin: Int, current: CurrentBlock?): BlockEdit {
        if (current == null) return BlockEdit.NONE
        val blocks = plan.sortedBy { it.startMin }
        val cur = blocks.indexOfFirst { it.id == current.id }
        val i = blocks.indexOfFirst { it.id == blockId }
        if (cur < 0 || i < cur) return BlockEdit.NONE                              // past blocks are read-only
        val b = blocks[i]
        if (!b.type.isEditable()) return BlockEdit.NONE
        if (i == cur) return if (current.endEditable && b.type.isWork) BlockEdit.END_ONLY else BlockEdit.NONE
        if (b.endMin <= snapUp(nowMin + SNAP)) return BlockEdit.NONE                // over before it could start: it will be missed
        return if (b.type == BlockType.Free) BlockEdit.TYPE_ONLY else BlockEdit.FULL
    }

    fun apply(plan: List<PlanBlock>, edit: PlanEdit, nowMin: Int, current: CurrentBlock?): List<PlanBlock>? {
        val mode = editMode(plan, edit.blockId, nowMin, current)
        val allowed = when (edit) {
            is PlanEdit.Retype -> mode == BlockEdit.FULL || mode == BlockEdit.TYPE_ONLY
            is PlanEdit.Resize -> mode == BlockEdit.FULL || (mode == BlockEdit.END_ONLY && edit.edge == Edge.BOTTOM)
        }
        if (!allowed || current == null) return null
        val blocks = plan.sortedBy { it.startMin }.toMutableList()
        val cur = blocks.indexOfFirst { it.id == current.id }
        val i = blocks.indexOfFirst { it.id == edit.blockId }
        return when (edit) {
            is PlanEdit.Retype -> retype(blocks, i, edit.type)
            is PlanEdit.Resize -> when (edit.edge) {
                Edge.BOTTOM -> moveEnd(blocks, i, snap(edit.toMin), nowMin)
                Edge.TOP -> moveStart(blocks, i, cur, snap(edit.toMin), nowMin)
            }
        }
    }

    private fun moveEnd(blocks: MutableList<PlanBlock>, i: Int, to: Int, nowMin: Int): List<PlanBlock>? {
        val b = blocks[i]
        val end = maxOf(to, b.startMin + MIN_WORK, snapUp(nowMin + SNAP))          // at least 10 min long; never ends in the next 5 min
        if (end == b.endMin) return null
        if (end < b.endMin) {                                                       // shorter: the freed time becomes free time
            blocks[i] = b.copy(endMin = end)
            freeAfter(blocks, i, end, b.endMin)
            return blocks
        }
        val delta = minOf(end - b.endMin, slackAfter(blocks, i))                    // longer: free time absorbs it, work blocks get pushed
        if (delta <= 0) return null
        pushForward(blocks, i, b.endMin + delta)
        return blocks
    }

    private fun moveStart(blocks: MutableList<PlanBlock>, i: Int, cur: Int, to: Int, nowMin: Int): List<PlanBlock>? {
        val b = blocks[i]
        val floor = floorBefore(blocks, i, cur, nowMin)
        val target = maxOf(to, floor)
        if (target > b.startMin) {                                                  // later: the freed time becomes free time
            val start = minOf(target, b.endMin - MIN_WORK)
            if (start <= b.startMin) return null
            blocks[i] = b.copy(startMin = start)
            freeBefore(blocks, i, b.startMin, start)
            return blocks
        }
        val delta = minOf(b.startMin - target, slackBefore(blocks, i, cur, floor))  // earlier: the free time before it shrinks, work blocks get pushed
        if (delta <= 0) return null
        pushBackward(blocks, i, cur, b.startMin - delta)
        return blocks
    }

    /** The earliest minute block i may start: not in the next 5 minutes, not before kid time, and not before the end of the
     *  current block, unless the current block is free time (free time can shrink). */
    private fun floorBefore(blocks: List<PlanBlock>, i: Int, cur: Int, nowMin: Int): Int {
        var floor = snapUp(nowMin + SNAP)
        for (j in i - 1 downTo cur) {
            val bj = blocks[j]
            if (j == cur && bj.type == BlockType.Free) break
            if (j == cur || !bj.type.isEditable()) { floor = maxOf(floor, bj.endMin); break }
        }
        return floor
    }

    /** Free minutes (free blocks and gaps) between block i's end and the next block that can't move (or 22:30). */
    private fun slackAfter(blocks: List<PlanBlock>, i: Int): Int {
        val anchor = blocks.drop(i + 1).firstOrNull { !it.type.isEditable() }?.startMin ?: DAY_END
        var slack = 0
        var cursor = blocks[i].endMin
        for (j in i + 1 until blocks.size) {
            val bj = blocks[j]
            if (bj.startMin >= anchor) break
            slack += maxOf(0, bj.startMin - cursor)
            if (bj.type == BlockType.Free) slack += bj.length
            cursor = bj.endMin
        }
        return slack + maxOf(0, anchor - cursor)
    }

    /** Free minutes (free blocks and gaps) between `floor` and block i's start. */
    private fun slackBefore(blocks: List<PlanBlock>, i: Int, cur: Int, floor: Int): Int {
        var slack = 0
        var cursor = blocks[i].startMin
        for (j in i - 1 downTo cur) {
            val bj = blocks[j]
            if (bj.endMin <= floor) break
            slack += maxOf(0, cursor - bj.endMin)
            if (bj.type == BlockType.Free) slack += bj.endMin - maxOf(bj.startMin, floor)
            cursor = bj.startMin
        }
        return slack + maxOf(0, cursor - floor)
    }

    private fun pushForward(blocks: MutableList<PlanBlock>, i: Int, newEnd: Int) {
        blocks[i] = blocks[i].copy(endMin = newEnd)
        var cursor = newEnd
        var j = i + 1
        while (j < blocks.size && cursor > blocks[j].startMin) {
            val bj = blocks[j]
            if (bj.type == BlockType.Free) {
                if (cursor < bj.endMin) { blocks[j] = bj.copy(startMin = cursor); return }  // partly absorbed: the push ends here
                blocks.removeAt(j)                                                          // fully absorbed
                continue
            }
            val shift = cursor - bj.startMin                                                // a work block moves later, same length
            blocks[j] = bj.copy(startMin = bj.startMin + shift, endMin = bj.endMin + shift)
            cursor = blocks[j].endMin
            j++
        }
    }

    private fun pushBackward(blocks: MutableList<PlanBlock>, i: Int, cur: Int, newStart: Int) {
        blocks[i] = blocks[i].copy(startMin = newStart)
        var cursor = newStart
        var j = i - 1
        while (j >= cur && cursor < blocks[j].endMin) {
            val bj = blocks[j]
            if (bj.type == BlockType.Free) {
                if (cursor > bj.startMin) { blocks[j] = bj.copy(endMin = cursor); return }  // partly absorbed: the push ends here
                blocks.removeAt(j)                                                          // fully absorbed
                j--
                continue
            }
            val shift = bj.endMin - cursor                                                  // a work block moves earlier, same length
            blocks[j] = bj.copy(startMin = bj.startMin - shift, endMin = bj.endMin - shift)
            cursor = blocks[j].startMin
            j--
        }
    }

    private fun freeAfter(blocks: MutableList<PlanBlock>, i: Int, from: Int, to: Int) {
        val next = blocks.getOrNull(i + 1)
        if (next != null && next.type == BlockType.Free && next.startMin == to) blocks[i + 1] = next.copy(startMin = from)
        else blocks.add(i + 1, PlanBlock(newId(blocks), BlockType.Free, from, to))
    }

    private fun freeBefore(blocks: MutableList<PlanBlock>, i: Int, from: Int, to: Int) {
        val prev = blocks.getOrNull(i - 1)
        if (prev != null && prev.type == BlockType.Free && prev.endMin == from) blocks[i - 1] = prev.copy(endMin = to)
        else blocks.add(i, PlanBlock(newId(blocks), BlockType.Free, from, to))
    }

    private fun retype(blocks: MutableList<PlanBlock>, i: Int, type: BlockType): List<PlanBlock>? {
        val b = blocks[i]
        if (type == b.type || !type.isEditable()) return null
        if (type != BlockType.Free && b.length < MIN_WORK) return null
        blocks[i] = b.copy(type = type)
        if (type != BlockType.Free) return blocks
        val next = blocks.getOrNull(i + 1)                                          // free time merges with the free time around it
        if (next != null && next.type == BlockType.Free && next.startMin == blocks[i].endMin) {
            blocks[i] = blocks[i].copy(endMin = next.endMin)
            blocks.removeAt(i + 1)
        }
        val prev = blocks.getOrNull(i - 1)
        if (prev != null && prev.type == BlockType.Free && prev.endMin == blocks[i].startMin) {
            blocks[i - 1] = prev.copy(endMin = blocks[i].endMin)
            blocks.removeAt(i)
        }
        return blocks
    }

    /** Ids for new free blocks: "edit-1", "edit-2", … The same plan always gets the same id, so preview and commit agree. */
    private fun newId(blocks: List<PlanBlock>): String {
        val last = blocks.mapNotNull { if (it.id.startsWith("edit-")) it.id.removePrefix("edit-").toIntOrNull() else null }.maxOrNull() ?: 0
        return "edit-${last + 1}"
    }

    fun snap(m: Int): Int = ((m + SNAP / 2) / SNAP) * SNAP
    private fun snapUp(m: Int): Int = ((m + SNAP - 1) / SNAP) * SNAP
}

/** CV, Coding, Gym and Free can be edited; kid time, Dishes and the evening can't. */
fun BlockType.isEditable(): Boolean = isWork || this == BlockType.Free
```

- Converting between `BlockSpec` and `PlanBlock` goes by minute of day. The Pomodoro follows the type: Coding gets the Pomodoro the templates give Coding; other types get none. Fields a `PlanBlock` doesn't carry (`quiet`) come from the original `BlockSpec` with the same id; new blocks get the defaults.
- `isWork` is the existing one in `Model.kt` (import it if it's an extension).

### 8.2 The orchestrator

```kotlin
/** Applies one edit to today's plan. False when it isn't allowed or changes nothing. */
suspend fun editPlan(edit: PlanEdit): Boolean
```

- It calls `ensureRestored()` first and runs through the same serialized path as `send` (whatever lock or queue it uses), so an alarm can't interleave.
- It works only in `Running` and `Paused`; anywhere else it returns false.
- `current` comes from one function that Home also uses, `currentForEditing(): CurrentBlock?`. It returns the current block's id, with `endEditable = false` while that block's leftover is free time (Next ended it early). `nowMin` comes from the injected clock.
- If `PlanEditor.apply` returns null: log it, change nothing, return false.
- On success, make every change in memory first, then save once, then run the side effects:
  1. The new plan with `edited = true`, and `blockIndex` pointed back at the current block.
  2. The current block's open `PLANNED` segment (not a `PAUSE` segment) gets the current block's end in the new plan as its planned end, so ran over is measured against the edited plan. An edit to a later block can move it too: pulling the next block earlier shortens the current free time.
  3. Save it all with `persist()`, the way a transition does.
  4. Reschedule the alarm.
  5. Re-render the notification through the silent path a restore uses.
- `PLAN_EDITED` is added at flush from `todayPlan.edited` (Slice 7), so nothing else needs saving for it.

### Tests

**`PlanEditorTest`** (pure; build the plans from the real template blocks; `current` has `endEditable = true` unless the test says otherwise):
- `extend_eatsIntoTheFollowingFree`: Sun/Tue/Wed, now 10:00, current `coding_am`. Its end to 12:00 → `coding_am` 09:30–12:00, `free_midday` 12:00–14:00, and nothing else moves.
- `extend_pushesWorkBlocksUntilFreeTimeAbsorbsIt`: Mon/Thu, now 09:40, current `coding_am`. Its end to 12:10 → `free_pre_gym` is gone, `gym` is 12:10–14:10, `free_post_gym` is 14:10–16:20, and `coding_pm` still starts at 16:20.
- `extend_stopsAtKidTime`: Sun/Tue/Wed, now 14:10, current `coding_pm`. Its end to 16:40 → it ends at 16:20, `free_pm` is gone, and kid time still starts at 16:20.
- `extend_endsAnOverrun`: Sun/Tue/Wed, now 14:10, current `coding_am`, which has run over since 11:30. Its end to 14:15 → `free_midday` is gone, `coding_pm` is 14:15–15:45, and `free_pm` is 15:45–16:20.
- `shorten_leavesFreeTime`: Sun/Tue/Wed, now 09:40, current `coding_am`. Its end to 10:30 → `coding_am` 09:30–10:30 and `free_midday` 10:30–14:00, with no new block.
- `currentBlock_onlyItsEndMoves`: Sun/Tue/Wed, current `coding_am`:
  - At 10:00, a TOP resize and a Retype → null.
  - At 10:02, its end to 10:00 → it ends at 10:10.
  - CV current at 09:20 with `endEditable = false`, its end to 09:50 → null.
- `readOnlyBlocks_refuseEdits`, all Sun/Tue/Wed:
  - At 10:00 (current `coding_am`), any edit to `cv` → null.
  - At 14:10 (current `coding_am`, which ran over), `free_midday` has `BlockEdit.NONE` and a Retype → null.
  - At 10:00, a Resize of `free_midday` → null, and its mode is `TYPE_ONLY`.
- `startEarlier_neverIntoThePast`: Sun/Tue/Wed, now 10:00, current `coding_am`. `coding_pm` TOP to 10:00 → `coding_pm` is 11:30–15:30 and `free_midday` is gone.
- `startEarlier_shrinksTheCurrentFreeTime`: Sun/Tue/Wed, now 12:00, current `free_midday`. `coding_pm` TOP to 13:00 → `free_midday` 11:30–13:00, `coding_pm` 13:00–15:30.
- `startLater_givesTheCurrentFreeTimeMore`: Sun/Tue/Wed, now 14:10, current `free_midday`, which ran over. `coding_pm` TOP to 14:30 → `free_midday` ends at 14:30, `coding_pm` 14:30–15:30.
- `startLater_nextToWork_insertsFreeTime`: Sun/Tue/Wed, now 09:10, current `cv`. `coding_am` TOP to 09:45 → a Free block `edit-1` 09:30–09:45, then `coding_am` 09:45–11:30.
- `retype_freeToCoding_keepsItsTimes`: now 10:00, current `coding_am`. `free_midday` → Coding: 11:30–14:00, same id.
- `retype_workToFree_mergesWithFreeNeighbours`: now 09:10, current `cv`. `coding_pm` → Free → one Free block 11:30–16:20 with `free_midday`'s id.
- `resize_snapsToFiveMinutes`: as in the first test, with the end to 12:03 → it ends at 12:05.

**`DayOrchestratorTest`:**
- `editPlan_keepsTheCurrentBlockRunning_andMovesTheAlarm`: Mon/Thu, Running on `mon_thu.gym` at 12:30 (Gym has no Pomodoro, so its end is the next wake-up), end to 14:30 → true. Still Running on `mon_thu.gym`, the next wake-up is 14:30, and the plan is saved with `edited = true`.
- `editPlan_ranOverIsMeasuredAgainstTheEditedEnd`: Mon/Thu, Running on `mon_thu.gym`. At 14:10 (running over since 14:00), end to 14:30; Next at 14:25 → the day's ran-over minutes are 0.
- `editPlan_survivesProcessDeath`: after the first test's edit, a fresh orchestrator over the same storage → the same plan, still Running on `mon_thu.gym`, and the open segment's planned end is 14:30.
- `editPlan_isSilent`: the notification re-render after an edit makes no sound.

---

## Slice 9: Editing on the calendar

### 9.1 What Roni sees

- **Tap a block** to select it; this replaces anything tapping a block does today. Tap it again, tap Done, or press Back to deselect. Blocks whose mode is `NONE` don't react.
- **The selected block** gets a 2dp border in its accent and is drawn above its neighbours (`zIndex`). It shows **handles** for its mode: 18dp accent circles with a 2dp `surface` ring, centered on the edge line, each with a 48dp touch target. The top handle sits 24dp in from the block's left end and the bottom one 24dp in from its right end, so they never overlap, even on a 10-minute block. `FULL` shows both, `END_ONLY` only the bottom one, `TYPE_ONLY` none.
- Handles must not be clipped by the block's rounded shape, and must be above the selected block (a higher `zIndex`) so their touch targets win. The blocks `Layout` pairs its measurables with the block list, so don't just append children: use the multi-content `Layout(contents = listOf({ blocks }, { handles }))` (or `layoutId`), and place the handles on the selected block's animated edges.
- **Dragging a handle** moves that edge in 5-minute steps. The other blocks slide to their new places with a spring (damping 0.85, stiffness 500), and the time labels update as you drag.
- While a block is selected, the **edit card** takes the NowCard's place:

```
┌────────────────────────────────────────────────┐
│ ● Coding                             [ Done ]  │   tag edit_card
│ 09:30 – 12:00 · 2h 30m                         │   live while dragging
│ [ CV ] [ Coding ] [ Gym ] [ Free ]             │   type chips; the selected one filled in its accent
│ Start  (–) 09:30 (+)      End  (–) 12:00 (+)   │   5-minute steps, the same rules as dragging
│ Changes apply to today only.                   │   bodySmall, textMuted
└────────────────────────────────────────────────┘
```

- `END_ONLY` (the current block): no type chips and no Start stepper. Caption: "This block is running. You can move its end; use Next to switch to something else."
- `TYPE_ONLY` (later free time): type chips only. Caption: "Free time fills the gaps. Pick a type to turn it into a block you can resize."
- Tags: `edit_type_<Type>`, `edit_start_minus`, `edit_start_plus`, `edit_end_minus`, `edit_end_plus`, `edit_done`.

### 9.2 How it's wired (no logic in composables)

- `CalendarBlockUi` gets `val edit: BlockEdit = BlockEdit.NONE`, set by `HomeViewModel` with `PlanEditor.editMode(...)` when it publishes. There's no extra ticking: `editPlan` checks again at commit, so a stale mode can only lead to a refused edit.
- `HomeState` gets `draggingId: String?`.
- The selection is UI-local: `rememberSaveable { mutableStateOf<String?>(null) }`. It's cleared when that id disappears from the blocks or its mode becomes `NONE`.
- `HomeAction` additions:
  - `DragEdge(id, edge, toMin)`: preview.
  - `DragDone(id, edge, toMin)`: commit.
  - `DragCancelled`.
  - `StepEdge(id, edge, byMin)`: commit.
  - `Retype(id, type)`: commit.
- **Preview:** `PlanEditor.apply` on today's plan, with `currentForEditing()` and the clock's minute. Publish the result, or the plan itself when the result is null, as the blocks to draw.
- **Commit:** `orchestrator.editPlan(edit)`, then drop the preview and publish from `todayPlan.value`. A refused edit simply snaps back.
- The preview changes only when the snapped minute changes, so a drag recomposes at most once per 5 minutes of movement. Ticks still recompose nothing.

The handle's gesture adds up the drag from where it started:

```kotlin
@Composable
private fun EdgeHandle(blockId: String, edge: Edge, edgeMin: Int, accent: Color, onAction: (HomeAction) -> Unit, modifier: Modifier) {
    val pxPerMin = with(LocalDensity.current) { HourHeight.toPx() / 60f }
    val currentEdge by rememberUpdatedState(edgeMin)
    Box(
        modifier
            .size(48.dp)
            .pointerInput(blockId, edge) {
                var startMin = 0
                var dragPx = 0f
                var lastMin = 0
                detectVerticalDragGestures(
                    onDragStart = { startMin = currentEdge; dragPx = 0f; lastMin = startMin },
                    onDragEnd = { onAction(HomeAction.DragDone(blockId, edge, lastMin)) },
                    onDragCancel = { onAction(HomeAction.DragCancelled) },
                ) { change, dy ->
                    change.consume()
                    dragPx += dy
                    val m = PlanEditor.snap(startMin + (dragPx / pxPerMin).roundToInt())
                    if (m != lastMin) { lastMin = m; onAction(HomeAction.DragEdge(blockId, edge, m)) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(18.dp).background(AppTheme.colors.surface, CircleShape).padding(2.dp).background(accent, CircleShape))
    }
}
```

Blocks move to their new places in the **layout phase**, so a slide recomposes nothing:

```kotlin
// in the calendar's blocks Layout (DayCalendar.kt)
val anim = remember { mutableStateMapOf<String, Pair<Animatable<Float, AnimationVector1D>, Animatable<Float, AnimationVector1D>>>() }
LaunchedEffect(blocks, draggingId) {
    val spec = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)
    val ids = blocks.mapTo(HashSet()) { it.id }
    anim.keys.filter { it !in ids }.forEach { anim.remove(it) }
    blocks.forEach { b ->
        val (s, e) = anim.getOrPut(b.id) { Animatable(b.startMin.toFloat()) to Animatable(b.endMin.toFloat()) }
        if (b.id == draggingId) { s.snapTo(b.startMin.toFloat()); e.snapTo(b.endMin.toFloat()) }
        else { launch { s.animateTo(b.startMin.toFloat(), spec) }; launch { e.animateTo(b.endMin.toFloat(), spec) } }
    }
}
// in the measure block, instead of b.startMin / b.endMin:
// val start = anim[b.id]?.first?.value ?: b.startMin.toFloat();  val end = anim[b.id]?.second?.value ?: b.endMin.toFloat()
```

- Auto-scrolling near the calendar's top or bottom edge while dragging is optional. If it fights the gesture code, skip it and note that in `NOTES.md`; the steppers cover long moves.
- Update `FakeHomeStates` and the recomposition tests' fakes for the new fields (compile-only).

No new tests: Slice 8 pins the rules. If the phone is connected, note what you checked, with screenshots of a selected block and of the edit card.

---

## Slice 10: Demo data, docs and release 2.0.0

1. **Demo data:** two of the generated days become a late start (a Sun/Tue/Wed day started at 10:40) and an edited day (`coding_am` extended to 12:00 at 10:00 through `PlanEditor`), built through the same pure pieces as the other days. The count stays 28 days. `DemoDataGeneratorTest` keeps passing.
2. **`CODEMAP.md`:**
   - Add rows for `PlanEditor.kt` and `DayRecap.kt`.
   - Remove the rows for Time Debt and `ActualDayMapper.kt`.
   - Update the rows whose job changed: orchestrator, `HistoryManager`, Home, History, Weekly, Settings, `Model.kt`, `TimelineQueries.kt`.
3. **`NOTES.md`:** the decisions you made, including any name you had to adapt. Correct the line "`BlockSpec` persists nowhere except templates.json": it's now stored in `today_plan` and `plannedBlocks`, so any new `BlockSpec` field needs a default value.
4. **`MANUAL_TESTS.md`:**
   - Remove "Known conflict that will affect D6". On Android 14+ an ongoing notification can be swiped away, so there's no conflict.
   - In the setup and D12, "Start Coding" → "Next: Coding", "Start Free" → "Next: Free", and "leak state" → "ran-over state".
   - In "Demo data fills the past", drop Time Debt. In "Emergency ends the day", "(Day done card)" → "(Home shows today's recap)".
   - Replace the 1.5.1 list with this one, at the top:

   **Check on the phone (2.0.0).** Use the debug app for steps that move the clock (Settings → Developer → debug clock).

   *Starting the day*
   1. Debug app: Developer → Jump to 09:31 → +1 hour (10:31), then Settings → Reset today and pick Sunday / Tuesday / Wednesday: Home starts on Coding, not on CV.
   2. Swipe the app away from recents and open it again: Home appears within 3 seconds, with today's plan.
   3. Same, but use the notification's Pause, then Next: both work.

   *Editing*
   4. Tap the current Coding block: only its bottom handle shows. Drag it down 30 minutes: the free time after it shrinks, and the notification's countdown moves to the new end.
   5. On a Mon/Thu, drag Gym's bottom edge down to 17:00: the free time after it disappears, the afternoon Coding slides to 17:00–18:00, and kid time stays at 19:00.
   6. Tap a Free block and choose Coding: it becomes Coding with break bands. Drag its edges: the trimmed parts turn into free time.
   7. Change a later Coding block to Free: it merges with the free time around it.
   8. Swipe the app away after editing and open it again: the edits are still there.
   9. Swap the template mid-day: the new template takes over from now.

   *During and after the day*
   10. The current Coding block shows its focus and break parts, with how much is left.
   11. Emergency: Home shows today's recap ending with "Day stopped (emergency)", and the title stays.
   12. After the day ends, tap swap: the dialog, then the picker, then tracking restarts from now.

   *Screens*
   13. History → a day: a list in plain words ("Ran over 47m", "Paused 15m", "Done early", "Missed"), nothing unlabeled, and no gap under the top bar.
   14. The History list's chips say "Ran over 18m", and the week headers line up with the cards.
   15. Weekly: each type shows "Xh of Yh" with a bar, and there's no legend.
   16. The menu has History and Settings; Time Debt is gone. Swiping back from Settings fades.
   17. Settings: the debug clock card is tidy with nothing overlapping. Alert sound opens Android's sound settings. Load demo data shows a spinner, then "Demo data loaded: 28 days".
   18. Debug: Jump to 20:05 → Kid asleep now → Skip dishes: the evening choice sheet appears on Home.
5. **`issues.md`:** keep only what's still open, with evidence.
6. `versionName = "2.0.0"`, `versionCode = 20000`. Add to `CHANGELOG.md`:

```markdown
## 2.0.0
- Edit today: drag a block's top or bottom edge to change its times; the blocks after it move along, free time absorbs the change, and kid time stays put.
- Change a block's type, for example turn free time into Coding. Changes apply to today only.
- Picking a template late starts on the block the clock is in; earlier blocks count as missed.
- Coding blocks show their focus and break parts, with the time left.
- History's day view is now a recap in plain words; Home shows it after the day ends.
- Weekly shows plan vs actual per type as progress bars.
- Time Debt removed; its numbers live on Weekly, Insights and History.
- Developer clock controls redesigned.
- Stored data: today's plan is saved on its own; older saves migrate automatically.
```

7. Final gate: `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin assembleDebug assembleRelease :app:assembleBenchmark :macrobenchmark:assembleBenchmark`.
8. Merge `task7` into `master` and `git tag v2.0.0`. If `origin` is configured, push `master` and the tags, and report the command's actual output.

---

## Final report

1. Each slice: done or not done, with its named tests.
2. Where Weekly's planned time used to come from (Slice 3).
3. Tests added, changed (and why) and deleted.
4. Names you had to adapt from this file's sketches.
5. What you checked on the phone, if it was connected.
6. `versionName` / `versionCode`, the changelog entry, the tag and the push output.
7. The updated `NOTES.md` and `issues.md`.
