export interface TrendDataPoint {
  label: string;
  value: number;
}

export interface MetricResponse {
  value: number;
  subValue: string | null;
  trendPercent: number | null;
  trendData: TrendDataPoint[];
}

export interface CurrencyAmount {
  amount: number;
  percent: number;
}

export interface CurrencyCount {
  count: number;
}

export interface RewardBreakdownData {
  monetary: {
    cash: CurrencyAmount;
    points: CurrencyAmount;
  };
  nonMonetary: {
    credits: CurrencyCount;
    tickets: CurrencyCount;
  };
}

export interface ParticipationMetricsResponse {
  partnerFiltered: boolean;
  // Global view
  partnerCompaniesEnrolled: MetricResponse | null;
  partnerUsersEnrolled: MetricResponse | null;
  companiesEarningRewards: MetricResponse | null;
  // Partner view
  partnerEnrolledUsers: MetricResponse | null;
  usersEarningRewards: MetricResponse | null;
  userClaimsMade: MetricResponse | null;
}

export interface IncentivePerformanceResponse {
  totalRewardsEarned: MetricResponse;
  budgetUtilized: MetricResponse;
  usersParticipating: MetricResponse;
  rewardBreakdown: RewardBreakdownData;
  totalBudget: number;
}

export interface PartnerCompanySearchResult {
  id: string;
  name: string;
  region: string;
  activeUserCount: number;
}

export type HomeDateFilter =
  | "LAST_30_DAYS"
  | "THIS_QUARTER"
  | "THIS_YEAR"
  | "CUSTOM";
export type HomeIncentiveTypeFilter =
  | "ALL"
  | "SALES"
  | "ENABLEMENT"
  | "JOURNEYS";
export type HomeRegion = "GLOBAL" | "AMERICAS" | "LATAM" | "EMEAR" | "APJ";

export interface HomeMetricsParams {
  dateFilter: HomeDateFilter;
  startDate?: string;
  endDate?: string;
  region: HomeRegion;
  partnerCompanyId?: string;
}

export interface IncentivePerformanceParams extends HomeMetricsParams {
  incentiveType: HomeIncentiveTypeFilter;
}

export interface ProgramPerformanceResponse {
  totalRewardsEarned: MetricResponse;
  budgetUtilized: MetricResponse;
  usersParticipating: MetricResponse;
  rewardBreakdown: RewardBreakdownData;
  totalBudget: number;
  partnerFiltered: boolean;
  partnerCompaniesEnrolled: MetricResponse | null;
  partnerUsersEnrolled: MetricResponse | null;
  companiesEarningRewards: MetricResponse | null;
  partnerEnrolledUsers: MetricResponse | null;
  usersEarningRewards: MetricResponse | null;
  userClaimsMade: MetricResponse | null;
  currentQuarterLabel: string;
}

export interface ProgramPerformanceParams {
  region: string;
  partnerCompanyId?: string;
}
