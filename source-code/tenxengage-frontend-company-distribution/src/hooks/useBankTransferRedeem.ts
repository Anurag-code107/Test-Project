// Adapted from: src/hooks/useRedemptionSubmit.ts (TanStack Query pattern)
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { submitBankTransferRedemption } from "@/services/redemption-flow.service";
import type { SubmitBankTransferRedemptionRequest } from "@/types/redemption-flow.types";
import type { AxiosError } from "axios";
import type { ErrorResponse } from "@/types/api.types";

interface UseBankTransferRedeemOptions {
  onSuccess?: (id: string) => void;
  onFieldError?: (field: string, message: string) => void;
  /** Fired on a 409 (no linked bank / max in-flight) — the server message is toasted here. */
  onConflict?: () => void;
}

/**
 * Submit a bank-transfer redemption (POST /redemption/requests/bank-transfer). The server resolves the
 * reserved per-client bank-transfer card and pays the user's default linked bank; the caller passes only
 * the funding wallet + amount. 422 → inline field error; 409 (no bank / in-flight limit) → toast the
 * server's reason.
 */
export function useBankTransferRedeem(options?: UseBankTransferRedeemOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (req: SubmitBankTransferRedemptionRequest) => submitBankTransferRedemption(req),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["redemption-requests"] });
      queryClient.invalidateQueries({ queryKey: ["wallet-balance"] });
      queryClient.invalidateQueries({ queryKey: ["reward-balances"] });
      options?.onSuccess?.(data.id);
    },
    onError: (error: AxiosError<ErrorResponse>) => {
      const status = error.response?.status;
      const message = error.response?.data?.errorMessage;
      if (status === 422) {
        options?.onFieldError?.("amount", message ?? "Validation error");
        return;
      }
      if (status === 409) {
        toast.error(message ?? "Maximum in-flight redemptions reached");
        options?.onConflict?.();
        return;
      }
      toast.error("Something went wrong — please try again");
    },
  });
}
