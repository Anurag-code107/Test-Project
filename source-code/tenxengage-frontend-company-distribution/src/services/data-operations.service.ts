import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  DataUploadResponse,
  TaggingJobResponse,
  SyncScheduleResponse,
  UpdateSyncScheduleRequest,
} from "@/types/data-operations.types";

export async function getUploadHistory(
  dataObjectId: string,
): Promise<DataUploadResponse[]> {
  const response = await api.get<ApiResponse<DataUploadResponse[]>>(
    `/data-operations/${dataObjectId}/uploads`,
  );
  return response.data.data;
}

export async function uploadFile(
  dataObjectId: string,
  file: File,
): Promise<DataUploadResponse> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await api.post<ApiResponse<DataUploadResponse>>(
    `/data-operations/${dataObjectId}/upload`,
    formData,
    { headers: { "Content-Type": "multipart/form-data" } },
  );
  return response.data.data;
}

export function getTemplateUrl(dataObjectId: string): string {
  return `/data-operations/${dataObjectId}/template`;
}

export async function downloadTemplate(dataObjectId: string): Promise<Blob> {
  const response = await api.get(`/data-operations/${dataObjectId}/template`, {
    responseType: "blob",
  });
  return response.data;
}

export async function triggerConnectorPull(
  dataObjectId: string,
): Promise<DataUploadResponse> {
  const response = await api.post<ApiResponse<DataUploadResponse>>(
    `/data-operations/${dataObjectId}/pull`,
  );
  return response.data.data;
}

export async function getTaggingHistory(): Promise<TaggingJobResponse[]> {
  const response = await api.get<ApiResponse<TaggingJobResponse[]>>(
    "/data-operations/tagging/history",
  );
  return response.data.data;
}

export async function triggerTaggingJob(): Promise<TaggingJobResponse> {
  const response = await api.post<ApiResponse<TaggingJobResponse>>(
    "/data-operations/tagging/run",
  );
  return response.data.data;
}

export async function getSyncSchedule(
  dataObjectId: string,
): Promise<SyncScheduleResponse> {
  const response = await api.get<ApiResponse<SyncScheduleResponse>>(
    `/data-operations/${dataObjectId}/sync-schedule`,
  );
  return response.data.data;
}

export async function updateSyncSchedule(
  dataObjectId: string,
  data: UpdateSyncScheduleRequest,
): Promise<SyncScheduleResponse> {
  const response = await api.put<ApiResponse<SyncScheduleResponse>>(
    `/data-operations/${dataObjectId}/sync-schedule`,
    data,
  );
  return response.data.data;
}
