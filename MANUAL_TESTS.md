# Manual device tests (Task-4 §4.6, D9–D12)

D1–D8 are automated in `app/src/androidTest/java/com/roni/blocks/NotificationDeviceTest.kt`.
With a phone attached, run them with:

```bash
./gradlew connectedDebugAndroidTest
```

These four cannot be automated reliably (they depend on the launcher, the reboot flow, the keyguard
and the system DND state), so Roni runs them by hand.

**Check on the phone (2.0.0).** Use the debug app for steps that move the clock (Settings → Developer → debug clock).

*Starting the day*
1. Debug app: Developer → Jump to 09:31 → +1 hour (10:31), then Settings → Reset today and pick Sunday / Tuesday / Wednesday: Home starts on Coding, not on CV.
2. Swipe the app away from recents and open it again: Home appears within 3 seconds, with today's plan.
3. Same, but use the notification's Pause, then Next: both work.

*Editing*
4. Tap the current Coding block: only its bottom handle shows. Drag it down 30 minutes: the free time after it shrinks, and the notification's countdown moves to the new end.
5. On a Mon/Thu, drag Gym's bottom edge down to 17:00: the free time after it disappears, the afternoon Coding slides to 17:00–18:00, and kid time stays at 19:00.
6. Tap a Free block and choose Coding: it becomes Coding with break bands. Drag its edges: the trimmed parts turn into free time.
7. Change a later Coding block to Free: it merges with the free time around it.
8. Swipe the app away after editing and open it again: the edits are still there.
9. Swap the template mid-day: the new template takes over from now.

*During and after the day*
10. The current Coding block shows its focus and break parts, with how much is left.
11. Emergency: Home shows today's recap ending with "Day stopped (emergency)", and the title stays.
12. After the day ends, tap swap: the dialog, then the picker, then tracking restarts from now.

*Screens*
13. History → a day: a list in plain words ("Ran over 47m", "Paused 15m", "Done early", "Missed"), nothing unlabeled, and no gap under the top bar.
14. The History list's chips say "Ran over 18m", and the week headers line up with the cards.
15. Weekly: each type shows "Xh of Yh" with a bar, and there's no legend.
16. The menu has History and Settings; Time Debt is gone. Swiping back from Settings fades.
17. Settings: the debug clock card is tidy with nothing overlapping. Alert sound opens Android's sound settings. Load demo data shows a spinner, then "Demo data loaded: 28 days".
18. Debug: Jump to 20:05 → Kid asleep now → Skip dishes: the evening choice sheet appears on Home.

## Setup for every test

- Install the debug build:

  ```bash
  ./gradlew installDebug      # com.roni.blocks.debug
  ```

- All steps below assume the **Sun/Tue/Wed** template, which is
  `cv 09:00–09:30`, `coding 09:30–11:30` (pomodoro 50/10), `free 11:30–14:00`,
  `coding 14:00–15:30` (pomodoro 50/10), `free 15:30–16:20`, `kidTime 16:20–22:30`.
  CV has no pomodoro, so **any time after 09:30 is the ran-over state**
  (`CV ran over`, action `Next: Coding`).
- Start a day: clear the app's data so no day is running, launch the app, and pick
  `Sunday / Tuesday / Wednesday` on the picker.
- Move the clock with the debug time-travel receiver:

  ```bash
  adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --es to 10:10
  adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --ei plus 30
  adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --ez reset true
  ```

  A jump catches the day up to where the clock now is, applying and recording the transitions it
  skipped, so you can reach the evening in seconds. Only an alarm that arrives with no clock movement
  behind it is ignored.

---

## D9 — Relaunch from launcher after a swipe from recents

- [ ] Start the day so a block notification is posted.
- [ ] Swipe the app out of the recents list.
- [ ] Tap the notification.

**Expected:** a fresh start goes straight to **Home** — never the template picker, no crash, no blank
screen.

## D10 — Reboot mid-block

- [ ] Start the day, then travel to `10:10` (inside the 09:30–11:30 Coding block, focus 1).
- [ ] Reboot the phone.
- [ ] Unlock once the phone is back.

**Expected:** a **silent** notification for Coding (`Coding · Focus 1/2`) is posted after the reboot,
with a live countdown that ticks down. Nothing plays a sound on reboot, and the app never has to be
opened to get the notification.

## D11 — Lock screen

- [ ] Start the day, then travel to `10:10` (mid-Coding).
- [ ] Lock the phone.

**Expected:** the lock screen shows the Coding block and a **live countdown**, and the notification
wakes the screen.

## D12 — Do Not Disturb

- [ ] Turn Do Not Disturb **on**.
- [ ] Start the day, travel to `10:10`, then travel forward to `11:30` so the Coding block ends.

**Expected:** **no sound** when the block ends, but the notification still updates rather than going
stale — the title should become `Coding ran over` with the `Next: Free` action.

---

## Manual check - Emergency ends the day, the notification disappears
- [ ] Start the day and travel to 09:40.
- [ ] Tap Emergency, then the red Stop today.
- [ ] The day ends (Home shows today's recap), the notification is gone, no new alerts fire.

## Manual check - Demo data fills the past
- [ ] Settings -> Developer -> Load demo data (4 weeks).
- [ ] Open History, Weekly and Insights: non-empty, rendered from the seeded days.
- [ ] Clear demo data: the past days disappear again.
