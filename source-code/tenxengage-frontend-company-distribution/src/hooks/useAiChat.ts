import { useState, useCallback, useRef, useEffect } from "react";
import { useBuilder } from "@/contexts/BuilderContext";
import {
  useLocationBuilderOptions,
  useLocationHierarchy,
} from "@/hooks/useLocationApi";
import { useExternalRoles } from "@/hooks/useBuilderConfig";
import {
  streamAiChat,
  streamAiChatWithDocument,
} from "@/services/ai-chat.service";
import type {
  AiChatRequest,
  AiChatMessageEntry,
  BuilderStateSnapshot,
  StepFieldAudit,
} from "@/types/ai-chat.types";
import type {
  BuilderAction,
  BuilderStep,
  ChatMessage,
  CriteriaData,
} from "@/types/builder-state.types";
import { BUILDER_STEPS, STEP_LABELS } from "@/types/builder-state.types";
import type { SalesRequirement, JourneyStage } from "@/types/incentive.types";
import { getIncentives } from "@/services/incentive.service";
import { getAllCurrencies } from "@/config/currencies";

function makeMsg(role: "user" | "assistant", content: string): ChatMessage {
  return {
    id: `${role}-${Date.now()}-${Math.random()}`,
    role,
    content,
    timestamp: new Date().toISOString(),
  };
}

/** Human-readable labels for raw field keys used in audit messages.
 *  Keys that aren't in this map get auto-humanized (e.g. "globalBudgets.cash" → "total cash budget"). */
const FIELD_LABELS: Record<string, string> = {
  name: "incentive name",
  description: "description",
  fiscalYears: "fiscal year",
  fiscalQuarters: "fiscal quarter",
  startDate: "start date",
  endDate: "end date",
  locations: "locations",
  regions: "regions",
  userRoles: "user roles",
  selectedCurrencies: "reward currencies",
  rewardMessage: "reward message",
  regionBudgets: "per-region budgets",
  "criteria (no requirements, courses, activities, or stages defined)":
    "incentive criteria (no requirements, courses, activities, or stages have been set up)",
  "approval configuration": "approval configuration (choose whether approval is required)",
  approvers: "approvers (at least one approver email)",
  requiredApprovals: "number of required approvals",
};

/** Convert a raw field key to a user-friendly label. */
function humanizeField(raw: string): string {
  if (FIELD_LABELS[raw]) return FIELD_LABELS[raw];

  // Handle dynamic keys like "globalBudgets.cash", "globalBudgets.points"
  if (raw.startsWith("globalBudgets.")) {
    const currency = raw.replace("globalBudgets.", "");
    return `total ${currency} budget`;
  }
  if (raw.startsWith("rewardAmounts.")) {
    const currency = raw.replace("rewardAmounts.", "");
    return `${currency} reward amount per completion`;
  }

  // Fallback: just clean up camelCase
  return raw.replace(/([A-Z])/g, " $1").toLowerCase().trim();
}

/** Convert an array of raw field keys to a human-readable comma-separated string. */
function humanizeMissing(fields: string[]): string {
  return fields.map(humanizeField).join(", ");
}

/** Check if AI-provided criteria payload contains valid, complete data */
function isCriteriaValid(payload: Partial<CriteriaData>): boolean {
  if (payload.salesRequirements?.length) {
    return payload.salesRequirements.every(
      (req: SalesRequirement) =>
        req.name &&
        (req.eligibilityGroups ?? []).some((g) =>
          (g.rules ?? []).some((r) => !!r.ruleType),
        ) &&
        (req.payouts ?? []).length > 0 &&
        (req.payouts ?? []).every(
          (p) => !!p.payoutType && (p.bands ?? []).length > 0,
        ),
    );
  }
  if (payload.trainingCourses?.length) return true;
  if (payload.activityDefinitions?.length) return true;
  if (payload.journeyStages && payload.journeyStages.length >= 2) return true;
  return false;
}

/** Compute a ground-truth audit of which required fields are filled vs missing for each step. */
function auditStepFields(state: {
  basics: BuilderStateSnapshot["basics"];
  schedule: BuilderStateSnapshot["schedule"];
  audience: BuilderStateSnapshot["audience"];
  budgetData: BuilderStateSnapshot["budgetData"];
  criteria: BuilderStateSnapshot["criteria"];
  approval: BuilderStateSnapshot["approval"];
}): Record<BuilderStep, StepFieldAudit> {
  const audit = (filled: string[], missing: string[]): StepFieldAudit => ({
    filled,
    missing,
    actuallyComplete: missing.length === 0,
  });

  // Step 1: Basics
  const basicsFilled: string[] = [];
  const basicsMissing: string[] = [];
  if (state.basics.name?.trim()) basicsFilled.push("name");
  else basicsMissing.push("name");
  if (state.basics.description?.trim()) basicsFilled.push("description");
  else basicsMissing.push("description");
  if (state.basics.rewardMessage?.trim()) basicsFilled.push("rewardMessage");
  else basicsMissing.push("rewardMessage");

  // Step 2: Schedule
  const schedFilled: string[] = [];
  const schedMissing: string[] = [];
  if (state.schedule.fiscalYears?.length > 0) schedFilled.push("fiscalYears");
  else schedMissing.push("fiscalYears");
  if (state.schedule.fiscalQuarters?.length > 0)
    schedFilled.push("fiscalQuarters");
  else schedMissing.push("fiscalQuarters");
  if (state.schedule.startDate?.trim()) schedFilled.push("startDate");
  else schedMissing.push("startDate");
  if (state.schedule.endDate?.trim()) schedFilled.push("endDate");
  else schedMissing.push("endDate");

  // Step 3: Audience
  const audFilled: string[] = [];
  const audMissing: string[] = [];
  const hasAnyLocation = Object.values(
    state.audience.locationSelections ?? {},
  ).some((vs) => vs.length > 0);
  if (hasAnyLocation) audFilled.push("locations");
  else audMissing.push("locations");
  if (state.audience.userRoles?.length > 0) audFilled.push("userRoles");
  else audMissing.push("userRoles");

  // Step 4: Budget
  const budFilled: string[] = [];
  const budMissing: string[] = [];
  if (state.budgetData.selectedCurrencies?.length > 0)
    budFilled.push("selectedCurrencies");
  else budMissing.push("selectedCurrencies");
  // Check monetary currency budgets
  const monetaryIds = ["cash", "points"];
  const selectedMonetary = (state.budgetData.selectedCurrencies ?? []).filter(
    (c) => monetaryIds.includes(c),
  );
  if (selectedMonetary.length > 0) {
    if (state.budgetData.budgetMode === "per-region") {
      const hasRegionBudgets =
        Object.keys(state.budgetData.regionBudgets ?? {}).length > 0;
      if (hasRegionBudgets) budFilled.push("regionBudgets");
      else budMissing.push("regionBudgets");
    } else {
      for (const cid of selectedMonetary) {
        const val = (state.budgetData.globalBudgets ?? {})[cid];
        if (val && val.trim()) budFilled.push(`globalBudgets.${cid}`);
        else budMissing.push(`globalBudgets.${cid}`);
      }
    }
  }
  // Check reward amounts for completion-based types (training/activity/journey)
  const selectedCurrencies = state.budgetData.selectedCurrencies ?? [];
  if (
    selectedCurrencies.length > 0 &&
    Object.keys(state.budgetData.rewardAmounts ?? {}).length > 0
  ) {
    for (const cid of selectedCurrencies) {
      const val = (state.budgetData.rewardAmounts ?? {})[cid];
      if (val && val.trim()) budFilled.push(`rewardAmounts.${cid}`);
      else budMissing.push(`rewardAmounts.${cid}`);
    }
  }

  // Step 5: Criteria (type-dependent — report what exists)
  const critFilled: string[] = [];
  const critMissing: string[] = [];
  const hasSales = (state.criteria.salesRequirements ?? []).length > 0;
  const hasTraining = (state.criteria.trainingCourses ?? []).length > 0;
  const hasActivities = (state.criteria.activityDefinitions ?? []).length > 0;
  const hasJourney = (state.criteria.journeyStages ?? []).length >= 2;
  if (hasSales)
    critFilled.push(
      `salesRequirements (${state.criteria.salesRequirements.length})`,
    );
  if (hasTraining)
    critFilled.push(
      `trainingCourses (${state.criteria.trainingCourses.length})`,
    );
  if (hasActivities)
    critFilled.push(
      `activityDefinitions (${state.criteria.activityDefinitions.length})`,
    );
  if (hasJourney)
    critFilled.push(`journeyStages (${state.criteria.journeyStages.length})`);
  if (!hasSales && !hasTraining && !hasActivities && !hasJourney) {
    critMissing.push(
      "criteria (no requirements, courses, activities, or stages defined)",
    );
  }

  // Step 6: Approval
  const apprFilled: string[] = [];
  const apprMissing: string[] = [];
  if (typeof state.approval.requiresApproval === "boolean") {
    apprFilled.push(
      `requiresApproval=${state.approval.requiresApproval}`,
    );
    if (state.approval.requiresApproval) {
      if ((state.approval.approvers ?? []).length >= 1) {
        apprFilled.push(`approvers (${state.approval.approvers.length})`);
      } else {
        apprMissing.push("approvers");
      }
      if (
        state.approval.requiredApprovals >= 1 &&
        state.approval.requiredApprovals <=
          (state.approval.approvers ?? []).length
      ) {
        apprFilled.push(
          `requiredApprovals=${state.approval.requiredApprovals}`,
        );
      } else {
        apprMissing.push("requiredApprovals");
      }
    }
  } else {
    apprMissing.push("requiresApproval");
  }

  return {
    basics: audit(basicsFilled, basicsMissing),
    schedule: audit(schedFilled, schedMissing),
    audience: audit(audFilled, audMissing),
    budget: audit(budFilled, budMissing),
    criteria: audit(critFilled, critMissing),
    approval: audit(apprFilled, apprMissing),
  };
}

function snapshotState(state: {
  basics: BuilderStateSnapshot["basics"];
  schedule: BuilderStateSnapshot["schedule"];
  audience: BuilderStateSnapshot["audience"];
  budgetData: BuilderStateSnapshot["budgetData"];
  criteria: BuilderStateSnapshot["criteria"];
  approval: BuilderStateSnapshot["approval"];
  completedSteps: BuilderStateSnapshot["completedSteps"];
  activeStep: BuilderStateSnapshot["activeStep"];
}): BuilderStateSnapshot {
  return {
    basics: state.basics,
    schedule: state.schedule,
    audience: state.audience,
    budgetData: state.budgetData,
    criteria: state.criteria,
    approval: state.approval,
    completedSteps: state.completedSteps,
    activeStep: state.activeStep,
    currencyMetadata: Object.values(getAllCurrencies()).map((c) => ({
      id: c.id,
      label: c.label,
      type: c.type === "monetary" ? "MONETARY" : "NON_MONETARY",
    })),
    stepFieldStatus: auditStepFields(state),
  };
}

/** Resolve AI-provided journey stages (which use incentiveName) to proper linkedIncentiveId format */
async function resolveJourneyStages(
  stages: Array<{
    incentiveName?: string;
    linkedIncentiveId?: string;
    sortOrder: number;
  }>,
): Promise<JourneyStage[]> {
  // If stages already have linkedIncentiveId (UUIDs), pass through
  if (stages.every((s) => s.linkedIncentiveId && !s.incentiveName)) {
    return stages as JourneyStage[];
  }

  // Fetch incentives to resolve names to IDs
  const result = await getIncentives({ pageSize: 100 });
  const incentives = result.data;

  return stages
    .map((s) => {
      if (s.linkedIncentiveId)
        return {
          linkedIncentiveId: s.linkedIncentiveId,
          sortOrder: s.sortOrder,
        };
      const match = incentives.find(
        (inc) => inc.name.toLowerCase() === s.incentiveName?.toLowerCase(),
      );
      if (!match) return null;
      return { linkedIncentiveId: match.id, sortOrder: s.sortOrder };
    })
    .filter((s): s is JourneyStage => s !== null);
}

/** Lightweight reference to a location value used by the budget-payload translator. */
export type BudgetTranslatorLocationValue = {
  id: string;
  name: string;
  parentId: string | null;
};

const UUID_PATTERN = /^[0-9a-f-]{36}$/i;
function looksLikeUuid(s: string): boolean {
  return s.length === 36 && UUID_PATTERN.test(s);
}

/**
 * Translate an UPDATE_BUDGET payload from the AI into the UUID-keyed shape the
 * Step 4 budget tree expects, and derive the per-currency parent total from
 * top-level entries when the AI omits it.
 *
 * Two failure modes this normalizes (BUG-071 follow-up):
 *
 * 1. **Name-keyed `locationBudgets`.** The system prompt tells the AI to key
 *    `locationBudgets[currencyId]` by `locationValueId` UUID, but the LLM
 *    sometimes falls back to value names (`{ "United States": "100000" }`).
 *    Those land in state but `BudgetAllocationTree` iterates by UUID and finds
 *    nothing, so the user sees empty fields. We re-key by case-insensitive
 *    name lookup against the loaded location values; unresolvable keys are
 *    dropped silently rather than left to mislead the UI.
 *
 * 2. **Missing per-currency parent total.** The Step 4 UI's per-currency
 *    `<Input>` and the tree's residual computation both bind to
 *    `globalBudgets[currencyId]`, even in per-location mode (it's the
 *    incentive's per-currency total, separate from the breakdown). When the
 *    AI dispatches `locationBudgets` without `globalBudgets[currencyId]` set,
 *    we derive it from the sum of top-level (root) entries so the UI has a
 *    parent to compute residuals against.
 *
 * Pure function — caller flattens the location hierarchy tree once and passes
 * the result in, so this can be unit-tested without a live hierarchy fetch.
 */
export function translateBudgetPayload(
  payload: Record<string, unknown>,
  locationValues: BudgetTranslatorLocationValue[],
): Record<string, unknown> {
  const lb = payload.locationBudgets;
  if (!lb || typeof lb !== "object" || Array.isArray(lb)) return payload;
  // Hierarchy not loaded yet — passing through preserves any name-keyed
  // entries the AI may have sent so a later translation pass can re-key them
  // once the hierarchy arrives. Mirrors translateAudiencePayload's behavior.
  if (locationValues.length === 0) return payload;

  const idByName = new Map<string, string>();
  const rootIds = new Set<string>();
  for (const v of locationValues) {
    idByName.set(v.name.toLowerCase(), v.id);
    if (v.parentId === null) rootIds.add(v.id);
  }

  let rekeyed = false;
  const newLocationBudgets: Record<string, Record<string, string>> = {};
  for (const [currencyId, entries] of Object.entries(
    lb as Record<string, unknown>,
  )) {
    if (!entries || typeof entries !== "object" || Array.isArray(entries)) {
      // Preserve whatever was there even if it's malformed — let the reducer
      // / UI ignore it rather than silently delete.
      continue;
    }
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(entries as Record<string, unknown>)) {
      if (typeof v !== "string") continue;
      if (looksLikeUuid(k)) {
        out[k] = v;
      } else {
        const id = idByName.get(k.toLowerCase());
        if (id) {
          out[id] = v;
        }
        // Unresolvable name — drop it rather than poison state.
        rekeyed = true;
      }
    }
    newLocationBudgets[currencyId] = out;
  }

  const isPerLocation = payload.budgetMode === "per-location";
  const originalGlobals =
    typeof payload.globalBudgets === "object" && payload.globalBudgets !== null
      ? (payload.globalBudgets as Record<string, string>)
      : null;
  const globalBudgets: Record<string, string> = { ...(originalGlobals ?? {}) };
  let globalsChanged = false;

  if (isPerLocation && rootIds.size > 0) {
    for (const [currencyId, entries] of Object.entries(newLocationBudgets)) {
      const existing = globalBudgets[currencyId];
      if (existing && existing.trim() !== "") continue;
      let sum = 0;
      let any = false;
      for (const [valueId, amount] of Object.entries(entries)) {
        if (!rootIds.has(valueId)) continue;
        const n = Number(amount);
        if (!Number.isFinite(n)) continue;
        sum += n;
        any = true;
      }
      if (any && sum > 0) {
        globalBudgets[currencyId] = String(sum);
        globalsChanged = true;
      }
    }
  }

  if (!rekeyed && !globalsChanged) return payload;

  const next: Record<string, unknown> = {
    ...payload,
    locationBudgets: newLocationBudgets,
  };
  if (globalsChanged) next.globalBudgets = globalBudgets;
  return next;
}

/** Recursively flatten a `LocationHierarchyResponse.tree` into the shape `translateBudgetPayload` expects. */
export function flattenLocationValuesForBudget(
  tree: Array<{
    id: string;
    name: string;
    parentId: string | null;
    children?: Array<unknown>;
  }>,
): BudgetTranslatorLocationValue[] {
  const out: BudgetTranslatorLocationValue[] = [];
  const walk = (
    nodes: Array<{
      id: string;
      name: string;
      parentId: string | null;
      children?: Array<unknown>;
    }>,
  ) => {
    for (const n of nodes) {
      out.push({ id: n.id, name: n.name, parentId: n.parentId });
      if (Array.isArray(n.children) && n.children.length > 0) {
        walk(
          n.children as Array<{
            id: string;
            name: string;
            parentId: string | null;
            children?: Array<unknown>;
          }>,
        );
      }
    }
  };
  walk(tree);
  return out;
}

/** Lightweight reference to a location value used by the audience-payload translator. */
export type AudienceTranslatorLocationValue = {
  id: string;
  name: string;
  levelId: string;
  parentId: string | null;
};

/** Recursively flatten a `LocationHierarchyResponse.tree` into the shape `translateAudiencePayload` expects. */
export function flattenLocationValuesForAudience(
  tree: Array<{
    id: string;
    name: string;
    levelId: string;
    parentId: string | null;
    children?: Array<unknown>;
  }>,
): AudienceTranslatorLocationValue[] {
  const out: AudienceTranslatorLocationValue[] = [];
  const walk = (
    nodes: Array<{
      id: string;
      name: string;
      levelId: string;
      parentId: string | null;
      children?: Array<unknown>;
    }>,
  ) => {
    for (const n of nodes) {
      out.push({ id: n.id, name: n.name, levelId: n.levelId, parentId: n.parentId });
      if (Array.isArray(n.children) && n.children.length > 0) {
        walk(
          n.children as Array<{
            id: string;
            name: string;
            levelId: string;
            parentId: string | null;
            children?: Array<unknown>;
          }>,
        );
      }
    }
  };
  walk(tree);
  return out;
}

/**
 * Walk every selected leaf up its parent chain and merge ancestor names into
 * `locationSelections` at the matching ancestor `levelId`. Returns the same
 * reference when nothing was added so callers can short-circuit. BUG-080: the
 * AI sometimes dispatches a deep leaf (e.g. City: Los Angeles) without its
 * ancestor chain — the reducer accepts it, but Step3Audience cascade-filters
 * deeper dropdowns by ancestor selection, so the leaf sits invisible until the
 * user manually picks the right Region. Back-filling here makes deep-leaf adds
 * immediately visible regardless of what the AI included.
 */
function backfillLocationAncestors(
  selections: Record<string, string[]>,
  locationValues: AudienceTranslatorLocationValue[],
): Record<string, string[]> {
  if (locationValues.length === 0) return selections;

  const byId = new Map<string, AudienceTranslatorLocationValue>();
  for (const v of locationValues) byId.set(v.id, v);

  const byLevelAndName = new Map<string, AudienceTranslatorLocationValue>();
  for (const v of locationValues) {
    byLevelAndName.set(`${v.levelId}|${v.name.toLowerCase()}`, v);
  }

  const out: Record<string, string[]> = {};
  for (const [lvlId, names] of Object.entries(selections)) {
    out[lvlId] = [...names];
  }
  let changed = false;

  for (const [lvlId, names] of Object.entries(selections)) {
    for (const name of names) {
      const node = byLevelAndName.get(`${lvlId}|${name.toLowerCase()}`);
      if (!node) continue;
      let cursor: AudienceTranslatorLocationValue | undefined = node;
      while (cursor && cursor.parentId) {
        const parent = byId.get(cursor.parentId);
        if (!parent) break;
        const existing = out[parent.levelId] ?? [];
        const alreadyHas = existing.some(
          (n) => n.toLowerCase() === parent.name.toLowerCase(),
        );
        if (!alreadyHas) {
          out[parent.levelId] = [...existing, parent.name];
          changed = true;
        }
        cursor = parent;
      }
    }
  }

  return changed ? out : selections;
}

/**
 * Translate an UPDATE_AUDIENCE payload from the AI into the hierarchy-aware
 * `locationSelections` shape the reducer stores, with ancestor back-fill so a
 * deep-level leaf is never stored without its ancestor chain.
 *
 * The backend copilot prompt now carries the tenant's location hierarchy and
 * instructs the AI to dispatch `locationSelections: { "<levelId>": [names] }`.
 * Older prompts / cached conversations / document-extraction flows may still
 * emit the legacy flat `regions: [names]` and `countries: [names]` arrays. We
 * normalize all three shapes here so the reducer only has to deal with one.
 *
 * - If the AI already sent `locationSelections`, walk each leaf up the
 *   hierarchy and merge missing ancestor names at their level keys (BUG-080).
 * - If only `regions` is present, map it under the top-most builder level.
 * - If only `countries` is present, map it under the second builder level,
 *   then back-fill ancestors so the parent region key is also populated.
 * - If both are present, populate both slots and back-fill.
 * - The legacy fields stay on the payload untouched so any downstream reader
 *   that still looks at `audience.regions` keeps working during the migration
 *   window (the separate cleanup ticket retires that).
 *
 * Pure function — caller flattens the location hierarchy tree once and passes
 * the result in, so this can be unit-tested without a live hierarchy fetch.
 * Pass `locationValues = []` (the default) to skip ancestor back-fill, which
 * preserves legacy behavior when the hierarchy hasn't loaded yet.
 */
/** Lightweight role reference used by the audience-payload translator. */
export type AudienceTranslatorRole = { id: string; name: string };

/**
 * Translate AI-emitted role display names in `userRoles` into role-id UUIDs.
 *
 * BUG-082 / BUG-020 frontend follow-up: the wire format for ROLE audience
 * rules is the role's `ClientRole.id`, but the AI's prompt continues to
 * speak in display-name terms. The resolve step is the single translation
 * boundary — entries that already look like UUIDs and match a known role id
 * pass through; entries that match a known role's name (case-insensitive)
 * are translated to that role's id; unknown names are dropped (so the wire
 * never carries hallucinated role names).
 *
 * Returns `null` when there is nothing to translate (no `userRoles` in the
 * payload or no `availableRoles` to resolve against), letting the caller
 * leave the payload reference untouched.
 */
function resolveUserRolesToIds(
  rawRoles: unknown,
  availableRoles: AudienceTranslatorRole[],
): string[] | null {
  if (!Array.isArray(rawRoles)) return null;
  if (availableRoles.length === 0) return null;

  const idSet = new Set(availableRoles.map((r) => r.id));
  const idByName = new Map<string, string>();
  for (const r of availableRoles) idByName.set(r.name.toLowerCase(), r.id);

  const resolved: string[] = [];
  const seen = new Set<string>();
  for (const entry of rawRoles) {
    if (typeof entry !== "string") continue;
    const trimmed = entry.trim();
    if (!trimmed) continue;
    let id: string | undefined;
    if (idSet.has(trimmed)) {
      id = trimmed;
    } else {
      id = idByName.get(trimmed.toLowerCase());
    }
    if (id && !seen.has(id)) {
      resolved.push(id);
      seen.add(id);
    }
  }
  return resolved;
}

export function translateAudiencePayload(
  payload: Record<string, unknown>,
  builderLevels: Array<{ id: string; name: string; depth: number }>,
  locationValues: AudienceTranslatorLocationValue[] = [],
  availableRoles: AudienceTranslatorRole[] = [],
): Record<string, unknown> {
  const hasLocationSelections =
    payload.locationSelections !== undefined &&
    payload.locationSelections !== null &&
    typeof payload.locationSelections === "object";

  const legacyRegions = Array.isArray(payload.regions)
    ? (payload.regions as string[])
    : undefined;
  const legacyCountries = Array.isArray(payload.countries)
    ? (payload.countries as string[])
    : undefined;

  let selections: Record<string, string[]> | null = null;
  let payloadChanged = false;

  if (hasLocationSelections) {
    selections = { ...(payload.locationSelections as Record<string, string[]>) };
  } else if (legacyRegions || legacyCountries) {
    if (builderLevels.length > 0) {
      const newSelections: Record<string, string[]> = {};
      if (legacyRegions && legacyRegions.length > 0) {
        const topLevel = builderLevels[0];
        if (topLevel) newSelections[topLevel.id] = legacyRegions;
      }
      if (legacyCountries && legacyCountries.length > 0) {
        const secondLevel = builderLevels[1];
        if (secondLevel) newSelections[secondLevel.id] = legacyCountries;
      }
      if (Object.keys(newSelections).length > 0) {
        selections = newSelections;
        payloadChanged = true;
      }
    }
  }

  if (selections) {
    const filled = backfillLocationAncestors(selections, locationValues);
    if (filled !== selections) {
      selections = filled;
      payloadChanged = true;
    }
  }

  // Resolve `userRoles` display names to role-id UUIDs (BUG-082).
  let resolvedUserRoles: string[] | null = null;
  if (
    Array.isArray(payload.userRoles) &&
    (payload.userRoles as unknown[]).length > 0
  ) {
    const candidate = resolveUserRolesToIds(payload.userRoles, availableRoles);
    if (candidate !== null) {
      const original = payload.userRoles as unknown[];
      const sameLength = candidate.length === original.length;
      const sameOrder =
        sameLength && candidate.every((v, i) => v === original[i]);
      if (!sameOrder) {
        resolvedUserRoles = candidate;
        payloadChanged = true;
      }
    }
  }

  if (!payloadChanged) return payload;

  const result: Record<string, unknown> = { ...payload };
  if (selections) result.locationSelections = selections;
  if (resolvedUserRoles !== null) result.userRoles = resolvedUserRoles;
  return result;
}

const ACTION_TO_STEP: Record<string, BuilderStep> = {
  UPDATE_BASICS: "basics",
  UPDATE_SCHEDULE: "schedule",
  UPDATE_AUDIENCE: "audience",
  UPDATE_BUDGET: "budget",
  UPDATE_CRITERIA: "criteria",
  UPDATE_APPROVAL: "approval",
};

export function useAiChat() {
  const { state, dispatch } = useBuilder();
  const { data: builderLevels = [] } = useLocationBuilderOptions();
  const { data: locationHierarchy } = useLocationHierarchy();
  // Drives BUG-082 resolve step in translateAudiencePayload — AI emits role
  // display names; we flip them to role-id UUIDs at the dispatch boundary.
  const { data: roleOptions = [] } = useExternalRoles();
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamingText, setStreamingText] = useState("");
  const [isFillingFields, setIsFillingFields] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const pendingSuggestionsRef = useRef<string[]>([]);
  const abortRef = useRef<AbortController | null>(null);
  const deferredActionsRef = useRef<BuilderAction[]>([]);

  // Keep a ref to the latest state so callbacks always audit against fresh data,
  // not the stale closure captured when startStream was created.
  const stateRef = useRef(state);
  useEffect(() => {
    stateRef.current = state;
  });

  // Builder levels drive the AI-payload translator; keep them in a ref so the
  // translator inside the streaming callback always sees the latest load, not
  // whatever was loaded when startStream was created.
  const builderLevelsRef = useRef(builderLevels);
  useEffect(() => {
    builderLevelsRef.current = builderLevels;
  });

  // Flatten the location-hierarchy tree once per load and keep it in a ref so
  // the budget translator inside the streaming callback always sees the latest
  // values without re-flattening on every action.
  const locationValuesRef = useRef<BudgetTranslatorLocationValue[]>([]);
  // Audience translator needs levelId in addition to id/name/parentId so it can
  // back-fill ancestor entries at the correct level keys when the AI sends a
  // deep-leaf payload without its chain (BUG-080).
  const audienceLocationValuesRef = useRef<AudienceTranslatorLocationValue[]>([]);
  // Audience translator also needs the available external roles to flip
  // AI-emitted display names into role-id UUIDs at the dispatch boundary
  // (BUG-082). `roleOptions` is `{ value: id, label: name }[]` — repackage
  // into the `{ id, name }` shape the translator expects.
  const audienceRolesRef = useRef<AudienceTranslatorRole[]>([]);
  useEffect(() => {
    audienceRolesRef.current = roleOptions.map((r) => ({
      id: r.value,
      name: r.label,
    }));
  });
  useEffect(() => {
    locationValuesRef.current = locationHierarchy?.tree
      ? flattenLocationValuesForBudget(locationHierarchy.tree)
      : [];
    audienceLocationValuesRef.current = locationHierarchy?.tree
      ? flattenLocationValuesForAudience(locationHierarchy.tree)
      : [];
  }, [locationHierarchy]);

  const cancel = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setIsStreaming(false);
    setStreamingText("");
    setIsFillingFields(false);
    deferredActionsRef.current = [];

    // Surface any buffered suggestions so the user sees them after pausing
    setSuggestions(pendingSuggestionsRef.current);
    pendingSuggestionsRef.current = [];
  }, []);

  /** Build the request payload and streaming callbacks, then kick off streaming. */
  const startStream = useCallback(
    (text: string, file?: File) => {
      // Build conversation history from existing messages (sliding window of 20)
      const existingMessages = state.chatMessages;
      const historyMessages: AiChatMessageEntry[] = existingMessages
        .slice(-19) // Leave room for the new user message
        .map((m) => ({ role: m.role, content: m.content }));
      historyMessages.push({ role: "user", content: text });

      const request: AiChatRequest = {
        conversationHistory: historyMessages,
        currentState: snapshotState(state),
        incentiveType: state.basics.incentiveType,
      };

      let accumulatedText = "";
      let hadActionSinceLastText = false;

      const callbacks = {
        onTextDelta: (data: { text: string }) => {
          // When text arrives after a tool-use round, the two text blocks are
          // separate thoughts. Insert a paragraph break so they don't run
          // together (e.g. "Done.Now let's…" → "Done.\n\nNow let's…").
          if (
            hadActionSinceLastText &&
            accumulatedText.length > 0 &&
            data.text.length > 0
          ) {
            accumulatedText += "\n\n";
          }
          hadActionSinceLastText = false;
          accumulatedText += data.text;
          setStreamingText(accumulatedText);
        },
        onAction: (data: {
          type: string;
          payload: Record<string, unknown>;
        }) => {
          hadActionSinceLastText = true;
          console.log("[AI Action]", JSON.stringify(data, null, 2));
          // Map SSE action to BuilderAction dispatch
          const actionType = data.type as BuilderAction["type"];
          const payload = data.payload;

          // SHOW_FORECASTING — navigate to the forecasting view
          if (actionType === "SHOW_FORECASTING") {
            dispatch({ type: "SHOW_FORECASTING" });
            return;
          }

          // CONFIRM_CREATE — defer until streaming completes so summary text renders first.
          // The actual audit gate runs in onDone when we flush deferred actions.
          if ((actionType as string) === "CONFIRM_CREATE") {
            deferredActionsRef.current.push({
              type: "REQUEST_CREATE_CONFIRMATION",
            });
            return;
          }

          // SET_ACTIVE_STEP and MARK_STEP_COMPLETE use a step name string
          if (
            actionType === "SET_ACTIVE_STEP" ||
            actionType === "MARK_STEP_COMPLETE" ||
            actionType === "MARK_STEP_INCOMPLETE"
          ) {
            const step =
              (payload as { step?: string }).step ??
              (typeof payload === "string" ? payload : undefined);
            if (step) {
              // Defer MARK_STEP_COMPLETE to onDone so the audit runs AFTER
              // React has re-rendered with the UPDATE_* dispatches from this stream.
              // Without deferral, the audit sees stale pre-update state and blocks
              // legitimate completions (e.g., AI fills basics then marks basics complete).
              if (actionType === "MARK_STEP_COMPLETE") {
                deferredActionsRef.current.push({
                  type: "MARK_STEP_COMPLETE",
                  payload: step,
                } as BuilderAction);
                return;
              }
              dispatch({ type: actionType, payload: step } as BuilderAction);
            }
          } else if (actionType.startsWith("UPDATE_")) {
            setIsFillingFields(true);

            // Translate AI dispatches into the shapes the reducer/UI expect.
            //
            //   - UPDATE_AUDIENCE: legacy flat regions/countries → hierarchical
            //     locationSelections (translateAudiencePayload).
            //   - UPDATE_BUDGET: name-keyed locationBudgets → UUID-keyed, plus
            //     derive globalBudgets[currencyId] from the top-level sum when
            //     the AI omits it (translateBudgetPayload — BUG-071 follow-up).
            const effectivePayload =
              actionType === "UPDATE_AUDIENCE"
                ? translateAudiencePayload(
                    payload,
                    builderLevelsRef.current,
                    audienceLocationValuesRef.current,
                    audienceRolesRef.current,
                  )
                : actionType === "UPDATE_BUDGET"
                  ? translateBudgetPayload(payload, locationValuesRef.current)
                  : payload;

            // Resolve journey stage names to IDs before dispatching
            if (
              actionType === "UPDATE_CRITERIA" &&
              (payload as Partial<CriteriaData>).journeyStages?.length
            ) {
              resolveJourneyStages(
                (payload as Partial<CriteriaData>).journeyStages as Array<{
                  incentiveName?: string;
                  linkedIncentiveId?: string;
                  sortOrder: number;
                }>,
              ).then((resolved) => {
                const resolvedPayload = {
                  ...payload,
                  journeyStages: resolved,
                } as Partial<CriteriaData>;
                dispatch({ type: "UPDATE_CRITERIA", payload: resolvedPayload });

                const step = ACTION_TO_STEP[actionType];
                if (step) dispatch({ type: "EXPAND_STEP", payload: step });

                if (isCriteriaValid(resolvedPayload)) {
                  dispatch({ type: "MARK_STEP_COMPLETE", payload: "criteria" });
                }
              });
              return;
            }

            dispatch({
              type: actionType,
              payload: effectivePayload,
            } as BuilderAction);

            // Auto-expand the accordion step the AI just updated
            const step = ACTION_TO_STEP[actionType];
            if (step) {
              dispatch({ type: "EXPAND_STEP", payload: step });
            }

            // Auto-complete criteria step when AI fills valid criteria data
            if (
              actionType === "UPDATE_CRITERIA" &&
              isCriteriaValid(payload as Partial<CriteriaData>)
            ) {
              dispatch({ type: "MARK_STEP_COMPLETE", payload: "criteria" });
            }
          }
        },
        onSuggestions: (data: { suggestions: string[] }) => {
          // Buffer suggestions until streaming ends — chips should not pop up while thinking
          pendingSuggestionsRef.current = data.suggestions;
        },
        onDone: () => {
          // Finalize: add the complete assistant message to chat
          if (accumulatedText.trim()) {
            const aiMsg = makeMsg("assistant", accumulatedText.trim());
            dispatch({ type: "ADD_CHAT_MESSAGE", payload: aiMsg });
          }
          dispatch({ type: "SET_CHAT_LOADING", payload: false });
          setIsStreaming(false);
          setStreamingText("");
          setIsFillingFields(false);
          abortRef.current = null;

          // Flush buffered suggestions now that streaming is complete
          setSuggestions(pendingSuggestionsRef.current);
          pendingSuggestionsRef.current = [];

          // Flush deferred actions (MARK_STEP_COMPLETE, CONFIRM_CREATE) after React
          // re-renders so stateRef has the latest values from UPDATE_* dispatches.
          const deferred = [...deferredActionsRef.current];
          deferredActionsRef.current = [];

          if (deferred.length > 0) {
            requestAnimationFrame(() => {
              const blockedSteps: Array<{
                step: BuilderStep;
                missing: string[];
              }> = [];

              for (const action of deferred) {
                if (action.type === "REQUEST_CREATE_CONFIRMATION") {
                  // Hard gate: audit against fresh state and block if incomplete
                  const fieldAudit = auditStepFields(stateRef.current);
                  const incompleteSteps = BUILDER_STEPS.filter(
                    (step) => !fieldAudit[step].actuallyComplete,
                  );
                  if (incompleteSteps.length > 0) {
                    const missingDetails = incompleteSteps.map((step) => {
                      const missing = fieldAudit[step].missing;
                      return `**${STEP_LABELS[step]}**: ${humanizeMissing(missing)}`;
                    });
                    const warningMsg = makeMsg(
                      "assistant",
                      `We're almost there, but a few required fields are still empty:\n\n${missingDetails.join("\n")}\n\nOnce those are filled in, just let me know and we'll get this created.`,
                    );
                    dispatch({ type: "ADD_CHAT_MESSAGE", payload: warningMsg });
                    console.warn(
                      "[AI Guard] Blocked CONFIRM_CREATE — incomplete steps:",
                      incompleteSteps,
                    );
                  } else {
                    dispatch(action);
                  }
                } else if (action.type === "MARK_STEP_COMPLETE") {
                  // Guard: only mark complete if the step's required fields are actually filled
                  const fieldAudit = auditStepFields(stateRef.current);
                  const step = action.payload as BuilderStep;
                  const stepAudit = fieldAudit[step];
                  if (stepAudit && !stepAudit.actuallyComplete) {
                    blockedSteps.push({ step, missing: stepAudit.missing });
                    console.warn(
                      `[AI Guard] Blocked MARK_STEP_COMPLETE for "${step}" — missing fields:`,
                      stepAudit.missing,
                    );
                  } else {
                    dispatch(action);
                  }
                } else {
                  dispatch(action);
                }
              }

              // If any MARK_STEP_COMPLETE actions were blocked, inject a correction
              // message so the user knows which steps still need attention — even if
              // the AI's text claimed everything was done.
              if (blockedSteps.length > 0) {
                const details = blockedSteps.map(
                  ({ step, missing }) =>
                    `**${STEP_LABELS[step]}**: ${humanizeMissing(missing)}`,
                );
                const correctionMsg = makeMsg(
                  "assistant",
                  `Heads up — a few steps still have required fields that need to be filled in:\n\n${details.join("\n")}\n\nLet me know if you'd like help completing them.`,
                );
                dispatch({ type: "ADD_CHAT_MESSAGE", payload: correctionMsg });
              }
            });
          }
        },
        onError: (message: string) => {
          const errorMsg = makeMsg(
            "assistant",
            `Something went wrong on my end: ${message}. Mind trying that again?`,
          );
          dispatch({ type: "ADD_CHAT_MESSAGE", payload: errorMsg });
          dispatch({ type: "SET_CHAT_LOADING", payload: false });
          setIsStreaming(false);
          setStreamingText("");
          setIsFillingFields(false);
          abortRef.current = null;
          deferredActionsRef.current = [];
          pendingSuggestionsRef.current = [];
        },
      };

      const controller = file
        ? streamAiChatWithDocument(request, file, callbacks)
        : streamAiChat(request, callbacks);

      abortRef.current = controller;
    },
    [state, dispatch],
  );

  const sendMessage = useCallback(
    (text: string) => {
      if (!text.trim() || isStreaming) return;

      const userMsg = makeMsg("user", text.trim());
      dispatch({ type: "ADD_CHAT_MESSAGE", payload: userMsg });
      dispatch({ type: "SET_CHAT_LOADING", payload: true });
      setIsStreaming(true);
      setStreamingText("");
      setSuggestions([]);

      startStream(text.trim());
    },
    [isStreaming, dispatch, startStream],
  );

  const sendMessageWithFile = useCallback(
    (text: string, file: File) => {
      if (isStreaming) return;

      const displayText = text.trim()
        ? `${text.trim()}\n\n📎 ${file.name}`
        : `📎 Uploaded: ${file.name}`;
      const userMsg = makeMsg("user", displayText);
      dispatch({ type: "ADD_CHAT_MESSAGE", payload: userMsg });
      dispatch({ type: "SET_CHAT_LOADING", payload: true });
      setIsStreaming(true);
      setStreamingText("");
      setSuggestions([]);

      // The message sent to the backend is the user's text (or a default prompt)
      const messageText =
        text.trim() ||
        `I've uploaded a document (${file.name}). Please extract all the incentive details from it and help me pre-fill the builder.`;

      startStream(messageText, file);
    },
    [isStreaming, dispatch, startStream],
  );

  return {
    sendMessage,
    sendMessageWithFile,
    cancel,
    isStreaming,
    isFillingFields,
    streamingText,
    suggestions,
  };
}
