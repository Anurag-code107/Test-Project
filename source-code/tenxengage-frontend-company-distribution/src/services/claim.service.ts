import api from "@/lib/axios";
import type { ApiResponse, PaginatedResponse } from "@/types/api.types";
import type {
  ClaimResponse,
  ClaimDetailResponse,
  ClaimSummaryResponse,
  RewardBalanceResponse,
  ClaimListParams,
  RewardTransactionResponse,
  RewardTransactionListParams,
} from "@/types/claim.types";

export async function getClaims(
  params?: ClaimListParams,
): Promise<PaginatedResponse<ClaimResponse>> {
  const response = await api.get<ApiResponse<PaginatedResponse<ClaimResponse>>>(
    "/claims",
    { params },
  );
  return response.data.data;
}

export async function getClaimDetail(id: string): Promise<ClaimDetailResponse> {
  const response = await api.get<ApiResponse<ClaimDetailResponse>>(
    `/claims/${id}`,
  );
  return response.data.data;
}

export async function claimDeal(id: string): Promise<ClaimDetailResponse> {
  const response = await api.post<ApiResponse<ClaimDetailResponse>>(
    `/claims/${id}/claim`,
  );
  return response.data.data;
}

export async function unclaimDeal(
  id: string,
  comment: string,
): Promise<ClaimDetailResponse> {
  const response = await api.post<ApiResponse<ClaimDetailResponse>>(
    `/claims/${id}/unclaim`,
    { comment },
  );
  return response.data.data;
}

export async function updateClaim(
  id: string,
  data: {
    rewardAdjustments?: Record<string, string>;
    statusChange?: string;
    comment: string;
  },
): Promise<ClaimDetailResponse> {
  const response = await api.put<ApiResponse<ClaimDetailResponse>>(
    `/claims/${id}`,
    data,
  );
  return response.data.data;
}

export async function getClaimSummary(
  params?: Omit<ClaimListParams, "page" | "size">,
): Promise<ClaimSummaryResponse> {
  const response = await api.get<ApiResponse<ClaimSummaryResponse>>(
    "/claims/summary",
    { params },
  );
  return response.data.data;
}

export async function getRewardBalances(): Promise<RewardBalanceResponse[]> {
  const response =
    await api.get<ApiResponse<RewardBalanceResponse[]>>("/reward-balances");
  return response.data.data;
}

export async function getUserRewardBalances(
  userId: string,
): Promise<RewardBalanceResponse[]> {
  const response = await api.get<ApiResponse<RewardBalanceResponse[]>>(
    `/reward-balances/${userId}`,
  );
  return response.data.data;
}

export async function getRewardTransactions(
  params?: RewardTransactionListParams,
): Promise<PaginatedResponse<RewardTransactionResponse>> {
  const response = await api.get<
    ApiResponse<PaginatedResponse<RewardTransactionResponse>>
  >("/reward-transactions", { params });
  return response.data.data;
}

export async function getUserRewardTransactions(
  userId: string,
  params?: RewardTransactionListParams,
): Promise<PaginatedResponse<RewardTransactionResponse>> {
  const response = await api.get<
    ApiResponse<PaginatedResponse<RewardTransactionResponse>>
  >(`/reward-transactions/${userId}`, { params });
  return response.data.data;
}
