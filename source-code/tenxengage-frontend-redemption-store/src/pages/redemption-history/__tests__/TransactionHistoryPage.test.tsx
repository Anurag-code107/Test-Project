import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";

vi.mock("@/hooks/useAuth", () => ({ useAuth: vi.fn() }));
vi.mock("@/hooks/usePermissions", () => ({ usePermissions: vi.fn() }));
vi.mock("@/hooks/redemption-history/usePersonalRedemptions", () => ({
  usePersonalRedemptions: vi.fn(() => ({ data: undefined, isLoading: false, isError: false })),
}));
vi.mock("@/hooks/redemption-history/useCompanyRedemptions", () => ({
  useCompanyRedemptions: vi.fn(() => ({ data: undefined, isLoading: false, isError: false })),
}));
vi.mock("@/components/redemption-history/HistoryFilterBar", () => ({
  HistoryFilterBar: () => <div data-testid="filter-bar" />,
}));
vi.mock("@/components/redemption-history/TransactionHistoryTable", () => ({
  TransactionHistoryTable: () => <div data-testid="history-table" />,
}));
vi.mock("@/components/redemption-history/TransactionDetailSheet", () => ({
  TransactionDetailSheet: () => null,
}));
vi.mock("@/components/redemption-history/ExportDialog", () => ({
  ExportDialog: () => null,
}));
vi.mock("@/components/PageBanner", () => ({
  PageBanner: ({ title }: { title: string }) => <h1>{title}</h1>,
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn() } }));
vi.mock("@tanstack/react-query", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-query")>();
  return {
    ...actual,
    useQueryClient: vi.fn(() => ({ invalidateQueries: vi.fn() })),
  };
});
vi.mock("@/components/redemption-returns/MyReturnsTab", () => ({
  MyReturnsTab: () => <div data-testid="my-returns-tab" />,
}));
vi.mock("@/components/redemption-returns/RequestReturnDialog", () => ({
  RequestReturnDialog: () => null,
}));

import { useAuth } from "@/hooks/useAuth";
import { usePermissions } from "@/hooks/usePermissions";
import TransactionHistoryPage from "@/pages/redemption-history/TransactionHistoryPage";

const mockUseAuth = vi.mocked(useAuth);
const mockUsePermissions = vi.mocked(usePermissions);

function setup(clientRoleName: string, canViewHistory = true) {
  mockUseAuth.mockReturnValue({
    user: {
      id: "u1",
      email: "test@example.com",
      firstName: "Test",
      lastName: "User",
      permissions: canViewHistory ? ["action.redemption.view_history"] : [],
      clientRoleId: "r1",
      clientRoleName,
      organizationId: null,
      clientId: "c1",
      clientName: "Acme",
      partnerCompanyId: "p1",
      partnerCompanyName: "Acme Corp",
      status: "ACTIVE",
    },
    isAuthenticated: true,
    isLoading: false,
    enabledFeatures: [],
    login: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
  });
  mockUsePermissions.mockReturnValue({
    can: vi.fn((key: string) => key === "action.redemption.view_history" ? canViewHistory : false),
    canAny: vi.fn(),
    canAll: vi.fn(),
    permissions: new Set(canViewHistory ? ["action.redemption.view_history"] : []),
  });
}

describe("TransactionHistoryPage — tab visibility", () => {
  // Company redemption is not yet supported (COMPANY_REDEMPTION_ENABLED = false), so the
  // Company tab is hidden for everyone — including a Partner Admin who has view_history.
  it("PARTNER_ADMIN sees Personal tab but no Company tab while company redemption is disabled", () => {
    setup("Partner Admin");
    render(<TransactionHistoryPage />);
    expect(screen.getByRole("tab", { name: "Personal" })).toBeDefined();
    expect(screen.queryByRole("tab", { name: "Company" })).toBeNull();
  });

  it("PARTNER_SELLER sees only Personal tab — no Company tab in DOM", () => {
    setup("PARTNER_SELLER");
    render(<TransactionHistoryPage />);
    expect(screen.getByRole("tab", { name: "Personal" })).toBeDefined();
    expect(screen.queryByRole("tab", { name: "Company" })).toBeNull();
  });
});
