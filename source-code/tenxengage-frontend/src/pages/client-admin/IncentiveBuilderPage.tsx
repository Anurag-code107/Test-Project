import { useReducer, useCallback, useRef, useState, useEffect, useMemo } from "react";
import { useLocation } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { EntryMenu } from "@/components/incentive-builder/EntryMenu";
import { TypeSelector } from "@/components/incentive-builder/TypeSelector";
import { ExistingIncentiveSelector } from "@/components/incentive-builder/ExistingIncentiveSelector";
import { TemplateSelector } from "@/components/incentive-builder/TemplateSelector";
import { TemplateUploadPage } from "@/components/incentive-builder/TemplateUploadPage";
import { EnablementSelector } from "@/components/incentive-builder/EnablementSelector";
import { BuilderLayout } from "@/components/incentive-builder/BuilderLayout";
import { ForecastingPanel } from "@/components/incentive-builder/forecasting/ForecastingPanel";
import { FlowTransition } from "@/components/FlowTransition";
import { BuilderFlowBackground } from "@/components/incentive-builder/BuilderFlowBackground";
import { BuilderProvider } from "@/contexts/BuilderContext";
import type {
  IncentiveType,
  IncentiveResponse,
  IncentiveDetailResponse,
} from "@/types/incentive.types";
import { INCENTIVE_TYPE_LABELS } from "@/types/incentive.types";
import type { FlowState } from "@/types/builder-state.types";
import { initialBuilderState } from "@/types/builder-state.types";
import { builderReducer } from "@/hooks/useBuilderReducer";
import {
  useLocationBuilderOptions,
  useLocationHierarchy,
} from "@/hooks/useLocationApi";
import { useExternalRoles } from "@/hooks/useBuilderConfig";
import { toast } from "sonner";
import { getIncentiveById } from "@/services/incentive.service";
import { parseExcelTemplate } from "@/utils/excelTemplateParser";
import { STEP5_LABELS } from "@/types/builder-state.types";
import type { BuilderStep } from "@/types/builder-state.types";

/* Depth map — higher = deeper in the flow. Used to determine slide direction. */
const FLOW_DEPTH: Record<FlowState, number> = {
  entry_menu: 0,
  type_select: 1,
  enablement_select: 2,
  existing_select: 1,
  template_select: 1,
  template_upload: 2,
  builder: 3,
  forecasting: 4,
};

const STEP_FRIENDLY_NAMES: Record<BuilderStep, string> = {
  basics: "Basic Information",
  schedule: "Timeline",
  audience: "Eligibility",
  budget: "Budget",
  criteria: "Criteria",
  approval: "Approval",
};

function IncentiveBuilderPage() {
  const location = useLocation();
  const [state, dispatch] = useReducer(builderReducer, initialBuilderState);
  const [templateProcessing, setTemplateProcessing] = useState(false);
  const [isLoadingEdit, setIsLoadingEdit] = useState(false);
  const editLoadedRef = useRef(false);
  const [flowDirection, setFlowDirection] = useState<"forward" | "backward">(
    "forward",
  );
  const prevFlowRef = useRef<FlowState>("entry_menu");

  // Track flow direction whenever flowState changes
  useEffect(() => {
    const prev = prevFlowRef.current;
    const next = state.flowState;
    if (prev !== next) {
      setFlowDirection(
        FLOW_DEPTH[next] >= FLOW_DEPTH[prev] ? "forward" : "backward",
      );
      prevFlowRef.current = next;
    }
  }, [state.flowState]);

  // Track the flow state the user was on before entering the builder,
  // so "Discard & Go Back" returns them to the correct screen.
  const preBuilderFlowRef = useRef<FlowState>("type_select");

  // Top-level location level id — passed to the Excel template parser so flat
  // "Eligible Regions" columns can be tagged with the correct level when
  // emitting LOCATION rules.
  const { data: builderLevels = [] } = useLocationBuilderOptions();
  const topLocationLevelId = builderLevels[0]?.id;
  // BUG-079: hierarchy used for parse-time name → UUID resolution in the Excel
  // upload path. Surfaces unresolved names as a toast so the user can fix
  // typos / re-pick before save instead of finding out at the mapper backstop.
  const { data: locationHierarchy } = useLocationHierarchy();
  // BUG-082: external role list, threaded into the Excel parser so role-name
  // cells in "Eligible Roles" resolve to ClientRole.id UUIDs at parse time.
  // `roleOptions` is `{ value: id, label: name }[]` — repackage for the parser.
  const { data: roleOptions = [] } = useExternalRoles();
  const availableRoles = useMemo(
    () => roleOptions.map((r) => ({ id: r.value, name: r.label })),
    [roleOptions],
  );

  // --- Edit mode: load incentive from location state ---
  useEffect(() => {
    const editId = (location.state as { editId?: string } | null)?.editId;
    if (!editId || editLoadedRef.current) return;
    editLoadedRef.current = true;
    setIsLoadingEdit(true);

    (async () => {
      try {
        const detail = await getIncentiveById(editId);
        // Keep original ID so editingIncentiveId is set → edit mode
        dispatch({
          type: "LOAD_INCENTIVE",
          payload: { incentive: detail },
        });
        dispatch({ type: "SET_FLOW_STATE", payload: "builder" });

        const typeName = INCENTIVE_TYPE_LABELS[detail.incentiveType];
        dispatch({
          type: "ADD_CHAT_MESSAGE",
          payload: {
            id: `edit-${Date.now()}`,
            role: "assistant",
            content: `Editing "${detail.name}" (${typeName}). All fields have been loaded. Make your changes and click "Review & Launch" when ready.`,
            timestamp: new Date().toISOString(),
          },
        });
      } catch {
        // If fetch fails, just show the entry menu
      } finally {
        setIsLoadingEdit(false);
      }
    })();
  }, [location.state, topLocationLevelId]);

  // --- Direct-create mode: jump to builder for a specific type, or to enablement selector ---
  const directCreateRef = useRef(false);
  useEffect(() => {
    const locState = location.state as {
      createType?: IncentiveType;
      flow?: string;
    } | null;
    if (directCreateRef.current || !locState) return;
    if (locState.flow === "enablement") {
      directCreateRef.current = true;
      dispatch({ type: "SET_FLOW_STATE", payload: "enablement_select" });
    } else if (locState.createType) {
      directCreateRef.current = true;
      preBuilderFlowRef.current = "type_select";
      dispatch({
        type: "UPDATE_BASICS",
        payload: { incentiveType: locState.createType },
      });
      dispatch({ type: "SET_FLOW_STATE", payload: "builder" });
    }
  }, [location.state]);

  // --- Entry menu handlers ---

  const handleCreateFromScratch = useCallback(() => {
    dispatch({ type: "SET_FLOW_STATE", payload: "type_select" });
  }, []);

  const handleCreateFromExisting = useCallback(() => {
    dispatch({ type: "SET_FLOW_STATE", payload: "existing_select" });
  }, []);

  const handleCreateFromTemplate = useCallback(() => {
    dispatch({ type: "SET_FLOW_STATE", payload: "template_select" });
  }, []);

  // --- Type selector ---

  const handleTypeSelect = useCallback((type: IncentiveType) => {
    preBuilderFlowRef.current = "type_select";
    dispatch({ type: "UPDATE_BASICS", payload: { incentiveType: type } });
    dispatch({ type: "SET_FLOW_STATE", payload: "builder" });
  }, []);

  // --- Existing incentive selector ---

  const handleExistingSelect = useCallback(
    async (incentive: IncentiveResponse) => {
      preBuilderFlowRef.current = "existing_select";

      // Fetch full detail from API
      let detail: IncentiveDetailResponse;
      try {
        detail = await getIncentiveById(incentive.id);
      } catch {
        // Fall back to constructing from the summary response if API fails
        detail = { ...incentive, audienceRules: [], journeyStages: undefined };
      }

      // Clone: prefix name with "Copy of" and clear the id so it's treated as new
      const cloneData: IncentiveDetailResponse = {
        ...detail,
        id: "",
        name: `Copy of ${detail.name}`,
      };

      dispatch({
        type: "LOAD_INCENTIVE",
        payload: { incentive: cloneData },
      });
      dispatch({ type: "SET_ORIGIN", payload: "existing" });

      // Add a contextual AI chat message about what was cloned
      const typeName = INCENTIVE_TYPE_LABELS[detail.incentiveType];
      const filledSections: string[] = [];
      if (detail.name) filledSections.push("basic info");
      if (detail.startDate && detail.endDate) filledSections.push("schedule");
      if (detail.audienceRules?.length) filledSections.push("audience");
      if (detail.budget) filledSections.push("budget");
      const hasCriteria =
        (detail.salesRequirements?.length ?? 0) > 0 ||
        (detail.trainingCourses?.length ?? 0) > 0 ||
        (detail.activityDefinitions?.length ?? 0) > 0 ||
        (detail.journeyStages?.length ?? 0) > 0;
      if (hasCriteria) filledSections.push("criteria");

      dispatch({
        type: "ADD_CHAT_MESSAGE",
        payload: {
          id: `clone-${Date.now()}`,
          role: "assistant",
          content: `I've loaded "${detail.name}" as a ${typeName} template. All ${filledSections.length} sections (${filledSections.join(", ")}) have been pre-filled.\n\nYou can review and modify any section, or let me know what you'd like to change!`,
          timestamp: new Date().toISOString(),
        },
      });
    },
    [],
  );

  // --- Template flow ---

  const handleTemplateTypeSelect = useCallback((type: IncentiveType) => {
    dispatch({ type: "UPDATE_BASICS", payload: { incentiveType: type } });
    dispatch({ type: "SET_FLOW_STATE", payload: "template_upload" });
  }, []);

  const handleTemplateFileUpload = useCallback(
    async (file: File) => {
      preBuilderFlowRef.current = "template_upload";
      const fileName = file.name.toLowerCase();
      const isDocumentFile =
        fileName.endsWith(".pdf") || fileName.endsWith(".pptx");

      // PDF/PPTX → hand off to the AI copilot for extraction
      if (isDocumentFile) {
        setTemplateProcessing(true);
        dispatch({ type: "SET_ORIGIN", payload: "template" });
        dispatch({ type: "SET_PENDING_DOCUMENT", payload: file });

        // Brief processing animation, then transition to builder where
        // the AI copilot will auto-process the pending document
        setTimeout(() => {
          setTemplateProcessing(false);
          dispatch({ type: "SET_FLOW_STATE", payload: "builder" });
        }, 1500);
        return;
      }

      // Excel/CSV → parse client-side (existing flow)
      setTemplateProcessing(true);

      try {
        const parsed = await parseExcelTemplate(
          file,
          topLocationLevelId,
          locationHierarchy,
          availableRoles,
        );
        const incentiveType = state.basics.incentiveType;

        // BUG-079: surface any region names from the sheet that didn't resolve
        // against the loaded hierarchy. Names still flow into state so the
        // user can edit/re-pick in Step 3, but we tell them which rows are
        // unresolved so they aren't surprised when the mapper rejects them
        // at save time. Falls through silently when hierarchy isn't loaded
        // yet (rare race; mapper backstop still catches at save).
        const unresolved = parsed.locationRows.filter(
          (r) => r.locationValueId === null,
        );
        if (unresolved.length > 0) {
          toast.warning(
            `${unresolved.length} location${unresolved.length === 1 ? "" : "s"} from the upload couldn't be matched to your hierarchy: ${unresolved.map((r) => r.raw).join(", ")}. Edit them in Step 3 before saving.`,
          );
        }

        // BUG-082: same shape for unresolved role names. The parser drops
        // unresolved roles from `audience.userRoles` (so the wire never
        // carries hallucinated names), but the user needs to know which
        // cells were skipped so they can fix the spreadsheet or re-pick in
        // Step 3.
        const unresolvedRoles = parsed.roleRows.filter(
          (r) => r.roleId === null,
        );
        if (unresolvedRoles.length > 0) {
          toast.warning(
            `${unresolvedRoles.length} role${unresolvedRoles.length === 1 ? "" : "s"} from the upload couldn't be matched: ${unresolvedRoles.map((r) => r.raw).join(", ")}. Re-pick in Step 3 before saving.`,
          );
        }

        // Populate builder state from parsed template
        if (
          parsed.basics.name ||
          parsed.basics.description ||
          parsed.basics.rewardMessage
        ) {
          dispatch({ type: "UPDATE_BASICS", payload: parsed.basics });
        }
        if (parsed.schedule.startDate || parsed.schedule.endDate) {
          dispatch({ type: "UPDATE_SCHEDULE", payload: parsed.schedule });
        }
        if (parsed.audience.rules && parsed.audience.rules.length > 0) {
          dispatch({
            type: "UPDATE_AUDIENCE",
            payload: {
              ...parsed.audience,
              countriesText: "",
              specificPartners: "",
            },
          });
        }
        if (
          parsed.budgetData.selectedCurrencies &&
          parsed.budgetData.selectedCurrencies.length > 0
        ) {
          dispatch({
            type: "UPDATE_BUDGET",
            payload: {
              ...parsed.budgetData,
              budget: null,
              regionBudgets: {},
              rewardAmounts: parsed.budgetData.rewardAmounts ?? {},
              journeyHasOwnRewards: true,
            },
          });
        }
        if (
          parsed.criteria.salesRequirements &&
          parsed.criteria.salesRequirements.length > 0
        ) {
          dispatch({ type: "UPDATE_CRITERIA", payload: parsed.criteria });
        }
        if (parsed.approval.requiresApproval != null) {
          dispatch({ type: "UPDATE_APPROVAL", payload: parsed.approval });
        }

        // Mark completed steps
        for (const step of parsed.filledSteps) {
          dispatch({
            type: "MARK_STEP_COMPLETE",
            payload: step as BuilderStep,
          });
        }

        dispatch({ type: "SET_ORIGIN", payload: "template" });

        // Build AI copilot acknowledgment message
        const typeName = incentiveType
          ? INCENTIVE_TYPE_LABELS[incentiveType]
          : "Incentive";
        const filledCount = parsed.filledSteps.length;
        const stepNames = parsed.filledSteps.map((s) => {
          if (s === "criteria" && incentiveType)
            return STEP5_LABELS[incentiveType];
          return STEP_FRIENDLY_NAMES[s as BuilderStep] || s;
        });

        const checkmarks = stepNames.map((name) => `\u2713 ${name}`).join("\n");
        const copilotMessage = `I've imported your Excel template for a ${typeName} program. ${filledCount} of 6 sections were pre-populated from the file.\n\n${checkmarks}\n\nYou can review and edit any section, or ask me to make changes.`;

        dispatch({
          type: "ADD_CHAT_MESSAGE",
          payload: {
            id: `template-${Date.now()}`,
            role: "assistant",
            content: copilotMessage,
            timestamp: new Date().toISOString(),
          },
        });

        // Show processing animation for 3 seconds, then transition to builder
        setTimeout(() => {
          setTemplateProcessing(false);
          dispatch({ type: "SET_FLOW_STATE", payload: "builder" });
        }, 3000);
      } catch {
        setTemplateProcessing(false);
        // On failure, just go to builder with whatever was already set
        dispatch({ type: "SET_FLOW_STATE", payload: "builder" });
      }
    },
    [
      state.basics.incentiveType,
      topLocationLevelId,
      locationHierarchy,
      availableRoles,
    ],
  );

  // --- Navigation ---

  const handleBackToMenu = useCallback(() => {
    dispatch({ type: "RESET" });
  }, []);

  const handleCreateComplete = useCallback(() => {
    dispatch({ type: "RESET" });
  }, []);

  const handleEnablement = useCallback(() => {
    dispatch({ type: "SET_FLOW_STATE", payload: "enablement_select" });
  }, []);

  const handleEnablementTypeSelect = useCallback((type: IncentiveType) => {
    preBuilderFlowRef.current = "enablement_select";
    dispatch({ type: "UPDATE_BASICS", payload: { incentiveType: type } });
    dispatch({ type: "SET_FLOW_STATE", payload: "builder" });
  }, []);

  const handleBackToTypeSelect = useCallback(() => {
    dispatch({ type: "SET_FLOW_STATE", payload: "type_select" });
  }, []);

  const handleBackFromBuilder = useCallback(() => {
    dispatch({ type: "SET_FLOW_STATE", payload: preBuilderFlowRef.current });
  }, []);

  const handleBackToTemplateSelect = useCallback(() => {
    dispatch({ type: "SET_FLOW_STATE", payload: "template_select" });
  }, []);

  const handleBackToBuilder = useCallback(() => {
    dispatch({ type: "SET_FLOW_STATE", payload: "builder" });
  }, []);

  if (isLoadingEdit) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-3">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
        <p className="text-sm text-muted-foreground">Loading incentive...</p>
      </div>
    );
  }

  // Hide shared background when inside the full builder or forecasting (they have their own layouts)
  const showFlowBackground =
    state.flowState !== "builder" && state.flowState !== "forecasting";

  return (
    <BuilderProvider state={state} dispatch={dispatch}>
      <div className="relative h-full">
        {showFlowBackground && <BuilderFlowBackground />}
        <div className="relative z-10 h-full">
          <FlowTransition
            transitionKey={state.flowState}
            direction={flowDirection}
          >
            {renderFlowState(state.flowState)}
          </FlowTransition>
        </div>
      </div>
    </BuilderProvider>
  );

  function renderFlowState(flowState: FlowState) {
    switch (flowState) {
      case "entry_menu":
        return (
          <EntryMenu
            onCreateFromScratch={handleCreateFromScratch}
            onCreateFromExisting={handleCreateFromExisting}
            onCreateFromTemplate={handleCreateFromTemplate}
          />
        );
      case "type_select":
        return (
          <TypeSelector
            onSelect={handleTypeSelect}
            onEnablement={handleEnablement}
            onBack={handleBackToMenu}
          />
        );
      case "enablement_select":
        return (
          <EnablementSelector
            onSelect={handleEnablementTypeSelect}
            onBack={handleBackToTypeSelect}
          />
        );
      case "existing_select":
        return (
          <ExistingIncentiveSelector
            onSelect={handleExistingSelect}
            onBack={handleBackToMenu}
          />
        );
      case "template_select":
        return (
          <TemplateSelector
            onSelectType={handleTemplateTypeSelect}
            onBack={handleBackToMenu}
          />
        );
      case "template_upload":
        return (
          <TemplateUploadPage
            type={state.basics.incentiveType!}
            onUpload={handleTemplateFileUpload}
            onBack={handleBackToTemplateSelect}
            isProcessing={templateProcessing}
          />
        );
      case "builder":
        return (
          <BuilderLayout
            onBack={handleBackFromBuilder}
            onComplete={handleBackToMenu}
            navigateTo="/manage-incentives"
          />
        );
      case "forecasting":
        return (
          <ForecastingPanel
            onEditSetup={handleBackToBuilder}
            onCreateIncentive={handleCreateComplete}
            navigateTo="/manage-incentives"
          />
        );
    }
  }
}

export default IncentiveBuilderPage;
