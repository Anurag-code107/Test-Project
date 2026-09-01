// Adapted from: src/services/redemption-flow.service.ts (TanStack Query pattern)
import api from "@/lib/axios";
import type { ApiResponse, PaginatedResponse } from "@/types/api.types";
import type {
  RedemptionRequestResponse,
  RedemptionRequestDetailResponse,
  RedemptionHistoryFilters,
  RedemptionAdminHistoryResponse,
  RedemptionAdminHistoryFilters,
  TriggerExportRequest,
  ExportTriggerResult,
  RedemptionExportJobResponse,
  RedemptionExportJobDetailResponse,
} from "@/types/redemption-history/redemption-history.types";

const BASE = "/redemption/requests";

export async function getPersonalRedemptions(
  filters: RedemptionHistoryFilters,
  page: number,
  pageSize: number,
): Promise<PaginatedResponse<RedemptionRequestResponse>> {
  const response = await api.get<ApiResponse<PaginatedResponse<RedemptionRequestResponse>>>(BASE, {
    params: { ...filters, page, pageSize },
  });
  return response.data.data;
}

export async function getCompanyRedemptions(
  filters: RedemptionHistoryFilters,
  page: number,
  pageSize: number,
): Promise<PaginatedResponse<RedemptionRequestResponse>> {
  const response = await api.get<ApiResponse<PaginatedResponse<RedemptionRequestResponse>>>(`${BASE}/company`, {
    params: { ...filters, page, pageSize },
  });
  return response.data.data;
}

export async function getTenantRedemptions(
  filters: RedemptionAdminHistoryFilters,
  page: number,
  pageSize: number,
): Promise<PaginatedResponse<RedemptionAdminHistoryResponse>> {
  const response = await api.get<ApiResponse<PaginatedResponse<RedemptionAdminHistoryResponse>>>(`${BASE}/all`, {
    params: { ...filters, page, pageSize },
  });
  return response.data.data;
}

export async function getRedemptionDetail(id: string): Promise<RedemptionRequestDetailResponse> {
  const response = await api.get<ApiResponse<RedemptionRequestDetailResponse>>(`${BASE}/${id}`);
  return response.data.data;
}

export async function triggerExport(request: TriggerExportRequest): Promise<ExportTriggerResult> {
  const response = await api.post<ArrayBuffer>(`${BASE}/export`, request, {
    responseType: 'arraybuffer',
  });

  if (response.status === 202) {
    const json = JSON.parse(new TextDecoder().decode(response.data)) as ApiResponse<RedemptionExportJobResponse>;
    return { kind: 'async', job: json.data };
  }

  const contentDisposition = (response.headers['content-disposition'] as string | undefined) ?? '';
  const filenameMatch = contentDisposition.match(/filename="?([^"]+)"?/);
  const filename = filenameMatch?.[1] ?? 'redemption-history.csv';
  const mimeType = request.format === 'XLSX'
    ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    : 'text/csv';
  return { kind: 'sync', blob: new Blob([response.data], { type: mimeType }), filename };
}

export async function getExportJob(jobId: string): Promise<RedemptionExportJobResponse> {
  const response = await api.get<ApiResponse<RedemptionExportJobResponse>>(`${BASE}/export/${jobId}`);
  return response.data.data;
}

export async function getExportJobDownload(jobId: string): Promise<RedemptionExportJobDetailResponse> {
  const response = await api.get<ApiResponse<RedemptionExportJobDetailResponse>>(`${BASE}/export/${jobId}/download`);
  return response.data.data;
}
