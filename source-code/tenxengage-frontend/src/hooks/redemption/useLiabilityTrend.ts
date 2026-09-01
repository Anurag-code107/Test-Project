// shape: contracts/endpoints/redemption-advanced-analytics.yaml → GET /liability-trend
// Covers: AC-1, AC-6 (story US-06)
import { useQuery } from "@tanstack/react-query";
import { getLiabilityTrend } from "@/services/redemption-analytics-advanced.service";

/**
 * Fetches the unredeemed balance liability trend for the advanced analytics tab.
 * staleTime: 60_000 — Redis-backed endpoint; 60s FE cache mirrors the BE TTL.
 * Query key includes dateFrom and dateTo so each window caches separately.
 * Spec: FR-08.5, AC-1.
 */
export function useLiabilityTrend(dateFrom: string, dateTo: string) {
  return useQuery({
    queryKey: ["redemption-analytics-advanced", "liability-trend", dateFrom, dateTo],
    queryFn: () => getLiabilityTrend(dateFrom, dateTo),
    staleTime: 60_000,
    enabled: !!dateFrom && !!dateTo,
  });
}
