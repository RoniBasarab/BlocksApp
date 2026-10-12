# TASK 15: The status line, the empty band under titles, walks on top (2.6.2)

**Target version: 2.6.2** (versionCode 20602). Three small fixes. Paths are under `app/src/main/java/com/roni/blocks/`.

## What Roni found (12 Oct, 22:58)

He started at 22:00 straight into Kid asleep and a walk. The top bar still said "10 blocks left". Once the day blocks are behind him it should say what happened: "2 done · 8 missed".

## Also found (12 Oct, 23:01, screenshot of History → Monday)

A tall empty band between the title bar and the content on History's day view, and even more on Settings: on every screen with a back or tab title bar. On the day view the walks sit at the very bottom, under the gesture bar. Roni wants the walks right under the date.

## Causes

- **Empty band:** `ScreenScaffold` (`designsystem/Components.kt`) already pads its content with the Scaffold's `inner` padding (`Box(Modifier.padding(inner))`) and then passes the same `inner` to `content`. Every screen applies `.padding(padding)` again, so the top bar's height counts twice. Home ignores the parameter, which is why Home looks right.
- **Status line:** see below.


`HomeViewModel.status()` computes `blocks.size - index - 1`, and `index` is −1 in every state except Running/Paused, so kid time, the evening and an ended day show *all* blocks as "left". It also says "leak", a word the app retired in favour of "ran over".

## Rules

Branch `task15`. First `git add docs ; git rm docs/Task-14.md ; git commit -m "task15 prep: docs"`, then `task15 slice 1: status line` and `task15 slice 2: release 2.6.2`. `docs/AGENT.md` has the shell and Gradle rules.

## Slice 1: The status line

Replace `status(...)` with this (paste it, adapt names):

```kotlin
/** The line under the day's title (2.6.2). */
private fun statusLine(dayState: DayState, blocks: List<CalendarBlockUi>, index: Int, ranOverMin: Int?, emergency: Boolean, nowMin: Int): String {
    if (emergency) return "Stopped early (emergency)"
    if (dayState == DayState.Off) return "Off day"
    if (dayState == DayState.NotStarted || blocks.isEmpty()) return "Pick a template"
    val dayBlocks = blocks.filter { it.type.isWork || it.type == BlockType.Free }
    val running = dayState is DayState.Running || dayState is DayState.Paused
    val ahead = if (running) dayBlocks.count { it.startMin > (blocks.getOrNull(index)?.startMin ?: nowMin) }
                else dayBlocks.count { it.startMin >= nowMin }              // Fri/Sat: kid time first, day blocks after
    if (running || ahead > 0) {
        val left = if (ahead == 0) "Last block" else "$ahead ${if (ahead == 1) "block" else "blocks"} left"
        return if (ranOverMin != null && ranOverMin > 0) "$left · ran over $ranOverMin min" else left
    }
    val rows = DayRecapMapper.map(day.liveRecord(), timeFormatter.use24.value).rows
        .filter { r -> r.type?.let { it.isWork || it == BlockType.Free } == true }
    val missed = rows.count { it.missed }
    val done = rows.size - missed
    return if (rows.isEmpty()) "Day done" else "$done done · $missed missed"
}
```

- Call it where `status = ...` is set now, passing `ranOverMin = if (leaking) minute - effectiveEndMin else null`.
- For an ended day the recap already has the record: use `endedRecord` instead of `day.liveRecord()` when it's non-null.

**Tests** (`HomeViewModelTest`):
- `status_inTheEvening_countsDoneAndMissed`: Mon/Thu started at 22:00 with the debug clock, then Kid asleep → status "0 done · 8 missed" (adapt the numbers to the template's real day blocks).
- `status_whileRunning_countsBlocksAhead`: Sun/Tue/Wed on `coding_am` at 10:00 → "4 blocks left" (`free_midday`, `coding_pm`, `free_pm` and anything after that's a day block; adapt to the real count).

## Slice 2: No double padding; walks on top

**One fix for every screen** (`ScreenScaffold`): keep `Box(Modifier.padding(inner))` and pass `PaddingValues(0.dp)` to `content`. Screens keep their `.padding(padding)` calls, which become no-ops. Then go through every `ScreenScaffold` user (`git grep -n "ScreenScaffold(" -- "*.kt"`: History, History day, Settings, Weekly, Insights, Home, any other) and check the first item sits about 8 dp under the title bar. Remove any extra top `Spacer` or padding that was compensating.

**The bottom:** screens without a bottom bar (History, History day, Settings) give their scrolling content a bottom padding of `WindowInsets.navigationBars` plus `Spacing.l`, so the last item clears the gesture bar.

**History day: steps and walks first.** Move `d.steps` and `WalksSection` out of the `Column` and into the list as its first item: `RecapList` gets `header: (@Composable () -> Unit)? = null`, rendered as item 0 so it scrolls with the rest. The header is one `AppCard`:

```
6,240 steps                                  (titleMedium, numbers)
●  22:28–22:48 · 20 min · 2,075 steps         (one line per walk; dot in the walk accent 0xFF8FC7C9; bodyMedium)
```

- No steps and no walks: no card.
- Steps but no walks: just the steps line.
- Delete the old bottom `WalksSection`.

No new tests (layout).

## Slice 3: Release 2.6.2

`versionName = "2.6.2"`, `versionCode = 20602`; CHANGELOG `## 2.6.2`: "After the day blocks, the top bar shows what happened (\"3 done · 5 missed\") instead of blocks left; \"leak\" now reads \"ran over\".", "Fixed: the empty band under every screen title", "History: a day's steps and walks are at the top". Add one line to `docs/NOTES.md`. Final gate (`cmd /c` form), merge, tag `v2.6.2`, push. A report of 5 lines at most, then stop.
