# TASK 26: The card and the calendar agree on the end (3.4.3)

**Target version: 3.4.3** (versionCode 30403). Runs after Task-25 (3.4.2) is merged. One agent, `C:/Blocks App`, branch `task-26/v3.4.3`. Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/` or `docs/`.

## What Roni found (14 Oct, 3.4.1)

A work block running over shows the red tail on the calendar, but Home's card still says **"Done early"**, with no "Ran over" chip.

## Cause

Two sources for "when does the running block end":
- `DayTimeline` (the calendar) uses **today's plan** (`TodayPlan` block end);
- `HomeViewModel.publish` computes `leaking` / `effectiveEndMin` from the **open segment's `plannedEnd`**, a copy taken when the block opened.

Some path leaves the copy behind the plan, so the calendar is red while the card thinks there's time left. Alerts (`AlertPlanner`) and the notification also read the copy. Removing the duplication for good belongs to the architecture round (planned with Roni). This task stops the symptom and the drift.

## Rules

1. `git checkout -b task-26/v3.4.3` ; `git add docs` ; `git rm docs/Task-25.md` ; `git commit -m "task-26 prep: docs"`. Then `task-26 slice N: <title>`.
2. Architecture (`docs/architecture.md` §5) is binding. Verified files stay untouched (`DayTimeline.kt` included: it is the source to follow).
3. JVM / Robolectric tests only. Add only the tests named here; list any test you change.

---

## Slice 1: One end for the card

1. **Home follows the timeline.** In `HomeViewModel.publish`, derive the current block's leak from the current `TimelineBlock` (phase NOW):
   - `leaking = overrunFromMin != null`;
   - `ranOverMin = minute - overrunFromMin`;
   - the card's "until HH:MM" and "ended at HH:MM" = the plan block's end.
   
   Delete the `openAny?.plannedEnd` computation for these. `doneEarly` (a synthetic-free segment) keeps its own branch.
2. **Find the drift.** Find every path that leaves the open PLANNED segment's `plannedEnd` different from today's plan end for that block. Candidates:
   - `SegmentBook.next` / `advanceAfter` resolve from the **template** (with `shift`) rather than today's plan when they open the next block;
   - `startNow` / `startEarly`;
   - walk inserts / `shortenWalk`;
   - the sheet's `commitPlan`.

   In each path you find, open or update the segment from **today's plan** block (its id and end). Name every path you changed in the report.
3. **The notification** uses the same end: `NotificationModelMapper`'s running/leak decision reads the plan block's end when today's plan exists.

**Tests:**
- `HomeViewModelTest.runningOver_cardSaysRanOver`: reproduce the drift you found (e.g. a plan edited before the block started, or a late start), clock past the plan end → `nowCard.chip == "Ran over"`, primary button "Next: <next block>", no "Done early" anywhere on the card.
- `DayOrchestratorTest.openSegment_followsThePlan`: through the same path, the open segment's `plannedEnd` equals today's plan block end.

---

## Slice 2: Docs and release 3.4.3

1. `docs/NOTES.md`: the cause and every path fixed, one line each. Add to `docs/issues.md`: "Two copies of a block's planned end (TodayPlan vs Segment.plannedEnd); remove one in the architecture round."
2. `docs/MANUAL_TESTS.md`, "Check on the phone (3.4.3)": let a Coding block run over (debug clock) → the card shows "Ran over" and "Next: …" the minute the red tail starts; also after an edit to that block made before it started.
3. `versionName = "3.4.3"`, `versionCode = 30403`. `CHANGELOG.md`:
   ```markdown
   ## 3.4.3
   - Fixed: a block running over still offered "Done early" on Home.
   ```
4. Final gate, merge into `master`, tag `v3.4.3`, push `master` and the tag. When `keystore.properties` exists, build the release APK to `out/blocks-3.4.3-release.apk`. A report of at most 8 lines (including the paths fixed), then stop.
