// Adapted from: src/services/redemption-flow.service.ts (TanStack Query pattern)
import api from "@/lib/axios";
import type { ApiResponse, PaginatedResponse } from "@/types/api.types";
import type {
  SubmitReturnRequest,
  ReturnDetailResponse,
  ReturnSummaryResponse,
  ReturnQueueItemResponse,
  MyReturnsFilters,
  AdminReturnsFilters,
} from "@/types/redemption-returns.types";

const PARTNER_BASE = "/redemption/returns";
const ADMIN_BASE = "/redemption/admin/returns";

// ── Partner endpoints ────────────────────────────────────────────────────────

export async function submitReturn(dto: SubmitReturnRequest): Promise<ReturnDetailResponse> {
  const response = await api.post<ApiResponse<ReturnDetailResponse>>(PARTNER_BASE, dto);
  return response.data.data;
}

export async function getMyReturns(
  filters?: MyReturnsFilters,
): Promise<PaginatedResponse<ReturnSummaryResponse>> {
  const response = await api.get<ApiResponse<PaginatedResponse<ReturnSummaryResponse>>>(PARTNER_BASE, {
    params: filters,
  });
  return response.data.data;
}

export async function getReturn(
  id: string,
  isAdmin: boolean,
): Promise<ReturnDetailResponse> {
  const url = isAdmin ? `${ADMIN_BASE}/${id}` : `${PARTNER_BASE}/${id}`;
  const response = await api.get<ApiResponse<ReturnDetailResponse>>(url);
  return response.data.data;
}

export async function cancelReturn(id: string): Promise<void> {
  await api.delete(`${PARTNER_BASE}/${id}`);
}

// ── Admin endpoints ──────────────────────────────────────────────────────────

export async function getAdminReturns(
  filters?: AdminReturnsFilters,
): Promise<PaginatedResponse<ReturnQueueItemResponse>> {
  const response = await api.get<ApiResponse<PaginatedResponse<ReturnQueueItemResponse>>>(ADMIN_BASE, {
    params: filters,
  });
  return response.data.data;
}

export async function approveReturn(id: string): Promise<ReturnDetailResponse> {
  const response = await api.post<ApiResponse<ReturnDetailResponse>>(`${ADMIN_BASE}/${id}/approve`);
  return response.data.data;
}

export async function rejectReturn(
  id: string,
  rejectionReason: string,
): Promise<ReturnDetailResponse> {
  const response = await api.post<ApiResponse<ReturnDetailResponse>>(`${ADMIN_BASE}/${id}/reject`, {
    rejectionReason,
  });
  return response.data.data;
}

export async function resolveTimedOutReturn(
  id: string,
  resolution: "CONFIRM" | "REJECT",
  notes?: string,
): Promise<ReturnDetailResponse> {
  const response = await api.post<ApiResponse<ReturnDetailResponse>>(`${ADMIN_BASE}/${id}/resolve`, {
    resolution,
    notes,
  });
  return response.data.data;
}
