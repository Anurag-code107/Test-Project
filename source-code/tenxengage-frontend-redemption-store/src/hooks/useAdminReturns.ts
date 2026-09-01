// Adapted from: src/hooks/useMyReturns.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/useAuth";
import { getAdminReturns } from "@/services/redemption-returns.service";
import type { AdminReturnsFilters } from "@/types/redemption-returns.types";

/**
 * useAdminReturns — paginates the admin return review queue.
 * Query key includes clientId (from auth) and filters so every
 * distinct filter combination has its own cache entry.
 * staleTime: 2 min — return status can change via async Xoxoday webhook.
 * Invalidated by useApproveReturn, useRejectReturn, useResolveTimedOutReturn.
 *
 * clientId is read from auth context (not a param) — tenantId resolved
 * server-side from the JWT; the query key uses it for cache partitioning.
 */
export function useAdminReturns(filters?: AdminReturnsFilters) {
  const { user } = useAuth();
  const clientId = user?.clientId ?? "";

  return useQuery({
    queryKey: ["admin-returns", clientId, filters] as const,
    queryFn: () => getAdminReturns(filters),
    staleTime: 2 * 60 * 1000, // 2 min — async webhook can change status
    enabled: !!clientId,
    retry: false,
  });
}
