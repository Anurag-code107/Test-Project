# Step 14: generate-technical-content

## Goal
Generate the full content of `technical.md` — Flyway DDL, permission seed SQL, BE/FE file paths, repository queries, hook specs. Implementation artifacts only; NO business rules, NO design rationale.

## Inputs (from prior steps)
- spec.md content (from step 13)
- All findings (data model, permissions matrix, etc.)
- Latest migration number (from step 03 glob)

## Loads (just-in-time)
- `tenxengage-blueprint/.claude/skills/create-spec/templates/technical-template.md`
- `tenxengage-blueprint/.claude/skills/create-spec/references/technical-section-guidance.md`
- For Flyway DDL grounding: JIT-read 1 existing migration from `tenxengage-backend/src/main/resources/db/migration/V*.sql` (typically the most recent one, for naming + structure conventions)
- For entity inheritance grounding: JIT-read `tenxengage-backend/src/main/java/com/tenxengage/app/entity/BaseEntity.java`
- For repo query patterns: JIT-read 1 existing repository from `tenxengage-backend/src/main/java/com/tenxengage/app/repository/`

## Procedure

1. Read template and technical-section-guidance.

2. For each section in technical-template.md:
   - **Flyway Migrations [BE]** — full DDL for new tables, indexes, FK constraints, sequence creates. Apply rules from `new-entities.md` and `tenant-isolation.md` if matched. **Read entity-shape decisions locked by step 13 from conversation context.** For each entity decided as a **configurable data object**, emit `data_objects` + `data_object_fields` seed `INSERT` statements (per `managed-data.md`'s implementation guidance) — do NOT emit a per-entity table DDL for configurable entities. For each entity decided as a **hardcoded JPA entity**, emit normal `CREATE TABLE` DDL.
   - **Permission Seed SQL [BE]** — INSERT statements for permissions, feature_flags, client_role_permissions. Use `ON CONFLICT DO NOTHING` per V2/V3 idempotency pattern. Source: step 11 matrix.
   - **BE Package Layout [BE]** — table mapping responsibility to file path: entity classes, DTOs (request/response), controllers, services, repositories, fixtures, tests.
   - **Repository Queries [BE]** — list of Spring Data JPA query method signatures. EVERY query must include `clientId` parameter (per `tenant-isolation.md`).
   - **FE Package Layout [FE]** — table mapping responsibility to file path: types, services, hooks, pages, components, routes, fixtures, tests.
   - **Hook Specs [FE]** — TanStack Query hook signatures: hook name, query key shape, staleTime, mutation invalidation patterns.
   - **Audit annotations [BE]** — `@Audited` annotation table for non-CRUD operations from step 13 (action, resourceType, description). New AuditAction / AuditResourceType enum values listed.

3. Cross-reference spec.md (in conversation context) — every endpoint in spec.md has a controller method here, every entity has a Flyway table, every permission key from step 11 has a seed SQL row.

## Rules (scoped to this step)
- **NO design rationale.** technical.md is artifacts only. The "why" lives in spec.md.
- **NO business rules.** Same reason.
- **Use exact file paths** following BE / FE package conventions.
- Flyway migration version number = (latest existing + 1). Use multi-digit (e.g., V47).
- Repository methods must include `clientId` in every signature.
- **Configurable entities are seeded, not table-created.** Entities decided as configurable data objects in step 13 are stored as rows in `data_objects` + `data_object_fields` (per `managed-data.md`). Do NOT emit a `CREATE TABLE {entity}` DDL for them. Emit only the seed `INSERT` rows.
- Ambiguities in technical content follow the same interactive resolution pattern as step 13: raise → user answers → fold in; or user defers → write `NEEDS_CLARIFICATION` inline. No cap on count.

## User interaction
None directly.

## Output for downstream steps
- Full technical.md content held in conversation context, ready to be assembled into the plan file in step 15.

## Boundary
technical.md content fully generated → route to step 15: read steps/step-15-write-plan-file.md`.