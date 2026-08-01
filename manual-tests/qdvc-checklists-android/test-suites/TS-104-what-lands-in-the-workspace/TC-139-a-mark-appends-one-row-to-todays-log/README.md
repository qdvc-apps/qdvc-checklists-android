---
id: TC-139
kind: test-case
sequence: 2
priority: Critical
type: Integration
status: Draft
created: 2026-08-02
updated: 2026-08-02
---

## A mark appends one row to today's log

Each mark adds exactly one row, leaving earlier rows untouched.

#### Preconditions

- A checklist with at least three items is open
- You can read `logs/log-<today>.csv` in the workspace
