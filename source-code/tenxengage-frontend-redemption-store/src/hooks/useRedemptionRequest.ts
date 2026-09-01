// Adapted from: src/hooks/useRedemptionCatalog.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { getRedemptionRequest, getRedemptionRequests } from "@/services/redemption-flow.service";
import type { RedemptionRequestListParams } from "@/types/redemption-flow.types";

export function useRedemptionRequest(id: string) {
  return useQuery({
    queryKey: ["redemption-request", id],
    queryFn: () => getRedemptionRequest(id),
    staleTime: 30 * 1000,
    enabled: !!id,
  });
}

export function useRedemptionRequests(params?: RedemptionRequestListParams) {
  return useQuery({
    queryKey: ["redemption-requests", params],
    queryFn: () => getRedemptionRequests(params),
    staleTime: 60 * 1000,
  });
}
