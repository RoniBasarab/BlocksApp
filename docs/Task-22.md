# TASK 22: The real day, one edit sheet, Dishes that run over, a first-run menu (3.3.0)

**Target version: 3.3.0** (versionCode 30300). Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/` or `docs/`.

Four parts, as in Task-19. Each prompt names its part.

| Part | Folder | Branch | Read |
|---|---|---|---|
| **Base** | `C:/Blocks App` | `task-22/v3.3.0` | this file 1–36, then 38–47 |
| **Track A**: Home and the evening | `C:/Blocks App 22a` | `task-22a/v3.3.0-home` | this file 1–36, then `docs/Task-22a.md` |
| **Track B**: first run and Profiles | `C:/Blocks App 22b` | `task-22b/v3.3.0-menu` | this file 1–36, then `docs/Task-22b.md` |
| **Finish** | `C:/Blocks App` | `task-22/v3.3.0` | this file 1–36, then 49–end |

## Decisions (Roni, 13 Oct)

| Topic | Decision |
|---|---|
| The real day | Home draws what happened, not only the plan. A block running over **grows live** with a red striped tail ("+12 min") and the next block **shrinks from the top**; eaten whole it becomes a "Coding · missed" pill. After Next the tail **stays** and the next block shows its real start ("20 min late"). Past blocks: real times, pauses as grey stripes, blocks never started faded with "Missed". Blocks ahead are the plan and edit as before |
| One edit sheet | Home's edit card becomes the template editor's **BlockSheet**: on a block ahead, category, start, length, **Add after**, **Delete**. On the running block: category and length (its end), Add after; no Delete (Done early / Next are for that) |
| Add after / moving | The editor's rule: free time first, then later blocks move later and keep their length; blocks before never move. Today adds: nothing starts in the next 5 minutes, and kid time and walks are walls (no room → the sheet says why, Save off) |
| Dishes | Never ends by itself any more: it runs until **Done dishes** (or Next); the picked activity starts then. At its planned end and every 5 minutes after: **vibration only**, no sound, no 5-minute warning. History: "Dishes 20:30–20:57 · 27 min (+7)" in red. Insights: a Dishes row |
| First-run main menu | Only until a profile was chosen once (fresh installs). Big profile cards (templates and their days), + New profile on top, Categories explained, "Use this profile" → the picker. Existing installs count as chosen. A debug button shows it again |
| ≡ → Profiles | Stays its own screen; + New profile moves to the top; the Categories row explains itself ("The kinds of blocks: CV, Coding, Gym and your own. Shared by every profile.") |
| Explainer cards | The first visit to a place shows its one card (Profiles, Templates, Days, Blocks, Categories): it can't be dismissed by tapping outside, swiping or Back, only "Got it". Once per card. The ? tour stays |
| Bugs | Off day on a profile page opened the editor and crashed (no blocks); the "Built-in" tag wrapped onto two lines |

## Rules (all parts)

1. Branch, folder and commit names as in the table: `task-22 slice N`, `task-22a slice N`, `task-22b slice N`.
2. **Architecture (`docs/architecture.md` §5) is binding**: `@Immutable` State, sealed Action, sealed Effect, `@HiltViewModel` with injected managers and one `onAction`, state only via `_state.update`, effects via `MutableSharedFlow`. Only Routes know ViewModels; everything else takes (state, `onAction`). No callback-parameter lists, no `TemplateStore.instance` in new code. Types for every new screen are spelled out: use them. If something seems to need breaking that, stop and say so in the report.
3. *Verified*, don't edit: `core/schedule/DayTimeline.kt`, `core/schedule/TodayEditor.kt` (new), `core/schedule/AlertPlanner.kt` (3.3: `warn` parameter) and their tests, plus every file named verified before (`Profiles.kt`, `TemplateEditor.kt`, `TemplateRules.kt`, `PlanEditor.kt`, …).
4. Tracks edit **only the files their track file names**; the other track is editing the rest right now.
5. Add only the tests named here; list every existing test you change or delete.
6. Phone: only `com.roni.blocks.debug`.

---

## Base, slice 1: Prep and hand over

1. `git checkout -b task-22/v3.3.0` ; `git add docs app` ; `git rm docs/Task-21.md` ; `git commit -m "task-22 prep: docs and verified DayTimeline, TodayEditor, AlertPlanner"`.
2. Full suite (the `cmd /c` form). The new verified tests must pass as delivered; `AlertPlanner`'s new parameter has a default, so nothing else changes.
3. `git checkout master ; git merge --ff-only task-22/v3.3.0 ; git push origin master task-22/v3.3.0` (no tag).
4. `git worktree add "C:/Blocks App 22a" -b task-22a/v3.3.0-home master ; git worktree add "C:/Blocks App 22b" -b task-22b/v3.3.0-menu master`.
5. `Copy-Item "C:/Blocks App/local.properties" "C:/Blocks App 22a/" ; Copy-Item "C:/Blocks App/local.properties" "C:/Blocks App 22b/"` (plus `keystore.properties` if it exists).
6. Report in at most 5 lines, ending with **"Base done. Start Track A and Track B."** Then stop.

---

## Finish, slice 2: Merge, docs, release 3.3.0

Run only when both tracks reported done (`git log --oneline master..task-22a/v3.3.0-home` and `…-menu` list their slices).

1. `git checkout task-22/v3.3.0 ; git merge --ff-only master ; git merge --no-ff task-22a/v3.3.0-home ; git merge --no-ff task-22b/v3.3.0-menu`. A conflict can only be in `docs/` or imports: keep both sides. Full suite.
2. `docs/CODEMAP.md` (DayTimeline, TodayEditor, the Home sheet, the Dishes channel, MainMenu, Explainer), `docs/architecture.md` (the start destinations; the timeline on Home), `docs/NOTES.md` (one line per decision, plus the tracks' real-name notes).
3. `docs/MANUAL_TESTS.md`, "Check on the phone (3.3.0)":
   1. Let a block run over (debug clock): it grows with a red striped tail and "+N min"; the next block shrinks from the top.
   2. Next: the tail stays; the next block reads "HH:MM–HH:MM · 20 min late".
   3. A pause shows as grey stripes in its block; a skipped block is faded with "Missed".
   4. Tap Coding ahead: the sheet; Add after → Gym 30 min right after it, later blocks move; make it run into kid time → "No room before kid time at 16:20", Save off. Delete a block → free time.
   5. Tap the running block: category and length only.
   6. Evening: pick an activity during Dishes; at Dishes' end it keeps running, the phone vibrates (no sound), again 5 minutes later; Done dishes starts the activity. History: Dishes "+N" in red.
   7. Developer tools → Show main menu: big profile cards; Use this profile → picker.
   8. Fresh state for explainers (Developer tools → Reset explainers): Profiles, a profile, a weekday, the template editor, Categories each show their card once; tapping outside does nothing.
   9. Profile page: Off day doesn't open anything; "Sunday / Tuesday / Wednesday" shows "Built-in" on one line.
4. `versionName = "3.3.0"`, `versionCode = 30300`. `CHANGELOG.md`:
   ```markdown
   ## 3.3.0
   - Home shows the real day: a block running over grows with a red tail and the next block shrinks; after Next the overrun stays and late starts show. Pauses and missed blocks are drawn too.
   - One edit sheet for today and for templates: category, start, length, Add after, Delete.
   - Dishes runs until you finish it, with a quiet vibration at its end and every 5 minutes; History and Insights show when it ran over.
   - New installs start with a main menu of profiles; first visits explain each place once.
   - Profiles: + New profile on top, Categories explained.
   - Fixed: opening Off day from a profile crashed; "Built-in" wrapped onto two lines.
   ```
5. Final gate, `git checkout master ; git merge --ff-only task-22/v3.3.0 ; git tag v3.3.0 ; git push origin master v3.3.0`, then `git worktree remove --force "C:/Blocks App 22a" ; git worktree remove --force "C:/Blocks App 22b"`.
6. A report of at most 15 lines (every slice of every part, the tests changed or deleted), then stop.
