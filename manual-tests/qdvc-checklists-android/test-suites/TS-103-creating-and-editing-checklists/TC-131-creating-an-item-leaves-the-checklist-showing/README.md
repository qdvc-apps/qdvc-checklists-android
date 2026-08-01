---
id: TC-131
kind: test-case
sequence: 6
priority: Critical
type: Regression
status: Draft
created: 2026-08-02
updated: 2026-08-02
---

## Creating an item leaves the checklist showing

Adding an item must leave you on the same checklist. This previously emptied the
checklist tab and made the new item appear on Home as a checklist in its own
right, until the app was restarted.

#### Preconditions

- A workspace with at least two checklists is added
- One checklist is open on the Checklist tab and has at least one existing item
