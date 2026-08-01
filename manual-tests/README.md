# Manual tests

A [QDVC Test Management](https://github.com/qdvc-apps/test-management) workspace
holding the manual test plan for this app: **7 suites, 55 cases, 206 steps**.

```
manual-tests/
├── README.md                     this file
├── validate-workspace.py         format checker
└── qdvc-checklists-android/      the workspace — import or zip this folder
    ├── README.md                 kind: test-workspace
    └── test-suites/
        ├── TS-100-loading-a-workspace/
        ├── TS-101-moving-around-the-app/
        ├── TS-102-marking-items/
        ├── TS-103-creating-and-editing-checklists/
        ├── TS-104-what-lands-in-the-workspace/
        ├── TS-105-search-and-index-status/
        └── TS-106-appearance-and-feedback/
```

Point the app at `qdvc-checklists-android/`, not at `manual-tests/` — this file
and the validator are not part of the workspace.

## The suites

| Suite | Component | Cases | Covers |
|---|---|---:|---|
| TS-100 | `workspaces` | 7 | Granting a folder, the launch-time read, removing a workspace, unreadable storage |
| TS-101 | `navigation` | 8 | Opening and switching checklists, Back, keeping your place in a list |
| TS-102 | `completion` | 11 | Done, skipped and not done; progress; dates; writes reaching disk |
| TS-103 | `authoring` | 12 | Creating and editing checklists, items and headings; drag to reorder |
| TS-104 | `workspace-files` | 7 | What the app writes, and what it does with files another program wrote |
| TS-105 | `search` | 5 | Finding checklists, and the index status page |
| TS-106 | `presentation` | 5 | Themes, icon legibility, haptic feedback |

Eight cases are typed `Regression` and exist because the behaviour was once
wrong. Each names the defect in its body so the case still makes sense once the
memory of it has gone:

| Case | Was |
|---|---|
| TC-110, TC-111 | Lists jumped back to the top whenever a tab was left |
| TC-112 | A checklist created in the session opened a *different* checklist |
| TC-113 | A checklist deleted on disk stayed in the Jump list |
| TC-131, TC-134 | Creating or editing an item emptied the checklist and added a bogus one to Home |
| TC-140 | Appending to a log with no final newline destroyed two rows |

## Before executing

Every case in TS-103 and TS-104 writes to a workspace, and several ask you to
delete or hand-edit files. **Work against a scratch copy of a Studio workspace,
never one you care about.** The workspace `README.md` sets out the rest of the
standing rules.

Nothing here has been run yet: there is no `test-runs/` or `logs/` folder, which
is the correct starting state for a plan. Both appear once you record a first run.

Every case is `status: Draft`. That is deliberate — nobody has reviewed them, and
the format's own guidance is that a workspace full of `Approved` cases nobody
reviewed is worse than one that admits to being a draft. Promote them as you
execute and confirm they are worth keeping. Suites carry no `owner` for the same
reason; fill it in if the field is useful to you.

## Checking the format after editing

```sh
python3 manual-tests/validate-workspace.py
```

Hand-editing these files is expected, and the importer is forgiving in ways that
hide mistakes — a typo in `priority` silently becomes `Critical` rather than
failing. The validator reports those cases instead: vocabulary values that would
be silently replaced, steps with no expected result, wrong heading levels, YAML
outside the importer's small dialect, and step numbering that has drifted from the
`sequence` field. It exits non-zero on any problem, so it works in a hook or a CI
job.
