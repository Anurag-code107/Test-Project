// Adapted from: src/hooks/useExpiringSoon.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/useAuth";
import { getBreakage } from "@/services/balanceExpiration.service";
import type { GetBreakageParams } from "@/types/balanceExpiration.types";

/**
 * useBalanceBreakage — fetches the breakage (expired value) report for a date range.
 * queryKey: ['balance-breakage', clientId, { from, to, currencyId, granularity }]
 * staleTime: 5 min — invalidated on manual filter change (refetch).
 * CSV export is a direct download, not a query hook.
 *
 * Anti-pattern guard: clientId uses null (not "") as fallback; enabled guards against
 * unauthenticated fetches (PROJECT-CONTEXT.md — never use "" as truthy clientId fallback).
 */
export function useBalanceBreakage(params: GetBreakageParams) {
  const { user } = useAuth();
  const clientId = user?.clientId ?? null;

  return useQuery({
    queryKey: ["balance-breakage", clientId, params] as const,
    queryFn: () => getBreakage(params),
    staleTime: 5 * 60 * 1000,
    enabled: !!clientId,
    retry: false,
  });
}
