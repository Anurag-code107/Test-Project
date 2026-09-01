import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";

vi.mock("@/hooks/useAuth", () => ({
  useAuth: vi.fn(),
}));
vi.mock("@/hooks/useHomeDashboardTemplate", () => ({
  useHomeDashboardTemplate: vi.fn(),
}));
vi.mock("@/components/home/HomeDashboardTemplateRenderer", () => ({
  HomeDashboardTemplateRenderer: ({ template }: { template: unknown }) => (
    <div
      data-testid="renderer-stub"
      data-template-name={
        template && typeof template === "object" && "name" in template
          ? (template as { name: string }).name
          : "none"
      }
    />
  ),
}));

import { useAuth } from "@/hooks/useAuth";
import { useHomeDashboardTemplate } from "@/hooks/useHomeDashboardTemplate";
import HomePage from "@/pages/HomePage";
import type { HomeDashboardTemplate } from "@/types/home-dashboard.types";

const mockUseAuth = vi.mocked(useAuth);
const mockUseTemplate = vi.mocked(useHomeDashboardTemplate);

function setUser(firstName = "Alex") {
  mockUseAuth.mockReturnValue({
    user: { firstName },
  } as unknown as ReturnType<typeof useAuth>);
}

function template(name: string): HomeDashboardTemplate {
  return {
    id: "t-1",
    clientId: "c-1",
    name,
    description: null,
    roleType: "INTERNAL",
    layout: { rows: [{ layout: "full", slots: [{ widgetKey: "approvals" }] }] },
    isSystem: true,
    createdAt: "2026-04-19T00:00:00Z",
    updatedAt: "2026-04-19T00:00:00Z",
  };
}

describe("HomePage (unified)", () => {
  beforeEach(() => {
    setUser();
    mockUseTemplate.mockReturnValue(null);
  });

  it("renders the greeting with the user's first name", () => {
    setUser("Jordan");
    render(<HomePage />);
    expect(screen.getByText(/Jordan/)).toBeDefined();
  });

  it("falls back to 'there' if user.firstName is missing", () => {
    mockUseAuth.mockReturnValue({
      user: null,
    } as unknown as ReturnType<typeof useAuth>);
    render(<HomePage />);
    expect(screen.getByText(/there/i)).toBeDefined();
  });

  it("uses the Client Admin subtitle when the template is 'Client Admin'", () => {
    mockUseTemplate.mockReturnValue(template("Client Admin"));
    render(<HomePage />);
    expect(screen.getByText(/Partner performance overview/)).toBeDefined();
  });

  it("uses the Partner User subtitle when the template is 'Partner User'", () => {
    mockUseTemplate.mockReturnValue(template("Partner User"));
    render(<HomePage />);
    expect(screen.getByText(/Rewards and recommendations/)).toBeDefined();
  });

  it("uses the Approver subtitle when the template is 'Approver'", () => {
    mockUseTemplate.mockReturnValue(template("Approver"));
    render(<HomePage />);
    expect(screen.getByText(/Proof-of-execution submissions/)).toBeDefined();
  });

  it("uses the generic subtitle when no template is available", () => {
    mockUseTemplate.mockReturnValue(null);
    render(<HomePage />);
    expect(screen.getByText(/Your home dashboard/)).toBeDefined();
  });

  it("passes the template through to HomeDashboardTemplateRenderer", () => {
    mockUseTemplate.mockReturnValue(template("Approver"));
    render(<HomePage />);
    expect(
      screen.getByTestId("renderer-stub").getAttribute("data-template-name"),
    ).toBe("Approver");
  });

  it("passes null to the renderer when no template exists", () => {
    mockUseTemplate.mockReturnValue(null);
    render(<HomePage />);
    expect(
      screen.getByTestId("renderer-stub").getAttribute("data-template-name"),
    ).toBe("none");
  });
});
