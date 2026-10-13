# TASK 24: Fix a walk's time (3.4.1)

**Target version: 3.4.1** (versionCode 30401). Runs **after** Task-23 (3.4.0) is merged and tagged. One agent, `C:/Blocks App`, branch `task-24/v3.4.1`. Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/` or `docs/`.

## What Roni found (13 Oct)

He came home from a walk and forgot End walk: the walk ran on to its planned end and was logged that long.

## Decisions (Roni, 13 Oct)

| Topic | Decision |
|---|---|
| Detection | **None.** The app doesn't guess that you're home |
| At the walk's end | The "Walk done" ring and the walk's end on Home ask **"Got home earlier?"** with **Fix the time** |
| Fix later | **Any walk in History** can be corrected: start and end in 5-minute steps |
| Steps | A walk's steps stay as counted (the counter was read only at its start and end) |
| A walk during a block | Its time in that day's record follows the correction |

## Rules

1. `git checkout -b task-24/v3.4.1` ; `git add docs` ; `git rm docs/Task-23.md` ; `git commit -m "task-24 prep: docs"`. Then `task-24 slice N: <title>`.
2. **Architecture (`docs/architecture.md` §5) is binding**: State / sealed Action / Effect / `@HiltViewModel` with one `onAction`; only Routes know ViewModels; no callback-parameter lists. If something seems to need breaking that, stop and say so in the report.
3. Verified, don't edit: every file named verified before.
4. ViewModel / JVM tests only. Add only the tests named here; list every existing test you change.

---

## Slice 1: Correct a walk

**The rule, pure (`core/walk/WalkCorrection.kt`):**
```kotlin
/** A walk's new times, or why not. Minutes of the walk's own date; [floorMin]/[ceilMin] are what the day allows around it. */
sealed interface WalkFix { data class Ok(val startMin: Int, val endMin: Int) : WalkFix ; data class Refused(val why: String) : WalkFix }
object WalkCorrection {
    fun check(startMin: Int, endMin: Int, floorMin: Int, ceilMin: Int, nowMin: Int?): WalkFix
}
```
- At least 5 minutes long → otherwise "A walk is at least 5 minutes."
- `startMin >= floorMin` → otherwise "Something else ran until `HH:MM`."
- `endMin <= ceilMin` → otherwise "Something else started at `HH:MM`."
- When `nowMin` isn't null (the walk is today), `endMin <= nowMin` → otherwise "That's in the future."

**Bounds:**
- **A standalone walk:** `floorMin` = the end of the walk before it that day (or 00:00), `ceilMin` = the start of the next walk (or 23:59).
- **A day-block walk:** a closed Walk segment starting at the walk's start. `floorMin` = the end of the segment before it, `ceilMin` = the start of the segment after it. Shrinking it leaves a gap before the block it interrupted, which is fine: a gap is untracked time.

**`DayOrchestrator.correctWalk(date: LocalDate, oldStart: Instant, newStartMin: Int, newEndMin: Int): WalkFix`:**
- The walk log entry with `start == oldStart` gets the new start and end. Its steps are unchanged.
- When a closed `Walk` segment of that date starts at `oldStart`, it moves the same way: for today, in the live book (persist, re-render); for a past day, in the stored record (`HistoryManager.updateDay` / the week rebuild, as `correctDay` does). The day's plan isn't touched.
- `HistoryManager.replaceWalk(date, old: WalkLog, new: WalkLog)`.

**Tests** (`WalkCorrectionTest`, plain JVM; `DayOrchestratorTest`):
- `WalkCorrectionTest.rules`: 3 min → refused; a start before the floor → "Something else ran until 21:00."; past `nowMin` → "That's in the future."; a valid fix → Ok.
- `DayOrchestratorTest.standaloneWalk_cameHomeEarly`: a 60-min standalone walk 21:00–22:00, `correctWalk(…, 21:00, 21:35)` → the log reads 21:00–21:35 with the same steps.
- `DayOrchestratorTest.dayBlockWalk_segmentFollows`: a day-block walk logged 10:00–10:30, corrected to 10:00–10:20 → its Walk segment ends at 10:20; the next segment still starts at 10:30.

---

## Slice 2: Where you fix it

**History (`History.kt`):**
- `HistoryDetailUi.walks` becomes `ImmutableList<WalkRowUi>`, with `WalkRowUi(val startEpochSec: Long, val text: String)` (the same text as today).
- Tapping a row sends `HistoryDetailAction.OpenWalk(startEpochSec)`.
- The sheet is the shared `BlockSheet`:
  - title "Walk";
  - no categories;
  - Start, and Length shown as the end ("Length" stays the label; the end time is in the result line);
  - the line: "21:00–21:35 · 35 min", or the refusal in `Leak`;
  - Save sends `correctWalk`.
- Its actions are wrapped as `HistoryDetailAction.Sheet(BlockSheetAction)`, like Home.

**At the walk's end:**
- `WalkRinger`'s "Walk done" notification gets the action **"Fix the time"**, which opens the app on Home.
- Home shows, for 30 minutes after a walk ended (and until it's dismissed), a small card under the calendar:
  - "Walk logged `21:00–22:00`. Got home earlier?";
  - a **Fix the time** button, which opens the same walk sheet on Home: `HomeAction.FixWalk`, then `HomeAction.Sheet(...)` as for blocks;
  - a ✕ (`HomeAction.DismissWalkFix`).
  - Its State field is `walkFix: WalkFixUi?` (start, text).

**Tests:**
- `HistoryViewModelTest.walkRow_opensTheSheet_andSaves`: open a walk, LengthBy(−25), Save → the row reads the new times.
- `HomeViewModelTest.walkJustEnded_offersTheFix`: a standalone walk ended 2 min ago → `walkFix` set; 31 min later → null.

---

## Slice 3: Docs and release 3.4.1

1. `docs/CODEMAP.md` (`WalkCorrection`, `correctWalk`, `replaceWalk`), `docs/NOTES.md` (one line per decision).
2. `docs/MANUAL_TESTS.md`, "Check on the phone (3.4.1)":
   1. Start a 30-min walk; don't end it. At its end: the ring says "Walk done" with "Fix the time"; tapping it opens Home with the walk card; move the end back 10 min → History shows the shorter walk.
   2. History → any day → tap a walk → fix it; a time that overlaps another walk is refused with the reason.
3. `versionName = "3.4.1"`, `versionCode = 30401`. `CHANGELOG.md`:
   ```markdown
   ## 3.4.1
   - Forgot to end a walk? "Fix the time" when it ends, or fix any walk later from History.
   ```
4. Final gate, merge into `master`, tag `v3.4.1`, push `master` and the tag. When `keystore.properties` exists, build the release APK to `out/blocks-3.4.1-release.apk`. A report of at most 8 lines, then stop.
