import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";

// Mock every widget so we can assert on render order + layout classes
// without pulling in hook dependencies.
vi.mock("@/components/home/widgets/AiAssistantWidget", () => ({
  AiAssistantWidget: () => <div data-testid="widget-ai_assistant" />,
}));
vi.mock("@/components/home/widgets/ProgramPerformanceWidget", () => ({
  ProgramPerformanceWidget: () => (
    <div data-testid="widget-program_performance" />
  ),
}));
vi.mock("@/components/home/widgets/TenXSuggestionsWidget", () => ({
  TenXSuggestionsWidget: () => <div data-testid="widget-tenx_suggestions" />,
}));
vi.mock("@/components/home/widgets/RewardsBalancesWidget", () => ({
  RewardsBalancesWidget: () => <div data-testid="widget-rewards_balances" />,
}));
vi.mock("@/components/home/widgets/ApprovalsWidget", () => ({
  ApprovalsWidget: () => <div data-testid="widget-approvals" />,
}));

import { HomeDashboardTemplateRenderer } from "@/components/home/HomeDashboardTemplateRenderer";
import type { HomeDashboardTemplate } from "@/types/home-dashboard.types";

function makeTemplate(
  overrides: Partial<HomeDashboardTemplate> = {},
): HomeDashboardTemplate {
  return {
    id: "t-1",
    clientId: "c-1",
    name: "Test",
    description: null,
    roleType: "INTERNAL",
    layout: { rows: [] },
    isSystem: true,
    createdAt: "2026-04-19T00:00:00Z",
    updatedAt: "2026-04-19T00:00:00Z",
    ...overrides,
  };
}

describe("HomeDashboardTemplateRenderer", () => {
  beforeEach(() => {
    vi.spyOn(console, "warn").mockImplementation(() => {});
  });

  it("renders the empty state when template is null", () => {
    render(<HomeDashboardTemplateRenderer template={null} />);
    expect(screen.getByTestId("home-dashboard-empty")).toBeDefined();
  });

  it("renders the empty state when template has no rows", () => {
    render(
      <HomeDashboardTemplateRenderer
        template={makeTemplate({ layout: { rows: [] } })}
      />,
    );
    expect(screen.getByTestId("home-dashboard-empty")).toBeDefined();
  });

  it("renders a custom empty state when provided", () => {
    render(
      <HomeDashboardTemplateRenderer
        template={null}
        emptyState={<div data-testid="custom-empty">Nope</div>}
      />,
    );
    expect(screen.getByTestId("custom-empty")).toBeDefined();
  });

  it("renders the Client Admin template as two full-width rows", () => {
    const template = makeTemplate({
      name: "Client Admin",
      layout: {
        rows: [
          { layout: "full", slots: [{ widgetKey: "ai_assistant" }] },
          { layout: "full", slots: [{ widgetKey: "program_performance" }] },
        ],
      },
    });
    const { container } = render(
      <HomeDashboardTemplateRenderer template={template} />,
    );
    const rows = container.querySelectorAll("[data-layout]");
    expect(rows).toHaveLength(2);
    expect(rows[0]?.getAttribute("data-layout")).toBe("full");
    expect(rows[0]?.className).toContain("grid-cols-1");
    expect(screen.getByTestId("widget-ai_assistant")).toBeDefined();
    expect(screen.getByTestId("widget-program_performance")).toBeDefined();
  });

  it("renders the Partner User template with a half-half row plus a full row", () => {
    const template = makeTemplate({
      name: "Partner User",
      roleType: "EXTERNAL",
      layout: {
        rows: [
          {
            layout: "half-half",
            slots: [
              { widgetKey: "ai_assistant" },
              { widgetKey: "rewards_balances" },
            ],
          },
          { layout: "full", slots: [{ widgetKey: "tenx_suggestions" }] },
        ],
      },
    });
    const { container } = render(
      <HomeDashboardTemplateRenderer template={template} />,
    );
    const rows = container.querySelectorAll("[data-layout]");
    expect(rows).toHaveLength(2);
    expect(rows[0]?.getAttribute("data-layout")).toBe("half-half");
    expect(rows[0]?.className).toContain("md:grid-cols-2");
    expect(rows[1]?.className).toContain("grid-cols-1");
    expect(screen.getByTestId("widget-ai_assistant")).toBeDefined();
    expect(screen.getByTestId("widget-rewards_balances")).toBeDefined();
    expect(screen.getByTestId("widget-tenx_suggestions")).toBeDefined();
  });

  it("preserves slot order within a row", () => {
    const template = makeTemplate({
      layout: {
        rows: [
          {
            layout: "half-half",
            slots: [
              { widgetKey: "rewards_balances" },
              { widgetKey: "ai_assistant" },
            ],
          },
        ],
      },
    });
    const { container } = render(
      <HomeDashboardTemplateRenderer template={template} />,
    );
    const row = container.querySelector("[data-layout]");
    const kids = row?.children ?? [];
    expect((kids[0] as HTMLElement).getAttribute("data-testid")).toBe(
      "widget-rewards_balances",
    );
    expect((kids[1] as HTMLElement).getAttribute("data-testid")).toBe(
      "widget-ai_assistant",
    );
  });

  it("renders a placeholder for an unknown widget key", () => {
    const template = makeTemplate({
      layout: {
        rows: [
          {
            layout: "full",
            slots: [{ widgetKey: "mystery_widget" }],
          },
        ],
      },
    });
    render(<HomeDashboardTemplateRenderer template={template} />);
    const placeholder = screen.getByTestId("home-dashboard-unknown-widget");
    expect(placeholder).toBeDefined();
    expect(placeholder.getAttribute("data-widget-key")).toBe("mystery_widget");
  });

  it("skips a row with an unknown layout", () => {
    const template = makeTemplate({
      layout: {
        rows: [
          { layout: "thirds", slots: [{ widgetKey: "ai_assistant" }] },
          { layout: "full", slots: [{ widgetKey: "program_performance" }] },
        ],
      },
    });
    const { container } = render(
      <HomeDashboardTemplateRenderer template={template} />,
    );
    const rows = container.querySelectorAll("[data-layout]");
    expect(rows).toHaveLength(1);
    expect(rows[0]?.getAttribute("data-layout")).toBe("full");
    expect(screen.getByTestId("widget-program_performance")).toBeDefined();
    // ai_assistant's row was skipped, so no widget render
    expect(screen.queryByTestId("widget-ai_assistant")).toBeNull();
  });

  it("renders the Approver template as a single full-width approvals row", () => {
    const template = makeTemplate({
      name: "Approver",
      layout: {
        rows: [{ layout: "full", slots: [{ widgetKey: "approvals" }] }],
      },
    });
    render(<HomeDashboardTemplateRenderer template={template} />);
    expect(screen.getByTestId("widget-approvals")).toBeDefined();
  });
});
