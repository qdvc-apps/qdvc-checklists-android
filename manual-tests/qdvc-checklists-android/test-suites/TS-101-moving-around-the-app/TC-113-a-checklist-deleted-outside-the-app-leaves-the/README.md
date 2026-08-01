---
id: TC-113
kind: test-case
sequence: 7
priority: Medium
type: Regression
status: Draft
created: 2026-08-02
updated: 2026-08-02
---

## A checklist deleted outside the app leaves the Jump list

A checklist that no longer exists on disk should not be offered in Jump after the next launch.

#### Preconditions

- Two checklists have been opened, so both appear in Jump
- Neither is the only checklist in its workspace
