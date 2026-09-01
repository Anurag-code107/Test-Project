// Adapted from: src/hooks/useRedemptionSubmit.ts (TanStack Query pattern)
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { isAxiosError } from "axios";
import { submitReturn } from "@/services/redemption-returns.service";
import type { SubmitReturnRequest, ReturnDetailResponse } from "@/types/redemption-returns.types";
import { useAuth } from "@/hooks/useAuth";

interface UseSubmitReturnOptions {
  onSuccess?: (data: ReturnDetailResponse) => void;
}

export function useSubmitReturn(options?: UseSubmitReturnOptions) {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id ?? "";

  return useMutation({
    mutationFn: (dto: SubmitReturnRequest) => submitReturn(dto),
    onSuccess: (data) => {
      // Invalidate partner's my-returns list
      queryClient.invalidateQueries({ queryKey: ["my-returns", userId] });
      // Invalidate F-05 history list so isReturnEligible refreshes
      queryClient.invalidateQueries({ queryKey: ["redemption-requests"] });
      toast.success("Return request submitted");
      options?.onSuccess?.(data);
    },
    onError: (error) => {
      if (isAxiosError(error) && error.response?.status === 409) {
        // 409 is displayed inline in the dialog — do not toast
        return;
      }
      if (isAxiosError(error) && error.response?.status === 422) {
        // 422 displayed inline in dialog — do not toast
        return;
      }
      // Other errors: shown inline via the mutation's isError state
    },
  });
}
