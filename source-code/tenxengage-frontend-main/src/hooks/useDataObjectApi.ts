import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type {
  CreateDataObjectRequest,
  UpdateDataObjectRequest,
  CreateFieldRequest,
  UpdateFieldRequest,
  ConnectorMappingRequest,
  DataObjectDetailResponse,
} from "@/types/data-object.types";
import * as dataObjectService from "@/services/data-object.service";

// Shared root prefix for every cache derived from data-object state. Mutations
// invalidate this root once; TanStack Query's prefix matching cascades to every
// nested key (list, by-id, rule-fields, available-custom-fields, ...). Mirrors
// the convention BUG-029 established in useLocationApi.
export const DATA_OBJECTS_ROOT_KEY = ["data-objects"] as const;

export function useDataObjects() {
  return useQuery({
    queryKey: [...DATA_OBJECTS_ROOT_KEY, "list"] as const,
    queryFn: dataObjectService.getDataObjects,
  });
}

/**
 * Fetches a data object by name (e.g. "Partner Data", "Partner User Data").
 * Uses the data objects list and filters client-side.
 */
export function useDataObjectByName(name: string) {
  const { data: dataObjects, ...rest } = useDataObjects();
  const match = dataObjects?.find((d) => d.name === name);
  const matchId = match?.id;
  const detailQuery = useQuery({
    queryKey: [...DATA_OBJECTS_ROOT_KEY, "by-id", matchId] as const,
    queryFn: () => dataObjectService.getDataObjectById(matchId!),
    enabled: !!matchId,
  });
  return {
    data: detailQuery.data,
    isLoading: rest.isLoading || detailQuery.isLoading,
    error: rest.error || detailQuery.error,
  };
}

export function useDataObject(id: string) {
  return useQuery({
    queryKey: [...DATA_OBJECTS_ROOT_KEY, "by-id", id] as const,
    queryFn: () => dataObjectService.getDataObjectById(id),
    enabled: !!id,
  });
}

export function useCreateDataObject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateDataObjectRequest) =>
      dataObjectService.createDataObject(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: DATA_OBJECTS_ROOT_KEY });
    },
  });
}

export function useUpdateDataObject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateDataObjectRequest }) =>
      dataObjectService.updateDataObject(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: DATA_OBJECTS_ROOT_KEY });
    },
  });
}

export function useDeleteDataObject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => dataObjectService.deleteDataObject(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: DATA_OBJECTS_ROOT_KEY });
    },
  });
}

export function useAddField() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      dataObjectId,
      data,
    }: {
      dataObjectId: string;
      data: CreateFieldRequest;
    }) => dataObjectService.addField(dataObjectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: DATA_OBJECTS_ROOT_KEY });
    },
  });
}

export function useUpdateField() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      dataObjectId,
      fieldId,
      data,
    }: {
      dataObjectId: string;
      fieldId: string;
      data: UpdateFieldRequest;
    }) => dataObjectService.updateField(dataObjectId, fieldId, data),
    onMutate: async ({ dataObjectId, fieldId, data }) => {
      const detailKey = [
        ...DATA_OBJECTS_ROOT_KEY,
        "by-id",
        dataObjectId,
      ] as const;
      await queryClient.cancelQueries({ queryKey: detailKey });
      const previous =
        queryClient.getQueryData<DataObjectDetailResponse>(detailKey);
      if (previous) {
        queryClient.setQueryData<DataObjectDetailResponse>(detailKey, {
          ...previous,
          fields: previous.fields.map((f) =>
            f.id === fieldId ? { ...f, ...data } : f,
          ),
        });
      }
      return { previous };
    },
    onError: (_err, { dataObjectId }, context) => {
      if (context?.previous) {
        queryClient.setQueryData(
          [...DATA_OBJECTS_ROOT_KEY, "by-id", dataObjectId] as const,
          context.previous,
        );
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: DATA_OBJECTS_ROOT_KEY });
    },
  });
}

export function useDeleteField() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      dataObjectId,
      fieldId,
    }: {
      dataObjectId: string;
      fieldId: string;
    }) => dataObjectService.deleteField(dataObjectId, fieldId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: DATA_OBJECTS_ROOT_KEY });
    },
  });
}

export function useSetConnectorMapping() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      dataObjectId,
      data,
    }: {
      dataObjectId: string;
      data: ConnectorMappingRequest;
    }) => dataObjectService.setConnectorMapping(dataObjectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: DATA_OBJECTS_ROOT_KEY });
    },
  });
}

export function useRemoveConnectorMapping() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (dataObjectId: string) =>
      dataObjectService.removeConnectorMapping(dataObjectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: DATA_OBJECTS_ROOT_KEY });
    },
  });
}

export function useRuleFields(dataObjectId?: string, dataObjectName?: string) {
  return useQuery({
    queryKey: [
      ...DATA_OBJECTS_ROOT_KEY,
      "rule-fields",
      dataObjectId,
      dataObjectName,
    ] as const,
    queryFn: () =>
      dataObjectService.getRuleFields(dataObjectId, dataObjectName),
  });
}
