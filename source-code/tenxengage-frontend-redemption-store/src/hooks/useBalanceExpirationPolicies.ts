// Adapted from: src/hooks/useAdminReturns.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/useAuth";
import { getPolicies } from "@/services/balanceExpiration.service";

/**
 * useBalanceExpirationPolicies — fetches all per-currency expiration policies for the tenant.
 * queryKey: ['balance-expiration-policies', clientId]
 * staleTime: 5 min — policy config is low-churn; invalidated by useUpsertBalanceExpirationPolicy.
 * retry: false — drawer/detail reads must not retry; this is a settings read.
 */
export function useBalanceExpirationPolicies() {
  const { user } = useAuth();
  const clientId = user?.clientId ?? null;

  return useQuery({
    queryKey: ["balance-expiration-policies", clientId] as const,
    queryFn: getPolicies,
    staleTime: 5 * 60 * 1000,
    enabled: !!clientId,
    retry: false,
  });
}
