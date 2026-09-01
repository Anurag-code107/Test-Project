// Adapted from: src/hooks/useAdminReturns.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/useAuth";
import { getExpiringSoon } from "@/services/balanceExpiration.service";
import type { GetExpiringSoonParams } from "@/types/balanceExpiration.types";

/**
 * useExpiringSoon — aggregate preview of balances approaching expiry.
 * queryKey: ['balance-expiring-soon', clientId, params]
 * staleTime: 1 min — preview updates as batch runs; invalidated by useUpsertBalanceExpirationPolicy.
 */
export function useExpiringSoon(params?: GetExpiringSoonParams) {
  const { user } = useAuth();
  const clientId = user?.clientId ?? null;

  return useQuery({
    queryKey: ["balance-expiring-soon", clientId, params] as const,
    queryFn: () => getExpiringSoon(params),
    staleTime: 1 * 60 * 1000,
    enabled: !!clientId,
    retry: false,
  });
}
