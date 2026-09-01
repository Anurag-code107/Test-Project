> **Feature**: [spec.md](spec.md)
> **Purpose**: Implementer reference — Flyway SQL, file paths, query shapes, hook specs.
> **Decisions and intent live in `spec.md`.** Read `spec.md` first, then use this file during implementation.

---

## Flyway Migrations [BE]

_Path: `src/main/resources/db/migration/`_

### V27__seed_redemption_analytics_permissions.sql

```sql
-- ============================================================
-- Redemption Analytics Basic (F-07): new view_analytics permission
-- Note: module.redemption_store already seeded by F-01 (V8).
-- No new tables — F-07 reads existing RewardWallet, LedgerEntry, RedemptionRequest entities.
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(),
   'action.redemption.view_analytics',
   'View Redemption Analytics',
   'Access the redemption analytics dashboard and export unredeemed balance CSV',
   'REDEMPTION_ACTIONS', 'ACTION', 412, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- CLIENT_ADMIN → action.redemption.view_analytics
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN ('action.redemption.view_analytics')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- Acme tenant seed grants (dev/seed only)
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN ('action.redemption.view_analytics')
ON CONFLICT (client_id, permission_key) DO NOTHING;
```

---

## Package Layout [BE]

_All paths relative to `../tenxengage-backend/`._

```
src/
├── main/
│   ├── java/com/tenxengage/app/
│   │   ├── controller/redemption/
│   │   │   └── RedemptionAnalyticsController.java
│   │   ├── service/redemption/
│   │   │   └── RedemptionAnalyticsService.java
│   │   ├── entity/enums/
│   │   │   └── AuditResourceType.java          (add REDEMPTION_ANALYTICS_EXPORT value)
│   │   └── dto/response/redemption/
│   │       ├── RedemptionAnalyticsSummaryResponse.java
│   │       ├── DateWindowDto.java
│   │       ├── CurrencyTypeRateDto.java
│   │       ├── CurrencyTypeBalanceDto.java
│   │       └── RedemptionCountDto.java
│   └── resources/db/migration/
│       └── V27__seed_redemption_analytics_permissions.sql
└── test/
    └── java/com/tenxengage/app/
        ├── service/redemption/
        │   └── RedemptionAnalyticsServiceTest.java
        └── controller/redemption/
            └── RedemptionAnalyticsControllerTest.java
```

**No new entity or repository files** — F-07 reads from existing entities using extended queries on existing repositories (see Repository Queries section).

---

## Repository Queries [BE]

_Queries to add to existing repositories. All include `clientId` for tenant isolation._

### Extensions to `LedgerEntryRepository`

```
sumAmountByClientIdAndCurrencyIdAndEntryType(clientId: UUID, currencyId: String, entryType: LedgerEntryType): Long
  @Query: SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e
          WHERE e.clientId = :clientId
            AND e.currencyId = :currencyId
            AND e.entryType = :entryType
            AND e.deleted = false
  Purpose: Lifetime earned (entryType = REWARD) and lifetime redeemed (entryType = REDEMPTION) for FR-07.1.

findDistinctCurrencyIdsByClientId(clientId: UUID): List<String>
  @Query: SELECT DISTINCT w.currencyId FROM RewardWallet w
          WHERE w.clientId = :clientId AND w.deleted = false
  Purpose: Determines which currency identifiers are active for a given tenant (show cards for active types only).
```

### Extensions to `RewardWalletRepository`

```
sumBalancesByClientIdAndCurrencyId(clientId: UUID, currencyId: String): BalanceSumProjection
  @Query: SELECT SUM(w.availableBalance) AS available, SUM(w.reservedBalance) AS reserved
          FROM RewardWallet w
          WHERE w.clientId = :clientId AND w.currencyId = :currencyId AND w.deleted = false
  Purpose: Unredeemed balance per currency identifier for FR-07.2.

findAllByClientIdForExport(clientId: UUID): List<RewardWalletExportProjection>
  @Query: SELECT w.userId AS userId, u.displayName AS userName,
                 w.partnerCompanyId AS companyId, c.name AS companyName,
                 w.currencyId AS currencyType,
                 w.availableBalance AS availableBalance,
                 w.reservedBalance AS reservedBalance
          FROM RewardWallet w
          LEFT JOIN User u ON u.id = w.userId
          LEFT JOIN PartnerCompany c ON c.id = w.partnerCompanyId
          WHERE w.clientId = :clientId AND w.deleted = false
          ORDER BY u.displayName ASC
  Purpose: CSV export data with user and company names for FR-07.6.
```

### Extensions to `RedemptionRequestRepository`

> **Date conversion note:** `RedemptionRequest.submittedAt` is `Instant`. The service converts
> `LocalDate` API params to an `Instant` range before calling these queries:
> ```java
> Instant from = dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
> Instant toExclusive = dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
> ```
> All queries below accept `from: Instant` and `toExclusive: Instant` (exclusive upper bound).

```
countByClientIdAndCurrencyIdAndSubmittedAtBetween(
    clientId: UUID, currencyId: String,
    from: Instant, toExclusive: Instant): Long
  @Query: SELECT COUNT(r) FROM RedemptionRequest r
          WHERE r.clientId = :clientId
            AND r.currencyId = :currencyId
            AND r.submittedAt >= :from AND r.submittedAt < :toExclusive
            AND r.deleted = false
  Purpose: Total request count in window per currency identifier (FR-07.3 denominator).

countByClientIdAndCurrencyIdAndStatusInAndSubmittedAtBetween(
    clientId: UUID, currencyId: String,
    statuses: Collection<RedemptionStatus>,
    from: Instant, toExclusive: Instant): Long
  @Query: SELECT COUNT(r) FROM RedemptionRequest r
          WHERE r.clientId = :clientId
            AND r.currencyId = :currencyId
            AND r.status IN :statuses
            AND r.submittedAt >= :from AND r.submittedAt < :toExclusive
            AND r.deleted = false
  Purpose: Failed + cancelled count in window per currency identifier (FR-07.3 numerator).
  Callers pass statuses = [FAILED, CANCELLED].

countGroupByStatusByClientIdAndSubmittedAtBetween(
    clientId: UUID, from: Instant, toExclusive: Instant): List<StatusCountProjection>
  @Query: SELECT r.status AS status, COUNT(r) AS count
          FROM RedemptionRequest r
          WHERE r.clientId = :clientId
            AND r.submittedAt >= :from AND r.submittedAt < :toExclusive
            AND r.deleted = false
          GROUP BY r.status
  Purpose: Status-breakdown counts for FR-07.7 (PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED).
```

---

## Index Recommendations [BE]

_Add to `V27__seed_redemption_analytics_permissions.sql` after the permission seed, or as a separate `V27b` migration if the team prefers to keep DDL and DML separate._

```sql
-- Support LedgerEntry aggregation queries (FR-07.1 lifetime totals)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_entries_client_currency_type
  ON ledger_entries (client_id, currency_id, entry_type)
  WHERE deleted = false;

-- Support RewardWallet balance aggregation (FR-07.2 unredeemed liability)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reward_wallets_client_currency
  ON reward_wallets (client_id, currency_id)
  WHERE deleted = false;

-- Support RedemptionRequest windowed count queries (FR-07.3, FR-07.7)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_redemption_requests_client_status_submitted
  ON redemption_requests (client_id, status, submitted_at)
  WHERE deleted = false;
```

These partial indexes (with `WHERE deleted = false`) exclude soft-deleted rows and are the minimum required to keep cold-query P95 within the 500ms SLA for tenants below the Phase 2 scaling gate (~50k users / ~5M ledger entries).

---

## Package Layout [FE]

_All paths relative to `../tenxengage-frontend/src/`._

```
src/
├── types/
│   └── redemption-analytics.types.ts                    (copy from ../tenxengage-contracts/ after /generate-contracts)
├── services/
│   └── redemption-analytics.service.ts
├── hooks/
│   ├── useRedemptionAnalytics.ts
│   └── useAnalyticsExport.ts
├── components/
│   └── redemption-analytics/
│       ├── RedemptionRateCard.tsx
│       ├── UnredeemedBalanceCard.tsx
│       ├── FailedCancelledRateCard.tsx
│       ├── TotalCountCard.tsx
│       ├── DateRangeFilter.tsx
│       ├── ExportConfirmDialog.tsx
│       └── __tests__/
│           ├── RedemptionRateCard.test.tsx
│           ├── UnredeemedBalanceCard.test.tsx
│           ├── FailedCancelledRateCard.test.tsx
│           ├── TotalCountCard.test.tsx
│           └── DateRangeFilter.test.tsx
└── pages/
    └── redemption/
        └── analytics/
            ├── RedemptionAnalyticsPage.tsx
            └── __tests__/
                └── RedemptionAnalyticsPage.test.tsx
```

**Route entry** — add to `App.tsx` inside an `AppLayout` route group, mirroring the pattern of existing CLIENT_ADMIN-only routes (e.g., `ApprovalQueuePage`):

```tsx
<Route element={<ProtectedRoute permission="action.redemption.view_analytics" />}>
  <Route element={<AppLayout />}>
    <Route path="/redemption/admin/analytics" element={<RedemptionAnalyticsPage />} />
  </Route>
</Route>
```

**Sidebar entry** — add to the Redemption section of the sidebar navigation config:

```tsx
{ label: 'Analytics', path: '/redemption/admin/analytics', permissionKey: 'action.redemption.view_analytics' }
```

---

## Hook Specs [FE]

### `useRedemptionAnalytics(dateFrom, dateTo)`

```ts
queryKey: ['redemption-analytics', { clientId, dateFrom: dateFrom.toISOString(), dateTo: dateTo.toISOString() }]
queryFn: () => redemptionAnalyticsService.getSummary(dateFrom, dateTo)
staleTime: 60 * 1000          // 60s — matches server Redis cache TTL
gcTime: 5 * 60 * 1000
enabled: !!clientId
```

Date range defaults applied in the hook: `dateFrom = today - 30 days`, `dateTo = today` when not provided by the caller.

Invalidation: key includes `dateFrom`/`dateTo` — date range change triggers a new key, which automatically refetches. No manual invalidation needed.

### `useAnalyticsExport()`

```ts
// Mutation — not a query; no queryKey
mutationFn: () => redemptionAnalyticsService.exportUnredeemedBalances()
// Returns: Blob (text/csv)
// onSuccess: trigger browser download:
//   const url = URL.createObjectURL(blob)
//   const a = document.createElement('a')
//   a.href = url; a.download = 'redemption-unredeemed-balances.csv'; a.click()
//   URL.revokeObjectURL(url)
// onError (status 429): extract Retry-After from error response headers;
//   pass countdown seconds to ExportConfirmDialog for disabled-with-countdown UX (FR-07.9)
```

---

## Audit Annotations [BE]

| Endpoint | `action` | `resourceType` | `description` |
|---|---|---|---|
| `GET /api/v1/redemption/analytics/export` | `DATA_EXPORTED` | `REDEMPTION_ANALYTICS_EXPORT` | `Analytics unredeemed balance export downloaded` |

**New enum value** to add to `AuditResourceType.java`:

```
REDEMPTION_ANALYTICS_EXPORT
```

_Path: `src/main/java/com/tenxengage/app/entity/enums/AuditResourceType.java`_

`AuditAction.DATA_EXPORTED` already exists — no change required to `AuditAction.java`.
