// Adapted from: src/hooks/useRedemptionRequest.ts (TanStack Query pattern)
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { approve, reject } from "@/services/redemption/redemption-approval.service";

export function useApproveRedemption() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (redemptionId: string) => approve(redemptionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approval-queue'] });
    },
  });
}

export function useRejectRedemption() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      redemptionId,
      rejectionReason,
    }: {
      redemptionId: string;
      rejectionReason: string;
    }) => reject(redemptionId, { rejectionReason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approval-queue'] });
    },
  });
}
