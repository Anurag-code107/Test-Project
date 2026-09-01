import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createDistribution,
  getDistributableCatalog,
  getDistributableGiftCards,
  getDistribution,
  getDistributions,
  getMyAward,
  getMyAwards,
  getRecipients,
} from "@/services/company-distribution.service";
import type {
  DistributionCatalogItem,
  CreateCompanyDistributionRequest,
  DistributionRail,
} from "@/types/company-distribution.types";

/**
 * Recipients change as sellers link banks or enrol, so this is kept fresh rather than cached hard —
 * a stale "no payout profile" would make an admin exclude someone who is now eligible.
 */
export function useDistributionRecipients(rail: DistributionRail | null) {
  return useQuery({
    queryKey: ["distribution-recipients", rail],
    queryFn: () => getRecipients(rail!),
    enabled: !!rail,
    staleTime: 30 * 1000,
  });
}

/** The gift-card catalog changes rarely, so it can be cached longer than recipients. */
/**
 * The provider's gift-card catalogue, mapped into the shape the picker already renders so the SKU becomes the
 * selection id. Cached for longer than the curated list: it is an external catalogue that barely changes, and
 * it costs a provider round-trip on a miss.
 */
export function useDistributableGiftCards(enabled = true) {
  return useQuery({
    queryKey: ["distribution-gift-cards"],
    queryFn: getDistributableGiftCards,
    enabled,
    staleTime: 30 * 60 * 1000,
    select: (skus): DistributionCatalogItem[] =>
      skus.map((s) => ({
        id: s.sku,
        name: s.rewardName,
        description: s.brandName,
        imageUrl: null,
        providerImageUrl: s.brandImageUrl,
        currencyId: "cash",
        valueType: s.valueType,
        // A FIXED card has one denomination, so both bounds collapse onto the face value — which is what
        // pins the amount field read-only downstream.
        minAmount: (s.valueType === "FIXED" ? s.faceValue : s.minValue) ?? "0",
        maxAmount: s.valueType === "FIXED" ? s.faceValue : s.maxValue,
      })),
  });
}

export function useDistributableCatalog(enabled = true) {
  return useQuery({
    queryKey: ["distribution-catalog"],
    queryFn: getDistributableCatalog,
    enabled,
    staleTime: 5 * 60 * 1000,
  });
}

export function useDistributions(params: {
  rail?: DistributionRail;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: ["distributions", params],
    queryFn: () => getDistributions(params),
    staleTime: 30 * 1000,
  });
}

/**
 * Per-recipient outcomes settle asynchronously after submit, so this polls while anything is still in
 * flight and stops once the distribution reaches a terminal rollup. Without the poll the admin would
 * have to refresh to find out whether their distribution actually paid out.
 */
export function useDistribution(id: string | null) {
  return useQuery({
    queryKey: ["distribution", id],
    queryFn: () => getDistribution(id!),
    enabled: !!id,
    refetchInterval: (query) =>
      query.state.data?.status === "PROCESSING" ? 5000 : false,
  });
}

export function useMyAwards(params: { page?: number; size?: number }) {
  return useQuery({
    queryKey: ["company-awards", params],
    queryFn: () => getMyAwards(params),
    staleTime: 30 * 1000,
  });
}

export function useMyAward(awardId: string | null) {
  return useQuery({
    queryKey: ["company-award", awardId],
    queryFn: () => getMyAward(awardId!),
    enabled: !!awardId,
  });
}

/**
 * Submitting moves money out of the company wallet, so the wallet balance and the history list are both
 * invalidated — leaving a stale balance on screen after a distribution is how an admin overspends.
 */
export function useCreateDistribution(companyId: string | null | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateCompanyDistributionRequest) => createDistribution(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["company-wallet", companyId] });
      queryClient.invalidateQueries({ queryKey: ["distributions"] });
    },
  });
}
