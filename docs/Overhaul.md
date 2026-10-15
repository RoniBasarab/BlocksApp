# The architecture overhaul (3.5 → 4.1)

Planned by Roni and the planning agent, 14 Oct 2026. **Goal:** the app looks and behaves the same on the surface, but inside it becomes consistent, safe when something fails, lighter, and ready for the Play Store. The app is no longer "Roni's personal app"; it will be generalized step by step.

**Identical 1:1, all tests green (Roni, binding).** Every screen looks and behaves exactly as before, pixel for pixel. The only differences are the ones listed in this file:
- the error card and the "couldn't be read" line, which appear only when something failed;
- debug-only developer buttons;
- the "while driving" lines, which go with the driving feature.

Every existing test stays green after every slice. A test may change only in two ways:
- its setup changes because a constructor changed (no expectation changes);
- it is deleted because it tests the removed driving feature.

If a test fails, the code is wrong, not the test.

Every decision below is Roni's. A task file may only apply what is written here. Anything new goes back to Roni with pros and cons first.

## Decisions (Roni, 14 Oct)

| Topic | Decision |
|---|---|
| Failures | **Soft in release, loud in debug.** Release: log it, show "Something went wrong" in place of the broken part, keep going. Debug: the same failure crashes, so it gets fixed. One class decides: `Failures` + `FailurePolicy(loud = BuildConfig.DEBUG)` |
| Unreadable data | Never overwritten. Moved to its own prefs file (`unreadable`), read as null. Settings → Your data shows "N saved items couldn't be read" with **Share**. Nothing pops up |
| Error card | **In place of the broken part** (in practice: the screen's content under its bars, since every screen loads as one piece; the tabs and top bar keep working). Buttons: **Restart app** and **Send log** |
| `!!` | Gone from app code. Lengths of segments go through helpers (`Segment.actualMinutes` etc.) |
| Guard rails | A small Gradle task (`codeRules`, no plugin) before every build: no `!!`, no `GlobalScope`, no `runBlocking`, experimental APIs only inside `designsystem/` |
| Experimental APIs | Remove the opt-ins the compiler no longer needs; wrap the rest once in `designsystem/` (`AppSheet`, `AppTimePicker`, ...) |
| Big constructors | **Startup set + slices + bundles.** App start = Hilt multibinding `Set<Startup>` run in a loop. Big screens (Home, Settings) = injected, typed "slices", each with its own state part and actions; the ViewModel injects the slices, `init` starts each, `onAction` routes. Things that always travel together = small bundles (`TimeEnv(clock, zone, ticker, timeFormatter)`). No service locator: constructors stay explicit and compiler-checked |
| Storage | **Room for history and the live day; prefs for settings.** Done after planned times are removed from segments, so the data migrates once, into its final shape |
| Day core | **One `DaySnapshot`** (one StateFlow, one write) + **planned times only in the plan** (segments keep actuals and a block id; one derived `DayView` feeds Home, the notification, the widget and alarms; `blockIndex` goes) + **split DayOrchestrator** into small classes behind its API (walks, evening, steps, side effects); it stays the single writer |
| Generalizing | **Personal constants in one place now** (greeting name, the MON_THU gym rule, the 22:30 cap, built-in template ids...). Strings to `strings.xml` later, as its own version |
| Dependencies | Out: `core-splashscreen` (platform splash screen), the `-ktx` / `ui-graphics` lines that come in anyway, `hilt-navigation-compose` → `hilt-lifecycle-viewmodel-compose`, **`material-icons-core` / `-extended`** (51 → our own `AppIcons`, generated from Google's SVGs and checked pixel by pixel), **`play-services-location` together with the "steps while driving" feature** (Roni: more real steps go uncounted than fake ones counted), **`kotlinx-collections-immutable`** (plain `List` + the stability config). Kept: Hilt, Navigation 3, kotlinx-serialization, **kotlinx-datetime**, lifecycle, Compose |
| Order | **Safety → Day core → Room → Screens** |

## The versions

| Version | Task | What |
|---|---|---|
| **3.5.0** | Task-27 | Safety: `Failures`, safe storage (set aside, never overwrite), the error card on every screen, no `!!`, `codeRules` |
| **3.5.1** | Task-28 | Cleanup: experimental APIs wrapped, `AppIcons`, the dependency removals, driving removed, APK size measured |
| 3.6.0 | later | Day core: `DaySnapshot`, planned times only in the plan, `DayView`, orchestrator split, personal constants in one place |
| 4.0.0 | later | Room for history + the live day, one-time import of the existing data (tested like `UpgradeFrom262Test`, backup first). No way back to 3.x |
| 4.1.0 | later | Screens: §5 files per screen, slices, bundles, `Set<Startup>`, immutable collections → `List`, no public ViewModel functions besides `onAction`, no `TemplateStore.instance` / `Categories.current`, no callback lists (AppTopBar, NowCard dialogs), Settings' Route stops calling ViewModel methods |

## What the overhaul doesn't change

What the user sees, apart from the error card and the driving line going away; the screen pattern (State / sealed Action / Effect / one `onAction`); Hilt; single module.
