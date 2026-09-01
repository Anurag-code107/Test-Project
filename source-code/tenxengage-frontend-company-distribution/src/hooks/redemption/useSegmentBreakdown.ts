// Adapted from: src/hooks/redemption/useApprovalQueue.ts (TanStack Query pattern)
// shape: contracts/endpoints/redemption-advanced-analytics.yaml → GET /segment-breakdown
import { useQuery } from "@tanstack/react-query";
import { getSegmentBreakdown } from "@/services/redemption-analytics-advanced.service";
import type { AdvancedAnalyticsFilters } from "@/types/redemption-analytics-advanced.types";

/**
 * Fetches the segment breakdown for the advanced analytics tab.
 * staleTime: 60_000 — Redis-backed endpoint; 60s FE cache mirrors the BE TTL.
 * Used by AdvancedAnalyticsTab to determine whether region/role dropdowns
 * should be disabled (empty segments array → isSegmentDataEmpty=true).
 */
export function useSegmentBreakdown(filters: AdvancedAnalyticsFilters) {
  return useQuery({
    queryKey: ["advanced-analytics", "segment-breakdown", filters],
    queryFn: () => getSegmentBreakdown(filters),
    staleTime: 60_000,
  });
}
