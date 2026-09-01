// shape: contracts/models/redemption-advanced-analytics.md → RedemptionTrendResponse
// Covers: AC-4 (story US-04)
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// ─── Mock recharts to avoid canvas / ResizeObserver errors in jsdom ───────────
// The mock replaces every recharts component with a trivial div/span so tests
// stay fast and avoid "ReferenceError: ResizeObserver is not defined".
vi.mock("recharts", () => {
  const MockResponsiveContainer = ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  );
  const MockLineChart = ({ children, "aria-label": ariaLabel }: { children: React.ReactNode; "aria-label"?: string }) => (
    <div data-testid="line-chart" aria-label={ariaLabel}>{children}</div>
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

import { RedemptionTrendChart } from "@/components/analytics/advanced/RedemptionTrendChart";
import type { TrendDataPointDto } from "@/types/redemption-analytics-advanced.types";

// ─── Fixtures ─────────────────────────────────────────────────────────────────
// shape: contracts/models/redemption-advanced-analytics.md → TrendDataPointDto

const MOCK_DATA_POINTS_SINGLE_CURRENCY: TrendDataPointDto[] = [
  {
    periodDate: "2026-05-21",
    currencyId: "POINTS",
    redeemedCount: 10,
    redemptionRate: 0.10,
  },
  {
    periodDate: "2026-05-22",
    currencyId: "POINTS",
    redeemedCount: 15,
    redemptionRate: 0.15,
  },
];

const MOCK_DATA_POINTS_TWO_CURRENCIES: TrendDataPointDto[] = [
  {
    periodDate: "2026-05-21",
    currencyId: "POINTS",
    redeemedCount: 10,
    redemptionRate: 0.10,
  },
  {
    periodDate: "2026-05-21",
    currencyId: "CASH",
    redeemedCount: 5,
    redemptionRate: 0.05,
  },
  {
    periodDate: "2026-05-22",
    currencyId: "POINTS",
    redeemedCount: 15,
    redemptionRate: 0.15,
  },
  {
    periodDate: "2026-05-22",
    currencyId: "CASH",
    redeemedCount: 8,
    redemptionRate: 0.08,
  },
];

const LAST_REFRESHED_AT = "2026-06-20T06:00:00Z";

// ─── Tests ─────────────────────────────────────────────────────────────────────

describe("RedemptionTrendChart", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("data state — renders chart with mocked data (AC-4)", () => {
    it("renders one Line per distinct currencyId", () => {
      render(
        <RedemptionTrendChart
          dataPoints={MOCK_DATA_POINTS_TWO_CURRENCIES}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      // Two distinct currencies → two Line components (mocked as data-testid="line-{key}")
      expect(screen.getByTestId("line-POINTS")).toBeDefined();
      expect(screen.getByTestId("line-CASH")).toBeDefined();
    });

    it("renders exactly 1 Line for a single currency dataset", () => {
      render(
        <RedemptionTrendChart
          dataPoints={MOCK_DATA_POINTS_SINGLE_CURRENCY}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByTestId("line-POINTS")).toBeDefined();
      // CASH line should NOT be present
      expect(screen.queryByTestId("line-CASH")).toBeNull();
    });

    it("renders the LineChart (mocked) with accessible aria-label", () => {
      render(
        <RedemptionTrendChart
          dataPoints={MOCK_DATA_POINTS_SINGLE_CURRENCY}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      const chart = screen.getByTestId("line-chart");
      expect(chart.getAttribute("aria-label")).toBe("Redemption Rate Trend chart");
    });

    it("renders 'Data as of' caption below the chart (AC-4)", () => {
      render(
        <RedemptionTrendChart
          dataPoints={MOCK_DATA_POINTS_SINGLE_CURRENCY}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      const caption = screen.getByText(/Data as of/i);
      expect(caption).toBeDefined();
      expect(caption.textContent).toContain("UTC");
    });
  });

  describe("loading state — skeleton placeholder (AC-4)", () => {
    it("renders a loading region with aria-busy when isLoading=true", () => {
      render(
        <RedemptionTrendChart
          dataPoints={undefined}
          lastRefreshedAt={undefined}
          isLoading={true}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      const status = screen.getByRole("status");
      expect(status.getAttribute("aria-busy")).toBe("true");
      expect(status.getAttribute("aria-label")).toContain("Loading Redemption Rate Trend");
    });

    it("does not render the chart in the loading state", () => {
      render(
        <RedemptionTrendChart
          dataPoints={undefined}
          lastRefreshedAt={undefined}
          isLoading={true}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.queryByTestId("line-chart")).toBeNull();
    });
  });

  describe("empty state (AC-4)", () => {
    it("renders empty-state copy when dataPoints is an empty array", () => {
      render(
        <RedemptionTrendChart
          dataPoints={[]}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByText("No data for the selected period")).toBeDefined();
    });

    it("does not render the chart in the empty state", () => {
      render(
        <RedemptionTrendChart
          dataPoints={[]}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.queryByTestId("line-chart")).toBeNull();
    });
  });

  describe("error state — inline error + Retry button (AC-4)", () => {
    it("renders the error message when isError=true", () => {
      render(
        <RedemptionTrendChart
          dataPoints={undefined}
          lastRefreshedAt={undefined}
          isLoading={false}
          isError={true}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("alert")).toBeDefined();
      expect(screen.getByText("Unable to load redemption rate trend")).toBeDefined();
    });

    it("renders a Retry button in the error state", () => {
      render(
        <RedemptionTrendChart
          dataPoints={undefined}
          lastRefreshedAt={undefined}
          isLoading={false}
          isError={true}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("button", { name: "Retry" })).toBeDefined();
    });

    it("calls refetch when Retry button is clicked", async () => {
      const refetch = vi.fn();
      const user = userEvent.setup();

      render(
        <RedemptionTrendChart
          dataPoints={undefined}
          lastRefreshedAt={undefined}
          isLoading={false}
          isError={true}
          refetch={refetch}
        />,
      );

      await user.click(screen.getByRole("button", { name: "Retry" }));
      expect(refetch).toHaveBeenCalledTimes(1);
    });
  });
});
