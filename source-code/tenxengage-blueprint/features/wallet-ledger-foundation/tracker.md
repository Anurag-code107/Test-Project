# Tracker — wallet-ledger-foundation

**Last updated:** 2026-05-15 by create-pr
**Feature PR (Contracts):** https://gitlab.com/genidev/tenxengage-new/tenxengage-contracts/-/merge_requests/1 _(merged)_
**Feature PR (BE):** https://gitlab.com/genidev/tenxengage-new/tenxengage-backend/-/merge_requests/6 _(merged)_
**Feature PR (FE):** https://gitlab.com/genidev/tenxengage-new/tenxengage-frontend/-/merge_requests/1 _(merged)_

> **Step 0 — Contracts (run before any foundation or story work):**
> `cd ../tenxengage-contracts && /generate-contracts wallet-ledger-foundation`
> FE story sessions can start immediately after contracts are generated.
> Contracts generated: **yes** (commit e57a753 on `features/wallet-ledger-foundation` in tenxengage-contracts)

---

## Foundation tasks (sequential)

| # | Task | Status | Session | Started | Completed | Duration | Commit | Notes |
|---|---|---|---|---|---|---|---|---|
| F1 | Enums | done | BE | 2026-05-07T12:38:45Z | 2026-05-07T12:45:00Z | ~7m | ddfe5ee | — |
| F2 | Flyway migrations | done | BE | 2026-05-07T12:55:00Z | 2026-05-07T13:05:00Z | ~10m | 2db6146 | — |
| F3 | Base entities + repositories + fixtures | done | BE | 2026-05-07T13:10:00Z | 2026-05-07T13:20:00Z | ~10m | 8627e1b | — |
| F4 | Permissions + feature flags seed | done | BE | 2026-05-07T13:25:00Z | 2026-05-07T13:30:00Z | ~5m | a6331db | — |
| F5 | BE-only plumbing | N/A | — | — | — | — | — | Kafka deferred to Phase 2 |

_`Commit` column: squash-merge SHA on the feature branch after the sub-branch is merged locally. `Duration`: elapsed time from claim to done for this session (e.g., `1h 23m`)._

---

## Stories

_BE and FE status track independently. FE `done` = wired to real BE + E2E passes (not just mocked). Use `N/A` for the layer that doesn't apply (BE-only or FE-only stories)._

| US | Title | Seed | Layers | Depends on | BE | BE Tests | FE | FE Tests | Mockup | Commit (BE) | Commit (FE) | Duration (BE) | Duration (FE) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| US-01 | Wallet read endpoints | F-01.S-01, F-01.S-02 | BE | Foundation | done | green @ 3f25f0bd6b94 | N/A | N/A | N/A | 3f25f0bd6b94 | — | ~35m | N/A | BE completed 2026-05-07T14:35:00Z |
| US-02 | Nav balance widget | F-01.S-04 | FE | Foundation, US-01 | N/A | N/A | done | green @ 849dfa5 | — | — | 849dfa5 | N/A | ~5h50m | FE completed 2026-05-07T20:15:00Z |
| US-03 | Wallet mutation service + grant integration | F-01.S-03, F-01.S-05 | BE | Foundation, US-01 | done | green @ 2eb1190 | N/A | N/A | N/A | 2eb1190 | — | ~2h | N/A | BE completed 2026-05-07T18:10:00Z |

_`Seed`: planning seed ID(s) from `roadmaps/redemption-store/features/F-01-redemption-store.md`. `Commit (BE)` / `Commit (FE)`: squash-merge SHA on the feature branch after the sub-branch is locally merged. `BE Tests` / `FE Tests`: `not-started` until load-story outer-loop passes, then `green @ <sha>`; `N/A` for layers not in this story. `Mockup`: `N/A` = BE-only story; `—` = FE story, no mockup created. `Duration (BE)` / `Duration (FE)`: elapsed time from tracker claim to done (e.g., `1h 23m`); `N/A` for layers not in this story._

---

## Cross-story integration tests

_`test-plan.md` covers full-lifecycle, business rules, concurrency, tenant isolation, and contract conformance tests. The feature is **not ready to ship** until this row is `done`._

| # | Scope | Status | Session | Started | Completed | Commit | Notes |
|---|---|---|---|---|---|---|---|
| T1 | All `test-plan.md` scenarios passing | done | BE | 2026-05-07T20:25:00Z | 2026-05-07T22:05:00Z | 70abf18 | 13 integration + 4 contract conformance tests green |

---

## Blocked / needs attention

_(empty)_

---

## Session protocol

**Branching model:**
- Feature branch (long-lived, per repo): `features/wallet-ledger-foundation`
- Sub-branch per session (local-only, never pushed): `work/wallet-ledger-foundation-{unit-id}` e.g. `work/wallet-ledger-foundation-F1-enums`, `work/wallet-ledger-foundation-US-01-be`, `work/wallet-ledger-foundation-US-02-fe`
- **No GitHub PR for sub-branches.** Sub-branch → feature-branch = local `git merge --squash` after developer approval in chat.
- **Only one GitHub PR per feature:** `features/wallet-ledger-foundation` → `main`, opened manually when the tracker is all-green.

**Starting a session:**
1. Read this tracker
2. Pick an eligible row: `status = not-started`, all deps = `done`
3. Flip status to `in-progress`, add your session ID + start timestamp
4. Commit this one-line tracker change as your **first commit** in the blueprint repo; push with retry-on-reject
5. In the sibling repo (backend or frontend): ensure the feature branch exists; create sub-branch `work/wallet-ledger-foundation-{unit-id}` off the feature branch

**During a session:**
- Never re-verify work already marked `done` — trust prior sessions
- Work through the story/task file's `## Execution checklist`, checking items `[x]` as you complete them
- Run scoped tests per item (inner loop), full layer suite before approval

**Ending a session (on success):**
1. Run the unit's "Done when" checks — all green
2. **Pause for developer approval** in chat: show one-paragraph summary + `git diff --stat` of sub-branch vs feature branch
3. Wait for 'merge' or 'change X' reply. On 'change X': implement change, rerun tests, re-enter the pause. Never merge without explicit 'merge'.
4. On 'merge': checkout feature branch, `git merge --squash work/wallet-ledger-foundation-{unit-id}`, commit with message `"{unit-id}: {title}"`, push the feature branch
5. Delete the sub-branch locally
6. Back to blueprint: flip status → `done`, add completed timestamp + the squash-merge commit SHA in the `Commit` column, commit + push

**Ending a session (on failure or interrupt):**
- Flip to `blocked` with a one-line reason in Notes, push tracker. Or back to `not-started` if work was cleanly undone and sub-branch deleted.

**Consistency rule:** If a checklist item shows `[x]` but the referenced file is missing → **STOP** and surface to human. Never silently redo prior session work.

**Contract change ritual:** If a mid-story DTO change is needed:
1. Edit the relevant section of `spec.md`
2. Re-run `/generate-contracts wallet-ledger-foundation` (idempotent)
3. Note in the affected story row's Notes column: "contract updated — refetch types"
