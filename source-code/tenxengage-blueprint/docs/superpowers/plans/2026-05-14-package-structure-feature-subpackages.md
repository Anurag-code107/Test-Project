# Package Structure — Feature Sub-Packages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update `docs/patterns/package-structure.md` so that new BE and FE features land in feature sub-packages within each layer, while existing files stay at the layer root. Pattern-file update only — no source code is moved.

**Architecture:** Single-file documentation change in `tenxengage-blueprint`. Existing top-level layered packages (BE: `controller/`, `entity/`, `service/`, `repository/`, `dto/{request,response}/`; FE: `components/`, `pages/`, `hooks/`, `services/`, `types/`) are preserved. The pattern adds a feature sub-package convention within each layer. Cross-cutting infrastructure (`security/`, `config/`, `audit/`, `event/`, Flyway migrations, `components/ui/`, etc.) stays flat. Existing code is not migrated; opportunistic migration is allowed but never required.

**Tech Stack:** Markdown only. No code changes, no tests, no migrations.

**Source spec:** `docs/superpowers/specs/2026-05-14-package-structure-feature-subpackages-design.md`

---

## File Structure

Only one file is modified:

- **Modify:** `docs/patterns/package-structure.md` — entirety of the pattern file is updated section-by-section.

No other files are created or modified. Existing entity files, controllers, services, etc., are not touched.

---

## Task 1: Add the "Feature sub-packages" rule section

**Files:**
- Modify: `docs/patterns/package-structure.md` — insert a new section after `## When this applies` and before `## Spec authoring guidance`.

- [ ] **Step 1: Read the current file**

Run: `cat docs/patterns/package-structure.md | head -15`

Expected: see the current `## When this applies` paragraph ending at line 5, with `## Spec authoring guidance` starting at line 7.

- [ ] **Step 2: Insert the new rule section**

Use the Edit tool to insert the new section between `## When this applies` and `## Spec authoring guidance`. The `old_string` is the current `When this applies` paragraph followed by the blank line and the `## Spec authoring guidance` header. The `new_string` is the same content with the new section inserted in between.

`old_string`:
```
This pattern applies to **every feature** that introduces new BE or FE files. It is the canonical reference for file placement conventions across all repos. Load it on every `create-spec` run alongside permissions-and-feature-flags.

## Spec authoring guidance
```

`new_string`:
```
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
```

- [ ] **Step 3: Verify the insertion**

Run: `grep -n "## Feature sub-packages" docs/patterns/package-structure.md && grep -n "## Spec authoring guidance" docs/patterns/package-structure.md`

Expected: `## Feature sub-packages` appears once, on a line lower than `## When this applies` and higher than `## Spec authoring guidance`.

- [ ] **Step 4: Commit**

```bash
git add docs/patterns/package-structure.md
git commit -m "$(cat <<'EOF'
docs(patterns): add feature sub-packages rule to package-structure

Establishes the rule that new features get a sub-package named after
the domain noun in each layer they touch, with shared files staying
flat at the layer root.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Update "Spec authoring guidance" to reference feature sub-packages

**Files:**
- Modify: `docs/patterns/package-structure.md` — adjust the first bullet to mention feature sub-packages.

- [ ] **Step 1: Make the edit**

`old_string`:
```
- In `technical.md`, list all new BE files with full package paths (e.g., `com.tenxengage.app.entity.Incentive`) and all new FE files with full `src/` paths.
```

`new_string`:
```
- In `technical.md`, list all new BE files with full package paths **including the feature sub-package** (e.g., `com.tenxengage.app.entity.incentive.Incentive`) and all new FE files with full `src/` paths **including the feature sub-folder** (e.g., `src/hooks/incentive/useIncentives.ts`).
```

- [ ] **Step 2: Commit**

```bash
git add docs/patterns/package-structure.md
git commit -m "$(cat <<'EOF'
docs(patterns): spec-authoring bullet now requires feature sub-package in paths

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Rewrite the Backend section with feature sub-package paths

**Files:**
- Modify: `docs/patterns/package-structure.md` — replace the contents of `### Backend (../tenxengage-backend/)` (table, invariants, and add stays-flat list + full example).

- [ ] **Step 1: Make the edit**

`old_string`:
```
### Backend (`../tenxengage-backend/`)

Root Java package: `com.tenxengage.app`

| Layer | Package / Path | Naming |
|---|---|---|
| **Entities** | `src/main/java/com/tenxengage/app/entity/` | `{Entity}.java` — extends `BaseEntity`, implements `TenantAware`, carries `@Filter(name="tenantFilter")` |
| **Enums** | `src/main/java/com/tenxengage/app/entity/enums/` | `{EnumName}.java` — Java enum; stored as `varchar(50)` in DB |
| **Repositories** | `src/main/java/com/tenxengage/app/repository/` | `{Entity}Repository.java` — extends `JpaRepository<{Entity}, UUID>`; all queries include `clientId` |
| **Services** | `src/main/java/com/tenxengage/app/service/` | `{Entity}Service.java` — `@Service`, constructor injection, `@Transactional` on writes, `@Transactional(readOnly=true)` on reads |
| **Controllers** | `src/main/java/com/tenxengage/app/controller/` | `{Entity}Controller.java` — `@RestController`, `@RequestMapping("/api/v1/{resource}")` |
| **Request DTOs** | `src/main/java/com/tenxengage/app/dto/request/` | `Create{Entity}Request.java`, `Update{Entity}Request.java` — Java records with Jakarta Bean Validation annotations |
| **Response DTOs** | `src/main/java/com/tenxengage/app/dto/response/` | `{Entity}Response.java`, `{Entity}DetailResponse.java` — immutable records with `from({Entity})` static factory |
| **Migrations** | `src/main/resources/db/migration/` | `V{N}__{description}.sql` — Flyway sequential versioning; `V{N}__create_{table}_table.sql` for schema, `V{N}__seed_{feature}_permissions.sql` for seed data |
| **Test fixtures** | `src/test/java/com/tenxengage/app/testdata/` | `{Entity}Fixtures.java` — builder-return pattern; mandatory for every new entity |
| **Service tests** | `src/test/java/com/tenxengage/app/service/` | `{Entity}ServiceTest.java` — JUnit 5 + Mockito |
| **Controller tests** | `src/test/java/com/tenxengage/app/controller/` | `{Entity}ControllerTest.java` — `@WebMvcTest` |

#### BE Key Invariants

- All IDs are `UUID` — never auto-increment integers
- Every table has `client_id UUID NOT NULL` for tenant isolation
- `BaseEntity` provides: `id`, `createdAt`, `updatedAt`
- `TenantAware` signals the entity participates in the Hibernate tenant filter
- `@Filter(name="tenantFilter", condition="client_id = :clientId")` must be on every entity class
- Constructor injection only — no field `@Autowired`
- DTOs use Java records — no `@Builder` or Lombok on DTOs
- Response DTOs never include: `clientId`, `deleted`, `version`, or internal fields
```

`new_string`:
```
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
├── dto/response/
│   └── incentive/
│       ├── IncentiveResponse.java
│       └── IncentiveDetailResponse.java
└── src/test/java/com/tenxengage/app/testdata/
    └── incentive/
        └── IncentiveFixtures.java
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
```

- [ ] **Step 2: Verify**

Run: `grep -n "entity/{feature}/" docs/patterns/package-structure.md && grep -n "What stays flat at the BE layer root" docs/patterns/package-structure.md && grep -n "BE full example" docs/patterns/package-structure.md`

Expected: each match found exactly once.

- [ ] **Step 3: Commit**

```bash
git add docs/patterns/package-structure.md
git commit -m "$(cat <<'EOF'
docs(patterns): rewrite BE section with feature sub-package paths

Updates the backend layer table to require feature sub-packages in
every layer, adds a "What stays flat" subsection enumerating
cross-cutting BE packages, and includes a full incentive-feature
example.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Rewrite the Frontend section with feature subfolder paths

**Files:**
- Modify: `docs/patterns/package-structure.md` — replace the contents of `### Frontend (../tenxengage-frontend/)`.

- [ ] **Step 1: Make the edit**

`old_string`:
```
### Frontend (`../tenxengage-frontend/`)

Source root: `src/`

| Layer | Path | Naming |
|---|---|---|
| **Types** | `src/types/` | `{feature}.types.ts` — TypeScript interfaces; **always copied from `../tenxengage-contracts/`**, never hand-written |
| **Services** | `src/services/` | `{feature}.service.ts` — typed wrappers around `fetch`; one file per feature |
| **Hooks** | `src/hooks/` | `use{Entity}.ts` — TanStack Query hooks; query key + staleTime + mutation invalidation |
| **Components** | `src/components/{feature}/` | `{Component}.tsx` — React components; co-located `__tests__/{Component}.test.tsx` |
| **Pages** | `src/pages/{feature}/` | `{Page}Page.tsx` — route-level components; wired in `App.tsx` |
| **Routes** | `src/App.tsx` | `<Route path="/{feature}" element={<{Page}Page />} />` inside the appropriate layout |

#### FE Key Invariants

- No `any` type — TypeScript strict mode enforced
- `@/` alias resolves to `src/` — use it for all imports
- Auth tokens stored in-memory only — never `localStorage`
- TanStack Query `staleTime` default: `5 * 60 * 1000` (5 minutes) for all hooks
- shadcn/ui components live in `src/components/ui/` — use them, don't reinvent
- Every component file has a co-located Vitest test in `__tests__/`
- E2E tests live in `e2e/` at the project root — one spec file per feature
```

`new_string`:
```
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
```

- [ ] **Step 2: Verify**

Run: `grep -n "src/hooks/{feature}/" docs/patterns/package-structure.md && grep -n "What stays flat at the FE root" docs/patterns/package-structure.md && grep -n "FE full example" docs/patterns/package-structure.md`

Expected: each match found exactly once.

- [ ] **Step 3: Commit**

```bash
git add docs/patterns/package-structure.md
git commit -m "$(cat <<'EOF'
docs(patterns): rewrite FE section with feature subfolder paths

Hooks, services, and types now require a feature subfolder. Adds a
"What stays flat" subsection enumerating cross-cutting FE folders
and notes the existing inconsistency in components/ and pages/ that
will migrate opportunistically.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Update "Examples in codebase" with feature sub-package paths

**Files:**
- Modify: `docs/patterns/package-structure.md` — replace the `## Examples in codebase` list.

- [ ] **Step 1: Make the edit**

`old_string`:
```
## Examples in codebase

- `../tenxengage-backend/src/main/java/com/tenxengage/app/entity/Incentive.java` — canonical entity (UUID id, BaseEntity, TenantAware, @Filter)
- `../tenxengage-backend/src/main/java/com/tenxengage/app/service/IncentiveService.java` — constructor injection, @Transactional pattern
- `../tenxengage-backend/src/main/java/com/tenxengage/app/dto/response/IncentiveResponse.java` — immutable record with `from()` factory
- `../tenxengage-backend/src/test/java/com/tenxengage/app/testdata/IncentiveFixtures.java` — builder-return test fixture
- `../tenxengage-frontend/src/hooks/useIncentives.ts` — TanStack Query hook with staleTime + mutation invalidation
- `../tenxengage-frontend/src/services/incentive.service.ts` — typed fetch wrapper
```

`new_string`:
```
## Examples in codebase

The examples below illustrate the **target paths** for new features under this pattern. The files currently live at the layer root and will migrate opportunistically when next touched; until then, the existing flat paths are still valid for reading the canonical conventions.

**Target paths (apply to new code):**

- `../tenxengage-backend/src/main/java/com/tenxengage/app/entity/incentive/Incentive.java` — canonical entity (UUID id, BaseEntity, TenantAware, @Filter)
- `../tenxengage-backend/src/main/java/com/tenxengage/app/service/incentive/IncentiveService.java` — constructor injection, @Transactional pattern
- `../tenxengage-backend/src/main/java/com/tenxengage/app/dto/response/incentive/IncentiveResponse.java` — immutable record with `from()` factory
- `../tenxengage-backend/src/test/java/com/tenxengage/app/testdata/incentive/IncentiveFixtures.java` — builder-return test fixture
- `../tenxengage-frontend/src/hooks/incentive/useIncentives.ts` — TanStack Query hook with staleTime + mutation invalidation
- `../tenxengage-frontend/src/services/incentive/incentive.service.ts` — typed fetch wrapper

**Current paths (read-only reference until opportunistic migration):**

- `../tenxengage-backend/src/main/java/com/tenxengage/app/entity/Incentive.java`
- `../tenxengage-backend/src/main/java/com/tenxengage/app/service/IncentiveService.java`
- `../tenxengage-backend/src/main/java/com/tenxengage/app/dto/response/IncentiveResponse.java`
- `../tenxengage-backend/src/test/java/com/tenxengage/app/testdata/IncentiveFixtures.java`
- `../tenxengage-frontend/src/hooks/useIncentives.ts`
- `../tenxengage-frontend/src/services/incentive.service.ts`
```

- [ ] **Step 2: Commit**

```bash
git add docs/patterns/package-structure.md
git commit -m "$(cat <<'EOF'
docs(patterns): split Examples in codebase into target vs current paths

Target paths show the feature-sub-package destinations new code must
use. Current paths remain as a read-only reference until each file
migrates opportunistically.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Add three new entries to "Common gotchas"

**Files:**
- Modify: `docs/patterns/package-structure.md` — append three entries to the `## Common gotchas` list.

- [ ] **Step 1: Make the edit**

`old_string`:
```
- **Test fixtures are mandatory, not optional.** Every new entity needs `{Entity}Fixtures.java`. Stories that skip fixtures create debt that blocks every other story that needs test data for that entity.
```

`new_string`:
```
- **Test fixtures are mandatory, not optional.** Every new entity needs `{Entity}Fixtures.java`. Stories that skip fixtures create debt that blocks every other story that needs test data for that entity.
- **Java feature package names are single words, lowercase, no separators.** `partnerrevenue` is ugly; if a feature spans two domains, pick the actual owner (`partner` or `revenue`) instead. Compound only when one word would be genuinely misleading.
- **Promotion to shared is a small dedicated PR, not bundled into the consuming feature.** When a feature-owned file gets pulled into a second feature, move it to the layer root in its own PR so reviewers can see the boundary change clearly. Bundling it into the second feature's PR hides the promotion.
- **Flyway migrations stay flat — do not put them in feature folders.** Flyway sequential global numbering does not care about folders, and folder-scoped numbering would conflict across parallel feature branches. All `V{N}__*.sql` files live in `src/main/resources/db/migration/`.
```

- [ ] **Step 2: Commit**

```bash
git add docs/patterns/package-structure.md
git commit -m "$(cat <<'EOF'
docs(patterns): add three gotchas for feature sub-packages

Covers single-word Java package naming, promotion as a dedicated PR,
and Flyway migrations staying flat regardless of feature.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Final read-through and verification

**Files:**
- Read: `docs/patterns/package-structure.md` (full file, no edits expected)

- [ ] **Step 1: Read the full updated file**

Use the Read tool on `docs/patterns/package-structure.md` with no offset/limit. Confirm the following are all present and in order:

1. `# Pattern: package-structure` heading
2. `## When this applies` (unchanged)
3. `## Feature sub-packages` with: the rule, shared vs feature-owned, when to create, promotion, naming, scope-of-this-convention
4. `## Spec authoring guidance` with the updated first bullet referencing the feature sub-package
5. `## Implementation guidance`
6. `### Backend (../tenxengage-backend/)` with: feature-sub-package paths in the table, `What stays flat at the BE layer root`, BE full example, BE Key Invariants
7. `### Frontend (../tenxengage-frontend/)` with: feature-subfolder paths in the table, `What stays flat at the FE root`, FE full example, FE Key Invariants
8. `### Flyway Migration Numbering` (unchanged)
9. `## Examples in codebase` with both target and current paths
10. `## Common gotchas` containing the original entries plus the three new ones

- [ ] **Step 2: Run consistency greps**

Run:
```bash
grep -c "{feature}/" docs/patterns/package-structure.md
grep -n "stays flat" docs/patterns/package-structure.md
grep -n "## " docs/patterns/package-structure.md
```

Expected:
- `{feature}/` appears multiple times (across BE table, FE table, and prose) — at least 15 occurrences.
- `stays flat` appears in both BE and FE sections plus the new gotcha about Flyway.
- Top-level headings appear in the order listed in Step 1.

- [ ] **Step 3: Check the addenda chain**

Run: `grep -rln "package-structure" docs/patterns/`

Expected: list of pattern files that reference `package-structure`. Open each briefly to confirm none of them require an addendum from this change (the change is additive — feature sub-packages are a new convention, not a redefinition of existing layer rules). If any addendum is needed, note it as a follow-up rather than expanding this plan.

- [ ] **Step 4: No code-side verification needed**

This change touches only `docs/patterns/package-structure.md`. There are no source files, tests, or build artifacts to verify. The change does not need to compile, lint, or test.

- [ ] **Step 5: Final state commit (only if anything was adjusted during read-through)**

If Step 1 or Step 2 surfaced any inconsistency that needed fixing, run:

```bash
git add docs/patterns/package-structure.md
git commit -m "$(cat <<'EOF'
docs(patterns): tidy package-structure read-through fixes

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

If nothing changed, skip this step — there is no need for an empty commit.

---

## Self-Review

**Spec coverage:**

| Spec requirement | Task |
|---|---|
| New "Feature sub-packages" section with rule, shared-vs-feature, naming, from-first-file, promotion | Task 1 |
| Spec authoring bullet updated to require feature sub-package in paths | Task 2 |
| BE layer table rewritten with feature sub-package paths | Task 3 |
| BE "What stays flat" subsection | Task 3 |
| BE full example | Task 3 |
| FE layer table rewritten with feature subfolder paths | Task 4 |
| FE "What stays flat" subsection | Task 4 |
| FE full example | Task 4 |
| Existing-inconsistency note for components/ and pages/ | Task 4 |
| Examples in codebase split into target / current | Task 5 |
| Three new gotchas (single-word names, promotion PR, Flyway flat) | Task 6 |
| Migration scope: new files only, opportunistic only | Task 1 (scope-of-this-convention paragraph) |
| Final read-through | Task 7 |

No gaps.

**Placeholder scan:** No TBDs, no "TODO", no "fill in", no vague references. Every edit shows the exact `old_string` and exact `new_string`.

**Type consistency:** Not applicable — this is a docs-only change. Feature name conventions (`incentive`, kebab-case `incentive-builder`, etc.) are used consistently across tasks. The example feature name `incentive` appears identically in BE Task 3 and FE Task 4.
