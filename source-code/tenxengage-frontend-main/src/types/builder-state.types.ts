import type {
  IncentiveType,
  IncentiveBudget,
  AudienceRule,
  SalesRequirement,
  TrainingCourseAssignment,
  ActivityDefinition,
  JourneyStage,
  IncentiveForecast,
  IncentiveDetailResponse,
  DocumentSummary,
} from "@/types/incentive.types";

// --- Flow State Machine ---

export type FlowState =
  | "entry_menu"
  | "type_select"
  | "enablement_select"
  | "existing_select"
  | "template_select"
  | "template_upload"
  | "builder"
  | "forecasting";

export type BuilderMode = "ai" | "manual";

export type BuilderOrigin = "scratch" | "template" | "existing" | "edit";

export type BuilderStep =
  | "basics"
  | "audience"
  | "budget"
  | "schedule"
  | "criteria"
  | "approval";

export const BUILDER_STEPS: BuilderStep[] = [
  "basics",
  "schedule",
  "audience",
  "budget",
  "criteria",
  "approval",
];

export const STEP_LABELS: Record<BuilderStep, string> = {
  basics: "Basic Information",
  schedule: "Incentive Timeline",
  audience: "Participant Eligibility",
  budget: "Budget Criteria",
  criteria: "Incentive Criteria",
  approval: "Approval Criteria",
};

export const STEP_SUBTITLES: Record<BuilderStep, string> = {
  basics: "Name and description",
  schedule: "Duration and active dates",
  audience: "Who can participate",
  budget: "Reward currencies and budget",
  criteria: "Rules and requirements",
  approval: "Configure approval workflow",
};

export const STEP_SHORT_LABELS: Record<BuilderStep, string> = {
  basics: "Basic",
  schedule: "Timeline",
  audience: "Eligibility",
  budget: "Budget",
  criteria: "Criteria",
  approval: "Approval",
};

/** Type-specific Step 5 labels */
export const STEP5_LABELS: Record<
  import("@/types/incentive.types").IncentiveType,
  string
> = {
  SALES: "Incentive Criteria",
  TRAINING: "Training Courses",
  ACTIVITY: "Activity Setup",
  JOURNEY: "Journey Stages",
};

export const STEP5_SUBTITLES: Record<
  import("@/types/incentive.types").IncentiveType,
  string
> = {
  SALES: "Rules and requirements",
  TRAINING: "LMS course assignment",
  ACTIVITY: "Define activities and required documents",
  JOURNEY: "Select incentives for the journey",
};

export const STEP5_DESCRIPTIONS: Record<
  import("@/types/incentive.types").IncentiveType,
  string
> = {
  SALES: "Define the specific rules and reward mechanics for this incentive",
  TRAINING:
    "Select training courses from your LMS and set completion requirements",
  ACTIVITY:
    "Define the activities partners must complete and the documents they need to upload",
  JOURNEY: "Select 2 or more existing incentives to form the journey stages",
};

export const STEP5_SHORT_LABELS: Record<
  import("@/types/incentive.types").IncentiveType,
  string
> = {
  SALES: "Criteria",
  TRAINING: "Courses",
  ACTIVITY: "Activities",
  JOURNEY: "Stages",
};

// --- Reference Data ---

export const FISCAL_QUARTERS = ["Q1", "Q2", "Q3", "Q4"] as const;

// --- Step Data ---

export interface BasicsData {
  name: string;
  description: string;
  rewardMessage: string;
  incentiveType: IncentiveType | null;
}

export interface AudienceData {
  rules: AudienceRule[];
  countriesText: string;
  userRoles: string[];
  partnerTypes: string[];
  specificPartners: string;
  /** Values for custom fields added via builder configuration */
  dynamicFields: Record<string, unknown>;
  /** Dynamic location selections keyed by location level ID */
  locationSelections: Record<string, string[]>;
}

export interface BudgetData {
  budget: IncentiveBudget | null;
  selectedCurrencies: string[];
  /**
   * "per-region" is a legacy alias kept only for incoming AI-chat payloads
   * and old hydrated state — it is normalized to "per-location" by
   * `UPDATE_BUDGET` and the request mapper. New code should write
   * "global" or "per-location".
   */
  budgetMode: "global" | "per-region" | "per-location";
  globalBudgets: Record<string, string>;
  /**
   * @deprecated Legacy name-keyed per-region budgets, surviving only as the
   * staging area for AI-chat tool calls that still emit names. Reducer
   * normalizes this into `locationBudgets` (id-keyed) when the hierarchy is
   * available. UI never reads it.
   */
  regionBudgets: Record<string, Record<string, string>>;
  /** LocationLevel UUID hint for the depth at which the user allocated. */
  budgetLocationLevelId: string | null;
  /**
   * Per-location budgets keyed by currency id, then by `locationValueId`
   * (UUID). Any depth in the location hierarchy is valid. Sparse — only
   * user-typed amounts are stored; auto-fill residuals are computed at
   * validation/save time by `budgetTreeHelpers`.
   */
  locationBudgets: Record<string, Record<string, string>>;
  /** Per-currency max caps: { "cash": "5000", "points": "10000" } */
  maxPerPartnerByCurrency: Record<string, string>;
  maxPerUserByCurrency: Record<string, string>;
  /** @deprecated — use maxPerPartnerByCurrency/maxPerUserByCurrency instead */
  maxPerPartner: string;
  maxPerUser: string;
  /** Per-currency reward amounts for completion-based incentives (Training/Activity/Journey) */
  rewardAmounts: Record<string, string>;
  /** Whether a Journey incentive has its own budget & rewards (vs individual rewards only) */
  journeyHasOwnRewards: boolean;
  /** Max claimers per PO# — sales incentives only */
  maxClaimersPerDeal: string;
}

export interface ScheduleData {
  startDate: string;
  endDate: string;
  fiscalYears: string[];
  fiscalQuarters: string[];
}

export interface CriteriaData {
  salesRequirements: SalesRequirement[];
  trainingCourses: TrainingCourseAssignment[];
  trainingRequiredCount: number;
  activityDefinitions: ActivityDefinition[];
  journeyStages: JourneyStage[];
  journeySequential: boolean;
}

export interface ApproverEntry {
  id: string;
  email: string;
  category: string;
}

export interface ApprovalData {
  requiresApproval: boolean;
  approvers: ApproverEntry[];
  requiredApprovals: number;
}

// --- Chat Messages ---

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: string;
  isStreaming?: boolean;
}

// --- Builder State ---

export interface BuilderState {
  flowState: FlowState;
  mode: BuilderMode;
  builderOrigin: BuilderOrigin;
  activeStep: BuilderStep;
  expandedSteps: BuilderStep[];
  completedSteps: BuilderStep[];

  // Step data
  basics: BasicsData;
  audience: AudienceData;
  budgetData: BudgetData;
  schedule: ScheduleData;
  criteria: CriteriaData;
  approval: ApprovalData;

  // AI Copilot
  chatMessages: ChatMessage[];
  isChatLoading: boolean;

  // Criteria editor panel
  showCriteriaEditor: boolean;

  // Forecasting
  showForecasting: boolean;
  forecast: IncentiveForecast | null;
  isForecastLoading: boolean;

  // Edit mode
  editingIncentiveId: string | null;
  existingDocuments: DocumentSummary[];

  // Dirty state
  isDirty: boolean;

  // AI-initiated creation
  pendingCreate: boolean;
  isCreating: boolean;

  // AI Copilot is actively streaming/filling fields — lock the builder UI to prevent user interference
  aiLocked: boolean;

  // Document uploaded via template flow — auto-processed by AI copilot on mount
  pendingDocumentFile: File | null;
}

// --- Actions ---

export type BuilderAction =
  | { type: "SET_FLOW_STATE"; payload: FlowState }
  | { type: "SET_MODE"; payload: BuilderMode }
  | { type: "SET_ORIGIN"; payload: BuilderOrigin }
  | { type: "SET_ACTIVE_STEP"; payload: BuilderStep }
  | { type: "TOGGLE_STEP"; payload: BuilderStep }
  | { type: "EXPAND_STEP"; payload: BuilderStep }
  | { type: "MARK_STEP_COMPLETE"; payload: BuilderStep }
  | { type: "MARK_STEP_INCOMPLETE"; payload: BuilderStep }
  | { type: "UPDATE_BASICS"; payload: Partial<BasicsData> }
  | { type: "UPDATE_AUDIENCE"; payload: Partial<AudienceData> }
  | { type: "UPDATE_BUDGET"; payload: Partial<BudgetData> }
  | { type: "UPDATE_SCHEDULE"; payload: Partial<ScheduleData> }
  | { type: "UPDATE_CRITERIA"; payload: Partial<CriteriaData> }
  | { type: "UPDATE_APPROVAL"; payload: Partial<ApprovalData> }
  | { type: "ADD_CHAT_MESSAGE"; payload: ChatMessage }
  | { type: "SET_CHAT_LOADING"; payload: boolean }
  | { type: "SHOW_CRITERIA_EDITOR" }
  | { type: "HIDE_CRITERIA_EDITOR" }
  | { type: "SHOW_FORECASTING" }
  | { type: "HIDE_FORECASTING" }
  | { type: "SET_FORECAST"; payload: IncentiveForecast }
  | { type: "SET_FORECAST_LOADING"; payload: boolean }
  | {
      type: "LOAD_INCENTIVE";
      payload: { incentive: IncentiveDetailResponse };
    }
  | { type: "REQUEST_CREATE_CONFIRMATION" }
  | { type: "DISMISS_CREATE_CONFIRMATION" }
  | { type: "SET_CREATING"; payload: boolean }
  | { type: "SET_AI_LOCKED"; payload: boolean }
  | { type: "SET_PENDING_DOCUMENT"; payload: File | null }
  | { type: "RESET" };

// --- Initial State ---

export const initialBuilderState: BuilderState = {
  flowState: "entry_menu",
  mode: "ai",
  builderOrigin: "scratch",
  activeStep: "basics",
  expandedSteps: ["basics"],
  completedSteps: [],

  basics: {
    name: "",
    description: "",
    rewardMessage: "",
    incentiveType: null,
  },
  audience: {
    rules: [],
    countriesText: "",
    userRoles: [],
    partnerTypes: [],
    specificPartners: "",
    dynamicFields: {},
    locationSelections: {},
  },
  budgetData: {
    budget: null,
    selectedCurrencies: [],
    budgetMode: "global",
    globalBudgets: {},
    regionBudgets: {},
    budgetLocationLevelId: null,
    locationBudgets: {},
    maxPerPartnerByCurrency: {},
    maxPerUserByCurrency: {},
    maxPerPartner: "",
    maxPerUser: "",
    rewardAmounts: {},
    journeyHasOwnRewards: true,
    maxClaimersPerDeal: "1",
  },
  schedule: {
    startDate: "",
    endDate: "",
    fiscalYears: [],
    fiscalQuarters: [],
  },
  criteria: {
    salesRequirements: [],
    trainingCourses: [],
    trainingRequiredCount: 0,
    activityDefinitions: [],
    journeyStages: [],
    journeySequential: true,
  },
  approval: {
    requiresApproval: true,
    approvers: [],
    requiredApprovals: 0,
  },

  chatMessages: [],
  isChatLoading: false,

  showCriteriaEditor: false,

  showForecasting: false,
  forecast: null,
  isForecastLoading: false,

  editingIncentiveId: null,
  existingDocuments: [],

  isDirty: false,

  pendingCreate: false,
  isCreating: false,
  aiLocked: false,
  pendingDocumentFile: null,
};
