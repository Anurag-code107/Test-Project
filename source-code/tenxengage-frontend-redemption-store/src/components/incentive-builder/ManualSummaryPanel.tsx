import { useMemo } from "react";
import { useBuilder } from "@/contexts/BuilderContext";
import { Button } from "@/components/ui/button";
import { FileText, Check, ChevronRight, CheckCircle } from "lucide-react";
import { INCENTIVE_TYPE_LABELS } from "@/types/incentive.types";
import type { IncentiveType } from "@/types/incentive.types";
import { BUILDER_STEPS, STEP5_LABELS } from "@/types/builder-state.types";
import type { BuilderStep } from "@/types/builder-state.types";
import { useExternalRoles } from "@/hooks/useBuilderConfig";

/** Section titles — step 5 is dynamic */
const SECTION_TITLES: Record<BuilderStep, string> = {
  basics: "Basic Information",
  schedule: "Timeline",
  audience: "Eligibility",
  budget: "Budget",
  criteria: "Criteria",
  approval: "Approval Criteria",
};

function getSection5Title(type: IncentiveType | null): string {
  if (!type) return SECTION_TITLES.criteria;
  return STEP5_LABELS[type];
}

interface ManualSummaryPanelProps {
  onStepClick?: (step: BuilderStep) => void;
  onComplete?: () => void;
}

export function ManualSummaryPanel({
  onStepClick,
  onComplete,
}: ManualSummaryPanelProps) {
  const { state, dispatch } = useBuilder();
  const incentiveType = state.basics.incentiveType;

  // Resolve role-id UUIDs in `audience.userRoles` back to display names for
  // the summary line. Falls back to the raw value when an id can't be matched
  // (legacy display-name rows during transition, or stale state).
  const { data: roleOptions = [] } = useExternalRoles();
  const roleLabelById = useMemo(() => {
    const map = new Map<string, string>();
    for (const r of roleOptions) map.set(r.value, r.label);
    return map;
  }, [roleOptions]);

  function handleSectionClick(step: BuilderStep) {
    dispatch({ type: "SET_ACTIVE_STEP", payload: step });
    onStepClick?.(step);
  }

  const allComplete = state.completedSteps.length >= BUILDER_STEPS.length;

  return (
    <div className="flex flex-col h-full rounded-xl border border-border bg-background overflow-hidden">
      {/* Header */}
      <div className="border-b border-border p-4 shrink-0">
        <div className="flex items-center gap-2">
          <FileText className="h-4 w-4 text-muted-foreground" />
          <span className="text-2xl font-semibold text-foreground">
            Incentive Summary
          </span>
        </div>
        <p className="text-xs text-muted-foreground mt-0.5">
          Click any field to edit
        </p>
      </div>

      {/* Scrollable content */}
      <div className="flex-1 overflow-y-auto px-5 py-4 space-y-5">
        {/* Section 1: Basic Information */}
        <SummarySection
          index={1}
          title={SECTION_TITLES.basics}
          completed={state.completedSteps.includes("basics")}
          onClick={() => handleSectionClick("basics")}
        >
          {state.basics.name || state.basics.description || incentiveType ? (
            <div className="pl-8 space-y-0.5 text-sm">
              {state.basics.name && (
                <FieldRow
                  label="Name:"
                  value={state.basics.name}
                  onClick={() => handleSectionClick("basics")}
                />
              )}
              {state.basics.description && (
                <FieldRow
                  label="Description:"
                  value={state.basics.description}
                  truncate
                  onClick={() => handleSectionClick("basics")}
                />
              )}
              {incentiveType && (
                <button
                  type="button"
                  onClick={() => handleSectionClick("basics")}
                  className="flex justify-between items-center w-full hover:bg-primary/5 rounded-md px-2 py-1.5 -mx-1 transition-[background-color] cursor-pointer group"
                >
                  <span className="text-muted-foreground group-hover:text-foreground transition-colors">
                    Type:
                  </span>
                  <span className="inline-flex items-center px-2 py-0.5 rounded-md border border-border text-xs font-medium text-muted-foreground">
                    {INCENTIVE_TYPE_LABELS[incentiveType]}
                  </span>
                </button>
              )}
            </div>
          ) : (
            <EmptyState onClick={() => handleSectionClick("basics")} />
          )}
        </SummarySection>

        {/* Section 2: Timeline */}
        <SummarySection
          index={2}
          title={SECTION_TITLES.schedule}
          completed={state.completedSteps.includes("schedule")}
          onClick={() => handleSectionClick("schedule")}
          border
        >
          {state.schedule.fiscalYears.length > 0 ||
          state.schedule.startDate ||
          state.schedule.endDate ? (
            <div className="pl-8 space-y-0.5 text-sm">
              {state.schedule.fiscalYears.length > 0 && (
                <FieldRow
                  label="Fiscal Year(s):"
                  value={state.schedule.fiscalYears.join(", ")}
                  onClick={() => handleSectionClick("schedule")}
                />
              )}
              {state.schedule.fiscalQuarters.length > 0 && (
                <FieldRow
                  label="Quarter(s):"
                  value={state.schedule.fiscalQuarters.join(", ")}
                  onClick={() => handleSectionClick("schedule")}
                />
              )}
              {state.schedule.startDate && (
                <FieldRow
                  label="Start:"
                  value={state.schedule.startDate}
                  onClick={() => handleSectionClick("schedule")}
                />
              )}
              {state.schedule.endDate && (
                <FieldRow
                  label="End:"
                  value={state.schedule.endDate}
                  onClick={() => handleSectionClick("schedule")}
                />
              )}
            </div>
          ) : (
            <EmptyState onClick={() => handleSectionClick("schedule")} />
          )}
        </SummarySection>

        {/* Section 3: Eligibility */}
        <SummarySection
          index={3}
          title={SECTION_TITLES.audience}
          completed={state.completedSteps.includes("audience")}
          onClick={() => handleSectionClick("audience")}
          border
        >
          {(() => {
            // Flatten every level's selection — the summary just lists names,
            // it doesn't care which hierarchy level they came from.
            const allLocationNames = Object.values(
              state.audience.locationSelections,
            ).flat();
            const hasLocations = allLocationNames.length > 0;
            const hasRoles = state.audience.userRoles.length > 0;
            const hasPartnerTypes = state.audience.partnerTypes.length > 0;
            return hasLocations || hasRoles || hasPartnerTypes ? (
              <div className="pl-8 space-y-0.5 text-sm">
                {hasLocations && (
                  <FieldRow
                    label="Locations:"
                    value={allLocationNames.join(", ")}
                    onClick={() => handleSectionClick("audience")}
                  />
                )}
              {state.audience.userRoles.length > 0 && (
                <FieldRow
                  label="Roles:"
                  value={state.audience.userRoles
                    .map((id) => roleLabelById.get(id) ?? id)
                    .join(", ")}
                  onClick={() => handleSectionClick("audience")}
                />
              )}
                {state.audience.partnerTypes.length > 0 && (
                  <FieldRow
                    label="Partner Types:"
                    value={state.audience.partnerTypes.join(", ")}
                    onClick={() => handleSectionClick("audience")}
                  />
                )}
              </div>
            ) : (
              <EmptyState onClick={() => handleSectionClick("audience")} />
            );
          })()}
        </SummarySection>

        {/* Section 4: Budget */}
        <SummarySection
          index={4}
          title={SECTION_TITLES.budget}
          completed={state.completedSteps.includes("budget")}
          onClick={() => handleSectionClick("budget")}
          border
        >
          {state.budgetData.selectedCurrencies.length > 0 ? (
            <div className="pl-8 space-y-0.5 text-sm">
              <FieldRow
                label="Currencies:"
                value={`${state.budgetData.selectedCurrencies.length} selected`}
                onClick={() => handleSectionClick("budget")}
              />
              {Object.keys(state.budgetData.rewardAmounts).length > 0 && (
                <FieldRow
                  label="Rewards:"
                  value={`${Object.keys(state.budgetData.rewardAmounts).length} configured`}
                  onClick={() => handleSectionClick("budget")}
                />
              )}
              {state.basics.rewardMessage && (
                <FieldRow
                  label="Message:"
                  value={
                    state.basics.rewardMessage.length > 30
                      ? `${state.basics.rewardMessage.slice(0, 30)}...`
                      : state.basics.rewardMessage
                  }
                  onClick={() => handleSectionClick("budget")}
                />
              )}
            </div>
          ) : (
            <EmptyState onClick={() => handleSectionClick("budget")} />
          )}
        </SummarySection>

        {/* Section 5: Criteria (dynamic title) */}
        <SummarySection
          index={5}
          title={getSection5Title(incentiveType)}
          completed={state.completedSteps.includes("criteria")}
          onClick={() => handleSectionClick("criteria")}
          border
        >
          {state.completedSteps.includes("criteria") ? (
            <div className="pl-8 space-y-0.5 text-sm">
              <FieldRow
                label="Status:"
                value={getCriteriaStatus(state, incentiveType)}
                onClick={() => handleSectionClick("criteria")}
              />
            </div>
          ) : (
            <EmptyState onClick={() => handleSectionClick("criteria")} />
          )}
        </SummarySection>

        {/* Section 6: Approval Criteria */}
        <SummarySection
          index={6}
          title={SECTION_TITLES.approval}
          completed={state.completedSteps.includes("approval")}
          onClick={() => handleSectionClick("approval")}
          border
        >
          {state.completedSteps.includes("approval") ||
          state.approval.requiresApproval ||
          state.approval.approvers.length > 0 ? (
            <div className="pl-8 space-y-0.5 text-sm">
              <FieldRow
                label="Requires Approval:"
                value={state.approval.requiresApproval ? "Yes" : "No"}
                onClick={() => handleSectionClick("approval")}
              />
              {state.approval.requiresApproval &&
                state.approval.approvers.length > 0 && (
                  <>
                    <FieldRow
                      label="Approvers:"
                      value={`${state.approval.approvers.length}`}
                      onClick={() => handleSectionClick("approval")}
                    />
                    <FieldRow
                      label="Required:"
                      value={`${state.approval.requiredApprovals} of ${state.approval.approvers.length}`}
                      onClick={() => handleSectionClick("approval")}
                    />
                  </>
                )}
            </div>
          ) : (
            <EmptyState onClick={() => handleSectionClick("approval")} />
          )}
        </SummarySection>

        {/* Footer action button */}
        {allComplete && onComplete && (
          <div className="pt-4 border-t border-border">
            <Button
              className="w-full h-9 text-sm bg-primary hover:bg-primary/90"
              onClick={onComplete}
            >
              <CheckCircle className="h-3.5 w-3.5 mr-2" />
              Complete Setup
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

/** Compute criteria status text based on incentive type */
function getCriteriaStatus(
  state: ReturnType<typeof useBuilder>["state"],
  type: IncentiveType | null,
): string {
  const c = state.criteria;
  switch (type) {
    case "JOURNEY":
      return `${c.journeyStages.length} stage${c.journeyStages.length === 1 ? "" : "s"} · ${c.journeySequential ? "Sequential" : "Any order"}`;
    case "TRAINING":
      return `${c.trainingCourses.length} course${c.trainingCourses.length === 1 ? "" : "s"} (${c.trainingRequiredCount} req.)`;
    case "ACTIVITY":
      return `${c.activityDefinitions.length} ${c.activityDefinitions.length === 1 ? "activity" : "activities"}`;
    case "SALES":
      return "Configured";
    default:
      return "Configured";
  }
}

/** Section header with numbered badge, title, and chevron */
function SummarySection({
  index,
  title,
  completed,
  onClick,
  border,
  children,
}: {
  index: number;
  title: string;
  completed: boolean;
  onClick: () => void;
  border?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div
      className={`space-y-1.5 ${border ? "border-t border-border pt-4" : ""}`}
    >
      <button
        type="button"
        onClick={onClick}
        className="flex items-center gap-2 w-full text-left group hover:bg-primary/5 rounded-lg px-2 py-1.5 -mx-2 transition-[background-color]"
      >
        <div
          className={`flex h-5 w-5 items-center justify-center rounded-full text-xs font-semibold ${
            completed
              ? "bg-primary text-primary-foreground"
              : "bg-muted text-muted-foreground"
          }`}
        >
          {completed ? <Check className="h-2.5 w-2.5" /> : index}
        </div>
        <span className="font-medium text-sm text-foreground group-hover:text-primary transition-colors">
          {title}
        </span>
        <ChevronRight className="h-3 w-3 ml-auto text-muted-foreground group-hover:text-primary group-hover:translate-x-0.5 transition-[color,transform]" />
      </button>
      {children}
    </div>
  );
}

/** Clickable field row: label on left, value on right */
function FieldRow({
  label,
  value,
  truncate,
  onClick,
}: {
  label: string;
  value: string;
  truncate?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex justify-between w-full hover:bg-primary/5 rounded-md px-2 py-1.5 -mx-1 transition-[background-color] cursor-pointer group"
    >
      <span className="text-muted-foreground group-hover:text-foreground transition-colors">
        {label}
      </span>
      <span
        className={`font-medium text-foreground ${truncate ? "truncate max-w-[150px]" : ""}`}
      >
        {value}
      </span>
    </button>
  );
}

/** Empty state for sections without data */
function EmptyState({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="pl-8 text-xs text-muted-foreground italic hover:text-primary transition-colors text-left flex items-center gap-1 group"
    >
      <span>Not yet completed</span>
      <ChevronRight className="h-2.5 w-2.5 group-hover:translate-x-0.5 transition-transform" />
    </button>
  );
}
