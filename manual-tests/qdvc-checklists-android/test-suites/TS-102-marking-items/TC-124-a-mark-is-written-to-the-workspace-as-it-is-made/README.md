---
id: TC-124
kind: test-case
sequence: 10
priority: Critical
type: Integration
status: Draft
created: 2026-08-02
updated: 2026-08-02
---

## A mark is written to the workspace as it is made

Marks are written to the workspace immediately, not batched or deferred, so that a
change cannot be lost if the app is killed.

#### Preconditions

- A checklist is open
- You can browse the workspace folder on the device
