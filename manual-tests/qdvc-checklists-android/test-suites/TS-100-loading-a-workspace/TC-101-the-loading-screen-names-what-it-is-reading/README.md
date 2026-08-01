---
id: TC-101
kind: test-case
sequence: 2
priority: Medium
type: Usability
status: Draft
created: 2026-08-02
updated: 2026-08-02
---

## The loading screen names what it is reading

The launch read is the one time the app deliberately blocks, so it should say what it is doing rather than show a bare spinner.

#### Preconditions

- A workspace is added, holding several checklists
- At least one item in it has been marked done on a previous day, so `logs/` holds more than one file
