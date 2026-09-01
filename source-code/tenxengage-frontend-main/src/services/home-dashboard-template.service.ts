import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  HomeDashboardTemplate,
  HomeDashboardWidgetCatalogEntry,
  HomeDashboardRowLayoutCatalogEntry,
} from "@/types/home-dashboard.types";
import type { ClientRole } from "@/types/permission.types";

export async function getHomeDashboardTemplates(
  roleType?: "INTERNAL" | "EXTERNAL",
): Promise<HomeDashboardTemplate[]> {
  const response = await api.get<ApiResponse<HomeDashboardTemplate[]>>(
    "/home-dashboard-templates",
    { params: roleType ? { roleType } : undefined },
  );
  return response.data.data;
}

export async function getHomeDashboardWidgets(): Promise<
  HomeDashboardWidgetCatalogEntry[]
> {
  const response = await api.get<
    ApiResponse<HomeDashboardWidgetCatalogEntry[]>
  >("/home-dashboard-widgets");
  return response.data.data;
}

export async function getHomeDashboardLayouts(): Promise<
  HomeDashboardRowLayoutCatalogEntry[]
> {
  const response = await api.get<
    ApiResponse<HomeDashboardRowLayoutCatalogEntry[]>
  >("/home-dashboard-layouts");
  return response.data.data;
}

export async function assignHomeDashboardTemplate(
  roleId: string,
  templateId: string,
): Promise<ClientRole> {
  const response = await api.put<ApiResponse<ClientRole>>(
    `/client-roles/${roleId}/dashboard-template`,
    { templateId },
  );
  return response.data.data;
}

export async function clearHomeDashboardTemplate(
  roleId: string,
): Promise<ClientRole> {
  const response = await api.delete<ApiResponse<ClientRole>>(
    `/client-roles/${roleId}/dashboard-template`,
  );
  return response.data.data;
}
