// Adapted from: src/hooks/useCancelReturn.ts (TanStack Query pattern)
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { isAxiosError } from "axios";
import { approveReturn } from "@/services/redemption-returns.service";
import { useAuth } from "@/hooks/useAuth";

/**
 * useApproveReturn — admin approve mutation.
 * On success: invalidates ['admin-returns', clientId] and ['return', id, true].
 */
export function useApproveReturn() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const clientId = user?.clientId ?? "";

  return useMutation({
    mutationFn: (id: string) => approveReturn(id),
    onSuccess: (_data, id) => {
      // Invalidate admin queue list
      queryClient.invalidateQueries({ queryKey: ["admin-returns", clientId] });
      // Invalidate the specific return detail (admin view)
      queryClient.invalidateQueries({ queryKey: ["return", id, true] });
      toast.success("Return request approved");
    },
    onError: (error) => {
      if (isAxiosError(error) && error.response?.status === 409) {
        toast.error("This return was updated concurrently. Please refresh and try again.");
        return;
      }
      if (isAxiosError(error) && error.response?.status === 404) {
        toast.error("Return request not found");
        return;
      }
      toast.error("Failed to approve return request — please try again");
    },
  });
}
