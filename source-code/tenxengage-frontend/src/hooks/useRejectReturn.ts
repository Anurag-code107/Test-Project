// Adapted from: src/hooks/useCancelReturn.ts (TanStack Query pattern)
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { isAxiosError } from "axios";
import { rejectReturn } from "@/services/redemption-returns.service";
import { useAuth } from "@/hooks/useAuth";

/**
 * useRejectReturn — admin reject mutation.
 * On success: invalidates ['admin-returns', clientId] and ['return', id, true].
 */
export function useRejectReturn() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const clientId = user?.clientId ?? "";

  return useMutation({
    mutationFn: ({ id, rejectionReason }: { id: string; rejectionReason: string }) =>
      rejectReturn(id, rejectionReason),
    onSuccess: (_data, { id }) => {
      // Invalidate admin queue list
      queryClient.invalidateQueries({ queryKey: ["admin-returns", clientId] });
      // Invalidate the specific return detail (admin view)
      queryClient.invalidateQueries({ queryKey: ["return", id, true] });
      toast.success("Return request rejected");
    },
    onError: (error) => {
      if (isAxiosError(error) && error.response?.status === 409) {
        toast.error("This return was updated concurrently. Please refresh and try again.");
        return;
      }
      if (isAxiosError(error) && error.response?.status === 400) {
        toast.error("Rejection reason is required");
        return;
      }
      toast.error("Failed to reject return request — please try again");
    },
  });
}
