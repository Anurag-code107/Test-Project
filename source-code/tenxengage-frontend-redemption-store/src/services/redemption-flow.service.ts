// Adapted from: src/services/redemption-catalog.service.ts (TanStack Query pattern)
import api from "@/lib/axios";
import type { ApiResponse, PaginatedResponse } from "@/types/api.types";
import type {
  SubmitPersonalRedemptionRequest,
  SubmitCompanyRedemptionRequest,
  SubmitBankTransferRedemptionRequest,
  RedemptionSubmissionConfirmationResponse,
  RedemptionRequestResponse,
  RedemptionRequestDetailResponse,
  RedemptionRequestListParams,
} from "@/types/redemption-flow.types";

const BASE = "/redemption/requests";

export async function submitPersonalRedemption(
  req: SubmitPersonalRedemptionRequest,
): Promise<RedemptionSubmissionConfirmationResponse> {
  const response = await api.post<ApiResponse<RedemptionSubmissionConfirmationResponse>>(BASE, req);
  return response.data.data;
}

export async function submitCompanyRedemption(
  req: SubmitCompanyRedemptionRequest,
): Promise<RedemptionSubmissionConfirmationResponse> {
  const response = await api.post<ApiResponse<RedemptionSubmissionConfirmationResponse>>(`${BASE}/company`, req);
  return response.data.data;
}

export async function submitBankTransferRedemption(
  req: SubmitBankTransferRedemptionRequest,
): Promise<RedemptionSubmissionConfirmationResponse> {
  const response = await api.post<ApiResponse<RedemptionSubmissionConfirmationResponse>>(
    `${BASE}/bank-transfer`,
    req,
  );
  return response.data.data;
}

export async function getRedemptionRequests(
  params?: RedemptionRequestListParams,
): Promise<PaginatedResponse<RedemptionRequestResponse>> {
  const response = await api.get<ApiResponse<PaginatedResponse<RedemptionRequestResponse>>>(BASE, {
    params,
  });
  return response.data.data;
}

export async function getRedemptionRequest(id: string): Promise<RedemptionRequestDetailResponse> {
  const response = await api.get<ApiResponse<RedemptionRequestDetailResponse>>(`${BASE}/${id}`);
  return response.data.data;
}
