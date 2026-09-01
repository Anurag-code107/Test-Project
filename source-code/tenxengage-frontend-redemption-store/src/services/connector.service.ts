import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  ConnectorResponse,
  CreateConnectorRequest,
  UpdateConnectorRequest,
  TestConnectionResponse,
} from "@/types/connector.types";

export async function getConnectors(): Promise<ConnectorResponse[]> {
  const response =
    await api.get<ApiResponse<ConnectorResponse[]>>("/connectors");
  return response.data.data;
}

export async function getConnectorById(id: string): Promise<ConnectorResponse> {
  const response = await api.get<ApiResponse<ConnectorResponse>>(
    `/connectors/${id}`,
  );
  return response.data.data;
}

export async function createConnector(
  data: CreateConnectorRequest,
): Promise<ConnectorResponse> {
  const response = await api.post<ApiResponse<ConnectorResponse>>(
    "/connectors",
    data,
  );
  return response.data.data;
}

export async function updateConnector(
  id: string,
  data: UpdateConnectorRequest,
): Promise<ConnectorResponse> {
  const response = await api.put<ApiResponse<ConnectorResponse>>(
    `/connectors/${id}`,
    data,
  );
  return response.data.data;
}

export async function deleteConnector(id: string): Promise<void> {
  await api.delete(`/connectors/${id}`);
}

export async function testConnection(
  id: string,
): Promise<TestConnectionResponse> {
  const response = await api.post<ApiResponse<TestConnectionResponse>>(
    `/connectors/${id}/test`,
  );
  return response.data.data;
}
