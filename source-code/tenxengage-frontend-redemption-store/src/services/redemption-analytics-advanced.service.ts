// Adapted from: src/services/redemption-analytics.service.ts (TanStack Query pattern)
// shape: contracts/endpoints/redemption-advanced-analytics.yaml
import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  AdvancedAnalyticsFilters,
  AnalyticsRefreshStatusResponse,
  ItemBreakdownResponse,
  SegmentBreakdownResponse,
  TimeToFirstRedemptionResponse,
  RedemptionTrendResponse,
  LiabilityTrendResponse,
  FailureBreakdownResponse,
} from "@/types/redemption-analytics-advanced.types";

const BASE = "/redemption/analytics/advanced";

export async function getRefreshStatus(): Promise<AnalyticsRefreshStatusResponse> {
  const response = await api.get<ApiResponse<AnalyticsRefreshStatusResponse>>(
    `${BASE}/refresh-status`,
  );
  return response.data.data;
}

export async function getItemBreakdown(
  filters: AdvancedAnalyticsFilters,
): Promise<ItemBreakdownResponse> {
  const response = await api.get<ApiResponse<ItemBreakdownResponse>>(
    `${BASE}/item-breakdown`,
    { params: filters },
  );
  return response.data.data;
}

export async function getSegmentBreakdown(
  filters: AdvancedAnalyticsFilters,
): Promise<SegmentBreakdownResponse> {
  const response = await api.get<ApiResponse<SegmentBreakdownResponse>>(
    `${BASE}/segment-breakdown`,
    { params: filters },
  );
  return response.data.data;
}

export async function getTimeToFirstRedemption(
  filters: AdvancedAnalyticsFilters,
): Promise<TimeToFirstRedemptionResponse> {
  const response = await api.get<ApiResponse<TimeToFirstRedemptionResponse>>(
    `${BASE}/time-to-first-redemption`,
    { params: filters },
  );
  return response.data.data;
}

export async function getRedemptionTrend(
  dateFrom: string,
  dateTo: string,
): Promise<RedemptionTrendResponse> {
  const response = await api.get<ApiResponse<RedemptionTrendResponse>>(
    `${BASE}/trend`,
    { params: { dateFrom, dateTo } },
  );
  return response.data.data;
}

export async function getLiabilityTrend(
  dateFrom: string,
  dateTo: string,
): Promise<LiabilityTrendResponse> {
  const response = await api.get<ApiResponse<LiabilityTrendResponse>>(
    `${BASE}/liability-trend`,
    { params: { dateFrom, dateTo } },
  );
  return response.data.data;
}

export async function getFailureBreakdown(
  filters: AdvancedAnalyticsFilters,
): Promise<FailureBreakdownResponse> {
  const response = await api.get<ApiResponse<FailureBreakdownResponse>>(
    `${BASE}/failure-breakdown`,
    { params: filters },
  );
  return response.data.data;
}

/**
 * Export the liability trend as a CSV file (Blob).
 *
 * On 429 (rate limited): parses the `Retry-After` header and rethrows a
 * typed error with `retryAfterSeconds` so the UI can show the countdown.
 * Uses `isAxiosError` for safe type-narrowing (anti-pattern: inline unsafe cast).
 */
export class ExportRateLimitedError extends Error {
  constructor(public readonly retryAfterSeconds: number) {
    super(`Export rate limited — retry in ${retryAfterSeconds}s`);
    this.name = "ExportRateLimitedError";
  }
}

export async function exportLiabilityTrendCsv(
  dateFrom: string,
  dateTo: string,
): Promise<Blob> {
  try {
    const response = await api.get(`${BASE}/liability-trend/export`, {
      params: { dateFrom, dateTo },
      responseType: "blob",
    });
    return response.data as Blob;
  } catch (err) {
    const { isAxiosError } = await import("axios");
    if (isAxiosError(err) && err.response?.status === 429) {
      const retryAfter = Number(err.response.headers["retry-after"] ?? 60);
      throw new ExportRateLimitedError(Number.isFinite(retryAfter) ? retryAfter : 60);
    }
    throw err;
  }
}
