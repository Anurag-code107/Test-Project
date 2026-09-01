// Adapted from: src/hooks/useRejectReturn.ts (same domain, same mutation shape)
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { isAxiosError } from "axios";
import { resolveTimedOutReturn } from "@/services/redemption-returns.service";
import { useAuth } from "@/hooks/useAuth";
import type { ResolveTimedOutReturnRequest } from "@/types/redemption-returns.types";

/**
 * useResolveTimedOutReturn — admin manual resolve mutation for RETURN_TIMED_OUT returns.
 * On success: invalidates ['admin-returns', clientId] and ['return', id, true].
 */
export function useResolveTimedOutReturn() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const clientId = user?.clientId ?? "";

  return useMutation({
    mutationFn: ({ id, dto }: { id: string; dto: ResolveTimedOutReturnRequest }) =>
      resolveTimedOutReturn(id, dto.resolution, dto.notes),
    onSuccess: (_data, { id }) => {
      // Invalidate admin queue list
      queryClient.invalidateQueries({ queryKey: ["admin-returns", clientId] });
      // Invalidate the specific return detail (admin view)
      queryClient.invalidateQueries({ queryKey: ["return", id, true] });
      toast.success("Return resolved successfully");
    },
    onError: (error) => {
      if (isAxiosError(error) && error.response?.status === 409) {
        toast.error("This return is no longer in a timed-out state. Please refresh and try again.");
        return;
      }
      toast.error("Failed to resolve return — please try again");
    },
  });
}
