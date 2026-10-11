# Manual device tests (Task-4 §4.6, D9–D12)

D1–D8 are automated in `app/src/androidTest/java/com/roni/blocks/NotificationDeviceTest.kt`.
With a phone attached, run them with:

```bash
./gradlew connectedDebugAndroidTest
```

These four cannot be automated reliably (they depend on the launcher, the reboot flow, the keyguard
and the system DND state), so Roni runs them by hand.

**Check on the phone (2.4.0).** Use the debug app for steps that move the clock (Settings → Developer tools).

1. At night (kid time, evening, or after the day ended): the walk icon is in the top bar; start a 10-minute walk.
2. With the phone on silent and the screen off, it rings and vibrates at 5 minutes ("Turn back now") and at 10 ("Walk done").
3. Turn back now at minute 2: the walk ends at minute 4.
4. History → today: "Walks" lists it.
5. During a day block, Walk still inserts into the day (2.3.0 behavior).

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
