# TASK 4 RECOVERY: Get back to green, then finish Task-4 properly

Read this before touching any code. It replaces the plan in `issues.md`.

## What went wrong

1. **Many files were changed without compiling in between.** Nothing in Task-4 is "done" until it compiles and its tests pass. Phases 2 and 3 are not done; they were never built.
2. **`ScheduleEngine` was refactored.** It was working and covered by tests. Task-4 never asked for engine changes. The notification mapper must adapt to the engine, not the other way round.
3. **Several fixes proposed in `issues.md` are wrong.** Corrections are below. Do not apply the originals.

## Corrections to `issues.md`

| `issues.md` says | Correct fix |
|---|---|
| Add `@Serializable` to `Timeline` / extract it as serializable | The timeline is **computed, never persisted**. No `@Serializable` on it at all. Only segments/day records are persisted. |
| Add `since` to the `DayState` interface | No. Use an exhaustive `when (state)`; inside `is DayState.Interrupted ->` the smart cast gives `state.since`. |
| Add `else -> null` to the `when` over `DayState` | **No `else`** on sealed types. Handle every state explicitly so the compiler flags new states. Return `null` inside the specific branches (Ended, Off, …). |
| Redefine `CenterAlignedTopAppBar` with `onHistory`/`onDebt`/`onSettings` params | Never redefine Material components. Write your own wrappers (`TabTopBar`, `BackTopBar`, below) that *call* M3 `CenterAlignedTopAppBar`. |
| Import `androidx.compose.foundation.layout.weight` | `weight` is not importable; it only exists inside `RowScope`/`ColumnScope`. Make helpers `RowScope.` extensions or pass a `Modifier` in from the caller. |
| Import `FilledTonalButtonDefaults` / `OutlinedButtonDefaults` | These don't exist. Use `ButtonDefaults.filledTonalButtonColors(...)`, `ButtonDefaults.outlinedButtonColors(...)`, `ButtonDefaults.outlinedButtonBorder(...)`. |
| Import `androidx.compose.material.icons.Default.Info` | Use `import androidx.compose.material.icons.Icons` + `import androidx.compose.material.icons.outlined.Info`, then `Icons.Outlined.Info`. |
| `NotificationActionReceiver` lives in `src/debug` | It's production code: `src/main`, declared in the **main** manifest, `exported="false"`. Only `TimeTravelReceiver` is debug-only. |
| Time estimates | Not needed. Don't include them in reports. |

## Step 0: Save the work and return to a green base

```bash
git status
git switch -c task4-wip
git add -A
git commit -m "task4 WIP (does not compile)"
git log --oneline -15
```

Find the **last commit where `./gradlew assembleDebug testDebugUnitTest` passed** (at the latest `9341100 task3 phase 5`, unless a later task4 commit was green). Then:

```bash
git switch -c task4 <last-green-commit>
./gradlew assembleDebug testDebugUnitTest
```

It must be green before continuing. From now on, bring code over from the WIP branch **one slice at a time** (`git checkout task4-wip -- <path>` or copy by hand), and **do not bring the `ScheduleEngine.kt` refactor**.

## The loop (mandatory for every slice)

1. Change at most ~3 files.
2. `./gradlew :app:compileDebugKotlin`. Fix until it compiles.
3. Once test files exist: `./gradlew :app:compileDebugUnitTestKotlin testDebugUnitTest`. Everything that passed before must still pass.
4. Commit: `task4 slice N: <title>`.

Never start a new slice with a red build.

## Reference: correct imports

```kotlin
// layout / scroll
import androidx.compose.foundation.layout.*            // Row, Column, Box, Spacer, padding, height, width, heightIn, size, fillMaxWidth, PaddingValues, Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
// runtime
import androidx.compose.runtime.*                      // Composable, remember, mutableStateOf, MutableState, getValue, setValue, SideEffect
// ui
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties     // NOT android.app anything
import com.roni.blocks.R                               // R follows the namespace, not the .debug applicationId
// material3
import androidx.compose.material3.*                    // ButtonDefaults, FilledTonalButton, OutlinedButton, TextButton, AlertDialog, CenterAlignedTopAppBar, TopAppBarDefaults, ExperimentalMaterial3Api, DropdownMenu, DropdownMenuItem, RadioButton, Checkbox, HorizontalDivider, Icon, IconButton, Text
// icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*      // Info, Pause, Warning, CheckCircle, Menu, History, HourglassBottom, Settings, …
// time (kotlinx-datetime 0.8 uses kotlin.time.Instant)
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime                // extension on kotlin.time.Instant; needs ExperimentalTime opt-in (already module-wide per NOTES)
```

## Slice plan (in this order)

### Slice 1: Build setup
`.debug` suffix and `lifecycle-process` from WIP. Compile, commit.

### Slice 2: Timeline queries + notification model + mapper + its test

**Do not modify `ScheduleEngine`.** Open `feature/home/CalendarMapper.kt`: it already compiles against the engine's public timeline API. Use exactly that API. If the mapper needs something that isn't public, add **pure top-level functions** in a new file:

```kotlin
// core/schedule/TimelineQueries.kt
package com.roni.blocks.core.schedule

// Signatures use the engine's existing public types (whatever CalendarMapper uses).
fun currentBlock(timeline: /* existing type */, now: Instant): /* existing block type */?
fun landingBlockForStartNext(timeline: /* existing type */, now: Instant): /* existing block type */?   // block containing `now` if the next one is already over
fun effectiveEnd(block: /* existing block type */, timeline: /* existing type */): Instant

data class PomodoroPhase(val isBreak: Boolean, val index: Int, val total: Int, val phaseEnd: Instant)
fun pomodoroPhase(block: /* existing block type */, now: Instant): PomodoroPhase?   // null if the block has no Pomodoro
```

- If the engine already has a Pomodoro-phase function (Task-1 required tests for "Pomodoro phase at any minute"), **call it** instead of re-implementing.
- `DayState` data is read with an exhaustive `when` and smart casts. No `else`.
- Add `NotificationModelMapperTest` (Task-4 §4.4) in this slice. Green, then commit.

### Slice 3: Renderer + action receiver + renderer test
- `NotificationActionReceiver` in `src/main` + main manifest.
- The action `PendingIntent`s, per action:

```kotlin
val intent = Intent(context, NotificationActionReceiver::class.java)
    .setAction(ACTION_NOTIFICATION_EVENT)
    .putExtra(EXTRA_EVENT, action.event.name)
    .putExtra(EXTRA_FOR_BLOCK, action.forBlockId)
val pi: PendingIntent = PendingIntent.getBroadcast(
    context,
    action.event.ordinal,                          // distinct requestCode per action
    intent,
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)
```

- The renderer takes `SystemTimeMapper` in its constructor for `setWhen(...)`.
- Add `NotificationRendererTest` (Task-4 §4.3). Green, then commit.

### Slice 4: Orchestrator integration
Stale/double-tap protection (`expectedBlockId`), rendering calls, removal of the old two-notification code. Add the `DayOrchestratorTest` cases from Task-4 §4.5. **All previous engine and state-machine tests must still pass.** Commit.

### Slice 5: Debug clock and time travel (no build-type source-set traps)

`DebugClock` and `SystemTimeMapper` live in **`src/main`** (tiny, harmless). Only the receiver is debug-only. Choose the clock with `BuildConfig.DEBUG`; R8 strips the debug path from release and benchmark:

```kotlin
@Module @InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides @Singleton
    fun clock(debugClock: DebugClock): Clock = if (BuildConfig.DEBUG) debugClock else Clock.System

    @Provides @Singleton
    fun systemTimeMapper(debugClock: DebugClock): SystemTimeMapper =
        if (BuildConfig.DEBUG) debugClock.asMapper() else SystemTimeMapper.Identity
}
```

- `src/debug/AndroidManifest.xml`: only `TimeTravelReceiver` (`exported="true"`).
- Gate: `./gradlew assembleDebug assembleRelease :app:assembleBenchmark`. All three must build, because a class referenced from main but defined only in `src/debug` breaks release and benchmark. Commit.

### Slice 6: Design primitives
`ScreenColumn`, `EmptyState`, `SectionCard`, `SettingsRow` from Task-4 §3.1, plus these two wrappers:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(title: String, onMenu: (MenuDestination) -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        actions = { BurgerMenu(onMenu) },          // IconButton(Menu) + DropdownMenu: History, Time Debt, Settings
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
}
```

`enum class MenuDestination { HISTORY, TIME_DEBT, SETTINGS }`. Compile, commit.

### Slice 7: NowCard + dialogs + `NowCardExpectationsTest`

The secondary buttons put the **icon above the label** so "Finish early" and "Emergency" fit a third of the card width without truncating:

```kotlin
@Composable
private fun RowScope.SecondaryAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        shape = AppShapes.card,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.surfaceRaised, contentColor = c.textPrimary),
        contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.s),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(Spacing.xxs))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// usage
Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
    SecondaryAction(Icons.Outlined.Pause, stringResource(R.string.now_pause)) { /* show dialog */ }
    SecondaryAction(Icons.Outlined.Warning, stringResource(R.string.now_emergency)) { /* show dialog */ }
    SecondaryAction(Icons.Outlined.CheckCircle, stringResource(R.string.now_finish_early)) { /* show dialog */ }
}
```

Which dialog is open is UI-local state (`var dialog by remember { mutableStateOf<NowDialog?>(null) }`). That is allowed; it's not business state. The "Don't ask again" values come from `NowCardUi`, and checking the box sends an action. Green, then commit.

### Slice 8: Screens, one per commit
Order: Settings → Weekly → Insights → History → Time Debt. For each:
1. Bring the screen over from WIP.
2. Fix it to use the primitives and the wrappers.
3. Run the matching `UiExpectationsTest` methods.
4. Commit.

The shell uses `TabTopBar` / `BackTopBar`. Each `XScreen(state, onAction)` is content only.

### Slice 9: Device tests
Write D1–D8 in `src/androidTest`. Gate: `./gradlew :app:compileDebugAndroidTestKotlin` (Roni runs them). Add `MANUAL_TESTS.md` with D9–D12. Commit.

### Slice 10: Screenshots, notes, report
- `./gradlew recordRoborazziDebug` with the full Task-4 §5 list.
- Rewrite `NOTES.md` so that nothing in it contradicts the code (including the `goAsync` line).
- Final gate: `./gradlew assembleDebug assembleRelease :app:assembleBenchmark :macrobenchmark:assembleBenchmark testDebugUnitTest`.
- Once everything is green, merge `task4` into your main branch. Keep `task4-wip` until Roni confirms, then delete it.

## Final report (exactly this)

1. The Task-4 checkboxes, each ticked only if its test passed.
2. Test counts per class: pass/fail.
3. Every test file you edited and the exact compile-only change.
4. What from `task4-wip` was **not** carried over, and why.
5. Screenshot list.
6. The final `NOTES.md`.
