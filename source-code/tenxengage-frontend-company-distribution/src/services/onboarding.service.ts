import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  OnboardingStatusResponse,
  LegalPolicyResponse,
  ConsentPreferenceResponse,
  SetPasswordRequest,
  CompleteProfileRequest,
  AcceptPoliciesRequest,
  SetConsentRequest,
} from "@/types/onboarding.types";

export async function validateOnboardingToken(
  token: string,
): Promise<OnboardingStatusResponse> {
  const response = await api.post<ApiResponse<OnboardingStatusResponse>>(
    "/onboarding/validate",
    { token },
  );
  return response.data.data;
}

export async function setPassword(
  data: SetPasswordRequest,
): Promise<OnboardingStatusResponse> {
  const response = await api.post<ApiResponse<OnboardingStatusResponse>>(
    "/onboarding/set-password",
    data,
  );
  return response.data.data;
}

export async function completeProfile(
  data: CompleteProfileRequest,
): Promise<OnboardingStatusResponse> {
  const response = await api.post<ApiResponse<OnboardingStatusResponse>>(
    "/onboarding/complete-profile",
    data,
  );
  return response.data.data;
}

export async function acceptPolicies(
  data: AcceptPoliciesRequest,
): Promise<OnboardingStatusResponse> {
  const response = await api.post<ApiResponse<OnboardingStatusResponse>>(
    "/onboarding/accept-policies",
    data,
  );
  return response.data.data;
}

export async function setConsent(
  data: SetConsentRequest,
): Promise<OnboardingStatusResponse> {
  const response = await api.post<ApiResponse<OnboardingStatusResponse>>(
    "/onboarding/set-consent",
    data,
  );
  return response.data.data;
}

export async function completeOnboarding(
  token: string,
): Promise<OnboardingStatusResponse> {
  const response = await api.post<ApiResponse<OnboardingStatusResponse>>(
    "/onboarding/complete",
    { token },
  );
  return response.data.data;
}

export async function getPolicies(
  token: string,
): Promise<LegalPolicyResponse[]> {
  const response = await api.get<ApiResponse<LegalPolicyResponse[]>>(
    "/onboarding/policies",
    { params: { token } },
  );
  return response.data.data;
}

export async function getConsentPreferences(
  token: string,
): Promise<ConsentPreferenceResponse[]> {
  const response = await api.get<ApiResponse<ConsentPreferenceResponse[]>>(
    "/onboarding/consent",
    { params: { token } },
  );
  return response.data.data;
}
