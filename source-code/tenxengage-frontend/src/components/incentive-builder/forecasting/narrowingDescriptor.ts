import type {
  LocationHierarchyResponse,
  LocationValueResponse,
} from "@/types/location.types";

export type NarrowingDescriptor =
  | {
      kind: "region-narrowed";
      deeperPicks: { levelName: string; names: string[] }[];
    }
  | {
      kind: "global-narrowed";
      narrowedRegions: string[];
    }
  | null;

/**
 * Derives the AI Forecasting "narrowed scope" chip descriptor from the user's
 * Participant Eligibility selections. Returns `null` when there's nothing to
 * surface — either the hierarchy isn't loaded yet or the selections are
 * region-only (no deeper levels picked).
 *
 * For a per-region view (e.g. `viewMode === "Americas"`), only the deeper-level
 * selections that fall under that region are returned, grouped by level. For
 * the GLOBAL view, returns the names of regions that have *any* deeper-level
 * selection — kept short so the line doesn't blow out.
 */
export function getNarrowingDescriptor(
  viewMode: "GLOBAL" | string,
  locationSelections: Record<string, string[]>,
  hierarchy: LocationHierarchyResponse | undefined,
): NarrowingDescriptor {
  if (!hierarchy?.levels?.length || !hierarchy.tree) return null;

  const { levels, tree } = hierarchy;
  const depth0LevelId = levels[0]?.id;
  if (!depth0LevelId) return null;

  if (viewMode !== "GLOBAL") {
    const regionNode = tree.find((n) => n.name === viewMode);
    if (!regionNode) return null;

    const descendantsByLevel = collectDescendantsByLevel(regionNode);
    const deeperPicks: { levelName: string; names: string[] }[] = [];
    for (const lvl of levels) {
      if (lvl.id === depth0LevelId) continue;
      const selected = locationSelections[lvl.id] ?? [];
      const inThisRegion = descendantsByLevel.get(lvl.id) ?? new Set<string>();
      const matched = selected.filter((n) => inThisRegion.has(n));
      if (matched.length > 0) {
        deeperPicks.push({ levelName: lvl.name, names: matched });
      }
    }
    return deeperPicks.length > 0
      ? { kind: "region-narrowed", deeperPicks }
      : null;
  }

  // GLOBAL view — list any region that has at least one deeper pick.
  const selectedRegions = locationSelections[depth0LevelId] ?? [];
  const narrowedRegions: string[] = [];
  for (const regionName of selectedRegions) {
    const regionNode = tree.find((n) => n.name === regionName);
    if (!regionNode) continue;
    const descendantsByLevel = collectDescendantsByLevel(regionNode);
    const hasDeeper = levels.some((lvl) => {
      if (lvl.id === depth0LevelId) return false;
      const selected = locationSelections[lvl.id] ?? [];
      const inThisRegion = descendantsByLevel.get(lvl.id) ?? new Set<string>();
      return selected.some((n) => inThisRegion.has(n));
    });
    if (hasDeeper) narrowedRegions.push(regionName);
  }
  return narrowedRegions.length > 0
    ? { kind: "global-narrowed", narrowedRegions }
    : null;
}

/** Group every descendant value name by its level id under the given node. */
function collectDescendantsByLevel(
  node: LocationValueResponse,
): Map<string, Set<string>> {
  const out = new Map<string, Set<string>>();
  walk(node, out);
  return out;
}

function walk(
  node: LocationValueResponse,
  out: Map<string, Set<string>>,
): void {
  for (const child of node.children ?? []) {
    const set = out.get(child.levelId) ?? new Set<string>();
    set.add(child.name);
    out.set(child.levelId, set);
    walk(child, out);
  }
}
