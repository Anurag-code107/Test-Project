// shape: contracts/models/redemption-analytics-summary.md + contracts/endpoints/redemption-analytics.yaml

export interface DateWindowDto {
  from: string;  // ISO 8601 date (YYYY-MM-DD)
  to: string;    // ISO 8601 date (YYYY-MM-DD)
}

export interface CurrencyTypeRateDto {
  currencyId: string;
  numerator: number;
  denominator: number;
  ratePercentage?: string; // omitted when hasActivity = false
  hasActivity: boolean;
}

export interface CurrencyTypeBalanceDto {
  currencyId: string;
  availableBalance: number;
  reservedBalance: number;
  totalOutstanding: number;
}

export interface RedemptionCountDto {
  total: number;
  byStatus: Record<string, number>;
  hasActivity: boolean;
}

export interface RedemptionAnalyticsSummaryResponse {
  dateWindow: DateWindowDto;
  redemptionRates: CurrencyTypeRateDto[];
  unredeemedBalances: CurrencyTypeBalanceDto[];
  failedCancelledRates: CurrencyTypeRateDto[];
  totalRedemptionCount: RedemptionCountDto;
}

export interface DateRange {
  from: string; // ISO 8601 date (YYYY-MM-DD)
  to: string;   // ISO 8601 date (YYYY-MM-DD)
}
