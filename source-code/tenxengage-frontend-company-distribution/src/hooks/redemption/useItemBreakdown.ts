// Adapted from: src/hooks/redemption/useSegmentBreakdown.ts (TanStack Query pattern)
// shape: contracts/endpoints/redemption-advanced-analytics.yaml → GET /item-breakdown
import { useQuery } from "@tanstack/react-query";
import { getItemBreakdown } from "@/services/redemption-analytics-advanced.service";
import type { AdvancedAnalyticsFilters } from "@/types/redemption-analytics-advanced.types";

/**
 * Fetches the item breakdown for the advanced analytics tab.
 * staleTime: 60_000 — Redis-backed endpoint; 60s FE cache mirrors the BE TTL (AC-4).
 * enabled: fires only when dateFrom and dateTo are set (story FE-2 spec).
 * Query key includes full filters object so all filter combinations cache separately.
 */
export function useItemBreakdown(filters: AdvancedAnalyticsFilters) {
  return useQuery({
    queryKey: ["advanced-analytics", "item-breakdown", filters],
    queryFn: () => getItemBreakdown(filters),
    staleTime: 60_000,
    enabled: !!filters.dateFrom && !!filters.dateTo,
  });
}
