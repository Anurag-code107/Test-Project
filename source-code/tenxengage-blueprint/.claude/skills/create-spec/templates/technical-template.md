> **Feature**: [spec.md](spec.md)
> **Purpose**: Implementer reference — Flyway SQL, file paths, query shapes, hook specs.
> **Decisions and intent live in `spec.md`.** Read `spec.md` first, then use this file during implementation.

---

## Flyway Migrations [BE]

_Path: `src/main/resources/db/migration/`_

### V{{N}}__create_{{entity}}_table.sql

```sql
CREATE TABLE {{table_name}} (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id     UUID          NOT NULL REFERENCES clients(id),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted       BOOLEAN       NOT NULL DEFAULT false,
    version       BIGINT        NOT NULL DEFAULT 0,
    {{field}}     {{TYPE}}      NOT NULL,
    {{field2}}    {{TYPE}}      NULL
);

CREATE INDEX idx_{{table}}_client_id ON {{table_name}}(client_id);
CREATE INDEX idx_{{table}}_client_status ON {{table_name}}(client_id, {{status_field}});
-- Add UNIQUE constraints for uniqueness business rules (e.g., email per tenant)
-- CREATE UNIQUE INDEX uq_{{table}}_client_{{field}} ON {{table_name}}(client_id, {{field}});
```

_Repeat one `### V{{N}}__...` block per migration file. Junction tables and child-entity tables each get their own block._

### V{{N+1}}__seed_{{feature}}_permissions.sql

```sql
-- ============================================================
-- {{Feature}}: Permission catalog
-- ============================================================
INSERT INTO permissions (id, permission_key, display_name, description, category, permission_type, sort_order, created_at, updated_at, scope)
VALUES
  (gen_random_uuid(), 'module.{{feature}}',          '{{Feature}} Access',   '{{description}}', 'MODULE_ACCESS',      'MODULE', {{sort}}, NOW(), NOW(), '{{scope}}'),
  (gen_random_uuid(), 'action.{{entity}}.view',      'View {{Entities}}',    '{{description}}', '{{DOMAIN}}_ACTIONS', 'ACTION', {{sort}}, NOW(), NOW(), '{{scope}}'),
  (gen_random_uuid(), 'action.{{entity}}.create',    'Create {{Entities}}',  '{{description}}', '{{DOMAIN}}_ACTIONS', 'ACTION', {{sort}}, NOW(), NOW(), '{{scope}}'),
  (gen_random_uuid(), 'action.{{entity}}.edit',      'Edit {{Entities}}',    '{{description}}', '{{DOMAIN}}_ACTIONS', 'ACTION', {{sort}}, NOW(), NOW(), '{{scope}}'),
  (gen_random_uuid(), 'action.{{entity}}.delete',    'Delete {{Entities}}',  '{{description}}', '{{DOMAIN}}_ACTIONS', 'ACTION', {{sort}}, NOW(), NOW(), '{{scope}}')
ON CONFLICT (permission_key) DO NOTHING;

-- ============================================================
-- {{Feature}}: Feature flag
-- ============================================================
INSERT INTO feature_flags (id, feature_key, description, starter_enabled, professional_enabled, enterprise_enabled, created_at, updated_at, category)
VALUES (gen_random_uuid(), '{{feature_key}}', '{{description}}', false, {{professional}}, true, NOW(), NOW(), '{{category}}')
ON CONFLICT (feature_key) DO NOTHING;

-- ============================================================
-- {{Feature}}: Role grants (per the Permission Matrix in spec.md)
-- ============================================================
-- One block per role that has at least one Y in the Permission Matrix.
-- The IN (...) list must match exactly the Y cells for that role.

INSERT INTO client_role_permissions (id, client_role_id, permission_key, granted, created_at, updated_at)
SELECT gen_random_uuid(), cr.id, p.permission_key, true, NOW(), NOW()
FROM client_roles cr
CROSS JOIN permissions p
WHERE cr.base_role_name = '{{ROLE_NAME}}'
  AND p.permission_key IN (
    'module.{{feature}}',
    'action.{{entity}}.view',
    'action.{{entity}}.create',
    'action.{{entity}}.edit',
    'action.{{entity}}.delete'
  )
ON CONFLICT (client_role_id, permission_key) DO NOTHING;
```

_`ON CONFLICT DO NOTHING` on all INSERTs for idempotency. The Permission Matrix in `spec.md` is the authoritative source — keep the `IN (...)` lists in sync with Y cells._

**Migration safety reminders:**
- UUID PKs via `DEFAULT gen_random_uuid()`
- `TIMESTAMPTZ` for all date/time columns; `JSONB` for flexible/configurable fields
- Adding a nullable column: safe (no table lock). Adding a NOT NULL column with a DEFAULT: safe in PostgreSQL 11+
- Dropping or renaming a column: two-step migration (add new → backfill → drop old in next release)

---

## Package Layout [BE]

_All paths relative to `../tenxengage-backend/`. Repeat entity subtrees for each entity in the feature. Drop files that don't apply (e.g., no `Update{{Entity}}Request.java` if update is out of scope)._

```
src/
├── main/
│   ├── java/com/tenxengage/app/
│   │   ├── entity/
│   │   │   ├── {{Entity}}.java                              (extends BaseEntity, implements TenantAware)
│   │   │   └── enums/
│   │   │       └── {{EnumName}}.java
│   │   ├── repository/
│   │   │   └── {{Entity}}Repository.java
│   │   ├── service/
│   │   │   └── {{Entity}}Service.java
│   │   ├── controller/
│   │   │   └── {{Entity}}Controller.java
│   │   └── dto/
│   │       ├── request/
│   │       │   ├── Create{{Entity}}Request.java
│   │       │   └── Update{{Entity}}Request.java
│   │       └── response/
│   │           ├── {{Entity}}Response.java                  (list shape)
│   │           └── {{Entity}}DetailResponse.java            (detail shape — omit if same as list)
│   └── resources/
│       └── db/migration/
│           ├── V{{N}}__create_{{table}}_table.sql
│           └── V{{N+1}}__seed_{{feature}}_permissions.sql
└── test/
    └── java/com/tenxengage/app/
        ├── service/
        │   └── {{Entity}}ServiceTest.java
        ├── controller/
        │   └── {{Entity}}ControllerTest.java
        └── testdata/
            └── {{Entity}}Fixtures.java                      (mandatory — builder-return pattern)
```

---

## Repository Queries [BE]

_Queries `{{Entity}}Repository` must implement. All include `clientId` for tenant isolation — never query without the tenant filter._

- `findByClientId(clientId, pageable)` — paginated list (all active records for tenant)
- `findByIdAndClientId(id, clientId)` — single fetch with tenant check; returns `Optional`
- `existsByClientIdAndEmail(clientId, email)` — duplicate check (create path)
- `existsByClientIdAndEmailAndIdNot(clientId, email, excludingId)` — duplicate check (update path; prevents self-collision)
- `searchByClientId(clientId, q, pageable)` — `@Query` JPQL: `LOWER(e.{{field}}) LIKE :q` with parameterized wildcard

_Omit queries that don't apply. Add feature-specific queries (e.g., `findByParentIdAndClientId`, `@EntityGraph` for associations)._

---

## Package Layout [FE]

_All paths relative to `../tenxengage-frontend/src/`. Repeat component subtrees for each independent component (e.g., list table + detail sheet + confirm dialog)._

```
src/
├── types/
│   └── {{feature}}.types.ts                               (copy from ../tenxengage-contracts/ — do not hand-write)
├── services/
│   └── {{feature}}.service.ts
├── hooks/
│   └── use{{Entity}}.ts
├── components/
│   └── {{feature}}/
│       ├── {{Component}}.tsx
│       └── __tests__/
│           └── {{Component}}.test.tsx
└── pages/
    └── {{feature}}/
        └── {{Page}}Page.tsx
```

Route entry — add to `App.tsx`:
```tsx
<Route path="/{{feature}}" element={<{{Page}}Page />} />
```

---

## Hook Specs [FE]

### `use{{Entities}}` (list hook)

```ts
queryKey: ['{{feature}}', { search, page, size, sort }]
staleTime: 5 * 60 * 1000   // 5 min
```

Invalidate on: `create{{Entity}}`, `update{{Entity}}`, `delete{{Entity}}` mutations.

### `use{{Entity}}` (detail hook — omit if list and detail use the same response shape)

```ts
queryKey: ['{{feature}}', id]
staleTime: 5 * 60 * 1000
```

Invalidate on: `update{{Entity}}` mutation for this `id`.

_Omit this entire section if the feature has no FE layer. Omit the detail hook if no separate detail endpoint exists._
