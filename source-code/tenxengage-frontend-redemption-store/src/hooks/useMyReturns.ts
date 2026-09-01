// Adapted from: src/hooks/useRedemptionRequest.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/useAuth";
import { getMyReturns } from "@/services/redemption-returns.service";
import type { MyReturnsFilters } from "@/types/redemption-returns.types";

export function useMyReturns(filters?: MyReturnsFilters) {
  const { user } = useAuth();
  const userId = user?.id ?? "";

  return useQuery({
    queryKey: ["my-returns", userId, filters],
    queryFn: () => getMyReturns(filters),
    staleTime: 2 * 60 * 1000, // 2 min — async webhook can change status
    enabled: !!userId,
    retry: false,
  });
}
