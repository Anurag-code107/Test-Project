# Tracker — reward-balance-expiration

**Last updated:** 2026-06-29 — all 4 MRs opened → `roadmaps/redemption-store` (feature fully implemented + tested)
**Feature MR (backend):** https://gitlab.com/genidev/tenxengage-new/tenxengage-backend/-/merge_requests/14
**Feature MR (frontend):** https://gitlab.com/genidev/tenxengage-new/tenxengage-frontend/-/merge_requests/9
**Feature MR (contracts):** https://gitlab.com/genidev/tenxengage-new/tenxengage-contracts/-/merge_requests/10
**Feature MR (blueprint):** https://gitlab.com/genidev/tenxengage-new/tenxengage-blueprint/-/merge_requests/9

> **Step 0 — Contracts (run before any foundation or story work):**
> `cd ../tenxengage-contracts && /generate-contracts reward-balance-expiration`
> FE story sessions (US-01, US-04) can start immediately after contracts are generated.
> Contracts generated: **yes**

---

## Foundation tasks (sequential)

| # | Task | Status | Session | Started | Completed | Duration | Commit | Notes |
|---|---|---|---|---|---|---|---|---|
| F1 | Enums | done | sess-20260624-f1a3 | 2026-06-24T00:00:00Z | 2026-06-24 | — | 41420c72 | ExpirationMode, ExpiryNoticeStatus (new); LedgerEntryType.EXPIRY; AuditResourceType +2 — finalized manually after run-feature halted at test-wait |
| F2 | Flyway migrations (V32) | done | sess-20260625-f2b7 | 2026-06-25T00:00:00Z | 2026-06-25 | — | 998695fc | 2 tables + ledger index — finalized manually (session paused at merge-approval); DDL verified vs spec; ⚠️ flywayMigrate NOT validated (Docker down) — run before T1/PR |
| F3 | Base entities + repositories + fixtures | done | sess-20260625-f3c9 | 2026-06-25T00:00:00Z | 2026-06-25 | — | 2b394ae3 | entities + 3 repos (incl. no-@Filter Scheduler repo) + breakage query + fixtures; tests green (1 pre-existing unrelated failure) — finalized manually (session paused at merge-approval) |
| F4 | Permissions + flag + notification seed (V33) | done | sess-20260625-f4d2 | 2026-06-25T00:00:00Z | 2026-06-25 | — | b5f8ab9d | 2 perms + flag + BOTH client_role_permissions & client_permission_grants (Layer-0) + 3 notification_types; idempotent; tests green (1 pre-existing) — finalized manually |

---

## Stories

_BE and FE status track independently. FE `done` = wired to real BE + E2E passes. `N/A` for the inapplicable layer._

| US | Title | Layers | Seed | Depends on | BE | BE Tests | FE | FE Tests | Mockup | Commit (BE) | Commit (FE) | Duration (BE) | Duration (FE) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| US-01 | Configure balance expiration policy | BE + FE | F-09.S-01 | Foundation | done | green @ a9c6ca645ebf | done | green @ e7acbf02cecc | — | a9c6ca645ebf | e7acbf02cecc | 59m | 1h 39m | session=1782346578 started=2026-06-25T00:19:12Z BE completed 2026-06-25T01:18:34Z; FE session=1782350587 started=2026-06-25T01:24:58Z FE completed 2026-06-25T03:03:59Z |
| US-02 | Advance-expiry notification engine | BE | F-09.S-02 | Foundation, US-01 | done | green @ 5ebbb2089e90 | N/A | N/A | N/A | 5ebbb2089e90 | — | 1h 12m | N/A | session=1782350595 started=2026-06-25T01:26:29Z BE completed 2026-06-25T02:38:51Z; [HIGH] BalanceExpiryBatchService — Kafka failure suppresses notice permanently: add outbox/retry or PENDING_DELIVERY state in US-03; [HIGH] COMPANY wallet role-broadcast leaks across partners: scope NotificationDispatcher to partnerCompanyId in US-03; [MEDIUM] N+1 findLastActivityAt per wallet: replace with bulk GROUP BY in US-03; [MEDIUM] findExpiryCandidateWallets unbounded: add Pageable in US-03 |
| US-03 | Balance expiry execution + cancellation | BE | F-09.S-03 | Foundation, US-01, US-02 | done | green @ 1d2d8935 | N/A | N/A | N/A | b9f5820736f2 | — | 43m | N/A | session=1782357681; scheduled batch (expire phase) + cancel-on-relax; BE completed 2026-06-25T04:07:46Z. Carried findings ALL RESOLVED: AC-7 ✅(b9f58207); AC-8 ✅(bae3eb80, publish-and-confirm gating, FR-09.7); AC-9 ✅ + AC-10 ✅ (1d2d8935, bulk last-activity GROUP BY + paged candidate sweep; DB-validated via SchedulerBalanceExpirationRepositoryLocalIT) |
| US-04 | Breakage report + CSV export | BE + FE | F-09.S-04 | Foundation | done | green @ d170bae1741a | done | green @ c01ab01191d7 | — | d170bae1741a | c01ab01191d7 | 32m | 54m | parallel with US-02/US-03; BE session=1782355463 started=2026-06-25T02:46:14Z BE completed 2026-06-25T03:18:55Z; FE session=1782357683 started=2026-06-25T03:23:57Z FE completed 2026-06-25T04:18:14Z; [HIGH] adversarial: /redemption/history route uses module.redemption_store guard instead of action.redemption.view_history (App.tsx:202-206) — pre-existing, fix separately |

_`Mockup`: `N/A` = BE-only; `—` = FE story, no mockup created (optional, never required). `BE Tests`/`FE Tests`: `not-started` until load-story outer-loop passes, then `green @ <sha>`._

---

## Cross-story integration tests

_`test-plan.md` covers full-lifecycle, idempotency, cancel-on-relax, grace window, breakage aggregation, contract conformance, tenant isolation, and audit/event tests. The feature is **not ready to ship** until this row is `done`._

| # | Scope | Status | Session | Started | Completed | Commit | Notes |
|---|---|---|---|---|---|---|---|
| T1 | All `test-plan.md` scenarios passing | done | — | 2026-06-25 | 2026-06-25 | 58d1edf3 | **COMPLETE — BE integration (35) + FE E2E (7) all green.** **FE Playwright E2E (7/7 passed headless in 36s, run directly — NOT via the run-feature `claude -p` dispatch, which is what hung before):** balance-expiration.spec.ts (3: configure inactivity happy-path AC-1/2/6, invalid lead-time field error AC-3, expiring-soon preview AC-7) + balance-expiration-breakage.spec.ts (4: report renders rows, invalid range inline error, export CSV downloads, empty state) — fully backend-mocked via page.route, run against the FE feature branch. **BE integration green (35 tests):** BalanceExpirationLifecycleIT (9: lifecycle, idempotency, grace, reserved-balance, disabled-after-warn, cancel-on-relax, breakage, config-validation — with real-DB audit-row assertions on expire `EXPIRED/REWARD_WALLET` + cancel `CANCELLED/BALANCE_EXPIRATION_POLICY` — plus Kafka round-trip: fresh-group consumer reads `BALANCE_EXPIRING_SOON` back off `notification-events`, asserts the serialized payload contract, deterministic via publishAndConfirm) + SchedulerBalanceExpirationRepositoryLocalIT (2: paged + bulk queries) + BalanceExpirationWebSecurityIT (5: 401 unauthenticated, real security chain) + **BalanceExpirationContractConformanceTest (19: file-based — `balance-expiration.yaml` declares all 5 endpoints, both permission scopes (configure vs view_breakage), endpoint-specific error codes (409 upsert / 429+Retry-After export), CSV export specifics, schemas + enums)**. _200/403/400/422 per-endpoint behaviour covered by BalanceExpirationControllerTest (@WebMvcTest); live response shape covered by the @WebMvcTest + lifecycle ITs exercising the real DTOs._ **T1 caught + fixed 5 bugs:** 4 DB-only prod bugs (breakage native query ×3 → GET /breakage 500; lockWallet entity-mapping → expire row-lock failed) + a lazy-`seekToEnd` test bug; **and surfaced a 6th latent prod bug** (`AuditLogRepository.findFiltered` → `could not determine data type of parameter $2` when `userType=null`; production query left unmodified — flag separately). **ONLY remaining:** FE Playwright E2E (configure→breakage + cross-tenant isolation; hangs headless — needs a headed/manual run). |

---

## Blocked / needs attention

_(empty)_

---

## Session protocol

**Branching model:**
- Feature branch (long-lived, per repo): `features/reward-balance-expiration`
- Sub-branch per session (local-only, never pushed): `work/reward-balance-expiration-{unit-id}` e.g. `work/NNN-F1-enums`, `work/NNN-US-01-be`, `work/NNN-US-01-fe`
- **No GitHub/GitLab PR for sub-branches.** Sub-branch → feature-branch = local `git merge --squash` after developer approval in chat.
- **Only one MR per feature per repo:** `features/reward-balance-expiration` → roadmap/main, opened manually when the tracker is all-green and feature testing is complete.

**Starting a session:**
1. Read this tracker
2. Pick an eligible row: `status = not-started`, all deps = `done`
3. Flip status to `in-progress`, add your session ID + start timestamp
4. Commit this one-line tracker change as your **first commit** in the blueprint repo; push with retry-on-reject
5. In the sibling repo (backend or frontend): ensure the feature branch exists; create sub-branch `work/reward-balance-expiration-{unit-id}` off the feature branch

**During a session:**
- Never re-verify work already marked `done` — trust prior sessions
- Work through the story/task file's `## Execution checklist`, checking items `[x]` as you complete them
- Run scoped tests per item (inner loop), full layer suite before approval

**Ending a session (on success):**
1. Run the unit's "Done when" checks — all green
2. **Pause for developer approval** in chat: show one-paragraph summary + `git diff --stat` of sub-branch vs feature branch
3. Wait for 'merge' or 'change X' reply. On 'change X': implement, rerun tests, re-enter pause. Never merge without explicit 'merge'.
4. On 'merge': checkout feature branch, `git merge --squash work/...`, commit `"{unit-id}: {title}"`, push the feature branch
5. Delete the sub-branch locally
6. Back to blueprint: flip status → `done`, add completed timestamp + squash-merge SHA in `Commit`, commit + push

**Ending a session (on failure or interrupt):**
- Flip to `blocked` with a one-line reason in Notes, push tracker. Or back to `not-started` if work was cleanly undone and sub-branch deleted.

**Consistency rule:** If a checklist item shows `[x]` but the referenced file is missing → **STOP** and surface to human.

**Contract change ritual:** If a mid-story DTO change is needed: edit `spec.md` → re-run `/generate-contracts reward-balance-expiration` (idempotent) → note "contract updated — refetch types" in the affected story row.
