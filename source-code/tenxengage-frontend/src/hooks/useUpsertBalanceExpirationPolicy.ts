// Adapted from: src/hooks/useRejectReturn.ts (TanStack Query pattern)
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { upsertPolicy } from "@/services/balanceExpiration.service";
import { useAuth } from "@/hooks/useAuth";
import type { UpsertBalanceExpirationPolicyRequest } from "@/types/balanceExpiration.types";
import type { UseFormSetError } from "react-hook-form";
import type { BalanceExpirationPolicyFormValues } from "@/components/balanceExpiration/balanceExpirationPolicySchema";

/**
 * useUpsertBalanceExpirationPolicy — PUT /api/v1/redemption/expiration/policies/{currencyId}
 *
 * onSuccess: invalidates ['balance-expiration-policies', clientId] and
 *   ['balance-expiring-soon', clientId] so the form and preview card refresh.
 *
 * 422 errorCode → react-hook-form field errors via setError callback.
 * Per PROJECT-CONTEXT.md: use data?.errorCode ?? data?.code to tolerate both shapes.
 */
export function useUpsertBalanceExpirationPolicy() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const clientId = user?.clientId ?? null;

  const { mutateAsync, isPending } = useMutation({
    mutationFn: ({
      currencyId,
      body,
    }: {
      currencyId: string;
      body: UpsertBalanceExpirationPolicyRequest;
    }) => upsertPolicy(currencyId, body),
    onSuccess: () => {
      if (clientId) {
        queryClient.invalidateQueries({
          queryKey: ["balance-expiration-policies", clientId],
        });
        queryClient.invalidateQueries({
          queryKey: ["balance-expiring-soon", clientId],
        });
      }
    },
  });

  /**
   * Upsert with field-error mapping for 422 responses.
   * Returns true on success, false on handled 422; re-throws everything else.
   */
  async function upsert(
    currencyId: string,
    body: UpsertBalanceExpirationPolicyRequest,
    setError: UseFormSetError<BalanceExpirationPolicyFormValues>,
  ): Promise<boolean> {
    try {
      await mutateAsync({ currencyId, body });
      return true;
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 422) {
        const data = err.response.data as { errorCode?: string; code?: string; errorMessage?: string } | undefined;
        const errorCode = data?.errorCode ?? data?.code ?? "UNKNOWN";

        // Map errorCode → form field errors
        if (
          errorCode === "LEAD_TIME_MUST_BE_LESS_THAN_INACTIVITY" ||
          errorCode === "INVALID_LEAD_TIME"
        ) {
          setError("leadTimeDays", {
            message:
              "Lead time must be at least 1 day and less than the inactivity period",
          });
        } else if (
          errorCode === "FIXED_EXPIRY_DATE_IN_PAST" ||
          errorCode === "INVALID_FIXED_EXPIRY_DATE"
        ) {
          setError("fixedExpiryDate", {
            message: "Fixed expiry date must be in the future",
          });
        } else if (
          errorCode === "INACTIVITY_DAYS_OUT_OF_BOUNDS" ||
          errorCode === "INVALID_INACTIVITY_DAYS"
        ) {
          setError("inactivityDays", {
            message:
              "Inactivity period must be between 30 and 1825 days",
          });
        } else {
          // Generic 422 — surface the errorCode as a leadTimeDays error
          setError("leadTimeDays", {
            message: data?.errorMessage ?? "Invalid policy configuration",
          });
        }
        return false;
      }
      throw err;
    }
  }

  return { upsert, isPending };
}
