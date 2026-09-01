# Pattern: package-structure

## When this applies

This pattern applies to **every feature** that introduces new BE or FE files. It is the canonical reference for file placement conventions across all repos. Load it on every `create-spec` run alongside permissions-and-feature-flags.

## Feature sub-packages

**The rule:**

> For every new feature, create a feature sub-package named after the domain noun in each layer the feature touches. Files shared across features stay flat at the layer root.

**Shared vs feature-owned:**

- **Shared** — used (or expected to be used) by 2+ features. Stays flat at the layer root.
- **Feature-owned** — owned by a single feature. Lives in that feature's sub-package.

**When to create the sub-package:** From the **first file**. Even if the feature has only one entity and one controller initially, both go into `entity/{feature}/` and `controller/{feature}/`. Avoiding single-file folders is not worth the relocation churn when the feature grows.

**Promotion (feature → shared):** When a feature-owned file gets pulled into a second feature, it gets promoted to the layer root in a small targeted PR. It is not a blocker for the second feature.

**Naming:**

- **Java packages** — lowercase, no separators: `incentive`, `claim`, `forecast`, `auditlog`, `dataobject`. Matches Java package convention.
- **Frontend folders** — kebab-case: `incentive/`, `deal-qualifier/`, `incentive-builder/`. Matches existing frontend convention.
- Use a **domain noun**, not a roadmap slug or a story name. One domain → one name, used identically across backend and frontend.
- Prefer single-word names. Only compound when one word would be genuinely misleading. Avoid roadmap-style multi-word names that turn into ugly Java package names (`partnerrevenue`); use `partner` or `revenue` instead, whichever is the actual owner.

**Scope of this convention:** This rule applies to **new files only**. Existing files at the layer root stay where they are and migrate opportunistically when next touched — never as a standalone bulk-move PR.

## Spec authoring guidance

- In `technical.md`, list all new BE files with full package paths **including the feature sub-package** (e.g., `com.tenxengage.app.entity.incentive.Incentive`) and all new FE files with full `src/` paths **including the feature subfolder** (e.g., `src/hooks/incentive/useIncentives.ts`).
- Note the next available Flyway migration number in `spec.md → ## Prerequisites` (confirmed by globbing `../tenxengage-backend/src/main/resources/db/migration/V*.sql`).
- Allocate Flyway numbers upfront: `V{N}` for entity table(s), `V{N+1}` for permissions + feature flag seed, `V{N+2}+` for junction tables / secondary schema.
- Every new entity must have a corresponding `{Entity}Fixtures.java` test fixture — note it in `technical.md`.

## Implementation guidance

### Backend (`../tenxengage-backend/`)

Root Java package: `com.tenxengage.app`. `{feature}` is a lowercase, no-separator domain noun (see **Feature sub-packages** above).

| Layer | Package / Path | Naming |
|---|---|---|
| **Entities** | `src/main/java/com/tenxengage/app/entity/{feature}/` | `{Entity}.java` — extends `BaseEntity`, implements `TenantAware`, carries `@Filter(name="tenantFilter")` |
| **Enums** | `src/main/java/com/tenxengage/app/entity/enums/{feature}/` | `{EnumName}.java` — Java enum; stored as `varchar(50)` in DB |
| **Repositories** | `src/main/java/com/tenxengage/app/repository/{feature}/` | `{Entity}Repository.java` — extends `JpaRepository<{Entity}, UUID>`; all queries include `clientId` |
| **Services** | `src/main/java/com/tenxengage/app/service/{feature}/` | `{Entity}Service.java` — `@Service`, constructor injection, `@Transactional` on writes, `@Transactional(readOnly=true)` on reads |
| **Controllers** | `src/main/java/com/tenxengage/app/controller/{feature}/` | `{Entity}Controller.java` — `@RestController`, `@RequestMapping("/api/v1/{resource}")` |
| **Request DTOs** | `src/main/java/com/tenxengage/app/dto/request/{feature}/` | `Create{Entity}Request.java`, `Update{Entity}Request.java` — Java records with Jakarta Bean Validation annotations |
| **Response DTOs** | `src/main/java/com/tenxengage/app/dto/response/{feature}/` | `{Entity}Response.java`, `{Entity}DetailResponse.java` — immutable records with `from({Entity})` static factory |
| **Migrations** | `src/main/resources/db/migration/` | `V{N}__{description}.sql` — **stays flat** (Flyway numbering is global); `V{N}__create_{table}_table.sql` for schema, `V{N}__seed_{feature}_permissions.sql` for seed data |
| **Test fixtures** | `src/test/java/com/tenxengage/app/testdata/{feature}/` | `{Entity}Fixtures.java` — builder-return pattern; mandatory for every new entity |
| **Service tests** | `src/test/java/com/tenxengage/app/service/{feature}/` | `{Entity}ServiceTest.java` — JUnit 5 + Mockito |
| **Controller tests** | `src/test/java/com/tenxengage/app/controller/{feature}/` | `{Entity}ControllerTest.java` — `@WebMvcTest` |

#### What stays flat at the BE layer root

These do **not** take a feature sub-package — they are cross-feature infrastructure or genuinely shared base types:

- `entity/BaseEntity.java`, `entity/TenantAware.java` — base classes / interfaces used by every entity
- `entity/Client.java`, `entity/AuditLog.java` — tenant root and audit framework, referenced everywhere
- Everything under `audit/`, `batch/`, `config/`, `event/`, `exception/`, `security/` — application-wide infrastructure
- All Flyway migrations in `src/main/resources/db/migration/V*.sql` — Flyway requires sequential global numbering

A new file goes flat at the layer root only when it is **used (or expected to be used) by 2+ features**. Otherwise it belongs in its feature sub-package.

#### BE full example — new `incentive` feature

```
com.tenxengage.app/
├── entity/
│   ├── BaseEntity.java                 ← shared, stays flat
│   ├── AuditLog.java                   ← shared, stays flat
│   ├── Client.java                     ← shared, stays flat
│   └── incentive/
│       ├── Incentive.java
│       ├── IncentiveBudget.java
│       └── IncentivePayout.java
├── entity/enums/
│   └── incentive/
│       └── IncentiveStatus.java
├── repository/
│   └── incentive/
│       ├── IncentiveRepository.java
│       └── IncentiveBudgetRepository.java
├── service/
│   └── incentive/
│       ├── IncentiveService.java
│       └── IncentivePayoutService.java
├── controller/
│   └── incentive/
│       └── IncentiveController.java
├── dto/request/
│   └── incentive/
│       ├── CreateIncentiveRequest.java
│       └── UpdateIncentiveRequest.java
└── dto/response/
    └── incentive/
        ├── IncentiveResponse.java
        └── IncentiveDetailResponse.java
```

Test packages mirror main packages: `src/test/java/com/tenxengage/app/service/incentive/IncentiveServiceTest.java`, `src/test/java/com/tenxengage/app/controller/incentive/IncentiveControllerTest.java`.

#### BE Key Invariants

- All IDs are `UUID` — never auto-increment integers
- Every table has `client_id UUID NOT NULL` for tenant isolation
- `BaseEntity` provides: `id`, `createdAt`, `updatedAt`
- `TenantAware` signals the entity participates in the Hibernate tenant filter
- `@Filter(name="tenantFilter", condition="client_id = :clientId")` must be on every entity class
- Constructor injection only — no field `@Autowired`
- DTOs use Java records — no `@Builder` or Lombok on DTOs
- Response DTOs never include: `clientId`, `deleted`, `version`, or internal fields

### Frontend (`../tenxengage-frontend/`)

Source root: `src/`. `{feature}` is a kebab-case domain noun (see **Feature sub-packages** above).

| Layer | Path | Naming |
|---|---|---|
| **Types** | `src/types/{feature}/` | `{feature}.types.ts` — TypeScript interfaces; **always copied from `../tenxengage-contracts/`**, never hand-written |
| **Services** | `src/services/{feature}/` | `{feature}.service.ts` — typed wrappers around `fetch`; one file per feature |
| **Hooks** | `src/hooks/{feature}/` | `use{Entity}.ts` — TanStack Query hooks; query key + staleTime + mutation invalidation |
| **Components** | `src/components/{feature}/` | `{Component}.tsx` — React components; co-located `__tests__/{Component}.test.tsx` |
| **Pages** | `src/pages/{feature}/` | `{Page}Page.tsx` — route-level components; wired in `App.tsx` |
| **Routes** | `src/App.tsx` | `<Route path="/{feature}" element={<{Page}Page />} />` inside the appropriate layout |

#### What stays flat at the FE root (no feature subfolder)

- `lib/`, `utils/`, `config/`, `contexts/`, `data/`, `assets/`, `test/`, `dev/`, `mockups/`
- `App.tsx`, `main.tsx`, `index.css`, `App.css`, `vite-env.d.ts`
- `components/ui/` — shadcn primitives, treat as a special non-feature folder
- `components/FeatureGate.tsx`, `components/PermissionGate.tsx`, `components/DataTable.tsx`, and similar shared layout/auth primitives — used by every feature
- Repo-root `e2e/` — one spec file per feature, flat

A new component, hook, service, type, or page goes flat at the root only when it is used by **2+ features**. Otherwise it belongs in its feature subfolder.

**Existing inconsistency:** Today `components/` and `pages/` contain a mix of feature folders and loose top-level files that are actually feature-specific (e.g., `ClaimsTable.tsx`, `RewardBalancesPanel.tsx`). This pattern governs **new** files only. Existing loose feature-specific files stay put and migrate opportunistically when next touched.

#### FE full example — new `incentive` feature

```
src/
├── components/
│   ├── ui/                       ← shadcn primitives, stays as-is
│   ├── FeatureGate.tsx           ← shared, stays flat
│   ├── PermissionGate.tsx        ← shared, stays flat
│   ├── DataTable.tsx             ← shared, stays flat
│   └── incentive/
│       ├── IncentiveCard.tsx
│       ├── IncentiveForm.tsx
│       └── __tests__/
│           └── IncentiveCard.test.tsx
├── pages/
│   ├── HomePage.tsx              ← generic/shared, stays flat
│   └── incentive/
│       ├── IncentiveListPage.tsx
│       └── IncentiveDetailPage.tsx
├── hooks/
│   ├── usePermissions.ts         ← shared, stays flat
│   └── incentive/
│       ├── useIncentives.ts
│       └── useIncentiveMutations.ts
├── services/
│   ├── auth.service.ts           ← shared, stays flat
│   └── incentive/
│       └── incentive.service.ts
└── types/
    └── incentive/
        └── incentive.types.ts    ← copied from contracts as today
```

`App.tsx` still imports page components and registers routes. The import path changes from `@/pages/IncentivePage` to `@/pages/incentive/IncentiveListPage`; `App.tsx` itself is not restructured.

#### FE Key Invariants

- No `any` type — TypeScript strict mode enforced
- `@/` alias resolves to `src/` — use it for all imports
- Auth tokens stored in-memory only — never `localStorage`
- TanStack Query `staleTime` default: `5 * 60 * 1000` (5 minutes) for all hooks
- shadcn/ui components live in `src/components/ui/` — use them, don't reinvent
- Every component file has a co-located Vitest test in `__tests__/`
- E2E tests live in `e2e/` at the project root — one spec file per feature

### Flyway Migration Numbering

The next available migration number is tracked in `spec.md → ## Prerequisites` and confirmed by globbing `../tenxengage-backend/src/main/resources/db/migration/V*.sql`.

Standard allocation per feature:

| Migration | Purpose |
|---|---|
| `V{N}` | Create entity table(s) |
| `V{N+1}` | Seed permissions + feature flag |
| `V{N+2}+` | Junction tables or secondary schema (if needed) |

## Examples in codebase

The examples below illustrate the **target paths** for new features under this pattern. The files currently live at the layer root and will migrate opportunistically when next touched; until then, the existing flat paths are still valid for reading the canonical conventions.

**Target paths (apply to new code):**

- `../tenxengage-backend/src/main/java/com/tenxengage/app/entity/incentive/Incentive.java` — canonical entity (UUID id, BaseEntity, TenantAware, @Filter)
- `../tenxengage-backend/src/main/java/com/tenxengage/app/service/incentive/IncentiveService.java` — constructor injection, @Transactional pattern
- `../tenxengage-backend/src/main/java/com/tenxengage/app/dto/response/incentive/IncentiveResponse.java` — immutable record with `from()` factory
- `../tenxengage-backend/src/test/java/com/tenxengage/app/testdata/incentive/IncentiveFixtures.java` — builder-return test fixture
- `../tenxengage-frontend/src/hooks/incentive/useIncentives.ts` — TanStack Query hook with staleTime + mutation invalidation
- `../tenxengage-frontend/src/services/incentive/incentive.service.ts` — typed fetch wrapper
- `../tenxengage-frontend/src/types/incentive/incentive.types.ts` — TypeScript interfaces copied from contracts

**Current paths (read-only reference until opportunistic migration):**

- `../tenxengage-backend/src/main/java/com/tenxengage/app/entity/Incentive.java`
- `../tenxengage-backend/src/main/java/com/tenxengage/app/service/IncentiveService.java`
- `../tenxengage-backend/src/main/java/com/tenxengage/app/dto/response/IncentiveResponse.java`
- `../tenxengage-backend/src/test/java/com/tenxengage/app/testdata/IncentiveFixtures.java`
- `../tenxengage-frontend/src/hooks/useIncentives.ts`
- `../tenxengage-frontend/src/services/incentive.service.ts`
- `../tenxengage-frontend/src/types/incentive.types.ts`

## Common gotchas

- **Types are copied from contracts, never hand-written.** Hand-written types drift from the OpenAPI spec. Always source from `../tenxengage-contracts/`.
- **Response DTOs must not leak `clientId`.** It is a security invariant — `clientId` is internal to the tenant isolation layer and must never appear in API responses.
- **Java records for DTOs — no Lombok.** Using `@Builder` or `@Data` on DTOs adds mutable state and complicates serialization. Records are immutable and serialization-friendly.
- **`@Filter` must be on the entity class, not just the repository.** The Hibernate filter is declared at the entity level. A repository without a filtered entity will return cross-tenant data.
- **Flyway migration numbers must be sequential with no gaps.** A gap causes Flyway to fail. Allocate numbers upfront in the spec to avoid collisions between parallel feature branches.
- **`staleTime` must be set on every TanStack Query hook.** Without it, queries refetch on every window focus — a significant performance problem for list views with large datasets.
- **`@/` alias is required for all imports.** Relative imports like `../../components/` break when files are moved and are harder to trace. Always use `@/components/`.
- **Test fixtures are mandatory, not optional.** Every new entity needs `{Entity}Fixtures.java`. Stories that skip fixtures create debt that blocks every other story that needs test data for that entity.
- **Java feature package names are single words, lowercase, no separators.** `partnerrevenue` is ugly; if a feature spans two domains, pick the actual owner (`partner` or `revenue`) instead. Compound only when one word would be genuinely misleading.
- **Promotion to shared is a small dedicated PR, not bundled into the consuming feature.** When a feature-owned file gets pulled into a second feature, move it to the layer root in its own PR so reviewers can see the boundary change clearly. Bundling it into the second feature's PR hides the promotion.
- **Flyway migrations stay flat — do not put them in feature folders.** Flyway sequential global numbering does not care about folders, and folder-scoped numbering would conflict across parallel feature branches. All `V{N}__*.sql` files live in `src/main/resources/db/migration/`.
