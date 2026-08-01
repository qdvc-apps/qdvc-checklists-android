---
id: TC-123
kind: test-case
sequence: 9
priority: Medium
type: Boundary
status: Draft
created: 2026-08-02
updated: 2026-08-02
---

## A mark made yesterday reads yesterday

The named days are today and yesterday only, and they are judged by calendar day
rather than by hours elapsed — so a mark made late last night reads as yesterday
this morning, not as today.

#### Preconditions

- A checklist is open holding an item that was marked done on the previous calendar day
- This can be arranged by marking an item and then advancing the device date by one day, or by editing the timestamp in `logs/` and relaunching
