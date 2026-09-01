import { useMemo } from "react";
import { useQueries } from "@tanstack/react-query";
import { getBuilderConfig } from "@/services/builder-config.service";
import type {
  BuilderFieldConfigResponse,
  BuilderSectionConfigResponse,
} from "@/types/builder-config.types";
import type { IncentiveType } from "@/types/incentive.types";

const INCENTIVE_TYPES: IncentiveType[] = [
  "SALES",
  "TRAINING",
  "ACTIVITY",
  "JOURNEY",
];

export interface BuilderFieldReference {
  incentiveType: IncentiveType;
  section: Pick<
    BuilderSectionConfigResponse,
    "id" | "sectionKey" | "displayName"
  >;
  field: BuilderFieldConfigResponse;
}

/**
 * Loads builder configs for all incentive types in parallel and exposes a
 * lookup of every builder field that references a given data-object field id.
 * Used by the "delete data field" dialog to surface impact.
 */
export function useBuilderFieldReferences() {
  const queries = useQueries({
    queries: INCENTIVE_TYPES.map((type) => ({
      queryKey: ["builder-config", type] as const,
      queryFn: () => getBuilderConfig(type),
      staleTime: 5 * 60 * 1000,
    })),
  });

  const isLoading = queries.some((q) => q.isLoading);

  const referencesByDataFieldId = useMemo(() => {
    const map = new Map<string, BuilderFieldReference[]>();
    queries.forEach((q, idx) => {
      const incentiveType = INCENTIVE_TYPES[idx];
      const config = q.data;
      if (!config || !incentiveType) return;
      for (const section of config.sections) {
        for (const field of section.fields) {
          if (!field.dataObjectFieldId) continue;
          const existing = map.get(field.dataObjectFieldId) ?? [];
          existing.push({
            incentiveType,
            section: {
              id: section.id,
              sectionKey: section.sectionKey,
              displayName: section.displayName,
            },
            field,
          });
          map.set(field.dataObjectFieldId, existing);
        }
      }
    });
    return map;
  }, [queries]);

  function findReferences(dataObjectFieldId: string): BuilderFieldReference[] {
    return referencesByDataFieldId.get(dataObjectFieldId) ?? [];
  }

  return { findReferences, isLoading };
}
