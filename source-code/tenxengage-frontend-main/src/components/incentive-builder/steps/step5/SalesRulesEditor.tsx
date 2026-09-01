import { useState, useCallback, useEffect } from "react";
import { useBuilder } from "@/contexts/BuilderContext";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import {
  Plus,
  Trash2,
  Copy,
  ChevronDown,
  ChevronUp,
  Shield,
  DollarSign,
} from "lucide-react";
import type {
  SalesRequirement,
  EligibilityRuleGroup,
  EligibilityRule,
  EligibilityRuleType,
  PayoutConfig,
  PayoutType,
} from "@/types/incentive.types";
import { EligibilityRulesSection } from "./sales/EligibilityRulesSection";
import { PayoutCriteriaSection } from "./sales/PayoutCriteriaSection";
let _uid = 0;
const uid = () => `rule-${Date.now()}-${++_uid}`;

const makeRule = (): EligibilityRule => ({
  id: uid(),
  ruleType: "" as EligibilityRuleType,
  selectedProducts: [],
  customerTypes: [],
});

const makeGroup = (): EligibilityRuleGroup => ({
  id: uid(),
  rules: [makeRule()],
});

const makePayoutConfig = (currencyId: string): PayoutConfig => ({
  currencyId,
  payoutType: "" as PayoutType,
  bands: [{ id: uid(), minAmount: "", maxAmount: "", payoutValue: "" }],
});

function makeRequirement(
  index: number,
  currencies: string[],
): SalesRequirement {
  return {
    id: uid(),
    name: `Requirement ${index}`,
    eligibilityGroups: [makeGroup()],
    payouts: currencies.map((c) => makePayoutConfig(c)),
  };
}

const REQ_COLORS = [
  {
    border: "border-l-blue-500",
    bg: "bg-blue-500/5",
    badge: "bg-blue-500 text-white",
  },
  {
    border: "border-l-violet-500",
    bg: "bg-violet-500/5",
    badge: "bg-violet-500 text-white",
  },
  {
    border: "border-l-emerald-500",
    bg: "bg-emerald-500/5",
    badge: "bg-emerald-500 text-white",
  },
  {
    border: "border-l-amber-500",
    bg: "bg-amber-500/5",
    badge: "bg-amber-500 text-white",
  },
  {
    border: "border-l-rose-500",
    bg: "bg-rose-500/5",
    badge: "bg-rose-500 text-white",
  },
  {
    border: "border-l-cyan-500",
    bg: "bg-cyan-500/5",
    badge: "bg-cyan-500 text-white",
  },
];

export function SalesRulesEditor() {
  const { state, dispatch } = useBuilder();
  const requirements = state.criteria.salesRequirements;
  const activeCurrencies = state.budgetData.selectedCurrencies;

  // Normalize AI-provided requirements that may be missing IDs or nested fields
  useEffect(() => {
    const needsNormalization = requirements.some(
      (r) =>
        !r.id ||
        !r.eligibilityGroups ||
        !r.payouts ||
        (r.eligibilityGroups ?? []).some(
          (g) => !g.id || (g.rules ?? []).some((rule) => !rule.id),
        ) ||
        (r.payouts ?? []).some((p) => (p.bands ?? []).some((b) => !b.id)),
    );
    if (!needsNormalization) return;

    const normalized = requirements.map((req) => ({
      ...req,
      id: req.id ?? uid(),
      eligibilityGroups: (req.eligibilityGroups ?? [makeGroup()]).map((g) => ({
        ...g,
        id: g.id ?? uid(),
        rules: (g.rules ?? [makeRule()]).map((r) => ({
          ...r,
          id: r.id ?? uid(),
        })),
      })),
      payouts: (
        req.payouts ?? activeCurrencies.map((c) => makePayoutConfig(c))
      ).map((p) => ({
        ...p,
        bands: (p.bands ?? []).map((b) => ({
          ...b,
          id: b.id ?? uid(),
        })),
      })),
    }));
    dispatch({
      type: "UPDATE_CRITERIA",
      payload: { salesRequirements: normalized },
    });
  }, [requirements, activeCurrencies, dispatch]);

  const [collapsedMap, setCollapsedMap] = useState<Record<string, boolean>>({});

  const setRequirements = useCallback(
    (updater: (prev: SalesRequirement[]) => SalesRequirement[]) => {
      dispatch({
        type: "UPDATE_CRITERIA",
        payload: { salesRequirements: updater(requirements) },
      });
    },
    [dispatch, requirements],
  );

  function addRequirement() {
    setRequirements((prev) => [
      ...prev,
      makeRequirement(prev.length + 1, activeCurrencies),
    ]);
  }

  function updateRequirement(
    reqId: string,
    updater: (r: SalesRequirement) => SalesRequirement,
  ) {
    setRequirements((prev) =>
      prev.map((r) => (r.id === reqId ? updater(r) : r)),
    );
  }

  function removeRequirement(reqId: string) {
    setRequirements((prev) => prev.filter((r) => r.id !== reqId));
  }

  function duplicateRequirement(reqId: string) {
    setRequirements((prev) => {
      const source = prev.find((r) => r.id === reqId);
      if (!source) return prev;
      const clone: SalesRequirement = JSON.parse(JSON.stringify(source));
      clone.id = uid();
      clone.name = `${source.name} (Copy)`;
      (clone.eligibilityGroups ?? []).forEach((g) => {
        g.id = uid();
        (g.rules ?? []).forEach((r) => {
          r.id = uid();
        });
      });
      (clone.payouts ?? []).forEach((p) => {
        (p.bands ?? []).forEach((b) => {
          b.id = uid();
        });
      });
      return [...prev, clone];
    });
  }

  function toggleCollapse(reqId: string) {
    setCollapsedMap((prev) => ({ ...prev, [reqId]: !prev[reqId] }));
  }

  return (
    <div className="space-y-5">
      {requirements.map((req, reqIdx) => {
        const colorsObj = REQ_COLORS[reqIdx % REQ_COLORS.length]!;
        const isCollapsed = collapsedMap[req.id ?? reqIdx] ?? false;

        return (
          <Card
            key={req.id ?? reqIdx}
            className={`transition-[box-shadow] border-l-4 ${colorsObj.border} ${isCollapsed ? "" : "shadow-md"}`}
          >
            {/* Header */}
            <CardHeader className={`py-3 px-4 rounded-t-lg ${colorsObj.bg}`}>
              <div className="flex items-center gap-3 min-h-[36px]">
                <button
                  type="button"
                  onClick={() => toggleCollapse(req.id ?? String(reqIdx))}
                  className="text-muted-foreground hover:text-foreground transition-colors flex items-center justify-center h-8 w-8 shrink-0"
                >
                  {isCollapsed ? (
                    <ChevronDown className="h-5 w-5" />
                  ) : (
                    <ChevronUp className="h-5 w-5" />
                  )}
                </button>
                <div className="flex items-center gap-2.5 flex-1 min-w-0">
                  <Badge
                    className={`shrink-0 text-xs font-semibold border-0 leading-none py-1 px-2.5 ${colorsObj.badge}`}
                  >
                    #{reqIdx + 1}
                  </Badge>
                  <Input
                    value={req.name}
                    onChange={(e) =>
                      updateRequirement(req.id ?? String(reqIdx), (r) => ({
                        ...r,
                        name: e.target.value,
                      }))
                    }
                    className="h-8 text-sm font-semibold border-none shadow-none px-1 focus-visible:ring-1 bg-transparent"
                    placeholder="Requirement name..."
                  />
                </div>
                <div className="flex items-center gap-1 shrink-0">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    onClick={() =>
                      duplicateRequirement(req.id ?? String(reqIdx))
                    }
                  >
                    <Copy className="h-3.5 w-3.5" />
                  </Button>
                  {requirements.length > 1 && (
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8 text-destructive"
                      onClick={() =>
                        removeRequirement(req.id ?? String(reqIdx))
                      }
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  )}
                </div>
              </div>
            </CardHeader>

            {!isCollapsed && (
              <CardContent className="space-y-0 pt-0 pb-4">
                {/* Eligibility Section */}
                <div className="mt-4 rounded-lg border border-blue-200 dark:border-blue-900/40 bg-blue-50/50 dark:bg-blue-950/20 p-4 space-y-3">
                  <div className="flex items-center gap-2 mb-1">
                    <div className="flex h-7 w-7 items-center justify-center rounded-md bg-blue-100 dark:bg-blue-900/50">
                      <Shield className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-foreground">
                        Eligibility Rules
                      </p>
                      <p className="text-xs text-muted-foreground">
                        <Badge
                          variant="secondary"
                          className="text-xs px-1.5 py-0 mr-1"
                        >
                          AND
                        </Badge>
                        within groups —
                        <Badge
                          variant="outline"
                          className="text-xs px-1.5 py-0 mx-1"
                        >
                          OR
                        </Badge>
                        between groups
                      </p>
                    </div>
                  </div>
                  <EligibilityRulesSection
                    groups={req.eligibilityGroups ?? []}
                    onChange={(groups) =>
                      updateRequirement(req.id ?? String(reqIdx), (r) => ({
                        ...r,
                        eligibilityGroups: groups,
                      }))
                    }
                  />
                </div>

                {/* Payout Section */}
                <div className="mt-4 rounded-lg border border-emerald-200 dark:border-emerald-900/40 bg-emerald-50/50 dark:bg-emerald-950/20 p-4 space-y-3">
                  <div className="flex items-center gap-2 mb-1">
                    <div className="flex h-7 w-7 items-center justify-center rounded-md bg-emerald-100 dark:bg-emerald-900/50">
                      <DollarSign className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-foreground">
                        Payout Criteria
                      </p>
                      <p className="text-xs text-muted-foreground">
                        Configure rewards per currency when eligibility is met
                      </p>
                    </div>
                  </div>
                  <PayoutCriteriaSection
                    payouts={req.payouts ?? []}
                    activeCurrencies={activeCurrencies}
                    onChange={(payouts) =>
                      updateRequirement(req.id ?? String(reqIdx), (r) => ({
                        ...r,
                        payouts,
                      }))
                    }
                  />
                </div>
              </CardContent>
            )}
          </Card>
        );
      })}

      <Button
        variant="outline"
        className="w-full border-dashed"
        onClick={addRequirement}
      >
        <Plus className="h-4 w-4 mr-2" />
        Add Requirement
      </Button>
    </div>
  );
}
