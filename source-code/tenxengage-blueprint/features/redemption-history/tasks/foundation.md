# Foundation Tasks: redemption-history

_Horizontal bedrock that all stories depend on. Execute **sequentially** — each task depends on the previous. Session granularity: one session per task._

> **Step 0 — Generate contracts first (before any foundation task):**
> ```
> cd ../tenxengage-contracts && /generate-contracts redemption-history
> ```
> This enables FE story sessions to start immediately in parallel with BE foundation work.

---

## Task Summary

| # | Task | Layer | Deps | Parallel With | Size | Done When |
|---|---|---|---|---|---|---|
| F1 | Enums | BE | None | — | S | All enum classes compile; `AuditResourceType` includes `REDEMPTION_EXPORT_JOB` |
| F2 | Flyway migrations | BE | F1 | — | M | `./gradlew flywayMigrate` applies; `redemption_export_jobs` table + 4 indexes exist |
| F3 | Base entities + repositories + fixtures | BE | F2 | F4 | M | Entity compiles; all repo queries include `clientId`; `./gradlew test` passes |
| F4 | Permissions seed | BE | F2 | F3 | S | V11 applies; `action.redemption.export` rows visible in DB for 3 roles |

_F5 = N/A — no Kafka events for this feature._

---

## Task F1: Enums [BE] — Size: S

_Dependencies: None_
_Parallel with: None_
_Done when: All enum classes compile; `AuditResourceType.java` includes `REDEMPTION_EXPORT_JOB`_

**Files:**

- `src/main/java/com/tenxengage/app/entity/enums/redemption/ExportFormat.java`
  - Values: `CSV`, `XLSX`
  - New file in new `redemption/` sub-package

- `src/main/java/com/tenxengage/app/entity/enums/redemption/RedemptionExportStatus.java`
  - Values: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`
  - New file

- `src/main/java/com/tenxengage/app/entity/enums/AuditResourceType.java`
  - Add value: `REDEMPTION_EXPORT_JOB`
  - No Flyway migration needed — stored as `varchar`

Refer to `spec.md → ## New Enums [BE]` for values and `spec.md → ## Audit Trail [BE]` for the new `AuditResourceType` value.

---

## Task F2: Flyway Migrations [BE] — Size: M

_Dependencies: F1_
_Parallel with: None_
_Done when: `./gradlew flywayMigrate` applies cleanly; `redemption_export_jobs` table exists with all columns and 4 indexes_

**Files:**

- `src/main/resources/db/migration/V10__create_redemption_export_jobs_table.sql`

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

Refer to `spec.md → ## Data Model / Entities [BE]` and `technical.md → ## Flyway Migrations [BE]`.

---

## Task F3: Base Entities + Repositories + Fixtures [BE] — Size: M

_Dependencies: F2_
_Parallel with: F4_
_Done when: Entity compiles with `TenantAware` + `@Filter`; all repo `@Query` methods include `clientId`; fixture builds a valid `RedemptionExportJob`; `./gradlew test` passes_

**Files:**

- `src/main/java/com/tenxengage/app/entity/redemption/RedemptionExportJob.java`
  - Extends `BaseEntity`, implements `TenantAware`
  - Carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`
  - Carries `@Version` on `version` field
  - `@ManyToOne` → `User` (FK: `requested_by`) — non-lazy

- `src/main/java/com/tenxengage/app/repository/redemption/RedemptionHistoryRepository.java`
  - All methods scope by `clientId`
  - JPQL queries: `findPersonalHistory`, `countPersonalHistory`, `findCompanyHistory`, `findTenantHistory`, `countTenantHistory`
  - See `technical.md → ## Repository Queries [BE]` for full `@Query` bodies

- `src/main/java/com/tenxengage/app/repository/redemption/RedemptionExportJobRepository.java`
  - `findByIdAndClientId(UUID id, UUID clientId)` → `Optional<RedemptionExportJob>`
  - `findByRequestedByAndClientId(UUID requestedBy, UUID clientId, Pageable pageable)` → `Page<RedemptionExportJob>`

- `src/test/java/com/tenxengage/app/testdata/RedemptionExportJobFixtures.java`
  - Builder-return pattern (follow `RedemptionRequestFixtures.java` in the same package)
  - Must build a valid `RedemptionExportJob` with all required fields

Refer to `spec.md → ## Data Model / Entities [BE]`, `spec.md → ## Service Layer [BE]`, and `technical.md → ## Repository Queries [BE]`.

**Fixture rule:** `RedemptionExportJobFixtures.java` is **required** — stories US-03 and US-04 depend on it for integration tests.

---

## Task F4: Permissions + Feature Flags Seed [BE] — Size: S

_Dependencies: F2_
_Parallel with: F3_
_Done when: V11 migration applies without error; `action.redemption.export` row in `permissions` table; 3 role grant rows in `client_role_permissions` (PARTNER_SELLER, PARTNER_ADMIN, CLIENT_ADMIN)_

**Files:**

- `src/main/resources/db/migration/V11__seed_redemption_history_permissions.sql`

Full SQL from `technical.md → ## Flyway Migrations [BE] → V11__seed_redemption_history_permissions.sql`. Uses `ON CONFLICT DO NOTHING` for idempotency.

Refer to `spec.md → ## Permissions & Feature Flags [BE + FE]` for the permission matrix. The `redemption_store` feature flag was seeded in V8 — no new flag row needed.
