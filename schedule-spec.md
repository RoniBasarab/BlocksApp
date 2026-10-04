# Daily Block Scheduler — Schedule Spec

Describes app version **1.5.0** (the target of `Task-6.md`)

---

## 1. Global rules

| Rule | Behavior |
|---|---|
| Day start | 9:00 notification to pick a template, every day including Fri/Sat. Re-notifies every 15 min until 10:00 if not tapped. |
| Template choice | Picked once per day; the picker opens only while today hasn't started. **Swap** (the button on Home) re-plans the rest of today from now with another template: what already happened stays, the new template's blocks that already ended are left out, and the block containing now starts now. If the day has ended it asks first; after 22:30 it's refused. Settings → Reset today starts the day over. |
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
| Empty CV slot | Tap **Next slot**; the rest of the slot becomes free time. |
| Coding | One generic "Coding" block (no subject split), 2 h in the morning. |
| Pomodoro | 50 min focus / 10 min break inside Coding blocks, counted from the block's **actual** start; the last phase is cut at the block's end. Phase switches are automatic (no leak), with a sound alert and an updated notification ("Coding · Break", "Coding · Focus 2/2"). |
| Gym | Mon/Thu 12:00–14:00, one block covering travel, training and shower. |
| Missed gym | Logged + feeds pattern detection. No make-up. |
| Non-gym movement | None. |
| Breakfast | Not in the app; day starts at 9:00. |
| Lunch | No block; eaten during free time. |

## 4. Time accounting

| Situation | Behavior |
|---|---|
| Overrun (leak) | Counter goes negative until **Next slot** is tapped. The next block keeps its original end time and shrinks. If fully consumed, it's logged as "consumed" and skipped. |
| Next slot early | **Work block** (CV, Coding, Gym): ends now (under-time logged) and the rest becomes free time (synthetic Free) until its planned end; the next block starts on schedule. **Free block** (planned or synthetic): the next block starts **now**, keeping its planned end. |
| Pause (bathroom, calls) | Work blocks only. Stops counting worked time. **No end time moves**: the current block still ends on time, so the pause shortens the current block only. Auto-closes at the block's end. Logged as pause time; **not** debt. |
| Automatic transitions | Block **starts** are automatic. Synthetic Free ends at its planned end, Pomodoro phases switch, and Kid time begins automatically (no Next, no leak). Every other block **end** needs a manual Next slot. |
| Dishes | Ends when an evening choice is picked. The choice sheet appears when Dishes' 20 min are up. |
| Gym missed | Gym counts as missed if it was consumed, cut by an emergency stop, or ended with under 30 min of actual (non-paused) time. |
| Emergency | Ends the day immediately ("The day has been disrupted by an emergency. Stop all schedules for today?"). Flagged `EMERGENCY_STOP`; nothing after it counts as planned time or debt. |
| Time debt | Leak only (pauses excluded), per day; flushed to history at day end (midnight fallback). |
| Swap template | Segments recorded so far stay, and the open one closes at now. The new template's blocks that ended before now are left out (not consumed); the block containing now starts now and keeps its planned end. Flag `TEMPLATE_SWAPPED`. |

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
- Actions: running work block **Pause** + **Next: Free**; free time or an ended block **Next: {block}**; paused **Resume**; evening **Kid asleep** / **End day**. Actions never open the app.
- No sound while the app is open on screen.
- The `day` channel's sound is chosen in Android's channel settings; Settings → Alert sound opens them.
- Tapping it resumes the app exactly where it was; if the app is already open, nothing happens.
- Full details: `architecture.md` §10 and `Task-4.md` §1, with the actions as updated in `Task-5.md` Slice 9.

## 7. History and analytics

- History records what actually happened (segments), separate from the template: actual start/end, leak, under-time, paused ranges, device-off downtime, consumed blocks, emergency stops.
- Categories tracked: blocks per type, leak, under-time, pause time, missed gym, evening skipped, evening choice (incl. Chill), off days, emergency stops.
- Pattern detection: consistent overruns per block type, missed gym, evenings skipped, pause time.
- Day flags: `OFF_DAY`, `EVENING_SKIPPED`, `GYM_MISSED`, `AUTO_ENDED`, `EMERGENCY_STOP`, `TEMPLATE_SWAPPED`.

## 8. Architecture decisions already made

- Native Android APK, sideloaded (not Play Store). Kotlin, Jetpack Compose, dark mode only, no localization.
- Local only: SharedPreferences, no cloud.
- MVI: immutable state data class updated via `.update {}`, collected with `collectAsStateWithLifecycle`; sealed `Action` interface up via a single `onAction`.
- ViewModels are bridges; logic lives in injected managers (storage, notifications, alarms, …).
- Exception handlers on all flows.
- Restoring the day after Android kills the app is single-flight and always finishes (`ensureRestored()`, called by every entry point); the splash never stays longer than 3 seconds.
- Minimal recompositions and skips is the #1 priority.
- AlarmManager exact alarms for block ends and prompts.
- **Day state machine** lives at app level (`core/daystate/`), plain Kotlin, app-scoped, exposes `StateFlow<DayState>`, accepts sealed `DayEvent`s, persisted to SharedPreferences on every transition. Every notification path checks it first.
  - States: `NotStarted → Running ⇄ Paused → KidTime → Evening → Ended`, plus `Off`. Emergency goes straight to `Ended`.
- Screens: Template picker (full screen), bottom nav **Home · Weekly · Insights**, top-right menu **History · Time Debt · Settings** (version label `V {versionName}`, bottom-right; versioning in `SUMMARY.md` §14). Full details: `architecture.md` §13 and `visuals.md`.
