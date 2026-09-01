import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// --- Mocks (declared BEFORE imports that depend on them) ---

vi.mock("@/hooks/useBalanceBreakage", () => ({
  useBalanceBreakage: vi.fn(),
}));

vi.mock("@/services/balanceExpiration.service", () => ({
  exportBreakage: vi.fn(),
}));

vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: () => ({
    can: () => true,
    canAny: () => true,
    canAll: () => true,
    permissions: new Set(),
  }),
}));

// sonner toast mock — prevents JSDOM from complaining about missing toast context
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

// --- Imports ---

import { useBalanceBreakage } from "@/hooks/useBalanceBreakage";
import { exportBreakage } from "@/services/balanceExpiration.service";
import { BreakageReportTable } from "@/components/balanceExpiration/BreakageReportTable";
import type { BalanceBreakageReportResponse } from "@/types/balanceExpiration.types";
import { toast } from "sonner";

const mockUseBalanceBreakage = vi.mocked(useBalanceBreakage);
const mockExportBreakage = vi.mocked(exportBreakage);
const mockToastError = vi.mocked(toast.error);

// shape: contracts/models/balance-breakage-report.md
const BREAKAGE_REPORT: BalanceBreakageReportResponse = {
  from: "2026-01-01",
  to: "2026-03-31",
  granularity: "MONTH",
  rows: [
    {
      periodStart: "2026-01-01",
      periodEnd: "2026-01-31",
      currencyId: "points",
      currencyDisplayName: "Points",
      expiredCount: 5,
      totalExpiredAmount: "1250.00",
    },
    {
      periodStart: "2026-02-01",
      periodEnd: "2026-02-28",
      currencyId: "cash",
      currencyDisplayName: "Cash",
      expiredCount: 3,
      totalExpiredAmount: "450.00",
    },
  ],
};

/** Default idle mock */
function makeIdleResult(
  overrides: Partial<ReturnType<typeof useBalanceBreakage>> = {},
): ReturnType<typeof useBalanceBreakage> {
  return {
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useBalanceBreakage>;
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseBalanceBreakage.mockReturnValue(makeIdleResult());
});

describe("BreakageReportTable", () => {
  // ── Loading skeleton ──────────────────────────────────────────────────────

  describe("loading state", () => {
    it("renders skeleton with role=status aria-busy when isLoading=true", () => {
      mockUseBalanceBreakage.mockReturnValue(
        makeIdleResult({ isLoading: true }),
      );

      render(<BreakageReportTable />);

      const statusEl = screen.getByRole("status", { name: /loading breakage report/i });
      expect(statusEl).toBeDefined();
      expect(statusEl.getAttribute("aria-busy")).toBe("true");
    });
  });

  // ── Error state ───────────────────────────────────────────────────────────

  describe("error state", () => {
    it("shows error message and Try again button when isError=true", () => {
      mockUseBalanceBreakage.mockReturnValue(
        makeIdleResult({ isError: true }),
      );

      render(<BreakageReportTable />);

      expect(screen.getByText(/could not load breakage report/i)).toBeDefined();
      expect(screen.getByRole("button", { name: /try again/i })).toBeDefined();
    });
  });

  // ── Empty state ───────────────────────────────────────────────────────────

  describe("empty state", () => {
    it("renders empty state copy when rows is empty (AC-1)", () => {
      mockUseBalanceBreakage.mockReturnValue(
        makeIdleResult({
          data: { from: "2026-01-01", to: "2026-03-31", granularity: "MONTH", rows: [] },
        }),
      );

      render(<BreakageReportTable />);

      expect(
        screen.getByText("No expired balances in this period"),
      ).toBeDefined();
    });
  });

  // ── Data rows ─────────────────────────────────────────────────────────────

  describe("populated data", () => {
    beforeEach(() => {
      mockUseBalanceBreakage.mockReturnValue(
        makeIdleResult({ data: BREAKAGE_REPORT }),
      );
    });

    it("renders currency labels from getCurrency (not raw BE values) (AC-1)", () => {
      render(<BreakageReportTable />);

      // getCurrency('points').label = 'Points'; getCurrency('cash').label = 'Cash'
      const pointsCells = screen.getAllByText("Points");
      expect(pointsCells.length).toBeGreaterThan(0);
      const cashCells = screen.getAllByText("Cash");
      expect(cashCells.length).toBeGreaterThan(0);
    });

    it("renders expiredCount values in rows", () => {
      render(<BreakageReportTable />);

      // expiredCount = 5 for points, 3 for cash
      expect(screen.getByText("5")).toBeDefined();
      expect(screen.getByText("3")).toBeDefined();
    });

    it("shows Export CSV button when rows exist (AC-2)", () => {
      render(<BreakageReportTable />);

      expect(
        screen.getByRole("button", { name: /export breakage report as csv/i }),
      ).toBeDefined();
    });
  });

  // ── Filter bar ────────────────────────────────────────────────────────────

  describe("filter bar", () => {
    it("shows Apply filters button", () => {
      render(<BreakageReportTable />);

      expect(
        screen.getByRole("button", { name: /apply filters/i }),
      ).toBeDefined();
    });

    it("shows range error when end date is before start date (AC-4)", async () => {
      const user = userEvent.setup();
      render(<BreakageReportTable />);

      // Clear and set an invalid date range (to < from)
      const fromInput = screen.getByLabelText(/start date/i);
      const toInput = screen.getByLabelText(/end date/i);

      await user.clear(fromInput);
      await user.type(fromInput, "2026-06-01");
      await user.clear(toInput);
      await user.type(toInput, "2026-05-01");

      await user.click(screen.getByRole("button", { name: /apply filters/i }));

      expect(
        screen.getByText("End date must be on or after start date"),
      ).toBeDefined();
    });

    it("shows range error when date span exceeds 24 months (AC-4)", async () => {
      const user = userEvent.setup();
      render(<BreakageReportTable />);

      const fromInput = screen.getByLabelText(/start date/i);
      const toInput = screen.getByLabelText(/end date/i);

      await user.clear(fromInput);
      await user.type(fromInput, "2024-01-01");
      await user.clear(toInput);
      await user.type(toInput, "2026-06-01");

      await user.click(screen.getByRole("button", { name: /apply filters/i }));

      expect(screen.getByText("Range cannot exceed 24 months")).toBeDefined();
    });
  });

  // ── Export CSV ────────────────────────────────────────────────────────────

  describe("export CSV", () => {
    beforeEach(() => {
      mockUseBalanceBreakage.mockReturnValue(
        makeIdleResult({ data: BREAKAGE_REPORT }),
      );
    });

    it("calls exportBreakage and triggers download on Export CSV click (AC-2)", async () => {
      const user = userEvent.setup();
      const csvBlob = new Blob(["period_start,period_end\n"], { type: "text/csv" });
      mockExportBreakage.mockResolvedValue(csvBlob);

      // Stub URL.createObjectURL / revokeObjectURL (not present in JSDOM)
      const createObjectURL = vi.fn(() => "blob://fake-url");
      const revokeObjectURL = vi.fn();
      vi.stubGlobal("URL", { createObjectURL, revokeObjectURL });

      // Render first so React's own createElement calls happen before the spy
      render(<BreakageReportTable />);

      // After render, intercept ALL createElement("a") calls via a persistent spy.
      // The spy replaces the method; we restore it in afterEach via vi.restoreAllMocks()
      // (registered in the outer beforeEach vi.clearAllMocks() + afterEach below).
      const clickFn = vi.fn();
      const originalCreate = document.createElement.bind(document);
      vi.spyOn(document, "createElement").mockImplementation((tag: string) => {
        if (tag === "a") {
          const a = originalCreate("a") as HTMLAnchorElement;
          a.click = clickFn;
          return a;
        }
        return originalCreate(tag);
      });

      const exportBtn = screen.getByRole("button", {
        name: /export breakage report as csv/i,
      });
      await user.click(exportBtn);

      expect(mockExportBreakage).toHaveBeenCalledOnce();
      expect(clickFn).toHaveBeenCalledOnce();

      // Restore to not pollute other tests
      vi.restoreAllMocks();
    });

    it("shows rate-limit toast on 429 export response (AC-3)", async () => {
      const user = userEvent.setup();

      // Simulate a 429 Axios error
      const axiosError = {
        isAxiosError: true,
        response: { status: 429 },
        message: "Too Many Requests",
      };
      mockExportBreakage.mockRejectedValue(axiosError);

      // isAxiosError mock — module-level vi.mock not needed since we're checking the property
      vi.mock("axios", () => ({
        isAxiosError: (err: unknown) =>
          Boolean(err && (err as { isAxiosError?: boolean }).isAxiosError),
      }));

      render(<BreakageReportTable />);

      const exportBtn = screen.getByRole("button", {
        name: /export breakage report as csv/i,
      });
      await user.click(exportBtn);

      expect(mockToastError).toHaveBeenCalledWith(
        "You're exporting too frequently. Please wait a moment and try again.",
      );
    });
  });
});
