# Consolidated Project Context — TenXEngage Platform

## Purpose

Provides Claude with complete understanding of all project standards when creating feature specs. The create-spec skill also reads PROJECT-CONTEXT.md from sibling repos at runtime for current details.

## Platform Overview

- Core app: tenxengage-frontend + tenxengage-backend + tenxengage-contracts
- Admin app: tenxengage-admin-frontend + tenxengage-admin-backend
- Planning: tenxengage-blueprint (this repo)
- All repos follow identical standards within their stack

## Backend Standards Summary

- Java 21, Spring Boot 3.2.5, PostgreSQL 16, Redis 7, Kafka, Gradle 8.5
- Layered architecture: controller → service → repository → entity
- All IDs UUID, Flyway migrations only, @RequiredArgsConstructor injection
- Response DTOs as Java records with from() factory
- @RequiresPermission for authorization (custom AOP annotation)
- JUnit 5 + Mockito + Testcontainers, JaCoCo 60%/50% minimum
- SLF4J + MDC structured logging

## Frontend Standards Summary

- React 18 + TypeScript (strict) + Vite + Tailwind CSS + shadcn/ui
- TanStack Query for server state, React Context for client state
- react-hook-form + zod for form validation
- Functional components only, @/ path alias, no `any` types
- usePermissions() + PermissionGate for access control
- Vitest + React Testing Library for unit tests
- Playwright + page.route() for E2E tests (planned)

## Contracts Standards Summary

- OpenAPI 3.0.1, /api/v1/ base, kebab-case paths, camelCase JSON
- UUID IDs, ISO 8601 dates, zero-based pagination
- Standard error shape: { errorCode, errorMessage, status, timestamp, path }
- Enums defined in contracts first, then implemented

## Cross-Cutting Standards

- Multi-tenancy: client_id on every entity, Hibernate @Filter, X-Client-Subdomain header
- Permission model: 5-layer resolution, module.* and action.* keys
  - Frontend: usePermissions() hook, <PermissionGate> component, <ProtectedRoute>
  - Backend: @RequiresPermission annotation (AOP), PermissionService.resolveEffectivePermissions()
- Feature flags: subscription-tier based (STARTER/PROFESSIONAL/ENTERPRISE) with multi-level resolution — see docs/patterns/permissions-and-feature-flags.md
  - Returned in login/refresh response as enabledFeatures[]
  - Frontend: enabledFeatures.includes('key')
  - Backend: FeatureFlagService.getEnabledFeatures(clientId)
- Currency types: only 4 (cash, points, credits, tickets)
- IDs: UUID everywhere
- Dates: ISO 8601 with timezone
- API versioning: /api/v1/
- **API response envelope:** `ApiResponseAdvice` (`@ControllerAdvice`) wraps ALL controller responses in `{ "data": <payload>, "message": "Success", "success": true, "timestamp": "..." }`. E2E tests must extract `.data` from every API call response — e.g., `(resp as any).data.id`. Error responses from `GlobalExceptionHandler` are NOT wrapped (they use their own `{ errorCode, errorMessage, status, ... }` structure).
- **FE error code extraction:** `GlobalExceptionHandler` emits `errorCode`; legacy handlers used `code`. All FE mutation hooks that map error codes must use `data?.errorCode ?? data?.code` (tolerate both). See `useCourseMutations.extractErrorCode` as the canonical example — copy it verbatim when writing new hooks.
- **Test stack rate limits:** `RateLimitFilter` uses in-memory fixed-window buckets with production limits (login: 10/60s/IP). Test-profile stacks should set `security.rate-limit.login-max: 100` in `application-test-stack.yml` to avoid exhaustion across sequential E2E logins. E2E login helpers should retry on 429 with backoff as a secondary guard.
- **JPQL optional filter parameters fail on PostgreSQL with null binds.** The `(:param IS NULL OR ...)` pattern in Spring Data JPQL cannot infer the type of a null bind parameter on PostgreSQL, causing `PSQLException: No heuristic for type class java.lang.Object` or similar. For optional filters, use a native SQL query with `COALESCE(CAST(:param AS text), CAST(col AS text)) = CAST(col AS text)` — or use Spring Data Specification/Criteria API to compose predicates dynamically. Also add an explicit `deleted = false` predicate in native queries since `@SQLRestriction` is not applied to `nativeQuery = true` methods.
- **JVM timezone "Asia/Calcutta" rejected by PostgreSQL 16 Docker image.** When running the backend on Indian-timezone systems (JVM default `user.timezone=Asia/Calcutta`), PostgreSQL 16 rejects JDBC connections with `FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"`. This is the deprecated IANA name for `Asia/Kolkata`. Fix: pass `-Duser.timezone=UTC` (or `-Duser.timezone=Asia/Kolkata`) in JVM args for any process that opens a JDBC connection. Add this to `bootRun` JVM args in `build.gradle` for the local dev profile.
- **Flyway version numbers must be globally unique across all migration sets.** If two `*.sql` files in `classpath:db/migration` share the same version number (e.g., two `V5__*.sql` files), Flyway raises `Found more than one migration with version N` at startup. Never regenerate schema-creation scripts with existing version numbers. When restructuring migrations, move old scripts to a non-classpath directory (e.g., `db/migration-pending`) rather than deleting them, but ensure the remaining migrations form a self-consistent chain on a fresh DB. Testcontainers integration tests mask this gap because they use Hibernate `create-drop` which bypasses Flyway entirely.
- **Playwright strict mode — scope text selectors:** `page.getByText('X')` matches all elements containing X (badges, help text, tooltips). Scope with `.first()` on the correct container or use role-qualified locators to avoid `strict mode violation: resolved to N elements` failures. See `docs/patterns/e2e-testing.md`.

## Platform Domain Model

### Client Hierarchy

```
Client (Vendor)
  └── Partner Companies
        └── Users
```

A **Client** is the top-level tenant (the vendor). **Partner Companies** belong to a Client and represent partner organizations. **Users** belong to a Partner Company and are scoped to that client via `client_id`.

### Access Control

The permission model is **permission-driven, NOT role-driven**. Always check named permission keys (e.g., `action.incentive.create`). Never gate access by role name — roles are client-configurable containers of permissions and the same permission may be assigned to different role names across tenants.

### Feature Flags

Feature flags resolve at multiple levels. Never assume a single-level check is sufficient. See `docs/patterns/permissions-and-feature-flags.md` for the full resolution model. When speccing features that involve access gating or module enablement, always reference the pattern file.

## Spec Impact Checklist

When creating a spec, ensure it addresses:

- [ ] New permissions needed (module.* and action.* keys, both FE and BE)
- [ ] New feature flags needed (tier enablement: starter/professional/enterprise)
- [ ] New enums (define in contracts first)
- [ ] Flyway migrations (with indexes on client_id)
- [ ] Tenant isolation (client_id on new entities, TenantAware, @Filter)
- [ ] Audit trail (which operations to log)
- [ ] Frontend builder pattern (if building a wizard/builder — read builder-wizard.md)
- [ ] Builder config (if builder has configurable sections — read builder-config.md)
- [ ] Location filtering or scoping ("Does the feature filter by location, allocate budget by location, or require location eligibility?") → read [docs/patterns/location-hierarchy.md](docs/patterns/location-hierarchy.md)
- [ ] AI copilot (if builder has AI assistant — read ai-copilot.md)
- [ ] ProtectedRoute + PermissionGate placement for new pages/actions
- [ ] Sidebar navigation entry with permissionKey
- [ ] API contract (OpenAPI spec with proper status codes)
- [ ] Test scenarios (unit, integration, E2E with Playwright page.route())
- [ ] Observability (log events, MDC fields, metrics)

## Pattern References

For detailed implementation patterns, read the relevant file in docs/patterns/:

- Builder/wizard components (spec-level, all domains) → docs/patterns/builder-wizard.md
  - Frontend impl — new canonical (course, learning-path, future builders) → [tenxengage-frontend/docs/patterns/builder-widget-platform.md](../tenxengage-frontend/docs/patterns/builder-widget-platform.md)
  - Frontend impl — incentive builder → [tenxengage-frontend/docs/patterns/builder-widget.md](../tenxengage-frontend/docs/patterns/builder-widget.md)
- Dynamic builder configuration → docs/patterns/builder-config.md
  (Sibling-repo copies in backend and frontend document the incentive builder specifically; enablement domains follow the blueprint canonical)
- Location hierarchy (tenant-configurable, multi-level tree; builder/filter integration) → [docs/patterns/location-hierarchy.md](docs/patterns/location-hierarchy.md)
- AI copilot integration → docs/patterns/ai-copilot.md
- Permissions & feature flags → docs/patterns/permissions-and-feature-flags.md
- Tenant isolation → docs/patterns/tenant-isolation.md
  (The backend copy was deleted; all content including the promoted Pitfalls section lives here in blueprint)
- BE/FE package conventions → docs/patterns/package-structure.md

## Repository Structure

Each feature folder uses the **story-sliced format** (features created from 2026-04-19 onward):

- `features/<slug>/spec.md` — Design reference: decisions, tables, business rules, security, edge cases (no Java code, no Flyway SQL)
- `features/<slug>/technical.md` — Implementer reference: Flyway SQL, BE/FE file paths, repository queries, hook specs
- `features/<slug>/stories.md` — Story index + dependency graph
- `features/<slug>/stories/US-NN-*.md` — One file per user story (self-contained execution unit for one Claude Code session)
- `features/<slug>/tasks/foundation.md` — Horizontal bedrock tasks (enums, migrations, entities, permissions)
- `features/<slug>/tracker.md` — Session status tracker (single source of truth, updated by sessions)
- `features/<slug>/test-plan.md` — Cross-story integration tests (Testcontainers, multi-entity workflows)
- `.claude/skills/create-spec/templates/` — spec-template.md, technical-template.md
- `.claude/skills/create-stories/templates/` — stories-index-template.md, foundation-tasks-template.md, tracker-template.md, story-template.md, test-plan-template.md
- `roadmaps/{slug}/roadmap.md` + `roadmaps/{slug}/digest.md` — Per-BRD output of `/decompose-brd`: a feature roadmap (slices, dependencies, ADR blockers, recommended sequence) plus a 1-2 page digest of BRD-specific cross-cutting context that downstream `/create-spec` runs reuse

## Skills

### Blueprint skills

- `/decompose-brd` — **Run BEFORE `/create-spec` when the input is initiative-scoped (multi-feature BRD, multiple personas, multi-phase plan).** Slices a BRD into a feature roadmap and writes a BRD digest. Outputs `roadmaps/{slug}/roadmap.md` + `roadmaps/{slug}/digest.md`. Each slice ends up with a `/create-spec {slug} F-NN` invocation. Skip this skill if the input is already a single well-scoped feature.
- `/create-spec` — Generate a feature spec from requirements (runs in plan mode; writes spec.md + technical.md; creates feature branch in blueprint + contracts). Accepts `{slug} F-NN` identifier to read context directly from the roadmap + digest — no copy-paste needed. Also accepts free-text or file path for features not derived from a BRD.
- `/create-stories` — Decompose a reviewed spec into stories, foundation tasks, tracker, and test plan (run after /create-spec + /review-spec).
- `/review-spec` — Validate spec across 12 architectural dimensions.
- `/bug-reporter` — Capture and file bugs: creates ClickUp tickets and/or `bugs-evidence/` folders from human text, MCP browser auto-capture, or evidence folder escalation.
- `/bug-fixer` — End-to-end bug fix: normalize → reproduce → failing test → fix → ready-check → MRs → learnings. Mode-aware (M1 inline / M2 standalone / M3 tracked). Supersedes `/clickup-bug-fix`.
- **`/run-feature <slug>`** — Orchestrates the implementation phase of a feature end-to-end: contracts → foundation → story-layers (BE + FE) → integration tests. Five-question interactive startup, four gate modes (every/story/ready-check/feature-end), per-layer stop targets, pause/peek keywords, per-layer failure halt with copy-paste resume instructions. The tracker is the source of truth; the orchestrator only reads it. 
- **`/finish-unit <slug> <unit-id>`** — Recovery helper for `/run-feature` failures. After you've manually fixed a halted unit's sub-branch, this skill re-verifies tests + ready-check, then squash-merges and flips the tracker in one command. The failure helper-text printed by `/run-feature` directs you here.
- **`/qa-explore <feature-slug>`** — Autonomous QA exploration: reads story files to build an exploration plan (route manifest, AC assertions, UI states, interaction inventory), creates test data via real API calls, runs a story-guided primary pass + unconstrained secondary pass against the real stack, auto-fixes CRITICAL/HIGH FE issues (max 2 cycles, committed to `work/<slug>-qa-explore-<YYYYMMDD>` in FE repo), and writes a report to `.qa-explore/<slug>/`. On-demand only — does not gate any tracker row. Use after T1 passes or ad-hoc during development. Accepts `--story=US-NN`, `--page=/route`, `--role`, `--reuse-stack`, `--dry-run`.

### Bug workflow data surfaces

- `bugs-evidence/` — Gitignored staging area for captured bugs (per-bug subfolders with meta.md, screenshots, console.log, network.har). Open `bugs-evidence/index.html` to browse.
- `docs/bugs-index.md` — Append-only historical corpus of all completed bug-fixer runs (committed to main).
- `docs/learnings.md` — Curated cross-repo learnings promoted by bug-fixer (Tier 3). Per-repo learnings land in each repo's own `docs/` and `PROJECT-CONTEXT.md`.

## Feature Branching Workflow

Blueprint's job ends at a reviewed spec + decomposed stories. Each repo owns its work from there.

All repos use the same branch name: `features/<slug>`.

0. **Blueprint step 0 (optional, for initiative-scoped BRDs)** — `/decompose-brd <brd-path>` → produces `roadmaps/{slug}/roadmap.md` + `roadmaps/{slug}/digest.md`. Each feature slice shows a `/create-spec {slug} F-NN` invocation. Skip if the input is already a single feature.
1. **Blueprint step 1** — `/create-spec` → spec.md written and reviewed. Creates `features/<slug>` branch in both blueprint and tenxengage-contracts. If a BRD digest exists, Phase 0a inherits it as cross-cutting context.
2. **Blueprint step 2** — `/create-stories {slug}` → stories + foundation tasks + tracker + test plan generated from the reviewed spec.
3. **Contracts (before any implementation)** — `cd ../tenxengage-contracts && /generate-contracts {slug}` → reads the reviewed spec, writes all endpoints/models/enums into this repo. Run this **first**, before foundation tasks, so both BE and FE can work from day one.
4. **Backend** — Foundation: `cd ../tenxengage-backend && /execute-foundation {slug} F1` (run F1–F4 sequentially). Stories: `/load-story {slug} US-01` (one session per story, after foundation; parallel where stories touch disjoint entities).
5. **Frontend** — `cd ../tenxengage-frontend && /load-story {slug} US-01` (one session per story). FE story sessions can start immediately after contracts are generated (scaffold against types + mocks; wire to real BE as it lands).

## Critical Rules

1. **Specs are the source of truth** — All implementation decisions trace back to the spec.
2. **No code in this repo** — Only markdown specs and templates.
3. **Contracts live in tenxengage-contracts** — All OpenAPI contracts (`endpoints/*.yaml`, `models/*.md`) belong in `../tenxengage-contracts/`. Never place contract files in this repo. Run `/generate-contracts` from the contracts repo after the spec is `reviewed`.
4. **Cross-repo reads are normal** — Skills read from `../tenxengage-backend/` and `../tenxengage-frontend/` to ground specs in actual patterns.
