---
name: evidence-schema
description: bugs-evidence folder structure, meta.md frontmatter spec (including optional user/screen/env blocks), status transition rules, and concurrency rules
type: reference
---

# bugs-evidence Folder — Schema & Rules

---

## Location

`tenxengage-blueprint/bugs-evidence/` — **gitignored**. Contains one subfolder per captured bug.

---

## Folder structure

```
bugs-evidence/
  index.html                              # auto-regenerated browse UI (gitignored)
  bug-a3f2b1c2/
    meta.md                               # REQUIRED
    screenshots/
      01-repro.png
      02-full-page.png
    console.log                           # optional
    network.har                           # optional
    notes.md                              # optional freeform notes
```

**Folder naming convention:** `bug-<8hexchars>` — a random ID, never derived from the bug description.

---

## meta.md schema

All fields under `user:`, `screen:`, and `env:` are **optional** — unavailable fields are omitted, never hard-fail the capture.

```markdown
---
slug: bug-a3f2b1c2
captured: 2026-04-23T14:35:12Z
reporter: vijay@tenxengage.com
source: in-app-reporter | mcp-capture | bug-channel-space | manual | claude-paste | bug-fixer-triage
status: pending | in-progress | needs-review | fixed | duplicate | cant-reproduce | wont-fix
mode-hint: M1 | M2 | M3
discovered-during: -   # slug of the parent bug that triggered triage; set by bug-fixer-triage only
affected-repos: [frontend, backend, admin-frontend, admin-backend, contracts]
base-branch: main
ticket: -
fix-mrs: []
fix-commits: []
linked-duplicates: []
root-cause-summary: -          # one-liner populated by bug-fixer at Step 12
last-updated: 2026-04-23T14:35:12Z

# Optional — populated by in-app reporter, MCP capture, or bug-reporter (all best-effort)
user:
  email: vijay@tenxengage.com
  id: usr_abc123
  name: Vijay Anand K
  roles: [admin, course-author]
  tenant-id: tnt_xyz789
  tenant-name: Acme Corp
  feature-flags: [new-import-flow, ai-copilot]

screen:
  url: https://app.example.com/courses/123/edit?tab=rules
  pathname: /courses/123/edit
  query: tab=rules
  route-pattern: /courses/:id/edit
  page-title: Edit Course — Acme Corp
  screen-name: Course Detail / Edit

env:
  build-sha: abc1234
  app-version: 2.3.1
  environment: local
  browser: Chrome 120 / macOS 14.3
  viewport: 1512x982
---

# <Title>

## Observed
<What actually happened.>

## Expected
<What should have happened.>

## Reproduction steps
1. ...

## Notes
<Optional freeform notes from dev/QA.>

<!-- The sections below are appended by bug-fixer at Step 12. Omit until then. -->

## Root cause
<Detailed explanation — populated by bug-fixer.>

## Fix summary
<Per-file change descriptions — populated by bug-fixer.>

## Tests
Framework: <JUnit | Vitest | Playwright>
Added: <test names>
Status: All green after fix

## Learnings promoted
Tier 1: <count> (<files>)
Tier 2: <count>
Tier 3: <count> (<files>)
```

---

## Required fields

Only these are strictly required for a valid evidence folder:

| Field | Notes |
|---|---|
| `slug` | Must equal the folder name (e.g. `bug-a3f2b1c2`) |
| `captured` | ISO 8601 timestamp |
| `status` | Must be one of the unified status vocabulary (see `clickup-lifecycle.md`) |
| `last-updated` | Updated by every writer |

All other fields are optional. bug-fixer Step 0 handles missing fields via normalization.

### Triage-specific fields (only present when `source: bug-fixer-triage`)

| Field | Notes |
|---|---|
| `discovered-during` | Slug of the parent evidence folder (M3), `"inline-<slug>"` for `--inline` runs (M1), or `"standalone-<slug>"` for `--standalone` runs (M2). Set automatically by bug-fixer Phase 0.5.1. Never set manually. |

---

## Status transition rules

Status values must match the unified vocabulary in `clickup-lifecycle.md` exactly.

| Transition | Who performs it |
|---|---|
| `pending` → `in-progress` | bug-fixer Step 0 (after dev says `go`) |
| `in-progress` → `needs-review` | bug-fixer Step 7 (after MRs created) |
| `in-progress` → `cant-reproduce` | bug-fixer Step 2 (graceful give-up) |
| `needs-review` → `fixed` | Manual by dev after deploy verification |
| any → `duplicate` | Manual by dev (Step 1 option `d`) |
| any → `wont-fix` | Manual by dev |

---

## Concurrency rules

- bug-fixer **refuses to start** on a folder whose status is `fixed`, `duplicate`, `wont-fix`, or `cant-reproduce` — unless `--force` flag is passed.
- If status is `in-progress` and `last-updated < 1 hour ago` → warn: "Another run may be active on this folder (last updated: `<time>`). Proceed? [y/n]"
- No global lock file — these checks are advisory.

---

## ID generation rules

Evidence folder IDs are random — never derived from the bug description.

Generate with:
- Node.js: `crypto.randomBytes(4).toString('hex')`
- Shell: `openssl rand -hex 4`

The resulting `bug-<id>` string is both the folder name and the `slug:` field in meta.md.

---

## When index.html is regenerated

Automatically after:
- `bug-reporter --capture` or `--from-evidence` run completes
- `bug-fixer` run that touched an evidence folder completes

Manually:
```bash
node tenxengage-blueprint/.claude/skills/_bug-shared/generate-bug-list.mjs
```

To open:
```bash
open tenxengage-blueprint/bugs-evidence/index.html
```