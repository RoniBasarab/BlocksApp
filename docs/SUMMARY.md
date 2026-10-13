# Blocks: Project Summary (handoff)

Last updated: Tuesday 13 October 2026 (after 3.0.0). Written so an agent with no prior context can take over: the **planning agent** (the next Claude chat working with Roni) or the **working agent** (a coding agent in the repo). Read it fully before anything else.

---

## 1. What this is

**Blocks** is a personal Android app (`com.roni.blocks`), sideloaded, never on Google Play, with exactly one user: Roni. It turns his day into timed blocks (CV sending, coding study, gym, free time) around fixed childcare times, tracks when a block runs over, and stays completely silent in the evening and at night.

Why it exists: Roni is job-hunting and his unstructured days were drifting (evenings lost to doomscrolling). The app gives structure without being rigid: it logs deviations instead of punishing them, and from 2.0.0 he can reshape today's plan as the day changes.

- Code: `C:\Blocks App` on Roni's Windows machine. A git repo; `master` holds released work, tagged `vX.Y.Z`. Remote `origin` = `github.com/RoniBasarab/BlocksApp`. Keep it private (§17).
- All spec and task files live in the repo root (committed from Task-6 on).
- Timezone Asia/Jerusalem. The work week is Sunday–Thursday; the weekend is Friday–Saturday.
- Released: **3.0.0** (Task-18: the invisible foundation for your own templates; checked in Blocks Debug 13 Oct, "everything still works"). In progress: **3.1.0** (Task-19: Templates tab, template editor, category editor, run as Base + two parallel tracks + Finish). Then 3.2.0 (Home edits with the same sheet), 3.3.0 (profiles).
- **Roni's phone:** the release app stays on **2.6.2** (his real days). Every new version is tested only in **Blocks Debug** (`com.roni.blocks.debug`); he upgrades the release only once a version is proven stable and data-safe. Same signing key, never uninstall.
- All docs live in `docs/` (from Task-8 on); the root holds only the build files, `README.md` and `CHANGELOG.md`.

## 2. People and roles

| Who | Role |
|---|---|
| **Roni** | Owner and only user. Senior Android/Kotlin developer (~6 years). Uses the app daily, reviews on his phone, carries files and reports between the agents, decides. |
| **Planning agent** (a Claude chat; earlier docs call it "architect") | Talks with Roni, runs the decision questionnaires, writes the specs and `Task-N.md` files, reviews the working agent's reports. Has no access to the repo or the phone. |
| **Working agent** (a coding LLM in the repo; earlier docs call it "executor") | Implements one task file at a time. |

About the working agent:
- **Known failure modes:**
  - Calls "first cuts" done.
  - Edits many files without compiling.
  - Refactors working code it wasn't asked to touch (the Task-4 engine refactor broke the build).
  - Writes wrong conclusions and wrong API fixes into its own `issues.md`.
  - Delegates to background jobs that time out.
  - Falls into analysis rabbit holes: in Task-6 it spent most of a day disassembling the serialization library instead of fixing the bug. Task files now carry a 20-minute time-box rule.
  - Stalls and dawdles: it reads in small chunks, re-checks git and its environment between edits, repeats shell mistakes, and goes silent after a few commands (Task-7, 7–8 Oct). Roni wants one session per task, not per slice; the §18 prompt keeps each slice to a few big steps.
- **What works:** exact strings, near-complete code for the tricky parts, small compile-gated slices, explicit checks, and a final report against the task's list. Its `NOTES.md` sometimes catches real gaps in a task (Task-6 asked for `onTimeChanged()` where only `onTimeTravelled()` could work), so read it as input, not just as a log.

Roni starts fresh sessions for both agents at handoffs, so everything they need has to be in these files.

## 3. How Roni likes to work

- **Architecture is his, and it's binding (13 Oct, after Task-19's tracks drifted).** Every task follows `docs/architecture.md` exactly: per screen an `@Immutable` State, a sealed Action, a `@HiltViewModel` with one `onAction()`, updates via `_state.update { copy() }`; the Route is the only composable that knows the ViewModel; every other composable is (state slice, `onAction`) only, never lists of callbacks. If the planning agent thinks another way is better, it **asks Roni first with pros and cons**; it never decides alone. Task files spell out the State/Action types for every new screen or sheet. The cause of the drift was the planning agent's own Task-19b line ("plain values and callbacks").
- Order (Roni, 13 Oct, revised after 3.1.0): **Task-20 = 3.1.1, done and checked by Roni 13 Oct** (picker scrolls as a 2-column grid; the template list follows the store through `TemplateStore.templates` StateFlow, `@HiltViewModel`) → **Task-21 = 3.2.0: profiles (done 13 Oct)** → **Task-22 = 3.3.0 (written 13 Oct; Base + Track A Home/evening + Track B first-run menu/explainers/Profiles + Finish): the real day on the calendar (verified `DayTimeline`), one edit sheet for today and templates (verified `TodayEditor`: push later, kid time and walks are walls, nothing in the next 5 min), Dishes runs over with vibration-only nags (`AlertPlanner` `warn = false`), first-run main menu (only until a profile was chosen once; existing installs count as chosen), first-visit explainer cards (non-dismissable, "Got it"), Profiles list tweaks, the Off-day crash and the Built-in tag** → **3.3.0 done and checked by Roni 13 Oct ("all fine")** → **3.4.0 (Task-23, written 13 Oct; slice 4: steps while driving; slice 5: safe upgrade from 2.6.2 (chosen-flag fix for 2.x installs, UpgradeFrom262Test on 2.6.2-shaped prefs, Settings → Your data: back up / restore JSON, restorable into Blocks Debug; 2.6.2 and 3.4.0 debug APKs built for an upgrade rehearsal)): explainers become inline hint cards + walk and step statistics (Roni, 13 Oct)**: 3.3.0's first-visit explainer popups felt like being jumped at on launch; they become a soft hint card at the top of each place (Profiles, a profile, Days, the template editor, Categories) with the explanation and "Got it", no popup, shown until closed; the first-run welcome screen gets none (its own text explains); the ? tour stays. Roni tests Blocks Debug with fresh installs, so he does see the welcome screen. Walk statistics: Weekly gets a "4 walks · 2h 10m" tile (day-block and standalone walks together) and a walk-steps share ("About 6,400 of your steps came from walks"); Insights gets scorecard rows compared to last week (walks, walk minutes, average steps, each with its change) and an evening-walks row (walks after kid time) → then an architecture refactor version, planned by Roni and the planning agent together (audit the repo against `architecture.md`, decide item by item); the working agent only applies the agreed changes.
- **Profiles design (Roni, 13 Oct):** the Templates tab goes away. The menu (≡) gets "Profiles" next to History and Settings: a list of profiles (active one marked, + New profile) → a profile page with its templates (+ New template, + Add existing from the library), its weekday → template map, and the categories. The picker shows "Profile: <name> ▾" to switch, scrolls, and has a "How to use" (?) button; the profiles area gets one too, top right next to the menu, with a short first-visit hint "Tap How to use for more". Templates are a **shared library**: profiles pick which ones they use and which template each weekday suggests (weekdays move from the template to the profile); editing a template changes it for every profile using it. Categories are **shared by all profiles**. **3.2.0 details (Roni, 13 Oct):** switching mid-day leaves today alone (new profile from the next pick); a new profile is "Start empty" (only Off) or "Copy <active>"; How to use = 5 swipeable cards (Profiles, Templates, Blocks, Days, Categories) from a ? button, with a first-visit hint on the Profiles list; History shows the profile a day *started* under (`profileName` on TodayPlan/DayRecord); Weekly/Insights unchanged. Verified pure `core/profiles/Profiles.kt` (ProfileBook, repair = the 3.1→3.2 weekday move, create/rename/delete/switch/add/remove/setDay/dropTemplate) with ProfilesTest incl. a 20,000-edit invariant fuzz. Task-21 written (3.2.0), 13 Oct.

- **Decisions via questionnaires:** multiple choice, 5 options per question plus "Other", as interactive forms when the tools allow. The answers go into the spec files. Sometimes he just decides in a message (he chose drag handles on 7 Oct that way).
- **Deliverables are Markdown files** he hands to the working agent, together with the exact prompt to start it (§18).
- **Performance is priority #1:** as few recompositions and skips as possible.
- **No over-engineering:** MVI-lite (§6); no reducers, use cases or clean-architecture layers.
- **Wants honest pushback with reasons**, and a clear recommendation when he asks "what do you think?". He decides fast, and he's open to removing features that don't earn their place (he questioned Time Debt himself).
- **Prefers fewer, clearer choices.** He cut the NowCard controls himself after using the app.
- **Wording a stranger would understand:** after a few days away he couldn't remember what "leak total" meant. Every label and number must explain itself, and every visual mark needs words ("understandable within a fraction of a second of a glance").
- **Looks matter:** "make sure it looks nice" (7 Oct, about dragging blocks).
- **Less testing (4 Oct):** he felt the tasks had become test-heavy. Policy in §15.
- **Every iteration is versioned** (§14); he leaves major/minor/patch to the planning agent.
- **Battery:** no vibration anywhere (walk rings are the one exception); sound only in the daytime segment; silence after the day ends.
- **Anything that could vary, ask (13 Oct):** whenever a feature has a value that could reasonably differ (per day, per template, per profile, on vacation), run a short questionnaire on how dynamic it should be before writing the task. Day start was the trigger: it was a constant and he wanted it per template.
- **Small tasks (13 Oct):** split big work into tasks small enough that the working agent never needs a huge context: one version per task, about 3–5 slices.
- **Parallel agents (13 Oct):** when two pieces of work touch disjoint files, say so and give them as separate tracks, each in its own git worktree (`git worktree add "../Blocks App B" -b <branch>`), merged one after the other. Never two agents in the same folder.
- **Branch naming from Task-18 (13 Oct):** branch `task-<N>/v<version>` (e.g. `task-18/v3.0.0`); parallel tracks `task-<N><letter>/v<version>-<what>` (`task-19a/v3.1.0-categories`) in worktree folders like `C:/Blocks App 19b`; commits `task-<N> slice <S>: <title>`; tags stay `v<version>`; task files stay `docs/Task-<N>.md`.
- **Agent sessions don't start in the repo:** `AGENT.md` step 0 is `cd "C:/Blocks App"`.
- He writes fast and casually, sometimes by voice dictation, so expect typos. Interpret the intent.
- He installs with Android Studio's Run button. His daily app must be the **release** variant (`com.roni.blocks`). `debug` installs a separate test copy (`com.roni.blocks.debug`) with demo data and the debug clock. Every installed copy fires the 9:00 prompt, so test copies should be uninstalled after use.
- **Family context that shapes the schedule:** a young child in kindergarten; drop-off before 08:00, pickup at 16:20 on Sun/Tue/Wed; grandparents take the child Mon/Thu until about 19:00; his wife usually runs the bedtime ritual, sometimes with Roni.

## 4. Documents (all in `docs/`)

| File | What it is | Status |
|---|---|---|
| `SUMMARY.md` | This file: the handoff | Current |
| `AGENT.md` | The working agent's standing instructions; Roni's prompt is just "Read "C:/Blocks App/docs/AGENT.md" and do what it says." | Updated by the planning agent for each task |
| `Task-18.md` | The current task (3.0.0), with a slice map of line ranges | Ready to hand over |
| `schedule-spec.md`, `architecture.md`, `visuals.md` | Behavior, code, look | Describe 2.1.0; 2.2.0 changes are in Task-9 and §10 here |
| `CODEMAP.md` | One row per source file | The real file map; task files use its names |
| `NOTES.md` | The working agent's decision log | Must not contradict the code |
| `issues.md` | Open issues, with evidence | Check it for wrong conclusions |
| `MANUAL_TESTS.md` | Device tests and the per-release phone checklist | For Roni |
| `CHANGELOG.md` (root) | Version history | Updated at every bump |

Old task files are deleted from the repo once done; git history keeps them.

When a decision changes, update the spec files and this summary, not only the next task.

## 5. Product rules (quick version; details in `schedule-spec.md`)

**Templates** (a bundled JSON file; every block has a stable id such as `sun_tue_wed.coding_am`)

| Template | Blocks |
|---|---|
| Monday / Thursday ("Gym day") | CV 9:00–9:30 · Coding 9:30–11:30 · Free 11:30–12:00 · Gym 12:00–14:00 · Free 14:00–16:20 · Coding 16:20–17:20 · Free 17:20–19:00 · Kid time from 19:00 |
| Sunday / Tuesday / Wednesday ("Focus day") | CV 9:00–9:30 · Coding 9:30–11:30 · Free 11:30–14:00 · Coding 14:00–15:30 · Free 15:30–16:20 · Kid time from 16:20 |
| Friday / Saturday ("Family day") | Kid time 9:00–13:00 · Free 13:00–15:00 · Kid time from 15:00 |
| Off day | No blocks, no notifications; counted in the weekly summary |

Block ids, in order: `mon_thu.{cv, coding_am, free_pre_gym, gym, free_post_gym, coding_pm, free_pm, kid_time}`, `sun_tue_wed.{cv, coding_am, free_midday, coding_pm, free_pm, kid_time}`, `fri_sat.{kid_time_am, free, kid_time_pm}`; at runtime the evening adds `evening.dishes` and `evening.choice`.

Coding uses Pomodoro 50/10, counted from the block's actual start. Phase switches are automatic and chime. From 2.0.0, Coding blocks show their focus and break parts on Home, and the current one says how much is left.

**Day lifecycle**
- 09:00: a prompt to pick a template, repeated every 15 minutes until 10:00. The picker appears **only** while today hasn't started.
- **Late start** (2.0.0): picking a template later lands on the block the clock is in. The blocks before it count as **missed**, not ran over. From 22:30, nothing starts.
- **Swap template:** re-plans the rest of today from now with another template; what already happened stays. If the day has ended it asks first; after 22:30 it's refused.
- 20:00: the evening prompt ("Kid asleep" / "End day"). "Kid asleep" starts Dishes (20 min), then a forced choice of Game / Project / Read / Chill until 22:30.
- The day ends at 22:30, on "End day", or on **Emergency**. Ending the day flushes history, cancels alarms and goes silent.
- If "Kid asleep" is never tapped, the day ends by itself at 22:30, flagged `EVENING_SKIPPED`.

**Today's plan and editing (2.0.0)**
- Each day has its own plan, copied from the template at the start (or at a swap). Edits change today only.
- Tap a block to select it. Work blocks (CV, Coding, Gym) get handles on their top and bottom edges. Dragging one moves that edge in 5-minute steps.
- A block that grows takes the free time next to it first. Work blocks in the way move along, keeping their length. Kid time never moves.
- Free time has no handles: it's the slack around the work blocks. Any later block can change type (CV, Coding, Gym, Free); a block turned into Free merges with the free time around it.
- The current block: only its end moves, and not into the next 5 minutes. After Next ended it early, it's fixed. Past blocks are fixed. Work blocks are at least 10 minutes long.
- Edits are silent; the alarm and the notification's countdown follow them. Stats compare against the day's final plan (flag `PLAN_EDITED`), so extending a block on purpose isn't running over.

**Controls (NowCard and notification)**
- **Next slot** (any time):
  - A work block finished early ends now (under-time logged), and the rest becomes free time until its planned end. The next block then starts on schedule.
  - Free time ended early (planned, or that leftover): the next block starts now and keeps its planned end.
  - Late, on any block: the next block starts now, and the overrun counts as **ran over**.
  - If the clock is already past the next block too, you land where the clock is, and the blocks in between are **missed**.
  - The button names what you get: "Next: Free" on a work block before its end, "Next: Coding" from free time or when late.
- **Pause** (work blocks only): stops counting worked time. No end time moves, so a pause shortens the current block only. It ends by itself at the block's end. Logged as pause time; it doesn't count as ran over.
- **Emergency:** a red button with the dialog "The day has been disrupted by an emergency. Stop all schedules for today?". It ends the day, flagged `EMERGENCY_STOP`.
- **Ran over** = time past a block's planned end before Next, per day. (It used to be called "time debt"; the Time Debt screen is removed in 2.0.0.) **Gym missed** = missed, cut by an emergency, or under 30 minutes worked.

**Notifications**
- One notification, updated in place, with a countdown drawn by the system (the app never wakes up to update it).
- The `day` channel plays a sound on transitions; the `evening` channel is silent. No sound for edits. From 2.2.0 there are also alerts: 5 minutes before a running block ends, at its end, every 5 minutes after that until Roni reacts, and at each Pomodoro break and focus start; alerts sound even with the app open. The sound can be changed in Android's channel settings (Settings → Alert sound); the debug and release apps each have their own.
- Actions: running work block "Pause" + "Done early" (2.2.0; was "Next: Free"); free time "Next: X"; ended block "Next: X"; paused "Resume"; evening "Kid asleep" / "End day". Actions never open the app, and stale or double taps are ignored.
- Tapping it resumes the app as it was (nothing happens if the app is already open). It's `ongoing`, but on Android 14+ it can still be swiped away; it comes back at the next transition.

## 6. Architecture (quick version; details in `architecture.md`)

- **Stack:** Kotlin, Jetpack Compose, Material 3, dark only, English only, minSdk 34, Hilt, Navigation 3, SharedPreferences + kotlinx.serialization, kotlinx-datetime, kotlinx.collections.immutable, AlarmManager exact alarms (`USE_EXACT_ALARM`). No Room, DataStore, Gson, WorkManager or foreground service. Single module, packages by feature.
- **Versions in use (from `NOTES.md`):** AGP 9.4 with built-in Kotlin, Kotlin 2.2.10, KSP 2.3.12, kotlinx-datetime 0.8 (`kotlin.time.Clock`/`Instant`, `ExperimentalTime` opt-in), Material3 1.4.0 (no `sheetGesturesEnabled`), core-splashscreen 1.2.0, Roborazzi 1.75.0, material-icons-extended. The compile SDK has removed `Notification.getTitle()`: read `extras[EXTRA_TITLE]` instead. Robolectric still has the old API, so JVM tests won't catch its use.
- **Pattern per screen:** an `@Immutable` State data class, sealed `Action` / `Effect`, a `@HiltViewModel` with `onAction()`, updates only via `_state.update { copy() }`, collected with `collectAsStateWithLifecycle`. Composables only render; logic lives in `@Singleton` managers.
- **Day state machine:** `core/daystate/`. A pure `DayStateMachine` plus the `DayOrchestrator` singleton, shared by the UI, alarms and notification actions. It persists before side effects. `ensureRestored()` is single-flight and always finishes, and every entry point calls it. `SegmentBook` records what actually happened.
- **Today's plan (2.0.0):** `DayOrchestrator` owns `TodayPlan`, saved with the day state. Everything reads today's blocks from it; `Running.blockIndex` indexes it. Edits go through `DayOrchestrator.editPlan()`, which uses the pure `PlanEditor`. Home uses the same `PlanEditor` for the live drag preview, so preview and commit always agree.
- **Schedule engine:** `ScheduleEngine` plus pure helpers in `core/schedule/TimelineQueries.kt`. Don't refactor the engine to suit callers; add pure functions. It's handed a `Template` built from today's plan. `planStartsAt` (Task-6) is retired in 2.0.0.
- **Notifications:** a pure `NotificationModelMapper` builds the model and `NotificationRenderer` posts it. There's exactly one pending exact alarm, always recomputed as "the next wake-up".
- **Performance:**
  - Ticks are passed as lambdas and read in the draw phase.
  - The calendar is a `drawWithCache` grid, a custom `Layout` for blocks and draw-phase overlays.
  - Block slides during edits animate in the layout phase, and a drag recomposes at most once per 5-minute step.
  - `RecompositionCounter` with Robolectric tests proves that ticks don't recompose.
- **Navigation:** the start destination is resolved once, after restore (`NotStarted` → picker, anything else → Home). Bottom bar: Home · Weekly · Insights. Burger menu: History · Settings (Time Debt removed in 2.0.0), as separate entries outside the tab scaffold. Every screen change is the same 250 ms crossfade. Each window inset is applied exactly once.
- **Builds:** `release` is Roni's daily app. `debug` (`.debug`) adds the debug clock (`DebugClock`, `DebugTimeController`, `TimeTravelReceiver`) and demo data. `benchmark` (`.benchmark`) is for Macrobenchmark.

## 7. Visual direction

- Dark, calm, low saturation. The page is a vertical gradient `#27282C` → `#1C1B19` with faint static glows (steel blue top-right, sand bottom-left). Cards are `#312F2C` with a top-lit border. Accents are steel blue `#7A9CC6` and sand `#D9C38A`; ran-over and destructive actions use `#D97070`.
- Each block type has its own muted hue. Roboto with tabular figures for times. Corner radii: blocks 10dp, cards 16dp, hero cards 20dp.
- The template picker has a slow "aurora" with a parallax dust layer: the only infinite animation in the app.
- Home is a Google-Calendar-style day view (08:00–23:00, 80dp per hour from 2.0.0) with a floating NowCard: a Pause / Next row and a small red Emergency button. A selected block shows round handles on its edges, and an edit card takes the NowCard's place.
- After the day ends (Home) and for any past day (History): a **recap**, meaning a summary card and one row per block, with everything in words. No hatching or unlabeled marks.
- **Wording:** user-facing text says "ran over", never "leak", and "missed", never "skipped" or "consumed", for blocks. Every metric has a one-line hint, and Weekly and Insights have an ⓘ "What these mean" dialog.
- Roni's verdicts:
  - After Task-4: "overall visually looks clean and nice".
  - After Task-5: Home "ok-ish".
  - 7 Oct: Insights "looks good now"; History's day view "an incoherent mess" (hence the recap).

## 8. How we got here

- **26 Sep:** Roni, between jobs and job-hunting, wanted structure; his evenings were disappearing into doomscrolling. A 25-question schedule questionnaire produced the three templates, the manual start at 9:00, the run-over-until-Next rule, the evening flow and the silence rules. As an Android developer he chose to build a native app rather than use a generic timer.
- **26 Sep:** a 28-question architecture questionnaire settled Hilt, a single module, minSdk 34, SharedPreferences, kotlinx-datetime, Navigation 3, MVI-lite, a Canvas calendar, exact alarms and no foreground service. He picked Gson; the planning agent argued for kotlinx.serialization and he agreed. A 10-question visuals questionnaire set the palette, block colors, corners and type.
- **27 Sep:**
  - **Task-1 (1.0.0):** the first build. It worked but looked raw, with no design system.
  - **Task-2 (1.1.0):** design system, edge-to-edge, splash, icon, segment recording, receiver fixes. The screens were still first cuts.
  - **Task-3 (1.2.0):** the Home calendar (given as near-complete code), the picker's aurora, theme fixes, and the performance harness: stability reports, recomposition tests, a Macrobenchmark module and Roborazzi.
  - **Task-4 (1.3.0):** Roni found two notifications awkward, so it became one notification with a system countdown and a launcher-style tap. Also a NowCard whose buttons explain themselves, screen primitives, and UI tests written by the planning agent. The working agent broke the build (many files changed without compiling, plus an engine refactor); `Task-4-recovery.md` introduced compile-gated slices, and it finished green.
  - **Task-5 (1.4.0):** after using the app, Roni cut the controls to Next slot / Pause / Emergency and kept "finishing a work block early gives free time". Also the cold-start fix (the picker reopened for a started day), real Settings, debug-only demo data, finished data screens, stable block ids, a planned-blocks snapshot per day, and the versioning policy.
- **4 Oct:** Roni's phone review of 1.4.0 found 12 bugs (the worst: stuck on the splash after Android kills the app). He asked to dial testing down, asked whether code comments would help agents (hence `CODEMAP.md`), and asked for editing. Task-6 was written.
- **4–7 Oct: Task-6 (1.5.0) and Task-6-fixes (1.5.1).**
  - The working agent spent most of a day on the splash bug, disassembling the serialization library, until the planning agent pointed it at the likely cause and added the time-box rule.
  - The root cause: `DayStateSerializer` called `delegate.serialize/deserialize` directly, which skips Json's class-discriminator handling. Writes produced the array form and reads expected the object form, so every cold start's restore threw. It now delegates through `encodeSerializableValue` / `decodeSerializableValue`, with a reader for the old array form.
  - Task-6-fixes made the tests deterministic (an injected `@IoDispatcher`; `robolectric.properties` with a plain `Application`), made Settings save before showing, and sent clock and timezone changes through the full catch-up replay.
  - Result: `master` = `0b14726` = `v1.5.1`, 188 tests, pushed to `origin`.
- **7 Oct:** the planning session couldn't attach the GitHub repo (no access for the session's GitHub credential; the Claude GitHub app isn't installed on his account). Roni attaches files by hand.
- **7 Oct:** Roni's review of 1.5.1 → Task-7 (2.0.0, §9). He chose drag handles for editing; the planning agent filled in the details (§10) and recommended removing Time Debt.

**Lessons for writing tasks**
- The JVM tests were all green while the app was stuck on the splash. They can't see process death, insets, real data or real gestures. Hence the lighter testing policy (§15), a short phone checklist per release, and the working agent using `adb` on Roni's phone when it's connected.
- The working agent's `issues.md` often contains wrong conclusions (e.g. "ongoing notifications can't be swiped away", or imports that don't exist). Correct them explicitly in the next task.
- Give exact strings and near-complete code for anything layout- or concurrency-heavy. When the working agent designs these itself, the result is a first cut.
- Give it a time-box: after ~20 minutes of analysis on one question, it writes down what it knows, applies the simplest fix that passes the named tests, and moves on.
- The working agent sometimes goes silent after a successful step (7 Oct, right after a green compile at the start of Task-7). The fix is a short nudge: state the facts (green build, warnings are old), then "continue with the next slice, one line per step, don't stop until the final report". The prompt (§18) now says this up front.
- Task-7's first sessions were far slower than earlier 12-slice tasks and ran out of quota. The logs show why: it read the whole doc set up front (~150k characters, re-sent on every call), ran `git log` seven times between edits, kept retrying `&&` in PowerShell 5.1, and used Python one-liners to search files. Each needless call re-sends the whole context. The prompt in §18 now covers each of these.
- When a task hands over pure logic, the planning agent compiles and tests it first and marks it *verified* (§17). Task-7's `PlanEditor` was checked against every named test plus 40,000 random edit sequences.

## 9. Current state (13 Oct 2026)

**Released, 2.6.2:** tag `v2.6.2`. Since 2.5.0:
- the walk works (`WalkClock`);
- the calendar never overlaps or clips (`CalendarLayout` with measured text heights; slivers and pills);
- Next goes to the very next block;
- permissions are asked at launch;
- the status line counts done/missed after the day;
- the doubled top padding is gone from every screen;
- steps and walks sit at the top of a History day.

Roni walks in the evenings now and likes the subtle steps counter.

**Done, 2.7.0/2.7.1 (checked on the phone 13 Oct, "everything worked perfectly"):** a template picked before its first block waits ("Day starts at 09:00 · in 1h 20m", Start now / Change day), and steps read "about 2,050 steps" (the sensor counts car bumps). Task-16 was folded into it and never run.

**The repo is public** (Roni opened it so the planning agent could read it). The docs describe his family's routine; he was told he can switch it back to private.

**Done, 3.0.0 (Task-18, reviewed 13 Oct):** `TemplateId` is a string value class (old "MON_THU" data decodes; `LegacyFormatTest` pins it), `BlockType.Custom`, `Categories`/`CategoryStore`, `StoredTemplate`/`Profile`/`TemplateStore` seeded from assets. Installed in Blocks Debug: no visible change, as intended.

**Waiting on Roni:** run Task-19 (Base, then Track A and Track B in parallel, then Finish; prompts in §18).

**Roni's Home feedback on 3.0 (13 Oct), for Task-20 (3.2.0):** adding a block only works on free time (by design since 2.1: Insert takes the start of a free block); after inserting one and *dragging its handle*, the time didn't change on screen (cause not found by reading: `PlanEditor` insert-then-resize is correct in the harness; the orchestrator logs "refusing plan edit" for silent refusals: check logcat on the phone); no Delete anywhere (turning a block into Free is the hidden way). Decided: Home gets the editor's `BlockSheet` (category, start, length, Add after, Delete) on any future block.

**Deviation to remember:** Roni once asked for "minutes left, updated once a minute" in the notification. It uses the system chronometer instead (it shows negative time while running over). He hasn't objected.

**Working-agent lessons from Task-8 (now in `AGENT.md`):** Gradle output through a PowerShell pipe hung until the shell's 900 s timeout (the daemon holds the pipe), and the case-insensitive filter matched warnings, so the agent doubted green builds. Gradle now runs through `cmd /c` into a temp file with a case-sensitive filter, and `exit=0` is the whole answer. After its final report it looped re-checking an old todo list; it now stops after the report.

## 10. Editing (2.0.0) and other future ideas

**What Roni decided (7 Oct):** drag a block's top and bottom edges, with the other blocks pushed along and a nice feel; change a block's type. A major version.

**Details the planning agent chose** (in Task-7; Roni can change any before the handover):

| Topic | Choice | Why |
|---|---|---|
| Scope | Today only; templates unchanged | The template stays a clean starting point |
| Handles | Work blocks only; free time is the slack | One mental model: you shape the work, and free time fills the rest |
| Growing | Takes the adjacent free time first; work blocks move along keeping their length; kid time never moves | Changes as little as possible; pickup and bedtime are fixed in real life |
| Current block | Its end only (2.2.0: also its type, work types only), not into the next 5 minutes; changing its type is Next's job | No rewriting what's already happening |
| Past blocks | Fixed in the plan (2.1.0/2.2.0: the record is corrected instead) | History stays true |
| Steps | 5 minutes; 10-minute minimum | Precise enough, and a 10-minute block still has room for both handles |
| Precise edits | The edit card's ±5 steppers do the same as dragging | Long moves without auto-scroll, and an accessible fallback |
| Sound | Edits are silent; the alarm and countdown follow | An edit isn't a transition |
| Stats | Against the final plan; flag `PLAN_EDITED` | Extending a block on purpose isn't running over |
| Undo | None (drag it back) | Edits are small and visible; fewer controls |
| Haptics | None | The no-vibration rule; a snap tick is an easy add if he allows it |

**Time Debt (Roni asked whether it's needed, 7 Oct):** the screen lists overruns for the last 14 days. Everything it shows also appears elsewhere: Weekly's "Ran over" tile, Insights' "Ran over" and "Blocks that run long", and each History day. "Debt" suggests paying something back, but by design it's flushed at midnight and never repaid, so the screen has nothing to act on. With editing, extending a block on purpose isn't an overrun at all. Task-7 removes it; the menu becomes History · Settings.

**Technical shape (Task-7):**
- `TodayPlan` is saved with the day state.
- `PlanEditor` is pure and *verified*. Home uses it for the live preview and the orchestrator for the commit.
- `blockIndex` indexes the plan.
- Segments carry a block id, and missed ones a reason.
- `planStartsAt` is retired with a migration.
- `HistoryManager.observeDay` replaces polling for the ended day's record.

**2.1.0 scope (decided with Roni, 10 Oct; one release, Task-8):**
- Fixes:
  - The evening showed plan block 1 (`currentIndex()` in `Home.kt` maps `Evening(blockIndex)` into the plan).
  - Weekly's empty week drops the arrows.
  - A tap on a locked block says nothing.
- Evening on the calendar: kid time ends at "Kid asleep", then Dishes and the chosen block appear as blocks, with time left on the card.
- Developer tools: one Settings row that opens a dialog with the debug clock (−10/−30/−1h as well as +), "Test a day" (pick a template and a start time, and the day starts there), evening helpers and demo data.
- Tests: delete the Roborazzi screenshot tests and the plugin, and any layout check with no functional use. Keep the logic tests and the recomposition tests.
- Editing:
  - Add a block into free time (30 minutes at the start of the free block, then drag).
  - Fix past blocks, today and older days in History: when it ended, what I did instead, mark a missed block done. The plan stays; the record changes.
- An evening check-in when the day ends (Good / OK / Rough, one tap, skippable), shown in History.
- A home-screen widget: the current block plus a system-drawn countdown, updated only at transitions.
- Not wanted: job-search features (keep it about time), export/backup (not now).

**2.2.0 scope (decided with Roni, 11 Oct; one release, Task-9):**
- Fixes: the widget, the debug-clock lag, the locked-tap dialog, ±15 in corrections.
- Done early, shown clearly: Next before a work block's end still gives free time until its planned end, but Home and the notification say "Free time · Done early", name the real next block and stop the Pomodoro. The button on a running work block says "Done early".
- The notification shows the Pomodoro round, the break time and a countdown to it.
- Alerts: 5 minutes before the end, at the end, every 5 minutes until Roni reacts (any tap, or opening the app), and each Pomodoro break and focus start. They sound even with the app open. Pure `AlertPlanner` (*verified*) decides the times.
- Editing: tap any block of today. Past blocks: corrected right away with the 2.1.0 correction sheet. The running block: its end (±5/±15) and, for an unfinished work block, its type (work types only). Running free time ended sooner starts the next work block sooner. `PlanEditor` gained `END_AND_TYPE` (*verified*).
- Not picked: a foreground service, heads-up banners, the app playing its own sound.

**2.3.0 scope (Roni, 11 Oct; Task-10):**
- Next before the next block's start: the next block that isn't free time starts now with its own length; free time in between is skipped; the rest becomes free time. Next into kid time starts kid time. Pure `PlanEditor.startEarly` / `nextStop` (*verified*).
- Walks: a Walk button on the NowCard; a start sheet (30 min, ±5, 10–60); the walk is inserted at now and the current block resumes after it (a longer walk eats the next blocks' starts; kid time is a wall). It rings like a timer at half time ("Turn back now") and at the end: alarm sound even on silent, repeating until Stop or a minute, with vibration (the one exception to the no-vibration rule). "Turn back now" ends the way out early; the way home takes as long. Home shows a WalkCard with a two-colour ring (out cool, home warm). Pure `insertWalk` / `shortenWalk` / `walkEnd` and `AlertPlanner.walkAlerts` (*verified*).

**3.x: profiles and your own templates (decided with Roni, 13 Oct; one task per version):**
- **Why:** he's job hunting, and once employed whole days change shape (an office block 09:00–17:00). The app should hold more than his four fixed days, and maybe one day serve other people. Play-store issues (exact alarms etc.) come later.
- **Profiles:** a profile holds its own templates plus which template is suggested on each weekday. Switch by hand in Settings; History keeps which profile each day used. Start: "Unemployed" (today's four templates).
- **Templates:** made in a **new Templates area with a visual editor** (Settings → Templates → list → an editor that looks like Home's calendar). New screens, built by reference to Home's look and behaviour, without touching Home's tested code.
- **Categories:** your own block kinds, each with a name, a colour, "counts as work" and Pomodoro on/off. CV, Coding, Gym and Free become the first four; Office, Commute and so on are added by him.
- **Kid time and the evening routine:** always on. Every template ends with kid time until 22:30; more kid time earlier is allowed (Fri/Sat). The four evening activities stay as they are.
- **Built-in categories** (CV, Coding, Gym, Free) can be renamed and recoloured, never deleted. Pomodoro is on/off per category, always 50/10.
- **Day start:** comes from each template's first block (2.7.0); templates can be picked from midnight. Day end stays 22:30 for now.
- **Major bump, 3.0.0:** the stored data model changes and can't go back to 2.x.
- **Order:**
  - **3.0.0:** the model underneath, with no visible change. Categories and stored templates replace the fixed lists, everything is seeded from today's four templates, and old history keeps decoding.
  - **3.1.0 (Task-19):** a Templates tab (not in Settings): templates + categories. Editor = list + sheet; push-later rule (`TemplateEditor`, verified); start from blank; delete own (archived, History keeps titles); built-in templates' blocks read-only; weekdays claimable on every template, one template per day; 12-colour palette; today follows an edit only while it is still waiting.
  - **3.2.0 (Task-20):** Home's today-edits use the same `BlockSheet`: Add after and Delete on any future block, the drag bug.
    - **The real day on the calendar (Roni, 13 Oct, after a supermarket run made Free run over and Next left Free drawn 11:30–14:00):**
      - A block that ran over *grows*: after Next it's drawn at its real times (Free 11:30–14:20) with the overrun as a red-striped tail and a "+20 min" label; the next block starts where it really started ("14:20–15:30 · 20 min late").
      - While running over, the block grows live minute by minute and the next block shrinks from the top; a block eaten completely becomes a "Coding · missed" pill (CalendarLayout's pills).
      - Everything already over is drawn as it happened: pauses as grey striped gaps inside the block, skipped blocks faded with "Missed", real times. Blocks still ahead are the plan and stay editable exactly as now (PlanEditor untouched; past blocks already open the correction sheet).
    - **Dishes runs over like a day block (Roni, 13 Oct):** Dishes no longer ends by itself at its planned end when an activity is picked (2.5.0 behaviour). It runs until Roni ends it (Done dishes / Next), and the picked activity starts then. At Dishes' planned end and every 5 minutes after, the phone **vibrates only**, no sound (the child is asleep; the second exception to the no-vibration rule, after walks); no 5-minute warning. History's Dishes row reads "Dishes 20:30–20:57 · 27 min (+7)" in Leak when it ran over; Insights gets a pattern line ("Dishes ran over on 4 of the last 7 evenings, by 9 min on average"). Same red tail on the calendar as day blocks.
      - Planned approach: a verified pure `DayTimeline` mapper (plan + segments + now → drawn intervals with overrun/pause/missed marks), fuzzed for no overlaps, feeding `CalendarLayout.fit`; the existing start/end springs animate the change on Next.
  - **3.3.0 (Task-21):** profiles and switching.
- **Technical plan for 3.0 (planning agent's, not yet a task):**
  - Keep `BlockType`'s built-ins and add `BlockType.Custom(id, name, colorArgb, work, pomodoro)`. Each block carries its category's flags, so an old record still says what it was after the category is edited, and old JSON needs no migration.
  - `TemplateId` (enum) becomes a String-backed `TemplateKey`. Enum values serialize as their names, so "MON_THU" in stored state and history still decodes.
  - The template validation rules and the seed conversion are delivered as verified pure code.

**Later, not agreed (don't build without asking):**
- A foreground service, if alerts still come late in deep Doze (or `setAlarmClock` for alerts).
- Saving today's edits as the template, or a template editor screen.
- Undo; haptics on snap.
- Moving a whole block by dragging its middle; handles on free time; adding or splitting blocks.
- Export/backup of history (JSON/CSV); a daily score or streaks; deeper pattern detection.

## 11. Next steps

1. **Roni:** run Task-19 (3.1.0): Base, then Track A and B in two agents at once, then Finish. Test in Blocks Debug only.
2. **Planning agent:** review Task-19: `TemplateEditor`/`TemplateRules` untouched, every edit goes through `TemplateActions`/`CategoryActions`, the tracks touched only their files.
3. Task-20 (3.2.0): Home's edit card → the `BlockSheet` (Add after, Delete anywhere; PlanEditor needs a verified `insertAfter`/`delete`), reproduce the drag bug first. Ask Roni what "Add after" does when no free time follows (push like the editor, or take the next block's start).
4. Task-21 (3.3.0): profiles. Optionally a "Back up my data" button before it.

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
| Pause shortens the current block and doesn't count as ran over | A pause never steals from other blocks |
| Emergency ends the day | Replaced the freeze/resume flow |
| Swap = re-plan the rest of today from now; confirm after the day ended; refused after 22:30 (4 Oct) | Roni's request |
| Demo data debug-only, flagged `demo` | See real layouts without touching the daily app |
| `.debug` / `.benchmark` app ID suffixes | Tests never touch the real day's data |
| Stable block ids and a planned-blocks snapshot (Task-5) | Groundwork for editing |
| Semantic versioning, one bump per task | §14 |
| Lighter testing (4 Oct) | Most real bugs were invisible to JVM tests; §15 |
| `CODEMAP.md` + short class headers; no change history in code comments (4 Oct) | Fast onboarding without comments that go stale; §16 |
| Wording: "ran over", never "leak"; "missed", never "skipped"; every metric has a hint | A stranger must understand every screen |
| Each window inset applied once; every screen change a 250 ms crossfade | Fixes the gaps; a consistent feel |
| A late start lands on the current block; earlier blocks are missed, not ran over (7 Oct) | Roni's request; starting late isn't an overrun |
| Editing: drag handles, pushing, retyping; today only (7 Oct) | Roni's request; details in §10 |
| Free time is slack (no handles); kid time is a fixed point (7 Oct) | One mental model; real-life fixed points |
| Time Debt screen removed (7 Oct) | Duplicated elsewhere; nothing to act on |
| History's day view is a words-first recap (7 Oct) | The calendar of actual blocks was unreadable |
| Weekly plan vs actual as per-type progress rows, no legend (7 Oct) | Paired bars and a two-color legend confused the per-type colors |
| Calendar hour height 80dp (7 Oct) | Room for break labels and handles |
| Alerts: 5-minute warning, end, then every 5 minutes until a reaction; Pomodoro breaks (11 Oct) | Roni: the app was too quiet to notice |
| Alerts sound even with the app open; edits stay silent (11 Oct) | A screen that's on isn't a screen that's watched |
| Tap any block of today: past blocks corrected live, the running block's end and type editable (11 Oct) | Roni: "changing the blocks themselves is impossible" |
| Day waits for its first block; start per template; pick from midnight (13 Oct) | Picking at 00:30 started a 9-hour countdown |
| Profiles, own templates and categories are 3.x, one version per task (13 Oct) | Generalizing beyond Roni's four days; data model changes, hence 3.0.0 |
| Templates tab; list + sheet editor; edits push later blocks, never earlier ones; blank + delete own; built-in blocks read-only; one template per weekday; 12-colour palette; today follows only while waiting (13 Oct) | Roni's 3.1 questionnaire |
| Free time in a template may be shorter than 10 min (3.1.0, `TemplateRules`) | Otherwise a 5-minute gap left by an edit would make the template "broken" |
| Steps while driving are **shown, never subtracted** (3.4.0, Roni 13 Oct 22:33): Activity Transition API IN_VEHICLE enter/exit, step counter read at both, `DriveLog` per date; History "· about 300 while driving", a Weekly tile, an Insights row. Reverses only the "don't detect drives" part of the row below | Roni wants to see how much of the count is car bumps without touching his total |
| Steps stay as they are: no car-step subtraction (activity recognition), no offset for steps without the phone (13 Oct) | Discussed: car steps are usually dozens–hundreds per drive, the phone-on-desk undercount is larger and varies by day; a fixed correction would be fake precision. A watch is the real fix if he ever wants accuracy |
| Home's today-edits get the editor's sheet in 3.2.0 (13 Oct) | Roni couldn't find Add on non-free blocks or Delete at all |
| No foreground service yet (11 Oct) | The silent alarms were the cause; revisit only if alerts come late |
| Next before the next block's time starts it now, keeping its length; free time in between is skipped (11 Oct) | Planned times on an early block gave a 327-minute countdown |
| Walks ring like a timer, with vibration (11 Oct) | The phone is in a pocket on a walk; the only exception to the no-vibration rule |

## 13. Glossary

- **Block:** a timed slot of one type (CV, Coding, Gym, Free, Kid time, Dishes, Evening choice).
- **Work block:** CV, Coding or Gym. Pause, "finish early → free time" and edit handles apply to these.
- **Today's plan** (`TodayPlan`, 2.0.0): today's own list of blocks, copied from the template at the start or at a swap, then changed by edits.
- **Edit mode** (`BlockEdit`): what a block offers. `FULL` (both handles and type), `END_ONLY` (the current block), `TYPE_ONLY` (later free time), `NONE`.
- **Next slot:** the advance action, allowed at any time.
- **Ran over** (`leak` in code): time past a block's planned end before Next was tapped. Shown in red. (It used to be called "time debt".)
- **Missed** (`CONSUMED` in code, with a `MissReason`): a planned block that never happened, because the day started after it (`LATE_START`) or an earlier block ran past its end (`OVERRUN`).
- **Under-time:** time left in a work block when Next was tapped early.
- **Synthetic Free:** the free time made from that leftover. It ends by itself at the block's planned end.
- **Pause time:** paused minutes inside a block. Tracked, but it doesn't count as ran over.
- **Plan followed:** minutes actually worked in CV, Coding and Gym ÷ their planned minutes (capped at 100%).
- **Emergency stop:** the day ended early via Emergency; flag `EMERGENCY_STOP`.
- **Swap:** replacing the rest of today's plan with another template, from now. Flag `TEMPLATE_SWAPPED`.
- **Plan changed** (`PLAN_EDITED`): the day's plan was edited.
- **Recap:** History's day view, and Home after the day ends. A summary card and one row per block, in words.
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
| **Major** (2.0.0) | A new core concept that changes how the app is used, or a stored-data change that can't be migrated automatically | Editable days (Task-7) |
| **Minor** (1.5.0) | A task that adds features or changes behavior, with stored data migrated automatically | Task-5 (1.4.0), Task-6 (1.5.0) |
| **Patch** (1.5.1) | Bug fixes only; no behavior change | Task-6-fixes (1.5.1) |

- One task = one bump. A big task is still one minor, not several; a fix-only follow-up is a patch.
- `versionName = "MAJOR.MINOR.PATCH"`, `versionCode = major * 10000 + minor * 100 + patch` (2.0.0 → 20000). It must always increase, or Android refuses to install the new release over the old one.
- Every bump adds an entry to `CHANGELOG.md` (repo root, newest first) and a git tag `vX.Y.Z` on `master` after the merge.
- Settings shows `V {versionName}` from `BuildConfig`; tests read `BuildConfig.VERSION_NAME`, never a literal.
- The header line of `schedule-spec.md`, `architecture.md` and `visuals.md` says which app version they describe; update it with each bump.
- History: 1.0.0 was Task-1. Tasks 2–4 were never bumped, so Task-5 recorded them retroactively as 1.1.0, 1.2.0 and 1.3.0 and shipped as 1.4.0. Task-6 shipped 1.5.0, Task-6-fixes 1.5.1. Task-7 targets 2.0.0.

## 15. Testing policy (from Task-6)

Roni felt the tasks had become test-heavy, and those layers missed the bugs that mattered. From Task-6 on:

| Kind | Policy |
|---|---|
| JVM unit tests for logic (state machine, engine, orchestrator, mappers, ViewModels) | **Required** for new rules, plus one regression test per logic bug, failing before the fix. The task names them. |
| Pure logic handed over as code (e.g. `PlanEditor`) | The planning agent compiles it and runs its named tests before handing it over, and marks it *verified* (§17). |
| The existing suite | Stays green. The recomposition tests stay: performance is rule #1. |
| UI tests | Only ones that check behavior (which buttons, what a tap does). Layout-only checks and Roborazzi screenshots were removed in 2.1.0: Roni never looks at screenshots ("all I do is install the app and play around with it"). Planning agent (13 Oct): prefer ViewModel/JVM tests; a Compose test only for behavior nothing else can check. Task-22b's ExplainerDialogTest was dropped for this reason; the 3.1.1 picker no-overlap bounds test was a layout check that shouldn't have been asked for. |
| Device tests D1–D14 | Kept. Roni runs them when notifications, navigation or process behavior change. Not a gate for the working agent. |
| On the phone | Each release adds a short "Check on the phone" list to `MANUAL_TESTS.md`. The working agent uses `adb` with the debug app when the phone is connected. |

## 16. Documentation conventions (from Task-6)

Roni asked whether big comments in every file the agent touches (how it works, a history of changes, what it depends on) would help future agents. What was agreed:
- **`docs/CODEMAP.md`** (in the repo root until 2.1.0): one row per source file (what it does, what it talks to). A fresh agent reads one file instead of opening dozens. It's updated in the same commit whenever a file is added, removed or repurposed.
- **Short KDoc headers on core classes only:** the responsibility, the 1–3 rules that must stay true, and "Talks to: …".
- **No change history in code comments:** it goes stale and duplicates git. The *why* goes in commit messages, `NOTES.md` and `CHANGELOG.md`.
- Spec and task files are committed in the repo root.

## 17. How to continue (planning agent)

1. Read this file, then the three specs. Tell Roni in a few sentences what you understood, so he can correct anything.
2. When reviewing a working agent's report, compare it with the task's slices and named tests, not with its claims. Read `NOTES.md` and `issues.md` for wrong conclusions, and correct them in the next task.
3. Decisions: questionnaires (5 options plus "Other"). Recommend one option when Roni asks.
4. Writing `Task-N.md`:
   - It overrides earlier docs and states its target version (§14).
   - Use the real names from `CODEMAP.md` (ask Roni for the current one if it isn't attached).
   - Compile-gated slices, ordered by what hurts Roni most; independent slices before risky ones.
   - Exact strings and tags; near-complete code for anything layout- or concurrency-heavy.
   - Tests per §15: named, logic-level, failing before the fix; plus an explicit list of tests to delete when behavior is removed.
   - The time-box rule.
   - A slice map near the top: each slice's line range in the file, the files to start in (from `CODEMAP.md`) and where its tests go, so the agent reads only what it needs and doesn't search.
   - Ship the heaviest pure logic as ready-made files Roni drops into the repo (compiled and tested first, §15), instead of code blocks the agent has to retype.
   - A "Check on the phone" list for `MANUAL_TESTS.md`.
   - A final report covering every slice.
5. **Verify pure logic before handing it over.** The planning workspace usually can't reach Maven, but Gradle's distribution ships a Kotlin compiler (`kotlin-compiler-embeddable-*.jar` with `kotlin-stdlib`, `kotlin-reflect`, `kotlin-script-runtime`, `kotlin-daemon-embeddable`, `trove4j` and `annotations` in Gradle's `lib/`). Run `org.jetbrains.kotlin.cli.jvm.K2JVMCompiler` with them on the classpath, mirror the few model types the code needs, and run the named tests from a `main`.
6. After each decision or task, update `schedule-spec.md`, `architecture.md`, `visuals.md` (including the header version line) and this file (§8 history, §9 state, §12 decisions).
7. Give Roni the working agent prompt (§18) with the task number and version filled in.
8. **GitHub:** attaching `RoniBasarab/BlocksApp` failed twice (no access for the session's GitHub credential). Don't retry it the same way. Ask Roni to attach files, or to install the Claude GitHub app on his account if he wants repo access. Keep the repo private: the specs describe his family's routine and his child's pickup times.

## 18. Prompts

**Working agent:** the instructions live in `docs/AGENT.md`, so Roni's whole prompt is:

```
Read "C:/Blocks App/docs/AGENT.md" and do what it says.
```

Task-19 has parts, one prompt each (Track A and B at the same time, in two agent windows):

```
Read "C:/Blocks App/docs/AGENT.md" and do the Base of Task-19.
Read "C:/Blocks App 19a/docs/AGENT.md" and do Track A of Task-19.
Read "C:/Blocks App 19b/docs/AGENT.md" and do Track B of Task-19.
Read "C:/Blocks App/docs/AGENT.md" and do the Finish of Task-19.
```

The planning agent updates `AGENT.md` for each task (task name, branch, version, verified files). It encodes every lesson so far: grep first and never web-search the repo, no scratch files in the repo, the PowerShell 5.1 rules, the error-only Gradle command, reading test results instead of rerunning, `./gradlew --stop` after 10 minutes, no waiting on anything external, few big steps per slice, at most two sentences of thinking between commands, a 15-minute time-box, and resume-from-git-log. The same line restarts a fresh session after a quota stop.

**Planning agent** (a fresh Claude chat; attach `SUMMARY.md`, `schedule-spec.md`, `architecture.md`, `visuals.md`, the latest task files, and the repo's `CODEMAP.md`, `NOTES.md` and `issues.md`):

```
You're the planning agent for Blocks, my personal Android time-block app. You're taking over from a previous planning session; everything it knew is in the attached files.

Attached: SUMMARY.md (the handoff; read it fully first), schedule-spec.md, architecture.md and visuals.md (the specs), the latest task files, and the repo's CODEMAP.md, NOTES.md and issues.md.

How we work:
- You don't write code. A separate working agent implements task files in my repo, and I bring you its reports.
- Decisions go through multiple-choice questions, 5 options plus "Other", as interactive forms if your tools allow it. When I ask what you think, recommend one option and say why.
- Your deliverables are Markdown files: task files, updated specs and an updated SUMMARY.md.

First, tell me in a few sentences what you understood about the project and where it stands, so I can correct anything. Then continue from SUMMARY.md §11.
```
