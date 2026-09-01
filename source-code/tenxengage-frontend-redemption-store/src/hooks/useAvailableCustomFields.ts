import { useQuery } from "@tanstack/react-query";
import {
  getDataObjects,
  getDataObjectById,
} from "@/services/data-object.service";
import { DATA_OBJECTS_ROOT_KEY } from "@/hooks/useDataObjectApi";
import type { DataObjectResponse } from "@/types/data-object.types";

export interface AvailableFieldOption {
  dataObjectId: string;
  dataObjectName: string;
  fieldId: string;
  fieldName: string;
  fieldDescription: string | null;
  dataType: string; // TEXT, NUMBER, DATE, LIST, BOOLEAN, CURRENCY
}

function getRelevantDataObjects(
  sectionKey: string,
  incentiveType: string | null,
): string[] {
  if (sectionKey === "audience") return ["Partner Data", "Partner User Data"];
  if (sectionKey === "criteria" && incentiveType === "TRAINING")
    return ["Training Data"];
  return [];
}

export function useAvailableCustomFields(
  sectionKey: string,
  incentiveType: string | null,
  existingFieldKeys?: string[],
): { data: AvailableFieldOption[]; isLoading: boolean } {
  const query = useQuery({
    queryKey: [
      ...DATA_OBJECTS_ROOT_KEY,
      "available-custom-fields",
      sectionKey,
      incentiveType,
    ] as const,
    queryFn: async () => {
      const relevantNames = getRelevantDataObjects(sectionKey, incentiveType);
      if (relevantNames.length === 0) return [];

      const allDataObjects = await getDataObjects();

      const matching = allDataObjects.filter((obj: DataObjectResponse) =>
        relevantNames.includes(obj.name),
      );

      const details = await Promise.all(
        matching.map((obj) => getDataObjectById(obj.id)),
      );

      const options: AvailableFieldOption[] = [];
      for (const detail of details) {
        for (const field of detail.fields) {
          options.push({
            dataObjectId: detail.id,
            dataObjectName: detail.name,
            fieldId: field.id,
            fieldName: field.name,
            fieldDescription: field.description,
            dataType: field.dataType,
          });
        }
      }

      return options;
    },
    staleTime: 5 * 60 * 1000,
  });

  const allOptions = query.data ?? [];

  const filtered =
    existingFieldKeys && existingFieldKeys.length > 0
      ? allOptions.filter(
          (opt) => !existingFieldKeys.includes(toKebabCase(opt.fieldName)),
        )
      : allOptions;

  return {
    data: filtered,
    isLoading: query.isLoading,
  };
}

function toKebabCase(str: string): string {
  return str
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}
