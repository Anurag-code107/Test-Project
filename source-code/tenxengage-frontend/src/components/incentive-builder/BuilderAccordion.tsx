import { useRef, useEffect, useState } from "react";
import { useBuilder } from "@/contexts/BuilderContext";
import {
  BUILDER_STEPS,
  STEP_LABELS,
  STEP_SUBTITLES,
  STEP5_LABELS,
  STEP5_SUBTITLES,
  STEP5_DESCRIPTIONS,
} from "@/types/builder-state.types";
import type { BuilderStep, CriteriaData } from "@/types/builder-state.types";
import type { IncentiveType } from "@/types/incentive.types";
import {
  ChevronDown,
  ChevronUp,
  Check,
  Settings2,
  GraduationCap,
  FileCheck,
  Route,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { Step1Basics } from "@/components/incentive-builder/steps/Step1Basics";
import { Step2Schedule } from "@/components/incentive-builder/steps/Step2Schedule";
import { Step3Audience } from "@/components/incentive-builder/steps/Step3Audience";
import { Step4Budget } from "@/components/incentive-builder/steps/Step4Budget";
import { Step6Approval } from "@/components/incentive-builder/steps/Step6Approval";

const stepComponents: Record<
  Exclude<BuilderStep, "criteria">,
  React.ComponentType
> = {
  basics: Step1Basics,
  schedule: Step2Schedule,
  audience: Step3Audience,
  budget: Step4Budget,
  approval: Step6Approval,
};

/** Button labels — each step's "Continue to X" uses the NEXT step's short name */
const CONTINUE_LABELS: Record<BuilderStep, string> = {
  basics: "Timeline",
  schedule: "Eligibility",
  audience: "Budget",
  budget: "", // will be dynamic based on type
  criteria: "", // last visible step — no "Continue to" needed
  approval: "",
};

/** Type-specific CTA config for Step 5 */
const STEP5_CTA: Record<
  IncentiveType,
  {
    setupLabel: string;
    editLabel: string;
    icon: React.ComponentType<{ className?: string }>;
  }
> = {
  SALES: {
    setupLabel: "Set Up Incentive Criteria",
    editLabel: "Edit Incentive Criteria",
    icon: Settings2,
  },
  TRAINING: {
    setupLabel: "Select Training Courses",
    editLabel: "Edit Training Courses",
    icon: GraduationCap,
  },
  ACTIVITY: {
    setupLabel: "Set Up Activities",
    editLabel: "Edit Activities",
    icon: FileCheck,
  },
  JOURNEY: {
    setupLabel: "Set Up Journey Stages",
    editLabel: "Edit Journey Stages",
    icon: Route,
  },
};

interface BuilderAccordionProps {
  onComplete: () => void;
  onOpenCriteriaEditor: () => void;
}

/** Animated collapsible wrapper */
function AnimatedCollapse({
  open,
  children,
}: {
  open: boolean;
  children: React.ReactNode;
}) {
  const contentRef = useRef<HTMLDivElement>(null);
  const [height, setHeight] = useState<number | undefined>(
    open ? undefined : 0,
  );
  const [animating, setAnimating] = useState(false);

  useEffect(() => {
    if (!contentRef.current) return;
    if (open) {
      setAnimating(true);
      setHeight(contentRef.current.scrollHeight);
      const timer = setTimeout(() => {
        setHeight(undefined);
        setAnimating(false);
      }, 300);
      return () => clearTimeout(timer);
    } else {
      setAnimating(true);
      setHeight(contentRef.current.scrollHeight);
      requestAnimationFrame(() => {
        requestAnimationFrame(() => setHeight(0));
      });
      const timer = setTimeout(() => setAnimating(false), 300);
      return () => clearTimeout(timer);
    }
  }, [open]);

  return (
    <div
      className="transition-[height,opacity] duration-300 ease-in-out"
      style={{
        height: height !== undefined ? `${height}px` : "auto",
        opacity: open ? 1 : 0,
        overflow: animating || !open ? "hidden" : "visible",
      }}
    >
      <div ref={contentRef}>{children}</div>
    </div>
  );
}

export function BuilderAccordion({
  onComplete,
  onOpenCriteriaEditor,
}: BuilderAccordionProps) {
  const { state, dispatch } = useBuilder();
  const incentiveType = state.basics.incentiveType;
  const stepRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const prevActiveStep = useRef(state.activeStep);

  const prevCriteriaComplete = useRef(
    state.completedSteps.includes("criteria"),
  );
  useEffect(() => {
    const nowComplete = state.completedSteps.includes("criteria");
    if (nowComplete && !prevCriteriaComplete.current) {
      dispatch({ type: "SET_ACTIVE_STEP", payload: "approval" });
    }
    prevCriteriaComplete.current = nowComplete;
  }, [state.completedSteps, dispatch]);

  // Auto-scroll to the newly active step when it changes
  useEffect(() => {
    if (state.activeStep !== prevActiveStep.current) {
      prevActiveStep.current = state.activeStep;
      const el = stepRefs.current[state.activeStep];
      if (el) {
        setTimeout(() => {
          el.scrollIntoView({ behavior: "smooth", block: "nearest" });
        }, 50);
      }
    }
  }, [state.activeStep]);

  function getStepLabel(step: BuilderStep): string {
    if (step === "criteria" && incentiveType) {
      return STEP5_LABELS[incentiveType];
    }
    return STEP_LABELS[step];
  }

  function getStepSubtitle(step: BuilderStep): string {
    if (step === "criteria" && incentiveType) {
      return STEP5_SUBTITLES[incentiveType];
    }
    return STEP_SUBTITLES[step];
  }

  function getContinueLabel(step: BuilderStep): string {
    if (step === "budget" && incentiveType) {
      return STEP5_LABELS[incentiveType];
    }
    return CONTINUE_LABELS[step];
  }

  function toggleStep(step: BuilderStep) {
    dispatch({ type: "TOGGLE_STEP", payload: step });
  }

  function goToNextStep(currentIndex: number) {
    const next = BUILDER_STEPS[currentIndex + 1];
    if (next) {
      dispatch({ type: "SET_ACTIVE_STEP", payload: next });
    }
  }

  return (
    <div className="space-y-2">
      <div className="space-y-2">
        {BUILDER_STEPS.map((step, index) => {
          const isExpanded = state.expandedSteps.includes(step);
          const isCompleted = state.completedSteps.includes(step);
          const isCriteriaStep = step === "criteria";
          const nextStep =
            index < BUILDER_STEPS.length - 1 ? BUILDER_STEPS[index + 1] : null;

          return (
            <div
              key={step}
              ref={(el) => {
                stepRefs.current[step] = el;
              }}
              className={cn(
                "rounded-xl border transition-[border-color,background-color,box-shadow] duration-200",
                isExpanded
                  ? "border-primary/25 bg-primary/5 shadow-[0_2px_8px_hsl(var(--foreground)/0.04)]"
                  : isCompleted
                    ? "border-border bg-muted/30"
                    : "border-border bg-background",
              )}
            >
              {/* Step Header */}
              <button
                type="button"
                onClick={() => toggleStep(step)}
                className={cn(
                  "flex items-center justify-between w-full px-4 py-3.5 text-left",
                  "transition-colors duration-150 rounded-xl",
                  !isExpanded && "hover:bg-muted/50",
                  isExpanded && "rounded-b-none",
                )}
              >
                <div className="flex items-center gap-3">
                  <div
                    className={cn(
                      "flex items-center justify-center w-7 h-7 rounded-full text-xs font-semibold transition-colors duration-200",
                      isCompleted
                        ? "bg-primary text-primary-foreground"
                        : isExpanded
                          ? "bg-primary/10 text-primary ring-1 ring-primary/30"
                          : "bg-muted text-muted-foreground",
                    )}
                  >
                    {isCompleted ? (
                      <Check className="h-3.5 w-3.5" />
                    ) : (
                      index + 1
                    )}
                  </div>
                  <div>
                    <span className="font-semibold text-sm text-foreground">
                      {getStepLabel(step)}
                    </span>
                    <p className="text-xs text-muted-foreground">
                      {getStepSubtitle(step)}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {isExpanded ? (
                    <ChevronUp className="h-3.5 w-3.5 text-muted-foreground" />
                  ) : (
                    <ChevronDown className="h-3.5 w-3.5 text-muted-foreground" />
                  )}
                </div>
              </button>

              {/* Step Content — animated */}
              <AnimatedCollapse open={isExpanded}>
                <div
                  className={cn(
                    "px-4 pb-4 relative",
                    state.aiLocked && "pointer-events-none",
                  )}
                >
                  {/* AI lock overlay — blocks user interaction while Copilot is streaming */}
                  {state.aiLocked && (
                    <div className="absolute inset-0 bg-background/50 z-10 rounded-b-xl" />
                  )}
                  {isCriteriaStep ? (
                    <CriteriaStepContent
                      incentiveType={incentiveType}
                      isCompleted={isCompleted}
                      criteria={state.criteria}
                      onOpen={onOpenCriteriaEditor}
                    />
                  ) : (
                    <>
                      <div className="pt-1">
                        {(() => {
                          const StepComponent =
                            stepComponents[
                              step as Exclude<BuilderStep, "criteria">
                            ];
                          return <StepComponent />;
                        })()}
                      </div>
                      {nextStep && (
                        <Button
                          className="w-full mt-4 h-9 text-sm bg-primary hover:bg-primary/90"
                          onClick={() => goToNextStep(index)}
                          disabled={!isCompleted}
                        >
                          Continue to {getContinueLabel(step)}
                        </Button>
                      )}
                    </>
                  )}
                </div>
              </AnimatedCollapse>
            </div>
          );
        })}
      </div>

      {/* Complete Setup button below all steps */}
      <Button
        className="w-full mt-2 h-10 text-sm font-semibold bg-primary hover:bg-primary/90"
        size="lg"
        onClick={onComplete}
        disabled={state.completedSteps.length < BUILDER_STEPS.length}
      >
        Complete Setup
      </Button>
    </div>
  );
}

/** Step 5 content — shows CTA button to open the criteria editor panel */
function CriteriaStepContent({
  incentiveType,
  isCompleted,
  criteria,
  onOpen,
}: {
  incentiveType: IncentiveType | null;
  isCompleted: boolean;
  criteria: CriteriaData;
  onOpen: () => void;
}) {
  if (!incentiveType) {
    return (
      <div className="pt-2">
        <p className="text-sm text-muted-foreground">
          Select an incentive type in Step 1 to configure criteria.
        </p>
      </div>
    );
  }

  const cta = STEP5_CTA[incentiveType];
  const Icon = cta.icon;

  return (
    <div className="pt-1 space-y-3">
      <p className="text-sm text-muted-foreground leading-relaxed">
        {STEP5_DESCRIPTIONS[incentiveType]}
      </p>

      {isCompleted &&
        incentiveType === "TRAINING" &&
        criteria.trainingCourses.length > 0 && (
          <div className="text-xs text-muted-foreground bg-muted/30 rounded-lg px-3 py-2">
            <span className="font-medium text-foreground">
              {criteria.trainingCourses.length}
            </span>{" "}
            courses assigned ·{" "}
            <span className="font-medium text-foreground">
              {criteria.trainingRequiredCount}
            </span>{" "}
            required to complete
          </div>
        )}

      {isCompleted &&
        incentiveType === "ACTIVITY" &&
        criteria.activityDefinitions.length > 0 && (
          <div className="text-xs text-muted-foreground bg-muted/30 rounded-lg px-3 py-2">
            <span className="font-medium text-foreground">
              {criteria.activityDefinitions.length}
            </span>{" "}
            {criteria.activityDefinitions.length === 1
              ? "activity"
              : "activities"}{" "}
            defined
          </div>
        )}

      {isCompleted &&
        incentiveType === "JOURNEY" &&
        criteria.journeyStages.length > 0 && (
          <div className="text-xs text-muted-foreground bg-muted/30 rounded-lg px-3 py-2">
            <span className="font-medium text-foreground">
              {criteria.journeyStages.length}
            </span>{" "}
            stages ·{" "}
            {criteria.journeySequential ? "Sequential order" : "Any order"}
          </div>
        )}

      <Button
        className="w-full gap-2 h-9 text-sm bg-primary hover:bg-primary/90"
        size="lg"
        onClick={onOpen}
      >
        <Icon className="h-3.5 w-3.5" />
        {isCompleted ? cta.editLabel : cta.setupLabel}
      </Button>
    </div>
  );
}
