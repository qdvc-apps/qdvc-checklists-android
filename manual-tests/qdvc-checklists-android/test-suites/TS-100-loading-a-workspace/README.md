---
id: TS-100
kind: test-suite
sequence: 1
component: workspaces
tags:
  - launch
created: 2026-08-02
updated: 2026-08-02
---

# Loading a workspace

Granting a workspace folder, the launch-time read that populates the app, and
removing a workspace again.

The app has no data of its own beyond a rebuildable cache, so this suite is where
you establish that it can find a workspace at all. A failure here blocks every
other suite.

Because the read happens at launch, most cases involve force-quitting and
reopening the app rather than pressing Back.
