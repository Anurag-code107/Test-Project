# Foundation Tasks: redemption-flow

_Horizontal bedrock that all stories depend on. Execute **sequentially** — each task depends on the previous. Session granularity: one session per task._

> **Step 0 — Generate contracts first (before any foundation task):**
> ```
> cd ../tenxengage-contracts && /generate-contracts redemption-flow
> ```
> This enables FE story sessions to start immediately in parallel with BE foundation work.

---

## Task Summary

| # | Task | Layer | Deps | Parallel With | Size | Done When |
|---|---|---|---|---|---|---|
| F1 | Enums | BE | None | — | S | All enum classes compile; `AuditAction` + `AuditResourceType` include new values |
| F2 | Flyway migrations (schema) | BE | F1 | — | M | `./gradlew flywayMigrate` applies; all tables exist with correct columns and indexes |
| F3 | Base entities + repositories + fixtures | BE | F2 | — | M | Entity classes compile; all repo queries include `clientId`; `./gradlew test` passes |
| F4 | Permissions seed | BE | F2 | — | S | Seed migration applies; `action.redemption.redeem` and `action.redemption.redeem_company` rows exist in DB |
| F5 | BE plumbing — Kafka | BE | F3, F4 | — | S | `redemption-events` topic constant in `KafkaConfig`; `RedemptionEventProducer` compiles; `./gradlew compileJava` passes |

---

## Task F1: Enums [BE] — Size: S

_Dependencies: None_
_Parallel with: None_
_Done when: All enum classes compile; `AuditAction` and `AuditResourceType` include the new values_

**New enum files:**

- `src/main/java/com/tenxengage/app/entity/enums/RedemptionStatus.java`
  ```
  PENDING_APPROVAL, RESERVED, PROCESSING, COMPLETED, FAILED, CANCELLED
  ```

- `src/main/java/com/tenxengage/app/entity/enums/WebhookStatus.java`
  ```
  RECEIVED, PROCESSED, DUPLICATE, FAILED, DEAD_LETTERED
  ```

**Additions to existing enum files:**

- `src/main/java/com/tenxengage/app/entity/enums/AuditAction.java` — add:
  ```
  COMPLETED, FAILED, CANCELLED
  ```

- `src/main/java/com/tenxengage/app/entity/enums/AuditResourceType.java` — add:
  ```
  REDEMPTION_REQUEST, REDEMPTION_WEBHOOK_EVENT
  ```

Refer to `spec.md → ## New Enums [BE]` for full context. These are Java enums stored as `varchar` — no Flyway migration needed.

---

## Task F2: Flyway Migrations [BE] — Size: M

_Dependencies: F1_
_Parallel with: None_
_Done when: `./gradlew flywayMigrate` applies cleanly; `redemption_requests` and `redemption_webhook_events` tables exist; `max_in_flight_redemptions` column added to `tenant_redemption_settings`_

**File:**
- `src/main/resources/db/migration/V16__create_redemption_request_tables.sql`

**Migration content** (copy from `technical.md → ## Flyway Migrations [BE] → V16`):

```sql
-- 1. Add max_in_flight_redemptions to existing tenant_redemption_settings
ALTER TABLE tenant_redemption_settings
    ADD COLUMN IF NOT EXISTS max_in_flight_redemptions INTEGER NOT NULL DEFAULT 10;

-- 2. redemption_requests table
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

-- 3. redemption_webhook_events table
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

Refer to `technical.md → ## Flyway Migrations [BE] → V16__create_redemption_request_tables.sql` for the authoritative SQL.

---

## Task F3: Base Entities + Repositories + Fixtures [BE] — Size: M

_Dependencies: F2_
_Parallel with: None_
_Done when: Entity classes compile; all repository queries scoped to `clientId`; `./gradlew test` passes including fixture usage_

**Files:**

### Entities
- `src/main/java/com/tenxengage/app/entity/RedemptionRequest.java`
  - extends `BaseEntity`, implements `TenantAware`, carries `@Filter`
  - Fields: `clientId`, `walletId`, `userId`, `catalogItemId`, `amount`, `currencyId`, `walletType`, `status` (RedemptionStatus), `processingMode`, `category`, `vendorReferenceId`, `reserveLedgerEntryId`, `debitLedgerEntryId`, `releaseLedgerEntryId`, `scheduledBatchDate`, `submittedAt`, `processingStartedAt`, `completedAt`, `failureReason`, `version`, `deleted`
  - Refer to `spec.md → ## Data Model / Entities [BE] → RedemptionRequest`

- `src/main/java/com/tenxengage/app/entity/RedemptionWebhookEvent.java`
  - extends `BaseEntity`, implements `TenantAware`, carries `@Filter`
  - Fields: `clientId`, `vendor`, `redemptionRequestId`, `idempotencyKey`, `payload` (JSONB), `status` (WebhookStatus), `receivedAt`, `processedAt`, `failureReason`, `version`, `deleted`
  - Refer to `spec.md → ## Data Model / Entities [BE] → RedemptionWebhookEvent`

### Repositories
- `src/main/java/com/tenxengage/app/repository/RedemptionRequestRepository.java`
  - `findByIdAndClientId(UUID id, UUID clientId)` → `Optional<RedemptionRequest>`
  - `findByClientIdAndUserIdAndDeletedFalse(UUID clientId, UUID userId, Pageable pageable)` → `Page<RedemptionRequest>`
  - `findByClientIdAndDeletedFalse(UUID clientId, Pageable pageable)` → `Page<RedemptionRequest>`
  - `countByClientIdAndUserIdAndStatusIn(UUID clientId, UUID userId, List<RedemptionStatus> statuses)` → `long`
  - `findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(UUID clientId, RedemptionStatus status, RedemptionProcessingMode mode, LocalDate batchDate)` → `List<RedemptionRequest>`

- `src/main/java/com/tenxengage/app/repository/RedemptionWebhookEventRepository.java`
  - `findByIdempotencyKey(String idempotencyKey)` → `Optional<RedemptionWebhookEvent>`
  - `findByRedemptionRequestIdAndClientId(UUID redemptionRequestId, UUID clientId)` → `List<RedemptionWebhookEvent>`
  - `findByClientIdAndStatusAndDeletedFalse(UUID clientId, WebhookStatus status, Pageable pageable)` → `Page<RedemptionWebhookEvent>`

Refer to `technical.md → ## Repository Queries [BE]` for all query signatures.

### Fixtures (mandatory — builder-return pattern)
- `src/test/java/com/tenxengage/app/testdata/RedemptionRequestFixtures.java`
  - Builder methods: `defaultPersonal()`, `defaultCompany()`, `withStatus(RedemptionStatus)`, `withProcessingMode(...)`, `withScheduledBatchDate(LocalDate)`, `inFlight()` (status=RESERVED)
  - Follow the builder-return pattern from `UserFixtures.java`

- `src/test/java/com/tenxengage/app/testdata/RedemptionWebhookEventFixtures.java`
  - Builder methods: `defaultXtrm()`, `defaultXoxoday()`, `withStatus(WebhookStatus)`, `withIdempotencyKey(String)`

---

## Task F4: Permissions + Feature Flags Seed [BE] — Size: S

_Dependencies: F2_
_Parallel with: None_
_Done when: Seed migration applies without error; `action.redemption.redeem` and `action.redemption.redeem_company` rows exist in `permissions` table; Acme client_permission_grants seeded_

**File:**
- `src/main/resources/db/migration/V17__seed_redemption_flow_permissions.sql`

**Migration content** (copy from `technical.md → ## Flyway Migrations [BE] → V17`):
- Inserts `action.redemption.redeem` (PARTNER_SELLER permission, sort_order=403)
- Inserts `action.redemption.redeem_company` (PARTNER_ADMIN permission, sort_order=404)
- Grants both to `PARTNER_SELLER` / `PARTNER_ADMIN` base roles via `client_role_permissions`
- Seeds Acme tenant (`a0000000-0000-0000-0000-000000000001`) via `client_permission_grants`
- All inserts use `ON CONFLICT DO NOTHING` for idempotency

Refer to `technical.md → ## Flyway Migrations [BE] → V17__seed_redemption_flow_permissions.sql` for the authoritative SQL.

> **Note:** No new feature flag — `redemption_store` was already seeded in V8 (F-01).

---

## Task F5: BE Plumbing — Kafka [BE] — Size: S

_Dependencies: F3, F4_
_Parallel with: None_
_Done when: `redemption-events` topic constant added to `KafkaConfig.java`; `RedemptionEventProducer.java` compiles; `./gradlew compileJava` passes_

**Files:**

- `src/main/java/com/tenxengage/app/config/KafkaConfig.java` — **ADD** topic constant:
  ```java
  public static final String REDEMPTION_EVENTS_TOPIC = "redemption-events";
  ```
  > **Critical:** Per project convention, all Kafka topics MUST be declared in `KafkaConfig.java`. Never create ad-hoc topic strings in service/producer classes.

- `src/main/java/com/tenxengage/app/service/RedemptionEventProducer.java`
  - Uses `KafkaTemplate<String, Object>` injected via constructor
  - Method: `publishRedemptionRequested(RedemptionRequest request)` — publishes to `KafkaConfig.REDEMPTION_EVENTS_TOPIC`
  - Method: `publishRedemptionCompleted(RedemptionRequest request)` — publishes to same topic (used by US-07 when unblocked)
  - Method: `publishRedemptionFailed(RedemptionRequest request)` — publishes to same topic (used by US-07 when unblocked)
  - Event payload shape: refer to `spec.md → ## Domain Events [BE]`

Refer to `spec.md → ## Domain Events [BE]` for topic name, event payload fields, and the Kafka prerequisite note.
