import type { BuilderState, BuilderStep } from "@/types/builder-state.types";

// --- Request ---

export interface AiChatMessageEntry {
  role: "user" | "assistant";
  content: string;
}

export interface AiChatRequest {
  conversationHistory: AiChatMessageEntry[];
  currentState: BuilderStateSnapshot;
  incentiveType: string | null;
}

/** Per-step audit of which required fields are filled vs missing */
export interface StepFieldAudit {
  filled: string[];
  missing: string[];
  actuallyComplete: boolean;
}

/** Subset of BuilderState sent to the backend for context */
export interface BuilderStateSnapshot {
  basics: BuilderState["basics"];
  schedule: BuilderState["schedule"];
  audience: BuilderState["audience"];
  budgetData: BuilderState["budgetData"];
  criteria: BuilderState["criteria"];
  approval: BuilderState["approval"];
  completedSteps: BuilderStep[];
  activeStep: BuilderStep;
  /** Tells the AI which currencies are MONETARY (require budget) vs NON_MONETARY */
  currencyMetadata: Array<{
    id: string;
    label: string;
    type: "MONETARY" | "NON_MONETARY";
  }>;
  /**
   * Ground-truth audit of each step's required fields, computed from actual form values.
   * Use this to verify whether a step is truly complete — it may disagree with completedSteps.
   */
  stepFieldStatus: Record<BuilderStep, StepFieldAudit>;
}

// --- SSE Events ---

export interface TextDeltaEvent {
  text: string;
}

export interface ActionEvent {
  type: string;
  payload: Record<string, unknown>;
}

export interface SuggestionsEvent {
  suggestions: string[];
}

export interface AiChatCallbacks {
  onTextDelta: (data: TextDeltaEvent) => void;
  onAction: (data: ActionEvent) => void;
  onSuggestions: (data: SuggestionsEvent) => void;
  onDone: () => void;
  onError: (message: string) => void;
}
