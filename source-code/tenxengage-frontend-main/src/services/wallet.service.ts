import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type { RewardWalletResponse } from "@/types/wallet.types";

export async function getMyWallets(): Promise<RewardWalletResponse[]> {
  const response = await api.get<ApiResponse<RewardWalletResponse[]>>("/wallets/me");
  return response.data.data;
}

export async function getCompanyWallet(companyId: string): Promise<RewardWalletResponse[]> {
  const response = await api.get<ApiResponse<RewardWalletResponse[]>>(`/wallets/company/${companyId}`);
  return response.data.data;
}

export async function getUserWallets(userId: string): Promise<RewardWalletResponse[]> {
  const response = await api.get<ApiResponse<RewardWalletResponse[]>>(`/wallets/users/${userId}`);
  return response.data.data;
}
