// Adapted from: src/hooks/redemption/useApprovalQueue.ts (TanStack Query pattern)
// shape: contracts/endpoints/redemption-advanced-analytics.yaml → GET /refresh-status
import { useQuery } from "@tanstack/react-query";
import { getRefreshStatus } from "@/services/redemption-analytics-advanced.service";

/**
 * Polls /advanced/refresh-status every 5 minutes.
 * staleTime: 0 — always re-fetch on mount so staleness check is live.
 * refetchInterval: 300_000 — poll every 5 minutes (AC-7).
 * When /refresh-status errors, data is undefined — callers should treat this as
 * banner-suppressed (silent fail, see US-05 negative path).
 */
export function useRefreshStatus() {
  return useQuery({
    queryKey: ["advanced-analytics", "refresh-status"],
    queryFn: getRefreshStatus,
    staleTime: 0,
    refetchInterval: 300_000,
    // Silent fail: do not surface error state to the consumer — AC-7 specifies
    // that /refresh-status failure suppresses the banner rather than showing an error.
    retry: false,
  });
}
