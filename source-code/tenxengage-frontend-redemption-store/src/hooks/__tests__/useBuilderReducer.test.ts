import { describe, it, expect } from "vitest";
import {
  initialBuilderState,
  BUILDER_STEPS,
} from "@/types/builder-state.types";
import type { BuilderState } from "@/types/builder-state.types";
import { builderReducer } from "@/hooks/useBuilderReducer";

describe("Builder State Types", () => {
  it("BUILDER_STEPS includes approval", () => {
    expect(BUILDER_STEPS).toContain("approval");
  });

  it("initialBuilderState has rewardMessage in basics", () => {
    expect(initialBuilderState.basics.rewardMessage).toBe("");
  });

  it("initialBuilderState has dynamicFields in audience", () => {
    expect(initialBuilderState.audience.dynamicFields).toEqual({});
  });

  it("initialBuilderState has maxPerPartnerByCurrency in budgetData", () => {
    expect(initialBuilderState.budgetData.maxPerPartnerByCurrency).toEqual({});
  });

  it("initialBuilderState has maxPerUserByCurrency in budgetData", () => {
    expect(initialBuilderState.budgetData.maxPerUserByCurrency).toEqual({});
  });

  it("REGION_COUNTRIES is no longer exported", async () => {
    const exports = await import("@/types/builder-state.types");
    expect("REGION_COUNTRIES" in exports).toBe(false);
  });
});

describe("builderReducer", () => {
  it("SET_MODE changes mode", () => {
    const state = builderReducer(initialBuilderState, {
      type: "SET_MODE",
      payload: "manual",
    });
    expect(state.mode).toBe("manual");
  });

  it("SET_ACTIVE_STEP changes activeStep and expandedSteps", () => {
    const state = builderReducer(initialBuilderState, {
      type: "SET_ACTIVE_STEP",
      payload: "budget",
    });
    expect(state.activeStep).toBe("budget");
    expect(state.expandedSteps).toEqual(["budget"]);
  });

  it("MARK_STEP_COMPLETE adds step to completedSteps", () => {
    const state = builderReducer(initialBuilderState, {
      type: "MARK_STEP_COMPLETE",
      payload: "basics",
    });
    expect(state.completedSteps).toContain("basics");
  });

  it("MARK_STEP_COMPLETE is idempotent", () => {
    const first = builderReducer(initialBuilderState, {
      type: "MARK_STEP_COMPLETE",
      payload: "basics",
    });
    const second = builderReducer(first, {
      type: "MARK_STEP_COMPLETE",
      payload: "basics",
    });
    expect(second.completedSteps.filter((s) => s === "basics")).toHaveLength(1);
  });

  it("MARK_STEP_INCOMPLETE removes step from completedSteps", () => {
    const completed = builderReducer(initialBuilderState, {
      type: "MARK_STEP_COMPLETE",
      payload: "basics",
    });
    const state = builderReducer(completed, {
      type: "MARK_STEP_INCOMPLETE",
      payload: "basics",
    });
    expect(state.completedSteps).not.toContain("basics");
  });

  it("UPDATE_BASICS merges partial data and sets isDirty", () => {
    const state = builderReducer(initialBuilderState, {
      type: "UPDATE_BASICS",
      payload: { name: "Test Incentive" },
    });
    expect(state.basics.name).toBe("Test Incentive");
    expect(state.basics.description).toBe("");
    expect(state.isDirty).toBe(true);
  });

  it("UPDATE_BASICS with only incentiveType does not set isDirty", () => {
    const state = builderReducer(initialBuilderState, {
      type: "UPDATE_BASICS",
      payload: { incentiveType: "SALES" },
    });
    expect(state.basics.incentiveType).toBe("SALES");
    expect(state.isDirty).toBe(false);
  });

  it("UPDATE_AUDIENCE sets isDirty", () => {
    const state = builderReducer(initialBuilderState, {
      type: "UPDATE_AUDIENCE",
      payload: { locationSelections: { "lvl-region": ["AMERICAS"] } },
    });
    expect(state.audience.locationSelections).toEqual({
      "lvl-region": ["AMERICAS"],
    });
    expect(state.isDirty).toBe(true);
  });

  it("UPDATE_BUDGET sets isDirty", () => {
    const state = builderReducer(initialBuilderState, {
      type: "UPDATE_BUDGET",
      payload: { selectedCurrencies: ["cash"] },
    });
    expect(state.budgetData.selectedCurrencies).toContain("cash");
    expect(state.isDirty).toBe(true);
  });

  // BUG-071 follow-up: the implied-currencies safety net used to only check
  // globalBudgets / regionBudgets / rewardAmounts. When the AI dispatched
  // locationBudgets without selectedCurrencies, the per-location tree section
  // never rendered because the currency dropdown stayed empty.
  it("UPDATE_BUDGET auto-adds currencies seen only in locationBudgets to selectedCurrencies", () => {
    const state = builderReducer(initialBuilderState, {
      type: "UPDATE_BUDGET",
      payload: {
        budgetMode: "per-location",
        locationBudgets: { cash: { "uuid-americas": "200000" } },
      },
    });
    expect(state.budgetData.selectedCurrencies).toContain("cash");
    expect(state.budgetData.budgetMode).toBe("per-location");
    expect(state.budgetData.locationBudgets.cash).toEqual({
      "uuid-americas": "200000",
    });
  });

  it("TOGGLE_STEP collapses if already expanded", () => {
    const expanded = builderReducer(initialBuilderState, {
      type: "EXPAND_STEP",
      payload: "schedule",
    });
    const toggled = builderReducer(expanded, {
      type: "TOGGLE_STEP",
      payload: "schedule",
    });
    expect(toggled.expandedSteps).toEqual([]);
  });

  it("TOGGLE_STEP expands if collapsed", () => {
    const state = builderReducer(
      { ...initialBuilderState, expandedSteps: [] },
      { type: "TOGGLE_STEP", payload: "audience" },
    );
    expect(state.expandedSteps).toEqual(["audience"]);
  });

  it("RESET returns to initial state", () => {
    const dirty: BuilderState = {
      ...initialBuilderState,
      isDirty: true,
      basics: { ...initialBuilderState.basics, name: "Modified" },
    };
    const state = builderReducer(dirty, { type: "RESET" });
    expect(state).toEqual(initialBuilderState);
  });

  it("SET_FLOW_STATE to builder resets isDirty", () => {
    const dirty: BuilderState = { ...initialBuilderState, isDirty: true };
    const state = builderReducer(dirty, {
      type: "SET_FLOW_STATE",
      payload: "builder",
    });
    expect(state.flowState).toBe("builder");
    expect(state.isDirty).toBe(false);
  });

  it("SHOW_FORECASTING hides criteria editor", () => {
    const state = builderReducer(
      { ...initialBuilderState, showCriteriaEditor: true },
      { type: "SHOW_FORECASTING" },
    );
    expect(state.showForecasting).toBe(true);
    expect(state.showCriteriaEditor).toBe(false);
  });

  it("SHOW_CRITERIA_EDITOR hides forecasting", () => {
    const state = builderReducer(
      { ...initialBuilderState, showForecasting: true },
      { type: "SHOW_CRITERIA_EDITOR" },
    );
    expect(state.showCriteriaEditor).toBe(true);
    expect(state.showForecasting).toBe(false);
  });
});
