# Stories Plan: redemption-analytics-basic

## Feature
- **Spec**: `features/redemption-analytics-basic/spec.md` (status: reviewed)
- **Stories folder**: `features/redemption-analytics-basic/stories/`
- **Branch**: `features/redemption-analytics-basic`

## Phase 1.5 — Flow-level completeness probe result
| # | Gap | Resolution | Story / Note |
|---|---|---|---|
| 1 | `DateRangeFilter` — no error copy for > 24-month selection (FR-07.4) | Added verbatim microcopy to US-01 | "Date range cannot exceed 24 months" |

## Foundation tasks

| ID | Title | Deps | Key files (2–3 paths) | Done-when |
|----|-------|------|----------------------|-----------|
| F0 | Contracts (FIRST) | — | `../tenxengage-contracts/` | `/generate-contracts` completes |
| F1 | Enums | F0 | `entity/enums/AuditResourceType.java` | `./gradlew compileJava` — `REDEMPTION_ANALYTICS_EXPORT` value present |
| F2 | Flyway V27 (permission seed + indexes) | F1 | `db/migration/V27__seed_redemption_analytics_permissions.sql` | `./gradlew flywayMigrate` — permission row + 3 indexes present in DB |
| F3 | Repository query extensions | F2 | `LedgerEntryRepository.java`, `RewardWalletRepository.java`, `RedemptionRequestRepository.java` | `./gradlew test` — all new query methods compile and return correct types |

_F4 skipped — permission seed is inside F2 (V27). F5 skipped — no Kafka events._

## Story index

| ID | Title | Layers | Seeds | Touches Entities | Deps | Parallel with |
|----|-------|--------|-------|-----------------|------|--------------|
| US-01 | View analytics dashboard | BE + FE | S-01, S-02 (balance), S-03, S-04 | RewardWallet, LedgerEntry, RedemptionRequest | Foundation | — |
| US-02 | Export unredeemed balances CSV | BE + FE | S-02 (export) | RewardWallet | Foundation + US-01 BE (BE layer); US-01 FE (FE layer) | — |

## Dependency graph

```
F0 (contracts — FIRST)
└── F1 (AuditResourceType enum)
    └── F2 (V27 Flyway — permission seed + indexes)
        └── F3 (repository query extensions)
            └── US-01 BE
                ├── US-01 FE (can start once US-01 BE is done)
                └── US-02 BE (sequential — same controller + service files)
                    └── US-02 FE (depends on US-01 FE page existing)
```

_US-02 BE must follow US-01 BE sequentially — both write to `RedemptionAnalyticsController.java` and `RedemptionAnalyticsService.java`; running them on parallel sub-branches causes merge conflicts. US-02 FE depends on US-01 FE because the export button lives inside `RedemptionAnalyticsPage`._

## Per-story capsules

---

### US-01 — View analytics dashboard

- **Layers:** BE + FE
- **Seeds:** S-01, S-02 (balance card portion), S-03, S-04 — `seed_id: ["S-01", "S-02", "S-03", "S-04"]`
- **Trigger:** CLIENT_ADMIN navigates to `/redemption/admin/analytics`
- **Steps:** Page loads with default range (Last 30 days) → API called → 4 metric card groups render; user can click presets (Last 7d / 30d / 90d / 12mo) or use calendar picker to change range → windowed cards refresh; lifetime cards stay unchanged
- **Acceptance Criteria:**
  - AC-1: `GET /api/v1/redemption/analytics` returns 200 with `RedemptionAnalyticsSummaryResponse`; each nested list has one entry per active `currencyId` in the tenant (only wallets that exist)
  - AC-2: `redemptionRates[].ratePercentage` reflects lifetime LedgerEntry data (not date-filtered); `unredeemedBalances[].totalOutstanding` is a current wallet snapshot (not date-filtered)
  - AC-3: `failedCancelledRates[]` and `totalRedemptionCount` reflect only `RedemptionRequest` rows with `submittedAt` in the requested Instant window derived from dateFrom/dateTo
  - AC-4: Identical request within 60s returns cached response (Redis); cache key scoped to `{clientId}:{dateFrom}:{dateTo}`
  - AC-5: 422 when `dateFrom` after `dateTo` or span > 730 days; 403 when caller lacks `action.redemption.view_analytics`; 401 with no token
  - AC-6: FE renders 4 metric card groups per active `currencyId`; `<Skeleton>` while data loads; "No redemptions in this period" when `hasActivity = false` on windowed cards; "No program activity yet" when `redemptionRates` empty (brand-new tenant)
  - AC-7: Preset buttons and custom calendar picker update query key → refetch; custom picker rejects ranges > 24 months client-side with inline message "Date range cannot exceed 24 months"
- **Out of Scope:** CSV export (US-02); per-item or tier breakdowns (Phase 2 F-08); cross-tenant aggregation
- **UI states:** Loading: `<Skeleton>` on each card; Empty (no wallets): "No program activity yet" on all cards; Empty (no windowed activity): "No redemptions in this period" on FR-07.3 + FR-07.7 cards; Error (5xx): toast + cards show retry state
- **Negative paths:** dateFrom after dateTo → 422 surfaced as toast; non-CLIENT_ADMIN → 403 → ProtectedRoute redirect; date span > 24mo → client-side validation inline message
- **E2E scenarios:**
  - S1 happy-path dashboard load _(covers AC-1, AC-2, AC-3, AC-6)_: navigate → all 4 card groups visible with data
  - S2 date filter preset _(covers AC-3, AC-7)_: click "Last 7 days" → windowed cards refresh; lifetime cards unchanged
  - S3 empty state _(covers AC-6)_: mock API returns `hasActivity=false` → "No redemptions in this period" visible
  - S4 permission guard _(covers AC-5)_: PARTNER_SELLER JWT → redirected away from analytics page
- **BE task intents:** 5 response DTO records in `dto/response/redemption/`; `RedemptionAnalyticsService.getAnalyticsSummary()` with Redis `@Cacheable`; `RedemptionAnalyticsController.getAnalyticsSummary()` with `@RequiresPermission` + `@Validated`; unit test + @WebMvcTest
- **FE task intents:** `redemption-analytics.types.ts`, `redemption-analytics.service.ts` (getSummary call), `useRedemptionAnalytics` hook (staleTime 60s), 4 metric card components + `DateRangeFilter` + tests, `RedemptionAnalyticsPage` + App.tsx route
- **Done when:** `./gradlew test` (BE) + `npm run test` (FE) + 4 Playwright scenarios pass; every AC referenced by ≥1 test

---

### US-02 — Export unredeemed balances CSV

- **Layers:** BE + FE
- **Seed:** S-02 (export portion) — `seed_id: "S-02"`
- **Trigger:** CLIENT_ADMIN clicks "Export" button on the analytics page (built in US-01)
- **Steps:** Export button → `ExportConfirmDialog` opens → user clicks Confirm → `GET /api/v1/redemption/analytics/export` → CSV file downloads; if rate limit hit → button disabled with countdown
- **Acceptance Criteria:**
  - AC-1: `GET /api/v1/redemption/analytics/export` returns 200 with `Content-Disposition: attachment; filename="redemption-unredeemed-balances.csv"` and headers: `userId, userName, companyId, companyName, currencyType, availableBalance, reservedBalance`
  - AC-2: One CSV row per wallet; `companyId=""` + `companyName="Individual"` for wallets with null `partnerCompanyId`; `currencyType` = `RewardWallet.currencyId` string value
  - AC-3: Audit row written with `action=DATA_EXPORTED`, `resourceType=REDEMPTION_ANALYTICS_EXPORT`, actor = calling userId
  - AC-4: 4th export request within 60s from the same tenant returns 429 with `Retry-After` header; 403 for non-CLIENT_ADMIN; 401 with no token
  - AC-5: FE export button opens dialog; on confirm, browser downloads CSV; on 429, button is disabled showing "Export limit reached. You can export again in {N} seconds." countdown until re-enabled
- **Out of Scope:** Analytics dashboard metrics (US-01); background/async export jobs; cross-tenant export
- **NFR notes:** Telemetry: `analytics_export_downloaded` log event emitted with `tenantId, userId, rowCount` on success (spec `## Observability`)
- **UI states:** ExportConfirmDialog loading: Confirm button shows spinner + disabled while mutation in flight; 429 state: button disabled with countdown; 5xx error: toast "Export failed. Please try again."
- **Negative paths:** 429 → countdown UX per FR-07.9; 5xx → error toast; non-CLIENT_ADMIN → 403 → ProtectedRoute (route inaccessible, export button never visible)
- **E2E scenarios:**
  - S1 export happy path _(covers AC-1, AC-2, AC-5)_: open dialog → confirm → CSV download triggered (assert `content-disposition` header or download event)
  - S2 rate limit UX _(covers AC-4, AC-5)_: mock API returns 429 with `Retry-After: 45` → button text shows countdown; assert disabled state
- **BE task intents:** `RedemptionAnalyticsService.exportUnredeemedBalances()` reading `RewardWallet` via export projection; `RedemptionAnalyticsController.exportUnredeemedBalances()` GET endpoint; `@Audited` annotation; per-tenant rate limit bucket; unit test + @WebMvcTest
- **FE task intents:** `redemption-analytics.service.ts` (exportUnredeemedBalances call added to existing file), `useAnalyticsExport` hook (mutation + Blob download), `ExportConfirmDialog` + test, wire export button into `RedemptionAnalyticsPage`
- **Done when:** `./gradlew test` (BE) + `npm run test` (FE) + 2 Playwright scenarios pass; every AC referenced by ≥1 test

---

## Test plan highlights

| Scenario | Class | What it verifies | Depends on |
|---|---|---|---|
| Analytics GET contract conformance (200, 400, 422) | `RedemptionAnalyticsContractConformanceTest` | Response body matches OpenAPI contract; error shapes match on 400/422 | US-01 |
| Export GET contract conformance (200, 403, 429) | `RedemptionAnalyticsContractConformanceTest` | CSV content-type header; 429 includes Retry-After | US-02 |
| Tenant A analytics data never visible to Tenant B | `RedemptionAnalyticsIsolationIT` | GET analytics with Tenant B JWT returns Tenant B data only (not Tenant A ledger entries) | US-01 |
| Unauthenticated analytics request → 401 | `RedemptionAnalyticsIsolationIT` | No token → 401; no body leakage | US-01 |
| Analytics rate limit enforcement (11 req → 429) | `RedemptionAnalyticsIsolationIT` | 11th request returns 429 with Retry-After | US-01 |
| Export rate limit enforcement (4 req → 429) | `RedemptionAnalyticsIsolationIT` | 4th per-tenant export returns 429 | US-02 |
| Export audit row written on success | `RedemptionAnalyticsAuditIT` | `audit_log` has `DATA_EXPORTED / REDEMPTION_ANALYTICS_EXPORT` row after 200 export | US-02 |
| Analytics read → NO audit row written | `RedemptionAnalyticsAuditIT` | `audit_log` unchanged after GET analytics (read-only, no audit) | US-01 |
| Failed export (403) → NO audit row | `RedemptionAnalyticsAuditIT` | Audit count unchanged after 403 response | US-02 |
| Aggregation accuracy against real DB | `RedemptionAnalyticsQueryIT` | Insert known ledger entries → assert ratePercentage = expected value; cache bypassed (TTL=0 test profile) | US-01 |
| E2E cross-story: load dashboard → filter → export | `e2e/redemption-analytics-basic/full-flow.spec.ts` | Real BE: page loads, date preset change refreshes windowed cards, export downloads CSV | US-01, US-02 |

## Story count summary
- Total: 2 stories
- BE + FE: 2 | FE-only: 0 | BE-only: 0
