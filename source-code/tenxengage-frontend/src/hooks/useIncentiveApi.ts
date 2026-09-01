import { useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type {
  CreateIncentiveRequest,
  UpdateIncentiveRequest,
  UpdateIncentiveStatusRequest,
  IncentiveResponse,
} from "@/types/incentive.types";
import type { PaginatedResponse } from "@/types/api.types";
import * as incentiveService from "@/services/incentive.service";
import type { IncentiveListParams } from "@/services/incentive.service";

export function useIncentives(params?: IncentiveListParams) {
  return useQuery({
    queryKey: ["incentives", params],
    queryFn: () => incentiveService.getIncentives(params),
    staleTime: 0,
  });
}

export function useIncentive(id: string) {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ["incentives", id],
    queryFn: () => incentiveService.getIncentiveById(id),
    enabled: !!id,
  });

  // Enrich list-level cache with computed fields from detail data
  // so cards reflect accurate progress without waiting for backend changes.
  // This runs as a side-effect after render (not inside select) to avoid
  // React hooks ordering errors.
  //
  // The predicate restricts the write to list-shaped caches only — list keys
  // are ["incentives", <params object>] and detail keys are
  // ["incentives", <id string>], so a non-string second segment identifies a
  // list cache. The updater short-circuits when the row isn't present or
  // when the value is already current, preserving array identity so
  // subscribers don't re-render gratuitously.
  const detail = query.data;
  useEffect(() => {
    if (detail?.id == null || detail.trainingRequiredCount == null) return;

    queryClient.setQueriesData<PaginatedResponse<IncentiveResponse>>(
      {
        queryKey: ["incentives"],
        predicate: (q) =>
          q.queryKey.length >= 2 && typeof q.queryKey[1] !== "string",
      },
      (old) => {
        if (!old || !Array.isArray(old.data)) return old;
        const idx = old.data.findIndex((inc) => inc.id === detail.id);
        if (idx === -1) return old;
        const existing = old.data[idx];
        if (
          !existing ||
          existing.trainingRequiredCount === detail.trainingRequiredCount
        ) {
          return old;
        }
        const next = old.data.slice();
        next[idx] = {
          ...existing,
          trainingRequiredCount: detail.trainingRequiredCount,
        };
        return { ...old, data: next };
      },
    );
  }, [detail?.id, detail?.trainingRequiredCount, queryClient]);

  return query;
}

export function useCreateIncentive() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateIncentiveRequest) =>
      incentiveService.createIncentive(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["incentives"] });
    },
  });
}

export function useUpdateIncentive() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateIncentiveRequest }) =>
      incentiveService.updateIncentive(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["incentives"] });
    },
  });
}

export function useDeleteIncentive() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => incentiveService.deleteIncentive(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["incentives"] });
    },
  });
}

export function useUpdateIncentiveStatus() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string;
      data: UpdateIncentiveStatusRequest;
    }) => incentiveService.updateIncentiveStatus(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["incentives"] });
    },
  });
}

export function useSubmitForApproval() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => incentiveService.submitForApproval(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["incentives"] });
    },
  });
}

export function useResendApprovalEmails() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => incentiveService.resendApprovalEmails(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ["incentives", id] });
    },
  });
}

export function useResendApprovalToApprover() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, email }: { id: string; email: string }) =>
      incentiveService.resendApprovalToApprover(id, email),
    onSuccess: (_data, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["incentives", id] });
    },
  });
}

export function useResubmitForApproval() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => incentiveService.resubmitForApproval(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["incentives"] });
    },
  });
}

export function useCloneIncentive() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      name,
      description,
    }: {
      id: string;
      name: string;
      description?: string;
    }) => incentiveService.cloneIncentive(id, name, description),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["incentives"] });
    },
  });
}

export function useGenerateForecast() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => incentiveService.generateForecast(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ["incentives", id] });
    },
  });
}

export function useUploadIncentiveDocuments() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      incentiveId,
      files,
    }: {
      incentiveId: string;
      files: { file: File; category: string }[];
    }) => incentiveService.uploadIncentiveDocuments(incentiveId, files),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["incentives"] });
    },
  });
}
