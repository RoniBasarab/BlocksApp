# TASK 29: Every category on Home (3.5.2)

**Target version: 3.5.2** (versionCode 30502). A bug fix outside the overhaul. Runs **after** Task-28 (3.5.1) is merged and tagged. One agent, `C:/Blocks App`, branch `task-29/v3.5.2`. Paths are under `app/src/main/java/com/roni/blocks/` unless they start with `app/` or `docs/`.

## What Roni found (15 Oct)

1. Mid-coding, he added a category "Errands" (Profiles → Categories).
2. Back on Home, he tapped the running block → Add after.
3. Errands wasn't in the category list.

## Cause

- `HomeViewModel.workCategories()` keeps only categories with `work = true`; `homeCategories()` is those plus Free (since 3.3.0).
- A category with "Counts as work" off (an errand) never shows on Home.
- The template editor lists every category (`templateCategories()`), so the two places disagree.

## Decision (planning agent, Roni said "you decide")

Home offers **every category**, like the template editor. "Counts as work" only decides what counts toward work hours, never what you can pick.

## Rules

1. `git checkout -b task-29/v3.5.2` ; `git add docs` ; `git rm docs/Task-28.md docs/AGENT-28.md` ; `git commit -m "task-29 prep: docs"`. Then `task-29 slice N: <title>`.
2. **Identical otherwise.** All existing tests stay green; no expectation changes except where a test pinned the work-only list (list it).
3. Architecture (`docs/architecture.md` §5) is binding. Verified files stay untouched.

---

## Slice 1: One list of categories

1. **One function for the list.** In `feature/templates/TemplateEditorScreen.kt`, next to `typeForCategory`:
   ```kotlin
   /** Every category in use (3.5.2): built-ins first, then customs, in their stored order. One list for every picker. */
   internal fun categoryChoices(set: CategorySet = Categories.current): List<BlockType> =
       (set.builtIns + set.customs).map { typeForCategory(it) }
   ```
   `templateCategories()` becomes `(categoryChoices() + BlockType.KidTime).toPersistentList()`.
2. **`Home.kt`** (keep Home's order: built-ins without Free, the customs, then Free last):
   ```kotlin
   /** Every category, Free last (3.5.2: no longer only work ones). */
   private fun homeCategories() = (categoryChoices().filter { it != BlockType.Free } + BlockType.Free).toPersistentList()

   /** The running block can be retyped to any category but Free, as before. */
   private fun retypeCategories() = categoryChoices().filter { it != BlockType.Free }.toPersistentList()
   ```
   - The running sheet (`END_AND_TYPE`) uses `retypeCategories()`.
   - `addAfter()`'s default type stays the first **work** category (`categoryChoices().firstOrNull { Categories.infoFor(it)?.work == true } ?: BlockType.Cv`), so a new block still starts as before.
   - Delete `workCategories()`.

**Test:** `HomeViewModelTest.aNonWorkCategory_isOfferedOnHome`.
- Add a custom category with `work = false` through the same store or actions the app uses.
- Open the sheet of a block ahead → its `categories` contain the custom category, with Free last.
- Add after on the running block → the new sheet's `categories` contain it.
- Retyping the running block (`END_AND_TYPE`) → contains it, and no Free.

---

## Slice 2: Docs and release 3.5.2

1. `docs/NOTES.md`: one line for the cause and one for the decision.
2. `docs/MANUAL_TESTS.md`, "Check on the phone (3.5.2)":
   1. Profiles → Categories → add "Errands" with "Counts as work" off.
   2. On Home, tap the running block → Add after → Errands is in the list. Save → the block shows on the calendar.
   3. Weekly doesn't count it as work.
3. `versionName = "3.5.2"`, `versionCode = 30502`. `CHANGELOG.md`:
   ```markdown
   ## 3.5.2
   - Fixed: categories that don't count as work (like an errand) were missing from Home's block sheet.
   ```
4. Final gate, merge into `master`, tag `v3.5.2`, push `master` and the tag. A report of at most 6 lines, then stop.
