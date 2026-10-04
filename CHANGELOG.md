# Changelog

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
