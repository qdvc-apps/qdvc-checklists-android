---
id: TC-140
kind: test-case
sequence: 3
priority: Critical
type: Regression
status: Draft
created: 2026-08-02
updated: 2026-08-02
---

## A log file with no final newline is appended to safely

Another program may leave the log without a trailing newline. Appending to it must
not join the new row onto the last one. This previously destroyed both rows.

#### Preconditions

- A checklist with at least two items is open
- Today's log file exists and holds at least one mark
- You can edit files in the workspace with a text editor
