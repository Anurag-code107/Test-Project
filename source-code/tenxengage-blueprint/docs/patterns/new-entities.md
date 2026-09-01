# Pattern: new-entities

## When this applies

Feature introduces one or more new database entities (new `@Entity` classes + corresponding Flyway migration).

## Spec authoring guidance

- **Every entity inherits from `BaseEntity`.** Spec must show entities as `extends BaseEntity`. Do NOT redefine `id`, `createdAt`, or `updatedAt` columns — they're inherited. `BaseEntity` provides `UUID id` (UUID-generated), `Instant createdAt`, and `Instant updatedAt` via JPA auditing, plus `TenantEntityListener` for automatic tenant wiring.
- **`client_id` is mandatory and NOT NULL.** Every new entity has a `client_id BIGINT NOT NULL` FK to `clients(id)`. Tenant isolation is non-negotiable. Spec must include this column explicitly.
- **Indexes:** spec must specify required indexes:
  - `(client_id)` always
  - `(client_id, status)` if entity has status
  - `(client_id, foreign_key_id)` for any FK used in lookups
  - `(client_id, created_at DESC)` for paginated time-ordered list views
- **Audit triggers:** every entity has an audit row written on CREATE, UPDATE (per field), DELETE. Spec the `@Audited` annotation policy: which fields trigger audit, what `AuditAction` and `AuditResourceType` values apply (check `enums-index.md`; new values are Java enum additions).
- **Soft delete vs hard delete:** every entity needs an explicit decision. Default for new entities: soft delete via a `deleted BOOLEAN NOT NULL DEFAULT FALSE` column if the entity has user-visible content. See `soft-delete.md` for the full implementation pattern including `@SQLRestriction`, bulk DML guards, and cascade rules.

## Implementation guidance

- Flyway migration in `tenxengage-backend/src/main/resources/db/migration/V{N}__{name}.sql`. Find latest N: `ls db/migration/V*.sql | sort -V | tail -1`.
- Migration includes: CREATE TABLE, all NOT NULL constraints, FK to `clients(id)`, all indexes from spec, and any seed data.
- Java entity in `com/tenxengage/app/entity/{Entity}.java`. Use Lombok `@Getter @Setter` consistent with `BaseEntity`'s own annotations (not `@Data` — `BaseEntity` uses `@Getter @Setter` separately).
- Repository in `com/tenxengage/app/repository/{Entity}Repository.java`. EVERY query method MUST include `clientId` — never expose a method that doesn't filter by client.
- Fixture in `src/test/java/com/tenxengage/app/testdata/{Entity}Fixtures.java` following the builder-return pattern of existing fixtures.

## Examples in codebase

- Base class: `tenxengage-backend/src/main/java/com/tenxengage/app/entity/BaseEntity.java`
- A simple existing entity: `ls tenxengage-backend/src/main/java/com/tenxengage/app/entity/*.java`
- Corresponding repository: `ls tenxengage-backend/src/main/java/com/tenxengage/app/repository/*.java`

## Common gotchas

- **Forgetting `client_id` on a query.** This is a tenant-isolation breach — Tenant A reading Tenant B's data.
- **Missing index on `client_id`.** Postgres won't use the FK index efficiently for `WHERE client_id = ?`; explicit index required.
- **Cascading deletes.** `ON DELETE CASCADE` from `clients` is dangerous in multi-tenant; prefer soft-delete + tenant-archival workflow.
- **Reusing existing AuditAction values without checking.** Always read `enums-index.md` first. Don't add a synonym if the value already exists.
- **Using `@Data` on the entity.** `BaseEntity` uses `@Getter @Setter` — match that pattern to avoid Lombok conflicts with the inherited fields.
