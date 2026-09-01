import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  RewardCurrencyResponse,
  SaveRewardCurrencyRequest,
} from "@/types/reward-currency.types";

export async function getRewardCurrencies(): Promise<RewardCurrencyResponse[]> {
  const response =
    await api.get<ApiResponse<RewardCurrencyResponse[]>>("/currencies");
  return response.data.data;
}

export async function getRewardCurrency(
  id: string,
): Promise<RewardCurrencyResponse> {
  const response = await api.get<ApiResponse<RewardCurrencyResponse>>(
    `/currencies/${id}`,
  );
  return response.data.data;
}

export async function createRewardCurrency(
  data: SaveRewardCurrencyRequest,
): Promise<RewardCurrencyResponse> {
  const response = await api.post<ApiResponse<RewardCurrencyResponse>>(
    "/currencies",
    data,
  );
  return response.data.data;
}

export async function updateRewardCurrency(
  id: string,
  data: SaveRewardCurrencyRequest,
): Promise<RewardCurrencyResponse> {
  const response = await api.put<ApiResponse<RewardCurrencyResponse>>(
    `/currencies/${id}`,
    data,
  );
  return response.data.data;
}

export async function deleteRewardCurrency(id: string): Promise<void> {
  await api.delete(`/currencies/${id}`);
}
