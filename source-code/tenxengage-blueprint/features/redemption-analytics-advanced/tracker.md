# Tracker — redemption-analytics-advanced

**Last updated:** 2026-06-24 — F-08 MRs opened (feature → `roadmaps/redemption-store`) in all 4 repos
**Feature MRs (source `features/redemption-analytics-advanced` → target `roadmaps/redemption-store`):**
- **BE:** https://gitlab.com/genidev/tenxengage-new/tenxengage-backend/-/merge_requests/13
- **FE:** https://gitlab.com/genidev/tenxengage-new/tenxengage-frontend/-/merge_requests/8
- **Contracts:** https://gitlab.com/genidev/tenxengage-new/tenxengage-contracts/-/merge_requests/9
- **Blueprint:** https://gitlab.com/genidev/tenxengage-new/tenxengage-blueprint/-/merge_requests/7

> **Step 0 — Contracts (run before any foundation or story work):**
> `cd ../tenxengage-contracts && /generate-contracts redemption-analytics-advanced`
> FE story sessions can start immediately after contracts are generated.
> Contracts generated: **yes**

---

## Foundation tasks (sequential)

| # | Task | Status | Session | Started | Completed | Duration | Commit | Notes |
|---|---|---|---|---|---|---|---|---|
| F1 | Enums | done | 20260622T000001-raa-f1 | 2026-06-22T00:00:01Z | 2026-06-22 | — | ab9f7451 | enum only; finalized by hand after run-feature inner session died on bg-test wait. ReturnServiceTest edit dropped as out-of-scope (pre-existing redemption-returns failures) |
| F2 | Flyway V28 — MV DDL + snapshot + tracking tables | done | 20260622T120000-raa-f2 | 2026-06-22T12:00:00Z | 2026-06-22 | — | 88e150df8d02 | V28 applies cleanly; full suite 1236 tests, 0 failures. Finalized by orchestrator (ready-check auto-merge) — inner execute-foundation paused for `merge` (ignores --gate). |
| F3 | Test fixtures | done | 20260622T140000-raa-f3 | 2026-06-22T14:00:00Z | 2026-06-22 | — | 712184e21efd | AdvancedAnalyticsFixtures + smoke test; 1243 tests, 0 failures. Schema notes for F5: analytics_mv_refresh_log has NO refresh_status; mv_redemption_rate_trend has NO total_issued (V28 is source of truth). |
| F4 | Flyway V29 — permissions + feature flags seed | done | 20260622T170000-raa-f4 | 2026-06-22T17:00:00Z | 2026-06-22 | — | 762ad205e686 | V29 seed (permission + feature flag + CLIENT_ADMIN grant, all ON CONFLICT DO NOTHING); 1243 tests, 0 failures. |
| F5 | MV refresh scheduler | done | 20260622T200000-raa-f5 | 2026-06-22T20:00:00Z | 2026-06-22 | — | 037f6b103d2a | AnalyticsMvRefreshScheduler + unit tests; concurrent MV refresh + liability snapshot; reward_wallets schema reconciled (currency_id, no deleted). BUILD SUCCESSFUL. |

_`Commit` column: squash-merge SHA on the feature branch after the sub-branch is merged locally. `Duration`: elapsed time from claim to done for this session (e.g., `1h 23m`)._

---

## Stories

_BE and FE status track independently. FE `done` = wired to real BE + E2E passes (not just mocked). `BE Tests` / `FE Tests` are written by load-story after the outer-loop full suite passes._

| US | Title | Layers | Depends on | BE | BE Tests | FE | FE Tests | Mockup | Commit (BE) | Commit (FE) | Duration (BE) | Duration (FE) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| US-05 | Advanced tab shell, filter bar, refresh status | BE + FE | Foundation, F5 | done | green @ 6b61b8b4a4af | done | vitest green @ 0b06e899d17f | — | 6b61b8b4a4af | 0b06e899d17f | 48m | — | FE finalized by orchestrator after bg-test-wait death; Vitest 11/11 green; E2E deferred to T1 (no live stack). |
| US-01 | View item breakdown | BE + FE | Foundation, US-05 | done | green @ e695b4f7 | done | vitest green @ 5404754caa2d | — | e695b4f7 | 5404754caa2d | 88m | — | Advisory (BE): @Cacheable gate bypass + totalRedeemedCount semantic — RESOLVED @ f5d3a615 (controller flag gate + V30 completed-only). FE finalized by orchestrator after bg-test-wait; Vitest 11/11; E2E→T1. |
| US-02 | View segment breakdown | BE + FE | Foundation, US-05 | done | green @ b975d4f8b90f | done | vitest green @ f3c153778aa9 | — | b975d4f8b90f | f3c153778aa9 | — | — | BE+FE finalized by orchestrator after bg-test-wait; ready-check green; Vitest 13/13; E2E→T1. |
| US-03 | View time-to-first-redemption | BE + FE | Foundation, US-05 | done | green @ 4c7aad3ca1a0 | done | vitest green @ 364fa02 | — | 4c7aad3ca1a0 | 364fa02 | — | 34m | FE completed 2026-06-22T16:55:36Z; Vitest 13/13; E2E→T1 (no live stack); antipattern: clean |
| US-04 | View redemption rate trend | BE + FE | Foundation, US-05 | done | green @ 97b43a38a37e | done | vitest green @ 24554583544c | — | 97b43a38a37e | 24554583544c | 52m | — | BE self-merged; FE finalized by orchestrator after bg-test-wait; Vitest 11/11; E2E→T1. |
| US-06 | Liability trend chart and CSV export | BE + FE | Foundation, US-05 | done | green @ 3be0b6d485f0 | done | vitest green @ 04dffe0b7cb0 | — | 3be0b6d485f0 | 04dffe0b7cb0 | 49m | 59m | session=1782149122 started=2026-06-22T17:27:16Z; BE completed 2026-06-22T18:16:16Z; FE completed 2026-06-22T19:20:35Z; E2E→T1 (no live stack) |
| US-07 | Failure breakdown | BE + FE | Foundation, US-05 | done | green @ 3d1e13b0d04d | done | vitest green @ 32114e3e4c96 | — | 3d1e13b0d04d | 32114e3e4c96 | — | — | BE verified+merged by orchestrator (died pre-commit); FE finalized after bg-test-wait, Vitest 14/14; E2E→T1. |

_`Commit (BE)` / `Commit (FE)`: squash-merge SHA on the feature branch after the sub-branch is locally merged. `BE Tests` / `FE Tests`: `not-started` until load-story outer-loop passes, then `green @ <sha>`. `Duration (BE)` / `Duration (FE)`: elapsed time from tracker claim to done for that layer session (e.g., `1h 23m`)._

---

## Cross-story integration tests

| # | Scope | Status | Session | Started | Completed | Commit | Notes |
|---|---|---|---|---|---|---|---|
| T1 | Cross-story integration suite (Java) passing | done | orchestrator | — | 2026-06-23 | f7d7bc71b6f0 | 3 IT classes green via `./gradlew integrationTest` (real compose PG): AdvancedAnalyticsIntegrationTest (cap/audit/isolation/flag @78742cc1), AdvancedAnalyticsMvQueryIT (seed→REFRESH MV→query correctness for all 5 breakdowns + isolation), AdvancedAnalyticsContractConformanceTest (DTO shapes, empty-data 200s, 422/403). Mocked Playwright E2E (per-story, page.route) FIXED + green 18/18 @ b1c16a4 (frontend). |
| T2 | Real-stack cross-story E2E (live BE+FE+DB, no mocking) | done | manual | — | 2026-06-23 | 1143101 (FE) | `e2e/redemption-analytics-advanced/full-happy-path.spec.ts` — real acme CLIENT_ADMIN login, all 6 sections render live data + freshness caption + data row + live liability CSV download. 4/4 green across repeats vs bootRun (`local` profile) + compose PG. Surfaced & fixed 4 BE bugs (see Blocked/needs-attention log below). FE component code unchanged. |
| T3 | Cross-cutting checks (export rate-limit, Redis cache-hit, no-PII logs) | done | work/raa-crosscutting-it | 2026-06-24 | 2026-06-24 | e09f03d2 | `AnalyticsExportRateLimiterTest` (unit, 2 tests: 3-then-429 + per-tenant) + `AdvancedAnalyticsCrossCuttingIT` (2 tests: seed→read→delete-row→re-read-from-cache; query log carries tenantId but no email). All 4 green. ⚠️ Closes the `test-plan.md` "Cross-Cutting Checks" gap **except** the "11th query → 429" row: query rate limit is NOT enforced on `/advanced/**` (`RateLimitFilter` exact-match + per-IP) — documented deviation in spec §Security Design; DB protected by export limiter + 60s cache. |

---

## Blocked / needs attention

_(none blocking)_

**Bugs found & fixed during real-stack E2E (T2), 2026-06-23:**
- Tenant-level permission grant missing — V29 granted `action.redemption.analytics.advanced` to the CLIENT_ADMIN role but not at tenant level (`client_permission_grants`); the 5-layer model's Layer-0 intersection stripped it → every advanced-analytics endpoint 403'd. Fixed by **V31** (BE `e35b980b`).
- `SegmentRedemptionDto` field name — emitted `redeemedCount`; contract/FE expect `totalRedeemedCount` (item-breakdown convention). Mismatch crashed the FE render (`undefined.toLocaleString()`). Fixed BE `f269dda6`.
- MV refresh used `REFRESH … CONCURRENTLY`, which rejects the 4 MVs' `COALESCE(region,'')` expression unique indexes; scheduler swallowed the error → those MVs never refreshed in prod. Dropped CONCURRENTLY. Fixed BE `3dea25b8`.
- Segment `redemptionRate` emitted on a 0–1 scale vs the contract's 0–100 (item/trend). Root cause: US-02 AC-3 wording (now corrected). Fixed BE `c3b7401c`, blueprint `da29423`.

**Follow-up (non-blocking, FE teammate):** per-story FE mocked Vitest/E2E still send `redeemedCount`/`redemptionRate: 0.35`; update mocks to `totalRedeemedCount`/`35.0` to match the corrected contract (tests currently pass against their own mocks).

**Bugs found & fixed during manual QA of the live Advanced tab, 2026-06-24** (both brought the impl in line with the existing spec — no contract change):
- **Item-breakdown Rate (%) showed negative values** (e.g. −100%, −75%). The service re-derived the rate as `(completed − failed − cancelled)/completed`, which went negative once V30 made `total_redeemed_count` COMPLETED-only. Fixed to use the count-weighted MV `redemption_rate` (always 0–100), matching `querySegmentBreakdown`. BE `0d3d5c2c`; regression test `getItemBreakdown_itemWithMoreFailuresThanCompletions_rateStaysWithin0to100`.
- **Region/Role filters were non-functional.** FE dropdowns were stubs (hardcoded single "All" item, no `onChange`) **and** the BE bound region/role as a single exact value (`= :region`), so the spec's comma-separated multi-select never filtered. Fixed end-to-end (FR-08.6): BE splits comma-separated values → `IN (:regions/:roles)` (BE `0d3d5c2c`, +2 regression tests); FE wires the `MultiSelect` primitive with options sourced from the segment-breakdown response (FE `fde5844`). Verified: BE `MvQueryIT` 9/9, FE vitest 92/92, `tsc -b` clean.

---

## Session protocol

**Branching model:**
- Feature branch (long-lived, per repo): `features/redemption-analytics-advanced`
- Sub-branch per session (local-only, never pushed): `work/redemption-analytics-advanced-{unit-id}` e.g. `work/raa-F1-enums`, `work/raa-US-05-be`, `work/raa-US-01-fe`
- **No GitHub PR for sub-branches.** Sub-branch → feature-branch = local `git merge --squash` after developer approval in chat.
- **Only one GitHub PR per feature:** `features/redemption-analytics-advanced` → `main`, opened manually when the tracker is all-green.

**Starting a session:**
1. Read this tracker
2. Pick an eligible row: `status = not-started`, all deps = `done`
3. Flip status to `in-progress`, add your session ID + start timestamp
4. Commit this one-line tracker change as your **first commit** in the blueprint repo; push with retry-on-reject
5. In the sibling repo (backend or frontend): ensure the feature branch exists; create sub-branch `work/raa-{unit-id}` off the feature branch

**During a session:**
- Never re-verify work already marked `done` — trust prior sessions
- Work through the story/task file's `## Execution checklist`, checking items `[x]` as you complete them
- Run scoped tests per item (inner loop), full layer suite before approval

**Ending a session (on success):**
1. Run the unit's "Done when" checks — all green
2. **Pause for developer approval** in chat: show one-paragraph summary + `git diff --stat` of sub-branch vs feature branch
3. Wait for 'merge' or 'change X' reply. On 'change X': implement change, rerun tests, re-enter the pause. Never merge without explicit 'merge'.
4. On 'merge': checkout feature branch, `git merge --squash work/raa-{unit-id}`, commit with message `"{unit-id}: {title}"`, push the feature branch
5. Delete the sub-branch locally
6. Back to blueprint: flip status → `done`, add completed timestamp + the squash-merge commit SHA in the `Commit` column, commit + push

**Ending a session (on failure or interrupt):**
- Flip to `blocked` with a one-line reason in Notes, push tracker. Or back to `not-started` if work was cleanly undone and sub-branch deleted.

**Consistency rule:** If a checklist item shows `[x]` but the referenced file is missing → **STOP** and surface to human. Never silently redo prior session work.

**Contract change ritual:** If a mid-story DTO change is needed:
1. Edit the relevant section of `spec.md`
2. Re-run `/generate-contracts redemption-analytics-advanced` (idempotent)
3. Note in the affected story row's Notes column: "contract updated — refetch types"
