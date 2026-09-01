import { useBuilder } from "@/contexts/BuilderContext";
import { Button } from "@/components/ui/button";
import { ArrowLeft, Megaphone, GraduationCap, FileCheck } from "lucide-react";
import { Step5Criteria } from "./steps/Step5Criteria";
import { cn } from "@/lib/utils";
import type { IncentiveType } from "@/types/incentive.types";
import { currencies } from "@/config/currencies";

const EDITOR_CONFIG: Record<
  IncentiveType,
  {
    title: string;
    subtitle: string;
    icon: React.ComponentType<{ className?: string }> | null;
  }
> = {
  SALES: {
    title: "Incentive Rules Engine",
    subtitle: "Define requirements with eligibility rules and payout criteria",
    icon: Megaphone,
  },
  TRAINING: {
    title: "Training Course Assignment",
    subtitle: "Select courses from your LMS and set completion requirements",
    icon: GraduationCap,
  },
  ACTIVITY: {
    title: "Activity Setup",
    subtitle:
      "Define activities partners must complete and the documents they need to upload",
    icon: FileCheck,
  },
  JOURNEY: {
    title: "Journey Stages",
    subtitle: "Select 2 or more incentives to form the journey stages",
    icon: null,
  },
};

interface CriteriaEditorPanelProps {
  onBack: () => void;
}

export function CriteriaEditorPanel({ onBack }: CriteriaEditorPanelProps) {
  const { state, dispatch } = useBuilder();
  const type = state.basics.incentiveType;

  if (!type) return null;

  const config = EDITOR_CONFIG[type];
  const Icon = config.icon;

  const c = state.criteria;

  const isValid = (() => {
    switch (type) {
      case "SALES":
        return (
          c.salesRequirements.length > 0 &&
          c.salesRequirements.every(
            (req) =>
              (req.eligibilityGroups ?? []).some((g) =>
                (g.rules ?? []).some((r) => {
                  if (!r.ruleType) return false;
                  if ((r.selectedProducts ?? []).length > 0) return true;
                  if ((r.listValues ?? []).length > 0) return true;
                  if (r.value === "true" || r.value === "false")
                    return !!r.operator;
                  if (!r.operator || !r.value) return false;
                  if (r.operator === "BETWEEN" && !r.valueMax) return false;
                  return true;
                }),
              ) &&
              (req.payouts ?? [])
                .filter((p) => {
                  const cfg = currencies[p.currencyId];
                  return !cfg || cfg.type === "monetary";
                })
                .every(
                  (p) =>
                    !!p.payoutType &&
                    (p.bands ?? []).length > 0 &&
                    (p.bands ?? []).every(
                      (b) => !!b.minAmount && !!b.maxAmount && !!b.payoutValue,
                    ),
                ),
          )
        );
      case "TRAINING":
        return c.trainingCourses.length > 0;
      case "ACTIVITY":
        return c.activityDefinitions.length > 0;
      case "JOURNEY":
        return c.journeyStages.length >= 2;
      default:
        return false;
    }
  })();

  function handleDone() {
    if (isValid) {
      dispatch({ type: "MARK_STEP_COMPLETE", payload: "criteria" });
    }
    onBack();
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="mb-4 shrink-0">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={handleDone}
            className="p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted transition-colors shrink-0"
          >
            <ArrowLeft className="h-4 w-4" />
          </button>
          {Icon && <Icon className="h-4 w-4 text-primary" />}
          <div>
            <h2 className="text-xl font-semibold text-foreground">
              {config.title}
            </h2>
            <p className="text-sm text-muted-foreground">{config.subtitle}</p>
          </div>
        </div>
      </div>

      {/* Editor content — scrollable, with AI lock overlay */}
      <div className="flex-1 relative min-h-0">
        <div
          className={cn(
            "absolute inset-0 px-2 overflow-y-auto",
            state.aiLocked && "pointer-events-none",
          )}
        >
          <Step5Criteria />
        </div>
        {/* AI lock overlay — blocks rules-engine interaction while Copilot is streaming */}
        {state.aiLocked && (
          <div className="absolute inset-0 bg-background/50 z-10 pointer-events-none" />
        )}
      </div>

      {/* Footer with Save button */}
      <div className="shrink-0 pt-4">
        <Button
          className="w-full h-10 text-sm font-semibold bg-primary hover:bg-primary/90"
          size="lg"
          onClick={handleDone}
          disabled={!isValid || state.aiLocked}
        >
          Save Incentive Criteria
        </Button>
      </div>
    </div>
  );
}
