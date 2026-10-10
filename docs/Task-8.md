# TASK 8: Evenings, corrections and a tidy repo (2.1.0)

**Target version: 2.1.0** (versionCode 20100). This file overrides older docs where they differ. Paths below are under `app/src/main/java/com/roni/blocks/` unless they start with `app/`, `docs/` or a root file name.

## Slice map

Read lines 1–54 once (map, decisions, rules), then each slice's lines when you start it.

| Slice | What | Lines | Start in | Tests |
|---|---|---|---|---|
| 0 | Tidy the repo | 55–73 | git, README.md | - |
| 1 | Fewer tests | 74–83 | build files, app/src/test | - |
| 2 | Evening on the calendar | 84–118 | feature/home/Home.kt | HomeViewModelTest |
| 3 | Weekly arrows | 119–140 | feature/weekly/Weekly.kt | WeeklyViewModelTest |
| 4 | Developer tools dialog | 141–177 | feature/settings/Settings.kt, core/time/DebugTimeController.kt, navigation/AppNav.kt | DebugTimeControllerTest |
| 5 | Add a block, locked taps | 178–199 | feature/home/EditCard.kt, Home.kt, HomeUi.kt | PlanEditorTest (in place) |
| 6 | Correct a past day | 200–253 | core/storage/HistoryManager.kt, feature/home/DayRecap.kt, feature/history/History.kt | RecordEditorTest (in place), CorrectDayTest |
| 7 | Evening check-in | 254–264 | core/model/Model.kt, HistoryManager.kt, DayRecap.kt, History.kt | checkIn_isStoredOnTheDay |
| 8 | Widget | 265–304 | new core/widget/, core/daystate/DayOrchestrator.kt, res/, AndroidManifest.xml | - |
| 9 | Docs and release | 305–344 | docs/, CHANGELOG.md, app/build.gradle.kts | final gate |

## What Roni asked for (10 Oct, after using 2.0.0)

1. The repo root is a mess of scratch files and old task files. Make it neat.
2. Evening: after Kid asleep and a choice, Home showed his Coding block with a timer of -10:33:10. Show the evening (Dishes, then the choice) on the calendar.
3. He can't test daytime at night. Developer tools go in one dialog, with −10/−30/−1h as well as +, and a one-tap "Test a day".
4. Weekly: going back to an empty week drops the arrows, so you can't get back.
5. Blocks "aren't adjustable at all": at night every block is past, so taps do nothing, silently. Say why.
6. New: add a block into free time; correct past blocks (today after it ends, and older days); an evening check-in; a home-screen widget.
7. Tests: delete the screenshot tests and layout-only checks ("all I do is install the app and play around with it"). Keep logic tests and anything that guards behavior or performance.

## Decisions

| Topic | Decision |
|---|---|
| Evening | Kid time ends at the moment Kid asleep was tapped; Dishes and the evening choice are blocks on the calendar; the NowCard counts down to the current one's end |
| Test a day | Developer dialog: pick a template and a start time; the debug clock jumps there, today resets and the day starts |
| Add a block | Only into later free time: a CV, Coding or Gym block takes the first 30 minutes (the whole block if it's shorter); drag to size it |
| Correcting | On the recap (History days, and Home after the day ends): when a block ended, what it really was, and "it happened" for a missed block. The plan never changes; only the record. The day gets the flag `CORRECTED` |
| Locked blocks | A tap on a block that can't be edited shows one short sentence saying why (a Toast) |
| Check-in | When the day has ended, the recap asks "How was today?" Good / OK / Rough, or Skip. One tap, stored on the day |
| Widget | Current block plus a countdown drawn by the system; updated only when the notification is, never on a timer |
| Not in 2.1.0 | Job-search features, export/backup, undo |

## Rules

1. Branch `task8` from `master`. One commit per slice: `task8 slice N: <title>` (plus one `task8 prep` commit in Slice 0).
2. The working-agent prompt has the shell, Gradle and flow rules; follow them.
3. Add only the tests named here. When an existing test checks something this task changes on purpose, update or delete it and list it in the report.
4. Code marked *verified* is already in the repo, compiled and tested: commit it, don't edit it.
5. Real names win: if a name here doesn't exist, use the real one and note it in `docs/NOTES.md`.

---

## Slice 0: Tidy the repo

Roni has put the new docs into `docs/` and the four verified `.kt` files into `app/`. Order matters: commit those first, then clean, or the clean deletes them. Never stash before the prep commit exists.

1. `git add docs app/src/main/java/com/roni/blocks/core/schedule app/src/test/java/com/roni/blocks/core/schedule ; git commit -m "task8 prep: docs and verified editors"`
2. Add `.kotlin/` and `screenshots/` to `.gitignore`.
3. Move the living docs into `docs/` and delete the rest from the root:
   `git mv CODEMAP.md NOTES.md MANUAL_TESTS.md issues.md docs/`
   `git rm SUMMARY.md schedule-spec.md architecture.md visuals.md Task-4.md Task-4-recovery.md Task-5.md Task-6.md Task-6-fixes.md Task-7.md`
   `git rm -r screenshots ; Remove-Item screenshots -Recurse -ErrorAction SilentlyContinue`
   `Remove-Item gate.log, gate.err -ErrorAction SilentlyContinue`
4. Delete the untracked scratch files and folders at the top level only (a43, aff, c5, g7, t8, hist, mapper and so on). Preview first; ignored files (`local.properties`, `keystore.properties`, `*.jks`, `.idea/`, `build/`, `.gradle/`) are never touched:
   `git clean -nd -- ':(glob)*'` then, if the list holds only scratch, `git clean -fd -- ':(glob)*'`
5. Rewrite `README.md` in about ten lines: what Blocks is (one sentence), where the docs are (`docs/SUMMARY.md` first), how to build and install (`./gradlew installDebug`; the release variant is the daily app).
6. The **tracked** files at the root should now be only: `.gitignore`, `README.md`, `CHANGELOG.md`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `compose_stability.conf`, `gradle/`, `app/`, `macrobenchmark/`, `docs/` (plus `.idea/.gitignore` if it's tracked). Ignored local files such as `local.properties`, `keystore.properties` and any `.jks` stay: never delete them. Check with `git ls-files | Select-String -NotMatch '/'`.
7. Commit `task8 slice 0: tidy the repo`.

---

## Slice 1: Fewer tests

- Delete `app/src/test/java/com/roni/blocks/ScreenshotsTest.kt`, `app/src/test/java/com/roni/blocks/expectations/UiExpectationsTest.kt`, `app/src/test/java/com/roni/blocks/ExampleUnitTest.kt`, `app/src/androidTest/java/com/roni/blocks/ExampleInstrumentedTest.kt`, and the test `themeRendersText` in `core/debug/ComposeSmokeTest.kt` (keep `counterCountsHits`).
- Remove Roborazzi: the plugin alias in `build.gradle.kts` (root) and `app/build.gradle.kts`, the `roborazzi { }` block and the two `testImplementation(libs.roborazzi…)` lines in `app/build.gradle.kts`, its four entries in `gradle/libs.versions.toml`, and the `roborazzi.*` lines (and their comment) in `gradle.properties`.
- `expectations/FakeStates.kt`: keep the `now*` builders (`NowCardExpectationsTest` uses them) and delete the `weekly*`, `insights*`, `history*`, `settings*` and `picker*` builders. Leave `FakeHomeStates` alone.
- Keep everything else, including `NowCardExpectationsTest`, `TemplatePickerTest` and `HomeRecompositionTest` (they check behavior and performance).
- Gate: `:app:testDebugUnitTest` green.

---

## Slice 2: The evening on the calendar

**Bug:** `HomeViewModel.currentIndex()` returns `Evening.blockIndex` (0 = Dishes, 1 = the choice) and `publish()` uses it as an index into today's plan, so slot 1 is the plan's second block (Coding 09:30–11:30) and `countdown()` counts from 11:30. Evening blocks are never added to the calendar.

**Fix in `feature/home/Home.kt`:** build the evening blocks from today's segments and append them to the specs before `CalendarMapper.toBlocks`, then find the current evening block by id.

```kotlin
/** After Kid asleep: kid time ends when it was tapped, then Dishes and the evening choice appear as blocks. */
private fun withEvening(specs: List<BlockSpec>, segments: List<Segment>): List<BlockSpec> {
    val evening = segments.filter { it.kind == SegmentKind.PLANNED && (it.type == BlockType.Dishes || it.type is BlockType.EveningChoice) }
    if (evening.isEmpty()) return specs
    val asleepAt = evening.first().actualStart.toLocalDateTime(zone).time
    val day = specs.map { s -> if (s.type == BlockType.KidTime && s.start < asleepAt && s.end > asleepAt) s.copy(end = asleepAt) else s }
    val added = evening.map { seg ->
        BlockSpec(
            id = if (seg.type == BlockType.Dishes) EVENING_DISHES_ID else EVENING_CHOICE_ID,
            type = seg.type,
            start = seg.actualStart.toLocalDateTime(zone).time,
            end = (seg.actualEnd ?: seg.plannedEnd ?: seg.actualStart).toLocalDateTime(zone).time,
        )
    }
    return (day + added).sortedBy { it.start }
}
```

- In `publish()`: `val daySpecs = specs?.let { withEvening(it, segments) }`, map `daySpecs`, and compute the index from it: for `DayState.Evening`, `daySpecs.indexOfFirst { it.id == if (dayState.isDishesSlot) EVENING_DISHES_ID else EVENING_CHOICE_ID }`; Running and Paused keep `blockIndex`; anything else `-1`.
- The evening blocks get `edit = NONE` (they're not plan blocks; `PlanEditor.editMode` already says NONE for an unknown id).
- The NowCard's evening branch already uses `current?.title`; with the right block it shows "Game", and `countdown()` counts to 22:30.
- `HomeViewModel` must also re-publish when `day.segments` changes: the orchestrator sets the state before the segments, so a publish on the state alone can miss the new evening segment.
- The notification is fine: `NotificationModelMapper.evening` already reads the timeline. No change there.

**Test** (`HomeViewModelTest`): `evening_showsTheEveningBlocks_notThePlan`: Sun/Tue/Wed, drive it through `viewModel.onAction` (Kid asleep at 21:12, Game at 21:32), clock 21:40 → `currentBlockId == EVENING_CHOICE_ID`, the blocks contain `evening.dishes` 21:12–21:32 and `evening.choice` 21:32–22:30 (`endMin == 22 * 60 + 30`), and kid time ends at 21:12.

---

## Slice 3: Weekly keeps its arrows

**Bug:** `WeeklyScreen` returns early with only `EmptyState` when `!state.hasData`, so the week selector disappears.

- Render the selector first in every case. `EmptyState` uses `fillMaxSize`, so don't put it in the scrolling `ScreenColumn`:

```kotlin
if (!state.hasData) {
    Column(Modifier.fillMaxSize()) {
        WeekSelector(state, onAction)
        EmptyState(icon = Icons.Outlined.BarChart, title = "No data for this week", body = "Use the arrows to see other weeks.", modifier = Modifier.weight(1f))
    }
    return
}
```

- Add `canGoPrevious` to `WeeklyUiState`: true while the selected week is after the week of the earliest stored day. Disable the previous arrow with it, and make `onPreviousWeek()` stop there too.

**Test** (`WeeklyViewModelTest`): `emptyWeek_keepsBothWaysOut`: seed data in the weeks 1 and 3 weeks ago; go back twice → week −2 has no data, and `canGoNext` and `canGoPrevious` are both true; go back once more → week −3, `canGoPrevious` false.

---

## Slice 4: Developer tools in one dialog

In `feature/settings/Settings.kt` (debug builds only, `BuildConfig.DEBUG`), replace the whole Developer section (`DebugClockCard`, the demo data card) with one row in the same style as the others:

`SettingsRow(icon = Icons.Outlined.Build, title = "Developer tools", trailing = { Chevron() }, onClick = { onAction(SettingsAction.OpenDevTools) })`

It opens `DeveloperToolsDialog` (a `Dialog` holding an `AppCard`, scrollable, max height 85% of the screen). Sections, top to bottom, each with a `labelMedium` title:

| Section | Content |
|---|---|
| Clock | The time (`headlineSmall` + numbers), "Shifted" or "Real time", and a "Real time" button |
| Move time | Two rows of three: "−1 hour" "−30 min" "−10 min" / "+10 min" "+30 min" "+1 hour" → `DebugClockShift(±n)` |
| Jump to | The four presets that exist today |
| Test a day | Template chips (Mon/Thu, Sun/Tue/Wed, Fri/Sat), "Start at 10:00" with (−15) (+15), then a full-width "Start test day" button |
| Evening | "Kid asleep now", "Skip dishes" (enabled as today) |
| Demo data | "Load demo data (4 weeks)", "Clear demo data" |

Reuse `DebugButton` (and so `DebugButtonUi`) for the buttons; delete `DebugClockCard` and `DebugGroupUi`. Fix `formatShift` for negative offsets ("−1h 30m", not "+-1h -30m"). Moving the clock back doesn't rewind what the day already recorded; note that in `docs/NOTES.md`.

- `DebugTimeController` gets:

```kotlin
/** Debug: today restarts on [template] as if it had been picked at [hhmm]. */
suspend fun testDay(template: TemplateId, hhmm: String): Boolean {
    if (!setTo(hhmm)) return false
    orchestrator.resetToday()
    orchestrator.handle(DayEvent.StartDay(template))
    return true
}
```

- `SettingsAction.StartTestDay(template, hhmm)` calls it, closes the dialog, and sends an effect that navigates to Home (`AppNav`: `openTab(HomeKey)`, as the picker does after a start).

**Test** (`DebugTimeControllerTest`): `testDay_startsTheTemplateAtTheChosenTime`: `testDay(SUN_TUE_WED, "10:40")` → Running on `sun_tue_wed.coding_am`, and the clock is within 60 seconds of 10:40 (the offset is whole minutes).

---

## Slice 5: Add a block, and say why a block is locked

`PlanEditor.kt` now has `PlanEdit.Insert(freeBlockId, type)` (*verified*; its tests are in `PlanEditorTest`).

- Edit card, `TYPE_ONLY` (later free time): under the type chips, a row "Add a block:" with chips CV · Coding · Gym → `HomeAction.Insert(id, type)` → `orchestrator.editPlan(PlanEdit.Insert(id, type))`. Caption becomes "Pick a type to turn this free time into a block, or add a 30-minute block at its start."
- After an insert, select the new block so its handles show. The selection lives in `HomeScreen` (`rememberSaveable selectedId`), so pass the id back with a one-shot `HomeState.selectId` the screen consumes: the new block is the one with the inserted type that starts where the free block (or the next 5 minutes) did; its id is `edit-N`, or the free block's own id when the whole block was retyped.
- Locked taps: the block click in `HomeScreen` currently selects only when `edit != NONE`. For `NONE`, send `HomeAction.LockedBlockTapped(id)`; `HomeViewModel` emits a one-shot message (a `SharedFlow<String>`), and `HomeRoute` shows it as a short `Toast`. Messages:

| Case | Message |
|---|---|
| Kid time, Dishes, the evening choice | "Kid time and the evening can't be moved." |
| A block before the current one, or any block once kid time or the evening started on a Sun–Thu day | "This block is over. You can correct what happened in the day's recap once the day ends." |
| Any block before the day starts | "Pick a template first; then you can change the day." |
| A later block while the day is in kid time (Fri/Sat morning) | "Blocks can be changed while a day block is running." |
| The current block after Next ended it early | "You finished this block early; the rest is free time." |
| The current free block | "This free time is running. Change the block after it instead." |
| A later block that ends before it could start | "This block can't start before it ends, so it will count as missed." |

Keep the reason logic in a small pure function next to `currentForEditing()`'s callers (not in a composable).

---

## Slice 6: Correct a past day

`RecordEditor.kt` (*verified*, tests in `RecordEditorTest`) does the rules on rows in minutes. You write the conversion and the UI.

- Add `DayFlag.CORRECTED`.
- New `core/schedule/RecordConversion.kt`:

```kotlin
/** Done rows: closed PLANNED and SYNTHETIC_FREE segments; missed rows: CONSUMED ones. Other segments aren't rows. */
fun DayRecord.toRecordRows(zone: TimeZone): List<RecordRow> = segments.mapIndexedNotNull { i, s ->
    when {
        (s.kind == SegmentKind.PLANNED || s.kind == SegmentKind.SYNTHETIC_FREE) && s.actualEnd != null ->
            RecordRow(i, s.type, done = true, startMin = s.actualStart.minuteOfDay(zone), endMin = s.actualEnd.minuteOfDay(zone))
        s.kind == SegmentKind.CONSUMED && s.plannedStart != null && s.plannedEnd != null ->
            RecordRow(i, s.type, done = false, startMin = s.plannedStart.minuteOfDay(zone), endMin = s.plannedEnd.minuteOfDay(zone))
        else -> null
    }
}.sortedBy { it.startMin }

fun DayRecord.withRows(rows: List<RecordRow>, zone: TimeZone): DayRecord {
    val fixed = segments.toMutableList()
    rows.forEach { r ->
        val s = fixed[r.segmentIndex]
        val kind = when {
            s.kind == SegmentKind.CONSUMED && r.done -> SegmentKind.PLANNED
            s.kind == SegmentKind.SYNTHETIC_FREE && r.type != BlockType.Free -> SegmentKind.PLANNED
            else -> s.kind
        }
        fixed[r.segmentIndex] = s.copy(
            type = r.type,
            kind = kind,
            actualStart = if (r.done) LocalTime(r.startMin / 60, r.startMin % 60).at(date, zone) else s.actualStart,
            actualEnd = if (r.done) LocalTime(r.endMin / 60, r.endMin % 60).at(date, zone) else s.actualEnd,
            missReason = if (kind == SegmentKind.CONSUMED) s.missReason else null,
        )
    }
    return copy(segments = fixed, flags = flags + DayFlag.CORRECTED)
}
```

- `HistoryManager.correctDay(date: LocalDate, edit: RecordEdit, zone: TimeZone): Boolean`: read the day, `RecordEditor.apply(record.toRecordRows(zone), edit)`, write `record.withRows(...)` with `writeDay`, then rebuild that week's aggregate (`writeWeek(aggregateWeek(weekStart(date), days of that week))`). False when the editor returns null.
- `RecapRow` gets `segmentIndex: Int?` (the PLANNED or CONSUMED segment the row is built from; null for the stop, evening-skipped and evening rows) and `fix: RowFix`; `DayRecapMapper` fills both (use `RecordEditor.fixMode` on the record's rows).
- Rows with `fix != NONE` are tappable and open `CorrectBlockSheet` (a `ModalBottomSheet`):
  - `END_AND_TYPE`: title "Correct Coding 09:30–12:17"; "Ended at" with (−5) 12:17 (+5); type chips CV · Coding · Gym · Free ("What did you do?"); a "Done" button. Each tap commits at once.
  - `MARK_DONE`: title "Free 11:30–12:00 was missed"; body "Did it happen after all? The block that ran into it is shortened to make room."; buttons "It happened" and "Cancel".
- Both recap screens refresh by themselves: `HistoryDetailViewModel` switches from `readDay` to `observeDay(date)`; Home's ended recap already observes today's record.
- `DayRecapMapper` currently takes a happened row's type, title and worked minutes from the plan block. Take them from the segment instead (`happened.type`), or a "What did you do?" correction would never show.
- Summary chip "Corrected" (NEUTRAL) when the day has `CORRECTED`.
- If today's record is corrected and the day is restarted with a swap afterwards, the next flush rewrites the record: acceptable; note it in `docs/NOTES.md`.

**Test** (new `app/src/test/java/com/roni/blocks/core/storage/CorrectDayTest.kt`): `correctDay_marksAMissedBlockDone_andFlagsTheDay`: store the Monday record (Coding 09:30–12:17, `free_pre_gym` consumed 11:30–12:00, Gym 12:17–14:01), `correctDay(MarkDone(index of free_pre_gym))` → the record's free segment is PLANNED 11:30–12:00 with no miss reason, Coding ends 11:30, flags contain `CORRECTED`, and the week aggregate was rewritten.

---

## Slice 7: Evening check-in

- `enum class CheckIn { GOOD, OK, ROUGH, SKIPPED }` in `Model.kt`; `DayRecord` gets `val checkIn: CheckIn? = null`.
- `HistoryManager.setCheckIn(date, checkIn)`: read, copy, `writeDay`.
- On a recap whose day isn't an Off day and has `checkIn == null`, a card above the summary (`recap_checkin`): title "How was today?" (History days: "How was this day?"), three equal buttons "Good", "OK", "Rough", and a text button "Skip". One tap stores it and the card disappears.
- After answering, the summary card shows a chip: "Felt good" (GOOD tone), "Felt OK" (NEUTRAL), "Felt rough" (MUTED). Skipped shows nothing. The History list row shows the same word after the template name ("Sunday / Tuesday / Wednesday · Good").

**Test** (`HistoryViewModelTest` or a new small test next to `CorrectDayTest`): `checkIn_isStoredOnTheDay`: `setCheckIn(date, GOOD)` → `readDay(date).checkIn == GOOD`, and the recap's chips contain "Felt good".

---

## Slice 8: Home-screen widget

Classic `AppWidgetProvider` and `RemoteViews` (no new dependency), fed by the same `NotificationModel` the notification uses.

- `core/widget/BlocksWidget.kt`: `@AndroidEntryPoint class BlocksWidget : AppWidgetProvider()`. In `onUpdate`, use `goAsync()`, then `orchestrator.ensureRestored()` and `orchestrator.refreshWidget()`.
- `core/widget/WidgetUpdater.kt` (`@Singleton`, injected `@ApplicationContext` and `SystemTimeMapper`):

```kotlin
fun update(model: NotificationModel?) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, BlocksWidget::class.java))
    if (ids.isEmpty()) return
    val views = RemoteViews(context.packageName, R.layout.widget_now)
    views.setTextViewText(R.id.widget_title, model?.title ?: "Blocks")
    views.setTextViewText(R.id.widget_text, model?.text ?: "No day running")
    views.setInt(R.id.widget_accent, "setBackgroundColor", model?.accentArgb ?: 0xFF46433D.toInt())
    val end = model?.countdownTo
    if (end == null) {
        views.setViewVisibility(R.id.widget_timer, View.GONE)
    } else {
        val base = SystemClock.elapsedRealtime() + (timeMapper.systemMillis(end) - System.currentTimeMillis())
        views.setChronometer(R.id.widget_timer, base, null, true)
        views.setChronometerCountDown(R.id.widget_timer, true)
        views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
    }
    views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context)) // the same launcher-style intent as NotificationRenderer.openApp(); share it
    manager.updateAppWidget(ids, views)
}
```

  (`systemMillis` maps the app clock to the wall clock, which keeps the debug clock's countdown right.)
- `DayOrchestrator`: wherever it calls `notificationRenderer.render(model, …)` (`sideEffects()`, and `flush()` with null), also call `widgetUpdater.update(model)`. Inject it as a small interface (`WidgetPort`, bound in `NotificationModule` like `NotificationPort`) given a no-op default in the constructor, so the six tests that build the orchestrator by hand compile unchanged. Add `suspend fun refreshWidget()` that computes the model the way `sideEffects()` does and calls only the widget.
- `res/layout/widget_now.xml`: a horizontal `LinearLayout` `widget_root` (background `@drawable/widget_bg`: `#312F2C`, 16dp corners; padding 12dp; vertical center). Inside: a 4dp-wide `View` `widget_accent` (match height); a vertical `LinearLayout` (weight 1, 12dp start margin) with `widget_title` (16sp, bold, `#E8E4DC`, one line) and `widget_text` (13sp, `#A8A298`, one line); a `Chronometer` `widget_timer` (20sp, `#E8E4DC`, `android:countDown="true"`).
- `res/xml/widget_info.xml`: `minWidth` 180dp, `minHeight` 40dp, `targetCellWidth` 3, `targetCellHeight` 1, `updatePeriodMillis` 0, `resizeMode` horizontal, `widgetCategory` home_screen, `initialLayout` and `previewLayout` `@layout/widget_now`, description "Current block and time left".
- Manifest: the receiver with the `APPWIDGET_UPDATE` intent filter and the `android.appwidget.provider` meta-data.

No named test (it's Android glue); if the phone is connected, add the widget and check it follows Next.

---

## Slice 9: Docs and release 2.1.0

1. `docs/CODEMAP.md`: rows for `PlanEditor` (Insert), `RecordEditor.kt`, `RecordConversion.kt`, `core/widget/*`, the dialog; remove deleted files.
2. `docs/NOTES.md`: your decisions, briefly. If it's over 20 KB, condense older entries into a short "Earlier tasks" section first; it's read by every future agent.
3. `docs/MANUAL_TESTS.md`: replace the top checklist with "Check on the phone (2.1.0)":
   1. Settings → Developer tools: one row opens the dialog; nothing overlaps.
   2. Test a day: Sun/Tue/Wed at 10:00 → Home starts on Coding.
   3. −1 hour and +1 hour move the clock both ways.
   4. Tap a later Free block → Add a block: Coding → a 30-minute Coding block appears and is selected; drag its bottom handle.
   5. Tap a past block, then kid time: each says why it can't move.
   6. Kid asleep, then pick Game: Dishes and Game appear on the calendar, kid time ends at the tap, the card counts down to 22:30.
   7. End day: the recap asks "How was today?"; pick Good: the chip appears.
   8. In the recap, tap Coding: move its end with −5; tap a missed block: "It happened" shortens the block that ran into it.
   9. History → an older day: the same corrections work.
   10. Weekly: go back past your data, then forward again.
   11. Add the widget: it shows the block and a running countdown; tap Next in the app and the widget follows.
   12. Release app (`com.roni.blocks`): pick a template, swipe the app away, reopen: it comes back.
4. `docs/issues.md`: only what's still open, with evidence.
5. `versionName = "2.1.0"`, `versionCode = 20100`. `CHANGELOG.md`:

```markdown
## 2.1.0
- Fixed: in the evening Home showed a day block with a huge negative timer. The evening (Dishes, then your choice) now shows on the calendar with time left.
- Fixed: Weekly keeps its arrows on an empty week.
- Add a block: turn the start of free time into a 30-minute CV, Coding or Gym block.
- Correct a past day from its recap: when a block ended, what it really was, and missed blocks that happened after all.
- Evening check-in: Good / OK / Rough, shown in History.
- Home-screen widget with the current block and time left.
- Tapping a block that can't move says why.
- Developer tools in one dialog, with "Test a day" and moving the clock back.
- Screenshot and layout-only tests removed; the repo root is tidy and the docs live in docs/.
```

6. Final gate: `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin assembleDebug assembleRelease :app:assembleBenchmark :macrobenchmark:assembleBenchmark` (if `:app:assembleBenchmark` needs Roborazzi anywhere, it was missed in Slice 1).
7. Merge `task8` into `master`, `git tag v2.1.0`, push `master` and the tag.

## Final report (keep it under 30 lines)

Each slice: done or not. Tests added, changed, deleted. Names you adapted. What you checked on the phone. Version, tag, push result.
