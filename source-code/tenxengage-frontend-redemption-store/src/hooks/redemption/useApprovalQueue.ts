// Adapted from: src/hooks/useRedemptionRequest.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/useAuth";
import { getApprovalQueue } from "@/services/redemption/redemption-approval.service";
import type { ApprovalQueueFilters } from "@/types/redemption/redemption.types";

export function useApprovalQueue(filters: ApprovalQueueFilters = {}) {
  const { user } = useAuth();
  const clientId = user?.clientId ?? null;

  return useQuery({
    queryKey: ['approval-queue', { clientId, ...filters }],
    queryFn: () => getApprovalQueue(filters),
    staleTime: 5 * 60 * 1000,
  });
}
