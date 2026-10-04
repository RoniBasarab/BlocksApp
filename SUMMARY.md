# Blocks: Project Summary (handoff)

Last updated: Sunday 4 October 2026. Written so an agent with no prior context can take over: the **planning agent** (the next Claude chat working with Roni) or the **working agent** (a coding agent in the repo). Read it fully before anything else.

---

## 1. What this is

**Blocks** is a personal Android app (`com.roni.blocks`), sideloaded, never on Google Play, with exactly one user: Roni. It turns his day into timed blocks (CV sending, coding study, gym, free time) around fixed childcare times, tracks when a block runs over, and stays completely silent in the evening and at night.

Why it exists: Roni is job-hunting and his unstructured days were drifting (evenings lost to doomscrolling). The app gives structure without being rigid: it logs deviations instead of punishing them.

- Code: `C:\Blocks App` on Roni's Windows machine. A git repo; `master` holds released work, tagged `vX.Y.Z`.
- All spec and task files live in the repo root (committed from Task-6 on).
- Timezone Asia/Jerusalem. The work week is Sunday–Thursday; the weekend is Friday–Saturday.
- Released: **1.4.0** (Task-5). In progress: **1.5.0** (Task-6). Planned: **2.0.0**, editing (§10).

## 2. People and roles

| Who | Role |
|---|---|
| **Roni** | Owner and only user. Senior Android/Kotlin developer (~6 years). Uses the app daily, reviews on his phone, carries files and reports between the agents, decides. |
| **Planning agent** (a Claude chat; earlier docs call it "architect") | Talks with Roni, runs the decision questionnaires, writes the specs and `Task-N.md` files, reviews the working agent's reports. Has no access to the repo or the phone. |
| **Working agent** (a coding LLM in the repo; earlier docs call it "executor") | Implements one task file at a time. |

About the working agent:
- **Known failure modes:** calls "first cuts" done; edits many files without compiling; refactors working code it wasn't asked to touch (the Task-4 engine refactor broke the build); writes wrong conclusions and wrong API fixes into its own `issues.md`; delegates to background jobs that time out.
- **What works:** exact strings, near-complete code for the tricky parts, small compile-gated slices, explicit checks, and a final report against the task's list. Task-5 (11 slices) went cleanly this way; it skipped the screenshot work but reported that honestly.

Roni starts fresh sessions for both agents at handoffs, so everything they need has to be in these files.

## 3. How Roni likes to work

- **Decisions via questionnaires:** multiple choice, 5 options per question plus "Other", as interactive forms when the tools allow. The answers go into the spec files.
- **Deliverables are Markdown files** he hands to the working agent, together with the exact prompt to start it (§18).
- **Performance is priority #1:** as few recompositions and skips as possible.
- **No over-engineering:** MVI-lite (§6); no reducers, use cases or clean-architecture layers.
- **Wants honest pushback with reasons**, and a clear recommendation when he asks "what do you think?". He decides fast.
- **Prefers fewer, clearer choices.** He cut the NowCard controls himself after using the app.
- **Wording a stranger would understand:** after a few days away he couldn't remember what "leak total" meant. Every label and number must explain itself.
- **Less testing (4 Oct):** he felt the tasks had become test-heavy. Policy in §15.
- **Every iteration is versioned** (§14); he leaves major/minor/patch to the planning agent.
- **Battery:** no vibration anywhere; sound only in the daytime segment; silence after the day ends.
- He writes fast and casually, sometimes by voice dictation, so expect typos. Interpret the intent.
- He installs with Android Studio's Run button. His daily app must be the **release** variant (`com.roni.blocks`). `debug` installs a separate test copy (`com.roni.blocks.debug`) with demo data and the debug clock. Every installed copy fires the 9:00 prompt, so test copies should be uninstalled after use.
- **Family context that shapes the schedule:** a young child in kindergarten; drop-off before 08:00, pickup at 16:20 on Sun/Tue/Wed; grandparents take the child Mon/Thu until about 19:00; his wife usually runs the bedtime ritual, sometimes with Roni.

## 4. Documents

| File | What it is | Status |
|---|---|---|
| `SUMMARY.md` | This file: the handoff | Current |
| `schedule-spec.md` | **Behavior**: templates, times, time accounting, evening flow, notifications | Source of truth for behavior; describes 1.5.0 |
| `architecture.md` | **Code**: packages, MVI pattern, managers, storage, alarms, notifications, performance | Source of truth for tech; describes 1.5.0 |
| `visuals.md` | **Look**: palette, block colors, shared layouts, wording rules | Shared look; task files hold the exact per-screen specs |
| `Task.md` … `Task-5.md`, `Task-4-recovery.md` | Done tasks | History, and format reference (Task-5 and Task-6 are the best examples) |
| `Task-6.md` | Fixes from Roni's phone review, clearer wording, debug clock controls | **In progress** (target 1.5.0) |
| `NOTES.md` (repo) | The working agent's decision log | Must not contradict the code |
| `issues.md` (repo) | The working agent's open issues, with evidence | Check it for wrong conclusions |
| `MANUAL_TESTS.md` (repo) | Device tests D1–D14 and the per-release phone checklist | For Roni |
| `CHANGELOG.md` (repo) | Version history, newest first | Updated at every bump |
| `CODEMAP.md` (repo) | One line per source file: what it does, what it talks to | Created in Task-6 (§16) |

When a decision changes, update the spec files and this summary, not only the next task.

## 5. Product rules (quick version; details in `schedule-spec.md`)

**Templates** (a bundled JSON file; every block has a stable id such as `sun_tue_wed.coding_am`)

| Template | Blocks |
|---|---|
| Monday / Thursday ("Gym day") | CV 9:00–9:30 · Coding 9:30–11:30 · Free 11:30–12:00 · Gym 12:00–14:00 · Free 14:00–16:20 · Coding 16:20–17:20 · Free 17:20–19:00 · Kid time from 19:00 |
| Sunday / Tuesday / Wednesday ("Focus day") | CV 9:00–9:30 · Coding 9:30–11:30 · Free 11:30–14:00 · Coding 14:00–15:30 · Free 15:30–16:20 · Kid time from 16:20 |
| Friday / Saturday ("Family day") | Kid time 9:00–13:00 · Free 13:00–15:00 · Kid time from 15:00 |
| Off day | No blocks, no notifications; counted in the weekly summary |

Coding uses Pomodoro 50/10, counted from the block's actual start. Phase switches are automatic and chime.

**Day lifecycle**
- 09:00: a prompt to pick a template, repeated every 15 minutes until 10:00. The picker appears **only** while today hasn't started.
- **Swap template** (Task-6): re-plans the rest of today from now with another template; what already happened stays. If the day has ended it asks first; after 22:30 it's refused.
- 20:00: the evening prompt ("Kid asleep" / "End day"). "Kid asleep" starts Dishes (20 min), then a forced choice of Game / Project / Read / Chill until 22:30.
- The day ends at 22:30, on "End day", or on **Emergency**. Ending the day flushes history and time debt, cancels alarms and goes silent.
- If "Kid asleep" is never tapped, the day ends by itself at 22:30, flagged `EVENING_SKIPPED`.

**Controls (NowCard and notification)**
- **Next slot** (any time):
  - A work block (CV, Coding, Gym) finished early ends now (under-time logged), and the rest becomes free time until its planned end. The next block then starts on schedule.
  - Free time ended early (planned, or that leftover): the next block starts now and keeps its planned end.
  - Late, on any block: the next block starts now, and the overrun counts as **ran over** (time debt).
  - If the clock is already past the next block too, you land where the clock is, and the skipped blocks are marked `CONSUMED`.
  - The button names what you get: "Next: Free" on a work block before its end, "Next: Coding" from free time or when late.
- **Pause** (work blocks only): stops counting worked time. No end time moves, so a pause shortens the current block only. It ends by itself at the block's end. Logged as pause time; not debt.
- **Emergency:** a red button with the dialog "The day has been disrupted by an emergency. Stop all schedules for today?". Ends the day, flagged `EMERGENCY_STOP`.
- **Time debt** = ran-over time only, per day. **Gym missed** = consumed, cut by an emergency, or under 30 minutes worked.

**Notifications**
- One notification, updated in place, with a countdown drawn by the system (the app never wakes up to update it).
- The `day` channel plays a sound on transitions; the `evening` channel is silent. No sound while the app is on screen. The sound can be changed in Android's channel settings (Settings → Alert sound, from Task-6).
- Actions: running work block "Pause" + "Next: Free"; free time "Next: X"; ended block "Next: X"; paused "Resume"; evening "Kid asleep" / "End day". Actions never open the app, and stale or double taps are ignored.
- Tapping it resumes the app as it was (nothing happens if the app is already open). It's `ongoing`, but on Android 14+ it can still be swiped away; it comes back at the next transition.

## 6. Architecture (quick version; details in `architecture.md`)

- **Stack:** Kotlin, Jetpack Compose, Material 3, dark only, English only, minSdk 34, Hilt, Navigation 3, SharedPreferences + kotlinx.serialization, kotlinx-datetime, kotlinx.collections.immutable, AlarmManager exact alarms (`USE_EXACT_ALARM`). No Room, DataStore, Gson, WorkManager or foreground service. Single module, packages by feature.
- **Versions in use (from `NOTES.md`):** AGP 9.4 with built-in Kotlin, Kotlin 2.2.10, KSP 2.3.12, kotlinx-datetime 0.8 (`kotlin.time.Clock`/`Instant`, `ExperimentalTime` opt-in), Material3 1.4.0 (no `sheetGesturesEnabled`), core-splashscreen 1.2.0, Roborazzi 1.75.0, material-icons-extended. The compile SDK has removed `Notification.getTitle()`: read `extras[EXTRA_TITLE]` instead. Robolectric still has the old API, so JVM tests won't catch its use.
- **Pattern per screen:** an `@Immutable` State data class, sealed `Action` / `Effect`, a `@HiltViewModel` with `onAction()`, updates only via `_state.update { copy() }`, collected with `collectAsStateWithLifecycle`. Composables only render; logic lives in `@Singleton` managers.
- **Day state machine:** `core/daystate/`. A pure `DayStateMachine` plus the `DayOrchestrator` singleton, shared by the UI, alarms and notification actions. It persists before side effects. `ensureRestored()` (Task-6) is single-flight and always finishes, and every entry point calls it. `SegmentBook` records what actually happened.
- **Schedule engine:** `ScheduleEngine` plus pure helpers in `core/schedule/TimelineQueries.kt`. Don't refactor the engine to suit callers; add pure query functions. After a swap, `planStartsAt` limits today's plan (Task-6).
- **Notifications:** a pure `NotificationModelMapper` builds the model and `NotificationRenderer` posts it. There's exactly one pending exact alarm, always recomputed as "the next wake-up".
- **Performance:** ticks are passed as lambdas and read in the draw phase. The calendar is a `drawWithCache` grid, a custom `Layout` for blocks and draw-phase overlays. `RecompositionCounter` with Robolectric tests proves that ticks don't recompose.
- **Navigation:** the start destination is resolved once, after restore (`NotStarted` → picker, anything else → Home). Bottom bar: Home · Weekly · Insights. Burger menu: History · Time Debt · Settings, as separate entries outside the tab scaffold. Every screen change is the same 250 ms crossfade. Each window inset is applied exactly once (Task-6).
- **Builds:** `release` is Roni's daily app. `debug` (`.debug`) adds the debug clock (`DebugClock`, `DebugTimeController`, `TimeTravelReceiver`) and demo data. `benchmark` (`.benchmark`) is for Macrobenchmark.

## 7. Visual direction

- Dark, calm, low saturation. The page is a vertical gradient `#27282C` → `#1C1B19` with faint static glows (steel blue top-right, sand bottom-left). Cards are `#312F2C` with a top-lit border. Accents are steel blue `#7A9CC6` and sand `#D9C38A`; ran-over and destructive actions use `#D97070`.
- Each block type has its own muted hue. Roboto with tabular figures for times. Corner radii: blocks 10dp, cards 16dp, hero cards 20dp.
- The template picker has a slow "aurora" with a parallax dust layer: the only infinite animation in the app.
- Home is a Google-Calendar-style day view (08:00–23:00) with a floating NowCard: a Pause / Next row and a small red Emergency button.
- **Wording:** user-facing text says "ran over", never "leak". Every metric has a one-line hint, and Weekly, Insights and Time Debt have an ⓘ "What these mean" dialog.
- Roni's verdicts: after Task-4, "overall visually looks clean and nice"; after Task-5, Home "ok-ish" and the other screens needing the fixes now in Task-6.

## 8. How we got here

- **26 Sep:** Roni, between jobs and job-hunting, wanted structure; his evenings were disappearing into doomscrolling. A 25-question schedule questionnaire produced the three templates, the manual start at 9:00, the run-over-until-Next rule, the evening flow and the silence rules. As an Android developer he chose to build a native app rather than use a generic timer.
- **26 Sep:** a 28-question architecture questionnaire settled Hilt, a single module, minSdk 34, SharedPreferences, kotlinx-datetime, Navigation 3, MVI-lite, a Canvas calendar, exact alarms and no foreground service. He picked Gson; the planning agent argued for kotlinx.serialization and he agreed. A 10-question visuals questionnaire set the palette, block colors, corners and type.
- **27 Sep:**
  - **Task-1 (1.0.0):** the first build. It worked but looked raw, with no design system.
  - **Task-2 (1.1.0):** design system, edge-to-edge, splash, icon, segment recording, receiver fixes. The screens were still first cuts.
  - **Task-3 (1.2.0):** the Home calendar (given as near-complete code), the picker's aurora, theme fixes, and the performance harness: stability reports, recomposition tests, a Macrobenchmark module and Roborazzi.
  - **Task-4 (1.3.0):** Roni found two notifications awkward, so it became one notification with a system countdown and a launcher-style tap. Also a NowCard whose buttons explain themselves, screen primitives, and UI tests written by the planning agent. The working agent broke the build (many files changed without compiling, plus an engine refactor); `Task-4-recovery.md` introduced compile-gated slices, and it finished green.
  - **Task-5 (1.4.0):** after using the app, Roni cut the controls to Next slot / Pause / Emergency and kept "finishing a work block early gives free time". Also the cold-start fix (the picker reopened for a started day), real Settings, debug-only demo data, finished data screens, stable block ids, a planned-blocks snapshot per day, and the versioning policy. Merged as `v1.4.0`.
- **4 Oct:** Roni's phone review of 1.4.0 found the bugs in §9 (the worst: stuck on the splash after Android kills the app). He asked to dial testing down, asked whether code comments would help agents, and asked for editing (2.0.0). Task-6 was written, and the project was handed to fresh planning and working sessions.

**Lessons for writing tasks**
- The JVM tests were all green while the app was stuck on the splash. They can't see process death, insets, real data or real gestures. Hence the lighter testing policy (§15), a short phone checklist per release, and the working agent using `adb` on Roni's phone when it's connected.
- The working agent's `issues.md` often contains wrong conclusions (e.g. "ongoing notifications can't be swiped away", or imports that don't exist). Correct them explicitly in the next task.
- Give exact strings and near-complete code for anything layout- or concurrency-heavy. When the working agent designs these itself, the result is a first cut.

## 9. Current state (4 Oct 2026)

**Released, 1.4.0:** `master` = `c2853ef` = tag `v1.4.0`. 170 JVM unit tests green; the full build gate is green (debug, release, benchmark, macrobenchmark, androidTest compile).

**Open from the working agent (`issues.md`):** Roborazzi screenshots weren't re-recorded (no longer required, §15); device tests D1–D14 have never run on hardware (Roni).

**Roni's findings on the phone (4 Oct), all handled in Task-6:**

| # | Finding | Task-6 |
|---|---|---|
| 1 | After picking a template and killing the app, it stays on the splash forever; the notification's Pause and Next do nothing | Slice 1 |
| 2 | After Emergency, Home's top bar says "Pick a template" | Slice 2 |
| 3 | After the day ends, swap template does nothing. He wants a confirm dialog, then tracking restarts from now with the new template | Slice 2 |
| 4 | History and Weekly: a big gap under the top bar; the list uses about 75% of the screen | Slice 3 |
| 5 | History's week headers sit too far left, overlapping their dark band | Slice 4 |
| 6 | A day in History: blocks overlap and get cut off | Slice 4 |
| 7 | Swiping back "destroys" the screen instead of fading like switching tabs | Slice 5 |
| 8 | "Leak total", "Evenings skipped" and "work done" are unclear | Slice 6 |
| 9 | The Insights headline is unclear, and its "8 of 10 days" actually counted blocks | Slice 6 |
| 10 | "Load demo data" gives no feedback | Slice 7 |
| 11 | Can the notification sound be changed? Yes, in Android's channel settings; Settings gets an "Alert sound" row | Slice 7 |
| 12 | There's no way to try the evening without waiting for it | Slice 8 |

Plus his question about comments for agents → `CODEMAP.md` and class headers (Slice 9, §16).

**Waiting on Roni:** the phone checklist in `MANUAL_TESTS.md` after Task-6; device tests (`.\gradlew connectedDebugAndroidTest`) and macrobenchmarks (`.\gradlew :macrobenchmark:connectedBenchmarkAndroidTest`) when convenient. Results: the HTML report under `app\build\reports\androidTests\connected\`, and a `*benchmarkData.json` under `macrobenchmark\build\outputs\connected_android_test_additional_output\`.

**Deviation to remember:** Roni once asked for "minutes left, updated once a minute" in the notification. It uses the system chronometer instead (zero wake-ups; it shows negative time while running over). He hasn't objected.

## 10. Editing (2.0.0) and other future ideas

**What Roni wants** (heads-up 27 Sep, request 4 Oct): more flexibility as time goes on. He wants to adjust a block's times and re-categorize blocks (change their type): "basically enabling editing". He wants the planning agent to talk it through with him, starting with: drag handles at the top and bottom of a block, or tap a block and set its times?

**The previous planning session's analysis** (a starting point, not a decision):

| | Tap a block → edit sheet | Drag handles on the calendar |
|---|---|---|
| Precision | Exact times (pickers or ±5-minute steps) | Snaps to 5 or 15 min; fiddly on a 30-minute block (~32dp tall) |
| Re-categorizing | Same sheet, with a type picker | Still needs a sheet or menu |
| Gestures | No conflicts | Fights the calendar's vertical scroll; needs long-press to enter edit mode, and auto-scroll at the edges |
| Performance rule | Trivial | Per-frame updates must stay in layout/draw |
| Risk for the working agent | Low | High |
| Feel | More taps | Direct; fast for "give me 30 more minutes" |

Its lean: the tap-to-edit sheet first (2.0.0), with both operations behind one command (`EditBlock(id, start, end, type)`), so drag handles can come later (2.1) without new logic. Plus a one-tap "+15 min" on the current block in the NowCard, which covers the most common flexible-timing moment with no editor at all.

**Decisions to settle with Roni (questionnaire):**
1. Edit scope: today only, the template from now on, or ask each time.
2. When a block grows into the next one: the next one shrinks, everything after it shifts later, or it isn't allowed.
3. Can the current block be extended or shortened live? Can past blocks be corrected?
4. Re-categorize into a fixed set of types (CV, Coding, Gym, Free), or custom types with their own names and colors?
5. Add, delete and split blocks? A template editor screen (create, rename, duplicate templates)?
6. Undo after an edit?
7. Drag handles: now, later or never. Haptics on snap would break "no vibration" unless he allows it there.
8. Notifications and alarms follow edits automatically; should an edit chime like a transition?

**Technical shape (for Task-7):**
- Today's plan becomes its own stored list of blocks (`TodayPlan`), seeded from the template at the start or at a swap; the engine reads the plan instead of the template. The stepping stones are already in place: stable block ids and the planned-blocks snapshot (Task-5), and `planStartsAt` (Task-6). With `TodayPlan`, a swap becomes "replace the rest of today's plan".
- Templates move from the bundled asset into storage, seeded from the asset on first run. The asset stays as the factory default (Settings → "Reset templates").
- A stored-data change with a migration → **2.0.0**.
- Stats already read each day's snapshot, so edited days count correctly.

**Other ideas, not agreed (don't build without asking):** export/backup of history (JSON/CSV) · daily score or streaks · a home-screen widget · deeper pattern detection.

## 11. Next steps

1. **Working agent:** execute `Task-6.md` (target 1.5.0) in a fresh session (prompt in §18).
2. **Planning agent (in parallel):** confirm the handoff with Roni, then run the editing questionnaire (§10). When Roni brings Task-6's report, review it (§17). Then write `Task-7.md` (2.0.0).
3. **Roni:** after Task-6, go through the phone checklist in `MANUAL_TESTS.md`; run the device tests and benchmarks when convenient.

## 12. Decisions log (settled; don't reopen without a reason)

| Decision | Why |
|---|---|
| Native APK, sideloaded; Play Store rules ignored | Personal tool |
| SharedPreferences + kotlinx.serialization; no Room, no Gson | Small, document-shaped data; Gson breaks with Kotlin, sealed classes and R8 |
| Exact alarms, one pending at a time; no WorkManager, no foreground service | On-time transitions; minimal battery |
| One notification, system chronometer, `ongoing` kept | Roni's request; zero wake-ups; survives "Clear all" |
| Launcher-style notification tap | "If the app is open, clicking the notification should do nothing" |
| State machine outside ViewModels; `ensureRestored()` at every entry point | Alarms and notification actions need it even when no screen exists |
| Ticks as lambdas, read in the draw phase; Canvas calendar and charts | Performance rule #1 |
| Start destination resolved once, after restore | A started day must never reopen the picker |
| Controls = Next slot / Pause / Emergency (27 Sep) | Roni: fewer, clearer options |
| Work block finished early → the rest becomes free time; free time ended early → the next block starts now | Roni's original rule, reconfirmed 27 Sep |
| Pause shortens the current block and isn't debt | A pause never steals from other blocks |
| Emergency ends the day | Replaced the freeze/resume flow |
| Swap = re-plan the rest of today from now; confirm after the day ended; refused after 22:30 (4 Oct) | Roni's request |
| Demo data debug-only, flagged `demo` | See real layouts without touching the daily app |
| `.debug` / `.benchmark` app ID suffixes | Tests never touch the real day's data |
| Stable block ids and a planned-blocks snapshot (Task-5) | Groundwork for editing |
| Semantic versioning, one bump per task | §14 |
| Lighter testing (4 Oct) | Most real bugs were invisible to JVM tests; §15 |
| `CODEMAP.md` + short class headers; no change history in code comments (4 Oct) | Fast onboarding without comments that go stale; §16 |
| Wording: "ran over", never "leak"; every metric has a hint | A stranger must understand every screen |
| Each window inset applied once; every screen change a 250 ms crossfade | Fixes the gaps; a consistent feel |

## 13. Glossary

- **Block:** a timed slot of one type (CV, Coding, Gym, Free, Kid time, Dishes, Evening choice).
- **Work block:** CV, Coding or Gym. Pause and "finish early → free time" apply to these.
- **Next slot:** the advance action, allowed at any time.
- **Ran over** (`leak` in code): time past a block's planned end before Next was tapped. Shown in red; it's the time debt.
- **Under-time:** time left in a work block when Next was tapped early.
- **Synthetic Free:** the free time made from that leftover. It ends by itself at the block's planned end.
- **Pause time:** paused minutes inside a block. Tracked, but not debt.
- **Consumed / skipped:** a block that never happened because the clock passed its end.
- **Plan followed:** minutes actually worked in CV, Coding and Gym ÷ their planned minutes.
- **Emergency stop:** the day ended early via Emergency; flag `EMERGENCY_STOP`.
- **Swap:** replacing the rest of today's plan with another template, from now. Flag `TEMPLATE_SWAPPED`; `planStartsAt` marks the moment.
- **Interruption (segment):** downtime while the phone was off (a reboot), nothing else.
- **Kid time:** a quiet period. The evening starts on "Kid asleep".
- **Segment:** a record of what actually happened.
- **NowCard:** the floating control card on Home.
- **Demo data:** generated history for reviewing layouts; debug builds only.
- **Debug clock:** a shifted clock in debug builds (Settings → Developer, or `TimeTravelReceiver` over adb).

## 14. Versioning policy

Semantic versioning. The planning agent picks the target version when writing a task and states it at the top; the working agent bumps it in the task's last slice.

| Bump | When | Examples |
|---|---|---|
| **Major** (2.0.0) | A new core concept that changes how the app is used, or a stored-data change that can't be migrated automatically | Editable templates and live slot editing (planned) |
| **Minor** (1.5.0) | A task that adds features or changes behavior, with stored data migrated automatically | Task-5 (1.4.0), Task-6 (1.5.0) |
| **Patch** (1.5.1) | Bug fixes only; no behavior change | A crash, a wrong label, a misaligned card |

- One task = one bump. A big task is still one minor, not several; a fix-only follow-up is a patch.
- `versionName = "MAJOR.MINOR.PATCH"`, `versionCode = major * 10000 + minor * 100 + patch` (1.5.0 → 10500). It must always increase, or Android refuses to install the new release over the old one.
- Every bump adds an entry to `CHANGELOG.md` (repo root, newest first) and a git tag `vX.Y.Z` on `master` after the merge.
- Settings shows `V {versionName}` from `BuildConfig`; tests read `BuildConfig.VERSION_NAME`, never a literal.
- The header line of `schedule-spec.md`, `architecture.md` and `visuals.md` says which app version they describe; update it with each bump.
- History: 1.0.0 was Task-1. Tasks 2–4 were never bumped, so Task-5 recorded them retroactively as 1.1.0, 1.2.0 and 1.3.0 and shipped as 1.4.0. Task-6 ships 1.5.0.

## 15. Testing policy (from Task-6)

Roni felt the tasks had become test-heavy, and those layers missed the bugs that mattered. From Task-6 on:

| Kind | Policy |
|---|---|
| JVM unit tests for logic (state machine, engine, orchestrator, mappers, ViewModels) | **Required** for new rules, plus one regression test per logic bug, failing before the fix. The task names them. |
| The existing suite (170+ tests, including the recomposition tests) | Stays green. The recomposition tests stay: performance is rule #1. |
| UI geometry / expectation tests | Keep the existing ones; no new ones for copy or spacing changes. |
| Roborazzi screenshots | Not required. Only when the planning agent asks for a design review. |
| Device tests D1–D14 | Kept. Roni runs them when notifications, navigation or process behavior change. Not a gate for the working agent. |
| On the phone | Each release adds a short "Check on the phone" list to `MANUAL_TESTS.md`. The working agent uses `adb` with the debug app when the phone is connected. |

## 16. Documentation conventions (from Task-6)

Roni asked whether big comments in every file the agent touches (how it works, a history of changes, what it depends on) would help future agents. What was agreed:
- **`CODEMAP.md`** in the repo root: one line per source file (what it does, what it talks to). A fresh agent reads one file instead of opening dozens. It's updated in the same commit whenever a file is added, removed or repurposed.
- **Short KDoc headers on core classes only:** the responsibility, the 1–3 rules that must stay true, and "Talks to: …".
- **No change history in code comments:** it goes stale and duplicates git. The *why* goes in commit messages, `NOTES.md` and `CHANGELOG.md`.
- Spec and task files are committed in the repo root.

## 17. How to continue (planning agent)

1. Read this file, then the three specs. Tell Roni in a few sentences what you understood, so he can correct anything.
2. When reviewing a working agent's report, compare it with the task's slices and named tests, not with its claims. Read `NOTES.md` and `issues.md` for wrong conclusions, and correct them in the next task.
3. Decisions: questionnaires (5 options plus "Other"). Recommend one option when Roni asks.
4. Writing `Task-N.md`:
   - It overrides earlier docs and states its target version (§14).
   - Compile-gated slices, ordered by what hurts Roni most.
   - Exact strings and tags; near-complete code for anything layout- or concurrency-heavy.
   - Tests per §15: named, logic-level, failing before the fix; plus an explicit list of tests to delete when behavior is removed.
   - A "Check on the phone" list for `MANUAL_TESTS.md`.
   - A final report covering every slice.
5. After each decision or task, update `schedule-spec.md`, `architecture.md`, `visuals.md` (including the header version line) and this file (§8 history, §9 state, §12 decisions).
6. Give Roni the working agent prompt (§18) with the task number and version filled in.

## 18. Prompts

**Working agent** (a fresh session in the repo; replace N and X.Y.Z):

```
You're the working agent for Blocks, an Android app in this repo. A separate planning agent writes the specs and task files; your job is to implement them exactly.

1. Read SUMMARY.md fully, then Task-N.md fully. Open schedule-spec.md, architecture.md, visuals.md, CODEMAP.md and NOTES.md when the task points to them. These files override anything from earlier sessions.
2. Create a branch taskN from master and execute Task-N.md slice by slice, in order, following its rules: small slices, compile and run the unit tests before every commit, never start a slice on a red build.
3. Work in the foreground. Don't delegate anything to background tasks or sub-agents.
4. Add only the tests the task names. Change existing tests only to make them compile or where the task changes the expected text, and list every test you touched in the report.
5. If adb devices lists my phone, use it to reproduce and check your fixes, but only with the debug app (com.roni.blocks.debug). Never install over, clear, force-stop or uninstall com.roni.blocks: that's my real app with my real days.
6. Don't ask me questions. If something is unspecified, pick the simplest option consistent with the specs and log it in NOTES.md.
7. If you can't finish a slice, stop and report exactly which checkboxes remain. Never mark partial work as done.
8. When the final gate passes, merge taskN into master, tag vX.Y.Z, and end with the final report exactly as the task specifies.
```

**Planning agent** (a fresh Claude chat; attach `SUMMARY.md`, `schedule-spec.md`, `architecture.md`, `visuals.md` and the latest task files):

```
You're the planning agent for Blocks, my personal Android time-block app. You're taking over from a previous planning session; everything it knew is in the attached files.

Attached: SUMMARY.md (the handoff; read it fully first), schedule-spec.md, architecture.md and visuals.md (the specs), and the latest task files.

How we work:
- You don't write code. A separate working agent implements task files in my repo, and I bring you its reports.
- Decisions go through multiple-choice questions, 5 options plus "Other", as interactive forms if your tools allow it. When I ask what you think, recommend one option and say why.
- Your deliverables are Markdown files: task files, updated specs and an updated SUMMARY.md.

First, tell me in a few sentences what you understood about the project and where it stands, so I can correct anything. Then continue from SUMMARY.md §11.
```
