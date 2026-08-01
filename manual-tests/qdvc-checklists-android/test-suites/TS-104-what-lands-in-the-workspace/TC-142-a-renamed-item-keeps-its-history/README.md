---
id: TC-142
kind: test-case
sequence: 5
priority: High
type: Integration
status: Draft
created: 2026-08-02
updated: 2026-08-02
---

## A renamed item keeps its history

Renaming an item renames its folder, and the app moves that item's logged history
to the new name so nothing is orphaned.

#### Preconditions

- A checklist is open with an item that has been marked done and then not done at least once, so it has several history rows
