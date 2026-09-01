import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  DataObjectResponse,
  DataObjectDetailResponse,
  CreateDataObjectRequest,
  UpdateDataObjectRequest,
  CreateFieldRequest,
  UpdateFieldRequest,
  DataObjectFieldResponse,
  ConnectorMappingRequest,
  RuleFieldResponse,
} from "@/types/data-object.types";

export async function getDataObjects(): Promise<DataObjectResponse[]> {
  const response =
    await api.get<ApiResponse<DataObjectResponse[]>>("/data-objects");
  return response.data.data;
}

export async function getDataObjectById(
  id: string,
): Promise<DataObjectDetailResponse> {
  const response = await api.get<ApiResponse<DataObjectDetailResponse>>(
    `/data-objects/${id}`,
  );
  return response.data.data;
}

export async function createDataObject(
  data: CreateDataObjectRequest,
): Promise<DataObjectResponse> {
  const response = await api.post<ApiResponse<DataObjectResponse>>(
    "/data-objects",
    data,
  );
  return response.data.data;
}

export async function updateDataObject(
  id: string,
  data: UpdateDataObjectRequest,
): Promise<DataObjectResponse> {
  const response = await api.put<ApiResponse<DataObjectResponse>>(
    `/data-objects/${id}`,
    data,
  );
  return response.data.data;
}

export async function deleteDataObject(id: string): Promise<void> {
  await api.delete(`/data-objects/${id}`);
}

export async function addField(
  dataObjectId: string,
  data: CreateFieldRequest,
): Promise<DataObjectFieldResponse> {
  const response = await api.post<ApiResponse<DataObjectFieldResponse>>(
    `/data-objects/${dataObjectId}/fields`,
    data,
  );
  return response.data.data;
}

export async function updateField(
  dataObjectId: string,
  fieldId: string,
  data: UpdateFieldRequest,
): Promise<DataObjectFieldResponse> {
  const response = await api.put<ApiResponse<DataObjectFieldResponse>>(
    `/data-objects/${dataObjectId}/fields/${fieldId}`,
    data,
  );
  return response.data.data;
}

export async function deleteField(
  dataObjectId: string,
  fieldId: string,
): Promise<void> {
  await api.delete(`/data-objects/${dataObjectId}/fields/${fieldId}`);
}

export async function setConnectorMapping(
  dataObjectId: string,
  data: ConnectorMappingRequest,
): Promise<void> {
  await api.put(`/data-objects/${dataObjectId}/connector-mapping`, data);
}

export async function removeConnectorMapping(
  dataObjectId: string,
): Promise<void> {
  await api.delete(`/data-objects/${dataObjectId}/connector-mapping`);
}

export async function getRuleFields(
  dataObjectId?: string,
  dataObjectName?: string,
): Promise<RuleFieldResponse[]> {
  const params: Record<string, string> = {};
  if (dataObjectId) params.dataObjectId = dataObjectId;
  if (dataObjectName) params.dataObjectName = dataObjectName;
  const response = await api.get<ApiResponse<RuleFieldResponse[]>>(
    "/data-objects/rule-fields",
    {
      params: Object.keys(params).length > 0 ? params : undefined,
    },
  );
  return response.data.data;
}
