# TASK 22, Track A: Home and the evening (3.3.0)

You work in `C:/Blocks App 22a` on `task-22a/v3.3.0-home`. Another agent works on the first-run menu and Profiles in `C:/Blocks App 22b` right now. Read `docs/Task-22.md` lines 1–36 first.

**Your files:** everything under `feature/home/`, `feature/templates/BlockSheet.kt` and `feature/templates/TemplateEditorScreen.kt`, `core/daystate/DayOrchestrator.kt` (and `DayState.kt` if needed), `core/notifications/` (`NotificationRenderer.kt`, `NotificationModel.kt`), `feature/history/History.kt`, `feature/insights/Insights.kt`, and their tests. **Not** `navigation/`, `feature/profiles/`, `feature/templatepicker/`, `feature/settings/`, `designsystem/`, `MainActivity.kt`: Track B owns those.

**Verified, delivered:** `core/schedule/DayTimeline.kt` (the real day: read its KDoc), `core/schedule/TodayEditor.kt` (Home's sheet rule), `AlertPlanner.next/due(…, warn = false)`. Don't re-implement any of their rules.

## Slice map

| Slice | What | Start in |
|---|---|---|
| 1 | The real day on the calendar | `feature/home/Home.kt` (`publish`), `HomeUi.kt`, `DayCalendar.kt` |
| 2 | One edit sheet | `feature/templates/BlockSheet.kt`, `feature/home/Home.kt`, `EditCard.kt`, `DayOrchestrator.editPlan` |
| 3 | Dishes runs over | `DayOrchestrator.kt` (Dishes, `onAlarm`, alerts), `NotificationRenderer.kt`, `History.kt`, `Insights.kt` |
| 4 | Done (push, no release) | |

---

## Slice 1: The real day on the calendar

**`HomeViewModel.publish()`:**
- Build the timeline from what's already there: `plan` = the `daySpecs` (with the evening appended by `withEvening`) as `PlanBlock`s; `segments` = today's segments as `SegmentSpan(blockId, kind, startMin, endMin)` in minutes of the day, where an evening segment gets the id `withEvening` gives its block (`EVENING_DISHES_ID`, `EVENING_CHOICE_ID`, `evening.choice@<min>`) and a closed SYNTHETIC_FREE is skipped; `currentId` = the id of the block whose PLANNED segment is open (kid time included), else null; `nowMin` = the minute.
- `val timeline = DayTimeline.build(plan, segments, currentId, minute)`, one per publish (the minute ticker already republishes).
- `CalendarBlockUi` gets: `phase: TimelinePhase`, `overrunFromMin: Int?`, `lateMin: Int`, `pauses: ImmutableList<IntRange>`. Its `startMin`/`endMin`/`timeLabel` come from the timeline. **`edit` still comes from `PlanEditor.editMode` on the plan**: editing rules don't change.
- `subtitle` (where a block shows one): NOW or DONE with `lateMin > 0` → "`N` min late"; DONE with an overrun → "+`N` min"; MISSED → "Missed". Keep the Pomodoro/"Done early" subtitles for the running block; a late start adds " · `N` min late".
- `CalendarLayout.fit` gets the timeline's intervals; a MISSED sliver's pill reads "`<Title>` · missed" (other slivers as now).
- `leakFromMin` and `LeakOverlay`: delete them. The tail replaces them. `ranOverMin` for the status line and the NowCard stays as it is.

**`DayCalendar` / `CalendarBlock`:**
- **Red tail:** from `overrunFromMin` to the block's end, a band filled with diagonal stripes (`Leak` at 35% alpha, 6 dp apart, 45°) over the block's container colour, a 1 dp `Leak` line at `overrunFromMin`, and "+`N` min" (`labelSmall`, `Leak`) bottom-right inside the band when it is at least 16 dp tall.
- **Pauses:** each range as stripes in `textMuted` at 25% alpha; "Paused `N` min" centred inside when at least 16 dp tall.
- **MISSED:** the whole block at 45% alpha, its title struck through.
- The block's text stays where `CalendarLayout` puts it; the tail and stripes are drawn behind the text.
- Growth and shrinking animate with the existing start/end springs (one step per minute).

**Tests** (`HomeViewModelTest`, Sun/Tue/Wed, the fake clock):
- `runningOver_growsAndEatsTheNextBlock`: Free running from 11:30, clock 14:12 → `free_midday` endMin 14:12 with `overrunFromMin` 14:00, `coding_pm` startMin 14:12.
- `afterNext_theOverrunStays`: then Next at 14:20 → `free_midday` 11:30–14:20 with `overrunFromMin` 14:00 (DONE); `coding_pm` timeLabel "14:20–15:30", subtitle contains "20 min late".
- `eatenWhole_isAMissedPill`: Free running, clock 15:40 → `coding_pm` is a sliver, its pill label "Coding · missed".

---

## Slice 2: One edit sheet

**`BlockSheet.kt` becomes generic** (Home and the template editor share it):
```kotlin
@Immutable
data class BlockSheetUi(
    val title: String,                          // the block's name, or "New block"
    val type: BlockType,
    val categories: ImmutableList<BlockType>,   // what the dropdown offers; empty = no dropdown
    val startMin: Int?,                         // null = no Start row
    val lengthMin: Int?,                        // null = no Length row
    val lengthText: String? = null,             // shown instead of the Length row, e.g. "Until 22:30"
    val line: String? = null,                   // the live result line
    val lineIsError: Boolean = false,
    val canSave: Boolean = true,
    val canDelete: Boolean = false,
    val canAddAfter: Boolean = false,
)
sealed interface BlockSheetAction {
    data class Type(val type: BlockType) : BlockSheetAction
    data class StartBy(val minutes: Int) : BlockSheetAction
    data class StartAt(val minute: Int) : BlockSheetAction
    data class LengthBy(val minutes: Int) : BlockSheetAction
    data object AddAfter : BlockSheetAction                  // new
    data object Delete : BlockSheetAction
    data object Save : BlockSheetAction
    data object Dismiss : BlockSheetAction
}
```
`BlockSheet(ui, use24, onAction)` draws only the rows that are set; "Add after" is a text button next to Delete. `TemplateEditorViewModel` maps its own state into this (its behaviour doesn't change; `canAddAfter = false` there).

**Template editor crash (`TemplateEditorScreen.kt`):** a template with no blocks (Off day) must never reach `TemplateEditor.dayEnd`: with no blocks, `rows` is empty, there's no Add block, and the screen says "Off day has no blocks." (Track B also stops opening it.)

**Home:**
- `EditCard.kt` is deleted (list it); `HomeState` gets `sheet: BlockSheetUi?` and `sheetId: String?` (the block being edited, null = new), `HomeAction.Sheet(val action: BlockSheetAction)`; tapping an editable block opens the sheet (`OpenBlock(id)`, which replaces the old select-then-card flow). The drag handles stay on the selected block exactly as now.
- **A block ahead** (`edit == FULL` or `TYPE_ONLY`): `categories` = the work categories of `Categories.current` plus Free; Start and Length set; `canDelete` unless it's Free; `canAddAfter`. Every change recomputes `TodayEditor.place(plan, id, type, start, length, nowMin, currentForEditing)` for the line.
- **The running block** (`END_AND_TYPE` / `END_ONLY`): `categories` = the work categories when `END_AND_TYPE`, else empty; no Start; Length = planned end − its start; `canAddAfter`; no Delete. The line comes from `PlanEditor.apply` (Retype, then `Resize(BOTTOM)`) as today.
- **Add after:** `TodayEditor.addAfter(plan, id, <the first work category>, nowMin, current)`; on Ok the sheet switches to the new block (id from the result, Start/Length set) so its category and length can be changed before Save. Until Save nothing is committed.
- **Lines:**

  | Result | Line |
  |---|---|
  | Ok, others moved | "Moves later: Coding, Gym" (non-free blocks other than this one whose start changed) |
  | Ok, nothing moved | none |
  | `TooSoon(f)` | "Starts at `HH:MM` at the earliest." |
  | `TooShort` | "A block is at least 10 minutes." |
  | `StartsInside(id, end)` | "`<Name>` runs until `HH:MM`. Start at `HH:MM` or later." |
  | `NoRoom(id, at)` | kid time: "No room before kid time at `HH:MM`."; a walk: "No room before your walk at `HH:MM`."; null: "No room before 22:30." |
  | `Locked` | "This block can't be changed now." |

  Errors use `Leak` and set `canSave = false`.
- **Save:** `DayOrchestrator.placeBlock(id: String?, type, startMin, lengthMin): Boolean` (new; null id = the Add-after block: the orchestrator calls `TodayEditor.addAfter`/`place` itself with its own clock and commits) and, for the running block, the existing `editPlan(Retype/Resize)`. Extract `editPlan`'s commit (plan, state index, open segment follow-up, persist, side effects) into one private `commitPlan(edited: List<PlanBlock>)` that both use.
- **Delete:** `editPlan(PlanEdit.Retype(id, BlockType.Free))`.

**Tests:**
- `HomeViewModelTest.addAfter_pushesAndSaves`: 10:00 in `coding_am`; open `coding_pm`, AddAfter, Save → the plan has a 30-min block at 15:30 and `free_pm` 16:00–16:20.
- `HomeViewModelTest.noRoomBeforeKidTime_saveOff`: open `coding_pm`, LengthBy(+60) → line "No room before kid time at 16:20.", `canSave == false`.
- `HomeViewModelTest.runningBlock_hasNoStartAndNoDelete`.
- `HomeViewModelTest.insertThenDrag_movesTheNewBlock`: Add after `coding_am` + Save, then `DragEdge`/`DragDone(BOTTOM)` +15 on the new block → its end moved by 15 (Roni saw a drag on an inserted block do nothing in 3.0; if this fails, find why and fix it).
- Update the tests that used `EditCard`/`Insert` and list them.

---

## Slice 3: Dishes runs over

**`DayOrchestrator`:**
- Remove "Dishes ends by itself at its planned end with a pick waiting" (the `isDishesSlot` block in the transition code that calls `startEvening(pendingChoice!!, end)`).
- `applyEveningChoice`: during Dishes a pick is **always** only remembered (drop the `now < plannedEnd` condition).
- Dishes ends only by **Done dishes** (the `SKIP_DISHES` button, relabelled "Done dishes"; `skipDishes()` keeps its logic) or Next. With a pick, the activity starts then; without, the sheet opens (as now).
- **Alerts:** while Dishes is open, the next alert is `AlertPlanner.next(dishesStart, dishesPlannedEnd, emptyList(), reactedAt, now, warn = false)`: its end, then every 5 minutes until a reaction. Schedule it like day alerts (`sideEffects` candidates) and use `AlertPlanner.due(…, warn = false)` in `onAlarm`. The 2.5.0 "Dishes done · pick your evening" alert becomes this.
- These alerts **vibrate only**: `NotificationRenderer` gets a third channel `DISHES = "dishes"` ("Dishes reminders"), `IMPORTANCE_DEFAULT`, no sound, `enableVibration(true)`, pattern `longArrayOf(0, 400, 200, 400)`. A render with `alert = true` while the model is the Dishes slot goes through it; everything else stays on its channel. (Walks and this are the only vibrations in the app.)

**History (`History.kt`, the day's rows):** a Dishes row that ran over reads "Dishes 20:30–20:57 · 27 min (+7)", the "+7" in `Leak`. Check what the recap already shows for Dishes before adding anything.

**Insights:** a scorecard row "Dishes" with value "`N` of `M` evenings over 20 min" and hint "+`K` min on average", for the subject week; only when `M > 0`.

**Tests:**
- `DayOrchestratorTest.dishes_runsOverUntilDone`: Kid asleep 20:00, pick GAME at 20:05; at 20:20:05 still Dishes and an alert went to the Dishes channel (vibration); at 20:25:05 again; `skipDishes()` at 20:27 → the GAME segment starts 20:27, the Dishes segment is 20:00–20:27.
- `DayOrchestratorTest.dishes_noWarningBeforeItsEnd`: no alert at 20:15.
- `InsightsTest.dishesRow_countsTheEveningsOver20`.

---

## Slice 4: Done (no release)

1. Full suite (the `cmd /c` form); each slice committed as `task-22a slice N: <title>`.
2. `git push origin task-22a/v3.3.0-home`. Don't merge, don't touch `master`.
3. A report of at most 10 lines: commits, tests added/changed/deleted, real-name differences. End with **"Track A done."** Then stop.
