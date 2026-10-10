# Daily Block Scheduler — Schedule Spec

Describes app version **2.1.0** (the target of `Task-8.md`)

---

## 1. Global rules

| Rule | Behavior |
|---|---|
| Day start | 9:00 notification to pick a template, every day including Fri/Sat. Re-notifies every 15 min until 10:00 if not tapped. |
| Template choice | Picked once per day; the picker opens only while today hasn't started. Picking late lands on the block the clock is in; the blocks before it are **missed** (not ran over). From 22:30 nothing starts ("Too late to start"). **Swap** (the button on Home) re-plans the rest of today from now with another template: what already happened stays, the new template's blocks that already ended are left out, and the block containing now starts now. If the day has ended it asks first; after 22:30 it's refused. Settings → Reset today starts the day over. |
| Day end | 22:30, or earlier via **End day** button (in-app and on the 20:00 notification). |
| On day end | History flush (the day's final plan and what happened), cancel all pending alarms, then dead silent until next 9:00 prompt. |
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
| Overrun (leak) | Counter goes negative until **Next slot** is tapped. The next block keeps its original end time and shrinks. If fully eaten, it's **missed** (`CONSUMED`, reason `OVERRUN`). |
| Late start | Blocks that ended before the template was picked are **missed** (`CONSUMED`, reason `LATE_START`); the block containing that moment starts then and keeps its planned end. Nothing counts as ran over. |
| Next slot early | **Work block** (CV, Coding, Gym): ends now (under-time logged) and the rest becomes free time (synthetic Free) until its planned end; the next block starts on schedule. **Free block** (planned or synthetic): the next block starts **now**, keeping its planned end. |
| Pause (bathroom, calls) | Work blocks only. Stops counting worked time. **No end time moves**: the current block still ends on time, so the pause shortens the current block only. Auto-closes at the block's end. Logged as pause time; **not** debt. |
| Automatic transitions | Block **starts** are automatic. Synthetic Free ends at its planned end, Pomodoro phases switch, and Kid time begins automatically (no Next, no leak). Every other block **end** needs a manual Next slot. |
| Dishes | Ends when an evening choice is picked. The choice sheet appears when Dishes' 20 min are up. |
| Gym missed | Gym counts as missed if it never happened (missed), was cut by an emergency stop, or ended with under 30 min of actual (non-paused) time. |
| Emergency | Ends the day immediately ("The day has been disrupted by an emergency. Stop all schedules for today?"). Flagged `EMERGENCY_STOP`; nothing after it counts as planned time or debt. |
| Ran over | Overruns only (pauses excluded), per day, measured against the day's final plan; saved with the day at day end (midnight fallback). The separate Time Debt screen and records were removed in 2.0.0. |
| Swap template | Segments recorded so far stay, and the open one closes at now. Today's plan keeps its blocks up to now (up to the end, if the day had ended), then takes the new template's blocks from now: the one containing now starts now and keeps its planned end; earlier ones are left out (not missed). Flag `TEMPLATE_SWAPPED`. |
| Edits | §9. Ran over and missed are measured against the edited plan. |

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

- History records the day's final plan and what actually happened (segments): actual start/end, ran over, under-time, paused ranges, device-off downtime, missed blocks with their reason, emergency stops.
- A past day is shown as a **recap**: a summary card and one row per block, everything in words (Task-7 Slice 5).
- Categories tracked: blocks per type, leak, under-time, pause time, missed gym, evening skipped, evening choice (incl. Chill), off days, emergency stops.
- Pattern detection: consistent overruns per block type, missed gym, evenings skipped, pause time.
- Day flags: `OFF_DAY`, `EVENING_SKIPPED`, `GYM_MISSED`, `AUTO_ENDED`, `EMERGENCY_STOP`, `TEMPLATE_SWAPPED`, `PLAN_EDITED`.

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
- Screens: Template picker (full screen), bottom nav **Home · Weekly · Insights**, top-right menu **History · Settings** (version label `V {versionName}`, bottom-right; versioning in `SUMMARY.md` §14). Full details: `architecture.md` §13 and `visuals.md`.

## 9. Today's plan and editing (2.0.0)

Each day has its own plan, copied from the template when the day starts (or at a swap). Edits change today only; templates stay as they are.

| Topic | Rule |
|---|---|
| Selecting | Tap a block. What it offers depends on where it is: later work blocks get both handles and a type choice; later free time gets a type choice only; the current work block gets its bottom handle only; past blocks, kid time and the evening get nothing. |
| Dragging | A handle moves its edge in 5-minute steps. Work blocks are at least 10 minutes long. |
| Growing | A block that grows takes the free time next to it first. Work blocks in the way move along, keeping their length, until free time absorbs the change. Kid time never moves; a push stops there. |
| Shrinking | The freed time becomes free time (merged with free time next to it). |
| Starting earlier | Never into the past or the next 5 minutes; the current free time can shrink down to then. |
| Changing type | Any later block: CV, Coding, Gym or Free. A block turned into Free merges with the free time around it. Coding gets the 50/10 Pomodoro. |
| The current block | Only its end moves, and not into the next 5 minutes. Its start and type are fixed (Next switches to something else). After Next ended it early, it's fixed. |
| Blocks that will be missed | A later block that ends before it could start (an earlier block ran over) can't be edited. |
| Effects | Silent. The alarm and the notification's countdown follow; ran over is measured against the edited end. Flag `PLAN_EDITED`. |
| Not in 2.0.0 | Undo, moving a whole block, handles on free time, adding or splitting blocks, editing templates. |


## 10. 2.1.0 additions

| Topic | Rule |
|---|---|
| Evening on screen | Kid time ends when Kid asleep is tapped; Dishes (20 min) and the evening choice (until 22:30) are blocks on the calendar, and the card counts down to the current one's end. |
| Add a block | Tap later free time → "Add a block": CV, Coding or Gym takes its first 30 minutes (the whole block if it's shorter). Then drag to size it. |
| Locked blocks | A tap on a block that can't be edited says why, in one sentence. |
| Correcting a day | From a recap (History, or Home after the day ends): move when a block ended (never into the next one, at least 5 minutes long), change what it was (CV, Coding, Gym, Free), or mark a missed block as having happened at its planned times (blocks overlapping it are shortened; it never swallows a whole block). Kid time and the evening stay as recorded. The plan is unchanged; the day gets `CORRECTED`. |
| Check-in | When the day has ended, the recap asks "How was today?": Good / OK / Rough, or Skip. Stored on the day, shown in History. |
| Widget | Home screen: the current block and a system-drawn countdown, updated only when the notification is. Tap opens the app. |
| Debug | Developer tools dialog: the clock moves both ways; "Test a day" restarts today on a template at a chosen time. |
