import {
  useQuery,
  useMutation,
  useQueryClient,
  keepPreviousData,
} from "@tanstack/react-query";
import type {
  ClaimListParams,
  RewardTransactionListParams,
} from "@/types/claim.types";
import * as claimService from "@/services/claim.service";

export function useClaims(params?: ClaimListParams) {
  return useQuery({
    queryKey: ["claims", "list", params],
    queryFn: () => claimService.getClaims(params),
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
}

export function useClaimDetail(id: string | null) {
  return useQuery({
    queryKey: ["claims", "detail", id],
    queryFn: () => claimService.getClaimDetail(id!),
    enabled: !!id,
  });
}

export function useClaimDeal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => claimService.claimDeal(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["claims"] });
      queryClient.invalidateQueries({ queryKey: ["reward-balances"] });
      queryClient.invalidateQueries({ queryKey: ["reward-transactions"] });
    },
  });
}

export function useUnclaimDeal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, comment }: { id: string; comment: string }) =>
      claimService.unclaimDeal(id, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["claims"] });
      queryClient.invalidateQueries({ queryKey: ["reward-balances"] });
      queryClient.invalidateQueries({ queryKey: ["reward-transactions"] });
    },
  });
}

export function useUpdateClaim() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string;
      data: {
        rewardAdjustments?: Record<string, string>;
        statusChange?: string;
        comment: string;
      };
    }) => claimService.updateClaim(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["claims"] });
      queryClient.invalidateQueries({ queryKey: ["reward-balances"] });
      queryClient.invalidateQueries({ queryKey: ["reward-transactions"] });
    },
  });
}

export function useClaimSummary(
  params?: Omit<ClaimListParams, "page" | "size">,
) {
  return useQuery({
    queryKey: ["claims", "summary", params],
    queryFn: () => claimService.getClaimSummary(params),
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
}

export function useRewardBalances() {
  return useQuery({
    queryKey: ["reward-balances"],
    queryFn: () => claimService.getRewardBalances(),
  });
}

export function useUserRewardBalances(userId: string | null) {
  return useQuery({
    queryKey: ["reward-balances", userId],
    queryFn: () => claimService.getUserRewardBalances(userId!),
    enabled: !!userId,
  });
}

export function useRewardTransactions(params?: RewardTransactionListParams) {
  return useQuery({
    queryKey: ["reward-transactions", params],
    queryFn: () => claimService.getRewardTransactions(params),
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
}

export function useUserRewardTransactions(
  userId: string | null,
  params?: RewardTransactionListParams,
) {
  return useQuery({
    queryKey: ["reward-transactions", userId, params],
    queryFn: () => claimService.getUserRewardTransactions(userId!, params),
    enabled: !!userId,
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
}
