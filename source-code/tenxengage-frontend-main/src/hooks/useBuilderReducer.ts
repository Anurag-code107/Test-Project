import type {
  BuilderState,
  BuilderAction,
  BuilderStep,
  ApproverEntry,
} from "@/types/builder-state.types";
import { initialBuilderState } from "@/types/builder-state.types";
import type {
  IncentiveDetailResponse,
  SalesRequirement,
  AudienceRule,
  AllocationMethod,
  BudgetMode,
} from "@/types/incentive.types";

/** Strip trailing decimal zeros from numeric strings: "500000.00" → "500000", "4.50" → "4.5" */
function stripZeros(v: string | undefined | null): string {
  if (!v) return "";
  if (!v.includes(".")) return v;
  const n = parseFloat(v);
  return isNaN(n) ? v : String(n);
}

/** Round to whole number string: "49999.99" → "50000", "5000.00" → "5000" */
function toWhole(v: string | undefined | null): string {
  if (!v) return "";
  const n = parseFloat(v);
  return isNaN(n) ? v : String(Math.round(n));
}

function toWholeMap(obj: Record<string, string>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(obj)) out[k] = toWhole(v);
  return out;
}

function cleanSalesRequirements(reqs: SalesRequirement[]): SalesRequirement[] {
  return reqs.map((req) => ({
    ...req,
    eligibilityGroups: req.eligibilityGroups.map((g) => ({
      ...g,
      rules: g.rules.map((r) => ({
        ...r,
        value: toWhole(r.value) || undefined,
        valueMax: toWhole(r.valueMax) || undefined,
      })),
    })),
    payouts: req.payouts.map((p) => ({
      ...p,
      maxPerDeal: toWhole(p.maxPerDeal) || undefined,
      bands: p.bands.map((b) => ({
        ...b,
        minAmount: toWhole(b.minAmount),
        maxAmount: toWhole(b.maxAmount),
        // Percentage payouts can have decimals (e.g., 4.5%), monetary amounts are whole
        payoutValue:
          p.payoutType === "PERCENTAGE"
            ? stripZeros(b.payoutValue)
            : toWhole(b.payoutValue),
      })),
    })),
  }));
}

export function builderReducer(
  state: BuilderState,
  action: BuilderAction,
): BuilderState {
  switch (action.type) {
    case "SET_FLOW_STATE":
      return {
        ...state,
        flowState: action.payload,
        // Reset dirty flag when entering the builder — initialization dispatches
        // (e.g. setting incentiveType) shouldn't count as user changes
        ...(action.payload === "builder" ? { isDirty: false } : {}),
      };

    case "SET_MODE":
      return { ...state, mode: action.payload };

    case "SET_ORIGIN":
      return { ...state, builderOrigin: action.payload };

    case "SET_ACTIVE_STEP":
      return {
        ...state,
        activeStep: action.payload,
        expandedSteps: [action.payload],
      };

    case "TOGGLE_STEP": {
      const step = action.payload;
      const isExpanded = state.expandedSteps.includes(step);
      return {
        ...state,
        activeStep: step,
        expandedSteps: isExpanded ? [] : [step],
      };
    }

    case "EXPAND_STEP":
      return {
        ...state,
        activeStep: action.payload,
        expandedSteps: [action.payload],
      };

    case "MARK_STEP_COMPLETE":
      return {
        ...state,
        completedSteps: state.completedSteps.includes(action.payload)
          ? state.completedSteps
          : [...state.completedSteps, action.payload],
      };

    case "MARK_STEP_INCOMPLETE":
      return {
        ...state,
        completedSteps: state.completedSteps.filter(
          (s) => s !== action.payload,
        ),
      };

    case "UPDATE_BASICS": {
      // Setting only incentiveType is an initialization action (type selection screen),
      // not a user edit — don't mark dirty for that
      const basicsKeys = Object.keys(action.payload);
      const isTypeInitOnly =
        basicsKeys.length === 1 && basicsKeys[0] === "incentiveType";
      return {
        ...state,
        basics: { ...state.basics, ...action.payload },
        isDirty: isTypeInitOnly ? state.isDirty : true,
      };
    }

    case "UPDATE_AUDIENCE":
      return {
        ...state,
        audience: { ...state.audience, ...action.payload },
        isDirty: true,
      };

    case "UPDATE_BUDGET": {
      const newBudgetData = { ...state.budgetData, ...action.payload };

      // Safety net: if the bot set globalBudgets, regionBudgets, or rewardAmounts for currencies
      // that aren't in selectedCurrencies yet, auto-add them so the dropdown stays in sync.
      const impliedCurrencies = new Set<string>(
        newBudgetData.selectedCurrencies ?? [],
      );
      if (newBudgetData.globalBudgets) {
        for (const cid of Object.keys(newBudgetData.globalBudgets)) {
          if (newBudgetData.globalBudgets[cid]) impliedCurrencies.add(cid);
        }
      }
      if (newBudgetData.regionBudgets) {
        for (const cid of Object.keys(newBudgetData.regionBudgets)) {
          if (
            newBudgetData.regionBudgets[cid] &&
            Object.keys(newBudgetData.regionBudgets[cid]).length > 0
          ) {
            impliedCurrencies.add(cid);
          }
        }
      }
      if (newBudgetData.locationBudgets) {
        for (const cid of Object.keys(newBudgetData.locationBudgets)) {
          if (
            newBudgetData.locationBudgets[cid] &&
            Object.keys(newBudgetData.locationBudgets[cid]).length > 0
          ) {
            impliedCurrencies.add(cid);
          }
        }
      }
      if (newBudgetData.rewardAmounts) {
        for (const cid of Object.keys(newBudgetData.rewardAmounts)) {
          if (newBudgetData.rewardAmounts[cid]) impliedCurrencies.add(cid);
        }
      }
      const mergedCurrencies = [...impliedCurrencies];
      if (
        JSON.stringify(mergedCurrencies.sort()) !==
        JSON.stringify([...(newBudgetData.selectedCurrencies ?? [])].sort())
      ) {
        newBudgetData.selectedCurrencies = mergedCurrencies;
      }

      // If selected currencies changed, invalidate criteria step so payout configs are re-evaluated
      const currenciesChanged =
        JSON.stringify(
          (newBudgetData.selectedCurrencies ?? []).slice().sort(),
        ) !==
        JSON.stringify(
          (state.budgetData.selectedCurrencies ?? []).slice().sort(),
        );
      // Prune stale payout configs for currencies that were removed
      const newCriteria =
        currenciesChanged && action.payload.selectedCurrencies
          ? {
              ...state.criteria,
              salesRequirements: state.criteria.salesRequirements.map(
                (req) => ({
                  ...req,
                  payouts: req.payouts.filter((p) =>
                    (action.payload.selectedCurrencies as string[]).includes(
                      p.currencyId,
                    ),
                  ),
                }),
              ),
            }
          : state.criteria;
      return {
        ...state,
        budgetData: newBudgetData,
        criteria: newCriteria,
        completedSteps: currenciesChanged
          ? state.completedSteps.filter((s) => s !== "criteria")
          : state.completedSteps,
        isDirty: true,
      };
    }

    case "UPDATE_SCHEDULE":
      return {
        ...state,
        schedule: { ...state.schedule, ...action.payload },
        isDirty: true,
      };

    case "UPDATE_CRITERIA":
      return {
        ...state,
        criteria: { ...state.criteria, ...action.payload },
        isDirty: true,
      };

    case "UPDATE_APPROVAL":
      return {
        ...state,
        approval: { ...state.approval, ...action.payload },
        isDirty: true,
      };

    case "ADD_CHAT_MESSAGE":
      return {
        ...state,
        chatMessages: [...state.chatMessages, action.payload],
      };

    case "SET_CHAT_LOADING":
      return { ...state, isChatLoading: action.payload };

    case "SHOW_CRITERIA_EDITOR":
      return { ...state, showCriteriaEditor: true, showForecasting: false };

    case "HIDE_CRITERIA_EDITOR":
      return { ...state, showCriteriaEditor: false };

    case "SHOW_FORECASTING":
      return { ...state, showForecasting: true, showCriteriaEditor: false };

    case "HIDE_FORECASTING":
      return { ...state, showForecasting: false };

    case "SET_FORECAST":
      return {
        ...state,
        forecast: action.payload,
        isForecastLoading: false,
      };

    case "SET_FORECAST_LOADING":
      return { ...state, isForecastLoading: action.payload };

    case "REQUEST_CREATE_CONFIRMATION":
      return { ...state, pendingCreate: true };

    case "DISMISS_CREATE_CONFIRMATION":
      return { ...state, pendingCreate: false };

    case "SET_CREATING":
      return { ...state, isCreating: action.payload };

    case "SET_AI_LOCKED":
      return { ...state, aiLocked: action.payload };

    case "SET_PENDING_DOCUMENT":
      return { ...state, pendingDocumentFile: action.payload };

    case "LOAD_INCENTIVE":
      return loadIncentiveIntoState(state, action.payload.incentive);

    case "RESET":
      return { ...initialBuilderState };

    default:
      return state;
  }
}

/**
 * Parse a "YYYY-MM-DD" string into year/month/day WITHOUT timezone shifting.
 * new Date("2026-07-01") is parsed as UTC midnight, which in western timezones
 * rolls back to June 30 — causing wrong quarter/year derivation. Splitting the
 * string avoids this entirely.
 */
function parseDateParts(dateStr: string): {
  year: number;
  month: number;
  day: number;
} {
  const parts = dateStr.split("-").map(Number);
  return { year: parts[0] ?? 0, month: parts[1] ?? 1, day: parts[2] ?? 1 };
}

/** Derive fiscal years (e.g. "FY2026") that a date range spans. */
function deriveFiscalYears(startDate: string, endDate: string): string[] {
  if (!startDate || !endDate) return [];
  const start = parseDateParts(startDate);
  const end = parseDateParts(endDate);
  const years: string[] = [];
  for (let y = start.year; y <= end.year; y++) {
    years.push(`FY${y}`);
  }
  return years;
}

/** Derive fiscal quarters (e.g. "Q1") that a date range spans. */
function deriveFiscalQuarters(startDate: string, endDate: string): string[] {
  if (!startDate || !endDate) return [];
  const start = parseDateParts(startDate);
  const end = parseDateParts(endDate);
  const startQ = Math.ceil(start.month / 3);
  const endQ = Math.ceil(end.month / 3);

  // If same year, just range startQ..endQ
  // If different years, include all quarters
  const quarters: string[] = [];
  if (start.year === end.year) {
    for (let q = startQ; q <= endQ; q++) quarters.push(`Q${q}`);
  } else {
    // Spans multiple years — include all quarters
    for (let q = 1; q <= 4; q++) quarters.push(`Q${q}`);
  }
  return quarters;
}

/**
 * Build globalBudgets for all reward currencies.
 * The budget record stores a total for one currency; for other currencies,
 * fall back to the rewardAmounts value so the field isn't empty.
 */
function buildGlobalBudgets(
  incentive: IncentiveDetailResponse,
): Record<string, string> {
  const result: Record<string, string> = {};

  // Prefer the budgets array (plural) which has per-currency totals
  if (incentive.budgets && incentive.budgets.length > 0) {
    for (const b of incentive.budgets) {
      if (b.totalBudget && b.currencyId) {
        result[b.currencyId] = toWhole(b.totalBudget);
      }
    }
    return result;
  }

  // Fallback to singular budget + rewardAmounts
  const singularCurrency =
    incentive.budget?.currencyId ?? incentive.budget?.currency;
  const currencies: string[] =
    incentive.rewardCurrencies ??
    (singularCurrency ? [singularCurrency] : []);
  for (const currency of currencies) {
    if (
      incentive.budget &&
      (incentive.budget.currencyId === currency ||
        incentive.budget.currency === currency)
    ) {
      result[currency] = toWhole(incentive.budget.totalBudget);
    } else if (incentive.rewardAmounts?.[currency]) {
      result[currency] = toWhole(incentive.rewardAmounts[currency]);
    }
  }
  return result;
}

/**
 * Build per-currency, per-`locationValueId` budgets from all budget entries
 * in the response. The new wire shape is `BudgetResponseItem.locationAllocations[]`
 * (id-keyed, denormalized with name + level for display). State stores the
 * id→amount map per currency; the UI looks up names via the location
 * hierarchy at render time.
 *
 * Returns: { cash: { "<uuid-Americas>": "1000000", "<uuid-California>": "400000" } }
 */
function buildLocationBudgets(
  incentive: IncentiveDetailResponse,
): Record<string, Record<string, string>> {
  const result: Record<string, Record<string, string>> = {};
  if (!incentive.budgets || incentive.budgets.length === 0) return result;

  for (const b of incentive.budgets) {
    if (!b.currencyId) continue;
    if (!b.locationAllocations || b.locationAllocations.length === 0) continue;
    const allocMap: Record<string, string> = {};
    for (const alloc of b.locationAllocations) {
      if (alloc.locationValueId && alloc.amount) {
        allocMap[alloc.locationValueId] = toWhole(alloc.amount);
      }
    }
    if (Object.keys(allocMap).length > 0) {
      result[b.currencyId] = allocMap;
    }
  }
  return result;
}

/**
 * Recover the `budgetLocationLevelId` hint from the response — first budget's
 * `budgetLocationLevelId` is the canonical signal, falling back to the depth
 * of the first allocation's level if the hint wasn't persisted.
 */
function buildBudgetLocationLevelId(
  incentive: IncentiveDetailResponse,
): string | null {
  if (!incentive.budgets || incentive.budgets.length === 0) return null;
  for (const b of incentive.budgets) {
    if (b.budgetLocationLevelId) return b.budgetLocationLevelId;
    const firstAlloc = b.locationAllocations?.[0];
    if (firstAlloc?.locationLevelId) return firstAlloc.locationLevelId;
  }
  return null;
}

function buildLocationSelectionsFromRules(
  rules: AudienceRule[],
): Record<string, string[]> {
  const selections: Record<string, string[]> = {};
  for (const rule of rules) {
    if (
      rule.ruleType === "LOCATION" &&
      rule.locationLevelId &&
      rule.locationValueName
    ) {
      const levelId = rule.locationLevelId;
      if (!selections[levelId]) selections[levelId] = [];
      selections[levelId]!.push(rule.locationValueName);
    }
  }
  return selections;
}

function loadIncentiveIntoState(
  state: BuilderState,
  incentive: IncentiveDetailResponse,
): BuilderState {
  const completedSteps: BuilderStep[] = [];

  // Mark steps as completed based on loaded data
  if (incentive.name) completedSteps.push("basics");
  if (incentive.audienceRules && incentive.audienceRules.length > 0)
    completedSteps.push("audience");
  if (incentive.budget || (incentive.budgets && incentive.budgets.length > 0))
    completedSteps.push("budget");
  if (incentive.startDate && incentive.endDate) completedSteps.push("schedule");

  const hasCriteria =
    (incentive.salesRequirements && incentive.salesRequirements.length > 0) ||
    (incentive.trainingCourses && incentive.trainingCourses.length > 0) ||
    (incentive.activityDefinitions &&
      incentive.activityDefinitions.length > 0) ||
    (incentive.journeyStages && incentive.journeyStages.length > 0);
  if (hasCriteria) completedSteps.push("criteria");

  // Approval: complete if requiresApproval is defined (either on or off)
  if (
    incentive.requiresApproval !== undefined &&
    incentive.requiresApproval !== null
  ) {
    completedSteps.push("approval");
  }

  // Build approvers list from response
  const approvers: ApproverEntry[] = (incentive.approvers ?? []).map((a) => ({
    id: a.id,
    email: a.email,
    category: a.category,
  }));

  return {
    ...state,
    flowState: "builder",
    builderOrigin: "edit",
    editingIncentiveId: incentive.id,
    completedSteps,
    expandedSteps: ["basics"],
    basics: {
      name: incentive.name,
      description: incentive.description ?? "",
      rewardMessage: incentive.rewardMessage ?? "",
      incentiveType: incentive.incentiveType,
    },
    audience: {
      rules: incentive.audienceRules ?? [],
      countriesText: incentive.countriesText ?? "",
      userRoles: (incentive.audienceRules ?? [])
        .filter((r) => r.ruleType === "ROLE")
        .map((r) => r.ruleValue),
      partnerTypes: (incentive.audienceRules ?? [])
        .filter((r) => r.ruleType === "PARTNER_TYPE")
        .map((r) => r.ruleValue),
      specificPartners: incentive.specificPartners ?? "",
      dynamicFields: incentive.customFieldValues
        ? JSON.parse(incentive.customFieldValues)
        : {},
      locationSelections: buildLocationSelectionsFromRules(
        incentive.audienceRules ?? [],
      ),
    },
    budgetData: {
      budget:
        incentive.budget ??
        (incentive.budgets?.[0]
          ? {
              totalBudget: incentive.budgets[0].totalBudget,
              currencyId: incentive.budgets[0].currencyId,
              allocationMethod: (incentive.budgets[0].allocationMethod ||
                "EQUAL") as AllocationMethod,
              budgetMode: (incentive.budgets[0].budgetMode ||
                "GLOBAL") as BudgetMode,
              budgetLocationLevelId:
                incentive.budgets[0].budgetLocationLevelId,
              locationAllocations: incentive.budgets[0].locationAllocations,
            }
          : null),
      selectedCurrencies:
        incentive.rewardCurrencies ??
        incentive.budgets?.map((b) => b.currencyId) ??
        (incentive.budget?.currencyId ?? incentive.budget?.currency
          ? [(incentive.budget?.currencyId ?? incentive.budget?.currency) as string]
          : []),
      // Normalize the wire enum to the internal mode. Both legacy "PER_REGION"
      // (pre-2026-04-28 saved data) and "PER_LOCATION" map to "per-location".
      budgetMode:
        (incentive.budgets?.[0]?.budgetMode ?? incentive.budget?.budgetMode) ===
          "PER_REGION" ||
        (incentive.budgets?.[0]?.budgetMode ?? incentive.budget?.budgetMode) ===
          "PER_LOCATION"
          ? "per-location"
          : "global",
      globalBudgets: toWholeMap(buildGlobalBudgets(incentive)),
      regionBudgets: {},
      budgetLocationLevelId: buildBudgetLocationLevelId(incentive),
      locationBudgets: buildLocationBudgets(incentive),
      maxPerPartner: toWhole(
        incentive.maxPerPartner ?? incentive.budget?.maxPerPartner,
      ),
      maxPerUser: toWhole(incentive.maxPerUser ?? incentive.budget?.maxPerUser),
      maxPerPartnerByCurrency: {},
      maxPerUserByCurrency: {},
      rewardAmounts: toWholeMap(incentive.rewardAmounts ?? {}),
      journeyHasOwnRewards: true,
      maxClaimersPerDeal: incentive.maxClaimersPerDeal
        ? String(incentive.maxClaimersPerDeal)
        : "1",
    },
    schedule: {
      startDate: incentive.startDate ?? "",
      endDate: incentive.endDate ?? "",
      fiscalYears:
        incentive.fiscalYears ??
        deriveFiscalYears(incentive.startDate ?? "", incentive.endDate ?? ""),
      fiscalQuarters:
        incentive.fiscalQuarters ??
        deriveFiscalQuarters(
          incentive.startDate ?? "",
          incentive.endDate ?? "",
        ),
    },
    criteria: {
      salesRequirements: cleanSalesRequirements(
        incentive.salesRequirements ?? [],
      ),
      trainingCourses: incentive.trainingCourses ?? [],
      trainingRequiredCount:
        incentive.trainingRequiredCount ??
        incentive.trainingCourses?.length ??
        0,
      activityDefinitions: incentive.activityDefinitions ?? [],
      journeyStages: incentive.journeyStages ?? [],
      journeySequential: incentive.journeySequential ?? true,
    },
    approval: {
      requiresApproval: incentive.requiresApproval ?? false,
      approvers,
      requiredApprovals: incentive.requiredApprovals ?? 0,
    },
    forecast: incentive.forecast ?? null,
    existingDocuments: incentive.documents ?? [],
    isDirty: false,
  };
}
