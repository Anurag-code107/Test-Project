> **Feature**: [spec.md](spec.md)
> **Purpose**: Implementer reference — Flyway SQL, file paths, query shapes, hook specs.
> **Decisions and intent live in `spec.md`.** Read `spec.md` first, then use this file during implementation.

---

## Flyway Migrations [BE]

_Path: `src/main/resources/db/migration/`_

### V25__create_redemption_returns_table.sql

```sql
-- ============================================================
-- F-06 Non-Cash Returns: RedemptionReturn entity table
-- ============================================================
CREATE TYPE return_status AS ENUM (
    'PENDING_APPROVAL',
    'APPROVED',
    'RETURN_CONFIRMED',
    'RETURN_REJECTED',
    'CANCELLED',
    'RETURN_TIMED_OUT'
);

CREATE TABLE redemption_returns (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id               UUID          NOT NULL REFERENCES clients(id),
    redemption_id           UUID          NOT NULL REFERENCES redemption_requests(id),
    partner_user_id         UUID          NOT NULL,
    status                  return_status NOT NULL DEFAULT 'PENDING_APPROVAL',
    reason                  TEXT          NULL,
    reviewed_by             UUID          NULL,
    reviewed_at             TIMESTAMPTZ   NULL,
    review_notes            TEXT          NULL,
    vendor_return_reference VARCHAR(255)  NULL,
    amount                  NUMERIC(19,4) NOT NULL,
    currency_id             VARCHAR(50)   NOT NULL,
    approved_at             TIMESTAMPTZ   NULL,
    timed_out_at            TIMESTAMPTZ   NULL,
    confirmed_at            TIMESTAMPTZ   NULL,
    rejected_at             TIMESTAMPTZ   NULL,
    cancelled_at            TIMESTAMPTZ   NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted                 BOOLEAN       NOT NULL DEFAULT false,
    version                 BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_redemption_returns_client_id     ON redemption_returns(client_id);
CREATE INDEX idx_redemption_returns_client_status ON redemption_returns(client_id, status);
CREATE INDEX idx_redemption_returns_redemption_id ON redemption_returns(redemption_id);
CREATE INDEX idx_redemption_returns_partner_user  ON redemption_returns(client_id, partner_user_id);
CREATE INDEX idx_redemption_returns_vendor_ref    ON redemption_returns(vendor_return_reference)
    WHERE vendor_return_reference IS NOT NULL;
```

### V26__seed_redemption_return_permissions.sql

```sql
-- ============================================================
-- F-06 Non-Cash Returns: Permission catalog
-- Note: module.redemption_store already seeded in F-01 V8.
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(),
   'action.redemption.return.request',
   'Request Redemption Return',
   'Submit, view, and cancel return requests for completed non-cash redemptions',
   'REDEMPTION_ACTIONS', 'ACTION', 410, NOW(), NOW(), 'EXTERNAL'),
  (gen_random_uuid(),
   'action.redemption.return.review',
   'Review Return Requests',
   'View, approve, reject, and resolve return requests in the admin queue',
   'REDEMPTION_ACTIONS', 'ACTION', 411, NOW(), NOW(), 'INTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- F-06: Feature flag
-- ============================================================
INSERT INTO feature_flags (id, feature_key, description, starter_enabled, professional_enabled, enterprise_enabled, created_at, updated_at, category)
VALUES (
    gen_random_uuid(),
    'redemption_non_cash_returns',
    'Enable non-cash gift-card / prepaid return requests for partners',
    false, true, true, NOW(), NOW(), 'REDEMPTION'
)
ON CONFLICT (feature_key) DO NOTHING;

-- ============================================================
-- PARTNER_ADMIN → action.redemption.return.request
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_ADMIN'
  AND p.permission_key IN ('action.redemption.return.request')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- PARTNER_SELLER → action.redemption.return.request
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_SELLER'
  AND p.permission_key IN ('action.redemption.return.request')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- CLIENT_ADMIN → action.redemption.return.review
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'CLIENT_ADMIN'
  AND p.permission_key IN ('action.redemption.return.review')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- ACTIVITY_APPROVER → action.redemption.return.review
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'ACTIVITY_APPROVER'
  AND p.permission_key IN ('action.redemption.return.review')
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- Acme tenant seed grants (dev/seed only)
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN (
    'action.redemption.return.request',
    'action.redemption.return.review'
)
ON CONFLICT (client_id, permission_key) DO NOTHING;
```

---

## Package Layout [BE]

_All paths relative to `../tenxengage-backend/src/main/java/com/tenxengage/app/`_

| Responsibility | File Path |
|---|---|
| `RedemptionReturn` entity | `entity/RedemptionReturn.java` (extends BaseEntity, implements TenantAware) |
| `ReturnStatus` enum | `entity/enums/ReturnStatus.java` |
| `ReturnResolution` enum | `entity/enums/ReturnResolution.java` |
| `RedemptionReturn` repository | `repository/RedemptionReturnRepository.java` |
| Return business service | `service/redemption/ReturnService.java` |
| Xoxoday vendor call service | `service/redemption/ReturnVendorService.java` |
| Timeout scheduler | `service/redemption/ReturnTimeoutScheduler.java` |
| Partner return controller | `controller/redemption/ReturnController.java` |
| Admin return controller | `controller/redemption/ReturnAdminController.java` |
| Return webhook controller | `controller/ReturnWebhookController.java` |
| Kafka event record | `event/ReturnEvent.java` |
| Kafka event producer | `service/ReturnEventProducer.java` |
| Submit return request DTO | `dto/request/redemption/SubmitReturnRequest.java` |
| Reject return request DTO | `dto/request/redemption/RejectReturnRequest.java` |
| Resolve TIMED_OUT request DTO | `dto/request/redemption/ResolveTimedOutReturnRequest.java` |
| Partner list response DTO | `dto/response/redemption/ReturnSummaryResponse.java` |
| Detail response DTO (partner + admin) | `dto/response/redemption/ReturnDetailResponse.java` |
| Admin queue item response DTO | `dto/response/redemption/ReturnQueueItemResponse.java` |
| `AuditResourceType` update | `entity/enums/AuditResourceType.java` — add `REDEMPTION_RETURN` |
| `KafkaConfig` update | `config/KafkaConfig.java` — add `return-events` topic bean (3 partitions, 1 replica) |
| Flyway schema migration | `resources/db/migration/V25__create_redemption_returns_table.sql` |
| Flyway permission seed | `resources/db/migration/V26__seed_redemption_return_permissions.sql` |
| Service unit test | `test/.../service/redemption/ReturnServiceTest.java` |
| Partner controller test | `test/.../controller/redemption/ReturnControllerTest.java` |
| Admin controller test | `test/.../controller/redemption/ReturnAdminControllerTest.java` |
| Fixtures | `test/.../testdata/RedemptionReturnFixtures.java` (builder-return pattern; mandatory) |

---

## Repository Queries [BE]

_`RedemptionReturnRepository extends JpaRepository<RedemptionReturn, UUID>`_

All methods include `clientId` for tenant isolation.

- `findByIdAndClientId(UUID id, UUID clientId)` → `Optional<RedemptionReturn>` — admin single fetch
- `findByIdAndClientIdAndPartnerUserId(UUID id, UUID clientId, UUID partnerUserId)` → `Optional<RedemptionReturn>` — partner ownership check
- `findByClientIdAndPartnerUserId(UUID clientId, UUID partnerUserId, Pageable pageable)` → `Page<RedemptionReturn>` — partner list (`@SQLRestriction` handles deleted filter)
- `findByClientId(UUID clientId, Pageable pageable)` → `Page<RedemptionReturn>` — admin list all (`@SQLRestriction` handles deleted filter)
- `existsByRedemptionIdAndClientIdAndStatusNotIn(UUID redemptionId, UUID clientId, List<ReturnStatus> excludedStatuses)` → `boolean` — duplicate active return check (exclude CANCELLED and RETURN_REJECTED to allow resubmission)
- `findByVendorReturnReference(String vendorReturnReference)` → `Optional<RedemptionReturn>` — webhook idempotency lookup
- `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT r FROM RedemptionReturn r WHERE r.id = :id") findByIdForUpdate(@Param("id") UUID id)` → `Optional<RedemptionReturn>` — concurrent state transition guard
- `@Query("SELECT r FROM RedemptionReturn r WHERE r.clientId = :clientId AND r.status = 'APPROVED' AND r.approvedAt < :cutoff") findApprovedTimedOut(@Param("clientId") UUID clientId, @Param("cutoff") Instant cutoff, Pageable pageable)` → `Page<RedemptionReturn>` — scheduler query (paginated to avoid bulk timeout; `@SQLRestriction` handles deleted filter)

---

## Package Layout [FE]

_All paths relative to `../tenxengage-frontend/src/`_

| Responsibility | File Path |
|---|---|
| TypeScript types | `types/redemption-returns.types.ts` (copy from contracts repo — do not hand-write) |
| API service | `services/redemption-returns.service.ts` |
| Partner list hook | `hooks/useMyReturns.ts` |
| Return detail hook | `hooks/useReturn.ts` |
| Admin list hook | `hooks/useAdminReturns.ts` |
| Submit mutation hook | `hooks/useSubmitReturn.ts` |
| Cancel mutation hook | `hooks/useCancelReturn.ts` |
| Approve mutation hook | `hooks/useApproveReturn.ts` |
| Reject mutation hook | `hooks/useRejectReturn.ts` |
| Resolve mutation hook | `hooks/useResolveTimedOutReturn.ts` |
| Submit dialog | `components/redemption-returns/RequestReturnDialog.tsx` |
| Partner returns tab | `components/redemption-returns/MyReturnsTab.tsx` |
| Return detail sheet | `components/redemption-returns/ReturnDetailSheet.tsx` |
| Reject dialog | `components/redemption-returns/RejectReturnDialog.tsx` |
| Resolve TIMED_OUT dialog | `components/redemption-returns/ResolveTimedOutDialog.tsx` |
| Admin approval tab | `components/redemption-returns/ReturnsApprovalTab.tsx` |
| Status badge | `components/redemption-returns/ReturnStatusBadge.tsx` |
| Component tests | `components/redemption-returns/__tests__/RequestReturnDialog.test.tsx` |
| Component tests | `components/redemption-returns/__tests__/ReturnsApprovalTab.test.tsx` |
| Component tests | `components/redemption-returns/__tests__/ReturnDetailSheet.test.tsx` |

**Route changes:** No new entries in `App.tsx` — returns are embedded in existing F-04 and F-05 page routes.

**F-05 integration point:** add `MyReturnsTab` as a new tab in the existing redemption history page shell (alongside existing "My Redemptions" tab).

**F-04 integration point:** add `ReturnsApprovalTab` as a new tab in the existing admin approval queue page shell (alongside existing "Redemptions" tab).

---

## Hook Specs [FE]

### `useMyReturns` (partner list hook)

```ts
queryKey: ['my-returns', userId, { status, page, size, sort }]
staleTime: 2 * 60 * 1000   // 2 min — async webhook can change status
```

Invalidate on: `useSubmitReturn`, `useCancelReturn` mutations.

### `useReturn(id)` (detail hook)

```ts
queryKey: ['return', id]
staleTime: 2 * 60 * 1000
```

Invalidate on: `useCancelReturn`, `useApproveReturn`, `useRejectReturn`, `useResolveTimedOutReturn` mutations for this `id`.

### `useAdminReturns` (admin list hook)

```ts
queryKey: ['admin-returns', clientId, { status, startDate, endDate, page, size, sort }]
staleTime: 2 * 60 * 1000
```

Invalidate on: `useApproveReturn`, `useRejectReturn`, `useResolveTimedOutReturn` mutations.

---

## Audit Annotations [BE]

**New enum value — add to `entity/enums/AuditResourceType.java`:**
- `REDEMPTION_RETURN`

No new `AuditAction` values needed.

| Operation | `action` value | `resourceType` value | `description` |
|---|---|---|---|
| `POST /returns` (partner submit) | `SUBMITTED` | `REDEMPTION_RETURN` | `Partner submitted return request` |
| `POST /admin/returns/{id}/approve` | `APPROVED` | `REDEMPTION_RETURN` | `Approved return request` |
| `POST /admin/returns/{id}/reject` | `REJECTED` | `REDEMPTION_RETURN` | `Rejected return request` |
| `DELETE /returns/{id}` (partner cancel) | `CANCELLED` | `REDEMPTION_RETURN` | `Partner cancelled return request` |
| `POST /admin/returns/{id}/resolve` (CONFIRM) | `COMPLETED` | `REDEMPTION_RETURN` | `Manually confirmed timed-out return` |
| `POST /admin/returns/{id}/resolve` (REJECT) | `REJECTED` | `REDEMPTION_RETURN` | `Manually rejected timed-out return` |
| Webhook confirm | `COMPLETED` | `REDEMPTION_RETURN` | `Return confirmed by Xoxoday` |
| Webhook reject | `REJECTED` | `REDEMPTION_RETURN` | `Return rejected by Xoxoday` |
| Scheduler `RETURN_TIMED_OUT` | `EXPIRED` | `REDEMPTION_RETURN` | `Return approval window expired — no vendor response after 7 days` |
