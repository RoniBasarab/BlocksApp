# TASK 23: Hint cards instead of popups; walk and step statistics; steps while driving; a safe upgrade from 2.6.2 (3.4.0)

**Target version: 3.4.0** (versionCode 30400). One agent, `C:/Blocks App`, branch `task-23/v3.4.0`. Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/` or `docs/`.

## Slice map

| Slice | What | Start in |
|---|---|---|
| 1 | Hint cards instead of popups | `designsystem/Explainer.kt`, `feature/profiles/`, `feature/templates/TemplateEditorScreen.kt`, `feature/templates/CategorySection.kt`, `navigation/AppNav.kt` |
| 2 | Walks and steps in Weekly | `feature/weekly/Weekly.kt`, `core/storage/HistoryManager.kt` |
| 3 | Walks and steps in Insights | `feature/insights/Insights.kt` |
| 4 | Steps while driving | new `core/steps/DriveDetector.kt`, `core/alarms/Receivers.kt`, `DayOrchestrator.kt` (step checkpoint), `HistoryManager.kt`, `History.kt`, `Weekly.kt`, `Insights.kt` |
| 5 | A safe upgrade from 2.6.2; back up and restore | `core/storage/TemplateStore.kt`, `core/storage/PrefsStorage.kt`, new `core/storage/Backup.kt`, `feature/settings/Settings.kt` |
| 6 | Docs and release | `docs/`, `CHANGELOG.md`, `app/build.gradle.kts` |

## Decisions (Roni, 13 Oct)

| Topic | Decision |
|---|---|
| Hint cards | 3.3.0's first-visit popups felt like being jumped at. They become a **soft card inside the screen**: icon, title, two or three lines, "Got it". You see the screen first; the card stays until "Got it", then never again. No popups at all |
| Where | Profiles list (PROFILES), a profile's page (TEMPLATES at the top, DAYS above the weekday rows), the template editor (BLOCKS), Categories (CATEGORIES). **None** on the first-run welcome screen (its own text explains). The ? tour stays as it is |
| Weekly | A **walks** tile ("4 walks · 2h 10m", day-block and standalone walks together) and a **steps from walks** tile ("About 6,400 of your steps came from walks") |
| Steps while driving | Android's activity recognition tells when you're in a car. Steps the sensor counted during a drive (bumps it took for steps) are **shown, never subtracted**: History "About 8,200 steps · about 300 while driving", a Weekly tile, an Insights row. The total stays exactly as it is |
| Upgrading the real app | Roni's release app is **2.6.2** with his real days. 3.4.0 must install **over** it (same key, never uninstall) and keep every day, walk and step. A test proves it on 2.6.2-shaped data, and a backup/restore in Settings protects every upgrade after this one |
| Insights | Rows compared with last week: **walks**, **walk time**, **average steps**, each with its change; and **evening walks** (walks that started after kid time) |

## Rules

1. `git checkout -b task-23/v3.4.0` ; `git add docs` ; `git rm docs/Task-22.md docs/Task-22a.md docs/Task-22b.md` ; `git commit -m "task-23 prep: docs"`. Then `task-23 slice N: <title>`.
2. **Architecture (`docs/architecture.md` §5) is binding**: State / sealed Action / Effect / `@HiltViewModel` with one `onAction`; only Routes know ViewModels; no callback-parameter lists. A design-system primitive may take one event lambda, like Material's `onClick` (the existing `AppCard(onClick)` style); screens pass `{ onAction(X.DismissHint) }`. If something seems to need breaking that, stop and say so in the report.
3. Verified, don't edit: every file named verified before (`DayTimeline.kt`, `TodayEditor.kt`, `AlertPlanner.kt`, `Profiles.kt`, `TemplateEditor.kt`, `TemplateRules.kt`, `PlanEditor.kt`, …).
4. Tests: ViewModel / JVM tests only (no Compose UI tests in this task). Add only the tests named here; list every existing test you change or delete.

---

## Slice 1: Hint cards instead of popups

**`designsystem/Explainer.kt`:**
- `HowToCard` gets `val hintTitle: String`: PROFILES "What's a profile?", TEMPLATES "What's a template?", BLOCKS "Building a day", DAYS "Which day suggests what", CATEGORIES "What are categories?". `text` stays (the tour uses `title` and `text` as now).
- New `@Composable fun HintCard(key: ExplainerKey, onGotIt: () -> Unit, modifier: Modifier = Modifier)` (tag `hint_<key lowercase>`):
  - a rounded card in `surfaceRaised` with a 1 dp `outline` border, 16 dp padding;
  - a row: the card's icon in a 40 dp `surface` circle (24 dp icon, `primary` tint), then `hintTitle` in `titleSmall`, `textPrimary`;
  - under it, `text` in `bodyMedium`, `textSecondary`;
  - a `TextButton` "Got it" aligned to the end (tag `hint_ok`).
  - The screen shows it inside `AnimatedVisibility` (fade + shrink), so closing it collapses smoothly.
- Delete `ExplainerDialog`.

**Remove the popups:** delete `feature/profiles/FirstVisit.kt` (`FirstVisitViewModel`, `FirstVisit`) and `FirstVisitViewModelTest`; unwrap every `FirstVisit(...)` in `AppNav.kt`. `ExplainerStore` stays as it is.

**Each place owns its hint** (read `ExplainerStore.seen(key)` once in the ViewModel's `init`; `DismissHint` → `markSeen(key)` and hide):

| Screen | State | Action | Where the card goes |
|---|---|---|---|
| `ProfilesScreen` | `hint: Boolean` (PROFILES) | `ProfilesAction.DismissHint` | first item, above "+ New profile" |
| `ProfileScreen` | `templatesHint: Boolean`, `daysHint: Boolean` | `ProfileAction.DismissHint(val key: ExplainerKey)` | TEMPLATES at the top; DAYS just above the weekday rows |
| `TemplateEditorScreen` | `hint: Boolean` (BLOCKS) | `TemplateEditorAction.DismissHint` | above the block list, for built-ins and your own alike |
| Categories (`CategoryListViewModel` / `CategorySection`) | `hint: Boolean` (CATEGORIES) | `CategoryAction.DismissHint` | above the category rows |

- `ProfileScreen`: delete `explainDays`, `pendingDay` and `GotItDays`. `OpenDay` always opens the day sheet at once; the DAYS card just sits above the rows until "Got it".
- `ProfilesScreen`: delete the old one-line "New here? Tap ? for a quick tour." (`showHint`, the `how_to_seen` read/write): the PROFILES card replaces it. The ? still opens the tour.
- The main menu shows no hint.
- Developer tools' "Reset explainers" keeps working (it resets the five keys).

**Tests:**
- `ProfilesViewModelTest.hint_untilGotIt`: fresh prefs → `hint`; `DismissHint` → not; a new ViewModel → still not. (Replace the old `hint_untilTheTourIsOpened`; list it.)
- `ProfileViewModelTest.daysHint_neverBlocksTheDaySheet`: fresh prefs → `daysHint`; `OpenDay(1)` → `dayPicker == 1` at once, `daysHint` still true; `DismissHint(DAYS)` → false. (Delete `firstDayTap_explainsThenOpens`; list it.)
- `TemplateEditorViewModelTest.blocksHint_once`.

---

## Slice 2: Walks and steps in Weekly

**Data:** the walk log already holds every walk, day-block walks included (`HistoryManager.walks(date)`; 2.6.0 skips duplicates on Home only). Add `suspend fun walksBetween(from: LocalDate, toInclusive: LocalDate): Map<LocalDate, List<WalkLog>>` to `HistoryManager` (reads each date's key).

**`WeeklyViewModel`** (the selected week's 7 dates):
- **Walks** tile, first after Steps: label "Walks", value "`N` walks · `Xh YYm`" ("1 walk · 25m"), hint "Day and evening walks together". With no walks: value "0", hint "No walks this week".
- **Steps from walks** tile, right after it, only when the week has step totals **and** at least one walk with `steps != null`: label "Steps from walks", value "≈ `formatStepsApprox(walkSteps)`", hint "About `formatStepsApprox(walkSteps)` of your steps came from walks".
- Walk minutes: `(end − start)` rounded to whole minutes, summed.

**Tests** (`WeeklyViewModelTest`): `walks_countAndTime` (three walks of 25, 40 and 65 min in the week, one in the week before → "3 walks · 2h 10m"); `stepsFromWalks_onlyWithWalkSteps` (walks without steps → no tile; one with 2,047 steps → "≈ 2,050").

---

## Slice 3: Walks and steps in Insights

**`WeekMetrics`** gets `walks: Int`, `walkMinutes: Int`, `avgSteps: Int` (average over the week's dates that have a step total; 0 when none), `eveningWalks: Int`. `metricsOf` takes the week's walk map and step totals besides the records (`InsightsViewModel` reads them with `walksBetween` / `steps(date)` for the subject and the previous week).

**Evening walk:** a walk whose start is at or after that day's evening start (the first `Dishes` or `EveningChoice` segment's `actualStart` in the day's record), or at or after the end of the day's last segment (the walk started after the day closed, e.g. after 22:30). A day without an evening has no evening walks.

**`scorecardRows`**, after "Pause time", with `row(...)` so each gets its change from last week:

| Label | Value | Hint | Compared on | Good when |
|---|---|---|---|---|
| Walks | "`N`" | "Walks this week" | count | HIGHER |
| Walk time | `formatHm(min)` | "Time spent walking" | minutes | HIGHER |
| Steps | "≈ `formatStepsApprox(avg)` a day" | "Average over days with steps" | avgSteps | HIGHER |
| Evening walks | "`N`" | "Walks after kid time" | count | HIGHER |

**Tests** (`InsightsViewModelTest`): `walkRows_comparedWithLastWeek` (2 walks / 50 min this week, 1 / 20 min the week before → Walks "2" ↑1, Walk time "50m" ↑30m); `eveningWalk_isAfterTheEveningStarts` (a 21:10 walk on a day whose Dishes started 20:30 counts; a 17:00 walk doesn't).

---

## Slice 4: Steps while driving

**What it is:** Google Play Services' Activity Transition API reports when you **enter** and **exit** `IN_VEHICLE`. The app reads the step counter at both moments; the difference is "steps while driving". Nothing is subtracted anywhere: the daily total, Weekly's steps and every existing number stay exactly as they are. This only adds a figure next to them.

**Dependency:** `com.google.android.gms:play-services-location:21.3.0` in `gradle/libs.versions.toml` and the app module. Run the **first** build after adding it **without** `--offline` (once), then go back to the usual command.

**`core/steps/DriveDetector.kt`** (`@Singleton`, injected context and logger):
- `fun register()`: `ActivityRecognition.getClient(context).requestActivityTransitionUpdates(request, pendingIntent)` for `IN_VEHICLE` × `ACTIVITY_TRANSITION_ENTER` / `ACTIVITY_TRANSITION_EXIT`, to a broadcast `PendingIntent` (`FLAG_MUTABLE`, required for this API) on a new `DriveTransitionReceiver`. Only when `ACTIVITY_RECOGNITION` is granted (API 29+); otherwise log and skip. Failures are logged, never thrown.
- Called from the same places that start the day's machinery: app start (after restore), `SystemEventsReceiver` on boot and package replaced, and after the permission result in `MainActivity`. Registering twice is harmless.

**`DriveTransitionReceiver`** (`@AndroidEntryPoint`, `goAsync()` like the other receivers, in `Receivers.kt` and the manifest, not exported): `ActivityTransitionResult.extractResult(intent)`, then for each event in order `orchestrator.onDrive(enter = event.transitionType == ENTER, at = <now>)`.

**`DayOrchestrator.onDrive(enter: Boolean, at: Instant)`**, the step logic, beside `checkpointCounter()`:
- `DAY` prefs key `open_drive`: `@Serializable data class OpenDrive(val start: Instant, val startCounter: Long?)`.
- **Enter:** if a drive is already open, close it first (as below); then `open_drive = OpenDrive(at, checkpointCounter())`.
- **Exit:** with an open drive: `end = at`, `steps = StepBook.between(startCounter, checkpointCounter())` (null when a reading is missing or the phone restarted, same as walks); `history.addDrive(start.date, DriveLog(start, end, steps))`; clear `open_drive`. Exit with nothing open: ignore.
- **Stale:** a drive open for more than 4 hours is closed at `start + 4h` with `steps = null` on the next `onDrive` or step checkpoint (a missed exit).
- A drive is logged on the date it started.

**`HistoryManager`:** `@Serializable data class DriveLog(start: Instant, end: Instant, steps: Long?)`, `addDrive(date, log)`, `drives(date)`, `drivesBetween(from, toInclusive)`, key `drives_<date>`, like walks.

**Where it shows** (all "about", rounded like every step figure, with `formatStepsApprox`):

| Where | When | Text |
|---|---|---|
| History day header | the day's drives have steps > 0 | after "About 8,200 steps": " · about 300 while driving" |
| Weekly tile, after "Steps from walks" | the week has step totals and drive steps > 0 | label "While driving", value "≈ 1,100", hint "Steps counted in the car (`N`% of your steps)" |
| Insights row, after "Steps" | the subject week has drive steps | label "While driving", value "≈ 1,100", hint "Steps counted in the car", compared with last week, good when LOWER |

Add to `ExplainerTerms`: `"While driving" to "Steps the phone counted while Android saw you in a car: mostly bumps taken for steps. They stay in your total; this just shows how many."`

**Debug** (`Settings.kt`, Developer tools): "Simulate a drive" → `onDrive(true, now − 20 min)` with the counter's current value minus 150 as the start counter, then `onDrive(false, now)`, so History shows "about 150 while driving" without a car.

**Tests** (`DayOrchestratorTest`, the fake step sensor):
- `drive_stepsBetweenEnterAndExit`: counter 1,000 at enter 08:00, 1,180 at exit 08:25 → a `DriveLog(08:00, 08:25, 180)` for today; the day's total isn't changed by it.
- `drive_missedExit_closesAfterFourHours`: enter 08:00, next checkpoint 12:30 → logged 08:00–12:00 with `steps == null`, nothing open.
- `drive_rebootInBetween_hasNoSteps`: the counter goes down between enter and exit → `steps == null`.
- `WeeklyViewModelTest.whileDriving_tile` (drives with 300 and 800 steps, week total 20,000 → "≈ 1,100", hint contains "6%").

---

## Slice 5: A safe upgrade from 2.6.2; back up and restore

**1. The welcome screen after an upgrade (bug).** `TemplateStore.load()` marks a profile as chosen only when templates were already stored. 2.6.2 never stored templates, so upgrading from it would open the first-run welcome screen as if the phone were new. An install is "existing" when **any** of these is true: templates were read; `DAY` has a `date`; `HISTORY` has any day record; `DAY` has a step state. Then write `profile_chosen = true`. Only a truly empty app gets the welcome screen.

**2. The upgrade test: `UpgradeFrom262Test`** (Robolectric, real stores, the orchestrator built as in `testActions`):
- **Write the prefs exactly as 2.6.2 left them.** Take the key names and JSON shapes from the tag itself (`git show v2.6.2:<file>` for `PrefsStorage`, `DayOrchestrator`, `HistoryManager`, the models). Write them as raw strings with the 2.6.2 shapes, not with today's serializers:
  - `DAY`: today's date, a `Running` state on `MON_THU` at block 2 with its segments and today's plan, a step state;
  - `HISTORY`: three finished days (one `SUN_TUE_WED` with a pause and an overrun, one `FRI_SAT`, one Off day), two walk logs and step totals;
  - `SETTINGS`: the 24-hour setting. No templates, profiles or categories.
- Then `load` every store, restore the orchestrator, and assert:
  - today continues: still `Running`, `MON_THU`, block 2, the same segments and plan;
  - History returns the three days unchanged (template, segments, flags), the walks and the step totals;
  - Weekly and Insights compute without throwing on those days;
  - `profileChosen()` is true, and the start destination is `HOME` (the day is running);
  - Unemployed is the active profile, with the built-in weekdays (`suggestedFor(MONDAY) == MON_THU`);
  - a second launch (new stores on the same prefs) changes nothing.
- If any of this fails, fix the code, not the test, and say so in the report.

**3. Back up and restore (`Settings.kt`, a "Your data" section, debug and release alike):**
- `core/storage/Backup.kt`:
  - `@Serializable data class BackupFile(val app: String = "blocks", val version: Int, val createdAt: Instant, val files: Map<String, Map<String, String>>)`, where `version` is the app's versionCode and `files` maps `DAY` / `SETTINGS` / `HISTORY` to every key → raw string.
  - `PrefsStorage` gets `suspend fun exportAll(): Map<String, Map<String, String>>` and `suspend fun replaceAll(files)` (clear each file, write every entry, `commit()`).
- **"Back up my data":**
  - a system "Save as" (`ActivityResultContracts.CreateDocument("application/json")`) with the name `blocks-backup-<yyyy-MM-dd>.json`;
  - the Route owns the launcher; the ViewModel builds the JSON on `BackupTo(uri)`;
  - a snackbar "Saved: 412 days, 38 walks".
- **"Restore from a backup":**
  - `OpenDocument`, then a confirm "Replace everything in this app with the backup from 13 Oct 2026? Today's day restarts from it.";
  - then `replaceAll`, and the app restarts itself: a `MainActivity` intent with `NEW_TASK | CLEAR_TASK`, then `Runtime.getRuntime().exit(0)`, so every store loads fresh;
  - a file that isn't a Blocks backup, or comes from a newer version than this app, is refused: "That file isn't a Blocks backup." / "That backup is from a newer version of Blocks."
- Settings' State, Actions (`BackupTo(uri)`, `RestoreFrom(uri)`, `ConfirmRestore`, `DismissRestore`) and Effects (`ShowMessage`, `Restart`) follow the screen's existing pattern.
- A backup from the release app can be restored into Blocks Debug: the way to try a new version on real data.

**Tests:**
- `BackupTest.roundTrip`: fill the three files, `exportAll`, clear them, `replaceAll(export)` → identical.
- `BackupTest.refusesNewerVersions`: `version = versionCode + 1` → refused.
- `TemplateStoreTest.upgradeFrom262_countsAsChosen` (a 2.6.2-shaped DAY date and one history day, no templates → chosen).

---

## Slice 6: Docs and release 3.4.0

1. `docs/CODEMAP.md` (`HintCard`, `walksBetween`, `DriveDetector`, `DriveTransitionReceiver`, `onDrive`, `DriveLog`), `docs/NOTES.md` (one line per decision).
2. `docs/MANUAL_TESTS.md`, "Check on the phone (3.4.0)":
   1. Fresh install: the welcome screen has no card. ≡ → Profiles: a "What's a profile?" card at the top; the screen is usable around it; "Got it" collapses it, and it never comes back.
   2. A profile's page: the templates card on top, the days card above the weekdays; tapping a weekday opens its sheet at once.
   3. The template editor and Categories each show their card once.
   4. Weekly: "Walks: 3 walks · 1h 20m" and, if the walks had steps, "Steps from walks".
   5. Insights (with two weeks of data): Walks, Walk time, Steps and Evening walks with arrows.
   6. Settings → Your data → Back up my data: a JSON file in Downloads; Restore from a backup on Blocks Debug: the app restarts with that data.
   7. Upgrade rehearsal (Roni, see the report): 2.6.2 debug → 3.4.0 debug installed over it → today, History, walks and steps are all there; no welcome screen.
   8. Developer tools → Simulate a drive: History shows "· about 150 while driving"; Weekly has the "While driving" tile. On a real drive (phone in the car): the same after you get out, a few minutes late at most.
3. `versionName = "3.4.0"`, `versionCode = 30400`. `CHANGELOG.md`:
   ```markdown
   ## 3.4.0
   - First-time explanations are now a small card at the top of the screen with "Got it", instead of a popup.
   - Weekly shows your walks (how many, how long) and how many of your steps came from walks.
   - Insights compares walks, walk time and average steps with last week, and counts evening walks.
   - Your data: back up everything to a file, and restore it (also into Blocks Debug, to try a version on real data).
   - Upgrading from 2.x keeps every day, walk and step, and skips the welcome screen.
   - Steps while driving: the phone notices drives and shows how many steps were counted in the car (History, Weekly, Insights). Your total isn't changed.
   ```
4. **Upgrade rehearsal APK for Roni:** `git worktree add "C:/Blocks App v262" v2.6.2`, copy `local.properties` (and `keystore.properties` if it exists) there, build its debug APK there with the usual `cmd /c` command but `:app:assembleDebug`, copy it to `C:/Blocks App/out/blocks-2.6.2-debug.apk` (create `out/`, it's in `.gitignore`: add it if not), then `git worktree remove --force "C:/Blocks App v262"`. Also build this version's debug APK to `out/blocks-3.4.0-debug.apk`, and, only when `keystore.properties` exists, the release APK to `out/blocks-3.4.0-release.apk`.
5. Final gate (the `cmd /c` form), merge into `master`, tag `v3.4.0`, push `master` and the tag. A report of at most 12 lines (slices, tests changed or deleted, the APK paths, and whether `keystore.properties` exists), then stop.
