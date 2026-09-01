// Adapted from: src/services/redemption-flow.service.ts (TanStack Query pattern)
import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type { RedemptionAnalyticsSummaryResponse } from "@/types/redemption-analytics.types";

const BASE = "/redemption/analytics";

export async function getSummary(
  dateFrom: string,
  dateTo: string,
): Promise<RedemptionAnalyticsSummaryResponse> {
  const response = await api.get<ApiResponse<RedemptionAnalyticsSummaryResponse>>(BASE, {
    params: { dateFrom, dateTo },
  });
  return response.data.data;
}

export async function exportUnredeemedBalances(): Promise<Blob> {
  const response = await api.get<Blob>(`${BASE}/export`, {
    responseType: "blob",
  });
  return response.data;
}
