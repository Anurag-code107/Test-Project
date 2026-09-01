// Incentive Types — matches contracts/enums.md and contracts/models/incentive.md

export type IncentiveType = "SALES" | "TRAINING" | "ACTIVITY" | "JOURNEY";

export type IncentiveStatus =
  | "DRAFT"
  | "PENDING_APPROVAL"
  | "DENIED"
  | "ACTIVE"
  | "INACTIVE";

export type AllocationMethod = "EQUAL" | "WEIGHTED" | "PERFORMANCE_BASED";

export type BudgetMode = "GLOBAL" | "PER_LOCATION";

export type AudienceRuleType =
  | "LOCATION"
  | "PARTNER_TYPE"
  | "ROLE";

// Values are data-object field UUIDs. The backend resolves them to the appropriate rule type.
export type EligibilityRuleType = string;

export type RuleOperator =
  | "EQUALS"
  | "GREATER_THAN"
  | "GREATER_THAN_OR_EQUAL"
  | "LESS_THAN"
  | "BETWEEN"
  | "IN"
  | "NOT_IN";

export type PayoutType = "PERCENTAGE" | "FLAT";

export type PayoutAgainst = "TOTAL_BOOKING" | "ELIGIBLE_PRODUCTS";

export type CourseLevel = "BEGINNER" | "INTERMEDIATE" | "ADVANCED";

export type ActivityCategory =
  | "CONTENT_CREATION"
  | "COMPLIANCE"
  | "EVENT_PARTICIPATION"
  | "CUSTOMER_ENGAGEMENT"
  | "IMPLEMENTATION";

export type RewardCurrencyType = "MONETARY" | "NON_MONETARY";

// --- Core Models ---

export interface IncentiveBudget {
  totalBudget: string;
  /** @deprecated Legacy alias kept for hydration of older saved payloads — prefer `currencyId`. */
  currency?: string;
  currencyId?: string;
  allocationMethod: AllocationMethod;
  budgetMode: BudgetMode;
  budgetLocationLevelId?: string | null;
  locationAllocations?: LocationAllocationResponse[];
  maxPerPartner?: string;
  maxPerUser?: string;
}

/** One per-LocationValue budget allocation (any depth in the location hierarchy). */
export interface LocationAllocationRequest {
  locationValueId: string;
  amount: string;
}

export interface LocationAllocationResponse {
  locationValueId: string;
  locationLevelId: string;
  locationValueName: string;
  levelName: string;
  amount: string;
}

export interface AudienceRule {
  id?: string;
  ruleType: AudienceRuleType;
  /**
   * BUG-079: UUID for LOCATION (LocationValue.id, any depth) and ROLE (ClientRole.id);
   * free-text label for PARTNER_TYPE. Names are resolved to UUIDs at the frontend
   * boundary (builderRequestMapper for picker selections, excelTemplateParser for
   * uploads), so the wire format is identity-stable across renames.
   */
  ruleValue: string;
  /** Required when ruleType=LOCATION. Disambiguates the depth in the tenant's hierarchy. */
  locationLevelId?: string;
  /**
   * Display-only on responses (resolved server-side from the LocationValue join).
   * The mapper does NOT forward this on save — kept on the type so existing UI
   * rendering paths that read it from local state during a draft session compile,
   * and so legacy parser code paths can populate it for in-state preview before
   * mapper resolution.
   */
  locationValueName?: string;
}

export interface EligibilityRule {
  id?: string;
  ruleType: EligibilityRuleType;
  operator?: RuleOperator;
  value?: string;
  valueMax?: string;
  selectedProducts?: string[];
  customerTypes?: string[];
  /** Generic list values for dynamic LIST-type rule fields */
  listValues?: string[];
}

export interface EligibilityRuleGroup {
  id?: string;
  rules: EligibilityRule[];
}

export interface PayoutBand {
  id?: string;
  minAmount: string;
  maxAmount: string;
  payoutValue: string;
}

export interface PayoutConfig {
  id?: string;
  currencyId: string;
  payoutType: PayoutType;
  against?: PayoutAgainst;
  maxPerDeal?: string;
  bands: PayoutBand[];
}

export interface SalesRequirement {
  id?: string;
  name: string;
  eligibilityGroups: EligibilityRuleGroup[];
  payouts: PayoutConfig[];
}

export interface TrainingCourseAssignment {
  id?: string;
  courseId: string;
  courseName: string;
  courseCategory?: string;
  courseProvider?: string;
  courseDuration?: string;
  courseLevel?: CourseLevel;
  required: boolean;
  /** Whether the current user has completed this course */
  userCompleted?: boolean;
}

export interface ActivityDocumentRequirement {
  id?: string;
  name: string;
  description?: string;
  required: boolean;
}

export interface ActivityDefinition {
  id?: string;
  name: string;
  description?: string;
  categoryId: string;
  sortOrder: number;
  requiredDocuments: ActivityDocumentRequirement[];
  /** Whether the current user has completed this activity */
  userCompleted?: boolean;
}

export interface JourneyStage {
  id?: string;
  linkedIncentiveId: string;
  sortOrder: number;
}

export interface MonthlyProjection {
  month: string;
  revenue: string;
  cost: string;
  participants: number;
}

export interface ForecastLocationBreakdown {
  locationValueId: string;
  name: string;
  parentId: string | null;
  budgetUtilizedPct: string;
  netNewDeals: number;
  netNewBookings: string;
  roi: string;
  participationRate: string;
  budgetAllocated: string;
  budgetPredictedSpend: string;
}

export interface ForecastInsight {
  type: "strength" | "risk" | "opportunity" | "warning";
  title: string;
  detail: string;
  /** Self-rated by Claude, 0-100. Backend caps each scope at 3 by this value. */
  confidence: number;
}

export interface IncentiveForecast {
  id: string;
  incentiveId: string;
  estimatedRoi: string;
  estimatedParticipation: number;
  estimatedParticipationRate: string;
  estimatedTotalCost: string;
  estimatedRevenue: string;
  estimatedNetNewDeals: number;
  estimatedNetNewBookings: string;
  confidenceScore: string;
  dataQualityScore: string;
  modelVersion: string;
  locationBreakdown: ForecastLocationBreakdown[];
  monthlyProjections: MonthlyProjection[];
  similarIncentiveIds: string[];
  insights: ForecastInsight[];
  /**
   * Per-top-level-location insights. Keys are top-level location names
   * (whatever depth-0 is for this client — Region, Theater, Country, etc.);
   * values are arrays of up to 3 insights. Children inherit their parent's
   * insights via the UI; un-targeted top-levels are absent.
   */
  topLevelInsights?: Record<string, ForecastInsight[]>;
  reasoning: string | null;
  generatedAt: string;
}

// --- API Response Types ---

export interface DocumentSummary {
  id: string;
  name: string;
  documentType: string;
  fileType: string;
  size: string;
  downloadUrl?: string;
}

/** Document metadata sent in create/update requests (no id, no file binary). */
export interface DocumentInput {
  name: string;
  documentType: string;
  fileType: string;
  size: string;
}

export interface JourneyStageSummary {
  sortOrder: number;
  incentiveType: IncentiveType;
  incentiveName: string;
  incentiveDescription?: string;
  incentiveStatus: IncentiveStatus;
  userCompleted?: boolean;
}

/** Per-currency budget entry returned by the API */
export interface BudgetResponseEntry {
  totalBudget: string;
  currencyId: string;
  allocationMethod: string;
  budgetMode: string;
  budgetLocationLevelId?: string | null;
  locationAllocations?: LocationAllocationResponse[];
}

export interface IncentiveResponse {
  id: string;
  name: string;
  description?: string;
  incentiveType: IncentiveType;
  status: IncentiveStatus;
  startDate?: string;
  endDate?: string;
  budgetTotal?: string;
  budgetCurrency?: string;
  /** Per-currency budget entries (e.g. cash: $500K, points: $100K) */
  budgets?: BudgetResponseEntry[];
  createdByName: string;
  createdAt: string;
  updatedAt: string;
  rewardCurrencies?: string[];
  rewardMessage?: string;
  budgetUtilizationPercent?: number;
  documents?: DocumentSummary[];
  trainingCourseCount?: number;
  /** Number of required training courses (subset of trainingCourseCount) */
  trainingRequiredCount?: number;
  activityDefinitionCount?: number;
  partnerProgressCompleted?: number;
  partnerProgressLabel?: string;
  journeyStages?: JourneyStageSummary[];
  requiresApproval?: boolean;
  statusChangedAt?: string;
  userCompleted?: boolean;
  userCompletedAt?: string;
}

export interface BudgetResponseItem {
  totalBudget: string;
  currencyId: string;
  allocationMethod: string;
  budgetMode: string;
  budgetLocationLevelId?: string | null;
  locationAllocations?: LocationAllocationResponse[];
}

export interface IncentiveDetailResponse extends Omit<
  IncentiveResponse,
  "journeyStages"
> {
  timezone?: string;
  budget?: IncentiveBudget;
  budgets?: BudgetResponseItem[];
  /** Reward currency IDs selected for this incentive (e.g. ["cash", "points"]) */
  rewardCurrencies?: string[];
  /** Message shown to partners (e.g. "Earn up to $5,000") */
  rewardMessage?: string;
  /** Per-currency reward amounts (e.g. { cash: "500", points: "100" }) */
  rewardAmounts?: Record<string, string>;
  audienceRules: AudienceRule[];
  salesRequirements?: SalesRequirement[];
  trainingCourses?: TrainingCourseAssignment[];
  activityDefinitions?: ActivityDefinition[];
  journeyStages?: JourneyStage[];
  journeySequential?: boolean;
  forecast?: IncentiveForecast;
  fiscalYears?: string[];
  fiscalQuarters?: string[];
  trainingRequiredCount?: number;
  countriesText?: string;
  specificPartners?: string;
  requiresApproval?: boolean;
  approvers?: Array<{ id: string; email: string; category: string }>;
  requiredApprovals?: number;
  approvalStatus?: ApprovalStatusResponse;
  /** Max number of unique users that can claim rewards against a single PO# */
  maxClaimersPerDeal?: number;
  /** Max total payout to a single partner company */
  maxPerPartner?: string;
  /** Max total payout to a single user (SALES only) */
  maxPerUser?: string;
  /** JSON-serialized custom field values for audience dynamic fields */
  customFieldValues?: string;
}

// --- Approval Status Types ---

export interface ApproverStatus {
  id: string;
  email: string;
  category: string;
  sortOrder: number;
  decision: "APPROVED" | "REJECTED" | null;
  decidedAt: string | null;
  comment: string | null;
}

export interface ApprovalStatusResponse {
  requiredApprovals: number;
  approvedCount: number;
  rejectedCount: number;
  pendingCount: number;
  approvers: ApproverStatus[];
}

// --- Request Types ---

/** Shape expected by the backend for each budget entry */
export interface BudgetRequestEntry {
  totalBudget: string;
  currencyId: string;
  allocationMethod: string;
  budgetMode?: string;
  budgetLocationLevelId?: string | null;
  locationAllocations?: LocationAllocationRequest[];
}

export interface CreateIncentiveRequest {
  name: string;
  description?: string;
  incentiveType: IncentiveType;
  startDate?: string;
  endDate?: string;
  timezone?: string;
  budgets?: BudgetRequestEntry[];
  maxPerPartner?: string;
  maxPerUser?: string;
  maxPerPartnerByCurrency?: string;
  maxPerUserByCurrency?: string;
  audienceRules?: AudienceRule[];
  rewardCurrencies?: string[];
  rewardMessage?: string;
  rewardAmounts?: Record<string, string>;
  salesRequirements?: SalesRequirement[];
  trainingCourses?: TrainingCourseAssignment[];
  activityDefinitions?: ActivityDefinition[];
  journeyStages?: JourneyStage[];
  journeySequential?: boolean;
  documents?: DocumentInput[];
  fiscalYears?: string[];
  fiscalQuarters?: string[];
  trainingRequiredCount?: number;
  countriesText?: string;
  specificPartners?: string;
  customFieldValues?: string;
  maxClaimersPerDeal?: number;
  requiresApproval?: boolean;
  approvers?: Array<{ email: string; category: string }>;
  requiredApprovals?: number;
}

export interface UpdateIncentiveRequest {
  name?: string;
  description?: string;
  startDate?: string;
  endDate?: string;
  timezone?: string;
  budgets?: BudgetRequestEntry[];
  maxPerPartner?: string;
  maxPerUser?: string;
  maxPerPartnerByCurrency?: string;
  maxPerUserByCurrency?: string;
  audienceRules?: AudienceRule[];
  rewardCurrencies?: string[];
  rewardMessage?: string;
  rewardAmounts?: Record<string, string>;
  salesRequirements?: SalesRequirement[];
  trainingCourses?: TrainingCourseAssignment[];
  activityDefinitions?: ActivityDefinition[];
  journeyStages?: JourneyStage[];
  journeySequential?: boolean;
  documents?: DocumentInput[];
  fiscalYears?: string[];
  fiscalQuarters?: string[];
  trainingRequiredCount?: number;
  countriesText?: string;
  specificPartners?: string;
  customFieldValues?: string;
  maxClaimersPerDeal?: number;
  requiresApproval?: boolean;
  approvers?: Array<{ email: string; category: string }>;
  requiredApprovals?: number;
}

export interface UpdateIncentiveStatusRequest {
  status: IncentiveStatus;
}

export interface CloneIncentiveRequest {
  name: string;
  description?: string;
}

// --- Reference Data Types ---

export interface ProductSKU {
  id: string;
  sku: string;
  name: string;
  category: string;
}

export interface ProductCategory {
  name: string;
  products: ProductSKU[];
}

export interface CreateProductRequest {
  name: string;
  category?: string;
}

export interface ProductUploadResponse {
  added: number;
  skipped: number;
  products: ProductSKU[];
}

export interface LmsCourse {
  id: string;
  externalCourseId?: string | null;
  name: string;
  description: string;
  category: string;
  duration?: string;
  level?: string;
  provider?: string;
}

export interface LmsCourseCategory {
  name: string;
  courses: LmsCourse[];
}

export interface RewardCurrency {
  id: string;
  label: string;
  type: RewardCurrencyType;
  unit: string;
  isCurrencyFormatted: boolean;
  conversionRate?: number;
}

// --- Display Helpers ---

export const INCENTIVE_TYPE_LABELS: Record<IncentiveType, string> = {
  SALES: "Sales Incentive",
  TRAINING: "Training Incentive",
  ACTIVITY: "Activity Incentive",
  JOURNEY: "Journey Incentive",
};

export const INCENTIVE_STATUS_LABELS: Record<IncentiveStatus, string> = {
  DRAFT: "Draft",
  PENDING_APPROVAL: "Pending Approval",
  DENIED: "Denied",
  ACTIVE: "Active",
  INACTIVE: "Inactive",
};

export const INCENTIVE_TYPE_DESCRIPTIONS: Record<IncentiveType, string> = {
  SALES:
    "Reward partners for achieving sales targets with tiered payouts and product-specific rules.",
  TRAINING:
    "Incentivize course completions and certifications from your LMS catalog.",
  ACTIVITY:
    "Drive specific actions like demos, site visits, or event participation with document verification.",
  JOURNEY:
    "Create multi-stage programs that combine sales, training, and activity incentives in sequence.",
};
