// Adapted from: src/hooks/useRedemptionCatalog.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { getPersonalRedemptions } from "@/services/redemption-history/redemption-history.service";
import type { RedemptionHistoryFilters } from "@/types/redemption-history/redemption-history.types";

export function usePersonalRedemptions(
  filters: RedemptionHistoryFilters = {},
  page: number = 0,
  pageSize: number = 20,
) {
  return useQuery({
    queryKey: ['redemption-history', 'personal', { filters, page, pageSize }],
    queryFn: () => getPersonalRedemptions(filters, page, pageSize),
    staleTime: 2 * 60 * 1000,
  });
}
