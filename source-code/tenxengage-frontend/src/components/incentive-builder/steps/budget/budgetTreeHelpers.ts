import type {
  LocationHierarchyResponse,
  LocationValueResponse,
} from "@/types/location.types";

/**
 * Pure helpers for the per-level budget allocation tree in Step 4.
 *
 * The tree is the user's eligibility scope (from `audience.locationSelections`)
 * intersected with the platform's location hierarchy. Each node carries a
 * user-typed amount per currency; blanks are auto-filled at validation/save
 * time with the residual `parent.total - sum(filled siblings)`. Overshoots
 * (filled siblings > parent) are surfaced as validation errors.
 *
 * Keys throughout are `locationValueId` (UUID) — never names — to avoid
 * collisions across levels (e.g. "Georgia" the country vs the state).
 */

/**
 * One node in the budget allocation tree. `children` already filtered to the
 * user's eligibility scope at every depth; nodes with no eligible descendants
 * become leaves.
 */
export interface BudgetTreeNode {
  locationValueId: string;
  locationValueName: string;
  locationLevelId: string;
  levelName: string;
  /** Filtered to children that are themselves part of the eligibility scope. */
  children: BudgetTreeNode[];
}

/**
 * Builds the eligibility-scoped budget tree. The roots are the depth-0
 * LocationValues the user picked in Eligibility. Each node's children are the
 * intersect of (its hierarchy children) and (the user's deeper picks).
 *
 * Returns an empty array when the hierarchy isn't loaded yet, or when no
 * eligibility has been picked.
 */
export function buildBudgetTree(
  hierarchy: LocationHierarchyResponse | undefined,
  locationSelections: Record<string, string[]>,
): BudgetTreeNode[] {
  if (!hierarchy?.levels?.length || !hierarchy.tree) return [];

  const levelNameById = new Map<string, string>();
  for (const lvl of hierarchy.levels) levelNameById.set(lvl.id, lvl.name);

  const depth0LevelId = hierarchy.levels[0]?.id;
  if (!depth0LevelId) return [];

  const depth0Selected = new Set(locationSelections[depth0LevelId] ?? []);

  function build(node: LocationValueResponse): BudgetTreeNode {
    const childrenSelected = new Set(
      locationSelections[
        // First child level under this node — derive from any child node.
        node.children[0]?.levelId ?? ""
      ] ?? [],
    );
    const eligibleChildren = (node.children ?? [])
      .filter((c) => childrenSelected.has(c.name))
      .map(build);
    return {
      locationValueId: node.id,
      locationValueName: node.name,
      locationLevelId: node.levelId,
      levelName: levelNameById.get(node.levelId) ?? "Location",
      children: eligibleChildren,
    };
  }

  return hierarchy.tree
    .filter((root) => depth0Selected.has(root.name))
    .map(build);
}

/**
 * Parses an amount string into a finite non-negative number. Empty / blank /
 * NaN return `null`. Negative or infinite amounts are treated as invalid and
 * also return `null`.
 */
export function parseAmount(raw: string | undefined | null): number | null {
  if (raw === undefined || raw === null) return null;
  const trimmed = String(raw).trim();
  if (trimmed === "") return null;
  const n = Number(trimmed);
  if (!Number.isFinite(n) || n < 0) return null;
  return n;
}

/**
 * For each node in `nodes`, computes the residual that would be auto-filled
 * into a blank node, given a parent total and per-node user values. Returns a
 * map of `locationValueId → effectiveAmount`. Nodes the user filled keep
 * their typed amount; blank nodes share the integer residual
 * `parentTotal - sum(filled)` — distributed as `floor(residual / blanks)` with
 * the leftover (`residual % blanks`) handed out one extra unit at a time to
 * the first blank siblings in tree order. This keeps Σ(children) === parent
 * exactly and never produces fractional cents.
 *
 * Caller decides whether `parentTotal` is the user's typed value or already a
 * computed effective value from a deeper recursion.
 */
export function computeSiblingResidual(
  nodes: BudgetTreeNode[],
  values: Record<string, string>,
  parentTotal: number | null,
): Record<string, number> {
  const result: Record<string, number> = {};
  if (parentTotal === null || nodes.length === 0) {
    for (const n of nodes) {
      const v = parseAmount(values[n.locationValueId]);
      result[n.locationValueId] = v ?? 0;
    }
    return result;
  }

  const filledIds = new Set<string>();
  let filledSum = 0;
  for (const n of nodes) {
    const v = parseAmount(values[n.locationValueId]);
    if (v !== null) {
      filledIds.add(n.locationValueId);
      filledSum += v;
    }
  }
  const blanks = nodes.filter((n) => !filledIds.has(n.locationValueId));
  const residual = Math.max(0, parentTotal - filledSum);
  const blankCount = blanks.length;
  const perBlankFloor = blankCount > 0 ? Math.floor(residual / blankCount) : 0;
  // Leftover units go to the first `remainder` blanks in tree order so the
  // sum is exact and the +1 placement is deterministic for the user.
  const remainder = blankCount > 0 ? residual - perBlankFloor * blankCount : 0;
  const blankIndexById = new Map<string, number>();
  blanks.forEach((b, i) => blankIndexById.set(b.locationValueId, i));

  for (const n of nodes) {
    if (filledIds.has(n.locationValueId)) {
      result[n.locationValueId] = parseAmount(values[n.locationValueId]) ?? 0;
    } else {
      const idx = blankIndexById.get(n.locationValueId) ?? 0;
      result[n.locationValueId] =
        perBlankFloor + (idx < remainder ? 1 : 0);
    }
  }
  return result;
}

/**
 * One overshoot detected during validation: a parent node whose explicitly
 * typed children sum strictly greater than the parent's typed amount.
 *
 * `filledChildNames` lists exactly the children whose typed amounts contribute
 * to the overshoot — so the UI can name the offenders rather than scolding the
 * user generically. Sentinel `__GLOBAL__` parent indicates the per-currency
 * top-level total, in which case the names are the depth-0 root nodes.
 */
export interface BudgetOvershoot {
  parentLocationValueId: string;
  parentLocationValueName: string;
  parentAmount: number;
  childrenSum: number;
  filledChildNames: string[];
}

/**
 * Walks the tree and surfaces every overshoot. The auto-fill residual handles
 * the underflow case (blanks fill in residuals, never negative), so the only
 * structural error is "filled children exceed parent."
 *
 * Roots are validated against `globalTotal` when supplied (the per-currency
 * top-level budget the user typed). When `globalTotal` is `null`, roots are
 * not checked — the user hasn't entered the top-level total yet.
 */
export function findOvershoots(
  nodes: BudgetTreeNode[],
  values: Record<string, string>,
  globalTotal: number | null,
): BudgetOvershoot[] {
  const overshoots: BudgetOvershoot[] = [];

  // Top-level: sum of root nodes must not exceed globalTotal.
  if (globalTotal !== null) {
    let rootFilledSum = 0;
    const filledRoots: string[] = [];
    for (const root of nodes) {
      const v = parseAmount(values[root.locationValueId]);
      if (v !== null) {
        rootFilledSum += v;
        filledRoots.push(root.locationValueName);
      }
    }
    if (filledRoots.length > 0 && rootFilledSum > globalTotal + 0.0001) {
      overshoots.push({
        parentLocationValueId: "__GLOBAL__",
        parentLocationValueName: "Total",
        parentAmount: globalTotal,
        childrenSum: rootFilledSum,
        filledChildNames: filledRoots,
      });
    }
  }

  function walk(node: BudgetTreeNode): void {
    const parentVal = parseAmount(values[node.locationValueId]);
    if (parentVal !== null && node.children.length > 0) {
      let filledSum = 0;
      const filledChildren: string[] = [];
      for (const child of node.children) {
        const v = parseAmount(values[child.locationValueId]);
        if (v !== null) {
          filledSum += v;
          filledChildren.push(child.locationValueName);
        }
      }
      if (filledChildren.length > 0 && filledSum > parentVal + 0.0001) {
        overshoots.push({
          parentLocationValueId: node.locationValueId,
          parentLocationValueName: node.locationValueName,
          parentAmount: parentVal,
          childrenSum: filledSum,
          filledChildNames: filledChildren,
        });
      }
    }
    for (const child of node.children) walk(child);
  }
  for (const root of nodes) walk(root);
  return overshoots;
}

/**
 * Resolves every node in the tree to an effective amount, applying auto-fill
 * residuals top-down. Nodes the user typed keep their value; blank nodes
 * inherit a share of their parent's residual. Returns a map keyed by
 * `locationValueId`.
 *
 * Used by the request mapper to materialize allocations at save time, and
 * (with `globalTotal`) by the UI to display residual placeholders.
 */
export function computeEffectiveAllocations(
  nodes: BudgetTreeNode[],
  values: Record<string, string>,
  globalTotal: number | null,
): Record<string, number> {
  const out: Record<string, number> = {};

  // Roots: residuals come from globalTotal. If globalTotal is null and no
  // root has been typed, every node falls through to 0.
  const rootEffective = computeSiblingResidual(nodes, values, globalTotal);
  for (const [k, v] of Object.entries(rootEffective)) out[k] = v;

  function walk(node: BudgetTreeNode): void {
    if (node.children.length === 0) return;
    const parentEffective = out[node.locationValueId] ?? 0;
    const childEffective = computeSiblingResidual(
      node.children,
      values,
      parentEffective,
    );
    for (const [k, v] of Object.entries(childEffective)) out[k] = v;
    for (const child of node.children) walk(child);
  }
  for (const root of nodes) walk(root);
  return out;
}

/**
 * Summary of how a parent's total breaks down across its visible children.
 * Drives the small caption shown under the per-currency total and under each
 * expanded parent row. Math kept here so the component never reimplements it.
 */
export interface AllocationSummary {
  /** Sum of typed (non-blank) child values. */
  allocatedSum: number;
  /** Parent total minus `allocatedSum`, clamped at 0. */
  residual: number;
  /** Number of blank children that will share the residual. */
  blankCount: number;
  /** Names of blank children in tree order — drives the indicator's "Name +N" copy and the hover-tooltip list. */
  blankNames: string[];
  /** True when every child is typed AND `allocatedSum === parentTotal`. */
  isFullyAllocated: boolean;
  /** True when typed children sum strictly exceeds parent. */
  hasOvershoot: boolean;
  /** Echoed back so the caption can render `$X of $Y`. */
  parentTotal: number;
}

/**
 * Computes the allocation summary for a set of sibling nodes against a parent
 * total. `parentTotal === null` means the user hasn't entered the parent yet
 * — caller should hide the indicator in that case (we still return a
 * well-formed object so consumers don't have to null-check both shapes).
 */
export function summarizeAllocation(
  nodes: BudgetTreeNode[],
  values: Record<string, string>,
  parentTotal: number | null,
): AllocationSummary {
  const total = parentTotal ?? 0;
  let allocatedSum = 0;
  const blankNames: string[] = [];
  for (const n of nodes) {
    const v = parseAmount(values[n.locationValueId]);
    if (v === null) {
      blankNames.push(n.locationValueName);
    } else {
      allocatedSum += v;
    }
  }
  const blankCount = blankNames.length;
  const residual = Math.max(0, total - allocatedSum);
  const hasOvershoot = parentTotal !== null && allocatedSum > total + 0.0001;
  const isFullyAllocated =
    parentTotal !== null &&
    blankCount === 0 &&
    Math.abs(allocatedSum - total) < 0.0001;
  return {
    allocatedSum,
    residual,
    blankCount,
    blankNames,
    isFullyAllocated,
    hasOvershoot,
    parentTotal: total,
  };
}

/**
 * True iff every depth-0 root node has a typed value. Step 4's per-location
 * mode marks the top level as required (asterisk in the UI); children at
 * deeper depths stay optional and auto-fill via the residual. The step gate
 * uses this to keep "Continue" disabled until the user explicitly allocates
 * every root row.
 *
 * Returns `false` for an empty tree — there is nothing to allocate against.
 */
export function areAllRootsFilled(
  nodes: BudgetTreeNode[],
  values: Record<string, string>,
): boolean {
  if (nodes.length === 0) return false;
  return nodes.every(
    (root) => parseAmount(values[root.locationValueId]) !== null,
  );
}

/**
 * Flattens the tree into a list of nodes, depth-first. Convenience for
 * consumers that need every node id (e.g. test assertions).
 */
export function flattenTree(nodes: BudgetTreeNode[]): BudgetTreeNode[] {
  const out: BudgetTreeNode[] = [];
  function visit(n: BudgetTreeNode) {
    out.push(n);
    for (const c of n.children) visit(c);
  }
  for (const n of nodes) visit(n);
  return out;
}
