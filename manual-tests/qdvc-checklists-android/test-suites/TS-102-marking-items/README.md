---
id: TS-102
kind: test-suite
sequence: 3
component: completion
tags:
  - core
created: 2026-08-02
updated: 2026-08-02
---

# Marking items

Moving an item between not done, done and skipped, and everything that displays as
a result: the tick, the fast-forward disc, the progress line and the dates.

An item has exactly three states. Only **not done** can become done or skipped,
and both of those can only return to not done — never straight to each other. Two
cases below check that the app enforces this rather than merely discouraging it.
