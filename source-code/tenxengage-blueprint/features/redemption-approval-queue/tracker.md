# Tracker — redemption-approval-queue

**Last updated:** 2026-05-27 by create-stories
**Feature PR (Contracts):** https://gitlab.com/genidev/tenxengage-new/tenxengage-contracts/-/merge_requests/5 — merged ✅
**Feature PR (BE):** https://gitlab.com/genidev/tenxengage-new/tenxengage-backend/-/merge_requests/9 — merged ✅
**Feature PR (FE):** https://gitlab.com/genidev/tenxengage-new/tenxengage-frontend/-/merge_requests/4 — merged ✅

> **Step 0 — Contracts (run before any foundation or story work):**
> `cd ../tenxengage-contracts && /generate-contracts redemption-approval-queue`
> FE story sessions can start immediately after contracts are generated.
> Contracts generated: **yes — fully current as of 2026-05-27 (synced with reviewed spec)**

---

## Foundation tasks (sequential)

| # | Task | Status | Session | Started | Completed | Duration | Commit | Notes |
|---|---|---|---|---|---|---|---|---|
| F1 | Enums | done | 20260527-F1-a7b2 | 2026-05-27T04:17:03Z | 2026-05-27T04:33:23Z | 16m | 762a8b53976d | Add `RedemptionRequestType` (REDEMPTION \| RETURN) |
| F2 | Flyway migrations | done | 20260527-F2-b3c4 | 2026-05-27T04:39:08Z | 2026-05-28T00:00:00Z | — | 1cd6802 | V20 ALTER TABLE only (renumbered from V18 — catalog V18/V19 merged in) |
| F3 | Base entities + repos + fixtures | done | 20260528-F3-d7e8 | 2026-05-28T06:00:00Z | 2026-05-28T06:45:00Z | 45m | dbb805154a34 | Modify existing F-03 files; update RedemptionRequestFixtures |
| F4 | Permissions + feature flags seed | done | 20260528-F4-e9f0 | 2026-05-28T07:00:00Z | 2026-05-28T07:20:00Z | 20m | 87a69a6a0ac2 | V21 (renumbered from V19); can run parallel with F3 |
| F5 | BE-only plumbing | N/A | — | — | — | — | — | SKIP — NotificationEventProducer reused in stories |

---

## Stories

| US | Title | Layers | Depends on | BE | BE Tests | FE | FE Tests | Mockup | seed_id | Commit (BE) | Commit (FE) | Duration (BE) | Duration (FE) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| US-01 | View approval queue | BE + FE | Foundation | done | green @ 3ae83115a2f0 | done | green @ 911e09adcf2f | — | F-04.S-01 | 3ae83115a2f0 | 911e09adcf2f | 36m | 2h 53m | BE completed 2026-05-28T08:06:00Z FE completed 2026-05-29T05:14:08Z |
| US-02 | Approve redemption | BE + FE | Foundation, US-01 | done | green @ 59819baaf5de | done | green @ cfab19fbf3ef | — | F-04.S-02 | 59819baaf5de | cfab19fbf3ef | 25m | 20m | BE completed 2026-05-28T08:35:00Z FE completed 2026-05-29T05:44:14Z |
| US-03 | Reject redemption | BE + FE | Foundation, US-01, US-02 BE | done | green @ 91d7c26f466c | done | green @ bba3c0092773 | — | F-04.S-03 | 91d7c26f466c | bba3c0092773 | 32m | 21m | BE completed 2026-05-29T00:32:00Z FE completed 2026-05-29T06:08:45Z |

---

## Cross-story integration tests

| # | Scope | Status | Session | Started | Completed | Commit | Notes |
|---|---|---|---|---|---|---|---|
| T1 | All `test-plan.md` scenarios passing | done | 20260530-T1-3ef7 | 2026-05-30T00:00:00Z | 2026-05-30T12:00:00Z | 3ef7e3b | 27 tests (17 integration + 10 contract conformance); also fixed findApprovalQueue null-param bug, AuthService DisabledException mapping, XoxodayApiClientStub guards |

---

## Blocked / needs attention

_(empty)_

---

## Session protocol

**Branching model:**
- Feature branch (long-lived, per repo): `features/redemption-approval-queue`
- Sub-branch per session (local-only, never pushed): `work/redemption-approval-queue-{unit-id}` e.g. `work/raq-F1-enums`, `work/raq-US-01-be`, `work/raq-US-01-fe`
- **No GitHub PR for sub-branches.** Sub-branch → feature-branch = local `git merge --squash` after developer approval in chat.
- **Only one GitHub PR per feature:** `features/redemption-approval-queue` → `main`, opened manually when the tracker is all-green.

**Starting a session:**
1. Read this tracker
2. Pick an eligible row: `status = not-started`, all deps = `done`
3. Flip status to `in-progress`, add your session ID + start timestamp
4. Commit this one-line tracker change as your **first commit** in the blueprint repo; push with retry-on-reject
5. In the sibling repo (backend or frontend): ensure the feature branch exists; create sub-branch `work/redemption-approval-queue-{unit-id}` off the feature branch

**During a session:**
- Never re-verify work already marked `done` — trust prior sessions
- Work through the story/task file's `## Execution checklist`, checking items `[x]` as you complete them
- Run scoped tests per item (inner loop), full layer suite before approval

**Ending a session (on success):**
1. Run the unit's "Done when" checks — all green
2. **Pause for developer approval** in chat: show one-paragraph summary + `git diff --stat` of sub-branch vs feature branch
3. Wait for 'merge' or 'change X' reply
4. On 'merge': checkout feature branch, `git merge --squash work/redemption-approval-queue-{unit-id}`, commit, push
5. Delete the sub-branch locally
6. Back to blueprint: flip status → `done`, add completed timestamp + squash-merge SHA, commit + push

**Contract change ritual:** If a mid-story DTO change is needed:
1. Edit the relevant section of `spec.md`
2. Re-run `/generate-contracts redemption-approval-queue` (idempotent)
3. Note in the affected story row's Notes column: "contract updated — refetch types"
