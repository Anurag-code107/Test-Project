# Pattern: Location Hierarchy

## When this applies

Use this pattern when a feature references, filters by, allocates budget to, or configures a **location hierarchy** — a tenant-configurable, multi-level tree used for geographic/organizational scoping. Examples: "incentive eligibility by region", "budget allocation by country", "filter reports by location".

## Spec authoring guidance

When location hierarchy is involved in your feature:

- **Call it out explicitly** — in Audience or Budget sections, state "location-scoped" or "requires location filtering"
- **Assume it's tenant-configurable** — don't hardcode specific levels (Region/Country/State) or assume fixed depth. The tenant's admin may have created custom levels or removed levels
- **Three independent flags** — a location level can be visible in the incentive builder (`useInBuilder`), in dashboard filters (`useInFilters`), or both, or neither. Spec author doesn't set these — they're admin-controlled — but be aware that "location eligibility" and "location filter" are independent features
- **Cascading scope** — if the user picks a location at a parent level (e.g., Region), child pickers (e.g., Country) should only show values under that parent. Frontend handles this automatically
- **Wire format**: When audience rule is persisted, it becomes `{ ruleType: "LOCATION", ruleValue: <locationValueId (UUID)>, locationLevelId: <levelId (UUID)> }`. Allocations are flat: `{ locationValueId: <UUID>, amount: <string> }[]` — not hierarchical
- **Names vs IDs** — internally, location selections are stored and transmitted as UUIDs, never names. UI may show names for readability

Examples in codebase: `../tenxengage-blueprint/docs/patterns/builder-config.md` (value source reference for location hierarchy as a field value source)

## Implementation guidance

### Backend

Location hierarchy is persisted in two tables: `LocationLevel` (metadata — names, depth, feature flags) and `LocationValue` (tree nodes — parent/child FK).

**Key invariants:**

- Tenant-scoped via `client_id`. No cross-tenant leakage
- Plain `parent_id` self-FK; no closure table. Traversal happens at DTO serialization, not SQL
- Three flags per level: `useInBuilder`, `useInFilters`, `isRequired`. **These are independent** — a level can be in builder but not filters, or vice versa
- `isRequired` constraint: only true at depth 0 (root level must always have a selection). Deeper levels are optional
- Name uniqueness constraint: `(client_id, level_id, name)` unique at root only; siblings can have duplicate names if they share a parent

**Service entry point:** `LocationService.getHierarchy()` returns the full tree. `LocationService.getBuilderOptions()` and `getFilterOptions()` return filtered subsets (by flag). These are called by the frontend; use them directly

See the backend LocationService (src/main/java/.../LocationService.java) for entity shapes, repository methods, API endpoints.

### Frontend

Location hierarchy is accessed via three hooks: `useLocationHierarchy()` (full tree), `useLocationBuilderOptions()`, `useLocationFilterOptions()`. The TypeScript shape is `LocationValueResponse` (recursive, with `children: LocationValueResponse[]`).

**Two consumer patterns:**

1. **LocationFilter** — single-select picker for dashboard widgets (e.g., ApprovalsWidget). Returns a single `locationValueId` or "GLOBAL"
2. **Step3Audience** — multi-select picker in the incentive builder with cascading scope. Parent selection filters children via `getLocationValuesForLevel(..., ancestorSelections)`

**Key invariant — names vs UUIDs:** Frontend state holds names (`audience.locationSelections: Record<levelId, string[]>`). At save time, `builderRequestMapper.ts` resolves names to UUIDs for the API call. Never send names to the backend; never assume names are stable (tenants rename locations)

**Budget allocation invariant:** Budget tree is scoped to the eligibility tree. If user selected Region=USA and State=California in Step 3, the budget tree in Step 4 shows only USA/California tree, not the full hierarchy

See the frontend location.types.ts and hooks/useLocationApi.ts for types, hooks, and component details.

### Cascading in audience rules (2026-05-25)

When a DataObject exposes multiple LOCATION_HIERARCHY fields, the FE infers cascading purely from `LocationLevel.depth`:

- A field whose `location_level_id` resolves to depth N cascades under any sibling field whose level resolves to depth N-1.
- The child's value picker is disabled until the parent rule has at least one selected value.
- The child's options are filtered to children of the parent's selected location IDs.

There is no `parent_field_id` column on `data_object_fields`. Admins cannot configure cascading manually; it follows from the LocationLevel topology.

**Recommended convention:** one LocationLevel per depth per tenant. If multiple LocationLevels share a depth, cascading is ambiguous — the service-layer guard logs a WARN and the FE treats the child as non-cascading (open list).

See [audience-rules.md](audience-rules.md) for the full rule pipeline.

## Common anti-patterns

- ❌ Hardcode hierarchy levels ("always Region, Country, State, City") — tenant may have removed or renamed levels
- ❌ Send location names to backend — always send UUIDs; locations can be renamed and names are not unique across siblings
- ❌ Assume all levels render everywhere — `useInBuilder` and `useInFilters` are independent; check the flags
- ❌ Allocate budget outside the eligibility scope — the budget tree is filtered to the locations selected in Step 3
