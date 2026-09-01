import type { BuilderState } from "@/types/builder-state.types";
import type {
  CreateIncentiveRequest,
  UpdateIncentiveRequest,
  BudgetRequestEntry,
  LocationAllocationRequest,
  AudienceRule,
  SalesRequirement,
} from "@/types/incentive.types";
import {
  buildBudgetTree,
  computeEffectiveAllocations,
  flattenTree,
  parseAmount,
} from "@/components/incentive-builder/steps/budget/budgetTreeHelpers";
import type {
  LocationHierarchyResponse,
  LocationValueResponse,
} from "@/types/location.types";

/**
 * Thrown when the mapper can't resolve one or more location names from
 * `audience.locationSelections` to UUIDs against the loaded hierarchy. Callers
 * surface this as a save-time toast — the user typically has to re-pick the
 * affected rows. Typical triggers: a location was renamed in another tab
 * between picker render and save (BUG-079 headline case), the AI Copilot
 * dispatched a hallucinated name, or a stale draft references a deleted node.
 */
export class UnresolvedLocationNameError extends Error {
  readonly unresolvedByLevel: Record<string, string[]>;

  constructor(unresolvedByLevel: Record<string, string[]>) {
    const flat = Object.values(unresolvedByLevel).flat();
    super(
      `Some locations couldn't be saved because they no longer exist in the ` +
        `hierarchy: ${flat.join(", ")}. Please re-pick them in Step 3.`,
    );
    this.name = "UnresolvedLocationNameError";
    this.unresolvedByLevel = unresolvedByLevel;
  }
}

/**
 * Build a lookup `Map<levelId, Map<lowercased name, LocationValue.id>>` from a
 * loaded hierarchy. Walks the full tree depth-first so any node at any depth
 * is reachable by (level, name). Lowercased on insertion so case-drift in
 * uploaded names ("California" vs "california") still resolves.
 */
function buildNameIndex(
  hierarchy: LocationHierarchyResponse | undefined,
): Map<string, Map<string, string>> {
  const byLevel = new Map<string, Map<string, string>>();
  if (!hierarchy?.tree) return byLevel;

  const visit = (node: LocationValueResponse): void => {
    let levelMap = byLevel.get(node.levelId);
    if (!levelMap) {
      levelMap = new Map<string, string>();
      byLevel.set(node.levelId, levelMap);
    }
    levelMap.set(node.name.trim().toLowerCase(), node.id);
    for (const child of node.children ?? []) {
      visit(child);
    }
  };
  for (const root of hierarchy.tree) visit(root);
  return byLevel;
}

/**
 * BUG-079: resolve picker selections from name-keyed state to UUID-keyed
 * audience rules. Names live in frontend state and Excel files; the wire
 * format is UUID-only end-to-end. If any name fails to resolve against the
 * loaded hierarchy, throw {@link UnresolvedLocationNameError} so the caller
 * can surface a toast — silent dropping would put the user back into the
 * pre-fix bug class (saved incentive has fewer rules than what the user
 * picked, with no signal).
 */
function buildAudienceRules(
  state: BuilderState,
  hierarchy?: LocationHierarchyResponse,
): AudienceRule[] {
  const rules: AudienceRule[] = [];
  const nameIndex = buildNameIndex(hierarchy);
  const unresolved: Record<string, string[]> = {};

  for (const [locationLevelId, values] of Object.entries(
    state.audience.locationSelections,
  )) {
    const levelMap = nameIndex.get(locationLevelId);
    for (const name of values) {
      const uuid = levelMap?.get(name.trim().toLowerCase());
      if (!uuid) {
        if (!unresolved[locationLevelId]) unresolved[locationLevelId] = [];
        unresolved[locationLevelId].push(name);
        continue;
      }
      rules.push({
        ruleType: "LOCATION",
        ruleValue: uuid,
        locationLevelId,
      });
    }
  }

  if (Object.keys(unresolved).length > 0) {
    throw new UnresolvedLocationNameError(unresolved);
  }

  for (const pt of state.audience.partnerTypes) {
    rules.push({ ruleType: "PARTNER_TYPE", ruleValue: pt });
  }
  for (const role of state.audience.userRoles) {
    rules.push({ ruleType: "ROLE", ruleValue: role });
  }

  return rules;
}

import { monetaryCurrencyIds } from "@/config/currencies";

/**
 * Maps builder budget state into the API budgets array.
 *
 * One {@link BudgetRequestEntry} per monetary currency that has a budget.
 * In `PER_LOCATION` mode the per-currency state is materialized into a flat
 * `locationAllocations` array — every node in the eligibility-scoped tree is
 * resolved to its effective amount via the auto-fill helper, so blank inputs
 * are filled with the residual `parent.total - sum(typed siblings)` before
 * hitting the wire.
 *
 * `hierarchy` is optional: when undefined (e.g. forecast-preview build paths)
 * we fall back to the user-typed values only, with no auto-fill expansion.
 */
function buildBudgets(
  state: BuilderState,
  hierarchy?: LocationHierarchyResponse,
): BudgetRequestEntry[] | undefined {
  const bd = state.budgetData;
  const isPerLocation =
    bd.budgetMode === "per-location" || bd.budgetMode === "per-region";
  const budgetMode = isPerLocation ? "PER_LOCATION" : "GLOBAL";
  const allocationMethod = bd.budget?.allocationMethod || "EQUAL";

  const tree = isPerLocation
    ? buildBudgetTree(hierarchy, state.audience.locationSelections)
    : [];

  const entries: BudgetRequestEntry[] = [];
  for (const currencyId of bd.selectedCurrencies) {
    if (!monetaryCurrencyIds.includes(currencyId)) continue;
    const totalBudget = bd.globalBudgets[currencyId];
    if (!totalBudget) continue;

    const entry: BudgetRequestEntry = {
      totalBudget,
      currencyId,
      allocationMethod,
      budgetMode,
    };

    if (isPerLocation) {
      entry.budgetLocationLevelId = bd.budgetLocationLevelId ?? null;
      const userValues = bd.locationBudgets[currencyId] ?? {};
      const allocations = serializeAllocationTree(
        tree,
        userValues,
        parseAmount(totalBudget),
      );
      if (allocations.length > 0) {
        entry.locationAllocations = allocations;
      }
    }

    entries.push(entry);
  }

  return entries.length > 0 ? entries : undefined;
}

/**
 * Resolves every tree node to its effective amount (typed or auto-filled
 * residual) and emits the flat `locationAllocations` array the backend
 * persists. Nodes whose effective amount rounds to zero are dropped.
 */
function serializeAllocationTree(
  tree: ReturnType<typeof buildBudgetTree>,
  userValues: Record<string, string>,
  globalTotal: number | null,
): LocationAllocationRequest[] {
  if (tree.length === 0) return [];
  const effective = computeEffectiveAllocations(tree, userValues, globalTotal);
  const out: LocationAllocationRequest[] = [];
  for (const node of flattenTree(tree)) {
    const amount = effective[node.locationValueId];
    if (amount === undefined || amount <= 0) continue;
    out.push({
      locationValueId: node.locationValueId,
      amount: String(Math.round(amount)),
    });
  }
  return out;
}

/**
 * Build the rewardAmounts map for completion-based incentives (Training/Activity/Journey).
 */
function buildRewardAmounts(
  state: BuilderState,
): Record<string, string> | undefined {
  const bd = state.budgetData;
  const merged: Record<string, string> = { ...bd.rewardAmounts };
  return Object.keys(merged).length > 0 ? merged : undefined;
}

/**
 * Strip unconfigured payouts from sales requirements before sending to the API.
 * The criteria editor creates empty payout shells for every selected currency,
 * but currencies the user didn't configure will have payoutType = "".
 * Sending these causes PayoutType.valueOf("") to throw on the backend.
 */
function cleanSalesRequirements(reqs: SalesRequirement[]): SalesRequirement[] {
  return reqs.map((req) => ({
    ...req,
    payouts: req.payouts.filter((p) => !!p.payoutType),
  }));
}

export function buildCreateRequest(
  state: BuilderState,
  hierarchy?: LocationHierarchyResponse,
): CreateIncentiveRequest {
  if (!state.basics.incentiveType) {
    throw new Error("Incentive type is required");
  }

  return {
    name: state.basics.name,
    description: state.basics.description || undefined,
    incentiveType: state.basics.incentiveType,
    startDate: state.schedule.startDate || undefined,
    endDate: state.schedule.endDate || undefined,
    budgets: buildBudgets(state, hierarchy),
    maxPerPartner: state.budgetData.maxPerPartner || undefined,
    maxPerUser: state.budgetData.maxPerUser || undefined,
    maxPerPartnerByCurrency:
      Object.keys(state.budgetData.maxPerPartnerByCurrency).length > 0
        ? JSON.stringify(state.budgetData.maxPerPartnerByCurrency)
        : undefined,
    maxPerUserByCurrency:
      Object.keys(state.budgetData.maxPerUserByCurrency).length > 0
        ? JSON.stringify(state.budgetData.maxPerUserByCurrency)
        : undefined,
    audienceRules: buildAudienceRules(state, hierarchy),
    rewardCurrencies:
      state.budgetData.selectedCurrencies.length > 0
        ? state.budgetData.selectedCurrencies
        : undefined,
    rewardMessage: state.basics.rewardMessage || undefined,
    rewardAmounts: buildRewardAmounts(state),
    salesRequirements:
      state.criteria.salesRequirements.length > 0
        ? cleanSalesRequirements(state.criteria.salesRequirements)
        : undefined,
    trainingCourses:
      state.criteria.trainingCourses.length > 0
        ? state.criteria.trainingCourses
        : undefined,
    activityDefinitions:
      state.criteria.activityDefinitions.length > 0
        ? state.criteria.activityDefinitions
        : undefined,
    journeyStages:
      state.criteria.journeyStages.length > 0
        ? state.criteria.journeyStages
        : undefined,
    journeySequential:
      state.basics.incentiveType === "JOURNEY"
        ? state.criteria.journeySequential
        : undefined,
    fiscalYears:
      state.schedule.fiscalYears.length > 0
        ? state.schedule.fiscalYears
        : undefined,
    fiscalQuarters:
      state.schedule.fiscalQuarters.length > 0
        ? state.schedule.fiscalQuarters
        : undefined,
    trainingRequiredCount:
      state.basics.incentiveType === "TRAINING"
        ? state.criteria.trainingRequiredCount
        : undefined,
    countriesText: state.audience.countriesText || undefined,
    specificPartners: state.audience.specificPartners || undefined,
    customFieldValues:
      Object.keys(state.audience.dynamicFields).length > 0
        ? JSON.stringify(state.audience.dynamicFields)
        : undefined,
    maxClaimersPerDeal:
      state.basics.incentiveType === "SALES" &&
      state.budgetData.maxClaimersPerDeal
        ? parseInt(state.budgetData.maxClaimersPerDeal, 10) || undefined
        : undefined,
    requiresApproval: state.approval.requiresApproval,
    approvers:
      state.approval.approvers.length > 0
        ? state.approval.approvers.map((a) => ({
            email: a.email,
            category: a.category,
          }))
        : undefined,
    requiredApprovals: state.approval.requiredApprovals || undefined,
  };
}

export function buildUpdateRequest(
  state: BuilderState,
  hierarchy?: LocationHierarchyResponse,
): UpdateIncentiveRequest {
  return {
    name: state.basics.name,
    description: state.basics.description || undefined,
    startDate: state.schedule.startDate || undefined,
    endDate: state.schedule.endDate || undefined,
    budgets: buildBudgets(state, hierarchy),
    maxPerPartner: state.budgetData.maxPerPartner || undefined,
    maxPerUser: state.budgetData.maxPerUser || undefined,
    maxPerPartnerByCurrency:
      Object.keys(state.budgetData.maxPerPartnerByCurrency).length > 0
        ? JSON.stringify(state.budgetData.maxPerPartnerByCurrency)
        : undefined,
    maxPerUserByCurrency:
      Object.keys(state.budgetData.maxPerUserByCurrency).length > 0
        ? JSON.stringify(state.budgetData.maxPerUserByCurrency)
        : undefined,
    audienceRules: buildAudienceRules(state, hierarchy),
    rewardCurrencies:
      state.budgetData.selectedCurrencies.length > 0
        ? state.budgetData.selectedCurrencies
        : undefined,
    rewardMessage: state.basics.rewardMessage || undefined,
    rewardAmounts: buildRewardAmounts(state),
    salesRequirements:
      state.criteria.salesRequirements.length > 0
        ? cleanSalesRequirements(state.criteria.salesRequirements)
        : undefined,
    trainingCourses:
      state.criteria.trainingCourses.length > 0
        ? state.criteria.trainingCourses
        : undefined,
    activityDefinitions:
      state.criteria.activityDefinitions.length > 0
        ? state.criteria.activityDefinitions
        : undefined,
    journeyStages:
      state.criteria.journeyStages.length > 0
        ? state.criteria.journeyStages
        : undefined,
    journeySequential:
      state.basics.incentiveType === "JOURNEY"
        ? state.criteria.journeySequential
        : undefined,
    fiscalYears:
      state.schedule.fiscalYears.length > 0
        ? state.schedule.fiscalYears
        : undefined,
    fiscalQuarters:
      state.schedule.fiscalQuarters.length > 0
        ? state.schedule.fiscalQuarters
        : undefined,
    trainingRequiredCount:
      state.basics.incentiveType === "TRAINING"
        ? state.criteria.trainingRequiredCount
        : undefined,
    countriesText: state.audience.countriesText || undefined,
    specificPartners: state.audience.specificPartners || undefined,
    customFieldValues:
      Object.keys(state.audience.dynamicFields).length > 0
        ? JSON.stringify(state.audience.dynamicFields)
        : undefined,
    maxClaimersPerDeal:
      state.basics.incentiveType === "SALES" &&
      state.budgetData.maxClaimersPerDeal
        ? parseInt(state.budgetData.maxClaimersPerDeal, 10) || undefined
        : undefined,
    requiresApproval: state.approval.requiresApproval,
    approvers:
      state.approval.approvers.length > 0
        ? state.approval.approvers.map((a) => ({
            email: a.email,
            category: a.category,
          }))
        : undefined,
    requiredApprovals: state.approval.requiredApprovals || undefined,
  };
}
