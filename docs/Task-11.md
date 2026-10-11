# TASK 11: Walks at any time (2.4.0)

**Target version: 2.4.0** (versionCode 20400). Paths below are under `app/src/main/java/com/roni/blocks/` unless they start with `app/`, `docs/` or a root file name.

## Slice map

Read lines 1–39 once, then each slice's lines when you start it.

| Slice | What | Lines | Start in | Tests |
|---|---|---|---|---|
| 1 | Standalone walks | 41–66 | core/daystate/DayOrchestrator.kt, core/storage/HistoryManager.kt, core/model/Model.kt, NotificationModel.kt | DayOrchestratorTest |
| 2 | Screens | 67–78 | feature/home/Home.kt, HomeSheets.kt, feature/history/History.kt | - |
| 3 | Docs and release | 79–99 | docs/, CHANGELOG.md, app/build.gradle.kts | final gate |

## What Roni found on 2.3.0 (11 Oct, night)

He wants walks mainly at night, and the Walk button never shows then. Task-10 put it only on a running day block (a planning mistake, not the agent's): at night the day is in kid time, the evening, ended, or not started yet after midnight.

## Decisions

| Topic | Decision |
|---|---|
| When | A walk can start at any time except during a walk or a pause |
| Entry point | A walk icon in `HomeTopBar` (next to the menu), visible in every state except during a walk. The NowCard's Walk button on day blocks stays |
| During a day block (`Running`) | Unchanged from 2.3.0: the walk goes into the plan and the block resumes after it |
| Any other time (kid time, evening, ended, off, not started) | A *standalone* walk: a timer that doesn't touch the day's state, plan or segments. Same start sheet, rings, WalkCard and notification. When it ends, Home shows the normal card again |
| Midnight | A standalone walk survives the day rollover and is logged on the date it started |
| History | Each day lists its standalone walks ("Walk · 23:10–23:40"); day-block walks already show as Walk rows |
| Kid time as a wall | Only for day-block walks. Standalone walks have no wall: 10–60 min |

## Rules

1. Branch `task11` from `master`. First `git add docs ; git rm docs/Task-10.md ; git commit -m "task11 prep: docs"`, then one commit per slice: `task11 slice N: <title>`.
2. `docs/AGENT.md` has the shell, Gradle and flow rules; follow them.
3. Add only the tests named here. List any existing test you update.
4. Verified files stay as they are: `PlanEditor.kt`, `AlertPlanner.kt`, `RecordEditor.kt` and their tests.
5. Real names win: if a name here doesn't exist, use the real one and note it in `docs/NOTES.md`.

---

## Slice 1: Standalone walks in the orchestrator

**Model** (`core/model/Model.kt`): `@Serializable data class WalkSession(val start: Instant, val end: Instant, val date: LocalDate)` and `@Serializable data class WalkLog(val start: Instant, val end: Instant)` (reuse the existing Instant/LocalDate serializers).

**HistoryManager:** `addWalk(date, log)` and `walks(date): List<WalkLog>` under the key `walks_<date>` (its own key, so day records and their aggregates are untouched). `observeWalks(date): Flow<List<WalkLog>>` for History, the same way `observeDay` works.

**DayOrchestrator:**
- `private var standaloneWalk: WalkSession?`, persisted under its own key `standalone_walk` in `PrefsFile.DAY`, written in `persist()`, read on restore. `applyRollover()` must not clear it.
- `val walkSession: StateFlow<WalkSession?>` for Home. While a day-block walk runs it's built from the open Walk segment; otherwise it's `standaloneWalk`.
- `walkPreview(minutes)` and `startWalk(minutes)`:
  - When the state is `Running`, as in 2.3.0.
  - When the state is `NotStarted`, `KidTime`, `Evening`, `Ended` or `Off` and no walk runs: a standalone walk. The preview is now → home at now + minutes (10–60, no wall) with the turn-back time at the middle and no "then". Starting it sets `standaloneWalk = WalkSession(now, now + minutes, today())`, sets `reactedAt`, then `persist()` and `sideEffects()`.
  - `Paused`: null / false.
- `turnBack()`: also works on a standalone walk before half time: `end = now + (now − start)`.
- `endWalk()`: for a standalone walk, log it now and clear it. A day-block walk keeps using Next.
- `sideEffects()`: when `standaloneWalk` is set, the alarm candidates include `AlertPlanner.nextWalk(start, end, now)`, scheduled with `clock = true`.
- `onAlarm()`: when `standaloneWalk` is set, `AlertPlanner.dueWalk(...)` → `walkRinger.ring(...)` (the same texts; "then" is left out). Once `now >= end`, `history.addWalk(session.date, WalkLog(start, end))`, clear it, persist. Do this before the day's own transitions, and don't return early because of it.
- Notification: while a standalone walk runs, the day notification shows the walk model (the same "heading out / heading home" texts and actions as 2.3.0, on the `day` channel) instead of the state's model. Afterwards it goes back to the state's model.

**Tests** (`DayOrchestratorTest`, fake `WalkRinger`):
- `walk_inTheEvening_leavesTheDayAlone`: Sun/Tue/Wed, Kid asleep 20:30, Chill 20:50, clock 21:00, `startWalk(30)` → state still `Evening(1)`, segments unchanged; `onAlarm()` at 21:15:05 → `ring(TURN_BACK)`; at 21:30:05 → `ring(WALK_DONE)`, no walk running, and `history.walks(today)` = one walk 21:00–21:30.
- `walk_afterMidnight_survivesTheRollover`: day ended, clock 23:50, `startWalk(30)`; clock 00:05:05 the next day, `onAlarm()` → `ring(TURN_BACK)`, still walking; 00:20:05 → logged on the earlier date.
- `walk_cantStartDuringAPause`: Paused → `walkPreview(30)` is null.

---

## Slice 2: Screens

- `HomeTopBar`: a walk icon button (`Icons.AutoMirrored.Outlined.DirectionsWalk`, content description "Take a walk"), left of the menu. It's visible whenever `walkPreview(30) != null`, and it opens the same `WalkSheet`.
- `HomeState.walkCard` comes from `day.walkSession`, so the WalkCard shows for a standalone walk too, in place of the NowCard. With no NowCard (an ended day showing its recap), it shows above the recap list.
- WalkCard buttons for a standalone walk: "Turn back now" (before half time) and "End walk" (`endWalk()`).
- The start sheet for a standalone walk: no "Then …" line, and no kid-time cut.
- History day view: a "Walks" section under the rows, one line each: "Walk · HH:MM–HH:MM · N min", from `observeWalks(date)`. Hidden when there are none.

No new tests (UI).

---

## Slice 3: Docs and release 2.4.0

1. `docs/CODEMAP.md`, and `docs/NOTES.md` (one line per decision, under 20 KB).
2. `docs/MANUAL_TESTS.md`, "Check on the phone (2.4.0)":
   1. At night (kid time, evening, or after the day ended): the walk icon is in the top bar; start a 10-minute walk.
   2. With the phone on silent and the screen off, it rings and vibrates at 5 minutes ("Turn back now") and at 10 ("Walk done").
   3. Turn back now at minute 2: the walk ends at minute 4.
   4. History → today: "Walks" lists it.
   5. During a day block, Walk still inserts into the day (2.3.0 behavior).
3. `versionName = "2.4.0"`, `versionCode = 20400`. `CHANGELOG.md`:

```markdown
## 2.4.0
- Walks at any time: the walk icon in the top bar starts one in kid time, the evening, after the day ends, or after midnight. Same rings and walk card; the day itself isn't touched.
- History lists each day's walks.
```

4. Final gate (the `cmd /c` form), merge `task11` into `master`, `git tag v2.4.0`, push `master` and the tag.

## Final report (under 20 lines), then stop.
