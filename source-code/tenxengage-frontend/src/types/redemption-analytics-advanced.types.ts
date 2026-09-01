// shape: contracts/models/redemption-advanced-analytics.md + contracts/endpoints/redemption-advanced-analytics.yaml

// ─── Shared ───────────────────────────────────────────────────────────────────

export interface DateWindowDto {
  from: string; // ISO 8601 date (YYYY-MM-DD)
  to: string;   // ISO 8601 date (YYYY-MM-DD)
}

// ─── Filter ───────────────────────────────────────────────────────────────────

export interface AdvancedAnalyticsFilters {
  dateFrom: string;  // ISO 8601 YYYY-MM-DD
  dateTo: string;
  region?: string;   // comma-separated, omit if empty
  role?: string;     // comma-separated, omit if empty
}

// ─── Item Breakdown (FR-08.1) ──────────────────────────────────────────────────

export interface ItemRedemptionDto {
  catalogItemId: string;
  catalogItemName: string;
  currencyId: string;
  totalRedeemedCount: number;
  totalRedeemedAmount: string;
  redemptionRate: number;
}

export interface ItemBreakdownResponse {
  dateWindow: DateWindowDto;
  items: ItemRedemptionDto[];
  lastRefreshedAt: string; // ISO 8601 date-time (Instant)
}

// ─── Segment Breakdown (FR-08.2) ───────────────────────────────────────────────

export interface SegmentRedemptionDto {
  region?: string | null;
  role?: string | null;
  currencyId: string;
  totalRedeemedCount: number;
  totalRedeemedAmount: string;
  redemptionRate: number;
}

export interface SegmentBreakdownResponse {
  dateWindow: DateWindowDto;
  segments: SegmentRedemptionDto[];
  lastRefreshedAt: string;
}

// ─── Time to First Redemption (FR-08.3) ────────────────────────────────────────

export interface RegionTimeToRedemptionDto {
  region?: string | null;
  avgHoursToFirstRedemption: number | null;
  medianHoursToFirstRedemption: number | null;
  sampleCount: number;
}

export interface TimeToFirstRedemptionResponse {
  filters: Record<string, unknown>;
  regions: RegionTimeToRedemptionDto[];
  lastRefreshedAt: string;
}

// ─── Redemption Rate Trend (FR-08.4) ───────────────────────────────────────────

export interface TrendDataPointDto {
  periodDate: string;
  currencyId: string;
  redeemedCount: number;
  redemptionRate: number;
}

export interface RedemptionTrendResponse {
  dateWindow: DateWindowDto;
  dataPoints: TrendDataPointDto[];
  lastRefreshedAt: string;
}

// ─── Liability Trend (FR-08.5) ─────────────────────────────────────────────────

export interface LiabilityDataPointDto {
  periodDate: string;
  currencyId: string;
  totalUnredeemedBalance: string;
}

export interface LiabilityTrendResponse {
  dateWindow: DateWindowDto;
  dataPoints: LiabilityDataPointDto[];
  lastRefreshedAt: string;
}

// ─── Failure Mode Breakdown (FR-08.7) ──────────────────────────────────────────

export interface FailureModeDto {
  processingMode: string; // INSTANT | BATCH | APPROVAL_REQUIRED
  catalogItemId: string;
  catalogItemName: string;
  currencyId: string;
  failedCount: number;
  cancelledCount: number;
  totalCount: number;
  failureRate: number;
}

export interface FailureBreakdownResponse {
  dateWindow: DateWindowDto;
  failureModes: FailureModeDto[];
  lastRefreshedAt: string;
}

// ─── Refresh Status (FR-08.8 / FR-08.11) ───────────────────────────────────────

export interface AnalyticsRefreshStatusResponse {
  // shape: contracts/models/redemption-advanced-analytics.md → AnalyticsRefreshStatusResponse
  lastRefreshedAt: string | null;
  isStale: boolean;
  stalenessThresholdHours: number;
}
