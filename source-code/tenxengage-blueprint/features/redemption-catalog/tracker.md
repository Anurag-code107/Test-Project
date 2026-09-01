# Tracker — redemption-catalog

**Last updated:** 2026-05-20 by pushpendra
**Feature PR (Contracts):** https://gitlab.com/genidev/tenxengage-new/tenxengage-contracts/-/merge_requests/2 _(merged)_
**Feature PR (BE):** https://gitlab.com/genidev/tenxengage-new/tenxengage-backend/-/merge_requests/7 _(merged)_
**Feature PR (FE):** https://gitlab.com/genidev/tenxengage-new/tenxengage-frontend/-/merge_requests/2 _(merged)_

> **Step 0 — Contracts (run before any foundation or story work):**
> `cd ../tenxengage-contracts && /generate-contracts redemption-catalog`
> FE story sessions can start immediately after contracts are generated.
> Contracts generated: **yes** (2026-05-12)

---

## Foundation tasks (sequential)

| # | Task | Status | Session | Started | Completed | Duration | Commit | Notes |
|---|---|---|---|---|---|---|---|---|
| F1 | Enums | done | sess_20260512_F1 | 2026-05-12T00:00:00Z | 2026-05-12T00:30:00Z | 30m | 1db43e6 | — |
| F2 | Flyway migrations | done | sess_20260512_F2 | 2026-05-12T01:00:00Z | 2026-05-12T02:30:00Z | 1h 30m | 4c92514 | — |
| F3 | Base entities + repositories + fixtures | done | sess_20260512_F3 | 2026-05-12T03:00:00Z | 2026-05-12T04:00:00Z | 1h | 9d8042e | — |
| F4 | Permissions + feature flags seed | done | sess_20260512_F4 | 2026-05-12T04:30:00Z | 2026-05-12T05:00:00Z | 30m | bae1154 | — |
| F5 | BE-only plumbing | N/A | — | — | — | — | — | Kafka deferred to Phase 2 |

_`Commit` column: squash-merge SHA on the feature branch after the sub-branch is merged locally. `Duration`: elapsed time from claim to done for this session (e.g., `1h 23m`)._

---

## Stories

_BE and FE status track independently. FE `done` = wired to real BE + E2E passes (not just mocked). Use `N/A` for the layer that doesn't apply (BE-only or FE-only stories)._

| US | Title | Seed | Layers | Depends on | BE | BE Tests | FE | FE Tests | Mockup | Commit (BE) | Commit (FE) | Duration (BE) | Duration (FE) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| US-01 | Manage global catalog items | S-01 | BE + FE | Foundation | done | green @ aeacb21 | done | green @ 679ed66 | — | aeacb21 | 679ed66 | — | — | FE completed 2026-05-14T13:50:00Z |
| US-02 | Configure tenant catalog | S-02 | BE + FE | Foundation, US-01 | done | green @ f01abe17ca14 | done | green @ b5d115f | — | f01abe17ca14 | b5d115f | 17h 7m | 1h 5m | BE completed 2026-05-13T05:21:56Z; FE completed 2026-05-14T15:05:00Z |
| US-03 | Configure regional catalog | S-03 | BE + FE | Foundation, US-02 | done | green @ d5209439c295 | done | green @ 69c1ac6 | — | d5209439c295 | 69c1ac6 | 22m | 46m | BE completed 2026-05-13T06:16:00Z; FE completed 2026-05-14T16:56:00Z |
| US-04 | Browse currency-aware catalog | S-04 | BE + FE | Foundation, US-01 | done | green @ 471fcdffbda9 | done | green @ f58502ed8bd3 | — | 471fcdffbda9 | f58502ed8bd3 | — | 59m | BE completed 2026-05-13T09:45:00Z; FE completed 2026-05-14T17:04:00Z |
| US-05 | Xoxoday sync + integration health | S-05 | BE + FE | Foundation, US-01 | done | green @ 791f95138637 | done | green @ a8a8b3fc057b | — | 791f95138637 | a8a8b3fc057b | 35m | — | BE completed 2026-05-13T10:25:49Z; FE completed 2026-05-14T18:40:00Z |

_`Seed`: planning seed ID from `spec.md → ## Planning Seeds`. `Commit (BE)` / `Commit (FE)`: squash-merge SHA on the feature branch after the sub-branch is locally merged. `BE Tests` / `FE Tests`: `not-started` until load-story outer-loop passes, then `green @ <sha>`; `N/A` for layers not in this story. `Mockup`: `—` = no mockup created (optional). `Duration (BE)` / `Duration (FE)`: elapsed time from tracker claim to done._

---

## Cross-story integration tests

_`test-plan.md` covers full-lifecycle, business rules, multi-entity workflows, tenant isolation, and contract conformance tests. The feature is **not ready to ship** until this row is `done`._

| # | Scope | Status | Session | Started | Completed | Commit | Notes |
|---|---|---|---|---|---|---|---|
| T1 | All `test-plan.md` scenarios passing | done | claude-sonnet-4-6 | 2026-05-14T00:00:00Z | 2026-05-14T00:00:00Z | 310e3401d7ab | 26/26 tests green (IT-01–IT-14); Flyway schema repair via clean-on-validation-error JVM arg |

---

## Blocked / needs attention

_(empty)_

---

## Session protocol

**Branching model:**
- Feature branch (long-lived, per repo): `features/redemption-catalog`
- Sub-branch per session (local-only, never pushed): `work/redemption-catalog-{unit-id}` e.g. `work/redemption-catalog-F1-enums`, `work/redemption-catalog-US-01-be`, `work/redemption-catalog-US-01-fe`
- **No GitHub PR for sub-branches.** Sub-branch → feature-branch = local `git merge --squash` after developer approval in chat.
- **Only one GitHub PR per feature:** `features/redemption-catalog` → `main`, opened manually when the tracker is all-green.

**Starting a session:**
1. Read this tracker
2. Pick an eligible row: `status = not-started`, all deps = `done`
3. Flip status to `in-progress`, add your session ID + start timestamp
4. Commit this one-line tracker change as your **first commit** in the blueprint repo; push with retry-on-reject
5. In the sibling repo (backend or frontend): ensure the feature branch exists; create sub-branch `work/redemption-catalog-{unit-id}` off the feature branch

**During a session:**
- Never re-verify work already marked `done` — trust prior sessions
- Work through the story/task file's `## Execution checklist`, checking items `[x]` as you complete them
- Run scoped tests per item (inner loop), full layer suite before approval

**Ending a session (on success):**
1. Run the unit's "Done when" checks — all green
2. **Pause for developer approval** in chat: show one-paragraph summary + `git diff --stat` of sub-branch vs feature branch
3. Wait for 'merge' or 'change X' reply. On 'change X': implement change, rerun tests, re-enter the pause. Never merge without explicit 'merge'.
4. On 'merge': checkout feature branch, `git merge --squash work/redemption-catalog-{unit-id}`, commit with message `"{unit-id}: {title}"`, push the feature branch
5. Delete the sub-branch locally
6. Back to blueprint: flip status → `done`, add completed timestamp + the squash-merge commit SHA in the `Commit` column, commit + push

**Ending a session (on failure or interrupt):**
- Flip to `blocked` with a one-line reason in Notes, push tracker. Or back to `not-started` if work was cleanly undone and sub-branch deleted.

**Consistency rule:** If a checklist item shows `[x]` but the referenced file is missing → **STOP** and surface to human. Never silently redo prior session work.

**Contract change ritual:** If a mid-story DTO change is needed:
1. Edit the relevant section of `spec.md`
2. Re-run `/generate-contracts redemption-catalog` (idempotent)
3. Note in the affected story row's Notes column: "contract updated — refetch types"
