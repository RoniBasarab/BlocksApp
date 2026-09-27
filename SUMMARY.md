# Blocks: Project Summary (handoff)

Last updated: Sunday 27 September 2026. Written so a model with no prior context can continue the project.

---

## 1. What this is

**Blocks** is a personal Android app (`com.roni.blocks`), sideloaded, never on Google Play, with exactly one user: Roni. It turns his weekday into timed blocks (CV sending, coding study, gym, free time) around fixed childcare times. It tracks "leak" when a block runs over, and stays completely silent in the evening and at night.

Why it exists: Roni is job-hunting and found his unstructured days drifting (lost evenings to doomscrolling). The app provides structure without being rigid: it logs deviations instead of punishing them.

- Code: `C:\Blocks App` (Windows), a git repo with a commit per task phase.
- Timezone Asia/Jerusalem. The work week is Sunday–Thursday; the weekend is Friday–Saturday.

## 2. People and roles

| Who | Role |
|---|---|
| **Roni** | Owner and only user. Senior Android/Kotlin developer (~6 years). Reviews the app on his phone and via screenshots, and decides. |
| **Architect** (a Claude chat) | Runs the questionnaires, writes the specs and `Task-N.md` files, reviews the executor's reports. That's your role if you're reading this. |
| **Executor** (a separate coding LLM) | Implements `Task-N.md` in the repo. It is less capable: it tends to call "navigable first cuts" done, skip verification steps, and leave notes that contradict the code. It does best with exact code for hard parts, exact strings, per-phase checkboxes, and **tests that act as the spec**. |

## 3. How Roni likes to work

- **Decisions via questionnaires:** interactive multiple-choice forms, 5 options per question plus "Other". The answers then go into the spec files.
- **Deliverables are Markdown files** he can hand to the executor ("execute Task-N.md").
- **Performance is priority #1:** as few recompositions and skips as possible.
- **No over-engineering:** his own MVI-lite pattern (below). No reducers, no use cases, no clean-architecture layers.
- **Wants honest pushback with reasons.** He usually accepts well-argued alternatives quickly.
- He writes fast and casually, sometimes by voice dictation, so expect typos. Interpret the intent.
- He cares about battery. No vibration anywhere; sound only in the daytime segment; dead silence after the day ends.
- Family context that shapes the schedule: a young child in kindergarten; drop-off before 08:00; pickup 16:20 on Sun/Tue/Wed; grandparents take the child Mon/Thu until ~19:00; his wife usually runs the bedtime ritual, sometimes with Roni.

## 4. Documents (read in this order)

| File | What it is | Status |
|---|---|---|
| `SUMMARY.md` | This file | Current |
| `schedule-spec.md` | **Behavior**: templates, times, leak/pause/emergency rules, evening flow, notifications | Current (source of truth for behavior) |
| `architecture.md` | **Code**: packages, MVI pattern, managers, storage, alarms, notifications, performance techniques | Current (source of truth for tech) |
| `visuals.md` | **Look**: palette, block colors, screen layouts | Current, but Task-3/4 override parts |
| `Task.md` | Initial build (12 phases) | Done |
| `Task-2.md` | Styling + gap fixing; detailed layouts for all screens (§D–§G still referenced) | Mostly done; screen layouts incomplete |
| `Task-3.md` | Perf harness, theme fixes, Home calendar, template picker | Done (report 27 Sep) |
| `Task-4.md` | Single notification, self-explaining NowCard, screen polish, UI + behavior tests | **Next to execute** |
| `NOTES.md` (in repo) | Executor's decision log | Contains at least one contradiction (see §8) |
| `MANUAL_TESTS.md` (in repo, after Task-4) | Manual device checklist for Roni | Pending |

When a decision changes, update the spec files too, not only the next task. The specs must never contradict each other.

## 5. Product rules (quick version; details in `schedule-spec.md`)

**Templates**

| Template | Blocks |
|---|---|
| Monday / Thursday ("Gym day") | CV 9:00–9:30 · Coding 9:30–11:30 · Free 11:30–12:00 · Gym 12:00–14:00 · Free 14:00–16:20 · Coding 16:20–17:20 · Free 17:20–19:00 · Kid time from 19:00 |
| Sunday / Tuesday / Wednesday ("Focus day") | CV 9:00–9:30 · Coding 9:30–11:30 · Free 11:30–14:00 · Coding 14:00–15:30 · Free 15:30–16:20 · Kid time from 16:20 |
| Friday / Saturday ("Family day") | Kid time 9:00–13:00 · Free 13:00–15:00 · Kid time from 15:00 |
| Off day | No blocks, no notifications, counted in the weekly summary |

Coding blocks use Pomodoro 50/10. Phase switches are automatic and chime.

**Day lifecycle**
- 09:00: prompt to pick a template, re-prompted every 15 minutes until 10:00.
- 20:00: the evening prompt ("Kid asleep" / "End day").
- "Kid asleep" starts the evening: Dishes (20 min), then a forced choice of Game / Project / Read / Chill until 22:30.
- The day ends at 22:30 or on "End day". Ending the day flushes history and time debt, cancels alarms, and goes fully silent.
- If "Kid asleep" is never tapped, the day auto-ends at 22:30 and is flagged `EVENING_SKIPPED` (not a failure).

**Time accounting**
- Block **starts** are automatic. Block **ends** need a manual advance, except synthetic Free, Pomodoro phases, and the switch into Kid time.
- **Leak:** time past a block's end before advancing. The next block keeps its planned end and shrinks. If it's fully eaten, it's marked `CONSUMED`.
- **Finish early:** the leftover becomes a synthetic Free block; under-time is logged.
- **Pause:** the current block's end moves later by the paused time, so the next block shrinks. It counts as debt.
- **Emergency (internally "Interrupt"):** freezes the whole day; not debt. On return, choose "continue where I was" (the day shifts) or "jump to now" (missed blocks are marked skipped).
- **Gym missed:** the Gym block was consumed, interrupted, or ended with less than 30 minutes of actual time.
- Time debt is per day and flushed at day end.

**Notifications (after Task-4)**
- One notification with a system-drawn countdown; no app wake-ups to update it.
- The `day` channel plays a sound on transitions; the `evening` channel is silent. No sound while the app is on screen.
- Tapping it resumes the app exactly as it was, and does nothing if the app is already open.
- Action buttons change state without opening the app. Stale or double taps are ignored.

## 6. Architecture (quick version; details in `architecture.md`)

- **Stack:** Kotlin, Jetpack Compose, Material 3, dark only, English only, minSdk 34, Hilt, Navigation 3, SharedPreferences + kotlinx.serialization, kotlinx-datetime, kotlinx.collections.immutable, AlarmManager exact alarms (`USE_EXACT_ALARM`). No Room, DataStore, Gson, WorkManager or foreground service.
- **Pattern per screen:** `@Immutable` State data class, sealed `Action` / `Effect`, `@HiltViewModel` with `onAction()`, updates only via `_state.update { copy() }`, collected with `collectAsStateWithLifecycle`. Composables only render. Logic lives in `@Singleton` managers injected into ViewModels.
- **Day state machine:** in `core/daystate/`. A pure `DayStateMachine.transition()` plus a `DayOrchestrator` singleton that persists before side effects and is shared by UI, alarms and notification actions. `SegmentBook` records what actually happened.
- **Scheduling:** exactly one pending exact alarm, always recomputed as "next wake-up".
- **Performance technique:**
  - High-frequency values (minute tick, leak seconds, progress) never go into screen state. They're passed as lambdas and read in the draw phase.
  - The calendar is a `drawWithCache` grid, a custom `Layout` for blocks, and draw-phase overlays for the now line and leak.
  - Recomposition counters (`RecompositionCounter` + `SideEffect`) with Robolectric tests prove that ticks don't recompose.
- **Navigation:**
  - Template picker (full screen) when the day hasn't started; otherwise Home.
  - Bottom bar: Home · Weekly · Insights.
  - Top-right burger menu: History · Time Debt · Settings.
- **Builds:**
  - `debug` has the `.debug` suffix (from Task-4).
  - `benchmark` has its own suffix and is used for Macrobenchmark.
  - `release` is Roni's daily app, signed via an optional `keystore.properties`, falling back to the debug keystore.

## 7. Visual direction

- Dark, calm, low saturation.
  - Page background: a vertical gradient from `#27282C` to `#1C1B19`, with faint static glows (steel blue top-right, sand bottom-left).
  - Cards: `#312F2C` with a top-lit gradient border.
  - Accents: steel blue `#7A9CC6` (primary) and sand `#D9C38A` (secondary). Leak and error: `#D97070`.
- Each block type has its own muted hue (a container plus an accent), consistent everywhere.
- Roboto, with tabular figures for times.
- Corner radii: blocks 10dp, cards 16dp, hero cards 20dp.
- The template picker has a slowly drifting "aurora" with a parallax dust layer. It's the only infinite animation in the app.
- Home looks like a Google Calendar day view:
  - Hours 08:00–23:00 at 64dp per hour.
  - The current block is lifted; other blocks sit at 60% alpha.
  - A red now line runs inside the current block, and a red overflow segment shows a leak.
  - A floating NowCard sits at the bottom.

## 8. Current state (after Task-3, 27 Sep 2026)

**Working, per the executor's report:**
- The state machine and schedule engine, with segments persisted.
- Alarms, receivers, and reboot/time-change recovery.
- The design system, edge-to-edge insets, splash screen and adaptive icon.
- Home: grid, blocks, emphasis, now line, leak overflow, auto-scroll, block detail sheet, evening sheet, resume dialog.
- Template picker: aurora, 2×2 cards, suggested template.
- Tooling: stability config and compiler reports, the `:macrobenchmark` module, Roborazzi screenshots in `screenshots/`.
- 53 JVM tests, all green.

**Versions in use (from `NOTES.md`):** AGP 9.4 with built-in Kotlin, Kotlin 2.2.10, KSP 2.3.12, kotlinx-datetime 0.8 (`kotlin.time.Clock`/`Instant`, needs `-opt-in=kotlin.time.ExperimentalTime`), Material3 1.4.0 (no `sheetGesturesEnabled`), core-splashscreen 1.2.0, Roborazzi 1.75.0, material-icons-extended.

**Roni's feedback after Task-3 (all addressed in Task-4):**
- Home is "ok-ish". Weekly, Insights, History and Time Debt are misaligned, off-center and empty-looking. Settings is left-aligned and looks bad.
- There were two notifications. Tapping the "NEXT" action produced a second notification, which felt awkward. He wants one notification with a countdown.
- The NowCard is unintuitive: "Next" doesn't say next *what*; icon-only buttons are unclear; "Interrupt" isn't explained; Pause vs Interrupt is unclear.
- Tapping the notification while the app is open must do nothing. No extra screens should pop up.

**Known open problems:**
- `NOTES.md` says receivers finish `goAsync()` immediately, but the Task-2 report says this was fixed. Task-4 §1.6 makes the executor verify.
- Macrobenchmarks haven't been run yet (they need Roni's phone: `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`).
- Compiler reports: only the three ViewModels are unstable, which is harmless because they're never passed as parameters.

**Deviation from Roni's wording:** he asked for the notification to show "minutes left, updated once a minute" to save battery. The architect chose the system chronometer countdown instead. It never wakes the app (per-minute updates would mean ~800 wake-ups a day, throttled by Doze), and it naturally shows negative time during a leak. If Roni objects, switch to a per-minute text update.

## 9. Next step

Hand `Task-4.md` to the executor. It adds:
1. The single notification: a pure `NotificationModelMapper` plus a `NotificationRenderer`, launcher-style tap, stale-action protection, a debug time-travel receiver, and the `.debug` suffix.
2. The self-explaining NowCard: labeled buttons, "Start *Next block*", confirmation dialogs with one-sentence explanations, and an ⓘ info dialog.
3. Polished Weekly, Insights, History, Time Debt and Settings, built from shared primitives (`ScreenColumn`, `EmptyState`, `SectionCard`, `SettingsRow`).
4. Tests written by the architect that the executor must pass unmodified:
   - UI geometry/expectation tests (centering, 16dp gutters, row heights, button labels, dialogs).
   - Notification renderer and mapper tests.
   - Orchestrator stale/double-tap tests.
   - UiAutomator device tests D1–D8 (Roni runs them) and a manual checklist D9–D12.

After Task-4, review the executor's report and the `screenshots/` folder with Roni. Next candidates: polishing the Weekly chart and Insights visuals, running the macrobenchmarks, and acting on their numbers.

## 10. Ideas raised but not agreed (don't build without asking)

Block customizer screen (templates are a bundled JSON for now) · export/backup of history (JSON/CSV) · daily score / streaks · home-screen widget · deeper pattern detection (the current insights are the scorecard metrics).

## 11. Decisions log (settled; don't reopen without a reason)

| Decision | Why |
|---|---|
| Native APK, sideloaded; Play Store rules ignored | Personal tool |
| SharedPreferences, not Room | Small, document-shaped data; one record per day |
| kotlinx.serialization, not Gson | Gson breaks with Kotlin nullability, sealed classes and R8 |
| Exact alarms, no WorkManager, no foreground service | On-time block ends; minimal battery |
| One pending alarm at a time | Trivial cancel, always derived from the true state |
| One notification, system chronometer | Roni's "single notification"; zero wake-ups |
| State machine outside ViewModels | Alarms and notification actions need it without UI |
| Ticks as lambdas and draw-phase reads | Performance rule #1 |
| Canvas-drawn calendar and charts; no chart libraries | Performance, full control |
| LazyColumn allowed only for History | The calendar is a fixed, short range |
| Hilt, Navigation 3, single module organized by feature | Roni's choices |
| Template picker every morning, manual start | The day is too variable for auto-start |
| No vibration anywhere; silence after the day ends | Battery and family evenings |
| `.debug` / `.benchmark` app ID suffixes | Tests never touch the real day's data |

## 12. Glossary

- **Block:** a timed slot of one type (CV, Coding, Gym, Free, Kid time, Dishes, Evening choice).
- **Effective end:** planned end plus paused time, shifted by any emergency "continue where I was".
- **Leak:** time past the effective end before advancing. It's shown in red, and adds to time debt.
- **Time debt:** a day's total leak and pause time, flushed at day end.
- **Synthetic Free:** the Free time created when a block finishes early or the CV slot is empty.
- **Consumed:** a block completely eaten by a leak.
- **Emergency / Interrupt:** a full-day freeze that isn't debt.
- **Kid time:** a quiet period with no alerts. The evening starts on "Kid asleep".
- **Segment:** a record of what actually happened (planned, synthetic Free, pause, interruption, consumed).
- **NowCard:** the floating control card at the bottom of Home.

## 13. How to continue (for the next architect)

1. Read the executor's latest report, `NOTES.md` and `screenshots/`. Compare them against the task's checkboxes and tests, not against the executor's claims.
2. For open decisions, ask Roni with a 5-option questionnaire.
3. Write the next `Task-N.md` in the same format:
   - It overrides earlier docs where they differ.
   - Phases in order, each with checkboxes.
   - Exact strings and tags.
   - Near-complete code for anything layout- or concurrency-heavy.
   - Tests written by you that must pass unmodified.
   - A final report that lists every checkbox.
4. Update `schedule-spec.md`, `architecture.md` and `visuals.md` whenever a decision changes, then update this summary.
