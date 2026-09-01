import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  BuilderConfigResponse,
  FieldValueOption,
  ActivityCategoryResponse,
  CreateBuilderFieldRequest,
  UpdateBuilderFieldRequest,
  UpdateSectionRequest,
  CreateActivityCategoryRequest,
  BuilderSectionConfigResponse,
  BuilderFieldConfigResponse,
} from "@/types/builder-config.types";

export async function getBuilderConfig(
  incentiveType: string,
): Promise<BuilderConfigResponse> {
  const response = await api.get<ApiResponse<BuilderConfigResponse>>(
    `/builder-config/${incentiveType}`,
  );
  return response.data.data;
}

export async function updateSection(
  sectionId: string,
  data: UpdateSectionRequest,
): Promise<BuilderSectionConfigResponse> {
  const response = await api.put<ApiResponse<BuilderSectionConfigResponse>>(
    `/builder-config/sections/${sectionId}`,
    data,
  );
  return response.data.data;
}

export async function addFieldToSection(
  sectionId: string,
  data: CreateBuilderFieldRequest,
): Promise<BuilderFieldConfigResponse> {
  const response = await api.post<ApiResponse<BuilderFieldConfigResponse>>(
    `/builder-config/sections/${sectionId}/fields`,
    data,
  );
  return response.data.data;
}

export async function updateField(
  fieldId: string,
  data: UpdateBuilderFieldRequest,
): Promise<BuilderFieldConfigResponse> {
  const response = await api.put<ApiResponse<BuilderFieldConfigResponse>>(
    `/builder-config/fields/${fieldId}`,
    data,
  );
  return response.data.data;
}

export async function removeField(fieldId: string): Promise<void> {
  await api.delete(`/builder-config/fields/${fieldId}`);
}

export async function resolveFieldValues(
  fieldId: string,
  context?: Record<string, string[]>,
): Promise<FieldValueOption[]> {
  const params = new URLSearchParams();
  if (context) {
    for (const [key, values] of Object.entries(context)) {
      for (const v of values) {
        params.append(`context[${key}]`, v);
      }
    }
  }
  const response = await api.get<ApiResponse<FieldValueOption[]>>(
    `/builder-config/fields/${fieldId}/values`,
    { params },
  );
  return response.data.data;
}

export async function getActivityCategories(): Promise<
  ActivityCategoryResponse[]
> {
  const response = await api.get<ApiResponse<ActivityCategoryResponse[]>>(
    "/activity-categories",
  );
  return response.data.data;
}

export async function createActivityCategory(
  data: CreateActivityCategoryRequest,
): Promise<ActivityCategoryResponse> {
  const response = await api.post<ApiResponse<ActivityCategoryResponse>>(
    "/activity-categories",
    data,
  );
  return response.data.data;
}
