import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  PermissionDef,
  ClientRole,
  CreateClientRoleRequest,
  CloneClientRoleRequest,
  UpdateClientRoleRequest,
  UpdatePermissionsRequest,
} from "@/types/permission.types";

// ── Permission catalog ────────────────────────────────────────────────────

export async function getAllPermissions(): Promise<PermissionDef[]> {
  const response = await api.get<ApiResponse<PermissionDef[]>>("/permissions");
  return response.data.data;
}

export async function getEffectivePermissions(): Promise<string[]> {
  const response = await api.get<ApiResponse<string[]>>(
    "/permissions/effective",
  );
  return response.data.data;
}

export async function getUserEffectivePermissions(
  userId: string,
): Promise<string[]> {
  const response = await api.get<ApiResponse<string[]>>(
    `/permissions/effective/${userId}`,
  );
  return response.data.data;
}

// ── Client Roles ──────────────────────────────────────────────────────────

export async function getClientRoles(): Promise<ClientRole[]> {
  const response = await api.get<ApiResponse<ClientRole[]>>("/client-roles");
  return response.data.data;
}

export async function getClientRole(id: string): Promise<ClientRole> {
  const response = await api.get<ApiResponse<ClientRole>>(
    `/client-roles/${id}`,
  );
  return response.data.data;
}

export async function createClientRole(
  data: CreateClientRoleRequest,
): Promise<ClientRole> {
  const response = await api.post<ApiResponse<ClientRole>>(
    "/client-roles",
    data,
  );
  return response.data.data;
}

export async function updateClientRole(
  id: string,
  data: UpdateClientRoleRequest,
): Promise<ClientRole> {
  const response = await api.put<ApiResponse<ClientRole>>(
    `/client-roles/${id}`,
    data,
  );
  return response.data.data;
}

export async function updateRolePermissions(
  id: string,
  data: UpdatePermissionsRequest,
): Promise<ClientRole> {
  const response = await api.put<ApiResponse<ClientRole>>(
    `/client-roles/${id}/permissions`,
    data,
  );
  return response.data.data;
}

export async function cloneClientRole(
  id: string,
  data: CloneClientRoleRequest,
): Promise<ClientRole> {
  const response = await api.post<ApiResponse<ClientRole>>(
    `/client-roles/${id}/clone`,
    data,
  );
  return response.data.data;
}

export async function deleteClientRole(id: string): Promise<void> {
  await api.delete(`/client-roles/${id}`);
}

// ── Company Permission Overrides ──────────────────────────────────────────

export async function getCompanyOverrides(
  companyId: string,
): Promise<Record<string, boolean>> {
  const response = await api.get<ApiResponse<Record<string, boolean>>>(
    `/company-permissions/${companyId}`,
  );
  return response.data.data;
}

export async function updateCompanyOverrides(
  companyId: string,
  data: UpdatePermissionsRequest,
): Promise<Record<string, boolean>> {
  const response = await api.put<ApiResponse<Record<string, boolean>>>(
    `/company-permissions/${companyId}`,
    data,
  );
  return response.data.data;
}

// ── User Permission Overrides ─────────────────────────────────────────────

export async function getUserOverrides(
  companyId: string,
  userId: string,
): Promise<Record<string, boolean>> {
  const response = await api.get<ApiResponse<Record<string, boolean>>>(
    `/company-permissions/${companyId}/users/${userId}`,
  );
  return response.data.data;
}

export async function updateUserOverrides(
  companyId: string,
  userId: string,
  data: UpdatePermissionsRequest,
): Promise<Record<string, boolean>> {
  const response = await api.put<ApiResponse<Record<string, boolean>>>(
    `/company-permissions/${companyId}/users/${userId}`,
    data,
  );
  return response.data.data;
}
