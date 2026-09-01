> **Feature**: [spec.md](spec.md)
> **Purpose**: Implementer reference — Flyway SQL, file paths, query shapes, hook specs.
> **Decisions and intent live in `spec.md`.** Read `spec.md` first, then use this file during implementation.

---

## Flyway Migrations [BE]

_Path: `src/main/resources/db/migration/`_

### V18__alter_redemption_request_add_approval_fields.sql

```sql
-- ============================================================
-- F-04 Redemption Approval Queue: Extend redemption_requests
-- ============================================================

ALTER TABLE redemption_requests
    ADD COLUMN IF NOT EXISTS reviewed_by      UUID         REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS reviewed_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(1000);

-- Index to support approval queue query (client_id + status = PENDING_APPROVAL)
-- idx_redemption_requests_client_status already exists from V16; no new index needed.
```

### V19__seed_redemption_approval_permissions.sql

```sql
-- ============================================================
-- F-04 Redemption Approval Queue: Permission catalog
-- Note: module.redemption_store already seeded in F-01 V8.
--       No new feature flag — redemption_store covers this feature.
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(),
   'action.redemption.approve',
   'Approve/Reject Redemptions',
   'View the redemption approval queue and approve or reject pending redemption requests',
   'REDEMPTION_ACTIONS', 'ACTION', 405, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- CLIENT_ADMIN → action.redemption.approve
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN (
    'action.redemption.approve'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- ACTIVITY_APPROVER → action.redemption.approve
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'ACTIVITY_APPROVER'
  AND p.permission_key IN (
    'action.redemption.approve'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- Acme tenant seed grants (dev/seed only)
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN (
    'action.redemption.approve'
)
ON CONFLICT (client_id, permission_key) DO NOTHING;
```

---

## Package Layout [BE]

_All paths relative to `tenxengage-backend/src/main/java/com/tenxengage/app/`._

**New files (feature sub-package: `redemption`):**

| Responsibility | File Path |
|---|---|
| Approval controller | `controller/redemption/RedemptionApprovalController.java` |
| Approval service | `service/redemption/RedemptionApprovalService.java` |
| Reject request DTO | `dto/request/redemption/RejectRedemptionRequest.java` |
| Approval queue item response DTO | `dto/response/redemption/ApprovalQueueItemResponse.java` |
| Approval service test | `test/java/com/tenxengage/app/service/redemption/RedemptionApprovalServiceTest.java` |
| Approval controller test | `test/java/com/tenxengage/app/controller/redemption/RedemptionApprovalControllerTest.java` |
| Redemption request fixtures | `test/java/com/tenxengage/app/testdata/RedemptionRequestFixtures.java` |

**Modified existing files (flat at layer root — F-03 files):**

| Responsibility | File Path | Change |
|---|---|---|
| Redemption request entity | `entity/RedemptionRequest.java` | Add `reviewedBy`, `reviewedAt`, `rejectionReason` fields |
| Redemption request repository | `repository/RedemptionRequestRepository.java` | Add `findApprovalQueue` and `findByIdAndClientIdForUpdate` query methods |
| Redemption request detail response | `dto/response/RedemptionRequestDetailResponse.java` | Add `reviewedBy`, `reviewedAt`, `rejectionReason` fields (additive) |

**Flyway migrations:**

| File | Purpose |
|---|---|
| `src/main/resources/db/migration/V18__alter_redemption_request_add_approval_fields.sql` | ALTER TABLE — approval columns |
| `src/main/resources/db/migration/V19__seed_redemption_approval_permissions.sql` | Permission seed |

---

## Repository Queries [BE]

_Added to existing `RedemptionRequestRepository.java`:_

```java
// Approval queue — all PENDING_APPROVAL items for tenant with optional filters.
// JOIN FETCH user and catalogItem to avoid N+1 when building ApprovalQueueItemResponse
// (which needs user displayName and catalogItem name). Both associations are lazy by default.
@Query("""
    SELECT r FROM RedemptionRequest r
    JOIN FETCH r.user u
    JOIN FETCH r.catalogItem ci
    WHERE r.clientId = :clientId
      AND r.status = 'PENDING_APPROVAL'
      AND r.deleted = false
      AND (:currencyId IS NULL OR r.currencyId = :currencyId)
      AND (:catalogItemId IS NULL OR r.catalogItemId = :catalogItemId)
      AND (:startDate IS NULL OR r.submittedAt >= :startDate)
      AND (:endDate IS NULL OR r.submittedAt <= :endDate)
    ORDER BY r.submittedAt DESC
    """)
Page<RedemptionRequest> findApprovalQueue(
    @Param("clientId") UUID clientId,
    @Param("currencyId") String currencyId,
    @Param("catalogItemId") UUID catalogItemId,
    @Param("startDate") Instant startDate,
    @Param("endDate") Instant endDate,
    Pageable pageable
);

// Pessimistic write lock for approve/reject — prevents concurrent double-action
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM RedemptionRequest r WHERE r.id = :id AND r.clientId = :clientId")
Optional<RedemptionRequest> findByIdAndClientIdForUpdate(
    @Param("id") UUID id,
    @Param("clientId") UUID clientId
);
```

_Existing `findByIdAndClientId(UUID id, UUID clientId)` reused for non-locking reads._

---

## Package Layout [FE]

_All paths relative to `tenxengage-frontend/src/`. Feature sub-folder: `redemption/` (existing from F-03; new files added to existing folder)._

| Responsibility | File Path |
|---|---|
| TypeScript types (extended) | `types/redemption/redemption.types.ts` — add `ApprovalQueueItem`, `RejectRedemptionRequest` types (copy from contracts repo after `/generate-contracts`) |
| API service | `services/redemption/redemption-approval.service.ts` |
| Queue list hook | `hooks/redemption/useApprovalQueue.ts` |
| Mutation hooks | `hooks/redemption/useRedemptionApproval.ts` — exports `useApproveRedemption()` and `useRejectRedemption()` |
| Approval queue table | `components/redemption/ApprovalQueueTable.tsx` |
| Filter controls | `components/redemption/ApprovalQueueFilters.tsx` |
| Approve confirm dialog | `components/redemption/ApproveConfirmDialog.tsx` |
| Reject dialog (with required reason field) | `components/redemption/RejectDialog.tsx` |
| Page | `pages/redemption/ApprovalQueuePage.tsx` |
| Table component test | `components/redemption/__tests__/ApprovalQueueTable.test.tsx` |
| Reject dialog test | `components/redemption/__tests__/RejectDialog.test.tsx` |
| E2E test | `e2e/redemption-approval-queue.spec.ts` |

**Route entry — add to `App.tsx`:**
```tsx
<Route element={<ProtectedRoute permission="action.redemption.approve" />}>
  <Route element={<AppLayout />}>
    <Route path="/redemption/approval-queue" element={<ApprovalQueuePage />} />
  </Route>
</Route>
```

**Sidebar entry — add to redemption nav section:**
```ts
{
  label: "Approval Queue",
  path: "/redemption/approval-queue",
  permissionKey: "action.redemption.approve"
}
```

---

## Hook Specs [FE]

### `useApprovalQueue(filters)` (list hook)

```ts
queryKey: ['approval-queue', { clientId, currencyId, catalogItemId, startDate, endDate, requestType, page, size }]
staleTime: 5 * 60 * 1000   // 5 min
```

Invalidate on: `useApproveRedemption` mutation success, `useRejectRedemption` mutation success.

### `useApproveRedemption()` (mutation hook)

```ts
mutationFn: (redemptionId: string) => redemptionApprovalService.approve(redemptionId)
onSuccess: () => queryClient.invalidateQueries({ queryKey: ['approval-queue'] })
```

### `useRejectRedemption()` (mutation hook)

```ts
mutationFn: ({ redemptionId, rejectionReason }: { redemptionId: string; rejectionReason: string }) =>
  redemptionApprovalService.reject(redemptionId, { rejectionReason })
onSuccess: () => queryClient.invalidateQueries({ queryKey: ['approval-queue'] })
```

---

## Audit Annotations [BE]

| Endpoint | `action` | `resourceType` | `description` |
|---|---|---|---|
| `POST /redemption/requests/{id}/approve` | `APPROVED` | `REDEMPTION_REQUEST` | `Approved redemption request` |
| `POST /redemption/requests/{id}/reject` | `REJECTED` | `REDEMPTION_REQUEST` | `Rejected redemption request` |

**New AuditAction enum values:** None — `APPROVED` and `REJECTED` already exist.

**New AuditResourceType enum values:** None — `REDEMPTION_REQUEST` already added by F-03.
