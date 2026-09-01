import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import {
  getCompanyAdminProfile,
  completeCompanyAdminProfile,
} from "@/services/company-admin-profile.service";
import type { CompleteCompanyAdminProfileRequest } from "@/types/partner-company.types";

/**
 * The signed-in user's company payout profile — or an error, which is itself the answer.
 *
 * The server answers 403 to any company admin who is not the one the company's payout account is bound to.
 * Every company admin shares the same PARTNER_ADMIN role, so no permission distinguishes them and the
 * client cannot decide this locally; callers gate on whether this query resolves.
 *
 * `retry: false` because those refusals are deterministic — retrying a 403 three times only delays the
 * moment the UI can act on it.
 */
export function useCompanyAdminProfile(options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: ["company-admin-profile"],
    queryFn: getCompanyAdminProfile,
    enabled: options.enabled ?? true,
    retry: false,
  });
}

/**
 * True when the refusal means "this is not your payout setup" rather than "something went wrong".
 *
 * A company has one payout account, bound at the provider to one admin's email. Other admins of the same
 * company hold identical permissions and are refused this profile — worth saying plainly, because the
 * generic failure message would send them looking for a fault that isn't there.
 */
export function isNotYourPayoutSetup(error: unknown): boolean {
  return isAxiosError(error) && error.response?.status === 403;
}

export function useCompleteCompanyAdminProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CompleteCompanyAdminProfileRequest) =>
      completeCompanyAdminProfile(data),
    // Re-read afterwards so the payout status reflects what provisioning actually did, not what was asked.
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["company-admin-profile"] }),
  });
}
