---
id: TC-105
kind: test-case
sequence: 6
priority: Medium
type: Negative
status: Draft
created: 2026-08-02
updated: 2026-08-02
---

## Add a folder that holds no checklists

A folder that is not a Studio workspace should be reported plainly rather than failing silently or crashing.

#### Preconditions

- An empty folder exists on the device, with no `checklists/` subfolder
