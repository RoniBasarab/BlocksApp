# TASK 19, Track A: The category editor (3.1.0)

You work in `C:/Blocks App 19a` on `task-19a/v3.1.0-categories`. Another agent works on the template editor in `C:/Blocks App 19b` at the same time: **edit only the files named here**. Read `docs/Task-19.md` lines 1–38 first (decisions and rules).

Base already built: `CategoryActions` (the only way to write), `CategoryResult`, `CategoryPalette`, and the read-only `CategorySection` in `feature/templates/CategorySection.kt`.

**Your files:** `feature/templates/CategorySection.kt`, new `feature/templates/CategorySheet.kt`, and `app/src/test/java/com/roni/blocks/feature/templates/CategoryListViewModelTest.kt`. Nothing else.

---

## Slice 1: Edit and add categories

**`CategoryListViewModel`** (in `CategorySection.kt`):
- State: the rows (as now) plus `sheet: CategorySheetUi?`.
- `CategorySheetUi(id: String?, name: String, colorArgb: Long, work: Boolean, pomodoro: Boolean, builtIn: Boolean, error: String?, confirmDelete: Boolean)`. `id == null` is a new category.
- Actions: `Open(id)`, `New`, `Name(text)`, `Color(argb)`, `Work(on)`, `Pomodoro(on)`, `Save`, `Delete`, `ConfirmDelete`, `Dismiss`.
- `New`: name "", colour = the first `CategoryPalette` colour no category uses (else the first), work on, Pomodoro off.
- `Save` → `CategoryActions.add` / `update`. `Saved` closes the sheet and refreshes the rows; otherwise `error`:

  | Result | Text |
  |---|---|
  | `EmptyName` | "Give it a name." |
  | `NameTaken` | "That name is taken." |
  | `TooLong` | "16 characters at most." |

- `Delete` (yours only) sets `confirmDelete`. `ConfirmDelete` → `CategoryActions.delete`, closes the sheet.

**`CategorySheet`** (`ModalBottomSheet`, the app's sheet look; copy the walk sheet's container and spacing):
1. Title: "New category", or the category's name.
2. Name: an `OutlinedTextField`, one line, max 16 characters.
3. Colour: the 12 `CategoryPalette` swatches, 6 per row, 36 dp circles; the chosen one has a 2 dp `textPrimary` ring. Tags `cat_color_<index>`.
4. "Pomodoro": a switch, subtitle "50 min focus, 10 min break".
5. "Counts as work": a switch, subtitle "Shows in your work hours". Only for your own categories (built-ins keep theirs).
6. The error line in `Leak` colour, when there is one.
7. Buttons: "Delete" (text button in `Leak`, yours only) and "Save" (primary).
8. With `confirmDelete`, an `AlertDialog`: "Delete <name>?" / "Its blocks in your templates become free time. Days you already had keep it." / "Delete" · "Cancel".

**`CategorySection`:** rows are clickable (`Open(id)`, tag `cat_row_<id>`), and a "+ New category" text button under them (tag `cat_new`).

**Tests** (`CategoryListViewModelTest`, Robolectric; `testActions(context)` from Base):
- `newCategory_appearsWithItsColour`: `New`, `Name("Office")`, `Color(0xFFE08A7A)`, `Save` → a row "Office" with that colour; the sheet is closed.
- `nameTaken_showsTheError`: `New`, `Name("coding")`, `Save` → `sheet.error == "That name is taken."`.
- `deleteCustom_freesItsBlocksInTemplates`: an own template with an Office block (through `TemplateActions`), `Open("cat_1")`, `Delete`, `ConfirmDelete` → no Office row; that template's block is now free time.

---

## Slice 2: Done (no release)

1. Full suite (the `cmd /c` form), commit `task-19a slice 1: The category editor` if not already.
2. `git push origin task-19a/v3.1.0-categories`. **Don't merge** and don't touch `master`: Finish does that.
3. A report of at most 8 lines: the commit, the tests, any real-name differences. End with **"Track A done."** Then stop.
