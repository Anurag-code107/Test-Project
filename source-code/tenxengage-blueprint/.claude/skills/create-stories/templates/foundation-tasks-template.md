# Foundation Tasks: {{feature-slug}}

_Horizontal bedrock that all stories depend on. Execute **sequentially** — each task depends on the previous. Session granularity: one session per task._

> **Step 0 — Generate contracts first (before any foundation task):**
> ```
> cd ../tenxengage-contracts && /generate-contracts {{feature-slug}}
> ```
> This enables FE story sessions to start immediately in parallel with BE foundation work.

---

## Task Summary

| # | Task | Layer | Deps | Parallel With | Size | Done When |
|---|---|---|---|---|---|---|
| F1 | Enums | BE | None | — | S | All enum classes compile; AuditAction + AuditResourceType include new values |
| F2 | Flyway migrations | BE | F1 | — | M | `./gradlew flywayMigrate` applies cleanly; all tables exist with correct columns and indexes |
| F3 | Base entities + repositories | BE | F2 | — | M | Entity classes compile; all repo queries include `clientId`; `./gradlew test` passes |
| F4 | Permissions + feature flags seed | BE | F2 | — | S | Seed migration applies; permissions rows visible in DB; feature flag row exists |
| F5 | BE-only plumbing | BE | F3, F4 | — | {{S/M}} | _Skip if no Kafka events or other BE infrastructure_ |

---

## Task F1: Enums [BE] — Size: S

_Dependencies: None_
_Parallel with: None_
_Done when: All enum classes compile; `AuditAction` and `AuditResourceType` include the new values listed in `spec.md → ## New Enums [BE]`_

**Files:**
- `src/main/java/com/tenxengage/app/entity/enums/{{EnumName}}.java` — new domain enum
- `src/main/java/com/tenxengage/app/entity/enums/AuditAction.java` — add `{{NEW_VALUE}}` _(if new action values needed)_
- `src/main/java/com/tenxengage/app/entity/enums/AuditResourceType.java` — add `{{NEW_VALUE}}` _(if new resource types needed)_

Refer to `spec.md → ## New Enums [BE]` for values. These are Java enums stored as `varchar` — no Flyway migration needed, just add to the Java files.

---

## Task F2: Flyway Migrations [BE] — Size: M

_Dependencies: F1_
_Parallel with: None_
_Done when: `./gradlew flywayMigrate` applies cleanly; confirm all tables and indexes exist via DB inspection_

**Files:**
- `src/main/resources/db/migration/V{{N}}__create_{{table}}_table.sql`
- `src/main/resources/db/migration/V{{N+1}}__create_{{table2}}_table.sql` _(if multiple tables)_

Refer to `spec.md → ## Flyway Migrations [BE]` for SQL. Every table must include:
- `client_id UUID NOT NULL` for tenant isolation
- `deleted BOOLEAN NOT NULL DEFAULT false` for soft delete
- `version BIGINT NOT NULL DEFAULT 0` for optimistic locking
- Indexes on `client_id`, `(client_id, status)`, and any filtered/sorted columns

---

## Task F3: Base Entities + Repositories [BE] — Size: M

_Dependencies: F2_
_Parallel with: None_
_Done when: Entity classes compile; all repository queries include `clientId`; `./gradlew test` passes including new fixture usage_

**Files:**
- `src/main/java/com/tenxengage/app/entity/{{Entity}}.java` — extends `BaseEntity`, implements `TenantAware`, carries `@Filter`
- `src/main/java/com/tenxengage/app/repository/{{Entity}}Repository.java` — queries scoped to `clientId`
- `src/test/java/com/tenxengage/app/testdata/{{Entity}}Fixtures.java` — builder-return pattern (follow `UserFixtures.java`)

Refer to `spec.md → ## Data Model / Entities [BE]` and `## Repositories [BE]`.

**Fixture rule:** If this feature introduces new entities, fixtures are **required** — they must appear explicitly in this task, not assumed to be created implicitly.

---

## Task F4: Permissions + Feature Flags Seed [BE] — Size: S

_Dependencies: F2_
_Parallel with: None_
_Done when: Seed migration applies without error; all permission rows visible in DB; feature flag row exists with correct tier booleans_

**Files:**
- `src/main/resources/db/migration/V{{N}}__seed_{{feature}}_permissions.sql`

Refer to `spec.md → ## Permissions & Feature Flags [BE + FE] → Flyway Seed Migration` for the complete SQL. Use `ON CONFLICT DO NOTHING` for idempotency.

---

## Task F5: BE-only Plumbing [BE] — Size: {{S/M}}

_Dependencies: F3, F4_
_Parallel with: None_
_Done when: {{Consumers compile and handle test messages; producers publish to correct topics}}_

_**Skip this task** if there are no Kafka consumers/producers or other BE infrastructure (no user-visible story) for this feature. In that case, delete this task from the summary table above._

**Files:**
- `src/main/java/com/tenxengage/app/kafka/{{Feature}}EventConsumer.java`
- `src/main/java/com/tenxengage/app/kafka/{{Feature}}EventProducer.java`
- `src/test/java/com/tenxengage/app/kafka/{{Feature}}EventConsumerTest.java`

Refer to `spec.md → ## Domain Events [BE]` for topic names, consumer group IDs, message schemas, and idempotency approach.
