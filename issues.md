# Known issues and open problems

Baseline: Task-4 is complete and merged. `master == task4` at `6290142`, JVM gate green (119 tests,
0 failures, 0 errors), `app-debug-androidTest.apk` builds.

Every item below is a real, still-open problem, recorded with the evidence behind it. Nothing here is
speculative. All boxes are unticked — they are the remaining work.

---

## 1. Needs a product decision: ongoing notification vs. swipe-to-dismiss

**This is the one genuine contradiction in the spec, and D6 fails because of it.**

- `Task-4.md:88` mandates `setOngoing(true)`.
- `Task-4.md:574` asserts `FLAG_ONGOING_EVENT` is set.
- `Task-4.md:647` (D6) expects the notification to be swipe-dismissable.

Stock Android does not allow both: an ongoing notification cannot be cleared by the user. So
`d6_swipeAway_staysGoneUntilTheNextTransition` **fails by construction** — the failure is the spec
contradicting itself, not a bug in the dismissal logic.

- [ ] Decide: drop `FLAG_ONGOING_EVENT` so swipe works, **or** drop D6 and keep the block pinned.

## 2. Device tests D1–D8 have never been run

`app/src/androidTest/java/com/roni/blocks/NotificationDeviceTest.kt` is written and compiles, and the
test APK builds. But no phone was attached, so **nothing about its runtime behaviour is verified**.

Run it with a device attached:

```bash
./gradlew connectedDebugAndroidTest
```

Expected first-run failures, in order of likelihood:

- [ ] **D6** fails on the ongoing flag (see §1). This one is known before you start.
- [ ] `startDayAt(hhmm)` assumption: all eight tests rely on starting a day at a chosen clock time
      landing on the block that contains that time. This is inferred from `NotificationModelMapper`
      and `assets/templates.json`, not observed. If the app instead lands on a different block, the
      selectors will not match.
- [ ] `By.text("CV ran over")` must resolve to a tappable shade node. The exact-title match is
      deliberate: `contains("Coding")` would also match the `Start Coding` action button and would
      tap the wrong element.
- [ ] **D5 is outcome-level only.** Tapping a notification action dismisses the shade, so a genuine
      second physical tap cannot land on the same node. The test attempts it defensively and asserts
      the real invariant (Coding, never Free). The true two-events case is covered by
      `DayOrchestratorTest.doubleTap_advancesOnce`.

## 3. D9–D12 are unrun hand-checks

- [ ] `MANUAL_TESTS.md` lists D9–D12 (relaunch from launcher after recents swipe, reboot mid-block,
      lock screen, Do Not Disturb). None has been performed. They depend on the launcher, the reboot
      flow, the keyguard and system DND state, which is why they are not automated.

## 4. Insights, History, Weekly and Time Debt are first cuts only

All four render and navigate, but are not finished screens. They are Task-3 scope, deliberately left
out of Task-4, which only required the screens to navigate and render.

- [ ] **Insights** — first cut. A background task to implement it timed out after 30 minutes
      (`bg_f02cd4fd`) and left no partial changes.
- [ ] **History** — first cut. Same timeout (`bg_f4e14ac9`), no partial changes.
- [ ] **Weekly chart** and **Time Debt** — also recorded as first cuts in `NOTES.md`.
- [ ] **There is no design reference to check them against.** `NOTES.md` twice claims these screens
      "do not yet match every visual in `visuals.md`", but **no `visuals.md` exists in this repo** —
      the only spec document present is `schedule-spec.md`, and there are no mockups or exported
      designs. So the shortfall is asserted but not verifiable. Someone needs to supply the design
      reference before these four can be finished or judged.

## 5. Screenshots were never visually inspected

- [ ] 19 PNGs in `screenshots/` and 19 tests in `ScreenshotsTest` are green, but the captures were
      validated **semantically** (asserting expected UI state before recording), not by eye. The
      image-viewing tool timed out, so no human or model has actually confirmed the images look
      right. This needs a real visual pass.

## 6. Settings screen actions are stubs

- [ ] `SettingsRoute` hardcodes `SettingsUiState()` instead of reading real state, and
      `SendTestNotification`, `ResetToday` and `SetHourFormat` are no-ops.
- [ ] Out of Task-4 scope on purpose: the spec only required these screens to navigate and render.
      Flagged so it is not mistaken for working functionality.

## 7. Watch item: `Notification.getTitle()` removed in API 37

- [ ] The project compiles against an SDK where `Notification.getTitle()` no longer exists.
      `androidTest` works around it via `extras` + `EXTRA_TITLE`. No main-source code calls the
      removed API today, but any new code that does will fail to compile. Note that JVM tests will
      *not* catch this, because Robolectric's `android-all` jar still exposes the old API — the same
      call can compile in the unit-test source set and fail in `androidTest`.
