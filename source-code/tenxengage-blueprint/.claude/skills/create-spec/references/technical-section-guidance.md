# Technical section authoring guidance

Reference loaded by step 13 (`generate-technical-content`). Rules for authoring `technical.md`. Read alongside `templates/technical-template.md`.

---

## Flyway Migrations [BE]

- One migration file per feature in `tenxengage-backend/src/main/resources/db/migration/V{N}__{descriptive_name}.sql`.
- Migration version `N` = (latest existing) + 1. Confirm via the glob captured in step 03.
- Naming: `V47__add_quiz_engine.sql` (snake_case description).
- Idempotency for permission seeds: `INSERT ... ON CONFLICT (permission_key) DO NOTHING`.
- UUID PKs via `DEFAULT gen_random_uuid()` — never auto-increment sequences.
- `TIMESTAMPTZ` for all date/time columns; `JSONB` for flexible/configurable fields.
- Always include `client_id UUID NOT NULL REFERENCES clients(id)` for new entities (see `docs/patterns/tenant-isolation.md` and `docs/patterns/new-entities.md`).
- Always include `deleted BOOLEAN NOT NULL DEFAULT false` and `version BIGINT NOT NULL DEFAULT 0` (BaseEntity fields).
- Always include `CREATE INDEX idx_{table}_client_id ON {table}(client_id)`.
- Adding a nullable column: safe (no table lock). Adding a NOT NULL column with a DEFAULT: safe in PostgreSQL 11+.
- Dropping or renaming: two-step migration (add new → backfill → drop old in next release).

---

## Permission Seed SQL [BE]

One block per role that has at least one `Y` in the Permission Matrix:

```sql
INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = '{ROLE_NAME}'
  AND p.permission_key IN (
    'module.{feature}',
    'action.{entity}.view',
    'action.{entity}.create'
    -- list exactly the Y-cell permission keys from spec.md's matrix for this role
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;
```

The `IN (...)` list MUST match exactly the `Y` cells for each role in `spec.md`'s Permission Matrix. No extras, no omissions.

---

## BE Package Layout [BE]

Table mapping responsibility to exact file path (relative to `tenxengage-backend/src/main/java/com/tenxengage/app/`):

| Responsibility | File Path |
|---|---|
| Entity | `entity/{Entity}.java` (extends BaseEntity, implements TenantAware) |
| Enum | `entity/enums/{EnumName}.java` |
| Repository | `repository/{Entity}Repository.java` |
| Service | `service/{Entity}Service.java` |
| Controller | `controller/{Entity}Controller.java` |
| Create Request DTO | `dto/request/Create{Entity}Request.java` |
| Update Request DTO | `dto/request/Update{Entity}Request.java` |
| Response DTO (list) | `dto/response/{Entity}Response.java` |
| Response DTO (detail) | `dto/response/{Entity}DetailResponse.java` (omit if same as list) |
| Service Test | `service/{Entity}ServiceTest.java` (under test/) |
| Controller Test | `controller/{Entity}ControllerTest.java` (under test/) |
| Fixtures | `testdata/{Entity}Fixtures.java` (builder-return pattern; mandatory) |

Repeat per entity in the feature. Drop rows that don't apply (e.g., no UpdateRequest if update is out of scope).

---

## Repository Queries [BE]

List every JPA query method signature for each entity. Group by repository (one sub-section per entity).

Rules:
- **EVERY method MUST include `clientId` as a parameter.** No exceptions. Queries without tenant filter are forbidden.
- Use Spring Data JPA method naming conventions or `@Query` JPQL.
- For `@Query` search: `LOWER(e.{field}) LIKE :q` with parameterized wildcard — never string concatenation.

Standard methods to include:
- `findByClientId(UUID clientId, Pageable pageable)` — paginated list
- `findByIdAndClientId(UUID id, UUID clientId)` — single fetch with tenant check; returns `Optional`
- Feature-specific: `findBy{Field}AndClientId(...)`, `@EntityGraph` for associations, etc.

---

## FE Package Layout [FE]

Table mapping responsibility to exact file path (relative to `tenxengage-frontend/src/`):

| Responsibility | File Path |
|---|---|
| TypeScript types | `types/{feature}.types.ts` (copy from contracts repo; do NOT hand-write) |
| API service | `services/{feature}.service.ts` |
| List hook | `hooks/use{Entities}.ts` |
| Detail hook | `hooks/use{Entity}.ts` (omit if same shape as list) |
| List component | `components/{feature}/{Component}.tsx` |
| Form component | `components/{feature}/{Entity}Form.tsx` |
| Page component | `pages/{feature}/{Page}Page.tsx` |
| Component test | `components/{feature}/__tests__/{Component}.test.tsx` |
| Route (in App.tsx) | `<Route path="/{feature}" element={<{Page}Page />} />` |

---

## Hook Specs [FE]

One block per TanStack Query hook. Format:

```
### use{Entities} (list hook)
queryKey: ['{feature}', { search, page, size, sort }]
staleTime: 5 * 60 * 1000   // 5 min
Invalidate on: create{Entity}, update{Entity}, delete{Entity} mutations

### use{Entity} (detail hook)
queryKey: ['{feature}', id]
staleTime: 5 * 60 * 1000
Invalidate on: update{Entity} mutation for this id
```

Omit detail hook if no separate detail endpoint exists. Omit this entire section if the feature has no FE layer.

---

## Audit Annotations [BE]

Table for non-CRUD `@Audited` operations (standard CRUD can be inferred at implementation time):

| Operation | `action` value | `resourceType` value | `description` |
|---|---|---|---|
| Publish course | `PUBLISHED` | `COURSE` | "Course published" |
| Approve submission | `APPROVED` | `SUBMISSION` | "Submission approved" |

New AuditAction / AuditResourceType enum values listed here for Java file update. No Flyway migration needed — stored as varchar.

---

## Authorship rules

- **NO design rationale.** The "why" lives in `spec.md`.
- **NO business rules.** Spec.md territory.
- Concrete file paths only. Never "appropriate package" or "somewhere in services".
- Cross-reference spec.md: every endpoint in spec.md → controller method here; every entity → Flyway table; every permission key → seed SQL row.
- Every repository method includes `clientId` in every signature. This is non-negotiable.