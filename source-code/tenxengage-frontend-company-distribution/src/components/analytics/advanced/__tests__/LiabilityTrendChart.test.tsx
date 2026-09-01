// shape: contracts/models/redemption-advanced-analytics.md → LiabilityTrendResponse
// Covers: AC-4, AC-6 (story US-06)
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// ─── Mock recharts to avoid canvas / ResizeObserver errors in jsdom ───────────
vi.mock("recharts", () => {
  const MockResponsiveContainer = ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  );
  const MockLineChart = ({
    children,
    "aria-label": ariaLabel,
  }: {
    children: React.ReactNode;
    "aria-label"?: string;
  }) => (
    <div data-testid="line-chart" aria-label={ariaLabel}>
      {children}
    </div>
  );
  const MockLine = ({ dataKey }: { dataKey: string }) => (
    <div data-testid={`line-${dataKey}`} />
  );
  const MockXAxis = () => null;
  const MockYAxis = () => null;
  const MockCartesianGrid = () => null;
  const MockTooltip = () => null;

  return {
    ResponsiveContainer: MockResponsiveContainer,
    LineChart: MockLineChart,
    Line: MockLine,
    XAxis: MockXAxis,
    YAxis: MockYAxis,
    CartesianGrid: MockCartesianGrid,
    Tooltip: MockTooltip,
  };
});

// ─── Mock export service call ─────────────────────────────────────────────────
vi.mock("@/services/redemption-analytics-advanced.service", async (importOriginal) => {
  const actual = await importOriginal<
    typeof import("@/services/redemption-analytics-advanced.service")
  >();
  return {
    ...actual,
    exportLiabilityTrendCsv: vi.fn(),
  };
});

// ─── Mock sonner toast ────────────────────────────────────────────────────────
vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

import { LiabilityTrendChart } from "@/components/analytics/advanced/LiabilityTrendChart";
import {
  exportLiabilityTrendCsv,
  ExportRateLimitedError,
} from "@/services/redemption-analytics-advanced.service";
import type { LiabilityDataPointDto } from "@/types/redemption-analytics-advanced.types";
import { toast } from "sonner";

// ─── Fixtures ─────────────────────────────────────────────────────────────────

const MOCK_DATA_POINTS_SINGLE_CURRENCY: LiabilityDataPointDto[] = [
  { periodDate: "2026-06-01", currencyId: "POINTS", totalUnredeemedBalance: "1200.50" },
  { periodDate: "2026-06-02", currencyId: "POINTS", totalUnredeemedBalance: "1150.00" },
];

const MOCK_DATA_POINTS_TWO_CURRENCIES: LiabilityDataPointDto[] = [
  { periodDate: "2026-06-01", currencyId: "POINTS", totalUnredeemedBalance: "1200.50" },
  { periodDate: "2026-06-01", currencyId: "CASH", totalUnredeemedBalance: "500.00" },
  { periodDate: "2026-06-02", currencyId: "POINTS", totalUnredeemedBalance: "1150.00" },
  { periodDate: "2026-06-02", currencyId: "CASH", totalUnredeemedBalance: "480.00" },
];

const LAST_REFRESHED_AT = "2026-06-20T06:00:00Z";
const DATE_FROM = "2026-06-01";
const DATE_TO = "2026-06-20";

const baseProps = {
  dataPoints: MOCK_DATA_POINTS_SINGLE_CURRENCY,
  lastRefreshedAt: LAST_REFRESHED_AT,
  isLoading: false,
  isError: false,
  refetch: vi.fn(),
  dateFrom: DATE_FROM,
  dateTo: DATE_TO,
};

// ─── Tests ─────────────────────────────────────────────────────────────────────

describe("LiabilityTrendChart", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Stub URL.createObjectURL / revokeObjectURL to avoid jsdom navigation errors
    global.URL.createObjectURL = vi.fn(() => "blob:mock-url");
    global.URL.revokeObjectURL = vi.fn();
  });

  // ────────────────────────────────────────────────────────────────────────────
  describe("data state — renders chart (AC-6)", () => {
    it("renders one Line per distinct currencyId", () => {
      render(
        <LiabilityTrendChart {...baseProps} dataPoints={MOCK_DATA_POINTS_TWO_CURRENCIES} />,
      );

      expect(screen.getByTestId("line-POINTS")).toBeDefined();
      expect(screen.getByTestId("line-CASH")).toBeDefined();
    });

    it("renders exactly one Line for a single-currency dataset", () => {
      render(<LiabilityTrendChart {...baseProps} />);

      expect(screen.getByTestId("line-POINTS")).toBeDefined();
      expect(screen.queryByTestId("line-CASH")).toBeNull();
    });

    it("renders the LineChart with accessible aria-label", () => {
      render(<LiabilityTrendChart {...baseProps} />);

      const chart = screen.getByTestId("line-chart");
      expect(chart.getAttribute("aria-label")).toBe("Liability Trend chart");
    });

    it("renders 'Data as of' caption (AC-6, FR-08.8)", () => {
      render(<LiabilityTrendChart {...baseProps} />);

      const caption = screen.getByText(/Data as of/i);
      expect(caption).toBeDefined();
      expect(caption.textContent).toContain("UTC");
    });

    it("Export CSV button is present and enabled in data state", () => {
      render(<LiabilityTrendChart {...baseProps} />);

      const btn = screen.getByRole("button", { name: /Export CSV/i });
      expect(btn).toBeDefined();
      expect(btn).not.toBeDisabled();
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  describe("loading state — skeleton placeholder (AC-6)", () => {
    it("renders a loading region with aria-busy when isLoading=true", () => {
      render(
        <LiabilityTrendChart
          {...baseProps}
          dataPoints={undefined}
          lastRefreshedAt={undefined}
          isLoading={true}
        />,
      );

      const status = screen.getByRole("status");
      expect(status.getAttribute("aria-busy")).toBe("true");
      expect(status.getAttribute("aria-label")).toContain("Loading Liability Trend");
    });

    it("does not render the chart in the loading state", () => {
      render(
        <LiabilityTrendChart
          {...baseProps}
          dataPoints={undefined}
          lastRefreshedAt={undefined}
          isLoading={true}
        />,
      );

      expect(screen.queryByTestId("line-chart")).toBeNull();
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  describe("empty state (AC-6)", () => {
    it("renders empty-state copy when dataPoints is an empty array", () => {
      render(<LiabilityTrendChart {...baseProps} dataPoints={[]} />);

      expect(screen.getByText("No data for the selected period")).toBeDefined();
    });

    it("does not render the chart in the empty state", () => {
      render(<LiabilityTrendChart {...baseProps} dataPoints={[]} />);

      expect(screen.queryByTestId("line-chart")).toBeNull();
    });

    it("renders Export CSV button in empty state", () => {
      render(<LiabilityTrendChart {...baseProps} dataPoints={[]} />);

      expect(screen.getByRole("button", { name: /Export CSV/i })).toBeDefined();
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  describe("error state — inline error + Retry button (AC-6)", () => {
    it("renders the error message when isError=true", () => {
      render(
        <LiabilityTrendChart
          {...baseProps}
          dataPoints={undefined}
          lastRefreshedAt={undefined}
          isError={true}
        />,
      );

      expect(screen.getByRole("alert")).toBeDefined();
      expect(screen.getByText("Unable to load liability trend")).toBeDefined();
    });

    it("renders a Retry button in the error state", () => {
      render(
        <LiabilityTrendChart
          {...baseProps}
          dataPoints={undefined}
          lastRefreshedAt={undefined}
          isError={true}
        />,
      );

      expect(screen.getByRole("button", { name: "Retry" })).toBeDefined();
    });

    it("calls refetch when Retry button is clicked", async () => {
      const refetch = vi.fn();
      const user = userEvent.setup();

      render(
        <LiabilityTrendChart
          {...baseProps}
          dataPoints={undefined}
          lastRefreshedAt={undefined}
          isError={true}
          refetch={refetch}
        />,
      );

      await user.click(screen.getByRole("button", { name: "Retry" }));
      expect(refetch).toHaveBeenCalledTimes(1);
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  describe("Export CSV — success (AC-2, AC-6)", () => {
    it("calls exportLiabilityTrendCsv with correct dateFrom and dateTo", async () => {
      const mockBlob = new Blob(["col1,col2"], { type: "text/csv" });
      vi.mocked(exportLiabilityTrendCsv).mockResolvedValueOnce(mockBlob);

      const user = userEvent.setup();
      render(<LiabilityTrendChart {...baseProps} />);

      await user.click(screen.getByRole("button", { name: /Export CSV/i }));
      // Flush pending promises
      await act(async () => { await Promise.resolve(); });

      expect(exportLiabilityTrendCsv).toHaveBeenCalledWith(DATE_FROM, DATE_TO);
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  // Countdown tests use vi.useFakeTimers() + fireEvent.click() (synchronous)
  // to avoid the deadlock between userEvent async pointer events and fake timers.
  describe("Export CSV — 429 rate limit countdown (AC-4, AC-6)", () => {
    afterEach(() => {
      vi.useRealTimers();
    });

    it("disables the Export button and shows countdown text when 429 is returned", async () => {
      vi.useFakeTimers();
      vi.mocked(exportLiabilityTrendCsv).mockRejectedValueOnce(
        new ExportRateLimitedError(45),
      );

      render(<LiabilityTrendChart {...baseProps} />);

      // fireEvent.click is synchronous; safe to use with fake timers
      fireEvent.click(screen.getByRole("button", { name: /Export CSV/i }));

      // Flush promise microtasks so the rejection handler runs
      await act(async () => { await Promise.resolve(); });

      const btn = screen.getByRole("button", { name: /Retry in/i });
      expect(btn).toBeDisabled();
      expect(btn.textContent).toContain("45");
    });

    it("decrements the countdown every second", async () => {
      vi.useFakeTimers();
      vi.mocked(exportLiabilityTrendCsv).mockRejectedValueOnce(
        new ExportRateLimitedError(45),
      );

      render(<LiabilityTrendChart {...baseProps} />);

      fireEvent.click(screen.getByRole("button", { name: /Export CSV/i }));
      await act(async () => { await Promise.resolve(); });

      // Advance 3 seconds
      await act(async () => {
        vi.advanceTimersByTime(3_000);
      });

      const btn = screen.getByRole("button", { name: /Retry in/i });
      expect(btn.textContent).toContain("42");
    });

    it("re-enables the Export button when countdown reaches 0", async () => {
      vi.useFakeTimers();
      vi.mocked(exportLiabilityTrendCsv).mockRejectedValueOnce(
        new ExportRateLimitedError(2),
      );

      render(<LiabilityTrendChart {...baseProps} />);

      fireEvent.click(screen.getByRole("button", { name: /Export CSV/i }));
      await act(async () => { await Promise.resolve(); });

      // Countdown expires after 2 ticks
      await act(async () => {
        vi.advanceTimersByTime(2_000);
      });

      const btn = screen.getByRole("button", { name: /Export CSV/i });
      expect(btn).not.toBeDisabled();
    });
  });

  // ────────────────────────────────────────────────────────────────────────────
  describe("Export CSV — non-429 error (AC-6)", () => {
    it("shows a toast and re-enables the Export button on non-429 error", async () => {
      vi.mocked(exportLiabilityTrendCsv).mockRejectedValueOnce(new Error("Network error"));

      const user = userEvent.setup();
      render(<LiabilityTrendChart {...baseProps} />);

      await user.click(screen.getByRole("button", { name: /Export CSV/i }));
      await act(async () => { await Promise.resolve(); });

      expect(toast.error).toHaveBeenCalledWith("Export failed — please try again");
      // Button should be re-enabled (not rate-limited)
      expect(screen.getByRole("button", { name: /Export CSV/i })).not.toBeDisabled();
    });
  });
});
