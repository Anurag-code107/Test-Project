import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  BrandingConfig,
  UpdateBrandingRequest,
} from "@/types/branding.types";

export async function getBranding(): Promise<BrandingConfig> {
  const response = await api.get<ApiResponse<BrandingConfig>>("/branding");
  return response.data.data;
}

export async function updateBranding(
  data: UpdateBrandingRequest,
): Promise<BrandingConfig> {
  const response = await api.put<ApiResponse<BrandingConfig>>("/branding", data);
  return response.data.data;
}

export async function uploadBrandingLogo(file: File): Promise<BrandingConfig> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await api.post<ApiResponse<BrandingConfig>>(
    "/branding/logo",
    formData,
    { headers: { "Content-Type": "multipart/form-data" } },
  );
  return response.data.data;
}

export async function removeBrandingLogo(): Promise<BrandingConfig> {
  const response = await api.delete<ApiResponse<BrandingConfig>>(
    "/branding/logo",
  );
  return response.data.data;
}
