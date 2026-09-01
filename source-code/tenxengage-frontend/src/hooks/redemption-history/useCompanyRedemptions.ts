// Adapted from: src/hooks/redemption-history/usePersonalRedemptions.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { getCompanyRedemptions } from "@/services/redemption-history/redemption-history.service";
import type { RedemptionHistoryFilters } from "@/types/redemption-history/redemption-history.types";

export function useCompanyRedemptions(
  filters: RedemptionHistoryFilters = {},
  page: number = 0,
  pageSize: number = 20,
) {
  return useQuery({
    queryKey: ['redemption-history', 'company', { filters, page, pageSize }],
    queryFn: () => getCompanyRedemptions(filters, page, pageSize),
    staleTime: 2 * 60 * 1000,
  });
}
