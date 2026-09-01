import api from "@/lib/axios";
import type { ApiResponse, PaginatedResponse } from "@/types/api.types";
import type {
  ClientResponse,
  CreateClientRequest,
  UpdateClientRequest,
  ClientStatsResponse,
  ClientFeatureOverrideResponse,
  SetFeatureOverrideRequest,
} from "@/types/client.types";

export interface ClientListParams {
  page?: number;
  size?: number;
  search?: string;
  status?: string;
  subscriptionTier?: string;
  sort?: string;
}

export async function getClients(params?: ClientListParams): Promise<PaginatedResponse<ClientResponse>> {
  const response = await api.get<ApiResponse<PaginatedResponse<ClientResponse>>>("/clients", { params });
  return response.data.data;
}

export async function getClientById(id: string): Promise<ClientResponse> {
  const response = await api.get<ApiResponse<ClientResponse>>(`/clients/${id}`);
  return response.data.data;
}

export async function createClient(data: CreateClientRequest): Promise<ClientResponse> {
  const response = await api.post<ApiResponse<ClientResponse>>("/clients", data);
  return response.data.data;
}

export async function updateClient(id: string, data: UpdateClientRequest): Promise<ClientResponse> {
  const response = await api.put<ApiResponse<ClientResponse>>(`/clients/${id}`, data);
  return response.data.data;
}

export async function deleteClient(id: string): Promise<void> {
  await api.delete(`/clients/${id}`);
}

export async function getClientStats(): Promise<ClientStatsResponse> {
  const response = await api.get<ApiResponse<ClientStatsResponse>>("/clients/stats");
  return response.data.data;
}

export async function getClientOverrides(clientId: string): Promise<ClientFeatureOverrideResponse[]> {
  const response = await api.get<ApiResponse<ClientFeatureOverrideResponse[]>>(`/clients/${clientId}/feature-overrides`);
  return response.data.data;
}

export async function setClientOverrides(
  clientId: string,
  overrides: SetFeatureOverrideRequest[]
): Promise<ClientFeatureOverrideResponse[]> {
  const response = await api.put<ApiResponse<ClientFeatureOverrideResponse[]>>(
    `/clients/${clientId}/feature-overrides`,
    overrides
  );
  return response.data.data;
}
