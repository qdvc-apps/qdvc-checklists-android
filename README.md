# QDVC Checklists (Android)

A native Android app that opens a **QDVC Checklist Studio** workspace folder,
renders every checklist, and lets you tick items off. Completion state and an
audit trail live in a `logs` folder inside the workspace, so the desktop Studio
and this app never fight over the same files.

- **Language / UI:** Kotlin + Jetpack Compose + Material 3, single Activity.
- **applicationId / namespace:** `qdvc.checklists.android.app`
- **minSdk 26, target/compile 34, JDK 17** (per the app-family spec).

## What it does

- Add one or more workspace folders (SAF tree grants; your files stay in place).
- Browse every checklist in a workspace; open checklists into a multitasking
  switcher.
- On a checklist you get an **information zone** at the top — the checklist's
  description (read from its `README.md`) plus a **Mark all items not done**
  button — above the ordered list of headings and items.
- **Tick an item** → it shows a filled tick, greyed-out strikethrough text, and
  the moment is recorded.
- **Tap a done item** → a dialog tells you when it was completed and offers to
  **un-mark** it.
- Full-text search over a workspace, backed by an on-device Room FTS4 index with
  a status/regenerate surface and a live-scan fallback.
- Light / dark / automatic theming with selectable colour themes (incl. a
  pure-black OLED theme); system bars match the app surface.

## Data format it reads (from Checklist Studio)

The workspace holds one folder per checklist, `<ID>-<slug>`. Each has a
`README.md` with optional `---`-fenced frontmatter carrying `id`, a `#` title
line, and a body used as the description. Inside are node subfolders `NN-<slug>`,
each with a `README.md` whose frontmatter `kind` is `heading` or `item`, a `##`
title line, and a body description. Nodes are ordered by their two-digit
sequence prefix, then by folder name for off-app folders. Any folder containing
a `README.md` is treated as a checklist/node, matching Studio's tolerant loader.
The app only **reads** these files; it never rewrites them.

## Completion tracking and the log (in `logs/`)

The app creates a `logs` folder at the workspace root if absent. Inside:

- `state.csv` — the current done-state per item: `checklist_doc_id`,
  `item_doc_id`, `item_title`, `done`, `marked_at`.
- `log-YYYY-MM-DD.csv` — **one file per day**. Every mark/unmark action appends a
  row: `timestamp`, `action`, `checklist_id`, `checklist_title`, `item_title`,
  `checklist_doc_id`, `item_doc_id`.

Action types are `marked_done`, `marked_not_done`, and — for the bulk button —
`marked_not_done_bulk`. The bulk action writes one row per item (as if each were
unmarked individually) but with the distinct bulk type.

## Deviations from the spec

The spec describes a general folder-backed **editor** family with a View/Edit
pair on Items 2 & 3. This app is read-only over the Studio's files (completion
state is app-owned and lives separately), so:

- **Item 2 (Checklist)** is the interactive checklist: the info zone plus the
  tickable item list. **Item 3 (Info)** is a read-only detail/progress view of
  the current checklist. This keeps the spec's shape (Item 1 = home/browse,
  Item 4 = switcher) while giving Items 2 & 3 app-appropriate meanings, which the
  spec explicitly permits.
- There is no Edit surface or custom-font system, since editing checklist content
  is the desktop Studio's job. Everything else (SAF rules, back-handling, the
  slide animation, Room FTS4 index, JSON themes, system-bar matching) follows
  Part B.

## Build

```
./gradlew assembleDebug
```

## Tests

Pure-Kotlin helpers (Markdown/frontmatter parsing, node-sequence naming, CSV,
FTS query building) are covered in `app/src/test`:

```
./gradlew test
```
