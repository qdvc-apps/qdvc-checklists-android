---
id: TS-104
kind: test-suite
sequence: 5
component: workspace-files
tags:
  - interop
created: 2026-08-02
updated: 2026-08-02
---

# What lands in the workspace

The app's changes as they appear on disk, and what it does with files another
program has written.

These cases need a file manager, or a computer with access to the same folder. The
workspace folders are the record — the app keeps only a rebuildable copy — so this
suite is where you establish that the record is correct and that QDVC Checklist
Studio will still be able to read it.

One case covers a defect where appending to a log file written by another program
destroyed two rows.
