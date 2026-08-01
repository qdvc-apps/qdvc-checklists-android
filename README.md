# QDVC Checklists (Android)

A native Android app that opens a **QDVC Checklist Studio** workspace folder,
renders every checklist, lets you tick items off, and lets you create and edit
checklists, headings, and items. Completion state and an audit trail live in a
`logs` folder inside the workspace.

- **Language / UI:** Kotlin + Jetpack Compose + Material 3, single Activity.
- **applicationId / namespace:** `qdvc.checklists.android.app`
- **minSdk 26, target/compile 34, JDK 17** (per the app-family spec).

## What it does

- Add one or more workspace folders (SAF tree grants; your files stay in place).
- Browse every checklist in a workspace; open checklists into a multitasking
  switcher ("Jump").
- On a checklist you get an **information zone** at the top — the checklist's
  name and description (read from its `README.md`), a completion **progress
  bar**, and a **Mark all items not done** button — above the ordered list of
  headings and items.
- Each item shows a tick when done, with greyed-out strikethrough text and, on
  its second line, the date and time it was marked done.
- An item can also be **skipped** — passed over deliberately rather than
  completed. A skipped item reads like a done one (greyed, struck through, with
  the time it was settled) but carries a muted fast-forward icon instead of an
  accented tick. Skipping is offered from the Info tab's menu, and is only
  reachable from not-done: a skipped or done item must be returned to not-done
  first, so the two settled states can never be confused for one another.
- The progress bar splits three ways — accented for done, muted for skipped,
  empty for the rest — above a line reading e.g. "4 done, 1 skipped,
  2 remaining".
- **Tap an item or heading** → the Info tab opens its detail: for an item, its
  done/not-done status and a toggle button; for a heading, a sensible read-only
  panel (headings have no done-state); plus the full logged history.
- **Create and edit** — a New checklist option on Home, and per-checklist
  options to edit its ID/name/description, add a heading or item, and rearrange
  headings and items. **Rearrange items** unlocks the list for drag-to-reorder:
  press and hold a row and drag it, with the toolbar menu replaced by Cancel and
  Save (each of which confirms first) and the tab bar sliding out of the way
  until you leave the mode. The draft order is never persisted, so quitting
  mid-rearrange simply discards it. Items and headings can be renamed from the Info tab. New
  checklists/items must not collide with an existing ID or name (the studio's
  exact-ID, case-insensitive-title rule). Every create, edit, rename, and
  reorder is recorded in the log.
- Full-text search over a workspace (via the Home menu), served from the
  on-device projection (Room FTS4).
- A **loading screen** on launch: the app reads every workspace from disk once,
  narrating its progress as a terminal-style transcript, and only then shows the
  UI. After that, nothing in normal use traverses the filesystem to read.
- Light / dark / automatic theming with selectable colour themes (incl. a
  pure-black OLED theme); system bars match the app surface.

## The four tabs

1. **Home** — workspaces, then straight into that workspace's checklists. The
   workspaces toolbar menu holds Settings; the checklists toolbar menu holds
   Search, Index status, and New checklist.
2. **Checklist** — toolbar title is the checklist ID; the info zone shows the
   name, description, progress, and bulk-clear, above the tickable list. The
   toolbar menu edits the checklist, adds an item/heading, or rearranges. Tapping
   any row opens it on the Info tab.
3. **Info** — the selected item/heading's detail panel (styled like the
   Checklist info zone), a toggle button for items, its logged history, and a
   toolbar menu to edit its name/description.
4. **Jump** — switch between open checklists; each row shows the checklist ID,
   its name, and its workspace.

## Data format it reads (from Checklist Studio)

The workspace root contains two folders: `checklists/` (the checklist data) and
`logs/` (this app's bookkeeping, described below). Inside `checklists/` there is
one folder per checklist, `<ID>-<slug>`. Each has a `README.md` with optional
`---`-fenced frontmatter carrying `id`, a `#` title line, and a body used as the
description. Inside are node subfolders `NN-<slug>`, each with a `README.md`
whose frontmatter `kind` is `heading` or `item`, a `##` title line, and a body
description. Nodes are ordered by their two-digit sequence prefix, then by folder
name for off-app folders. Any folder under `checklists/` containing a `README.md`
is treated as a checklist, matching Studio's tolerant loader.

The app reads these files, and (via the create/edit/reorder actions) writes them
using the same on-disk format. New checklist folders are created inside
`checklists/`.

## Completion tracking and the log (in `logs/`)

The app creates a `logs` folder at the workspace root if absent. Inside are the
daily logs:

- `log-YYYY-MM-DD.csv` — **one file per day**. Every action appends a row:
  `timestamp`, `action`, `client`, `checklist_folder`, `item_folder`.

There is no `state.csv`. **The daily logs are the source of truth on disk**: the
done-state of every item is derived by replaying all the logs in timestamp order
(last write wins). That replay happens once per launch, into the app's local
projection (see below); the UI then reads completion from there.

Marks are appended to today's log **immediately** when you tick something — in
append mode, so adding a row costs one row's worth of writing rather than
rewriting the day's file. If another client has left the file without a final
newline, the missing separator is supplied first, so a row is never spliced onto
the previous one.

"Mark all items not done" is exactly equivalent to unmarking each item in turn,
so it clears skipped items as well as done ones.

The `client` column records which app wrote the row; this app writes
`android-app`.

Items are identified only by their **workspace-relative folder names** (e.g.
`BCL091-resupply-lunar-base` and `01-power-check`). The app never writes SAF
document ids or absolute paths, so the log files never reveal where the
workspace lives on the device or what the workspace folder itself is called.

Action types include `marked_done`, `marked_skipped`, `marked_not_done`,
`marked_not_done_bulk` (the bulk button, one row per item), and the structural
actions
`created_checklist`, `created_item`, `created_heading`, `renamed_checklist`,
`renamed_item`, `edited_checklist`, `edited_item`, and `reordered_nodes`.

When a checklist, heading, or item is **renamed**, its folder name changes, so
the app rewrites every daily log — replacing the old `checklist_folder` /
`item_folder` values with the new ones — so historical records stay attached to
the renamed thing. The rename itself is also logged.

## The local projection and its status page

Your workspace folders remain the system of record. On launch the app reads them
in full — every checklist, every node, every log row — into a local Room
database, and from then on **the UI reads only from that projection**. Opening a
checklist, switching between open checklists, ticking an item and viewing an
item's history are all SQL queries, so they don't touch the Storage Access
Framework at all.

Writes go the other way, and go immediately. Every create, edit, reorder and mark
is written to the workspace as soon as you perform it — never queued, never
batched, never handed to a background scheduler — because the files are shared
with the desktop Studio and a deferred write is a write that can be lost. Marks
update the projection first (so the UI responds at once) and then append to the
log; if that append fails, the projection is rolled back and you're told.
Structural changes are written to disk first, since that is where naming and
uniqueness are validated, and the projection is then refreshed from the result.

Because the projection never holds anything that doesn't already exist on disk,
it stays disposable: it can be deleted or rebuilt without data loss. The **Index
status** page (from the checklists toolbar menu) shows how many checklists are
indexed and when it was last rebuilt, and offers **Regenerate now**, which
re-reads that workspace from disk behind the same loading screen. Regenerating
only touches the app's private projection — your files are never modified.

External edits (from the desktop Studio, say) are picked up on the next launch or
by **Regenerate now**; the app does not currently watch the folders while running.

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
- The index status/regenerate screen lives inside the all-checklists view rather
  than as its own Home level, keeping Home to two levels.

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
