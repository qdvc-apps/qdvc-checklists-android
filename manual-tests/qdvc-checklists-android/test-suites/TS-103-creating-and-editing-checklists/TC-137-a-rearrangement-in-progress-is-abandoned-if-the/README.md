---
id: TC-137
kind: test-case
sequence: 12
priority: Medium
type: Boundary
status: Draft
created: 2026-08-02
updated: 2026-08-02
---

## A rearrangement in progress is abandoned if the app is quit

An unsaved order is deliberately not remembered: the app should come back in its normal state rather than resuming a half-finished drag.

#### Preconditions

- A checklist with at least four items is open
