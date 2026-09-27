# Daily Block Scheduler — Schedule Spec

Version: v1.0.0 (spec) · Status: locked for architecture phase

---

## 1. Global rules

| Rule | Behavior |
|---|---|
| Day start | 9:00 notification to pick a template, every day including Fri/Sat. Re-notifies every 15 min until 10:00 if not tapped. |
| Day end | 22:30, or earlier via **End day** button (in-app and on the 20:00 notification). |
| On day end | History flush + time-debt flush, cancel all pending alarms, then dead silent until next 9:00 prompt. |
| Fallback flush | Midnight, if the day was never ended. |
| Vibration | Never, anywhere. |
| Sound | Day segment only (9:00 prompt → kid time). Kid time, evening and post-day are silent. |

## 2. Templates

### Mon / Thu — gym days, grandparents take the kid

| Time | Block |
|---|---|
| 9:00–9:30 | CV |
| 9:30–11:30 | Coding (Pomodoro 50/10) |
| 11:30–12:00 | Free (eat) |
| 12:00–14:00 | Gym (travel + train + shower) |
| 14:00–16:20 | Free (post-gym meal) |
| 16:20–17:20 | Coding / project |
| 17:20–19:00 | Free |
| 19:00 → | Kid time (earliest 19:00) |

### Sun / Tue / Wed — kid pickup at 16:20

| Time | Block |
|---|---|
| 9:00–9:30 | CV |
| 9:30–11:30 | Coding (Pomodoro 50/10) |
| 11:30–14:00 | Free |
| 14:00–15:30 | Coding (Pomodoro 50/10) |
| 15:30–16:20 | Free |
| 16:20 → | Kid time |

### Fri / Sat — kid at home

| Time | Block |
|---|---|
| 13:00–15:00 | Free |
| other | Kid / family time |

Start prompt still fires at 9:00.

### Off day

- Zero blocks, zero notifications.
- Counted in the weekly summary.

## 3. Block rules

| Topic | Rule |
|---|---|
| CV days | Sun–Thu only, 30 min. |
| Empty CV slot | Leftover becomes free time. |
| Coding | One generic "Coding" block (no subject split), 2 h in the morning. |
| Pomodoro | 50 min focus / 10 min break inside Coding blocks. Phase switches are automatic (no leak), with a sound alert and an updated notification ("Coding · break 10 min", "Coding · focus 2/2"). |
| Gym | Mon/Thu 12:00–14:00, one block covering travel, training and shower. |
| Missed gym | Logged + feeds pattern detection. No make-up. |
| Non-gym movement | None. |
| Breakfast | Not in the app; day starts at 9:00. |
| Lunch | No block; eaten during free time. |

## 4. Time accounting

| Situation | Behavior |
|---|---|
| Overrun (leak) | Counter goes negative until **Next** is tapped. Next block keeps its original end time and shrinks. If fully consumed, it's logged as "consumed" and skipped. |
| Early end | Logged as under-time **and** the leftover becomes a synthetic Free block until the original end. |
| Pause (bathroom, calls) | Current block's end moves later by the paused duration (you get your remaining time back). The next block keeps its planned end, so it shrinks: the pause "eats" the following block. Logged. Counts as debt. |
| Automatic transitions | Block **starts** are automatic. Synthetic Free, Pomodoro phases and transitions **into** Kid time end/start automatically (no Next, no leak). Every other block **end** needs a manual Next. |
| Dishes | Ends when an evening choice is picked. The choice sheet appears when Dishes' 20 min are up. |
| Gym missed | Gym counts as missed if it was consumed, interrupted, or ended with under 30 min of actual (non-paused) time. |
| Interrupt (real emergency) | Freezes the whole day. On resume: *resume where I was* (day shifts later) or *jump to now* (skipped blocks marked "interrupted"). Logged separately. Not debt. |
| Time debt | Per day; flushed to history at day end (midnight fallback). |

## 5. Kid time and evening

- Kid time: the app goes quiet (Mon/Thu from 19:00, Sun/Tue/Wed from 16:20).
- 20:00: one silent, sticky notification with **Kid asleep** and **End day**. No re-pings.
- Tapping **Kid asleep** starts the evening:
  1. Dishes, 20 min.
  2. Forced choice: **Game / Project / Read / Chill**, running until 22:30.
- Evening end is fixed at 22:30; late taps shrink blocks normally (after 22:10 only dishes remain).
- No tap by 22:30 → day ends silently, logged as **"evening skipped"** (not debt, not failure).

## 6. Notifications

| Channel | Sound | Vibration | Used for |
|---|---|---|---|
| `day` | Yes, on transitions only | No | 9:00 prompt + re-prompts, current block, block ended, Pomodoro switches |
| `evening` | No | No | 20:00 kid-asleep prompt, dishes, evening choice |

- **One notification only**, updated in place, with a system-drawn countdown (no per-second or per-minute app updates).
- No sound while the app is open on screen.
- Tapping it resumes the app exactly where it was; if the app is already open, nothing happens.
- Full details: `architecture.md` §10 and `Task-4.md` §1.

## 7. History and analytics

- History records what actually happened (segments), separate from the template: actual start/end, leak, under-time, synthetic Free blocks, paused and interrupted ranges.
- Categories tracked: blocks per type, leak, under-time, pauses, interruptions, missed gym, evening skipped, evening choice (incl. Chill), off days.
- Pattern detection: consistent overruns per block type, missed gym, evenings skipped, interruption frequency.

## 8. Architecture decisions already made

- Native Android APK, sideloaded (not Play Store). Kotlin, Jetpack Compose, dark mode only, no localization.
- Local only: SharedPreferences, no cloud.
- MVI: immutable state data class updated via `.update {}`, collected with `collectAsStateWithLifecycle`; sealed `Action` interface up via a single `onAction`.
- ViewModels are bridges; logic lives in injected managers (storage, notifications, alarms, …).
- Exception handlers on all flows.
- Minimal recompositions and skips is the #1 priority.
- AlarmManager exact alarms for block ends and prompts.
- **Day state machine** lives at app level (`core/daystate/`), plain Kotlin, app-scoped, exposes `StateFlow<DayState>`, accepts sealed `DayEvent`s, persisted to SharedPreferences on every transition. Every notification path checks it first.
  - States: `NotStarted → Running ⇄ Paused / Interrupted → KidTime → Evening → Ended`, plus `Off`.
- Screens: Template picker (full screen), bottom nav **Home · Weekly · Insights**, top-right menu **History · Time Debt · Settings** (version `V 1.0.0`, bottom-right). Full details: `architecture.md` §13 and `visuals.md`.
