import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

// ── Module mocks ──────────────────────────────────────────────────────────────

const mockCan = vi.fn();
vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: () => ({ can: mockCan, canAny: () => false, canAll: () => false, permissions: new Set() }),
}));
vi.mock("@/hooks/useFeatures", () => ({
  useFeatures: () => ({ has: () => true }),
}));
vi.mock("@/contexts/BrandingContext", () => ({
  useBrandingContext: () => ({ logoSrc: "logo.png" }),
}));
vi.mock("@/contexts/NavigationGuardContext", () => ({
  useNavigationGuard: () => ({ checkGuard: () => true }),
}));
vi.mock("@/components/layout/sidebars/SidebarProfileMenu", () => ({
  SidebarProfileMenu: () => null,
}));
vi.mock("@/components/layout/sidebars/SidebarTooltip", () => ({
  SidebarTooltip: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

import { RoleSidebar } from "@/components/layout/sidebars/RoleSidebar";
import { sidebarConfig } from "@/components/layout/sidebars/sidebarConfigs";

function canWith(...granted: string[]) {
  const set = new Set(granted);
  return (key: string) => set.has(key);
}

function renderSidebar() {
  return render(
    <MemoryRouter>
      <RoleSidebar config={sidebarConfig} />
    </MemoryRouter>,
  );
}

describe("RoleSidebar — consolidated Redemption nav (CR-03/CR-04)", () => {
  beforeEach(() => mockCan.mockReset());

  it("Partner Seller: Redemption group with Store + Transaction History; non-redemption nav intact", () => {
    mockCan.mockImplementation(
      canWith(
        "module.home",
        "module.rewards.balances",
        "module.redemption_store",
        "action.redemption.redeem",
        "action.redemption.view_history",
      ),
    );
    renderSidebar();

    expect(screen.getByText("Redemption")).toBeDefined(); // group parent
    expect(screen.getByText("Redemption Store")).toBeDefined(); // has module + personal redeem
    expect(screen.getByText("Transaction History")).toBeDefined();
    expect(screen.getByText("Home")).toBeDefined(); // R6 regression: other nav untouched
    expect(screen.queryByText("Approval Queue")).toBeNull();
  });

  it("Partner Admin: module + company-redeem unlocks the storefront", () => {
    mockCan.mockImplementation(
      canWith(
        "module.home",
        "module.rewards.claims",
        "module.redemption_store",
        "action.redemption.redeem_company",
        "action.redemption.view_history",
      ),
    );
    renderSidebar();

    expect(screen.getByText("Redemption Store")).toBeDefined();
  });

  it("Client Admin: no Redemption Store (cannot redeem) but sees the admin sub-items", () => {
    mockCan.mockImplementation(
      canWith(
        "module.home",
        "module.manage_incentives",
        "module.redemption_store",
        "action.redemption.approve",
        "action.redemption.view_all_history",
        "action.redemption.view_analytics",
        "action.redemption.expiration.configure",
        "action.redemption.expiration.view_breakage",
      ),
    );
    renderSidebar();

    expect(screen.getByText("Redemption")).toBeDefined();
    // CR-04: holds the module umbrella but cannot redeem → storefront still hidden.
    expect(screen.queryByText("Redemption Store")).toBeNull();
    expect(screen.queryByText("Transaction History")).toBeNull(); // no view_history (personal)
    expect(screen.getByText("Approval Queue")).toBeDefined();
    expect(screen.getByText("All Redemptions")).toBeDefined();
    expect(screen.getByText("Analytics")).toBeDefined();
    expect(screen.getByText("Home")).toBeDefined();
  });

  it("hides the entire Redemption group when no redemption sub-items are permitted", () => {
    mockCan.mockImplementation(canWith("module.home"));
    renderSidebar();

    expect(screen.queryByText("Redemption")).toBeNull();
    expect(screen.getByText("Home")).toBeDefined();
  });

  it("hides the WHOLE Redemption group when module.redemption_store is removed, even with admin sub-item perms", () => {
    // Company/user-level override removing module.redemption_store → the entire Redemption
    // tab disappears (Approval Queue / All Redemptions / Analytics included), not just the store.
    mockCan.mockImplementation(
      canWith(
        "module.home",
        "module.manage_incentives",
        // module.redemption_store intentionally ABSENT (overridden off)
        "action.redemption.approve",
        "action.redemption.view_all_history",
        "action.redemption.view_analytics",
      ),
    );
    renderSidebar();

    expect(screen.queryByText("Redemption")).toBeNull();
    expect(screen.queryByText("Approval Queue")).toBeNull();
    expect(screen.queryByText("All Redemptions")).toBeNull();
    expect(screen.getByText("Home")).toBeDefined();
  });
});
