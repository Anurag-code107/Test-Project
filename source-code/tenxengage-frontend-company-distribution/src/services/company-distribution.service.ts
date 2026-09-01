import api from "@/lib/axios";
import type { ApiResponse, PaginatedResponse } from "@/types/api.types";
import type {
  CompanyAward,
  CompanyDistribution,
  CreateCompanyDistributionRequest,
  DistributionCatalogItem,
  DistributionGiftCardSku,
  DistributionRail,
  DistributionRecipient,
  FundCompanyWalletRequest,
} from "@/types/company-distribution.types";
import type { RewardWalletResponse } from "@/types/wallet.types";

const BASE = "/redemption/distribution";

/** Sellers of the caller's company, each annotated with whether this rail can reach them. */
export async function getRecipients(rail: DistributionRail): Promise<DistributionRecipient[]> {
  const response = await api.get<ApiResponse<DistributionRecipient[]>>(`${BASE}/recipients`, {
    params: { rail },
  });
  return response.data.data;
}

/**
 * Gift cards available to distribute. Distinct from the personal store's catalog: this one is filtered
 * to what the server will actually accept and carries the effective amount bounds.
 */
export async function getDistributableCatalog(): Promise<DistributionCatalogItem[]> {
  const response = await api.get<ApiResponse<DistributionCatalogItem[]>>(`${BASE}/catalog`);
  return response.data.data;
}

/**
 * Every gift-card SKU the provider offers.
 *
 * Not `/catalog`, which returns only the client's curated items — a partner admin distributing to their own
 * sellers picks from the provider's whole catalogue, and the server backs whichever they pick.
 */
export async function getDistributableGiftCards(): Promise<DistributionGiftCardSku[]> {
  const response = await api.get<ApiResponse<DistributionGiftCardSku[]>>(`${BASE}/gift-cards`);
  return response.data.data;
}

/** Returns 202 — funds are reserved, recipients are paid asynchronously. */
export async function createDistribution(
  payload: CreateCompanyDistributionRequest,
): Promise<CompanyDistribution> {
  const response = await api.post<ApiResponse<CompanyDistribution>>(BASE, payload);
  return response.data.data;
}

export async function getDistributions(params: {
  rail?: DistributionRail;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
}): Promise<PaginatedResponse<CompanyDistribution>> {
  const response = await api.get<ApiResponse<PaginatedResponse<CompanyDistribution>>>(BASE, {
    params,
  });
  return response.data.data;
}

export async function getDistribution(id: string): Promise<CompanyDistribution> {
  const response = await api.get<ApiResponse<CompanyDistribution>>(`${BASE}/${id}`);
  return response.data.data;
}

export async function getMyAwards(params: {
  page?: number;
  size?: number;
}): Promise<PaginatedResponse<CompanyAward>> {
  const response = await api.get<ApiResponse<PaginatedResponse<CompanyAward>>>(`${BASE}/awards`, {
    params,
  });
  return response.data.data;
}

export async function getMyAward(awardId: string): Promise<CompanyAward> {
  const response = await api.get<ApiResponse<CompanyAward>>(`${BASE}/awards/${awardId}`);
  return response.data.data;
}

/** CLIENT_ADMIN only. Idempotent on `reference`. */
export async function fundCompanyWallet(
  companyId: string,
  payload: FundCompanyWalletRequest,
): Promise<RewardWalletResponse> {
  const response = await api.post<ApiResponse<RewardWalletResponse>>(
    `/wallets/company/${companyId}/fund`,
    payload,
  );
  return response.data.data;
}
