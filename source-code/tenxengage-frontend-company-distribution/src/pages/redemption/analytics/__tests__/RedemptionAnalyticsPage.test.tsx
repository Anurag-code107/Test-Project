import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

// ── Module mocks ──────────────────────────────────────────────────────────────

vi.mock("@/hooks/useRedemptionAnalytics", () => ({
  useRedemptionAnalytics: vi.fn(),
}));

vi.mock("@/hooks/useAnalyticsExport", () => ({
  useAnalyticsExport: vi.fn(() => ({
    exportCsv: vi.fn(),
    isPending: false,
    retryAfter: null,
    isServerError: false,
  })),
}));

vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: () => ({ can: () => false, canAny: () => false, canAll: () => false, permissions: new Set() }),
}));

vi.mock("@/hooks/useFeatures", () => ({
  useFeatures: () => ({ has: () => false }),
}));

// Stub heavy / portal children we aren't testing here.
vi.mock("@/components/analytics/advanced/AdvancedAnalyticsTab", () => ({
  AdvancedAnalyticsTab: () => <div data-testid="advanced-tab" />,
}));
vi.mock("@/components/redemption-analytics/DateRangeFilter", () => ({
  DateRangeFilter: () => <div data-testid="date-range-filter" />,
}));
vi.mock("@/components/redemption-analytics/ExportConfirmDialog", () => ({
  ExportConfirmDialog: () => null,
}));

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { useRedemptionAnalytics } from "@/hooks/useRedemptionAnalytics";
import RedemptionAnalyticsPage from "@/pages/redemption/analytics/RedemptionAnalyticsPage";
import type {
  RedemptionAnalyticsSummaryResponse,
  CurrencyTypeRateDto,
  CurrencyTypeBalanceDto,
} from "@/types/redemption-analytics.types";

const mockUseAnalytics = vi.mocked(useRedemptionAnalytics);

const rate = (currencyId: string, over: Partial<CurrencyTypeRateDto> = {}): CurrencyTypeRateDto => ({
  currencyId,
  numerator: 150,
  denominator: 30050,
  ratePercentage: "0.50",
  hasActivity: true,
  ...over,
});

const bal = (currencyId: string, over: Partial<CurrencyTypeBalanceDto> = {}): CurrencyTypeBalanceDto => ({
  currencyId,
  availableBalance: 100,
  reservedBalance: 10,
  totalOutstanding: 110,
  ...over,
});

function makeSummary(over: Partial<RedemptionAnalyticsSummaryResponse> = {}): RedemptionAnalyticsSummaryResponse {
  return {
    dateWindow: { from: "2026-05-20", to: "2026-06-19" },
    redemptionRates: [rate("points"), rate("cash")],
    unredeemedBalances: [bal("points"), bal("cash")],
    failedCancelledRates: [rate("points"), rate("cash")],
    totalRedemptionCount: { total: 34, byStatus: { COMPLETED: 9, FAILED: 20, CANCELLED: 1, PENDING: 4, PROCESSING: 0 }, hasActivity: true },
    ...over,
  };
}

function makeQuery(over = {}) {
  return {
    data: makeSummary(),
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    ...over,
  } as unknown as ReturnType<typeof useRedemptionAnalytics>;
}

function renderAt(path = "/redemption/admin/analytics") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <RedemptionAnalyticsPage />
    </MemoryRouter>,
  );
}

describe("RedemptionAnalyticsPage — currency section (CR-02)", () => {
  it("selects a default currency on load and renders only its three metric cards", () => {
    mockUseAnalytics.mockReturnValue(makeQuery());
    renderAt();

    // default = first active in priority order [points, cash, ...] → points
    // titles render the human currency label (getCurrency().label), not the raw id
    expect(screen.getByText("Points Redemption Rate")).toBeDefined();
    expect(screen.getByText("Points Outstanding Liability")).toBeDefined();
    expect(screen.getByText(/Points Failed & Cancelled Rate/)).toBeDefined();
    // the other currency's cards are NOT shown (one currency at a time)
    expect(screen.queryByText("Cash Redemption Rate")).toBeNull();
  });

  it("honors ?currency= from the URL", () => {
    mockUseAnalytics.mockReturnValue(makeQuery());
    renderAt("/redemption/admin/analytics?currency=cash");

    expect(screen.getByText("Cash Redemption Rate")).toBeDefined();
    expect(screen.queryByText("Points Redemption Rate")).toBeNull();
  });

  it("keeps Total Redemptions visible regardless of selected currency (global)", () => {
    mockUseAnalytics.mockReturnValue(makeQuery());
    renderAt("/redemption/admin/analytics?currency=cash");

    // TotalCountCard renders its by-status list (currency-agnostic)
    expect(screen.getByLabelText("Redemptions by status")).toBeDefined();
  });

  it("derives the dropdown from the union of all arrays (currency present in only one array is selectable)", () => {
    // 'tickets' appears ONLY in unredeemedBalances
    mockUseAnalytics.mockReturnValue(
      makeQuery({
        data: makeSummary({
          redemptionRates: [rate("points")],
          unredeemedBalances: [bal("points"), bal("tickets")],
          failedCancelledRates: [rate("points")],
        }),
      }),
    );
    renderAt("/redemption/admin/analytics?currency=tickets");

    // tickets was in the union → selectable → its (only) card renders
    expect(screen.getByText("Tickets Outstanding Liability")).toBeDefined();
    // tickets has no rate/failed DTO → those cards simply aren't rendered
    expect(screen.queryByText("Tickets Redemption Rate")).toBeNull();
  });

  it("shows the no-activity empty state and no dropdown when there is no currency data", () => {
    mockUseAnalytics.mockReturnValue(
      makeQuery({
        data: makeSummary({
          redemptionRates: [],
          unredeemedBalances: [],
          failedCancelledRates: [],
          totalRedemptionCount: { total: 0, byStatus: {}, hasActivity: false },
        }),
      }),
    );
    renderAt();

    expect(screen.getByText("No program activity yet")).toBeDefined();
    expect(screen.queryByLabelText("Select currency")).toBeNull();
  });

  it("labels the export action as all-currency (R3)", () => {
    mockUseAnalytics.mockReturnValue(makeQuery());
    renderAt();

    expect(screen.getByRole("button", { name: /export all balances/i })).toBeDefined();
  });
});
