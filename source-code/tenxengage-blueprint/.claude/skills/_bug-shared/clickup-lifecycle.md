---
name: clickup-lifecycle
description: SINGLE SOURCE OF TRUTH — unified bug status vocabulary, transition rules, custom fields, and ClickUp comment templates used by both bug-reporter and bug-fixer
type: reference
---

# Bug Lifecycle — Single Source of Truth

Both `bug-reporter` and `bug-fixer` read status names, transition rules, and comment templates **only from this file**. Never define or override status strings anywhere else.

---

## Unified status vocabulary

These exact strings are used in **both** ClickUp custom statuses **and** `bugs-evidence/<slug>/meta.md`.

| Status | Meaning |
|---|---|
| `pending` | Captured or reported; no fix work started |
| `in-progress` | Step 0 normalization passed and dev said `go`; actively being worked |
| `needs-review` | All MRs created (bug-fixer Step 7); awaiting human review + merge + deploy verification |
| `fixed` | Deploy verified; manually closed by dev |
| `duplicate` | Linked to another bug as duplicate (still requires deliberate fix or consolidation) |
| `cant-reproduce` | Step 2 graceful give-up; handed back to reporter |
| `wont-fix` | Manually set; intentional no-op (stale, by-design, deprecated area) |

---

## Allowed transitions

```
pending ──▶ in-progress ──▶ needs-review ──▶ fixed
                │
                ▼
           cant-reproduce

any status ──▶ duplicate     (manual, via Step 1 duplicate-detection)
any status ──▶ wont-fix      (manual)
```

**Transitions that bug-fixer performs automatically (M3 only):**

| Trigger | From | To |
|---|---|---|
| Step 0 normalization + dev `go` | `pending` | `in-progress` |
| Step 7 MR creation | `in-progress` | `needs-review` |
| Step 2 graceful give-up | `in-progress` | `cant-reproduce` |
| `--wont-fix` invocation | any | `wont-fix` |

Step 12 posts the "Fix complete" comment but does **not** change status — status is already `needs-review` from Step 7.

**Transitions that are ALWAYS manual (dev action required):**

| Transition | When |
|---|---|
| `needs-review` → `fixed` | After deploy verification |
| any → `duplicate` | After Step 1 duplicate-detection (dev picks option `d`) |

**Rule: skill never auto-closes.** Deploy verification is a human responsibility.

---

## Custom fields (ClickUp workspace-level)

Configure these in ClickUp before using M3 mode. If missing, bug-reporter prints setup instructions and continues with built-in fields only.

| Field name | ClickUp type | Values |
|---|---|---|
| `base_branch` | Text | Branch the fix should target (e.g., `main`) |
| `affected_repos` | Labels (multi-select) | `backend`, `frontend`, `admin-backend`, `admin-frontend`, `contracts` |
| `reporter_source` | Dropdown | See `payload-schema.md` enum |
| `fix_mrs` | Text | Comma-separated MR URLs, set after Step 7 |
| `fix_branch` | Text | Shared branch name, set at Step 0 |
| `linked_duplicates` | Text | Comma-separated ticket IDs / evidence slugs |

---

## Comment templates

Bug-fixer posts these verbatim (M3 only). Substitute placeholders at runtime.

### On `go` (Step 0 completes, dev approves)

```
Fix started.

Mode: M3 (Tracked)
Branch: `{BRANCH_NAME}`
Worktrees:
  - {REPO_1}: /tmp/bugfix-{SLUG}-{REPO_1}/
  - {REPO_2}: /tmp/bugfix-{SLUG}-{REPO_2}/

Affected repos: {AFFECTED_REPOS}
Base branch: {BASE_BRANCH}

Reproduction steps (as understood):
{REPRO_STEPS_NUMBERED_LIST}
```

### After visual re-confirmation (Step 6, if screenshots captured)

```
Visual re-confirmation complete.

Before screenshot: [view](/tmp/bug-{SLUG}-before.png)
After screenshot: [view](/tmp/bug-{SLUG}-after.png)
Vision check: {VISION_SUMMARY}
```

### After MRs created (Step 7)

```
MRs opened:
{MR_LIST}

Tag applied: `{TAG_NAME}` in each affected repo.
Status → needs-review. Awaiting human review, merge, and deploy verification.
```

### On fix complete (Step 12, M3 only)

```
Fix complete.

Root cause:
{ROOT_CAUSE}

Fix summary:
{FIX_SUMMARY_PER_FILE}

Tests:
  Framework: {TEST_FRAMEWORK}
  Added: {TEST_NAMES}
  Status: All green after fix

MRs:
{MR_LIST}

Learnings promoted:
  Tier 1: {TIER_1_COUNT} ({TIER_1_FILES})
  Tier 2: {TIER_2_COUNT}
  Tier 3: {TIER_3_COUNT} ({TIER_3_FILES})

{ACTION_REQUIRED_BLOCK — omit this line entirely if no manual steps were flagged}
```

### On wont-fix (`--wont-fix` invocation)

```
Closed as won't fix.

Reason: {REASON}

No fix will be implemented. Reopen with additional context if still occurring.
```

### On graceful give-up (Step 2)

```
Could not reproduce this bug.

What was tried:
{REPRODUCTION_ATTEMPTS}

Evidence missing:
{MISSING_EVIDENCE}

Questions for reporter:
{QUESTIONS}

Status → cant-reproduce. Please add more details and re-open if still occurring.
```

---

## ClickUp setup instructions (print if custom fields missing)

```
To set up required custom fields in ClickUp:

1. Open your ClickUp workspace → Settings → Custom Fields
2. Add the following fields to your bug list:
   - "base_branch" (Text)
   - "affected_repos" (Labels: backend, frontend, admin-backend, admin-frontend, contracts)
   - "reporter_source" (Dropdown: manual, browser-inapp, mcp-capture, bug-channel-space, bug-reporter, other)
   - "fix_mrs" (Text)
   - "fix_branch" (Text)
   - "linked_duplicates" (Text)
3. Also configure these custom statuses on the list (matching exactly):
   pending | in-progress | needs-review | fixed | duplicate | cant-reproduce | wont-fix

These fields are optional for V1 — bug-reporter and bug-fixer will proceed without them.
```