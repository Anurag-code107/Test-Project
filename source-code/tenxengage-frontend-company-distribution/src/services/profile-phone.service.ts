import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  InitiatePhoneUpdateRequest,
  ConfirmPhoneUpdateRequest,
  PhoneUpdateResponse,
} from "@/types/profile-phone.types";

/** Step 1 of a mobile-number change — sends the OTP if the user is XTRM-enrolled. */
export async function initiatePhoneUpdate(
  req: InitiatePhoneUpdateRequest,
): Promise<PhoneUpdateResponse> {
  const response = await api.post<ApiResponse<PhoneUpdateResponse>>("/me/phone/initiate", req);
  return response.data.data;
}

/** Step 2 — confirms with the OTP; on success the number is applied at XTRM and persisted. */
export async function confirmPhoneUpdate(
  req: ConfirmPhoneUpdateRequest,
): Promise<PhoneUpdateResponse> {
  const response = await api.post<ApiResponse<PhoneUpdateResponse>>("/me/phone/confirm", req);
  return response.data.data;
}
