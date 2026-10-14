# TASK 25: Quiet means quiet (3.4.2)

**Target version: 3.4.2** (versionCode 30402). One agent, `C:/Blocks App`, branch `task-25/v3.4.2`. Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/` or `docs/`.

## What Roni found (14 Oct, 00:02)

After midnight he picked a template, with the app open. Home correctly showed "Day starts at 09:00", but a notification appeared **and rang**.

## Causes

| # | Cause |
|---|---|
| 1 | By design (2.7.0) a waiting day posts a "Day starts at 09:00" notification. Roni wants no notification until the day really starts |
| 2 | `NotificationRenderer.build` keeps quiet renders quiet with `setOnlyAlertOnce(true)`. That only silences **updates** of a notification already showing. A notification posted fresh rings on the `day` channel, app open or not: the first one of the day, or one re-posted after a channel switch cancelled it. Tonight's was fresh |

## Decisions (Roni, 14 Oct)

| Topic | Decision |
|---|---|
| Waiting day | **No notification** while the day waits for its first block. The first one is the real start, with its sound. Home's "Day starts at" card and the widget stay as they are |
| Quiet renders | A render that shouldn't sound is **silent**, whether it's new or an update |

## Rules

1. `git checkout -b task-25/v3.4.2` ; `git add docs` ; `git rm docs/Task-24.md docs/AGENT-24.md` ; `git commit -m "task-25 prep: docs"`. Then `task-25 slice N: <title>`.
2. Architecture (`docs/architecture.md` §5) is binding. Verified files stay untouched.
3. JVM / Robolectric tests only, no Compose UI tests. Add only the tests named here; list any test you change.

---

## Slice 1: No notification while waiting; silent means silent

**`core/notifications/NotificationModel.kt`:** `NotificationModel` gets `val inShade: Boolean = true` (last field). The waiting model (`key = "waiting"`) sets `inShade = false`. The widget keeps using the same model, so it still shows "Day starts at 09:00".

**`core/notifications/NotificationRenderer.kt`:**
- `render(model, …)`: when `model != null && !model.inShade`, cancel our notification if one is showing (as for `null`), and post nothing.
- In `build`, replace the `setOnlyAlertOnce(...)` line with:
  ```kotlin
  val sounds = alert || (transition && !visibility.isForeground)
  builder.setOnlyAlertOnce(!sounds)
  if (!sounds) builder.setSilent(true)          // new or updated, a quiet render never rings (3.4.2)
  ```
  The `dishes` channel's vibration and the walk ringer (its own channel and builder) don't change. Alerts and real transitions still sound exactly as before.

**Tests** (`NotificationRendererTest`, the existing Robolectric setup):
- `waitingDay_postsNothing`: render the waiting model with `transition = true` → no active notification. Render it while a "Pick today's template" notification is showing → that one is cancelled.
- `quietRender_ofANewNotification_isSilent`: no notification showing, app in foreground, render a running model with `transition = true, alert = false` → the posted notification is silent. Check what `NotificationCompat.Builder.setSilent(true)` sets on the built `Notification` and assert that (e.g. `groupAlertBehavior == GROUP_ALERT_SUMMARY` with its group key, and no sound or vibration).
- `alert_stillSounds`: the same with `alert = true` → not silent.
- Update `HomeViewModelTest`/`DayOrchestratorTest` cases that expected the waiting notification, and list them.

---

## Slice 2: Docs and release 3.4.2

1. `docs/NOTES.md`: the two causes, one line each.
2. `docs/MANUAL_TESTS.md`, "Check on the phone (3.4.2)":
   1. After midnight (or the debug clock at 07:00), pick a template with the app open → no notification, no sound; Home shows "Day starts at 09:00".
   2. At 09:00 the first block starts with its notification and sound.
   3. With the app open, Next to the next block → the notification updates silently.
3. `versionName = "3.4.2"`, `versionCode = 30402`. `CHANGELOG.md`:
   ```markdown
   ## 3.4.2
   - No notification while the day waits for its first block; the first one comes when the day starts.
   - Fixed: a quiet notification could still ring when it was posted for the first time (e.g. picking a template after midnight).
   ```
4. Final gate, merge into `master`, tag `v3.4.2`, push `master` and the tag. When `keystore.properties` exists, build the release APK to `out/blocks-3.4.2-release.apk`. A report of at most 8 lines, then stop.
