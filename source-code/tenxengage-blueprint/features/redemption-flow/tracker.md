# Tracker — redemption-flow

**Last updated:** 2026-05-26 by pushpendra
**Feature PR (Blueprint):** https://gitlab.com/genidev/tenxengage-new/tenxengage-blueprint/-/merge_requests/2 _(merged)_
**Feature PR (Contracts):** https://gitlab.com/genidev/tenxengage-new/tenxengage-contracts/-/merge_requests/4 _(merged)_
**Feature PR (BE):** https://gitlab.com/genidev/tenxengage-new/tenxengage-backend/-/merge_requests/8 _(merged)_
**Feature PR (FE):** https://gitlab.com/genidev/tenxengage-new/tenxengage-frontend/-/merge_requests/3 _(merged)_

> **Step 0 — Contracts (run before any foundation or story work):**
> `cd ../tenxengage-contracts && /generate-contracts redemption-flow`
> FE story sessions can start immediately after contracts are generated.
> Contracts generated: **yes**

---

## Foundation tasks (sequential)

| # | Task | Status | Session | Started | Completed | Duration | Commit | Notes |
|---|---|---|---|---|---|---|---|---|
| F1 | Enums | done | 20260521T100814-rf7k | 2026-05-21T10:08:14Z | 2026-05-21T10:21:00Z | 13m | 2886e7b1b370 | — |
| F2 | Flyway migrations (schema) | done | 20260521T104537-rf8m | 2026-05-21T10:45:37Z | 2026-05-21T10:52:00Z | 7m | 7165c6a1b42d | — |
| F3 | Base entities + repositories + fixtures | done | 20260521T105538-rf9p | 2026-05-21T10:55:38Z | 2026-05-21T11:18:00Z | 22m | f510008f3dce | — |
| F4 | Permissions seed | done | 20260521T112547-rfap | 2026-05-21T11:25:47Z | 2026-05-21T13:10:00Z | 1h 44m | 0e5bb7c9e15e | — |
| F5 | BE plumbing — Kafka | done | 20260521T131500-rf5q | 2026-05-21T13:15:00Z | 2026-05-21T14:05:00Z | 50m | 7f8a580794c0 | — |

_`Commit` column: squash-merge SHA on the feature branch after the sub-branch is merged locally. `Duration`: elapsed time from claim to done for this session (e.g., `1h 23m`)._

---

## Stories

_BE and FE status track independently. FE `done` = wired to real BE + E2E passes (not just mocked). Use `N/A` for the layer that doesn't apply. `BE Tests` / `FE Tests`: written by load-story after full suite passes. `Mockup`: `N/A` = BE-only story; `—` = FE story, no mockup created (optional)._

| US | Title | Layers | Seed | Depends on | BE | BE Tests | FE | FE Tests | Mockup | Commit (BE) | Commit (FE) | Duration (BE) | Duration (FE) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| US-01 | Submit personal wallet redemption | BE + FE | F-03.S-01 | Foundation | done | green @ b309b5e9526a | done | green @ 11453c2b8183 | — | b309b5e9526a | 11453c2b8183 | — | 36m | BE completed 2026-05-22T00:00:00Z; FE completed 2026-05-22T05:45:02Z |
| US-02 | Submit company wallet redemption | BE + FE | F-03.S-02 | Foundation, US-01 | done | green @ 8c79a2a02a28 | done | green @ 1ef15ca65108 | — | 8c79a2a02a28 | 1ef15ca65108 | — | 1h 8m | BE completed 2026-05-22T00:10:00Z; FE completed 2026-05-22T07:13:00Z |
| US-03 | Batch redemption processor | BE | F-03.S-05 | Foundation, US-01 | done | green @ ba1bae48ce3b | N/A | N/A | N/A | ba1bae48ce3b | N/A | — | N/A | BE completed 2026-05-25T00:00:00Z |
| US-04 | Partner redemption notifications | BE | F-03.S-07 | Foundation, US-01 | done | green @ 07d08abfb82c | N/A | N/A | N/A | 07d08abfb82c | N/A | — | N/A | BE completed 2026-05-22T01:00:00Z |
| US-05 | Route cash redemptions to XTRM | BE | F-03.S-03 | Foundation, US-01 | blocked | N/A | N/A | N/A | N/A | — | N/A | — | N/A | BLOCKED: XTRM profile setup incomplete (TransferFund API not working) |
| US-06 | Route non-cash redemptions to Xoxoday | BE | F-03.S-04 | Foundation, US-01 | blocked | N/A | N/A | N/A | N/A | — | N/A | — | N/A | BLOCKED: Xoxoday API credentials not available |
| US-07 | Process vendor webhooks idempotently | BE | F-03.S-06 | Foundation, US-05, US-06 | blocked | N/A | N/A | N/A | N/A | — | N/A | — | N/A | BLOCKED: depends on US-05 + US-06 |

---

## Cross-story integration tests

_`test-plan.md` covers full-lifecycle, contract conformance, state machine, business rules, tenant isolation, and audit/event tests. The feature is **not ready to ship** until this row is `done`._

| # | Scope | Status | Session | Started | Completed | Commit | Notes |
|---|---|---|---|---|---|---|---|
| T1 | All `test-plan.md` scenarios passing | done | — | 2026-05-26T06:00:00Z | 2026-05-26T06:10:00Z | 40a27d6 | 16/16 green |

---

## Blocked / needs attention

- **US-05** — Blocked. XTRM profile setup incomplete; TransferFund API not working. Vijay notified 2026-05-21.
- **US-06** — Blocked. Xoxoday API credentials not yet provided. Vijay notified 2026-05-21.
- **US-07** — Blocked. Depends on US-05 + US-06 (vendor webhook payload shapes not yet known).

---

## Session protocol

**Branching model:**
- Feature branch (long-lived, per repo): `features/redemption-flow`
- Sub-branch per session (local-only, never pushed): `work/redemption-flow-{unit-id}` e.g. `work/redemption-flow-F1-enums`, `work/redemption-flow-US-01-be`, `work/redemption-flow-US-01-fe`
- **No GitHub PR for sub-branches.** Sub-branch → feature-branch = local `git merge --squash` after developer approval in chat.
- **Only one PR per feature per repo:** `features/redemption-flow` → `roadmaps/redemption-store`, opened manually when the tracker is all-green.

**Starting a session:**
1. Read this tracker
2. Pick an eligible row: `status = not-started`, all deps = `done`
3. Flip status to `in-progress`, add your session ID + start timestamp
4. Commit this one-line tracker change as your **first commit** in the blueprint repo; push with retry-on-reject
5. In the sibling repo (backend or frontend): ensure the feature branch exists; create sub-branch off the feature branch

**During a session:**
- Never re-verify work already marked `done` — trust prior sessions
- Work through the story/task file's `## Execution checklist`, checking items `[x]` as you complete them
- Run scoped tests per item (inner loop), full layer suite before approval

**Ending a session (on success):**
1. Run the unit's "Done when" checks — all green
2. **Pause for developer approval** in chat: show one-paragraph summary + `git diff --stat` of sub-branch vs feature branch
3. Wait for 'merge' or 'change X' reply. On 'change X': implement change, rerun tests, re-enter the pause. Never merge without explicit 'merge'.
4. On 'merge': checkout feature branch, `git merge --squash work/redemption-flow-{unit-id}`, commit with message `"{unit-id}: {title}"`, push the feature branch
5. Delete the sub-branch locally
6. Back to blueprint: flip status → `done`, add completed timestamp + squash-merge SHA in `Commit` column, commit + push

**Ending a session (on failure or interrupt):**
- Flip to `blocked` with a one-line reason in Notes, push tracker. Or back to `not-started` if work was cleanly undone.

**Consistency rule:** If a checklist item shows `[x]` but the referenced file is missing → **STOP** and surface to human. Never silently redo prior session work.

**Contract change ritual:** If a mid-story DTO change is needed:
1. Edit the relevant section of `spec.md`
2. Re-run `/generate-contracts redemption-flow` (idempotent)
3. Note in the affected story row's Notes column: "contract updated — refetch types"
