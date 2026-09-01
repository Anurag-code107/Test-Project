// Adapted from: src/hooks/useRedemptionCatalog.ts (TanStack Query pattern)
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { submitPersonalRedemption, submitCompanyRedemption } from "@/services/redemption-flow.service";
import type {
  SubmitPersonalRedemptionRequest,
  SubmitCompanyRedemptionRequest,
} from "@/types/redemption-flow.types";
import type { AxiosError } from "axios";
import type { ErrorResponse } from "@/types/api.types";

interface UseRedemptionSubmitOptions {
  type?: "personal" | "company";
  onSuccess?: (id: string) => void;
  onFieldError?: (field: string, message: string) => void;
  onInFlightError?: () => void;
}

type RedemptionSubmitRequest = SubmitPersonalRedemptionRequest | SubmitCompanyRedemptionRequest;

export function useRedemptionSubmit(options?: UseRedemptionSubmitOptions) {
  const queryClient = useQueryClient();
  const type = options?.type ?? "personal";

  return useMutation({
    mutationFn: (req: RedemptionSubmitRequest) =>
      type === "company"
        ? submitCompanyRedemption(req as SubmitCompanyRedemptionRequest)
        : submitPersonalRedemption(req as SubmitPersonalRedemptionRequest),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["redemption-requests"] });
      queryClient.invalidateQueries({ queryKey: ["wallet-balance"] });
      queryClient.invalidateQueries({ queryKey: ["company-wallet"] });
      queryClient.invalidateQueries({ queryKey: ["reward-balances"] });
      options?.onSuccess?.(data.id);
    },
    onError: (error: AxiosError<ErrorResponse>) => {
      const status = error.response?.status;
      if (status === 409) {
        toast.error("Maximum in-flight redemptions reached");
        options?.onInFlightError?.();
        return;
      }
      if (status === 422) {
        const msg = error.response?.data?.errorMessage ?? "Validation error";
        options?.onFieldError?.("amount", msg);
        return;
      }
      toast.error("Something went wrong — please try again");
    },
  });
}
