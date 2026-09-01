import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { SaveFiscalYearConfigRequest } from "@/types/fiscal-year.types";
import * as fiscalYearService from "@/services/fiscal-year.service";

const QUERY_KEY = ["fiscal-year-configs"] as const;

export function useFiscalYearConfigs() {
  return useQuery({
    queryKey: QUERY_KEY,
    queryFn: fiscalYearService.getFiscalYearConfigs,
  });
}

export function useFiscalYearLabels() {
  return useQuery({
    queryKey: [...QUERY_KEY, "labels"],
    queryFn: fiscalYearService.getFiscalYearLabels,
  });
}

export function useCurrentFiscalConfig() {
  return useQuery({
    queryKey: [...QUERY_KEY, "current"],
    queryFn: fiscalYearService.getCurrentFiscalYearConfig,
  });
}

export function useCreateFiscalYearConfig() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: SaveFiscalYearConfigRequest) =>
      fiscalYearService.createFiscalYearConfig(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });
}

export function useUpdateFiscalYearConfig() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string;
      data: SaveFiscalYearConfigRequest;
    }) => fiscalYearService.updateFiscalYearConfig(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });
}

export function useDeleteFiscalYearConfig() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => fiscalYearService.deleteFiscalYearConfig(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });
}
