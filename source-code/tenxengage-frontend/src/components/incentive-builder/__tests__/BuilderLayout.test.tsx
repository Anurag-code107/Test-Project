import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, act } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { BuilderLayout } from "@/components/incentive-builder/BuilderLayout";
import { BuilderProvider } from "@/contexts/BuilderContext";
import { NavigationGuardProvider } from "@/contexts/NavigationGuardContext";
import {
  initialBuilderState,
  type BuilderAction,
  type BuilderState,
} from "@/types/builder-state.types";

vi.mock("@/hooks/useFeatures", () => ({
  useFeatures: vi.fn(),
}));

vi.mock("@/hooks/useCreateFromBuilder", () => ({
  useCreateFromBuilder: vi.fn(),
}));

vi.mock("@/components/PageBanner", () => ({
  PageBanner: ({ actions }: { actions?: React.ReactNode }) => (
    <div data-testid="page-banner">{actions}</div>
  ),
}));

vi.mock("@/components/incentive-builder/BuilderAccordion", () => ({
  BuilderAccordion: () => <div>BuilderAccordion</div>,
}));

vi.mock("@/components/incentive-builder/CriteriaEditorPanel", () => ({
  CriteriaEditorPanel: () => <div>CriteriaEditorPanel</div>,
}));

vi.mock("@/components/incentive-builder/forecasting/ForecastingPanel", () => ({
  ForecastingPanel: () => <div>ForecastingPanel</div>,
}));

vi.mock("@/components/incentive-builder/ai/AICopilotPanel", () => ({
  AICopilotPanel: () => <div>AICopilotPanel</div>,
}));

// ManualSummaryPanel exposes onComplete so tests can fire the Complete Setup
// reroute path without needing the panel's full state to be populated.
vi.mock("@/components/incentive-builder/ManualSummaryPanel", () => ({
  ManualSummaryPanel: ({ onComplete }: { onComplete?: () => void }) => (
    <div>
      ManualSummaryPanel
      <button type="button" onClick={onComplete}>
        Complete Setup
      </button>
    </div>
  ),
}));

import { useFeatures } from "@/hooks/useFeatures";
import { useCreateFromBuilder } from "@/hooks/useCreateFromBuilder";

const mockUseFeatures = vi.mocked(useFeatures);
const mockUseCreateFromBuilder = vi.mocked(useCreateFromBuilder);

function mockFeatures(features: string[]) {
  const set = new Set(features);
  mockUseFeatures.mockReturnValue({
    has: (key: string) => set.has(key),
    hasAny: (...keys: string[]) => keys.some((k) => set.has(k)),
    hasAll: (...keys: string[]) => keys.every((k) => set.has(k)),
    features: set,
  });
}

function renderWithProviders(stateOverrides: Partial<BuilderState> = {}) {
  const dispatch = vi.fn();
  const state: BuilderState = { ...initialBuilderState, ...stateOverrides };

  const result = render(
    <MemoryRouter>
      <NavigationGuardProvider>
        <BuilderProvider
          state={state}
          dispatch={dispatch as React.Dispatch<BuilderAction>}
        >
          <BuilderLayout
            onBack={vi.fn()}
            onComplete={vi.fn()}
            navigateTo="/manage-incentives"
          />
        </BuilderProvider>
      </NavigationGuardProvider>
    </MemoryRouter>,
  );

  return { ...result, dispatch };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseCreateFromBuilder.mockReturnValue({
    execute: vi.fn().mockResolvedValue(undefined),
    isCreating: false,
  });
});

describe("BuilderLayout ai_copilot gating", () => {
  it("AI Mode button is disabled when ai_copilot is off", () => {
    mockFeatures(["ai_forecasting"]);

    renderWithProviders({ mode: "manual" });

    const aiButton = screen.getByRole("button", { name: /AI Mode/i });
    expect(aiButton).toBeDefined();
    expect((aiButton as HTMLButtonElement).disabled).toBe(true);
  });

  it("AI Mode button is enabled when ai_copilot is on", () => {
    mockFeatures(["ai_copilot", "ai_forecasting"]);

    renderWithProviders({ mode: "manual" });

    const aiButton = screen.getByRole("button", { name: /AI Mode/i });
    expect((aiButton as HTMLButtonElement).disabled).toBe(false);
  });

  it("forces mode to manual when ai_copilot is off and current mode is 'ai'", () => {
    mockFeatures([]);

    const { dispatch } = renderWithProviders({ mode: "ai" });

    expect(dispatch).toHaveBeenCalledWith({
      type: "SET_MODE",
      payload: "manual",
    });
  });

  it("does NOT force mode when ai_copilot is on", () => {
    mockFeatures(["ai_copilot"]);

    const { dispatch } = renderWithProviders({ mode: "ai" });

    const setModeCalls = dispatch.mock.calls.filter(
      (c) => c[0]?.type === "SET_MODE",
    );
    expect(setModeCalls).toHaveLength(0);
  });
});

describe("BuilderLayout ai_forecasting gating", () => {
  it("Complete Setup dispatches SHOW_FORECASTING when ai_forecasting is on", () => {
    mockFeatures(["ai_copilot", "ai_forecasting"]);

    const { dispatch } = renderWithProviders({ mode: "manual" });

    fireEvent.click(screen.getByText("Complete Setup"));

    expect(dispatch).toHaveBeenCalledWith({ type: "SHOW_FORECASTING" });
  });

  it("Complete Setup reroutes to REQUEST_CREATE_CONFIRMATION when ai_forecasting is off", () => {
    // The wizard should skip the AI Forecasting panel entirely and open the
    // create-incentive confirmation dialog instead (Robert Snow direction,
    // BUG-035 reopen).
    mockFeatures(["ai_copilot"]);

    const { dispatch } = renderWithProviders({ mode: "manual" });

    fireEvent.click(screen.getByText("Complete Setup"));

    expect(dispatch).toHaveBeenCalledWith({
      type: "REQUEST_CREATE_CONFIRMATION",
    });
    expect(dispatch).not.toHaveBeenCalledWith({ type: "SHOW_FORECASTING" });
  });

  it("renders the create-incentive confirmation dialog when pendingCreate + manual + ai_forecasting off", () => {
    mockFeatures(["ai_copilot"]);

    renderWithProviders({
      mode: "manual",
      pendingCreate: true,
      basics: { ...initialBuilderState.basics, name: "Q3 Sprint Bonus" },
    });

    // Dialog title + name + Confirm button
    expect(screen.getByText(/Q3 Sprint Bonus/)).toBeDefined();
    expect(screen.getByText("Confirm & Create")).toBeDefined();
  });

  it("does NOT render the manual confirmation dialog in AI mode (AI flow has its own card)", () => {
    mockFeatures(["ai_copilot"]);

    renderWithProviders({
      mode: "ai",
      pendingCreate: true,
      basics: { ...initialBuilderState.basics, name: "Q3 Sprint Bonus" },
    });

    // The manual dialog should NOT appear when state.mode === "ai" — even
    // when pendingCreate is true, since the AICopilot panel renders its own
    // CreateConfirmationCard for that flow.
    expect(screen.queryByText("Confirm & Create")).toBeNull();
  });

  it("clicking Confirm & Create invokes useCreateFromBuilder.execute", async () => {
    const execute = vi.fn().mockResolvedValue(undefined);
    mockUseCreateFromBuilder.mockReturnValue({ execute, isCreating: false });
    mockFeatures(["ai_copilot"]);

    renderWithProviders({
      mode: "manual",
      pendingCreate: true,
      basics: { ...initialBuilderState.basics, name: "Q3 Sprint Bonus" },
    });

    await act(async () => {
      fireEvent.click(screen.getByText("Confirm & Create"));
    });

    expect(execute).toHaveBeenCalled();
  });
});
