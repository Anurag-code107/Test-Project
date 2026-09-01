// Adapted from: src/hooks/redemption/useSegmentBreakdown.ts (same domain, same TanStack Query pattern)
// shape: contracts/endpoints/redemption-advanced-analytics.yaml -> GET /time-to-first-redemption
import { useQuery } from "@tanstack/react-query";
import { getTimeToFirstRedemption } from "@/services/redemption-analytics-advanced.service";
import type { AdvancedAnalyticsFilters } from "@/types/redemption-analytics-advanced.types";

/**
 * Fetches the time-to-first-redemption breakdown for the advanced analytics tab.
 * staleTime: 60_000 -- Redis-backed endpoint; 60s FE cache mirrors the BE TTL.
 * Query key uses 'ttfr' alias per technical.md Hook Specs.
 */
export function useTimeToFirstRedemption(filters: AdvancedAnalyticsFilters) {
  return useQuery({
    queryKey: ["advanced-analytics", "ttfr", filters],
    queryFn: () => getTimeToFirstRedemption(filters),
    staleTime: 60_000,
  });
}
