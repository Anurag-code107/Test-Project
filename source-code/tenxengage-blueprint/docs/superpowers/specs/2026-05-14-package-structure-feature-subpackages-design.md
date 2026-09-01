# Design: Feature Sub-Packages for Package Structure

**Date:** 2026-05-14
**Status:** draft
**Affects:** `docs/patterns/package-structure.md`, all future BE/FE feature work

## Problem

The current `package-structure.md` pattern prescribes a strict package-by-layer arrangement:

- Backend: every controller in one flat `controller/` package, every entity in `entity/`, every service in `service/`, every repository in `repository/`, every DTO in `dto/request/` or `dto/response/`.
- Frontend: components and pages already have a partial feature-subfolder convention but `hooks/`, `services/`, and `types/` are flat.

At current scale this hurts:

| Layer | File count |
|---|---|
| `controller/` | 47 |
| `entity/` | 97 |
| `service/` | 69 |
| `repository/` | 80 |
| `dto/request/` | 87 |
| `dto/response/` | 113 |
| Frontend `hooks/` | 41 |
| Frontend `services/` | 31 |

Finding all files related to one feature requires grepping by prefix, and reviewing cohesion across a feature requires opening files from six different folders.

## Goal

Update the canonical pattern so **new features** land in a structure that keeps related files together, without paying the cost of relocating the ~400 existing backend files or the loose frontend files.

## Non-Goals

- **No migration of existing code.** Existing files stay where they are. They move opportunistically when a feature already touches them.
- **No change to top-level layer packages.** `controller/`, `entity/`, `service/`, `repository/`, `dto/request/`, `dto/response/` remain as the first level of organization.
- **No change to Flyway migration layout.** Migrations stay flat at `src/main/resources/db/migration/` because Flyway requires sequential global numbering.
- **No change to cross-cutting infrastructure.** `security/`, `config/`, `exception/`, `audit/`, `batch/`, `event/` stay flat.
- **No change to non-feature frontend folders.** `lib/`, `utils/`, `config/`, `contexts/`, `data/`, `assets/`, `test/`, `dev/`, `mockups/`, `components/ui/`, repo-root `e2e/` stay as-is.
- **No prescribed feature catalogue.** Feature names emerge per feature; we do not maintain a master list.

## The Rule

> **For every new feature, create a feature sub-package named after the domain noun in each layer the feature touches. Files shared across features stay flat at the layer root.**

### Shared vs feature-owned

- **Shared** — used (or expected to be used) by 2+ features. Stays flat at the layer root.
- **Feature-owned** — owned by a single feature. Lives in that feature's sub-package.

### Promotion (feature → shared)

When a file that started as feature-owned gets pulled into a second feature, it gets promoted to the layer root at that moment, as a small targeted PR. It is not a blocker for the second feature.

### Naming

- **Java packages** — lowercase, no separators: `incentive`, `claim`, `forecast`, `auditlog`, `dataobject`. Matches Java package convention.
- **Frontend folders** — kebab-case: `incentive/`, `deal-qualifier/`, `incentive-builder/`. Matches existing frontend convention.
- The feature name is a **domain noun**, not a roadmap slug or a story name. One domain → one name, used identically across backend and frontend.
- Prefer single-word names. Only compound when one word would be misleading. Avoid roadmap-style multi-word names that turn into ugly Java package names (`partnerrevenue`); use `partner` or `revenue` instead, whichever is the actual owner.

### When to create the sub-package

A new feature creates its sub-package in every layer where it adds files, **from the first file**. Even if the feature only has one controller and one entity initially, both go into `controller/{feature}/` and `entity/{feature}/`. Avoiding single-file folders is not worth the relocation churn when the feature grows.

## Backend Layout

Root Java package remains `com.tenxengage.app`. Top-level packages remain:

```
com.tenxengage.app/
├── audit/          ← stays flat (cross-cutting)
├── batch/          ← stays flat (cross-cutting)
├── config/         ← stays flat (cross-cutting)
├── controller/     ← feature sub-packages inside
├── dto/
│   ├── request/    ← feature sub-packages inside
│   └── response/   ← feature sub-packages inside
├── entity/         ← feature sub-packages inside
│   └── enums/      ← feature sub-packages inside
├── event/          ← stays flat (cross-cutting)
├── exception/      ← stays flat (cross-cutting)
├── repository/     ← feature sub-packages inside
├── security/       ← stays flat (cross-cutting)
└── service/        ← feature sub-packages inside
```

### Example: a new `incentive` feature

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

### What stays flat at the layer root

Examples of existing files that should remain flat because they are genuinely cross-feature:

- `entity/BaseEntity.java`, `entity/TenantAware.java`, `entity/AuditLog.java`, `entity/Client.java`
- Anything inside `audit/`, `batch/`, `config/`, `event/`, `exception/`, `security/`
- All Flyway migrations in `src/main/resources/db/migration/V*.sql`

### Test packages mirror main packages

`src/test/java/com/tenxengage/app/service/incentive/IncentiveServiceTest.java`
`src/test/java/com/tenxengage/app/controller/incentive/IncentiveControllerTest.java`
`src/test/java/com/tenxengage/app/testdata/incentive/IncentiveFixtures.java`

## Frontend Layout

Source root remains `src/`. Top-level folders remain unchanged. Feature subfolders are required for `components/`, `pages/`, `hooks/`, `services/`, and `types/`.

### Example: a new `incentive` feature

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

### What stays flat (no change from today)

- `lib/`, `utils/`, `config/`, `contexts/`, `data/`, `assets/`, `test/`, `dev/`, `mockups/`
- `App.tsx`, `main.tsx`, `index.css`, `App.css`, `vite-env.d.ts`
- `components/ui/` (shadcn primitives) — treat as a special non-feature folder
- Repo-root `e2e/` — one spec file per feature, flat

### Existing inconsistency, handled

`components/` and `pages/` today already have a mix of feature folders and loose top-level files (e.g., `ClaimsTable.tsx`, `RewardBalancesPanel.tsx` are loose but feature-specific). This pattern update governs **new** files only. Existing loose feature-specific files stay put and migrate opportunistically when next touched.

### Routing

`App.tsx` continues to import page components and register routes. Import paths change from `@/pages/IncentivePage` to `@/pages/incentive/IncentiveListPage`. `App.tsx` itself is not restructured.

## Updates to the Pattern File

`docs/patterns/package-structure.md` needs the following changes:

1. **Add a new section** above the existing layer tables titled **"Feature sub-packages"** stating the rule, the shared-vs-feature definition, the naming convention, and the from-first-file requirement.
2. **Update each row of the BE layer table** to show feature-subpackage paths (e.g., `src/main/java/com/tenxengage/app/entity/{feature}/`) with examples.
3. **Update each row of the FE layer table** to show feature-subfolder paths (e.g., `src/hooks/{feature}/`, `src/services/{feature}/`, `src/types/{feature}/`).
4. **Add a "What stays flat" subsection** under each of BE and FE listing the cross-cutting packages/folders that do not take a feature sub-package.
5. **Update the "Examples in codebase" list** so each example uses a feature sub-package path. Existing examples will be valid only after the referenced files migrate; mark them as **"new feature pattern"** examples and note that existing in-tree files still live at the layer root.
6. **Add to "Common gotchas"** entries for:
   - Single-word Java package names; avoid `partnerrevenue`-style compounds.
   - Promotion is a small dedicated PR, not bundled into the feature using it.
   - Migrations stay flat — Flyway numbering is global.

## Out of Scope (Explicit)

- A bulk-move PR for existing 400+ backend files.
- A bulk-move PR for loose frontend files.
- A renamed/restructured `roadmaps/` directory.
- Changes to the contracts repo or admin repos. If the same pattern is wanted there, that is a follow-up spec.
- Tooling (lint rules, ArchUnit tests) to enforce the convention automatically. Reviewer judgment carries this for now.

## Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Inconsistency during transition (existing flat + new sub-packaged) | Pattern file explicitly documents the "new only" rule; opportunistic migration is allowed but never required. |
| Bikeshedding on feature names | Rule says "domain noun, single word preferred." Reviewers can push back on multi-word names. |
| Cross-feature files placed wrong (in a feature folder when they should be flat) | "Used by 2+ features → flat" is a clear test; reviewer judgment applies. Promotion PR exists for missteps. |
| Imports break on opportunistic migrations | Each opportunistic move is a small isolated PR; IDE-driven refactors keep imports correct. |

## Acceptance Criteria

- `docs/patterns/package-structure.md` is updated to describe and enforce the feature-sub-package convention for new code.
- The pattern file clearly states that existing code is not migrated and that opportunistic migration is allowed.
- The pattern file explicitly lists which BE packages and FE folders stay flat (cross-cutting / shared).
- The pattern file contains at least one full backend example and one full frontend example showing a feature's layout across all relevant layers.
- The pattern file's "Common gotchas" section includes the three new entries listed above.
