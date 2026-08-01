---
id: ws-qdvc-checklists-android
kind: test-workspace
format: qdvc-test-workspace/1
name: QDVC Checklists (Android)
product: QDVC Checklists 1.0 (Android)
created: 2026-08-02
suites: 7
cases: 55
---

# QDVC Checklists (Android)

Manual tests for the QDVC Checklists Android app: the phone client that reads a
QDVC Checklist Studio workspace folder from device storage and lets you work
through its checklists.

Everything the app shows is read from your workspace folder, and every change you
make is written back to it. These tests therefore modify real files.

#### Standing rules

- **Test against a scratch copy of a workspace, never one you care about.** Copy a
  Studio workspace to a fresh folder on the device and grant that. Several cases
  ask you to delete, rename or hand-edit files.
- The app reads a workspace in full **when it launches**. Edits made outside the
  app while it is running are not picked up until the next launch or until
  **Regenerate now**. Where a case depends on that, it says so.
- Where a case says "force-quit", use the system app switcher to swipe the app
  away rather than pressing Back, so no state is saved on the way out.
- Take a fresh copy of the workspace between suites if a case has left files in an
  odd state. Nothing here is designed to corrupt a workspace, but several cases
  deliberately create unusual files.
- The tabs are named **Home**, **Checklist**, **Info** and **Jump** along the
  bottom. Cases refer to them by those names.

#### Contents

- `test-suites/` — one folder per suite, each holding one folder per case, each
  holding one folder per step.
- There is no `test-runs/` or `logs/` folder: nothing here has been executed yet.
  Both appear once you record a first run.
