// Adapted from: src/hooks/useRedemptionSubmit.ts (TanStack Query pattern)
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { isAxiosError } from "axios";
import { cancelReturn } from "@/services/redemption-returns.service";
import { useAuth } from "@/hooks/useAuth";

export function useCancelReturn() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id ?? "";

  return useMutation({
    mutationFn: (id: string) => cancelReturn(id),
    onSuccess: (_data, id) => {
      // Invalidate the partner's my-returns list
      queryClient.invalidateQueries({ queryKey: ["my-returns", userId] });
      // Invalidate the specific return detail
      queryClient.invalidateQueries({ queryKey: ["return", id, false] });
      toast.success("Return request cancelled");
    },
    onError: (error) => {
      if (isAxiosError(error) && error.response?.status === 409) {
        toast.error("This return can no longer be cancelled");
        return;
      }
      toast.error("Failed to cancel return request — please try again");
    },
  });
}
