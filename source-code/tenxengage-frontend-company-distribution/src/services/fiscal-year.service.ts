import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  FiscalYearConfigResponse,
  FiscalYearLabelResponse,
  SaveFiscalYearConfigRequest,
} from "@/types/fiscal-year.types";

export async function getFiscalYearConfigs(): Promise<
  FiscalYearConfigResponse[]
> {
  const response = await api.get<ApiResponse<FiscalYearConfigResponse[]>>(
    "/fiscal-year-configs",
  );
  return response.data.data;
}

export async function getFiscalYearConfig(
  id: string,
): Promise<FiscalYearConfigResponse> {
  const response = await api.get<ApiResponse<FiscalYearConfigResponse>>(
    `/fiscal-year-configs/${id}`,
  );
  return response.data.data;
}

export async function getFiscalYearConfigByLabel(
  label: string,
): Promise<FiscalYearConfigResponse> {
  const response = await api.get<ApiResponse<FiscalYearConfigResponse>>(
    `/fiscal-year-configs/by-label/${label}`,
  );
  return response.data.data;
}

export async function getCurrentFiscalYearConfig(): Promise<FiscalYearConfigResponse> {
  const response = await api.get<ApiResponse<FiscalYearConfigResponse>>(
    "/fiscal-year-configs/current",
  );
  return response.data.data;
}

export async function getFiscalYearLabels(): Promise<
  FiscalYearLabelResponse[]
> {
  const response = await api.get<ApiResponse<FiscalYearLabelResponse[]>>(
    "/fiscal-year-configs/labels",
  );
  return response.data.data;
}

export async function createFiscalYearConfig(
  data: SaveFiscalYearConfigRequest,
): Promise<FiscalYearConfigResponse> {
  const response = await api.post<ApiResponse<FiscalYearConfigResponse>>(
    "/fiscal-year-configs",
    data,
  );
  return response.data.data;
}

export async function updateFiscalYearConfig(
  id: string,
  data: SaveFiscalYearConfigRequest,
): Promise<FiscalYearConfigResponse> {
  const response = await api.put<ApiResponse<FiscalYearConfigResponse>>(
    `/fiscal-year-configs/${id}`,
    data,
  );
  return response.data.data;
}

export async function deleteFiscalYearConfig(id: string): Promise<void> {
  await api.delete(`/fiscal-year-configs/${id}`);
}
