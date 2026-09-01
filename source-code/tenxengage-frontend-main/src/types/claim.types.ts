export type ClaimStatus = "UNCLAIMED" | "CLAIMED";

export interface RewardBreakdown {
  monetary: Record<string, string>;
  nonMonetary: Record<string, string>;
}

export interface ClaimerInfo {
  userId: string;
  name: string;
  claimedAt: string;
}

export interface ClaimResponse {
  id: string;
  orderNumber: string;
  orderDate: string;
  status: ClaimStatus;
  sellerName: string;
  sellerId: string;
  partnerCompanyName: string;
  partnerCompanyId: string;
  region: string | null;
  totalAmount: number;
  totalMonetaryReward: number;
  rewardBreakdown: RewardBreakdown;
  claimers: ClaimerInfo[];
  eligibleIncentiveCount: number;
  primaryIncentiveName: string | null;
  eligibleIncentiveNames: string[];
  createdAt: string;
  updatedAt: string;
}

export interface EligibleIncentive {
  incentiveId: string;
  incentiveName: string;
  rewardBreakdown: RewardBreakdown;
  totalReward: number;
}

export interface IneligibleIncentive {
  incentiveId: string;
  incentiveName: string;
  reason: string;
}

export interface ClaimDetailResponse {
  id: string;
  orderNumber: string;
  orderDate: string;
  status: ClaimStatus;
  sellerName: string;
  sellerId: string;
  partnerCompanyName: string;
  partnerCompanyId: string;
  region: string;
  customerName: string;
  totalAmount: number;
  totalMonetaryReward: number;
  rewardBreakdown: RewardBreakdown;
  claimers: ClaimerInfo[];
  maxClaimersPerDeal: number;
  eligibleIncentives: EligibleIncentive[];
  ineligibleIncentives: IneligibleIncentive[];
  adminComment: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ClaimSummaryResponse {
  totalEarnings: string;
  currencyBreakdown: Record<string, string>;
  claimedCount: number;
  unclaimedCount: number;
}

export interface RewardBalanceResponse {
  currencyId: string;
  balance: string;
}

export interface ClaimListParams {
  status?: ClaimStatus;
  search?: string;
  startDate?: string;
  endDate?: string;
  region?: string;
  partnerCompanyId?: string;
  userId?: string;
  page?: number;
  size?: number;
}

export type RewardTransactionType = "earned" | "spent";

export interface RewardTransactionResponse {
  id: string;
  date: string;
  type: RewardTransactionType;
  currencyId: string;
  amount: string;
  incentiveId: string;
  incentiveName: string;
  claimActionId: string | null;
  purchaseOrderNumber: string | null;
}

export interface RewardTransactionListParams {
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}
