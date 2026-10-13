# TASK 19, Track B: The template editor (3.1.0)

You work in `C:/Blocks App 19b` on `task-19b/v3.1.0-editor`. Another agent works on categories in `C:/Blocks App 19a` at the same time: **edit only the files named here**. Read `docs/Task-19.md` lines 1–38 first (decisions and rules).

Base already built: `TemplateActions` (the only way to write), `TemplateStore.find/all/isBuiltIn`, `shortFor`, `OWN_TEMPLATE_ICONS`, and the read-only `TemplateSection` in `feature/templates/TemplateSection.kt`. **`core/schedule/TemplateEditor.kt` is verified**: every block edit goes through `TemplateEditor.place` / `delete` / `addStart` / `newId`; don't reimplement any of it.

**Your files:** `feature/templates/TemplateSection.kt`, new `feature/templates/TemplateEditorScreen.kt` and `feature/templates/BlockSheet.kt`, `navigation/AppNav.kt` (the `TemplatesKey` entry and a new key), and the tests named below. Nothing else.

---

## Slice 1: The editor screen

**Navigation:** `@Serializable data class TemplateEditorKey(val key: String) : NavKey`; `entry<TemplateEditorKey> { TemplateEditorRoute(id = TemplateId(it.key), onBack = { backStack.removeAt(backStack.lastIndex) }) }`. In the `TemplatesKey` entry: `onOpenTemplate = { backStack.add(TemplateEditorKey(it.key)) }`.

**`TemplateEditorViewModel`** (in `TemplateEditorScreen.kt`; assisted or `SavedStateHandle` id, whatever this repo already does for `HistoryDayKey`):
- Loads `TemplateStore.find(id)`; `blocks = template.blocks.toPlanBlocks()`; `readOnly = store.isBuiltIn(id)`.
- State: `title`, `iconKey`, `weekdays`, `rows`, `readOnly`, `strip` (the picker's `stripFor`), `sheet: BlockSheetUi?`, `dayMoved: String?`, `confirmDelete`.
- Rows: one per block, `"<name>"` and `"09:00–09:30 · 30 min"`; free time muted with a hollow dot; the last kid time reads `"20:00–22:30"` and "until 22:30".
- Names: `Categories.infoFor(type)?.name`, "Kid time" for kid time.

**Sheet** (`BlockSheetUi(id: String?, type: BlockType, startMin: Int, lengthMin: Int, isDayEnd: Boolean, result: TemplateEdit)`):
- Tap a row (own templates only) → the block's values. "+ Add block" → `id = null`, `type` = the first work category, `startMin = TemplateEditor.addStart(blocks)`, `lengthMin = TemplateEditor.DEFAULT_LENGTH`.
- Every change recomputes `result = TemplateEditor.place(blocks, id ?: TemplateEditor.newId(blocks, key), type, startMin, lengthMin, key)`. The sheet shows it live:

  | `result` | Line under the fields |
  |---|---|
  | `Ok`, nothing else moved | nothing |
  | `Ok`, blocks moved | "Moves later: Gym, Coding · kid time from 17:30": names of the non-free blocks (not this one) whose start changed, in order; the kid-time part only when its start changed |
  | `Refused(StartsInside(id, end))` | "<name> runs until 11:30. Start at 11:30 or later." |
  | `Refused(TooShort)` | "A block is at least 10 minutes." |
  | `Refused(TooEarly)` | "Blocks start at 05:00 or later." |
  | `Refused(NoRoomForKidTime)` | "No room: kid time would be under 10 minutes." |
  | `Refused(KidTimeEndsTheDay)` | "Every day ends with kid time." |
  | `Refused(Broken)` | "That change doesn't fit." (and log it) |

  Refusals use `Leak`, and Save is disabled.
- **Save** (`Ok` only): `TemplateActions.save(template.copy(blocks = result.blocks.toBlockSpecs(template.blocks)))`, close the sheet.
- **Delete** (not for the last kid time): `TemplateEditor.delete(blocks, id)` → save the same way.

**`BlockSheet.kt`** (`ModalBottomSheet`, the walk sheet's look):
1. Title: the block's name, or "New block".
2. **Category:** an `ExposedDropdownMenuBox` with every category from `Categories.current` (built-ins, then yours), then "Kid time". Hidden for the last kid time.
3. **Start:** a row `−15  −5  [09:30]  +5  +15` (tags `sheet_start_minus15` … `sheet_start_plus15`); tapping the time opens a Material3 `TimePicker` dialog (24 h when the app's format is 24 h).
4. **Length:** the same row for the length, shown "2h 0m" (tags `sheet_len_…`), in 5-minute steps, 10 min to 16 h. For the last kid time: the text "Until 22:30" instead.
5. The result line.
6. Buttons: "Delete" (text, `Leak`; not for a new block or the last kid time) and "Save" (primary, tag `sheet_save`).

The same `BlockSheet` will serve Home in 3.2.0, so it takes plain values and callbacks, no ViewModel.

**Screen** (`TemplateEditorRoute`): a top bar with back and the template's title; the strip (full width, 28 dp, rounded); the rows; "+ Add block" (tag `editor_add`). For built-ins: rows aren't clickable, no add button, and a muted line "Built-in templates can't be changed. Make your own from blank in Templates." **The block list is the same for built-ins and yours.**

**Tests** (`TemplateEditorViewModelTest`, Robolectric; `testActions(context)` from Base):
- `addBlock_toABlankTemplate_savesIt`: `TemplateActions.create("Office day")`, open it, "+ Add block", type Coding, length 480, Save → stored blocks: Coding 09:00–17:00, kid time 17:00–22:30.
- `aStartInsideTheBlockBefore_showsTheReason`: an own template Coding 09:00–11:30 then Gym 12:00–14:00; open Gym, start 11:00 → the line "Coding runs until 11:30. Start at 11:30 or later.", Save disabled.
- `longer_showsWhatMoves`: Coding 09:00–11:30, Gym 11:30–13:30; Coding length +60 → "Moves later: Gym".
- `builtIn_isReadOnly`: open `MON_THU` → `readOnly`, opening a row does nothing.

---

## Slice 2: New, rename, weekdays, delete

**On the editor screen**, above the strip, own templates only:
- **Name:** an `OutlinedTextField`, 1–24 characters, saved when it loses focus (`TemplateActions.save` with the new `title`).
- **Icon:** the `OWN_TEMPLATE_ICONS` as a row of 40 dp toggles; saved on tap.

**Weekdays** (every template, built-ins too): seven chips "S M T W T F S" (Sunday first, ISO numbers 7,1,2,3,4,5,6; tags `day_<iso>`). Tapping toggles the day: `TemplateActions.setWeekdays(id, newDays)`. When the day was another template's, show `dayMoved = "Monday moves here from Monday / Thursday"` under the chips until the next tap.

**Delete** (own only): a "Delete template" text button at the bottom in `Leak` → `AlertDialog` "Delete <title>?" / "Days you already had keep their history." / "Delete" · "Cancel" → `TemplateActions.delete`, then back.

**New:** `TemplateSection` gets a "+ New template" button (tag `tpl_new`) → an `AlertDialog` with a name field ("New template", Create disabled while blank) → `TemplateActions.create(name)` → `onOpenTemplate(newId)`. Wire `onNewTemplate` in `AppNav`'s `TemplatesKey` entry if you need it, or handle it inside the section.

**Tests:**
- `TemplateEditorViewModelTest.weekday_movesFromABuiltIn`: open an own template, tap `day_1` → `dayMoved == "Monday moves here from Monday / Thursday"`, and `find(MON_THU)!!.weekdays == setOf(4)`.
- `TemplateEditorViewModelTest.deleteTemplate_leavesThePicker`: delete an own template → `TemplateStore.all()` no longer has it.
- `TemplatePickerTest.ownTemplate_isSuggestedOnItsDay`: an own template with weekday 1, the clock on a Monday → its card is `suggested`, Mon/Thu's isn't.

---

## Slice 3: Done (no release)

1. Full suite (the `cmd /c` form), commit each slice as `task-19b slice N: <title>`.
2. `git push origin task-19b/v3.1.0-editor`. **Don't merge** and don't touch `master`: Finish does that.
3. A report of at most 10 lines: the commits, the tests, any real-name differences. End with **"Track B done."** Then stop.
