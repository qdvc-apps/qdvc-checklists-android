"""Check a QDVC Test Management workspace against the published format.

Usage:  python3 validate-workspace.py [path-to-workspace]

Catches the mistakes hand-editing tends to introduce and that the importer
absorbs silently rather than reporting: a value outside a vocabulary (a typo in
`priority` becomes `Critical`), a step with no expected result, a wrong heading
level, YAML the importer's small dialect cannot read, or step folders whose
numbering has drifted out of step with their `sequence` field.

Exits non-zero if anything is wrong, so it can be used in a hook or a CI job.
No dependencies beyond the standard library.

Format reference:
https://github.com/qdvc-apps/test-management/blob/main/paperwork/WORKSPACE-FORMAT.md
"""

import os, re, sys

PRIORITY = ["Critical", "High", "Medium", "Low"]
TYPE = ["Functional", "Regression", "Smoke", "Integration", "Boundary",
        "Negative", "Usability", "Performance", "Security"]
STATUS = ["Draft", "In review", "Approved", "Deprecated"]

problems = []
def bad(path, msg):
    problems.append(f"{path}: {msg}")

def parse(path):
    raw = open(path, "rb").read()
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        bad(path, "not valid UTF-8"); return None, None
    if b"\r\n" in raw:
        bad(path, "CRLF line endings")
    if not raw.endswith(b"\n"):
        bad(path, "no trailing newline")
    lines = text.split("\n")
    if lines[0] != "---":
        bad(path, "does not start with frontmatter"); return None, None
    try:
        end = lines.index("---", 1)
    except ValueError:
        bad(path, "unterminated frontmatter"); return None, None

    fm, key = {}, None
    for ln in lines[1:end]:
        if ln.startswith("  - "):
            if key is None: bad(path, f"sequence item with no key: {ln!r}")
            else: fm.setdefault(key, []).append(ln[4:])
            continue
        m = re.match(r"^([A-Za-z_][\w-]*):\s*(.*)$", ln)
        if not m:
            bad(path, f"unparseable frontmatter line: {ln!r}"); continue
        key, val = m.group(1), m.group(2)
        if val == "":
            fm[key] = []          # block sequence follows
        else:
            if val.startswith("[") or val.startswith("{"):
                bad(path, f"flow collection not allowed: {ln!r}")
            if val in ("|", ">"):
                bad(path, f"multi-line scalar not allowed: {ln!r}")
            if val.startswith("&") or val.startswith("*"):
                bad(path, f"anchor/alias not allowed: {ln!r}")
            fm[key] = val.strip('"')
            key = None
    return fm, "\n".join(lines[end + 1:])

def headings(body):
    return [(len(m.group(1)), m.group(2))
            for m in re.finditer(r"^(#{1,6})\s+(.*)$", body, re.M)]

def check(path, expect_kind, expect_level, sections_required=()):
    fm, body = parse(path)
    if fm is None: return None
    if fm.get("kind") != expect_kind:
        bad(path, f"kind is {fm.get('kind')!r}, expected {expect_kind!r}")
    hs = [h for h in headings(body) if h[0] <= 3]
    if len(hs) != 1:
        bad(path, f"expected exactly one title heading (level 1-3), found {len(hs)}")
    elif hs[0][0] != expect_level:
        bad(path, f"title is level {hs[0][0]}, expected {expect_level}")
    elif not hs[0][1].strip():
        bad(path, "title is empty")
    for s in sections_required:
        if f"#### {s}" not in body:
            bad(path, f"missing '#### {s}' section")
    for lvl, txt in headings(body):
        if lvl > 4:
            bad(path, f"heading deeper than #### : {txt!r}")
    return fm

def validate(root):
    global problems
    problems = []
    ws = check(f"{root}/README.md", "test-workspace", 1)
    if ws and "name" not in ws:
        bad(f"{root}/README.md", "workspace has no name")

    sdir = f"{root}/test-suites"
    if not os.path.isdir(sdir):
        bad(root, "no test-suites/ folder"); return
    case_ids, seen_cases = [], 0
    for suite in sorted(os.listdir(sdir)):
        sp = f"{sdir}/{suite}"
        if not os.path.isdir(sp): continue
        if not re.match(r"^TS-\d{3}-[a-z0-9-]+$", suite):
            bad(sp, f"suite folder name not TS-<nnn>-<slug>: {suite}")
        sfm = check(f"{sp}/README.md", "test-suite", 1)
        if sfm and sfm.get("id") and sfm["id"] != f"TS-{suite.split('-')[1]}":
            bad(sp, f"id {sfm['id']} disagrees with folder {suite}")
        for case in sorted(os.listdir(sp)):
            cp = f"{sp}/{case}"
            if not os.path.isdir(cp): continue
            if not re.match(r"^TC-\d{3}-[a-z0-9-]+$", case):
                bad(cp, f"case folder name not TC-<nnn>-<slug>: {case}")
            cfm = check(f"{cp}/README.md", "test-case", 2)
            seen_cases += 1
            if cfm:
                case_ids.append(cfm.get("id"))
                for f, vocab in [("priority", PRIORITY), ("type", TYPE), ("status", STATUS)]:
                    if f in cfm and cfm[f] not in vocab:
                        bad(cp, f"{f} {cfm[f]!r} outside vocabulary (would silently become {vocab[0]!r})")
            steps = sorted(d for d in os.listdir(cp) if os.path.isdir(f"{cp}/{d}"))
            if not steps:
                bad(cp, "case has no steps")
            for i, step in enumerate(steps, start=1):
                tp = f"{cp}/{step}"
                if not re.match(r"^\d{2}-[a-z0-9-]+$", step):
                    bad(tp, f"step folder name not NN-<slug>: {step}")
                elif int(step[:2]) != i:
                    bad(tp, f"step prefix {step[:2]} is not consecutive (expected {i:02d})")
                tfm = check(f"{tp}/README.md", "test-step", 3, sections_required=["Expected result"])
                if tfm and "id" in tfm:
                    bad(tp, "steps must not have an id")
                if tfm and str(tfm.get("sequence")) != str(i):
                    bad(tp, f"sequence {tfm.get('sequence')} disagrees with folder prefix {i}")
    dupes = [i for i in set(case_ids) if case_ids.count(i) > 1]
    if dupes: bad(root, f"duplicate case ids: {dupes}")
    for d in ("test-runs", "logs"):
        if os.path.isdir(f"{root}/{d}"):
            print(f"  note: {d}/ present")
    return seen_cases

if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "qdvc-checklists-android"
    )
    if not os.path.isdir(root):
        print(f"no workspace at {root}")
        sys.exit(2)
    cases = validate(root)
    print(f"{root}: {cases} cases, {len(problems)} problem(s)")
    for p in problems:
        print(f"  {p}")
    sys.exit(1 if problems else 0)
