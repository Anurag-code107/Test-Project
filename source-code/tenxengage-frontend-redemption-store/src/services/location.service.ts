import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  LocationHierarchyResponse,
  LocationLevelResponse,
  LocationValueResponse,
  LocationFilterOptionsResponse,
  CreateLocationLevelRequest,
  UpdateLocationLevelRequest,
  UpdateLocationLevelSettingsRequest,
  CreateLocationValueRequest,
  UpdateLocationValueRequest,
} from "@/types/location.types";

export async function getLocationHierarchy(): Promise<LocationHierarchyResponse> {
  const response =
    await api.get<ApiResponse<LocationHierarchyResponse>>("/location-levels");
  return response.data.data;
}

export async function createLocationLevel(
  data: CreateLocationLevelRequest,
): Promise<LocationLevelResponse> {
  const response = await api.post<ApiResponse<LocationLevelResponse>>(
    "/location-levels",
    data,
  );
  return response.data.data;
}

export async function updateLocationLevel(
  id: string,
  data: UpdateLocationLevelRequest,
): Promise<LocationLevelResponse> {
  const response = await api.put<ApiResponse<LocationLevelResponse>>(
    `/location-levels/${id}`,
    data,
  );
  return response.data.data;
}

export async function deleteLocationLevel(id: string): Promise<void> {
  await api.delete(`/location-levels/${id}`);
}

export async function createLocationValue(
  data: CreateLocationValueRequest,
): Promise<LocationValueResponse> {
  const response = await api.post<ApiResponse<LocationValueResponse>>(
    "/location-levels/values",
    data,
  );
  return response.data.data;
}

export async function updateLocationValue(
  id: string,
  data: UpdateLocationValueRequest,
): Promise<LocationValueResponse> {
  const response = await api.put<ApiResponse<LocationValueResponse>>(
    `/location-levels/values/${id}`,
    data,
  );
  return response.data.data;
}

export async function deleteLocationValue(id: string): Promise<void> {
  await api.delete(`/location-levels/values/${id}`);
}

export async function updateLocationLevelSettings(
  id: string,
  data: UpdateLocationLevelSettingsRequest,
): Promise<LocationLevelResponse> {
  const response = await api.patch<ApiResponse<LocationLevelResponse>>(
    `/location-levels/${id}/settings`,
    data,
  );
  return response.data.data;
}

export async function getLocationFilterOptions(): Promise<LocationFilterOptionsResponse> {
  const response = await api.get<ApiResponse<LocationFilterOptionsResponse>>(
    "/location-levels/filter-options",
  );
  return response.data.data;
}

export async function getLocationBuilderOptions(): Promise<
  LocationLevelResponse[]
> {
  const response = await api.get<ApiResponse<LocationLevelResponse[]>>(
    "/location-levels/builder-options",
  );
  return response.data.data;
}
