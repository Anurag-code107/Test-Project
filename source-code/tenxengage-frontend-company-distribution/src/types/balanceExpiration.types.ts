// shape: contracts/models/balance-expiration-policy.md
// shape: contracts/models/balance-breakage-report.md
// DO NOT hand-write — copied from contracts/endpoints/balance-expiration.yaml
// and contracts/models/*.md

export type ExpirationMode = "INACTIVITY" | "FIXED_DATE";

export interface UpsertBalanceExpirationPolicyRequest {
  enabled: boolean;
  expirationMode: ExpirationMode;
  /** Required when expirationMode = INACTIVITY (bounds [30, 1825]); null when FIXED_DATE */
  inactivityDays: number | null;
  /** Required when expirationMode = FIXED_DATE and must be in the future; null when INACTIVITY. ISO 8601 date string */
  fixedExpiryDate: string | null;
  leadTimeDays: number;
}

// shape: contracts/models/balance-expiration-policy.md
export interface BalanceExpirationPolicyResponse {
  currencyId: string;
  currencyDisplayName: string;
  enabled: boolean;
  expirationMode: ExpirationMode;
  /** Present when expirationMode = INACTIVITY */
  inactivityDays: number | null;
  /** Present when expirationMode = FIXED_DATE. ISO 8601 date string */
  fixedExpiryDate: string | null;
  leadTimeDays: number;
  /** ISO 8601 datetime; null when the policy has never been enabled */
  enabledAt: string | null;
  /** ISO 8601 datetime */
  updatedAt: string;
}

// shape: contracts/models/balance-breakage-report.md (ExpiringBalancePreviewResponse)
export interface ExpiringBalancePreviewResponse {
  currencyId: string;
  currencyDisplayName: string;
  /** ISO 8601 date string */
  scheduledExpiryDate: string;
  affectedWalletCount: number;
  /** BigDecimal as string */
  totalAmountAtRisk: string;
}

// shape: contracts/models/balance-breakage-report.md (BreakageRowDto)
export interface BreakageRowDto {
  /** ISO 8601 date string */
  periodStart: string;
  /** ISO 8601 date string */
  periodEnd: string;
  currencyId: string;
  currencyDisplayName: string;
  expiredCount: number;
  /** BigDecimal as string */
  totalExpiredAmount: string;
}

export type BreakageGranularity = "MONTH" | "QUARTER";

// shape: contracts/models/balance-breakage-report.md (BalanceBreakageReportResponse)
export interface BalanceBreakageReportResponse {
  /** ISO 8601 date string */
  from: string;
  /** ISO 8601 date string */
  to: string;
  granularity: BreakageGranularity;
  rows: BreakageRowDto[];
}

export interface GetExpiringSoonParams {
  withinDays?: number;
  currencyId?: string;
}

export interface GetBreakageParams {
  from: string;
  to: string;
  currencyId?: string;
  granularity?: BreakageGranularity;
}
