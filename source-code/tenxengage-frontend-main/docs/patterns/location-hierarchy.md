# Location Hierarchy Pattern — Frontend

TypeScript shapes, hooks, and component integration for location-scoped features (eligibility picking, budget allocation, filtering).

## Types

Read the canonical source: `src/types/location.types.ts`

**Core types:**

| Type | Purpose |
|---|---|
| `LocationValueResponse` | Single location node: id, name, code, levelName, levelId, parentId, children (recursive) |
| `LocationLevelResponse` | Level metadata: id, name, depth, valueCount, useInBuilder, useInFilters, isRequired |
| `LocationHierarchyResponse` | Complete tree: levels array + root values tree |

## Hooks

| Hook | Returns | Used by |
|---|---|---|
| `useLocationHierarchy()` | `LocationHierarchyResponse` | Admin LocationMappingSection; rarely needed in user features |
| `useLocationBuilderOptions()` | `LocationLevelResponse[]` (filtered to `useInBuilder=true`) | Step3Audience pickers |
| `useLocationFilterOptions()` | `LocationFilterOptionsResponse` (filtered to `useInFilters=true`) | LocationFilter dashboard widgets |

All hooks are cached via TanStack Query; fetch happens once per session.

## Consumer Components

### LocationFilter (Single-Select)

**Location:** `src/components/LocationFilter.tsx`

Single-select location picker for dashboard widgets (e.g., ApprovalsWidget, ProgramPerformanceWidget). **Returns:** one `locationValueId` (UUID string) or `"GLOBAL"` (no filter).

**Behavior:** Popover tree with search, leaf-count badges, ancestor display context. Clicking a selected value again clears to "GLOBAL".

### Step3Audience (Multi-Select with Cascading)

**Location:** `src/components/incentive-builder/steps/Step3Audience.tsx`

Multi-select picker in the incentive builder's Step 3 (Audience). **Returns:** `audience.locationSelections: Record<levelId, string[]>` (names, not UUIDs — see next invariant).

**Cascading scope invariant:** When user selects a location at a parent level, deeper level pickers are automatically filtered to show only children of that parent. Implemented via `getLocationValuesForLevel(deeperLevel.id, hierarchy, ancestorSelections)` helper (line ~89).

**Budget tree:** Step 4 calls `buildBudgetTree(hierarchy, eligibilitySelections)` to scope the budget allocation tree to only the locations the user picked in Step 3. **Critical:** the budget tree is NOT the full hierarchy; it's filtered to the eligibility selections.

## Critical Invariant: Names vs UUIDs

**Frontend state:** `audience.locationSelections` holds **names**, not IDs.
```typescript
audience.locationSelections = {
  "level-uuid-1": ["California", "Texas"],
  "level-uuid-2": ["Bay Area", "Austin"],
}
```

**On wire (at save time):** `builderRequestMapper.ts` resolves names to UUIDs via `buildNameIndex()`. Wire format is:
```typescript
rules.push({ ruleType: "LOCATION", ruleValue: <uuid>, locationLevelId: <levelId> })
```

**Why:** Names are user-readable and survive renames in the UI. UUIDs are stable and immutable on the wire. Never send names to the backend; names are not unique across siblings and can change.

See `src/utils/builderRequestMapper.ts:49–68` for the resolution logic.

## Three Flags & Visibility

Each level has three independent feature flags:

- **`useInBuilder`** — show in Step3Audience multi-selects (incentive builder)?
- **`useInFilters`** — show in LocationFilter (dashboard widgets)?
- **`isRequired`** — must user select a value at this level (in builder)?

Implications:
- A level can be in the builder but not in filters, or vice versa
- Only root level can be required; deeper levels are always optional
- Filtering logic must check these flags: `levels.filter(l => l.useInBuilder)` for builder, `levels.filter(l => l.useInFilters)` for filters

## Anti-patterns

- ❌ Send location selection names to the API — always resolve to UUIDs first
- ❌ Render all levels in Step 3 or in LocationFilter — check `useInBuilder` / `useInFilters` flags
- ❌ Allocate budget to locations outside the Step 3 eligibility tree — always scope budget tree to eligibility
- ❌ Assume a cascading picker can pick locations at multiple levels simultaneously without ancestor scoping — always apply ancestor filters when rendering child pickers
