// Adapted from: src/hooks/useRedemptionRequest.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/useAuth";
import { getSummary } from "@/services/redemption-analytics.service";

function formatLocalDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function defaultDateFrom(): string {
  const d = new Date();
  d.setDate(d.getDate() - 30);
  return formatLocalDate(d);
}

function defaultDateTo(): string {
  return formatLocalDate(new Date());
}

export function useRedemptionAnalytics(
  dateFrom?: string,
  dateTo?: string,
) {
  const { user } = useAuth();
  const clientId = user?.clientId ?? null;

  const resolvedFrom = dateFrom ?? defaultDateFrom();
  const resolvedTo = dateTo ?? defaultDateTo();

  return useQuery({
    queryKey: ["redemption-analytics", { clientId, dateFrom: resolvedFrom, dateTo: resolvedTo }],
    queryFn: () => getSummary(resolvedFrom, resolvedTo),
    staleTime: 60 * 1000,
    gcTime: 5 * 60 * 1000,
    enabled: !!clientId,
    retry: false,
  });
}
