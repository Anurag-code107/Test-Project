# Tracker — redemption-history

**Last updated:** 2026-06-03 by generate-contracts
**Feature PR (Blueprint):** https://gitlab.com/genidev/tenxengage-new/tenxengage-blueprint/-/merge_requests/new?merge_request[source_branch]=features/redemption-history _(open when tracker is all-green)_
**Feature PR (Contracts):** https://gitlab.com/genidev/tenxengage-new/tenxengage-contracts/-/merge_requests/6 ✓ merged
**Feature PR (BE):** https://gitlab.com/genidev/tenxengage-new/tenxengage-backend/-/merge_requests/10 ✓ merged
**Feature PR (FE):** https://gitlab.com/genidev/tenxengage-new/tenxengage-frontend/-/merge_requests/5 ✓ merged

> **Step 0 — Contracts (run before any foundation or story work):**
> `cd ../tenxengage-contracts && /generate-contracts redemption-history`
> FE story sessions can start immediately after contracts are generated.
> Contracts generated: **yes**

---

## Foundation tasks (sequential)

| # | Task | Status | Session | Started | Completed | Duration | Commit | Notes |
|---|---|---|---|---|---|---|---|---|
| F1 | Enums | done | 20260603-F1-a1b2 | 2026-06-03T07:00:00Z | 2026-06-03T07:30:00Z | 30m | 540dd826b9ab | — |
| F2 | Flyway migrations | done | 20260603-F2-c3d4 | 2026-06-03T07:35:00Z | 2026-06-03T08:05:00Z | 30m | 5395be3cf5be | V23 (not V10 — V10 taken; V20-V22 reserved for approval-queue) |
| F3 | Base entities + repositories + fixtures | done | 20260603-F3-e5f6 | 2026-06-03T08:10:00Z | 2026-06-03T09:00:00Z | 50m | 236564888b78 | LEFT JOIN FETCH r.catalogItem removed — no association on RedemptionRequest; service layer loads catalog names separately |
| F4 | Permissions seed | done | 20260603-F4-g7h8 | 2026-06-03T09:05:00Z | 2026-06-03T09:20:00Z | 15m | d90ba5293ec1 | V24 (not V11 — stale spec number) |
| F5 | BE-only plumbing | N/A | — | — | — | — | — | No Kafka events |

_`Commit` column: squash-merge SHA on the feature branch after the sub-branch is merged locally. `Duration`: elapsed time from claim to done._

---

## Stories

_BE and FE status track independently. FE `done` = wired to real BE + E2E passes (not just mocked). Use `N/A` for the layer that doesn't apply._

| US | Title | Seed | Layers | Depends on | BE | BE Tests | FE | FE Tests | Mockup | Commit (BE) | Commit (FE) | Duration (BE) | Duration (FE) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| US-01 | View personal redemption history | F-05.S-01 | BE + FE | Foundation | done | green @ 963ea697ab69 | done | green @ 89a43956f448 | — | 963ea697ab69 | 89a43956f448 | 1h 10m | 1h 26m | BE completed 2026-06-04T00:35:00Z FE completed 2026-06-04T07:17:33Z |
| US-02 | View company redemption history | F-05.S-02 | BE + FE | Foundation, US-01 FE | done | green @ a313888068c3 | done | green @ 88ff500128548d | — | a313888068c3 | 88ff500128548d | 1h 20m | 17m | BE completed 2026-06-04T09:35:00Z; fixed FE-session Lombok regression + duplicate handler; FE completed 2026-06-08T01:03:00Z |
| US-03 | Export personal transaction data | F-05.S-03 | BE + FE | Foundation, US-01 FE | done | green @ e54ca67f97f7 | done | green @ 3b40ab0107b9 | — | e54ca67f97f7 | 3b40ab0107b9 | 1h 45m | 12h 31m | BE completed 2026-06-04T11:35:00Z; FE completed 2026-06-08T13:45:00Z |
| US-04 | View and export tenant-wide history | F-05.S-04 | BE + FE | Foundation, US-03 FE | done | green @ 775f3eafb7dc | done | green @ 73811fc | — | 775f3eafb7dc | 73811fc | 40m | 2h 15m | BE completed 2026-06-08T08:25:00Z; FE completed 2026-06-08T16:15:00Z |

_`Commit (BE)` / `Commit (FE)`: squash-merge SHA on the feature branch after the sub-branch is locally merged. `BE Tests` / `FE Tests`: `not-started` until load-story outer-loop passes, then `green @ <sha>`. `Mockup`: `—` = FE story, no mockup created (optional). `Duration (BE)` / `Duration (FE)`: elapsed time from tracker claim to done._

---

## Cross-story integration tests

_`test-plan.md` covers full-lifecycle, contract conformance, state machine, business rules, tenant isolation, and permission enforcement tests. The feature is **not ready to ship** until this row is `done`._

| # | Scope | Status | Session | Started | Completed | Commit | Notes |
|---|---|---|---|---|---|---|---|
| T1 | All `test-plan.md` scenarios passing | done | 20260608-T1-r7s8 | 2026-06-08T08:30:00Z | 2026-06-08T11:45:00Z | 31bbdc5ba7c4 | Fixed PostgreSQL COALESCE bug in all 5 JPQL nullable params; 27 integration scenarios green |

---

## Blocked / needs attention

_(empty)_

---

## Session protocol

**Branching model:**
- Feature branch (long-lived, per repo): `features/redemption-history`
- Sub-branch per session (local-only, never pushed): `work/redemption-history-{unit-id}` e.g. `work/redemption-history-F1-enums`, `work/redemption-history-US-01-be`, `work/redemption-history-US-01-fe`
- **No PR for sub-branches.** Sub-branch → feature-branch = local `git merge --squash` after developer approval in chat.
- **Only one PR per feature per repo:** `features/redemption-history` → `roadmaps/redemption-store`, opened manually when the tracker is all-green.

**Starting a session:**
1. Read this tracker
2. Pick an eligible row: `status = not-started`, all deps = `done`
3. Flip status to `in-progress`, add your session ID + start timestamp
4. Commit this one-line tracker change as your **first commit** in the blueprint repo; push with retry-on-reject
5. In the sibling repo (backend or frontend): ensure the feature branch exists; create sub-branch `work/redemption-history-{unit-id}` off the feature branch

**During a session:**
- Never re-verify work already marked `done` — trust prior sessions
- Work through the story/task file's `## Execution checklist`, checking items `[x]` as you complete them
- Run scoped tests per item (inner loop), full layer suite before approval

**Ending a session (on success):**
1. Run the unit's "Done when" checks — all green
2. **Pause for developer approval** in chat: show one-paragraph summary + `git diff --stat` of sub-branch vs feature branch
3. Wait for 'merge' or 'change X' reply. Never merge without explicit 'merge'.
4. On 'merge': checkout feature branch, `git merge --squash work/redemption-history-{unit-id}`, commit, push
5. Delete the sub-branch locally
6. Back to blueprint: flip status → `done`, add completed timestamp + commit SHA, commit + push

**Ending a session (on failure or interrupt):**
- Flip to `blocked` with a one-line reason in Notes, push tracker. Or back to `not-started` if work was cleanly undone.

**Consistency rule:** If a checklist item shows `[x]` but the referenced file is missing → **STOP** and surface to human. Never silently redo prior session work.

**Contract change ritual:** If a mid-story DTO change is needed:
1. Edit the relevant section of `spec.md`
2. Re-run `/generate-contracts redemption-history` (idempotent)
3. Note in the affected story row's Notes column: "contract updated — refetch types"
