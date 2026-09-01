// Adapted from: src/hooks/redemption/useItemBreakdown.ts (TanStack Query pattern)
// shape: contracts/endpoints/redemption-advanced-analytics.yaml → GET /trend
import { useQuery } from "@tanstack/react-query";
import { getRedemptionTrend } from "@/services/redemption-analytics-advanced.service";

/**
 * Fetches the redemption rate trend for the advanced analytics tab.
 * staleTime: 60_000 — Redis-backed endpoint; 60s FE cache mirrors the BE TTL (AC-3).
 * Query key includes dateFrom and dateTo so each window caches separately.
 * No region/role filter — trend is tenant-wide per spec FR-08.4 and Out of Scope.
 */
export function useRedemptionTrend(dateFrom: string, dateTo: string) {
  return useQuery({
    queryKey: ["redemption-analytics-advanced", "trend", dateFrom, dateTo],
    queryFn: () => getRedemptionTrend(dateFrom, dateTo),
    staleTime: 60_000,
    enabled: !!dateFrom && !!dateTo,
  });
}
