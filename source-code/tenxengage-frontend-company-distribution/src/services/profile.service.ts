import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  ProfileFieldResponse,
  UpdateProfileRequest,
} from "@/types/profile.types";

export async function getMyProfileFields(): Promise<ProfileFieldResponse[]> {
  const response =
    await api.get<ApiResponse<ProfileFieldResponse[]>>("/me/profile-fields");
  return response.data.data;
}

export async function updateMyProfileDynamic(data: UpdateProfileRequest) {
  const response = await api.patch<ApiResponse<unknown>>("/me/profile", data);
  return response.data.data;
}
