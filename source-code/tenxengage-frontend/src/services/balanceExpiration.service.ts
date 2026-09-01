// Adapted from: src/services/redemption-analytics.service.ts (TanStack Query pattern)
import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  BalanceExpirationPolicyResponse,
  ExpiringBalancePreviewResponse,
  BalanceBreakageReportResponse,
  UpsertBalanceExpirationPolicyRequest,
  GetExpiringSoonParams,
  GetBreakageParams,
} from "@/types/balanceExpiration.types";

const BASE = "/redemption/expiration";

export async function getPolicies(): Promise<BalanceExpirationPolicyResponse[]> {
  const response = await api.get<ApiResponse<BalanceExpirationPolicyResponse[]>>(
    `${BASE}/policies`,
  );
  return response.data.data;
}

export async function upsertPolicy(
  currencyId: string,
  body: UpsertBalanceExpirationPolicyRequest,
): Promise<BalanceExpirationPolicyResponse> {
  const response = await api.put<ApiResponse<BalanceExpirationPolicyResponse>>(
    `${BASE}/policies/${currencyId}`,
    body,
  );
  return response.data.data;
}

export async function getExpiringSoon(
  params?: GetExpiringSoonParams,
): Promise<ExpiringBalancePreviewResponse[]> {
  const response = await api.get<ApiResponse<ExpiringBalancePreviewResponse[]>>(
    `${BASE}/expiring-soon`,
    { params },
  );
  return response.data.data;
}

export async function getBreakage(
  params: GetBreakageParams,
): Promise<BalanceBreakageReportResponse> {
  const response = await api.get<ApiResponse<BalanceBreakageReportResponse>>(
    `${BASE}/breakage`,
    { params },
  );
  return response.data.data;
}

export async function exportBreakage(params: GetBreakageParams): Promise<Blob> {
  const response = await api.get<Blob>(`${BASE}/breakage/export`, {
    params,
    responseType: "blob",
  });
  return response.data;
}
