import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";

interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
  avatar?: string;
}

interface ConsentPreference {
  consentType: string;
  granted: boolean;
  lastUpdated: string | null;
}

export async function updateMyProfile(data: UpdateProfileRequest) {
  const response = await api.patch<ApiResponse<unknown>>("/me/profile", data);
  return response.data.data;
}

export async function exportMyData() {
  const response =
    await api.get<ApiResponse<Record<string, unknown>>>("/me/data-export");
  return response.data.data;
}

export async function getMyConsent(): Promise<ConsentPreference[]> {
  const response =
    await api.get<ApiResponse<ConsentPreference[]>>("/me/consent");
  return response.data.data;
}

export async function updateMyConsent(consents: Record<string, boolean>) {
  const response = await api.put<ApiResponse<ConsentPreference[]>>(
    "/me/consent",
    { consents },
  );
  return response.data.data;
}

export async function requestAccountDeletion() {
  const response = await api.post<ApiResponse<{ message: string }>>(
    "/me/deletion-request",
  );
  return response.data.data;
}
