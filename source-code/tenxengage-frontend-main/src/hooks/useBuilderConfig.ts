import { useQuery } from "@tanstack/react-query";
import {
  getBuilderConfig,
  resolveFieldValues,
  getActivityCategories,
} from "@/services/builder-config.service";
import { getClientRoles } from "@/services/permission.service";
import type { FieldValueOption } from "@/types/builder-config.types";
import type { LocationValueResponse } from "@/types/location.types";
import type { ClientRole } from "@/types/permission.types";

export function useBuilderConfig(incentiveType: string | null) {
  return useQuery({
    queryKey: ["builder-config", incentiveType],
    queryFn: () => getBuilderConfig(incentiveType!),
    enabled: !!incentiveType,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Returns location values for a specific level, optionally filtered by the
 * caller's ancestor selections.
 *
 * `ancestorSelections` maps a level's id to the names of values the user has
 * selected at that level. When walking the hierarchy tree, a node at a level
 * with a non-empty ancestor entry is only traversed if its name appears in
 * that entry. Levels not present in the map are traversed freely. This is
 * what makes the walker work for 3+ level hierarchies: a filter at the
 * Country level no longer rejects every Region node during the walk.
 */
export function getLocationValuesForLevel(
  levelId: string | null,
  hierarchy: { tree: LocationValueResponse[] } | undefined,
  ancestorSelections?: Record<string, string[]>,
) {
  if (!levelId || !hierarchy) return [];

  const values: FieldValueOption[] = [];

  function collectValues(nodes: LocationValueResponse[]): void {
    for (const node of nodes) {
      if (node.levelId === levelId) {
        values.push({ value: node.name, label: node.name });
        continue;
      }
      // Gate recursion at this level only if the caller supplied a filter for
      // this level's id. If no entry exists, the node passes through freely.
      const allowed = ancestorSelections?.[node.levelId];
      if (allowed && allowed.length > 0 && !allowed.includes(node.name)) {
        continue;
      }
      if (node.children) collectValues(node.children);
    }
  }

  collectValues(hierarchy.tree);
  return values;
}

/**
 * Map an external `ClientRole[]` list into MultiSelect options keyed on
 * `ClientRole.id` (UUID) with the display name as the visible label.
 *
 * BUG-082 / BUG-020 frontend follow-up: the wire format for ROLE audience
 * rules is the role's UUID (matching what the seeder writes and the
 * backend's eligibility checker parses first). The MultiSelect compares
 * `selected[i]` against `options[i].value` exactly, so the option `value`
 * must be the UUID — not the display name — for the field to round-trip
 * correctly when editing a seeded or any UUID-keyed incentive.
 */
export function mapExternalRolesToOptions(
  roles: ClientRole[],
): FieldValueOption[] {
  return roles
    .filter((r) => r.roleType === "EXTERNAL")
    .map((r) => ({ value: r.id, label: r.name }));
}

export function useExternalRoles() {
  return useQuery({
    queryKey: ["external-roles"],
    queryFn: async (): Promise<FieldValueOption[]> => {
      const roles = await getClientRoles();
      return mapExternalRolesToOptions(roles);
    },
    staleTime: 10 * 60 * 1000,
  });
}

export function useActivityCategories() {
  return useQuery({
    queryKey: ["activity-categories"],
    queryFn: getActivityCategories,
    staleTime: 5 * 60 * 1000,
  });
}

export function useFieldValues(
  fieldId: string | null,
  context?: Record<string, string[]>,
) {
  return useQuery({
    queryKey: ["field-values", fieldId, context],
    queryFn: () => resolveFieldValues(fieldId!, context),
    enabled: !!fieldId,
    staleTime: 5 * 60 * 1000,
  });
}
