# Daily Block Scheduler — Visual Spec

Describes app version **2.0.0** (the target of `Task-7.md`) · Companion to `schedule-spec.md` and `architecture.md`. The task files hold the exact per-screen specs; this file keeps the shared look and the wording rules (§12).

Dark mode only. Calm, low-saturation, easy on the eyes. No infinite animations (battery), except the Template picker's aurora.

---

## 1. Palette

### Base (warm graphite)

| Token | Hex | Use |
|---|---|---|
| `backgroundTop` | `#27282C` | Top of the page gradient (slightly lifted, hint of slate) |
| `backgroundBottom` | `#1C1B19` | Bottom of the page gradient |
| `background` | `#1C1B19` | Solid fallback (window background, splash) |
| `surface` | `#312F2C` | Cards, bars, sheets |
| `surfaceRaised` | `#3A3834` | Current block container tone, menus, dialogs |
| `outline` | `#46433D` | Dividers, hour grid lines |
| `outlineStrong` | `#56524B` | Focused borders |

Every screen draws the page gradient plus two soft static glows (steel blue top-right, sand bottom-left); see `Task-3.md` §2.

### Accents

| Token | Hex | Use |
|---|---|---|
| `primary` | `#7A9CC6` (steel blue) | Buttons, selected nav item, links, switches |
| `onPrimary` | `#1C1B19` | Text/icons on primary |
| `secondary` | `#D9C38A` (sand) | Secondary highlights, badges, "suggested" chips |
| `onSecondary` | `#1C1B19` | Text/icons on secondary |

### Text

| Token | Hex | Use |
|---|---|---|
| `textPrimary` | `#E8E4DC` | Body, titles |
| `textSecondary` | `#A8A298` | Supporting text, hour labels |
| `textMuted` | `#6F6A62` | Disabled, version label |

### Status

| Token | Hex | Use |
|---|---|---|
| `leak` / `error` | `#D97070` | Leak countdown, now line, overflow border, regressions |
| `leakContainer` | `#4A2C2C` | Overflow segment fill |
| `good` | `#8FB58A` | Improvement trend arrows |
| `neutralTrend` | `#A8A298` | Flat trend arrows |

### Material 3 mapping

`darkColorScheme(background, surface = surface, surfaceContainer = surface, surfaceContainerHigh = surfaceRaised, primary, onPrimary, secondary, onSecondary, error = leak, outline, outlineVariant = outline, onBackground = textPrimary, onSurface = textPrimary, onSurfaceVariant = textSecondary)`.
Block colors and status colors not covered by M3 live in an `@Immutable` `AppColors` object provided through a `CompositionLocal`.

---

## 2. Block colors (each type its own muted hue)

Each type has a **container** (block fill, dark enough for light text) and an **accent** (type label, chart bars, sparklines). Text on containers is always `textPrimary`.

| Block type | Container | Accent |
|---|---|---|
| CV | `#3B3548` | `#A99BC9` (dusty lavender) |
| Coding | `#2E3B4D` | `#7A9CC6` (steel blue) |
| Gym | `#2F3F33` | `#8FB58A` (sage) |
| Free | `#463E2C` | `#D9C38A` (sand) |
| Kid time | `#33312D` | `#9C968C` (warm gray) |
| Dishes | `#2A3E3D` | `#7FB3AE` (muted teal) |
| Evening · Game | `#47332E` | `#C98F7A` (terracotta) |
| Evening · Project | `#333A52` | `#8C9BD1` (slate indigo) |
| Evening · Read | `#3D3F2C` | `#B8B77E` (moss) |
| Evening · Chill | `#43343F` | `#B58FAA` (mauve) |

Colors are fixed per type across all days and screens. Mapping lives in the design system (`BlockType` → `BlockColors`), not in the model.

Special cases:
- **Synthetic Free** (a work block finished early with Next slot): Free colors.
- **Pomodoro breaks** (2.0.0): inside Coding blocks, a band of `secondary` at 16% alpha with 1dp lines at 45%. The current block labels its phases at the right edge: "Focus 2 · 32m left", "Break · 6m left", "Focus 1 ✓"; later Coding blocks show the bands only.
- **Pause, phone-off downtime, missed blocks, emergency stop:** said in words in the recap (§8), never drawn as hatching or unlabeled lines. History marks an emergency day with an "Emergency" chip.

---

## 3. Shape and type

- Corners: blocks 10dp, cards 16dp, hero cards and sheets' top corners 20dp; chips and pills fully rounded.
- Font: **system default (Roboto)**, Material 3 type scale.
- All times and countdowns use tabular figures (`fontFeatureSettings = "tnum"`) so digits don't jitter while ticking.
- Hour labels: `labelSmall`, `textSecondary`. Block title: `titleSmall`. Block time range: `labelSmall`, type accent color.

---

## 4. Home screen

```
┌ Top bar ─────────────────────────────────┐
│ (27) Sunday / Tuesday / Wednesday  [⇄][≡]│  (27) = date badge · ⇄ = swap template · ≡ = History · Settings
├──────────────────────────────────────────┤
│ 08:00 ┊                                  │
│ 09:00 ┊ ▓ CV 09:00–09:30                 │
│       ┊ ▓▓ Coding 09:30–11:30  ←current  │  scrolls; auto-scrolls to current block on open
│ ...   ┊                                  │
├ NowCard (floating) ──────────────────────┤
│ ● Coding            [Focus 2/2]      ⓘ   │
│ 42 min left                 until 11:30  │
│ [   Pause   ]        [  Next: Free  ]    │  Pause = tonal · Next = filled primary
│              Emergency                   │  small red text button
├ Bottom nav ──────────────────────────────┤
│ Home          Weekly          Insights   │
└──────────────────────────────────────────┘
```

- Calendar range: **08:00–23:00**, 80dp per hour (2.0.0), drawn as in `architecture.md` §12 (grid in `drawBehind`, blocks in a custom `Layout`, now line in draw phase).
- Hour grid lines: 1dp `outline`; half-hour lines: 0.5dp `outline` at 50% alpha.
- **Current block emphasis:** container shifts to a slightly lighter tone of its hue, shadow elevation 6dp, scale 1.02; all other blocks at 60% alpha. Shadows barely show on a dark background, so the lighter tone does most of the work. Emphasis changes animate over 250ms, applied inside `graphicsLayer {}` (draw phase only).
- **Now line:** 1.5dp `leak` line drawn **only across the current block's width**, with a 3dp dot at its left edge. Moves once per minute. No current block (kid time, ended, off) → no line. While paused the line keeps moving.
- **Leak:** a `leakContainer` overflow segment with a 1dp `leak` top border grows downward from the block's planned end (drawn over the next block), updated per minute. The now line sits on the overflow segment's bottom edge. The countdown text turns `leak` and shows `-mm:ss` (seconds only while leaking).
- **NowCard:** Pause (tonal) and Next (filled `primary`) share a row; Emergency is a small `leak`-colored text button below them. The Next label always names what you get: "Next: Free" on a work block before its end, "Next: Coding" from free time or when you ran over. Exact states and dialogs: `Task-5.md` Slice 9.
- **Evening choice:** when Dishes ends, a bottom sheet with four large options (Game / Project / Read / Chill) in their block colors. It shows on Home whenever the choice is pending; tapping the notification only brings the app back (Android allows at most 3 notification action buttons, so the choice happens in-app).
- Kid time: a single muted Kid time block; the NowCard shows **Kid asleep** and **End day**.
- **Editing (2.0.0):** tap a block to select it: a 2dp border in its accent, drawn above its neighbours. Handles are 18dp accent circles with a 2dp `surface` ring, centered on the edge line with 48dp touch targets: the top one 24dp in from the block's left end, the bottom one 24dp in from its right end (they never overlap). They sit above the selected block and are never clipped by its shape. While dragging, the edge moves in 5-minute steps and the other blocks slide with a spring (damping 0.85, stiffness 500). The edit card takes the NowCard's place: type dot and name with Done, the live time range, type chips (CV · Coding · Gym · Free), Start/End ±5 steppers, and "Changes apply to today only." Exact layout and captions: `Task-7.md` Slice 9.
- **After the day ends:** the title stays today's template, and Home shows today's recap (§8) instead of the calendar and the NowCard.
- **Debug builds:** while the debug clock is shifted, a small `secondary` chip "Debug clock 20:05" ends the status line.

---

## 5. Template picker (full screen)

- Four cards: Mon/Thu · Sun/Tue/Wed · Fri/Sat · Off.
- Each card shows the full weekday title ("Monday / Thursday"), a friendly subtitle ("Gym day") and a thin strip of its blocks in their colors. Details: `Task-2.md` §D, `Task-3.md` §4.
- The template matching today's weekday gets a `secondary` "Suggested" chip; Off is visually quieter (outlined card).

---

## 6. Weekly tab

- Week selector at top: `‹  Sun 20 – Sat 26 Sep  ›` (week starts Sunday).
- Main visual (2.0.0): **one progress row per block type** in the card "Planned vs actual time": a dot and name, "11h 05m of 13h", a 10dp bar whose track is the type accent at 20% and whose fill is the solid accent, and "1h 30m more than planned" when over. No legend. Exact code: `Task-7.md` Slice 3.
- Above it: a hero card "Plan followed" with a percentage and the caption "16h 05m of the 18h 30m planned for CV, Coding and Gym", plus "Missed: 2h 10m of planned blocks that never happened" when any were missed.
- Below: 2×2 stat tiles (Ran over · Gym sessions · Off days · Evenings skipped), each with a one-line hint under its label. Exact wording: `Task-6.md` Slice 6.
- No chart library.

---

## 7. Insights tab

- **Scorecard with trend arrows vs last week.** One row per metric: metric name, this week's value, arrow + delta.
- Arrow color is judged per metric direction (e.g. leak ↓ = `good`, coding hours ↑ = `good`); flat = `neutralTrend`; worse = `leak`.
- Metrics, each with a one-line hint: Ran over · Coding time · Gym sessions · Job-search days · Evenings skipped · Chill evenings · Pause time · Off days. Above them a headline card (a title and one sentence, counting blocks, not days); below them "Blocks that run long". Exact wording: `Task-6.md` Slice 6; layout: `Task-5.md` Slice 6.

---

## 8. History (menu)

- List of days, newest first, under plain week headers ("This week", "Last week", "13 – 19 Sep"; not sticky, no band), 4dp inside the cards' edge. Each row: date, template name, a thin strip of that day's actual segments in block colors, and a chip ("Ran over 18m", "On time", "Emergency" or "Off day").
- Tap a day → the **recap** (2.0.0), 8dp below the top bar:
  - A summary card: "Plan followed", the percentage, "{worked} of the {planned} planned for CV, Coding and Gym", and pills for what applies ("Ran over 58m", "Paused 15m", "Missed 1 block", "Plan changed", "Template swapped").
  - One card per block in time order: a 4dp accent bar, the title and duration, the time range, pills ("Ran over 47m" red, "Paused 15m" outlined, "Done early" green, "Missed" muted), and a one-line note in plain words ("The last 10m became free time.", "Started 17m late (Coding ran over).", "The day started at 10:40.").
  - Missed rows at 55% alpha; "Day stopped (emergency)" and "Evening skipped" rows where they happened. Rules: `Task-7.md` Slice 5.

## 9. Time Debt (removed)

- Removed in 2.0.0: Weekly, Insights and History already show ran-over time.

## 10. Settings (menu)

- Grouped cards: Notifications (permission, exact alarms, test notification, alert sound), Display (24-hour / 12-hour), Data (share log, reset today, clear history), About ("All data stays on this device"); debug builds add Developer (the debug clock card with "Jump to", "Move forward" and "Evening" groups of two-line buttons, then demo data). Every action answers with a snackbar. Layout: `Task-4.md` §3.4; behavior: `Task-5.md` Slice 3 and `Task-6.md` Slices 7–8.
- Version label `V {versionName}` in `textMuted`, bottom-right.

---

## 11. Icons and motion

- Icons: Material icons in the **outlined** style, from `material-icons-extended` (R8 strips the unused ones in release).
- Motion: 250ms fades/scale for emphasis and sheets; no looping animations except the Template picker's aurora.
- Screen transitions: one 250 ms crossfade for pushing, popping, the predictive back gesture and switching tabs.

## 12. Wording

- The test: would someone who isn't Roni understand the screen?
- User-facing text says "ran over", never "leak" (the color token and the code keep `leak`), and "missed", never "skipped" or "consumed", for blocks ("Evenings skipped" keeps its name).
- Every visual mark has words next to it: no unlabeled colors, lines or hatching.
- Every metric has a one-line hint under its label.
- Weekly and Insights have an ⓘ button that opens "What these mean" (definitions in `Task-6.md` Slice 6.5, with "Ran over" and "Missed blocks" as updated in `Task-7.md` Slices 4–5).
