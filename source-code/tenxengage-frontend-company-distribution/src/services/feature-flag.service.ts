import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  FeatureFlagResponse,
  CreateFeatureFlagRequest,
  UpdateFeatureFlagRequest,
} from "@/types/client.types";

export async function getAllFeatureFlags(): Promise<FeatureFlagResponse[]> {
  const response =
    await api.get<ApiResponse<FeatureFlagResponse[]>>("/feature-flags");
  return response.data.data;
}

export async function createFeatureFlag(
  data: CreateFeatureFlagRequest,
): Promise<FeatureFlagResponse> {
  const response = await api.post<ApiResponse<FeatureFlagResponse>>(
    "/feature-flags",
    data,
  );
  return response.data.data;
}

export async function updateFeatureFlag(
  id: string,
  data: UpdateFeatureFlagRequest,
): Promise<FeatureFlagResponse> {
  const response = await api.put<ApiResponse<FeatureFlagResponse>>(
    `/feature-flags/${id}`,
    data,
  );
  return response.data.data;
}
