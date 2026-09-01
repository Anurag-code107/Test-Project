// Adapted from: src/hooks/redemption-history/useCompanyRedemptions.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { getTenantRedemptions } from "@/services/redemption-history/redemption-history.service";
import type { RedemptionAdminHistoryFilters } from "@/types/redemption-history/redemption-history.types";

export function useTenantRedemptions(filters: RedemptionAdminHistoryFilters = {}, page = 0, pageSize = 20) {
  return useQuery({
    queryKey: ['redemption-history', 'all-tenant', { filters, page, pageSize }],
    queryFn: () => getTenantRedemptions(filters, page, pageSize),
    staleTime: 2 * 60 * 1000,
  });
}
