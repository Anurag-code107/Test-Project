// Adapted from: src/hooks/useRedemptionRequest.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { getReturn } from "@/services/redemption-returns.service";

/**
 * useReturn — fetches a single return by id.
 * isAdmin=false → GET /redemption/returns/{id} (partner path)
 * isAdmin=true  → GET /redemption/admin/returns/{id} (admin path)
 * Query key includes isAdmin to prevent cross-role cache collisions (AC-7).
 */
export function useReturn(id: string | null, isAdmin: boolean = false) {
  return useQuery({
    queryKey: ["return", id, isAdmin],
    queryFn: () => getReturn(id!, isAdmin),
    staleTime: 2 * 60 * 1000,
    enabled: !!id,
    retry: false, // drawer/detail-panel: 404 must surface immediately, not after 3 retries
  });
}
