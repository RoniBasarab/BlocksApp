# Manual device tests (Task-4 §4.6, D9–D12)

D1–D8 are automated in `app/src/androidTest/java/com/roni/blocks/NotificationDeviceTest.kt`.
With a phone attached, run them with:

```bash
./gradlew connectedDebugAndroidTest
```

These four cannot be automated reliably (they depend on the launcher, the reboot flow, the keyguard
and the system DND state), so Roni runs them by hand.

**Check on the phone (3.1.0).** Use the debug app for steps that move the clock (Settings -> Developer tools).

1. Templates tab: the four templates and the four categories.
2. New category "Office", a palette colour, Work on, Pomodoro off.
3. New template "Office day": add Office 09:00, length 8h → kid time still 17:00–22:30; claim Monday → Mon/Thu shows THU.
4. Make Office 8h 30m → "Moves later" mentions kid time from 17:30. Start Office at 08:55 when a block ends at 09:00 → the reason shows and Save is off.
5. Picker on a Monday: "Office day" is Suggested.
6. Rename CV, recolour Gym: Home, History and Weekly follow.
7. Delete "Office": Office day becomes free time until kid time.
8. Pick Office day before its first block, edit it in Templates: Home's "Day starts at" follows.
9. Delete Office day: History of a day that used it still shows its name.

**Check on the phone (3.2.0).**

1. After installing over 3.1.1: ≡ → Profiles shows Unemployed, active, and its days match what 3.1 suggested (Monday = In-Office if you set it).
2. The hint shows; ? opens the 5 cards; after closing, the hint is gone for good.
3. New profile "Employed", Start empty → only Off day. + New template "Office day", add blocks; + Add existing → Gym day; Days: Sunday–Thursday → Office day.
4. Use this profile → today's day keeps going unchanged; tomorrow's picker shows "Employed ▾" and suggests Office day.
5. The picker's switcher flips between the two; the cards follow.
6. Remove a template from Employed: it stays in Unemployed. Delete a template in the editor: it leaves both.
7. Delete Employed while it's active: not offered. Switch back, then delete it.
8. History: today's day says "Unemployed" under its template.

**Check on the phone (3.0.0).** Install over 2.7.1, don't uninstall.

1. Today's day continues exactly where it was (same block, same timer).
2. History shows every past day as before; Weekly and Insights show the same numbers.
3. The picker shows the same four cards, texts, icons and "Suggested" day.
4. Coding still has its Pomodoro; colours are unchanged.
5. Kill the app and reopen it: all of the above again.

**Check on the phone (2.7.0).** Use the debug app for steps that move the clock (Settings -> Developer tools).

1. After midnight (or with the debug clock at 07:00), pick a template: the calendar plus "Day starts at 09:00 · in 2h 0m", nothing counting.
2. At 09:00 (debug clock) the first block starts by itself with a sound.
3. Start now at 07:40: CV 07:40–08:10, free time until 09:30, and the calendar now begins at 07:00.
4. Change day opens the picker.
5. Steps read "about 2,050 steps"; Weekly shows "≈".

**Check on the phone (2.6.0).** Use the debug app for steps that move the clock (Settings -> Developer tools).

1. Evening: the top-bar walk icon opens the sheet; Start: the card counts 15:00 down in seconds.
2. Turn back after 1 minute: "Heading home", counting down from about 1:00.
3. End walk, then a new walk at once: a fresh "Heading out" from the full length.
4. A 3-minute walk during Coding: a thin line and a "Walked 3 min" pill; Coding's title isn't covered.
5. Start CV, after a minute Done early then Next: a thin CV bar with a "CV · 1 min" pill; Coding's text starts below it.
6. Dishes (20 min) reads "Dishes · 20:12" on one line, nothing cut.
7. On Coding: Done early, then Next → the free time after Coding starts, not the next Coding; nothing disappears.
8. Fresh install: notifications and physical activity are asked at launch; no card on Home.

**Check on the phone (2.5.0).** Use the debug app for steps that move the clock (Settings -> Developer tools).

1. Walk, Turn back after 1 minute: the card counts 1 minute home, to the second.
2. End walk, Walk again at once: a fresh walk "Heading out", 30:00 counting down.
3. A 3-minute walk during Coding: the calendar shows a "Walked 3 min" line in Coding, not a tiny block.
4. First launch: the step card; Allow. Walk around the flat: the top bar's number goes up while you watch.
5. Next morning: History -> yesterday shows its steps; Weekly shows the week's total.
6. Restart the phone, open the app: today's steps are still there (only the steps since the last reading before the restart are missing).
7. Evening: Kid asleep -> pick Read -> the calendar shows kid time until the tap, Dishes 20 min, then Reading; Reading starts by itself when Dishes ends.
8. +15 on Dishes moves Reading later; Skip dishes starts Reading now.
9. Switch activity to Game after a while: Reading and Game are two blocks, and two rows in History.

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

## Check on the phone (3.1.1)
- [ ] Five or more templates: the picker scrolls; Off day sits in the grid, nothing overlaps.
- [ ] Templates tab: open a template, rename it, change a day, back -> the card shows the new name and days at once.
- [ ] Delete a template, back -> its card is gone.
- [ ] + New template -> name -> Create opens the editor.
