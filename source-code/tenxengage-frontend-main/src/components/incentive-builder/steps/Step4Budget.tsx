import { useEffect, useMemo } from "react";
import { useBuilder } from "@/contexts/BuilderContext";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
import { MultiSelect } from "@/components/ui/multi-select";
import type { MultiSelectOption } from "@/components/ui/multi-select";
import { Users } from "lucide-react";
import {
  getCurrency,
  currencyIds,
  monetaryCurrencyIds,
} from "@/config/currencies";
import type { IncentiveType } from "@/types/incentive.types";
import {
  useLocationBuilderOptions,
  useLocationHierarchy,
} from "@/hooks/useLocationApi";
import {
  AllocationIndicator,
  BudgetAllocationTree,
} from "./budget/BudgetAllocationTree";
import {
  areAllRootsFilled,
  buildBudgetTree,
  findOvershoots,
  parseAmount,
  summarizeAllocation,
} from "./budget/budgetTreeHelpers";

const blockInvalidChars = (e: React.KeyboardEvent) => {
  if (
    e.key === "." ||
    e.key === "," ||
    e.key === "-" ||
    e.key === "e" ||
    e.key === "E"
  )
    e.preventDefault();
};

function getIncentiveKind(
  type: IncentiveType | null,
): "sales" | "training" | "activity" | "journey" {
  switch (type) {
    case "TRAINING":
      return "training";
    case "ACTIVITY":
      return "activity";
    case "JOURNEY":
      return "journey";
    default:
      return "sales";
  }
}

interface Step4BudgetProps {
  onContinue?: () => void;
}

export function Step4Budget({ onContinue }: Step4BudgetProps) {
  const { state, dispatch } = useBuilder();
  const { budgetData } = state;

  // Compute currency options dynamically (reflects hydrated state)
  const CURRENCY_OPTIONS: MultiSelectOption[] = currencyIds.map((id) => {
    const cfg = getCurrency(id);
    const Icon = cfg.icon;
    return {
      value: id,
      label: cfg.label,
      icon: <Icon className="h-4 w-4 text-muted-foreground" />,
      meta: cfg.type === "monetary" ? "Monetary" : "Non-Monetary",
    };
  });
  const MONETARY_CURRENCIES = [...monetaryCurrencyIds];

  const incentiveKind = getIncentiveKind(state.basics.incentiveType);
  const isJourney = incentiveKind === "journey";
  const isCompletionBased =
    incentiveKind === "training" || incentiveKind === "activity";
  const showRewardAmounts =
    isCompletionBased || (isJourney && budgetData.journeyHasOwnRewards);
  const hideMaxPerUser = isCompletionBased || isJourney;
  const journeyRewardsOnly = isJourney && !budgetData.journeyHasOwnRewards;

  const selectedMonetary = budgetData.selectedCurrencies.filter((c) =>
    MONETARY_CURRENCIES.includes(c),
  );
  const hasMonetary = selectedMonetary.length > 0;

  // Dynamic location levels for budget allocation
  const { data: builderLevels = [] } = useLocationBuilderOptions();
  const { data: locationHierarchy } = useLocationHierarchy();

  // Eligibility-scoped allocation tree (same shape, all currencies share it).
  const budgetTree = useMemo(
    () => buildBudgetTree(locationHierarchy, state.audience.locationSelections),
    [locationHierarchy, state.audience.locationSelections],
  );

  const isPerLocation =
    budgetData.budgetMode === "per-region" ||
    budgetData.budgetMode === "per-location";

  // Per-location validation: per-currency total must be entered, every
  // top-level (root) row must be typed (children auto-fill via residual and
  // remain optional), and there must be no overshoots at any depth in any
  // currency.
  const perLocationFilled = useMemo(() => {
    if (!hasMonetary) return true;
    if (!isPerLocation) {
      return selectedMonetary.every((c) => !!budgetData.globalBudgets[c]);
    }
    if (budgetTree.length === 0) return false;
    for (const c of selectedMonetary) {
      const globalTotal = parseAmount(budgetData.globalBudgets[c]);
      if (globalTotal === null || globalTotal <= 0) return false;
      const cValues = budgetData.locationBudgets[c] ?? {};
      if (!areAllRootsFilled(budgetTree, cValues)) return false;
      const overshoots = findOvershoots(budgetTree, cValues, globalTotal);
      if (overshoots.length > 0) return false;
    }
    return true;
  }, [
    hasMonetary,
    isPerLocation,
    selectedMonetary,
    budgetTree,
    budgetData.globalBudgets,
    budgetData.locationBudgets,
  ]);

  const monetaryBudgetsFilled = perLocationFilled;

  // Claimers per PO# validation (sales only, must be >= 1)
  const claimersValid =
    incentiveKind !== "sales" ||
    (!!budgetData.maxClaimersPerDeal &&
      parseInt(budgetData.maxClaimersPerDeal, 10) >= 1);

  // For journey with no own rewards, step is always valid if currencies are selected
  const isComplete = journeyRewardsOnly
    ? true
    : budgetData.selectedCurrencies.length > 0 &&
      monetaryBudgetsFilled &&
      claimersValid;

  useEffect(() => {
    if (isComplete && !state.completedSteps.includes("budget")) {
      dispatch({ type: "MARK_STEP_COMPLETE", payload: "budget" });
    } else if (!isComplete && state.completedSteps.includes("budget")) {
      dispatch({ type: "MARK_STEP_INCOMPLETE", payload: "budget" });
    }
  }, [isComplete, state.completedSteps, dispatch]);

  function update(partial: Partial<typeof budgetData>) {
    dispatch({ type: "UPDATE_BUDGET", payload: partial });
  }

  return (
    <div className="space-y-4">
      {/* Journey reward mode toggle */}
      {isJourney && (
        <div className="rounded-lg border border-indigo-500/20 bg-indigo-500/5 p-4 space-y-3">
          <div>
            <Label className="text-sm font-medium">
              Journey Completion Rewards
            </Label>
            <p className="text-xs text-muted-foreground mt-0.5">
              Should the journey itself have a budget and reward, or do partners
              only earn rewards from individual incentives?
            </p>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              className={`flex-1 rounded-md border px-3 py-2 text-sm transition-[border-color,background-color,color] ${
                budgetData.journeyHasOwnRewards
                  ? "border-primary bg-primary/10 text-foreground font-medium"
                  : "border-border bg-background text-muted-foreground hover:border-primary/30"
              }`}
              onClick={() => update({ journeyHasOwnRewards: true })}
            >
              <span className="block font-medium">
                Journey + Individual Rewards
              </span>
              <span className="block text-xs mt-0.5 opacity-70">
                Partners earn rewards for completing the journey AND each
                individual incentive
              </span>
            </button>
            <button
              type="button"
              className={`flex-1 rounded-md border px-3 py-2 text-sm transition-[border-color,background-color,color] ${
                !budgetData.journeyHasOwnRewards
                  ? "border-primary bg-primary/10 text-foreground font-medium"
                  : "border-border bg-background text-muted-foreground hover:border-primary/30"
              }`}
              onClick={() => update({ journeyHasOwnRewards: false })}
            >
              <span className="block font-medium">Individual Rewards Only</span>
              <span className="block text-xs mt-0.5 opacity-70">
                No extra journey-level budget — partners earn from each
                incentive independently
              </span>
            </button>
          </div>
        </div>
      )}

      {/* Journey individual-only mode: skip all budget fields */}
      {journeyRewardsOnly ? (
        <div className="rounded-lg border border-border bg-muted/30 p-4 text-center space-y-2">
          <p className="text-sm text-muted-foreground">
            No journey-level budget needed. Partners will earn rewards from each
            individual incentive within the journey.
          </p>
          {onContinue && (
            <Button className="w-full" onClick={onContinue}>
              Continue to Journey Stages
            </Button>
          )}
        </div>
      ) : (
        <>
          {/* Reward Currencies */}
          <div className="space-y-2">
            <Label>
              Reward Currencies <span className="text-destructive">*</span>
            </Label>
            <MultiSelect
              options={CURRENCY_OPTIONS}
              selected={budgetData.selectedCurrencies}
              onChange={(v) => update({ selectedCurrencies: v })}
              placeholder="Select currencies..."
            />
          </div>

          {/* Completion Reward Amounts — for Training, Activity, Journey (with own rewards) */}
          {showRewardAmounts && budgetData.selectedCurrencies.length > 0 && (
            <div className="space-y-3 rounded-lg border border-border p-4">
              <div>
                <Label className="text-sm font-medium">
                  {incentiveKind === "journey"
                    ? "Journey Completion Reward Per Currency"
                    : "Reward Per Completion"}{" "}
                  <span className="text-destructive">*</span>
                </Label>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {incentiveKind === "journey"
                    ? "How much of each selected currency will a partner user earn as a bonus for completing the full journey?"
                    : "How much of each selected currency will a partner user earn when they complete this incentive?"}
                </p>
              </div>
              <div className="grid grid-cols-1 gap-3">
                {budgetData.selectedCurrencies.map((id) => {
                  const cur = CURRENCY_OPTIONS.find((c) => c.value === id);
                  if (!cur) return null;
                  const Icon = getCurrency(id).icon;
                  const isMoney = MONETARY_CURRENCIES.includes(id);
                  return (
                    <div key={id} className="space-y-1.5">
                      <Label className="flex items-center gap-1.5 text-sm">
                        <Icon className="h-3.5 w-3.5" />
                        {cur.label} per completion{" "}
                        <span className="text-destructive">*</span>
                      </Label>
                      <div className="relative">
                        {isMoney && (
                          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
                            $
                          </span>
                        )}
                        <Input
                          type="number"
                          min="0"
                          step="1"
                          className={isMoney ? "pl-7" : ""}
                          placeholder={
                            isMoney
                              ? "e.g., 500"
                              : `e.g., 100 ${getCurrency(id).label.toLowerCase()}`
                          }
                          value={budgetData.rewardAmounts[id] ?? ""}
                          onKeyDown={blockInvalidChars}
                          onChange={(e) =>
                            update({
                              rewardAmounts: {
                                ...budgetData.rewardAmounts,
                                [id]: e.target.value,
                              },
                            })
                          }
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Budget Allocation — only shown when at least one monetary currency selected */}
          {hasMonetary && (
            <div className="rounded-lg border border-border p-4 space-y-4">
              <div className="flex items-center justify-between">
                <Label className="text-sm font-medium">Budget Allocation</Label>
                <div className="flex items-center gap-2 text-sm">
                  <span
                    className={
                      !isPerLocation
                        ? "text-foreground font-medium"
                        : "text-muted-foreground"
                    }
                  >
                    Global
                  </span>
                  <Switch
                    checked={isPerLocation}
                    onCheckedChange={(checked) => {
                      const topLevel = builderLevels[0];
                      update({
                        budgetMode: checked ? "per-location" : "global",
                        budgetLocationLevelId:
                          checked && topLevel ? topLevel.id : null,
                      });
                    }}
                  />
                  <span
                    className={
                      isPerLocation
                        ? "text-foreground font-medium"
                        : "text-muted-foreground"
                    }
                  >
                    Per Location
                  </span>
                </div>
              </div>

              {/* Per-currency total budget input — required in both modes. In
                  per-location mode this also acts as the root total the
                  allocation tree validates against. */}
              <div className="grid grid-cols-1 gap-3">
                {selectedMonetary.map((currencyId) => {
                  const curr = CURRENCY_OPTIONS.find(
                    (c) => c.value === currencyId,
                  );
                  const Icon = getCurrency(currencyId).icon;
                  const totalForSummary = isPerLocation
                    ? parseAmount(budgetData.globalBudgets[currencyId])
                    : null;
                  const topLevelSummary =
                    isPerLocation && budgetTree.length > 0
                      ? summarizeAllocation(
                          budgetTree,
                          budgetData.locationBudgets[currencyId] ?? {},
                          totalForSummary,
                        )
                      : null;
                  return (
                    <div key={currencyId} className="space-y-1.5">
                      <Label className="flex items-center gap-1.5 text-sm">
                        <Icon className="h-3.5 w-3.5" />
                        {curr?.label} Budget{" "}
                        <span className="text-destructive">*</span>
                      </Label>
                      <div className="relative">
                        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
                          $
                        </span>
                        <Input
                          type="number"
                          min="0"
                          step="1"
                          className="pl-7"
                          placeholder="e.g., 100,000"
                          value={budgetData.globalBudgets[currencyId] ?? ""}
                          onKeyDown={blockInvalidChars}
                          onChange={(e) =>
                            update({
                              globalBudgets: {
                                ...budgetData.globalBudgets,
                                [currencyId]: e.target.value,
                              },
                            })
                          }
                        />
                      </div>
                      {topLevelSummary && (
                        <AllocationIndicator
                          summary={topLevelSummary}
                          hasOvershoot={topLevelSummary.hasOvershoot}
                        />
                      )}
                    </div>
                  );
                })}
              </div>

              {/* Per-location mode: one allocation tree per monetary currency.
                  Trees share the same hierarchy structure but the user types
                  amounts per currency independently. */}
              {isPerLocation && (
                <div className="space-y-4 pt-2 border-t border-border">
                  {selectedMonetary.map((currencyId) => {
                    const curr = CURRENCY_OPTIONS.find(
                      (c) => c.value === currencyId,
                    );
                    const Icon = getCurrency(currencyId).icon;
                    const globalTotal = parseAmount(
                      budgetData.globalBudgets[currencyId],
                    );
                    return (
                      <div key={currencyId} className="space-y-2">
                        <Label className="flex items-center gap-1.5 text-sm font-medium">
                          <Icon className="h-3.5 w-3.5" />
                          {curr?.label} Allocation Tree
                        </Label>
                        <p className="text-xs text-muted-foreground">
                          Top-level rows (marked{" "}
                          <span className="text-destructive">*</span>) are
                          required. Drill in to break them down further; blank
                          children auto-fill the residual from the parent.
                        </p>
                        <BudgetAllocationTree
                          nodes={budgetTree}
                          values={
                            budgetData.locationBudgets[currencyId] ?? {}
                          }
                          globalTotal={globalTotal}
                          currencyPrefix="$"
                          currencyLabel={curr?.label}
                          onChange={(locationValueId, value) =>
                            update({
                              locationBudgets: {
                                ...budgetData.locationBudgets,
                                [currencyId]: {
                                  ...(budgetData.locationBudgets[
                                    currencyId
                                  ] || {}),
                                  [locationValueId]: value,
                                },
                              },
                            })
                          }
                        />
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Per-currency max caps — optional */}
              <div className="space-y-3 pt-2 border-t border-border">
                <div className="space-y-1">
                  <Label className="text-sm font-medium">
                    Max Per Partner Company (Optional)
                  </Label>
                  <p className="text-xs text-muted-foreground">
                    Maximum monetary reward a single partner company can earn,
                    per currency.
                  </p>
                </div>
                <div className="grid grid-cols-1 gap-2">
                  {selectedMonetary.map((currencyId) => {
                    const curr = CURRENCY_OPTIONS.find(
                      (c) => c.value === currencyId,
                    );
                    const Icon = getCurrency(currencyId).icon;
                    return (
                      <div key={currencyId} className="flex items-center gap-2">
                        <Label className="flex items-center gap-1 text-xs text-muted-foreground w-24 shrink-0">
                          <Icon className="h-3 w-3" />
                          {curr?.label}
                        </Label>
                        <div className="relative flex-1">
                          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
                            $
                          </span>
                          <Input
                            type="number"
                            min="0"
                            step="1"
                            className="pl-7"
                            placeholder="No limit"
                            value={
                              budgetData.maxPerPartnerByCurrency[currencyId] ??
                              ""
                            }
                            onKeyDown={blockInvalidChars}
                            onChange={(e) =>
                              update({
                                maxPerPartnerByCurrency: {
                                  ...budgetData.maxPerPartnerByCurrency,
                                  [currencyId]: e.target.value,
                                },
                              })
                            }
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>

                {!hideMaxPerUser && (
                  <>
                    <div className="space-y-1 pt-2">
                      <Label className="text-sm font-medium">
                        Max Per User (Optional)
                      </Label>
                      <p className="text-xs text-muted-foreground">
                        Maximum monetary reward a single partner user can earn,
                        per currency.
                      </p>
                    </div>
                    <div className="grid grid-cols-1 gap-2">
                      {selectedMonetary.map((currencyId) => {
                        const curr = CURRENCY_OPTIONS.find(
                          (c) => c.value === currencyId,
                        );
                        const Icon = getCurrency(currencyId).icon;
                        return (
                          <div
                            key={currencyId}
                            className="flex items-center gap-2"
                          >
                            <Label className="flex items-center gap-1 text-xs text-muted-foreground w-24 shrink-0">
                              <Icon className="h-3 w-3" />
                              {curr?.label}
                            </Label>
                            <div className="relative flex-1">
                              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
                                $
                              </span>
                              <Input
                                type="number"
                                min="0"
                                step="1"
                                className="pl-7"
                                placeholder="No limit"
                                value={
                                  budgetData.maxPerUserByCurrency[currencyId] ??
                                  ""
                                }
                                onKeyDown={blockInvalidChars}
                                onChange={(e) =>
                                  update({
                                    maxPerUserByCurrency: {
                                      ...budgetData.maxPerUserByCurrency,
                                      [currencyId]: e.target.value,
                                    },
                                  })
                                }
                              />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </>
                )}
              </div>

              {/* Claimers Per PO# — Sales incentives only */}
              {incentiveKind === "sales" && (
                <div className="space-y-1.5 pt-2 border-t border-border">
                  <Label className="flex items-center gap-1.5 text-sm">
                    <Users className="h-3.5 w-3.5" />
                    Claimers Per PO# <span className="text-destructive">*</span>
                  </Label>
                  <p className="text-xs text-muted-foreground">
                    Maximum number of unique users that can claim rewards
                    against a single PO#.
                  </p>
                  <Input
                    type="number"
                    min="1"
                    step="1"
                    className="w-32"
                    placeholder="e.g., 1"
                    value={budgetData.maxClaimersPerDeal}
                    onKeyDown={blockInvalidChars}
                    onChange={(e) => {
                      const val = e.target.value;
                      update({ maxClaimersPerDeal: val });
                    }}
                  />
                  {!claimersValid && (
                    <p className="text-xs text-destructive">
                      Must be at least 1 claimer.
                    </p>
                  )}
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
