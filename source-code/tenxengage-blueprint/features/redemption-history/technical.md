> **Feature**: [spec.md](spec.md)
> **Purpose**: Implementer reference — Flyway SQL, file paths, query shapes, hook specs.
> **Decisions and intent live in `spec.md`.** Read `spec.md` first, then use this file during implementation.

---

## Flyway Migrations [BE]

_Path: `src/main/resources/db/migration/`_

### V10__create_redemption_export_jobs_table.sql

```sql
CREATE TABLE redemption_export_jobs (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       UUID          NOT NULL REFERENCES clients(id),
    requested_by    UUID          NOT NULL REFERENCES users(id),
    status          VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    format          VARCHAR(10)   NOT NULL,
    scope           VARCHAR(20)   NOT NULL,
    filter_snapshot JSONB         NOT NULL DEFAULT '{}',
    row_count       INTEGER       NULL,
    file_key        VARCHAR(500)  NULL,
    expires_at      TIMESTAMPTZ   NULL,
    failure_reason  VARCHAR(500)  NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN       NOT NULL DEFAULT false,
    version         BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_redemption_export_jobs_client_id        ON redemption_export_jobs(client_id);
CREATE INDEX idx_redemption_export_jobs_client_requester ON redemption_export_jobs(client_id, requested_by);
CREATE INDEX idx_redemption_export_jobs_client_status    ON redemption_export_jobs(client_id, status);
CREATE INDEX idx_redemption_export_jobs_client_created   ON redemption_export_jobs(client_id, created_at DESC);
```

### V11__seed_redemption_history_permissions.sql

```sql
-- ============================================================
-- Redemption History (F-05): new export permission
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(), 'action.redemption.export', 'Export Redemption History', 'Export redemption transaction history as CSV or XLSX', 'REDEMPTION_ACTIONS', 'ACTION', 403, NOW(), NOW(), 'ALL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- Role grants
-- ============================================================

-- PARTNER_SELLER
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_SELLER'
  AND p.permission_key IN ('action.redemption.export')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- PARTNER_ADMIN
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_ADMIN'
  AND p.permission_key IN ('action.redemption.export')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- CLIENT_ADMIN
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN ('action.redemption.export')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- Acme tenant seed grants (dev/seed only)
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN ('action.redemption.export')
ON CONFLICT (client_id, permission_key) DO NOTHING;
```

---

## Package Layout [BE]

_All paths relative to `tenxengage-backend/src/main/java/com/tenxengage/app/`._

### New Files

```
entity/
  redemption/
    RedemptionExportJob.java                  (new — extends BaseEntity, implements TenantAware)
entity/enums/
  redemption/
    ExportFormat.java                         (new — CSV, XLSX)
    RedemptionExportStatus.java               (new — PENDING, PROCESSING, COMPLETED, FAILED)
repository/
  redemption/
    RedemptionHistoryRepository.java          (new — custom JPQL queries for filtered history)
    RedemptionExportJobRepository.java        (new)
service/
  redemption/
    RedemptionHistoryService.java             (new)
    RedemptionExportService.java              (new)
controller/
  redemption/
    RedemptionHistoryController.java          (new — company + all-tenant endpoints)
    RedemptionExportController.java           (new — export trigger, poll, download)
dto/
  request/
    redemption/
      TriggerExportRequest.java               (new)
  response/
    redemption/
      RedemptionExportJobResponse.java        (new — poll response)
      RedemptionExportJobDetailResponse.java  (new — with presigned downloadUrl)
      RedemptionAdminHistoryResponse.java     (new — all-tenant list with userId, userDisplayName, partnerCompanyId, partnerCompanyName)
```

_Migrations: `src/main/resources/db/migration/V10__create_redemption_export_jobs_table.sql`, `V11__seed_redemption_history_permissions.sql`_

### Modified Files

| File | Change |
|---|---|
| `controller/RedemptionRequestController.java` | Add `dateFrom`, `dateTo`, `status` (RedemptionStatus), `category` (RedemptionCategory) `@RequestParam` to `GET /` list endpoint; validate status/category as enums; validate sortBy allowlist |
| `service/RedemptionSubmissionService.java` | Update `getPersonalRedemptions()` signature to accept filter params; update `getRedemptionById()` to populate `linkedReturnId` from F-06 entity (null-safe lookup) |
| `dto/response/RedemptionRequestResponse.java` | Add `catalogItemName: String`, `completedAt: Instant`; update `from()` factory to accept catalog item name as param |
| `dto/response/RedemptionRequestDetailResponse.java` | Add `linkedReturnId: UUID` (nullable); update `from()` factory |
| `entity/enums/AuditResourceType.java` | Add `REDEMPTION_EXPORT_JOB` value |

### Test Files

```
test/java/com/tenxengage/app/
  service/
    redemption/
      RedemptionHistoryServiceTest.java       (new)
      RedemptionExportServiceTest.java        (new)
  controller/
    redemption/
      RedemptionHistoryControllerTest.java    (new)
      RedemptionExportControllerTest.java     (new)
  testdata/
    RedemptionExportJobFixtures.java          (new — builder-return pattern; mandatory)
```

---

## Repository Queries [BE]

### RedemptionHistoryRepository

_All methods include `clientId` for tenant isolation._

```java
// Filtered personal history
@Query("SELECT r FROM RedemptionRequest r " +
       "LEFT JOIN FETCH r.catalogItem " +
       "WHERE r.clientId = :clientId AND r.userId = :userId " +
       "AND (:status IS NULL OR r.status = :status) " +
       "AND (:category IS NULL OR r.category = :category) " +
       "AND (:dateFrom IS NULL OR r.submittedAt >= :dateFrom) " +
       "AND (:dateTo IS NULL OR r.submittedAt <= :dateTo)")
Page<RedemptionRequest> findPersonalHistory(
    @Param("userId") UUID userId,
    @Param("clientId") UUID clientId,
    @Param("status") RedemptionStatus status,
    @Param("category") RedemptionCategory category,
    @Param("dateFrom") Instant dateFrom,
    @Param("dateTo") Instant dateTo,
    Pageable pageable);

// Count for export threshold check
@Query("SELECT COUNT(r) FROM RedemptionRequest r " +
       "WHERE r.clientId = :clientId AND r.userId = :userId " +
       "AND (:status IS NULL OR r.status = :status) " +
       "AND (:category IS NULL OR r.category = :category) " +
       "AND (:dateFrom IS NULL OR r.submittedAt >= :dateFrom) " +
       "AND (:dateTo IS NULL OR r.submittedAt <= :dateTo)")
long countPersonalHistory(
    @Param("userId") UUID userId,
    @Param("clientId") UUID clientId,
    @Param("status") RedemptionStatus status,
    @Param("category") RedemptionCategory category,
    @Param("dateFrom") Instant dateFrom,
    @Param("dateTo") Instant dateTo);

// Company history — filtered by walletId (resolved from partnerCompanyId in service layer)
@Query("SELECT r FROM RedemptionRequest r " +
       "LEFT JOIN FETCH r.catalogItem " +
       "WHERE r.clientId = :clientId AND r.walletId = :walletId " +
       "AND (:status IS NULL OR r.status = :status) " +
       "AND (:category IS NULL OR r.category = :category) " +
       "AND (:dateFrom IS NULL OR r.submittedAt >= :dateFrom) " +
       "AND (:dateTo IS NULL OR r.submittedAt <= :dateTo)")
Page<RedemptionRequest> findCompanyHistory(
    @Param("walletId") UUID walletId,
    @Param("clientId") UUID clientId,
    @Param("status") RedemptionStatus status,
    @Param("category") RedemptionCategory category,
    @Param("dateFrom") Instant dateFrom,
    @Param("dateTo") Instant dateTo,
    Pageable pageable);

// All-tenant history — CLIENT_ADMIN; optional userId filter
@Query("SELECT r FROM RedemptionRequest r " +
       "LEFT JOIN FETCH r.catalogItem " +
       "WHERE r.clientId = :clientId " +
       "AND (:userId IS NULL OR r.userId = :userId) " +
       "AND (:status IS NULL OR r.status = :status) " +
       "AND (:category IS NULL OR r.category = :category) " +
       "AND (:dateFrom IS NULL OR r.submittedAt >= :dateFrom) " +
       "AND (:dateTo IS NULL OR r.submittedAt <= :dateTo)")
Page<RedemptionRequest> findTenantHistory(
    @Param("clientId") UUID clientId,
    @Param("userId") UUID userId,
    @Param("status") RedemptionStatus status,
    @Param("category") RedemptionCategory category,
    @Param("dateFrom") Instant dateFrom,
    @Param("dateTo") Instant dateTo,
    Pageable pageable);

@Query("SELECT COUNT(r) FROM RedemptionRequest r " +
       "WHERE r.clientId = :clientId " +
       "AND (:userId IS NULL OR r.userId = :userId) " +
       "AND (:status IS NULL OR r.status = :status) " +
       "AND (:category IS NULL OR r.category = :category) " +
       "AND (:dateFrom IS NULL OR r.submittedAt >= :dateFrom) " +
       "AND (:dateTo IS NULL OR r.submittedAt <= :dateTo)")
long countTenantHistory(
    @Param("clientId") UUID clientId,
    @Param("userId") UUID userId,
    @Param("status") RedemptionStatus status,
    @Param("category") RedemptionCategory category,
    @Param("dateFrom") Instant dateFrom,
    @Param("dateTo") Instant dateTo);
```

### RedemptionExportJobRepository

```java
Optional<RedemptionExportJob> findByIdAndClientId(UUID id, UUID clientId);
Page<RedemptionExportJob> findByRequestedByAndClientId(UUID requestedBy, UUID clientId, Pageable pageable);
```

---

## Package Layout [FE]

_All paths relative to `tenxengage-frontend/src/`._

| Responsibility | File Path |
|---|---|
| TypeScript types | `types/redemption-history/redemption-history.types.ts` |
| API service | `services/redemption-history/redemption-history.service.ts` |
| Personal history hook | `hooks/redemption-history/usePersonalRedemptions.ts` |
| Company history hook | `hooks/redemption-history/useCompanyRedemptions.ts` |
| Tenant history hook | `hooks/redemption-history/useTenantRedemptions.ts` |
| Detail hook | `hooks/redemption-history/useRedemptionDetail.ts` |
| Export trigger mutation | `hooks/redemption-history/useTriggerExport.ts` |
| Export job poll hook | `hooks/redemption-history/useExportJob.ts` |
| History table component | `components/redemption-history/TransactionHistoryTable.tsx` |
| Filter bar component | `components/redemption-history/HistoryFilterBar.tsx` |
| Detail sheet component | `components/redemption-history/TransactionDetailSheet.tsx` |
| Export dialog component | `components/redemption-history/ExportDialog.tsx` |
| Component tests | `components/redemption-history/__tests__/TransactionHistoryTable.test.tsx` |
| Component tests | `components/redemption-history/__tests__/ExportDialog.test.tsx` |
| Partner history page | `pages/redemption-history/TransactionHistoryPage.tsx` |
| Admin history page | `pages/redemption-history/TenantTransactionHistoryPage.tsx` |
| Routes (modify App.tsx) | `<ProtectedRoute permission="module.redemption_store"><Route path="/redemption/history" element={<TransactionHistoryPage />} /></ProtectedRoute>` |
| Routes (modify App.tsx) | `<ProtectedRoute permission="action.redemption.view_all_history"><Route path="/redemption/admin/history" element={<TenantTransactionHistoryPage />} /></ProtectedRoute>` |
| E2E test | `e2e/redemption-history.spec.ts` |

---

## Hook Specs [FE]

### `usePersonalRedemptions(filters, page)` — list hook

```
queryKey: ['redemption-history', 'personal', { filters, page }]
staleTime: 2 * 60 * 1000   // 2 min
endpoint: GET /api/v1/redemption/requests
Invalidate on: none (read-only)
```

### `useCompanyRedemptions(filters, page)` — list hook

```
queryKey: ['redemption-history', 'company', { filters, page }]
staleTime: 2 * 60 * 1000
endpoint: GET /api/v1/redemption/requests/company
Invalidate on: none
```

### `useTenantRedemptions(filters, page)` — list hook

```
queryKey: ['redemption-history', 'all-tenant', { filters, page }]
staleTime: 2 * 60 * 1000
endpoint: GET /api/v1/redemption/requests/all
Invalidate on: none
```

### `useRedemptionDetail(id)` — detail hook

```
queryKey: ['redemption-history', 'detail', id]
staleTime: 5 * 60 * 1000
endpoint: GET /api/v1/redemption/requests/{id}
Invalidate on: none
```

### `useTriggerExport()` — mutation

```
endpoint: POST /api/v1/redemption/requests/export
onSuccess (202): store jobId in component state; begin polling via useExportJob
onSuccess (200): trigger browser file download from response bytes
onError: toast.error("Export failed. Please try again.")
```

### `useExportJob(jobId)` — poll hook

```
queryKey: ['redemption-history', 'export-job', jobId]
staleTime: 0  // always fresh while polling
enabled: jobId !== null
refetchInterval: (data) => (data?.status === 'PENDING' || data?.status === 'PROCESSING') ? 3000 : false
endpoint: GET /api/v1/redemption/requests/export/{jobId}
```

---

## Audit Annotations [BE]

| Operation | `action` value | `resourceType` value | `description` |
|---|---|---|---|
| `POST /api/v1/redemption/requests/export` (async — job created) | `DATA_EXPORTED` | `REDEMPTION_EXPORT_JOB` | `"Redemption export job triggered"` |
| `POST /api/v1/redemption/requests/export` (sync — file returned) | `DATA_EXPORTED` | `REDEMPTION_REQUEST` | `"Redemption history exported synchronously"` |

**New enum values required:**
- `AuditResourceType.REDEMPTION_EXPORT_JOB` — add to `entity/enums/AuditResourceType.java` (no migration needed — stored as varchar)

`AuditAction.DATA_EXPORTED` already exists — no new value needed.
