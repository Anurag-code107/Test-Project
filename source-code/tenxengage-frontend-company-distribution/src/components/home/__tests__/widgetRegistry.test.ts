import { describe, it, expect } from "vitest";
import {
  homeDashboardWidgetRegistry,
  getWidgetComponent,
} from "@/components/home/widgetRegistry";
import { HOME_DASHBOARD_WIDGET_KEYS } from "@/types/home-dashboard.types";

describe("widgetRegistry", () => {
  it("has a component for every canonical widget key", () => {
    for (const key of HOME_DASHBOARD_WIDGET_KEYS) {
      expect(homeDashboardWidgetRegistry[key]).toBeDefined();
      expect(typeof homeDashboardWidgetRegistry[key]).toBe("function");
    }
  });

  it("getWidgetComponent returns the component for a valid key", () => {
    const component = getWidgetComponent("ai_assistant");
    expect(component).toBeDefined();
    expect(component).toBe(homeDashboardWidgetRegistry.ai_assistant);
  });

  it("getWidgetComponent returns undefined for an unknown key", () => {
    expect(getWidgetComponent("not_a_real_widget")).toBeUndefined();
    expect(getWidgetComponent("")).toBeUndefined();
  });

  it("covers all five initial widgets", () => {
    expect(Object.keys(homeDashboardWidgetRegistry).sort()).toEqual([
      "ai_assistant",
      "approvals",
      "program_performance",
      "rewards_balances",
      "tenx_suggestions",
    ]);
  });
});
