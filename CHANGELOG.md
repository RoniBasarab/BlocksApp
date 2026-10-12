# Changelog

## 2.6.2
- After the day blocks, the top bar shows what happened ("3 done · 5 missed") instead of blocks left; "leak" now reads "ran over".
- Fixed: the empty band under every screen title
- History: a day's steps and walks are at the top

## 2.6.1
- Short blocks never clip their text (it adapts to the phone's font size)
- Fixed: the Off day card's stray shadow

## 2.6.0
- Fixed: the walk timer (it showed 29862554:24), Turn back, and the walk icon in the top bar.
- The calendar never overlaps: very short blocks become a thin bar with a label pill ("CV · 1 min"), short walks a "Walked 3 min" pill, and block titles move out of their way.
- Next goes to the very next block again; nothing is skipped or removed.
- Permissions are asked at launch; the step card on Home is gone.

## 2.5.0
- Steps: Blocks reads the phone's step counter (no extra battery) and shows today's steps on Home, each day's total in History, each walk's steps, and the week's total in Weekly.
- Fixed: a second walk right after the first showed the old walk's times, and End walk started another walk.
- Fixed: the walk card counts to the second; Turn back counts the real way home.
- Short walks show as a "Walked N min" line inside the block, not as a tiny block.
- Evening: kid time stays as it was, then a 20-minute Dishes block (editable, skippable), then your activity, which starts by itself and can be switched during the evening.

## 2.4.0
- Walks at any time: the walk icon in the top bar starts one in kid time, the evening, after the day ends, or after midnight. Same rings and walk card; the day itself isn't touched.
- History lists each day's walks.

## 2.2.1
- Fixed: the "done early" card and the notification said "then then" before the next block.
- Fixed: the running notification now names the next block for every block type, not only work blocks.
- Fixed: correcting a past block kept closing the sheet after one ±5; it stays open until Done.

## 2.2.0
- Sounds that get noticed: 5 minutes before a block ends, at the end, and every 5 minutes until you react; Pomodoro breaks too.
- Done early: the screen and the notification show your free time and the real next block; the Pomodoro no longer restarts.
- The notification shows the Pomodoro round and the time to the break.
- Tap any block of today: fix past blocks right away, change the running block's end and type, ±15 steps.
- Fixed: the widget wouldn't load.
- Fixed: after moving the debug clock, minutes left were wrong for up to a minute.
- Locked blocks explain why in a dialog instead of a cut-off message.

## 2.1.0
- Fixed: in the evening Home showed a day block with a huge negative timer. The evening (Dishes, then your choice) now shows on the calendar with time left.
- Fixed: Weekly keeps its arrows on an empty week.
- Add a block: turn the start of free time into a 30-minute CV, Coding or Gym block.
- Correct a past day from its recap: when a block ended, what it really was, and missed blocks that happened after all.
- Evening check-in: Good / OK / Rough, shown in History.
- Home-screen widget with the current block and time left.
- Tapping a block that can't move says why.
- Developer tools in one dialog, with "Test a day" and moving the clock back.
- Screenshot and layout-only tests removed; the repo root is tidy and the docs live in docs/.

## 2.0.0
- Edit today: drag a block's top or bottom edge to change its times; the blocks after it move along, free time absorbs the change, and kid time stays put.
- Change a block's type, for example turn free time into Coding. Changes apply to today only.
- Picking a template late starts on the block the clock is in; earlier blocks count as missed.
- Coding blocks show their focus and break parts, with the time left.
- History's day view is now a recap in plain words; Home shows it after the day ends.
- Weekly shows plan vs actual per type as progress bars.
- Time Debt removed; its numbers live on Weekly, Insights and History.
- Developer clock controls redesigned.
- Stored data: today's plan is saved on its own; older saves migrate automatically.

## 1.5.1
- Unit tests are deterministic: every coroutine the code starts belongs to the test that started it.
- Settings saves a change before showing it.
- Fixed: a clock or timezone change now catches up on missed blocks, like a restart.

## 1.5.0
- Fixed: the app could stay on the splash screen after Android killed it mid-day, and the notification buttons did nothing then.
- Swap template works in every state, including after the day ended; it re-plans the rest of today from now.
- After the day ends, Home keeps the template title and shows what happened today.
- Lists use the full screen; back gestures fade like tab switches.
- History: cleaner week headers; the day detail no longer overlaps blocks.
- Clearer wording on Weekly, Insights and Time Debt, with a "What these mean" explainer.
- Settings: feedback for every action, and an Alert sound setting.
- Debug builds: clock controls to try any time of day, including the evening.
- CODEMAP.md and class headers for faster onboarding.

## 1.4.0
- Controls simplified: Next slot (finishing a work block early gives free time), Pause (shortens the current block), red Emergency (ends the day). Finish early and freeze/resume removed.
- Cold start never reopens the template picker for a started day.
- Settings fully wired (notification status, test notification, 12/24-hour, reset today, clear history, share log).
- Weekly, Insights, History and Time Debt finished; debug-only demo data.
- Day records keep a snapshot of the planned blocks.

## 1.3.0 (retroactive, Task-4)
- Single notification with system countdown; launcher-style tap; stale-action protection.
- Labeled NowCard with explanations; screen primitives; UI + behavior tests; `.debug` app ID suffix; debug time travel.

## 1.2.0 (retroactive, Task-3)
- Home calendar (grid, blocks, now line, leak overflow), template picker aurora, theme fixes.
- Performance harness: stability reports, recomposition tests, Macrobenchmark module, Roborazzi.

## 1.1.0 (retroactive, Task-2)
- Design system, edge-to-edge, splash, app icon, segment recording, receiver fixes.

## 1.0.0 (Task-1)
- Initial build: state machine, schedule engine, alarms, notifications, storage, first screens.
