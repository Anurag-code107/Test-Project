export type RewardCurrencyType = "MONETARY" | "NON_MONETARY";

export interface RewardCurrencyResponse {
  id: string;
  code: string;
  name: string;
  type: RewardCurrencyType;
  conversionRate?: number;
  unit: string;
  isCurrencyFormatted: boolean;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SaveRewardCurrencyRequest {
  code: string;
  name: string;
  type: RewardCurrencyType;
  conversionRate?: number;
  unit: string;
  isCurrencyFormatted: boolean;
}
