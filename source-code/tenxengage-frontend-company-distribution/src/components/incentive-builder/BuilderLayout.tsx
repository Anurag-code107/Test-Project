import { useState, useEffect, useCallback, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useBuilder } from "@/contexts/BuilderContext";
import { useNavigationGuard } from "@/contexts/NavigationGuardContext";
import { useFeatures } from "@/hooks/useFeatures";
import { useCreateFromBuilder } from "@/hooks/useCreateFromBuilder";
import {
  INCENTIVE_TYPE_LABELS,
} from "@/types/incentive.types";
import {
  BUILDER_STEPS,
  STEP_SHORT_LABELS,
  STEP5_SHORT_LABELS,
} from "@/types/builder-state.types";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { Bot, ClipboardList, Loader2, Rocket, Settings2 } from "lucide-react";
import { PageBanner } from "@/components/PageBanner";
import { BuilderAccordion } from "@/components/incentive-builder/BuilderAccordion";
import { ManualSummaryPanel } from "@/components/incentive-builder/ManualSummaryPanel";
import { AICopilotPanel } from "@/components/incentive-builder/ai/AICopilotPanel";
import { CriteriaEditorPanel } from "@/components/incentive-builder/CriteriaEditorPanel";
import { ForecastingPanel } from "@/components/incentive-builder/forecasting/ForecastingPanel";
import { FlipTransition } from "@/components/FlipTransition";
import { cn } from "@/lib/utils";

interface BuilderLayoutProps {
  onBack: () => void;
  onComplete: () => void;
  navigateTo?: string;
}

export function BuilderLayout({
  onBack,
  onComplete,
  navigateTo,
}: BuilderLayoutProps) {
  const { state, dispatch } = useBuilder();
  const navigate = useNavigate();
  const { setGuard, clearGuard, pendingPath, clearPendingPath } =
    useNavigationGuard();
  const { has } = useFeatures();
  const aiCopilotEnabled = has("ai_copilot");
  const aiForecastingEnabled = has("ai_forecasting");
  const { execute: executeCreate } = useCreateFromBuilder({
    navigateTo,
    onSuccess: onComplete,
  });
  const [showDiscardDialog, setShowDiscardDialog] = useState(false);
  const showCriteriaEditor = state.showCriteriaEditor;
  const showForecasting = state.showForecasting;
  const popstateTriggeredRef = useRef(false);

  // Force Manual mode when the tenant's plan does not include ai_copilot.
  // The AI Mode toggle is also disabled below; this guards against any state
  // that pre-existed the toggle (edit mode, restored draft, hot reload).
  useEffect(() => {
    if (!aiCopilotEnabled && state.mode === "ai") {
      dispatch({ type: "SET_MODE", payload: "manual" });
    }
  }, [aiCopilotEnabled, state.mode, dispatch]);

  // Defensive: isDirty alone can be true from initialization dispatches (e.g. setting
  // incentiveType). Only treat it as real user progress if the user has actually filled
  // a form field or completed a step beyond the initial type selection.
  const hasActualEdits =
    state.basics.name !== "" ||
    state.basics.description !== "" ||
    state.schedule.startDate !== "" ||
    state.schedule.endDate !== "" ||
    state.schedule.fiscalYears.length > 0 ||
    state.schedule.fiscalQuarters.length > 0 ||
    Object.values(state.audience.locationSelections).some(
      (vs) => vs.length > 0,
    ) ||
    state.budgetData.selectedCurrencies.length > 0 ||
    state.criteria.salesRequirements.length > 0 ||
    state.criteria.trainingCourses.length > 0 ||
    state.criteria.activityDefinitions.length > 0 ||
    state.criteria.journeyStages.length > 0 ||
    state.approval.approvers.length > 0 ||
    state.completedSteps.length > 0;
  const hasProgress = state.isDirty && hasActualEdits;

  // Register/clear the navigation guard based on progress
  useEffect(() => {
    if (hasProgress) {
      setGuard(() => false);
    } else {
      clearGuard();
    }
    return () => clearGuard();
  }, [hasProgress, setGuard, clearGuard]);

  // Show discard dialog when a sidebar link sets pendingPath
  useEffect(() => {
    if (pendingPath) {
      popstateTriggeredRef.current = false;
      setShowDiscardDialog(true);
    }
  }, [pendingPath]);

  // Handle browser back/forward via popstate
  useEffect(() => {
    if (!hasProgress) return;

    window.history.pushState(null, "", window.location.href);

    const handlePopState = () => {
      popstateTriggeredRef.current = true;
      setShowDiscardDialog(true);
      window.history.pushState(null, "", window.location.href);
    };

    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, [hasProgress]);

  // Warn on browser reload / tab close
  useEffect(() => {
    if (!hasProgress) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [hasProgress]);

  function handleBackClick() {
    if (hasProgress) {
      popstateTriggeredRef.current = false;
      setShowDiscardDialog(true);
    } else {
      onBack();
    }
  }

  const handleDiscardCancel = useCallback(() => {
    setShowDiscardDialog(false);
    clearPendingPath();
    popstateTriggeredRef.current = false;
  }, [clearPendingPath]);

  const handleDiscard = useCallback(() => {
    dispatch({ type: "RESET" });
    clearGuard();
    setShowDiscardDialog(false);

    if (pendingPath) {
      const path = pendingPath;
      clearPendingPath();
      navigate(path);
    } else if (popstateTriggeredRef.current) {
      popstateTriggeredRef.current = false;
      window.history.back();
    } else {
      onBack();
    }
  }, [dispatch, clearGuard, pendingPath, clearPendingPath, navigate, onBack]);

  const typeName = state.basics.incentiveType
    ? INCENTIVE_TYPE_LABELS[state.basics.incentiveType]
    : "Incentive";

  const currentStepIndex = BUILDER_STEPS.indexOf(state.activeStep);
  const completedCount = state.completedSteps.length;
  const completionPercent = Math.round(
    (completedCount / BUILDER_STEPS.length) * 100,
  );

  function getShortLabel(step: (typeof BUILDER_STEPS)[number]): string {
    if (step === "criteria" && state.basics.incentiveType) {
      return STEP5_SHORT_LABELS[state.basics.incentiveType];
    }
    return STEP_SHORT_LABELS[step];
  }

  function handleShowForecasting() {
    // When ai_forecasting is gated off for the tenant, skip the forecasting
    // panel entirely and open the create-incentive confirmation dialog
    // instead — there's no AI insight to review, so the wizard's terminal
    // step is the create confirmation itself.
    if (!aiForecastingEnabled) {
      dispatch({ type: "REQUEST_CREATE_CONFIRMATION" });
      return;
    }
    dispatch({ type: "SHOW_FORECASTING" });
  }

  const flipKey = showForecasting
    ? "forecasting"
    : showCriteriaEditor
      ? "criteria-editor"
      : "setup";

  return (
    <div className="flex flex-col gap-5 h-full p-8">
      {/* Banner with back arrow + mode toggle */}
      <PageBanner
        title="Incentive Builder"
        subtitle={
          state.mode === "ai"
            ? "AI-powered incentive copilot — design, configure, and launch"
            : "Manual incentive setup — configure and launch step by step"
        }
        theme={state.mode === "ai" ? "builder-ai" : "builder-manual"}
        onBack={handleBackClick}
        actions={
          <div className="flex items-center gap-1 p-1 rounded-xl border border-primary/20 bg-primary/5">
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  {/* span wrapper so the tooltip still fires when the
                      underlying button is disabled (Radix won't dispatch
                      pointer events through a `disabled` element). */}
                  <span>
                    <button
                      type="button"
                      disabled={!aiCopilotEnabled}
                      onClick={() => {
                        if (!aiCopilotEnabled) return;
                        dispatch({ type: "SET_MODE", payload: "ai" });
                      }}
                      className={cn(
                        "flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-[background-color,color,box-shadow]",
                        !aiCopilotEnabled
                          ? "text-muted-foreground/50 cursor-not-allowed"
                          : state.mode === "ai"
                            ? "bg-primary text-primary-foreground shadow-md shadow-primary/30"
                            : "text-muted-foreground hover:bg-primary/10 hover:text-foreground",
                      )}
                    >
                      <Bot className="h-4 w-4" />
                      AI Mode
                    </button>
                  </span>
                </TooltipTrigger>
                {!aiCopilotEnabled && (
                  <TooltipContent side="bottom" className="text-xs max-w-[240px]">
                    AI Mode isn&apos;t included in your subscription plan.
                    Contact your account admin to upgrade.
                  </TooltipContent>
                )}
              </Tooltip>
            </TooltipProvider>
            <button
              type="button"
              onClick={() => dispatch({ type: "SET_MODE", payload: "manual" })}
              className={cn(
                "flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-[background-color,color,box-shadow]",
                state.mode === "manual"
                  ? "bg-primary text-primary-foreground shadow-md shadow-primary/30"
                  : "text-muted-foreground hover:bg-primary/10 hover:text-foreground",
              )}
            >
              <ClipboardList className="h-4 w-4" />
              Manual
            </button>
          </div>
        }
      />

      {/* Body — two-column layout */}
      <div className="flex flex-1 overflow-hidden pb-4 gap-5">
        {/* Left Panel — AI Copilot or Manual Summary */}
        <div className="flex-[2] min-w-[340px]" data-tour="builder-ai-copilot">
          <div key={state.mode} className="h-full animate-mode-fade-in">
            {state.mode === "ai" ? (
              <AICopilotPanel onComplete={onComplete} navigateTo={navigateTo} />
            ) : (
              <ManualSummaryPanel onComplete={handleShowForecasting} />
            )}
          </div>
        </div>

        {/* Right Panel — flip transition between setup and criteria editor */}
        <div
          className="flex-[3] flex flex-col overflow-hidden min-w-[400px]"
          data-tour="builder-setup-panel"
        >
          <FlipTransition transitionKey={flipKey}>
            {showForecasting ? (
              <ForecastingPanel
                onEditSetup={() => dispatch({ type: "HIDE_FORECASTING" })}
                onCreateIncentive={onComplete}
                navigateTo={navigateTo}
              />
            ) : showCriteriaEditor ? (
              <CriteriaEditorPanel
                onBack={() => dispatch({ type: "HIDE_CRITERIA_EDITOR" })}
              />
            ) : (
              <div className="flex flex-col h-full">
                {/* Setup header */}
                <div className="rounded-xl border border-border bg-background p-4 mb-4 shrink-0">
                  <div className="flex items-center gap-2 mb-1">
                    <Settings2 className="h-5 w-5 text-muted-foreground" />
                    <h3 className="text-2xl font-semibold text-foreground mb-0.5">
                      New {typeName} Setup
                    </h3>
                  </div>
                  <p className="text-base text-muted-foreground mb-4">
                    Complete each section to build your incentive program
                  </p>

                  {/* Step progress */}
                  <div className="flex items-center justify-between text-xs mb-2">
                    {" "}
                    <span className="font-medium text-foreground">
                      Step {currentStepIndex + 1} of {BUILDER_STEPS.length}
                    </span>
                    <span className="text-muted-foreground tabular-nums">
                      {completionPercent}%
                    </span>
                  </div>

                  {/* Progress bar segments */}
                  <div className="flex gap-1.5 mb-2">
                    {BUILDER_STEPS.map((step) => (
                      <div
                        key={step}
                        className={cn(
                          "h-1.5 flex-1 rounded-full transition-[background-color]",
                          state.completedSteps.includes(step)
                            ? "bg-primary"
                            : state.expandedSteps.includes(step)
                              ? "bg-primary/35"
                              : "bg-border",
                        )}
                      />
                    ))}
                  </div>

                  {/* Segment labels */}
                  <div className="flex gap-1.5">
                    {BUILDER_STEPS.map((step) => (
                      <span
                        key={step}
                        className="flex-1 text-xs text-muted-foreground text-center"
                      >
                        {getShortLabel(step)}
                      </span>
                    ))}
                  </div>
                </div>

                {/* Scrollable accordion steps */}
                <div className="flex-1 overflow-y-auto">
                  <BuilderAccordion
                    onComplete={handleShowForecasting}
                    onOpenCriteriaEditor={() =>
                      dispatch({ type: "SHOW_CRITERIA_EDITOR" })
                    }
                  />
                </div>
              </div>
            )}
          </FlipTransition>
        </div>
      </div>

      {/* Discard confirmation dialog */}
      <Dialog
        open={showDiscardDialog}
        onOpenChange={(open) => {
          if (!open) handleDiscardCancel();
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-foreground">
              Discard current work?
            </DialogTitle>
            <DialogDescription className="text-sm text-muted-foreground">
              You have unsaved changes in your incentive setup. Going back will
              discard all your progress.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="flex gap-2 sm:justify-end">
            <Button
              variant="outline"
              onClick={handleDiscardCancel}
              className="border-border text-foreground"
            >
              Keep Working
            </Button>
            <Button
              variant="destructive"
              onClick={handleDiscard}
              className="bg-destructive hover:bg-destructive/90"
            >
              Discard &amp; Go Back
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Create-incentive confirmation dialog — manual mode reroute when
          ai_forecasting is gated off. AI mode has its own inline
          CreateConfirmationCard inside AICopilotPanel and is unaffected. */}
      <Dialog
        open={
          state.pendingCreate &&
          state.mode === "manual" &&
          !aiForecastingEnabled
        }
        onOpenChange={(open) => {
          if (!open && !state.isCreating) {
            dispatch({ type: "DISMISS_CREATE_CONFIRMATION" });
          }
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-foreground">
              <Rocket className="h-5 w-5 text-primary" />
              Ready to{" "}
              {state.editingIncentiveId ? "update" : "create"} this{" "}
              {(state.basics.incentiveType
                ? INCENTIVE_TYPE_LABELS[state.basics.incentiveType]
                : "incentive"
              ).toLowerCase()}
              ?
            </DialogTitle>
            <DialogDescription className="text-sm text-muted-foreground">
              {state.basics.name || "Untitled Incentive"} — confirm to{" "}
              {state.editingIncentiveId ? "save your changes" : "launch this incentive"}.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="flex gap-2 sm:justify-end">
            <Button
              variant="outline"
              disabled={state.isCreating}
              onClick={() => dispatch({ type: "DISMISS_CREATE_CONFIRMATION" })}
              className="border-border text-foreground"
            >
              Cancel
            </Button>
            <Button
              disabled={state.isCreating}
              onClick={() => executeCreate()}
              className="bg-primary hover:bg-primary/90"
            >
              {state.isCreating ? (
                <Loader2 className="h-4 w-4 mr-1.5 animate-spin" />
              ) : null}
              {state.isCreating
                ? "Creating..."
                : state.editingIncentiveId
                  ? "Confirm & Update"
                  : "Confirm & Create"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
