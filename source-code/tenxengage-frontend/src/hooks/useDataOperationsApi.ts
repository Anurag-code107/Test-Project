import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { UpdateSyncScheduleRequest } from "@/types/data-operations.types";
import * as dataOpsService from "@/services/data-operations.service";

export function useUploadHistory(dataObjectId: string) {
  return useQuery({
    queryKey: ["upload-history", dataObjectId],
    queryFn: () => dataOpsService.getUploadHistory(dataObjectId),
    enabled: !!dataObjectId,
  });
}

export function useUploadFile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      dataObjectId,
      file,
    }: {
      dataObjectId: string;
      file: File;
    }) => dataOpsService.uploadFile(dataObjectId, file),
    onSuccess: (_data, { dataObjectId }) => {
      queryClient.invalidateQueries({
        queryKey: ["upload-history", dataObjectId],
      });
    },
  });
}

export function useDownloadTemplate() {
  return useMutation({
    mutationFn: (dataObjectId: string) =>
      dataOpsService.downloadTemplate(dataObjectId),
  });
}

export function useConnectorPull() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (dataObjectId: string) =>
      dataOpsService.triggerConnectorPull(dataObjectId),
    onSuccess: (_data, dataObjectId) => {
      queryClient.invalidateQueries({
        queryKey: ["upload-history", dataObjectId],
      });
    },
  });
}

export function useTaggingHistory() {
  return useQuery({
    queryKey: ["tagging-history"],
    queryFn: dataOpsService.getTaggingHistory,
  });
}

export function useRunTaggingJob() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: dataOpsService.triggerTaggingJob,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tagging-history"] });
    },
  });
}

export function useSyncSchedule(dataObjectId: string) {
  return useQuery({
    queryKey: ["sync-schedule", dataObjectId],
    queryFn: () => dataOpsService.getSyncSchedule(dataObjectId),
    enabled: !!dataObjectId,
  });
}

export function useUpdateSyncSchedule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      dataObjectId,
      data,
    }: {
      dataObjectId: string;
      data: UpdateSyncScheduleRequest;
    }) => dataOpsService.updateSyncSchedule(dataObjectId, data),
    onSuccess: (_data, { dataObjectId }) => {
      queryClient.invalidateQueries({
        queryKey: ["sync-schedule", dataObjectId],
      });
    },
  });
}
