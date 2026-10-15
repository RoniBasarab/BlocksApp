# TASK 28: Lighter and stable (3.5.1)

**Target version: 3.5.1** (versionCode 30501). The second version of the architecture overhaul (`docs/Overhaul.md`). Runs **after** Task-27 (3.5.0) is merged and tagged. One agent, `C:/Blocks App`, branch `task-28/v3.5.1`. Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/`, `gradle/` or `docs/`.

## Rules

1. `git checkout -b task-28/v3.5.1` ; `git add docs` ; `git rm docs/Task-27.md` ; `git commit -m "task-28 prep: docs"`. Then `task-28 slice N: <title>`.
2. **Identical 1:1, all tests green (Roni, binding).**
   - Every screen looks and behaves exactly as in 3.5.0. Same paddings, colors, shapes and icons; same sheet behaviour.
   - The only change the user sees: the "while driving" lines are gone (slice 3).
   - Every existing test stays green after every slice. A test's setup may change when a constructor does; its expectations never. The driving tests are deleted (list them).
   - If a test fails, fix the code, not the test.
3. Architecture (`docs/architecture.md` §5) is binding. Verified files stay untouched.
4. JVM / Robolectric tests only. No new tests unless named here.
5. **Shipped, verified file:** `designsystem/icons/AppIcons.kt`, already in the repo (it came with the Task-27 bundle). The planning agent generated it from Google's Material Icons SVGs (outlined, Apache 2.0) and checked it two ways:
   - compiled against Compose's PathBuilder / ImageVector signatures;
   - rendered every icon against the original SVG: identical, with 5 auto-mirrored like before.

   Don't edit it.

---

## Slice 1: Experimental APIs

0. **Before any change:** `cmd /c "gradlew.bat :app:assembleRelease --offline --console=plain > %TEMP%\g.txt 2>&1"`. Note the size of `app/build/outputs/apk/release/*.apk` in `NOTES.md` as "3.5.0 release APK: N KB". The release falls back to the debug key without `keystore.properties`; that's fine for measuring.
1. **Remove opt-ins.** Remove every `@OptIn(...)` and every `Experimental*Api` import outside `designsystem/` (16 annotations, 9 imports). Compile.
2. **Wrap what still fails** in `designsystem/`, once per component:
   - **`AppSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`**
     - Wraps `ModalBottomSheet` with exactly today's settings: `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, `shape = AppShapes.sheet`, `containerColor = AppTheme.colors.surface`, `dragHandle = null`.
     - Keep each site's own `properties`, if any (add a parameter only for the ones actually used).
     - All 8 sheets use it.
   - **`AppTimePicker`** for `BlockSheet`'s time picker, and **`AppDropdown`** for its `ExposedDropdownMenuBox`:
     - the same parameters the one call site uses today, nothing more;
     - the time picker's state type stays inside the wrapper.
   - **`FlowRow`** (Settings, DayRecap) and **`HorizontalPager`** (HowToUse): keep them as they are if they compile without the opt-in. Otherwise add a thin wrapper.
   - Designsystem components take plain parameters and slots like Material's own (`onDismiss`, `content`). Feature composables keep (state, `onAction`).
3. **Experimental rule.** Turn on `codeRules`' experimental rule (remove the `// 3.5.1` comment from 3.5.0).
4. **`-opt-in=kotlin.time.ExperimentalTime`** in `app/build.gradle.kts`:
   - Remove it and compile.
   - If the compiler still needs it (`kotlin.time.Instant` / `Clock` still experimental in this Kotlin), put it back, and note "ExperimentalTime still needed with Kotlin X" in `NOTES.md`.
   - Don't upgrade Kotlin for it.

**Tests:** the existing suite. The 8 sheets, the time picker and the dropdown are covered by the ViewModel and expectation tests that already open them.

---

## Slice 2: Our own icons

1. **Replace the icons.** Every `Icons.Outlined.X` / `Icons.AutoMirrored.Outlined.X` becomes `AppIcons.X` (same names: 50 icons; `git grep -n "Icons\." -- "*.kt"`). Remove the `androidx.compose.material.icons.*` imports.
2. **Remove the dependencies.** `app/build.gradle.kts`: remove `material.icons.core` and `material.icons.extended`; `gradle/libs.versions.toml`: their two library lines.
3. **Check the graph.**
   - `cmd /c "gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath --offline > %TEMP%\d.txt 2>&1"`
   - `Select-String -Path $env:TEMP\d.txt -Pattern 'material-icons' | Select-Object -First 3`
   - If Material 3 still pulls `material-icons-core` in on its own, that's fine: we no longer use it. Note it in `NOTES.md`.
4. `git grep -n "material.icons" -- "*.kt" "*.kts" "*.toml"` → nothing.

---

## Slice 3: Remove "steps while driving"

Roni's decision (14 Oct): more real steps go uncounted than fake ones counted, so the feature goes, and with it `play-services-location`.

**Delete:**
- `core/steps/DriveDetector.kt`;
- `DriveTransitionReceiver` (in `core/alarms/Receivers.kt`) and its `<receiver>` in the manifest;
- `DriveLog` (in `Model.kt`);
- `OpenDrive`, `openDrive`, `onDrive`, `simulateDrive`, `closeStaleDrive`, `closeDrive` and `persistDrive` in `DayOrchestrator`;
- `HistoryManager.addDrive`, `drives`, `drivesBetween` and `driveKey`;
- the "While driving" row in Weekly and Insights (and `driveSteps` in their state);
- the "· about N while driving" suffix in History;
- the "Simulate a drive" debug button and `SettingsAction.SimulateDrive`;
- `driveDetector` in `App` and `MainActivity`;
- the driving lines in `MANUAL_TESTS.md`.

**Keep:** the `ACTIVITY_RECOGNITION` permission and its request. The step counter needs it.

**Old data:**
- `DayOrchestrator`'s restore removes the `DAY` key `open_drive`.
- `HistoryManager.prune` also removes every `HISTORY` key starting with `drives_`.
- Both are one line each and harmless when nothing is there.

**Dependencies:** remove `play-services-location` from `app/build.gradle.kts` and from the catalog. `git grep -n "gms" -- "*.kt" "*.kts" "*.toml" "*.xml"` → nothing.

**Tests:** delete the tests of the driving feature and list them. Every other test stays as it is.

---

## Slice 4: Smaller dependency list

1. **Splash screen without `core-splashscreen`** (minSdk 34 has the platform one). It must look and time exactly as now.
   - `themes.xml`: `Theme.Blocks` becomes the main theme, `parent="android:Theme.Material.NoActionBar"`, with:
     - `android:windowBackground` `#1C1B19`;
     - `android:windowSplashScreenBackground` `#1C1B19`;
     - `android:windowSplashScreenAnimatedIcon` `@mipmap/ic_launcher`.

     Delete `Theme.Blocks.Main` (check the manifest uses only `Theme.Blocks`).
   - `MainActivity`: remove `installSplashScreen()`. Keep the splash on screen with the platform's documented way, the same condition as today (`!orchestrator.isRestored.value`, at most `SPLASH_CAP_MS`):
     ```kotlin
     val content = findViewById<View>(android.R.id.content)
     content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
         override fun onPreDraw(): Boolean {
             val ready = orchestrator.isRestored.value || SystemClock.uptimeMillis() - shownAt >= SPLASH_CAP_MS
             if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
             return ready
         }
     })
     ```
   - Remove the dependency and its catalog line.
2. **Hilt.** `androidx.hilt:hilt-navigation-compose` → `androidx.hilt:hilt-lifecycle-viewmodel-compose`, same version. Our `hiltViewModel` import (`androidx.hilt.lifecycle.viewmodel.compose`) already comes from it; the old artifact only added Navigation 2 on top.
3. **Lines that come in anyway.** Remove `lifecycle-runtime-ktx`, `lifecycle-viewmodel-ktx` and `compose-ui-graphics` from `app/build.gradle.kts`. Keep the catalog lines unless nothing references them; then remove those too.
4. `kotlinx-collections-immutable` stays until 4.1.0 (Overhaul.md).
5. **Measure again.** `:app:assembleRelease`, then note "3.5.1 release APK: N KB (was M)" in `NOTES.md`.

---

## Slice 5: Docs and release 3.5.1

1. **Docs.**
   - `docs/CODEMAP.md`: `AppIcons`, `AppSheet`, `AppTimePicker`, `AppDropdown`; drop the driving entries.
   - `architecture.md` §2 (the stack): no `core-splashscreen`, no icons libraries, no Play Services; icons are `AppIcons`.
   - `docs/NOTES.md`: one line per choice.
2. **`docs/MANUAL_TESTS.md`, "Check on the phone (3.5.1)":**
   1. Open every screen and every sheet: same look as 3.5.0, icons included (bottom tabs, menu, category icons, walk icon, arrows).
   2. Cold start: the splash shows the same way and leaves when the day is ready.
   3. History, Weekly, Insights: no "while driving" line; everything else unchanged.
3. **Version.** `versionName = "3.5.1"`, `versionCode = 30501`. `CHANGELOG.md`:
   ```markdown
   ## 3.5.1
   - Removed "steps while driving" (and Google Play services with it).
   - A smaller app: our own icons, fewer libraries. Nothing else changes.
   ```
4. **Finish.** Final gate, merge into `master`, tag `v3.5.1`, push `master` and the tag. A report of at most 8 lines, including both APK sizes, then stop.
