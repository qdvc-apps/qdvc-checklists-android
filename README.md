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
  switcher ("Jump").
- On a checklist you get an **information zone** at the top — the checklist's
  description (read from its `README.md`), a completion **progress bar**, and a
  **Mark all items not done** button — above the ordered list of headings and
  items.
- Each item shows a tick when done, with greyed-out strikethrough text and, on
  its second line, the date and time it was marked done.
- **Tap an item** → the Info tab opens that item's detail: its current
  done/not-done status, a button to toggle it, and the full history of
  mark/unmark actions recorded for it.
- Full-text search over a workspace (via the magnifier in the checklists view),
  backed by an on-device Room FTS4 index with a live-scan fallback.
- Light / dark / automatic theming with selectable colour themes (incl. a
  pure-black OLED theme); system bars match the app surface.

## The four tabs

1. **Home** — workspaces, then straight into that workspace's checklists.
   Search is a toolbar action within the checklist list.
2. **Checklist** — the info zone (description, progress, bulk-clear) above the
   tickable item list. Tapping an item opens it on the Info tab.
3. **Info** — the selected item's detail: done status, a mark done / not done
   button, and the item's logged history.
4. **Jump** — switch between open checklists; each row shows the checklist ID,
   its name, and its workspace.

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

- **Item 2 (Checklist)** is the interactive checklist: the info zone (with the
  progress bar and bulk-clear) plus the tickable item list. **Item 3 (Info)** is
  the detail view for whichever item you tapped — its done-state, a toggle
  button, and its logged history. This keeps the spec's shape (Item 1 =
  home/browse, Item 4 = jump/switcher) while giving Items 2 & 3
  app-appropriate meanings, which the spec explicitly permits.
- **Item 4 (Jump)** mirrors the switcher in the sibling markdown-notebook app,
  including its layered "Jump" icon.
- There is no Edit surface or custom-font system, since editing checklist content
  is the desktop Studio's job. Everything else (SAF rules, back-handling, the
  slide animation, Room FTS4 index, JSON themes, system-bar matching) follows
  Part B.
- The search index still exists and powers search, but its status/regenerate
  screen was dropped from the UI to keep Home to two levels; the index is
  reconciled quietly in the background.

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
