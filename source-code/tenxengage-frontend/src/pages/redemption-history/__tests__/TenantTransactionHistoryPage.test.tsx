import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import type { UseQueryResult } from "@tanstack/react-query";
import type { PaginatedResponse } from "@/types/api.types";
import type { RedemptionAdminHistoryResponse } from "@/types/redemption-history/redemption-history.types";
import { useTenantRedemptions } from "@/hooks/redemption-history/useTenantRedemptions";
import { usePermissions } from "@/hooks/usePermissions";

vi.mock("@/hooks/redemption-history/useTenantRedemptions", () => ({
  useTenantRedemptions: vi.fn(),
}));
vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: vi.fn(),
}));
vi.mock("@/components/redemption-history/HistoryFilterBar", () => ({
  HistoryFilterBar: () => <div data-testid="filter-bar" />,
}));
vi.mock("@/components/redemption-history/TransactionDetailSheet", () => ({
  TransactionDetailSheet: () => null,
}));
vi.mock("@/components/redemption-history/ExportDialog", () => ({
  ExportDialog: ({ open }: { open: boolean }) =>
    open ? <div data-testid="export-dialog" /> : null,
}));
vi.mock("@/components/PageBanner", () => ({
  PageBanner: ({ title, actions }: { title: string; actions?: React.ReactNode }) => (
    <div><h1>{title}</h1><div>{actions}</div></div>
  ),
}));
vi.mock("@/components/PermissionGate", () => ({
  PermissionGate: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
vi.mock("@/config/currencies", () => ({
  getCurrency: () => ({
    format: (v: string | number) => `$${v}`,
    rewardFormat: (v: string | number) => `$${v}`,
  }),
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn() } }));

import TenantTransactionHistoryPage from "@/pages/redemption-history/TenantTransactionHistoryPage";

const mockUseTenantRedemptions = vi.mocked(useTenantRedemptions);
const mockUsePermissions = vi.mocked(usePermissions);

function makePage(rows: RedemptionAdminHistoryResponse[]): PaginatedResponse<RedemptionAdminHistoryResponse> {
  return {
    data: rows,
    page: 0,
    pageSize: 20,
    totalElements: rows.length,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  };
}

const SAMPLE_ROW: RedemptionAdminHistoryResponse = {
  id: "r1",
  status: "COMPLETED",
  amount: "100",
  currencyId: "USD",
  catalogItemId: "ci1",
  catalogItemName: "Gift Card",
  processingMode: "INSTANT",
  userId: "u1",
  userDisplayName: "Alice Smith",
  partnerCompanyId: "p1",
  partnerCompanyName: "Acme Corp",
  submittedAt: "2025-03-01T10:00:00Z",
  completedAt: "2025-03-01T10:05:00Z",
  estimatedDelivery: "2025-03-01",
  createdAt: "2025-03-01T10:00:00Z",
  updatedAt: "2025-03-01T10:05:00Z",
};

function idle() {
  mockUseTenantRedemptions.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
  } as unknown as UseQueryResult<PaginatedResponse<RedemptionAdminHistoryResponse>>);
}

function withData(rows: RedemptionAdminHistoryResponse[]) {
  mockUseTenantRedemptions.mockReturnValue({
    data: makePage(rows),
    isLoading: false,
    isError: false,
  } as unknown as UseQueryResult<PaginatedResponse<RedemptionAdminHistoryResponse>>);
}

function withViewDetail(allowed: boolean) {
  // Detail drill-in is gated on EITHER view_history or view_all_history (canAny).
  // Model a tenant admin who holds view_all_history when allowed.
  const perms = new Set(allowed ? ["action.redemption.view_all_history"] : []);
  mockUsePermissions.mockReturnValue({
    can: (key: string) => perms.has(key),
    canAny: (...keys: string[]) => keys.some((k) => perms.has(k)),
    canAll: (...keys: string[]) => keys.every((k) => perms.has(k)),
    permissions: perms,
  } as unknown as ReturnType<typeof usePermissions>);
}

beforeEach(() => {
  idle();
  withViewDetail(true);
});

describe("TenantTransactionHistoryPage — empty states", () => {
  it("shows 'No redemptions yet' when no data and no active filters", () => {
    withData([]);
    render(<TenantTransactionHistoryPage />);
    expect(screen.getByText("No redemptions yet")).toBeDefined();
  });
});

describe("TenantTransactionHistoryPage — table columns", () => {
  it("renders User and Company column headers", () => {
    withData([SAMPLE_ROW]);
    render(<TenantTransactionHistoryPage />);
    expect(screen.getByText("User")).toBeDefined();
    expect(screen.getByText("Company")).toBeDefined();
  });

  it("renders user display name and company name in table rows", () => {
    withData([SAMPLE_ROW]);
    render(<TenantTransactionHistoryPage />);
    expect(screen.getByText("Alice Smith")).toBeDefined();
    expect(screen.getByText("Acme Corp")).toBeDefined();
  });

  it("shows em-dash for rows without partnerCompanyName", () => {
    withData([{ ...SAMPLE_ROW, partnerCompanyId: undefined, partnerCompanyName: undefined }]);
    render(<TenantTransactionHistoryPage />);
    const dashes = screen.getAllByText("—");
    expect(dashes.length).toBeGreaterThanOrEqual(1);
  });
});

describe("TenantTransactionHistoryPage — export dialog", () => {
  it("renders page title 'All redemption history'", () => {
    render(<TenantTransactionHistoryPage />);
    expect(screen.getByText("All redemption history")).toBeDefined();
  });

  it("Export button opens the ExportDialog", () => {
    render(<TenantTransactionHistoryPage />);
    expect(screen.queryByTestId("export-dialog")).toBeNull();
    const btn = screen.getByRole("button", { name: /export/i });
    fireEvent.click(btn);
    expect(screen.getByTestId("export-dialog")).toBeDefined();
  });
});

describe("TenantTransactionHistoryPage — row detail permission", () => {
  it("rows have role=button when user can view detail (view_history or view_all_history)", () => {
    withViewDetail(true);
    withData([SAMPLE_ROW]);
    render(<TenantTransactionHistoryPage />);
    const cell = screen.getByText("Alice Smith");
    expect(cell.closest("tr")?.getAttribute("role")).toBe("button");
  });

  it("rows are not interactive when user lacks any detail-view permission", () => {
    withViewDetail(false);
    withData([SAMPLE_ROW]);
    render(<TenantTransactionHistoryPage />);
    const cell = screen.getByText("Alice Smith");
    expect(cell.closest("tr")?.getAttribute("role")).toBeNull();
  });
});
