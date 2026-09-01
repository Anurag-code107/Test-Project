import { useMutation } from "@tanstack/react-query";
import type { AxiosError } from "axios";
import type { ErrorResponse } from "@/types/api.types";
import { initiatePhoneUpdate, confirmPhoneUpdate } from "@/services/profile-phone.service";

/** Extract the machine error code from a failed phone-update response. */
export function phoneErrorCode(error: unknown): string | undefined {
  const ax = error as AxiosError<ErrorResponse & { code?: string }>;
  const data = ax?.response?.data;
  return data?.errorCode ?? data?.code;
}

/** Map a phone-update error code to friendly, user-facing copy. */
export function friendlyPhoneError(code: string | undefined): string {
  switch (code) {
    case "UNSUPPORTED_MOBILE_COUNTRY":
      return "That country isn't supported for mobile payouts yet.";
    case "XTRM_PROFILE_OTP_INVALID":
      return "That code wasn't accepted. Please request a new code and try again.";
    case "XTRM_PROFILE_UPDATE_FAILED":
      return "We couldn't update your mobile number. Please check it and try again.";
    case "XTRM_UNAVAILABLE":
      return "Payouts are temporarily unavailable. Please try again shortly.";
    default:
      return "Something went wrong — please try again.";
  }
}

export function useInitiatePhoneUpdate() {
  return useMutation({ mutationFn: initiatePhoneUpdate });
}

export function useConfirmPhoneUpdate() {
  return useMutation({ mutationFn: confirmPhoneUpdate });
}
