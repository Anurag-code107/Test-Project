import type {
  LocationFilterLevel,
  LocationFilterOptionsResponse,
} from "@/types/location.types";

/** Sentinel emitted when the user clears the filter entirely. */
export const GLOBAL_VALUE = "GLOBAL";

export interface FlattenedValue {
  id: string;
  name: string;
  parentId: string | null;
  levelId: string;
}

/**
 * Levels eligible for rendering: non-empty values arrays, preserving the
 * backend's depth-ascending order.
 */
export function getVisibleLevels(
  filterOptions: LocationFilterOptionsResponse | undefined,
): LocationFilterLevel[] {
  if (!filterOptions?.levels?.length) return [];
  return filterOptions.levels.filter((level) => level.values.length > 0);
}

/**
 * Flatten every visible level's values into one list. Any value whose
 * original `parentId` points at a value NOT in the visible set is re-parented
 * to `null` so orphaned subtrees render as roots instead of disappearing.
 */
export function flattenVisibleValues(
  filterOptions: LocationFilterOptionsResponse | undefined,
): FlattenedValue[] {
  const levels = getVisibleLevels(filterOptions);
  if (levels.length === 0) return [];
  const visibleIds = new Set(
    levels.flatMap((level) => level.values.map((v) => v.id)),
  );
  return levels.flatMap((level) =>
    level.values.map<FlattenedValue>((v) => ({
      id: v.id,
      name: v.name,
      parentId: v.parentId && visibleIds.has(v.parentId) ? v.parentId : null,
      levelId: level.levelId,
    })),
  );
}

/** Direct children of a given parent (use `null` for roots), sorted by name. */
export function getChildren(
  parentId: string | null,
  values: FlattenedValue[],
): FlattenedValue[] {
  return values
    .filter((v) => v.parentId === parentId)
    .sort((a, b) => a.name.localeCompare(b.name));
}

/** Walk up the parent chain; returns ancestor ids in deepest-first order. */
export function getAncestorIds(
  valueId: string,
  values: FlattenedValue[],
): string[] {
  const byId = new Map(values.map((v) => [v.id, v]));
  const ancestors: string[] = [];
  let current = byId.get(valueId);
  const seen = new Set<string>();
  while (current?.parentId && !seen.has(current.parentId)) {
    seen.add(current.parentId);
    ancestors.push(current.parentId);
    current = byId.get(current.parentId);
  }
  return ancestors;
}

export function getValueName(
  valueId: string,
  values: FlattenedValue[],
): string | null {
  return values.find((v) => v.id === valueId)?.name ?? null;
}

/**
 * Precompute leaf-descendant count per node. A leaf counts as 1; an internal
 * node's count is the sum of its leaf descendants. Used to render the "11"
 * / "5" badges next to each row without recomputing per render.
 */
export function buildLeafCountMap(
  values: FlattenedValue[],
): Map<string, number> {
  const childrenByParent = new Map<string | null, FlattenedValue[]>();
  for (const v of values) {
    const bucket = childrenByParent.get(v.parentId) ?? [];
    bucket.push(v);
    childrenByParent.set(v.parentId, bucket);
  }
  const counts = new Map<string, number>();
  function compute(id: string): number {
    const cached = counts.get(id);
    if (cached !== undefined) return cached;
    const children = childrenByParent.get(id) ?? [];
    const n =
      children.length === 0
        ? 1
        : children.reduce((sum, c) => sum + compute(c.id), 0);
    counts.set(id, n);
    return n;
  }
  for (const v of values) compute(v.id);
  return counts;
}

/**
 * Given a search query, return the set of value ids that either match the
 * query (substring, case-insensitive) or are an ancestor of a match. Empty
 * query → returns null, signaling "no search filter — render everything".
 */
export function computeVisibleIds(
  query: string,
  values: FlattenedValue[],
): Set<string> | null {
  const q = query.trim().toLowerCase();
  if (!q) return null;
  const matches = values.filter((v) => v.name.toLowerCase().includes(q));
  const visible = new Set<string>();
  for (const m of matches) {
    visible.add(m.id);
    for (const a of getAncestorIds(m.id, values)) visible.add(a);
  }
  return visible;
}
