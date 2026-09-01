# Tracker — redemption-analytics-basic

**Last updated:** 2026-06-19 — all 4 MRs merged
**Feature PR (BE):** https://gitlab.com/genidev/tenxengage-new/tenxengage-backend/-/merge_requests/12 ✅ merged
**Feature PR (FE):** https://gitlab.com/genidev/tenxengage-new/tenxengage-frontend/-/merge_requests/7 ✅ merged
**Feature PR (Contracts):** https://gitlab.com/genidev/tenxengage-new/tenxengage-contracts/-/merge_requests/8 ✅ merged
**Feature PR (Blueprint):** https://gitlab.com/genidev/tenxengage-new/tenxengage-blueprint/-/merge_requests/6 ✅ merged

> **Step 0 — Contracts (run before any foundation or story work):**
> `cd ../tenxengage-contracts && /generate-contracts redemption-analytics-basic`
> FE story sessions can start immediately after contracts are generated.
> Contracts generated: **yes**

---

## Foundation tasks (sequential)

| # | Task | Status | Session | Started | Completed | Duration | Commit | Notes |
|---|---|---|---|---|---|---|---|---|
| F1 | Enums — AuditResourceType | done | 694fb7b4 | 2026-06-17T00:00:00Z | 2026-06-17 | — | 55ed15b4 | Add `REDEMPTION_ANALYTICS_EXPORT` to `AuditResourceType.java` |
| F2 | Flyway V27 — permission seed + indexes | done | e908e30b | 2026-06-17T15:13:32Z | 2026-06-17 | — | e584f562 | Permission seed SQL + 3 composite indexes; fixed partial index on non-soft-delete tables |
| F3 | Repository query extensions | done | 00d608a1 | 2026-06-17T15:34:35Z | 2026-06-17 | — | c9e07ce5 | 3 projection interfaces + query methods on LedgerEntry, RewardWallet, RedemptionRequest repos |

_F4 skipped — permission seed is in F2. F5 skipped — no Kafka._

---

## Stories

| US | Title | Layers | seed_id | Depends on | BE | BE Tests | FE | FE Tests | Mockup | Commit (BE) | Commit (FE) | Duration (BE) | Duration (FE) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| US-01 | View analytics dashboard | BE + FE | S-01, S-02, S-03, S-04 | Foundation | done | green @ 37703a88cf98 | done | green @ c194c9e3 | — | 37703a88cf98 | c194c9e3 | 1h 07m | — | BE: session=1781711753; FE: session=1781716257; 15 pre-existing FE flakes (unrelated); squash-merged 2026-06-18 |
| US-02 | Export unredeemed balances CSV | BE + FE | S-02 | Foundation + US-01 BE (BE); US-01 FE (FE) | done | green @ dbe3e4b8 | done | green @ 82a5b939b7b9 | — | dbe3e4b8 | 82a5b939b7b9 | — | 27m | session=1781716259; ready-check fixes: atomic RateLimitResult, CSV formula-injection guard; squash-merged 2026-06-18; FE session=1781731769 started=2026-06-17T21:30:59Z; FE completed 2026-06-17T21:57:44Z |

_`Mockup`: `—` = FE story, no mockup created yet (optional, never required)._
_`BE Tests` / `FE Tests`: `not-started` until load-story outer-loop passes, then `green @ <sha>`._

---

## Cross-story integration tests

| # | Scope | Status | Session | Started | Completed | Commit | Notes |
|---|---|---|---|---|---|---|---|
| T1 | All `test-plan.md` scenarios passing | done | — | 2026-06-18 | 2026-06-19 | 2f063709 | T-01–T-12: 34 BE integration tests green 2026-06-18; T-13 full-flow E2E written + green 2026-06-19 (31s, real BE, clientadmin@acme.com) |

---

## Blocked / needs attention

_(empty)_

---

## Session protocol

**Branching model:**
- Feature branch (long-lived, per repo): `features/redemption-analytics-basic`
- Sub-branch per session (local-only, never pushed): `work/redemption-analytics-basic-{unit-id}` e.g. `work/redemption-analytics-basic-F1-enums`, `work/redemption-analytics-basic-US-01-be`
- **No GitHub PR for sub-branches.** Sub-branch → feature-branch = local `git merge --squash` after developer approval in chat.
- **Only one GitHub PR per feature:** `features/redemption-analytics-basic` → `main`, opened manually when tracker is all-green.

**Starting a session:**
1. Read this tracker
2. Pick an eligible row: `status = not-started`, all deps = `done`
3. Flip status to `in-progress`, add your session ID + start timestamp
4. Commit this one-line tracker change as your **first commit** in the blueprint repo; push with retry-on-reject
5. In the sibling repo (backend or frontend): ensure the feature branch exists; create sub-branch off the feature branch

**Ending a session (on success):**
1. Run the unit's "Done when" checks — all green
2. **Pause for developer approval** in chat: show one-paragraph summary + `git diff --stat`
3. Wait for 'merge' or 'change X' reply. Never merge without explicit approval.
4. On 'merge': checkout feature branch, `git merge --squash work/redemption-analytics-basic-{unit-id}`, commit, push
5. Back to blueprint: flip status → `done`, add commit SHA + timestamp, push

**Contract change ritual:** If a mid-story DTO change is needed:
1. Edit the relevant section of `spec.md`
2. Re-run `/generate-contracts redemption-analytics-basic`
3. Note in the affected story row's Notes column: "contract updated — refetch types"
