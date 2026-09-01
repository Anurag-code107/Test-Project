> **Feature**: [spec.md](spec.md)
> **Purpose**: Implementer reference — Flyway SQL, file paths, query shapes, hook specs.
> **Decisions and intent live in `spec.md`.** Read `spec.md` first, then use this file during implementation.

---

## Flyway Migrations [BE]

_Path: `src/main/resources/db/migration/`_

### V16__create_redemption_request_tables.sql

```sql
-- ============================================================
-- F-03 Redemption Flow: Schema
-- ============================================================

-- 1. Add max_in_flight_redemptions to existing tenant_redemption_settings
ALTER TABLE tenant_redemption_settings
    ADD COLUMN IF NOT EXISTS max_in_flight_redemptions INTEGER NOT NULL DEFAULT 10;

-- 2. Create redemption_requests table
CREATE TABLE redemption_requests (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                UUID          NOT NULL REFERENCES clients(id),
    wallet_id                UUID          NOT NULL REFERENCES reward_wallets(id),
    user_id                  UUID          NOT NULL REFERENCES users(id),
    catalog_item_id          UUID          NOT NULL REFERENCES redemption_catalog_items(id),
    amount                   NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency_id              VARCHAR(50)   NOT NULL,
    wallet_type              VARCHAR(20)   NOT NULL,
    status                   VARCHAR(30)   NOT NULL,
    processing_mode          VARCHAR(30)   NOT NULL,
    category                 VARCHAR(20)   NOT NULL,
    vendor_reference_id      VARCHAR(255),
    reserve_ledger_entry_id  UUID          REFERENCES ledger_entries(id),
    debit_ledger_entry_id    UUID          REFERENCES ledger_entries(id),
    release_ledger_entry_id  UUID          REFERENCES ledger_entries(id),
    scheduled_batch_date     DATE,
    submitted_at             TIMESTAMPTZ   NOT NULL,
    processing_started_at    TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    failure_reason           VARCHAR(500),
    version                  BIGINT        NOT NULL DEFAULT 0,
    deleted                  BOOLEAN       NOT NULL DEFAULT false,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_redemption_requests_client_id     ON redemption_requests(client_id);
CREATE INDEX idx_redemption_requests_client_status ON redemption_requests(client_id, status);
CREATE INDEX idx_redemption_requests_user_id       ON redemption_requests(client_id, user_id);
CREATE INDEX idx_redemption_requests_wallet_id     ON redemption_requests(wallet_id);

-- 3. Create redemption_webhook_events table
CREATE TABLE redemption_webhook_events (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id             UUID          NOT NULL REFERENCES clients(id),
    vendor                VARCHAR(20)   NOT NULL,
    redemption_request_id UUID          NOT NULL REFERENCES redemption_requests(id),
    idempotency_key       VARCHAR(255)  NOT NULL,
    payload               JSONB         NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    received_at           TIMESTAMPTZ   NOT NULL,
    processed_at          TIMESTAMPTZ,
    failure_reason        VARCHAR(1000),
    version               BIGINT        NOT NULL DEFAULT 0,
    deleted               BOOLEAN       NOT NULL DEFAULT false,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_webhook_events_idempotency_key        ON redemption_webhook_events(idempotency_key);
CREATE INDEX        idx_webhook_events_client_id             ON redemption_webhook_events(client_id);
CREATE INDEX        idx_webhook_events_redemption_request_id ON redemption_webhook_events(redemption_request_id);
CREATE INDEX        idx_webhook_events_status                ON redemption_webhook_events(status);
```

### V17__seed_redemption_flow_permissions.sql

```sql
-- ============================================================
-- F-03 Redemption Flow: New action permissions
-- Note: module.redemption_store, action.redemption.view_history,
--       action.redemption.view_all_history seeded in F-01 V8.
--       action.redemption.configure, action.redemption.catalog.manage seeded in F-02 V12.
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(),
   'action.redemption.redeem',
   'Redeem from Personal Wallet',
   'Initiate a redemption request from the partner''s personal reward wallet',
   'REDEMPTION_ACTIONS', 'ACTION', 403, NOW(), NOW(), 'EXTERNAL'),
  (gen_random_uuid(),
   'action.redemption.redeem_company',
   'Redeem from Company Wallet',
   'Initiate a redemption request from the partner company reward wallet',
   'REDEMPTION_ACTIONS', 'ACTION', 404, NOW(), NOW(), 'EXTERNAL')
ON CONFLICT (permission_key) DO NOTHING;

-- No new feature_flag — redemption_store already seeded in V8.

-- ============================================================
-- PARTNER_SELLER → action.redemption.redeem
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_SELLER'
  AND p.permission_key IN (
    'action.redemption.redeem'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- PARTNER_ADMIN → action.redemption.redeem_company
-- ============================================================
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = 'PARTNER_ADMIN'
  AND p.permission_key IN (
    'action.redemption.redeem_company'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;

-- ============================================================
-- Acme tenant seed grants (dev/seed only)
-- ============================================================
INSERT INTO client_permission_grants (id, client_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001', p.permission_key, true, NOW(), NOW()
FROM permissions p
WHERE p.permission_key IN (
    'action.redemption.redeem',
    'action.redemption.redeem_company'
)
ON CONFLICT (client_id, permission_key) DO NOTHING;
```

---

## Package Layout [BE]

_All paths relative to `../tenxengage-backend/`._

```
src/
├── main/
│   ├── java/com/tenxengage/app/
│   │   ├── entity/
│   │   │   ├── RedemptionRequest.java                         (extends BaseEntity, implements TenantAware)
│   │   │   ├── RedemptionWebhookEvent.java                    (extends BaseEntity, implements TenantAware)
│   │   │   └── enums/
│   │   │       ├── RedemptionStatus.java                      (PENDING_APPROVAL, RESERVED, PROCESSING, COMPLETED, FAILED, CANCELLED)
│   │   │       ├── WebhookStatus.java                         (RECEIVED, PROCESSED, DUPLICATE, FAILED, DEAD_LETTERED)
│   │   │       ├── AuditAction.java                           (ADD: COMPLETED, FAILED, CANCELLED)
│   │   │       └── AuditResourceType.java                     (ADD: REDEMPTION_REQUEST, REDEMPTION_WEBHOOK_EVENT)
│   │   ├── repository/
│   │   │   ├── RedemptionRequestRepository.java
│   │   │   └── RedemptionWebhookEventRepository.java
│   │   ├── service/
│   │   │   ├── RedemptionSubmissionService.java
│   │   │   ├── RedemptionOrchestrationService.java
│   │   │   ├── RedemptionWebhookService.java
│   │   │   └── BatchRedemptionProcessor.java
│   │   ├── controller/
│   │   │   ├── RedemptionRequestController.java               (auth-gated; tag: Redemption Flow)
│   │   │   └── RedemptionWebhookController.java               (HMAC-gated, no JWT; tag: Redemption Webhooks)
│   │   └── dto/
│   │       ├── request/
│   │       │   ├── SubmitPersonalRedemptionRequest.java
│   │       │   └── SubmitCompanyRedemptionRequest.java
│   │       └── response/
│   │           ├── RedemptionRequestResponse.java             (list shape)
│   │           ├── RedemptionRequestDetailResponse.java       (detail shape)
│   │           └── RedemptionSubmissionConfirmationResponse.java
│   └── resources/
│       └── db/migration/
│           ├── V16__create_redemption_request_tables.sql
│           └── V17__seed_redemption_flow_permissions.sql
└── test/
    └── java/com/tenxengage/app/
        ├── service/
        │   ├── RedemptionSubmissionServiceTest.java
        │   ├── RedemptionOrchestrationServiceTest.java
        │   └── RedemptionWebhookServiceTest.java
        ├── controller/
        │   ├── RedemptionRequestControllerTest.java
        │   └── RedemptionWebhookControllerTest.java
        └── testdata/
            ├── RedemptionRequestFixtures.java                 (builder-return pattern; mandatory)
            └── RedemptionWebhookEventFixtures.java            (builder-return pattern; mandatory)
```

---

## Repository Queries [BE]

### RedemptionRequestRepository

- `findByIdAndClientId(UUID id, UUID clientId)` — single fetch with tenant check; returns `Optional<RedemptionRequest>`
- `findByClientIdAndUserIdAndDeletedFalse(UUID clientId, UUID userId, Pageable pageable)` — partner's personal history (paginated)
- `findByClientIdAndDeletedFalse(UUID clientId, Pageable pageable)` — all-tenant list (CLIENT_ADMIN, used in F-05)
- `countByClientIdAndUserIdAndStatusIn(UUID clientId, UUID userId, List<RedemptionStatus> statuses)` — in-flight count check
- `findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(UUID clientId, RedemptionStatus status, RedemptionProcessingMode mode, LocalDate batchDate)` — batch processor query (returns `List<RedemptionRequest>`)

### RedemptionWebhookEventRepository

- `findByIdempotencyKey(String idempotencyKey)` — idempotency check; returns `Optional<RedemptionWebhookEvent>`
- `findByRedemptionRequestIdAndClientId(UUID redemptionRequestId, UUID clientId)` — webhook history for a redemption
- `findByClientIdAndStatusAndDeletedFalse(UUID clientId, WebhookStatus status, Pageable pageable)` — DLQ admin view (future F-07)

---

## Package Layout [FE]

_All paths relative to `../tenxengage-frontend/src/`._

```
src/
├── types/
│   └── redemption-flow.types.ts                    (copy from ../tenxengage-contracts/ — do not hand-write)
├── services/
│   └── redemption-flow.service.ts
├── hooks/
│   ├── useRedemptionRequests.ts                     (list hook)
│   ├── useRedemptionRequest.ts                      (detail hook)
│   └── useRedemptionSubmit.ts                       (mutation hook)
├── components/
│   └── redemption-flow/
│       ├── RedemptionSubmitModal.tsx
│       ├── RedemptionConfirmationCard.tsx
│       ├── InFlightLimitBanner.tsx
│       └── __tests__/
│           ├── RedemptionSubmitModal.test.tsx
│           └── RedemptionConfirmationCard.test.tsx
└── pages/
    └── redemption-flow/
        └── RedemptionConfirmationPage.tsx
```

Route entry — add to `App.tsx`:
```tsx
<Route path="/redemption/confirmation/:id" element={<RedemptionConfirmationPage />} />
```

The submit modal is invoked from the existing catalog item detail route, not a new route.

---

## Hook Specs [FE]

### `useRedemptionRequests` (list hook)

```ts
queryKey: ['redemption-requests', { userId, status, currencyId, page, pageSize }]
staleTime: 60 * 1000   // 1 min — recent status changes matter
```

Invalidate on: `submitPersonalRedemption`, `submitCompanyRedemption` mutations.

### `useRedemptionRequest` (detail hook)

```ts
queryKey: ['redemption-request', id]
staleTime: 30 * 1000   // 30s — status changes frequently during processing
```

Invalidate on: `submitPersonalRedemption`, `submitCompanyRedemption` mutations for matching id.

### `useRedemptionSubmit` (mutation)

```ts
// Calls POST /api/v1/redemption/requests or /company
// On success: invalidate ['redemption-requests'] + ['wallet-balance']
// On 409: show "Maximum in-flight redemptions reached" toast
// On 400: surface field-level validation errors inline in modal
```

---

## Audit Annotations [BE]

New `AuditAction` values — add to `src/main/java/com/tenxengage/app/entity/enums/AuditAction.java`:
- `COMPLETED`
- `FAILED`
- `CANCELLED`

New `AuditResourceType` values — add to `src/main/java/com/tenxengage/app/entity/enums/AuditResourceType.java`:
- `REDEMPTION_REQUEST`
- `REDEMPTION_WEBHOOK_EVENT`

Non-CRUD `@Audited` annotations:

| Operation | `action` value | `resourceType` value | `description` |
|---|---|---|---|
| `POST /redemption/requests` | `SUBMITTED` | `REDEMPTION_REQUEST` | `Partner submitted personal wallet redemption` |
| `POST /redemption/requests/company` | `SUBMITTED` | `REDEMPTION_REQUEST` | `Partner Admin submitted company wallet redemption` |
| Webhook XTRM completion handler | `COMPLETED` | `REDEMPTION_REQUEST` | `XTRM confirmed fulfillment` |
| Webhook XTRM failure handler | `FAILED` | `REDEMPTION_REQUEST` | `XTRM reported failure` |
| Webhook Xoxoday completion handler | `COMPLETED` | `REDEMPTION_REQUEST` | `Xoxoday confirmed fulfillment` |
| Webhook Xoxoday failure handler | `FAILED` | `REDEMPTION_REQUEST` | `Xoxoday reported failure` |

> **Kafka prerequisite:** `redemption-events` topic constant must be added to `com.tenxengage.app.config.KafkaConfig.java` before the producer is implemented.
