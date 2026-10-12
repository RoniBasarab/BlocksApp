# TASK 14: Real text heights, the Off day card, tidy notes (2.6.1)

**Target version: 2.6.1** (versionCode 20601). A small fix release on master; no feature branch slices beyond these three. Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/` or `docs/`.

## What Roni found on 2.6.0 (12 Oct, screenshots)

1. Short blocks still clip their second line: "CV" shows, "09:00–09:30" under it is cut in half (Free 11:30–12:00 too).
2. Template picker: the Off day card shows a dark rectangle, offset, inside its outline.

## Causes

| # | Cause |
|---|---|
| 1 | `CalendarLayout` assumed fixed text heights (two lines = 34 dp) and ignored the 1 dp gap each block is drawn inside. Real text is taller (titleSmall + labelSmall lines, ×the system font scale), so a 30-minute block (40 dp) was given two lines it can't hold. Fixed in `CalendarLayout.kt` (*verified*): it now takes `TextMetrics(oneLineDp, twoLinesDp)` measured on the phone, counts the gap, and gives any block too short for one line a pill instead of clipped or missing text |
| 2 | The Off day card is `AppCard(containerColor = Color.Transparent)`, but `AppCard` still draws `.shadow(...)`. Through a transparent card the shadow shows as an offset dark shape |

## Rules

Branch `task14`. First `git add docs app ; git rm docs/Task-13.md ; git commit -m "task14 prep: verified CalendarLayout"`, then `task14 slice N: <title>`. `docs/AGENT.md` has the shell and Gradle rules. Verified, don't edit: `CalendarLayout.kt` and `CalendarLayoutTest.kt`, plus every file named verified before.

---

## Slice 1: Measure the text, then lay out

- `HomeScreen` (or `DayCalendar`'s caller) measures once per font scale, with `rememberTextMeasurer()` and `LocalDensity`, in the **exact styles `CalendarBlock` draws**:
  - `oneLineDp`: the height of "Ag · 00:00" in the one-line style (`labelLarge`);
  - `twoLinesDp`: the height of "Ag" in `titleSmall` plus "00:00" in the time style (`labelSmall.merge(AppType.numbers)`).

  Convert px to dp. Send them with a `LaunchedEffect(metrics)` as `HomeAction.TextMeasured(TextMetrics)`.
- `HomeViewModel` keeps `textMetrics` (default `TextMetrics()`) and passes it to `CalendarLayout.fit(...)`; a new value re-publishes.
- `CalendarBlock`:
  - the block is placed with the 1 dp gap as now (`Metrics.calendarGap` must equal `CalendarLayout.GAP_DP`);
  - the text `Column` gets only `start`/`end` padding plus `top = textTopDp − block top`, with **no bottom padding** (the layout already reserved it);
  - `TWO_LINES` draws exactly title + time, `ONE_LINE` exactly "Title · time", in the measured styles.
- The behaviour change Roni will see: a 30-minute block shows "CV · 09:00–09:30" on one line, and a block under ~16 min becomes a sliver with a pill.

**Test:** `HomeViewModelTest.biggerFont_shortBlocksUseOneLine`: send `TextMeasured(TextMetrics(22f, 44f))`, then the CV block's fit is `ONE_LINE`.

## Slice 2: The Off day card

`AppCard` gets `elevated: Boolean = true`; with `false` it skips `.shadow(...)`. `TemplateCard` passes `elevated = !card.outlined`. Check every other `AppCard(containerColor = Color.Transparent …)` call (`git grep -n "Color.Transparent" -- "*.kt"`) and pass `elevated = false` there too.

## Slice 3: Tidy, then release 2.6.1

1. `DebugTimeControllerTest.evening_canBeReachedWithoutWaiting`: `controller.setTo("10:00")` before `StartDay`, so it passes at any hour (it failed every final gate after 22:30).
2. Rewrite `docs/NOTES.md` under 15 KB:
   - keep the top "still-live rules";
   - one section per version (2.1.0 … 2.6.1), with one heading each;
   - only what is still true in the code: remove what later tasks reverted (Next skipping free time, the step permission card, the toast);
   - no test-name lists older than 2.5.0.
3. `versionName = "2.6.1"`, `versionCode = 20601`. `CHANGELOG.md`: `## 2.6.1` with "Short blocks never clip their text (it adapts to the phone's font size)" and "Fixed: the Off day card's stray shadow".
4. Final gate (the `cmd /c` form), merge `task14` into `master`, tag `v2.6.1`, push. A report of at most 10 lines, then stop.
