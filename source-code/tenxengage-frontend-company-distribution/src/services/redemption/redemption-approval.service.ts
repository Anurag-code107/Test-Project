// Adapted from: src/services/redemption-flow.service.ts (TanStack Query pattern)
import api from "@/lib/axios";
import type { ApiResponse, PaginatedResponse } from "@/types/api.types";
import type {
  ApprovalQueueItem,
  ApprovalQueueFilters,
  RedemptionRequestDetailResponse,
  RejectRedemptionRequest,
} from "@/types/redemption/redemption.types";

const BASE = "/redemption/requests";

export async function reject(
  redemptionId: string,
  body: RejectRedemptionRequest,
): Promise<RedemptionRequestDetailResponse> {
  const response = await api.post<ApiResponse<RedemptionRequestDetailResponse>>(
    `${BASE}/${redemptionId}/reject`,
    body,
  );
  return response.data.data;
}

export async function approve(
  redemptionId: string,
): Promise<RedemptionRequestDetailResponse> {
  const response = await api.post<ApiResponse<RedemptionRequestDetailResponse>>(
    `${BASE}/${redemptionId}/approve`,
  );
  return response.data.data;
}

export async function getApprovalQueue(
  filters: ApprovalQueueFilters,
): Promise<PaginatedResponse<ApprovalQueueItem>> {
  const response = await api.get<ApiResponse<PaginatedResponse<ApprovalQueueItem>>>(
    `${BASE}/approval-queue`,
    { params: filters },
  );
  return response.data.data;
}
