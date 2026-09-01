import { useMemo } from "react";
import { useQueries, useQuery } from "@tanstack/react-query";
import {
  getDataObjects,
  getDataObjectById,
} from "@/services/data-object.service";
import { DATA_OBJECTS_ROOT_KEY } from "./useDataObjectApi";
import type { BuilderFieldConfigResponse } from "@/types/builder-config.types";

/**
 * Cross-references Builder Config fields against Manage Data fields.
 * A builder field is "invalid" when its dataObjectFieldId points to a
 * data field that no longer exists (the underlying data field was deleted).
 */
export function useDataFieldValidity() {
  const listQuery = useQuery({
    queryKey: [...DATA_OBJECTS_ROOT_KEY, "list"] as const,
    queryFn: getDataObjects,
  });

  const detailQueries = useQueries({
    queries: (listQuery.data ?? []).map((obj) => ({
      queryKey: [...DATA_OBJECTS_ROOT_KEY, "by-id", obj.id] as const,
      queryFn: () => getDataObjectById(obj.id),
    })),
  });

  const isLoading =
    listQuery.isLoading || detailQueries.some((q) => q.isLoading);

  const validFieldIds = useMemo(() => {
    const set = new Set<string>();
    for (const q of detailQueries) {
      if (q.data) {
        for (const f of q.data.fields) set.add(f.id);
      }
    }
    return set;
  }, [detailQueries]);

  function isBuilderFieldInvalid(field: BuilderFieldConfigResponse): boolean {
    if (!field.dataObjectFieldId) return false;
    if (isLoading) return false;
    return !validFieldIds.has(field.dataObjectFieldId);
  }

  return { validFieldIds, isBuilderFieldInvalid, isLoading };
}
