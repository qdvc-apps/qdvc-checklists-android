---
id: TC-119
kind: test-case
sequence: 5
priority: Medium
type: Negative
status: Draft
created: 2026-08-02
updated: 2026-08-02
---

## Skipping is offered only for an untouched item

Going straight from done to skipped, or skipped to done, is not allowed. The menu
should not offer it, rather than offering it and then behaving oddly.

#### Preconditions

- A checklist is open with one item marked done, one marked skipped, one not done, and at least one heading
