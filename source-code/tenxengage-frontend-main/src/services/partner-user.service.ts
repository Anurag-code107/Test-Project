import api from "@/lib/axios";
import type { UpdatePermissionsRequest } from "@/types/permission.types";

export interface PartnerUserResponse {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  clientRoleName: string | null;
  status: string;
}

export async function listPartnerUsers(): Promise<PartnerUserResponse[]> {
  const response = await api.get<PartnerUserResponse[]>("/partner-users");
  return response.data;
}

export async function getSellerPermissions(
  userId: string,
): Promise<Set<string>> {
  const response = await api.get<Set<string>>(
    `/partner-users/${userId}/permissions`,
  );
  return response.data;
}

export async function updateSellerPermissions(
  userId: string,
  data: UpdatePermissionsRequest,
): Promise<Set<string>> {
  const response = await api.put<Set<string>>(
    `/partner-users/${userId}/permissions`,
    data,
  );
  return response.data;
}
