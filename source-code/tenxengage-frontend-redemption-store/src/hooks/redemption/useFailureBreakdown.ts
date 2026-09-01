// shape: contracts/endpoints/redemption-advanced-analytics.yaml → GET /failure-breakdown
// Covers: AC-1, AC-3, AC-4 (story US-07)
import { useQuery } from "@tanstack/react-query";
import { getFailureBreakdown } from "@/services/redemption-analytics-advanced.service";
import type { AdvancedAnalyticsFilters } from "@/types/redemption-analytics-advanced.types";

/**
 * Fetches the failure mode breakdown for the advanced analytics tab.
 * staleTime: 60_000 — Redis-backed endpoint; 60s FE cache mirrors the BE TTL.
 * Query key includes all filter params so each combination caches separately.
 * Spec: FR-08.7, AC-1, AC-3.
 */
export function useFailureBreakdown(filters: AdvancedAnalyticsFilters) {
  return useQuery({
    queryKey: ["redemption-analytics-advanced", "failure-breakdown", filters],
    queryFn: () => getFailureBreakdown(filters),
    staleTime: 60_000,
    enabled: !!filters.dateFrom && !!filters.dateTo,
  });
}
