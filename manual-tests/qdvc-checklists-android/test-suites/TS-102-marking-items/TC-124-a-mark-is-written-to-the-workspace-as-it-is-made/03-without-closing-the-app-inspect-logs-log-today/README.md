---
kind: test-step
sequence: 3
---

### Without closing the app, inspect `logs/log-<today>.csv`

#### Expected result

The file exists and its last row records this mark: today's timestamp, the action `marked_done`, the client `android-app`, and the checklist and item folder names.
