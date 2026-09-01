// Adapted from: src/hooks/useRedemptionCatalog.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { getRedemptionDetail } from "@/services/redemption-history/redemption-history.service";

export function useRedemptionDetail(id: string | null) {
  return useQuery({
    queryKey: ['redemption-history', 'detail', id],
    queryFn: () => getRedemptionDetail(id!),
    staleTime: 5 * 60 * 1000,
    enabled: id !== null,
  });
}
