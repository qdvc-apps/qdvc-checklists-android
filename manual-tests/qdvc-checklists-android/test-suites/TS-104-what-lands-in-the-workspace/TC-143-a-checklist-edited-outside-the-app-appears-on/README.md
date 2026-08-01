---
id: TC-143
kind: test-case
sequence: 6
priority: High
type: Integration
status: Draft
created: 2026-08-02
updated: 2026-08-02
---

## A checklist edited outside the app appears on the next launch

The app reads the workspace at launch. An external edit is therefore expected to
appear after a restart or **Regenerate now**, and not before.

#### Preconditions

- A workspace is added and open at its checklist list
- You can edit files in the workspace
