// shape: contracts/models/redemption-advanced-analytics.md -> TimeToFirstRedemptionResponse
// Covers: AC-2, AC-4 (story US-03)
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TimeToFirstRedemptionTable } from "@/components/analytics/advanced/TimeToFirstRedemptionTable";
import type { RegionTimeToRedemptionDto } from "@/types/redemption-analytics-advanced.types";

// --- Fixtures ---
// shape: contracts/models/redemption-advanced-analytics.md -> RegionTimeToRedemptionDto

const MOCK_REGIONS: RegionTimeToRedemptionDto[] = [
  {
    region: "APAC",
    avgHoursToFirstRedemption: 24.5,
    medianHoursToFirstRedemption: 18.0,
    sampleCount: 120,
  },
  {
    region: "EMEA",
    avgHoursToFirstRedemption: null,
    medianHoursToFirstRedemption: null,
    sampleCount: 0,
  },
];

const LAST_REFRESHED_AT = "2026-06-20T06:00:00Z";

// --- Tests ---

describe("TimeToFirstRedemptionTable", () => {
  describe("data state -- renders all columns with mock data (AC-4)", () => {
    it("renders all column headers per AC-4", () => {
      render(
        <TimeToFirstRedemptionTable
          regions={MOCK_REGIONS}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("columnheader", { name: "Region" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Avg Hours" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Median Hours" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Sample Count" })).toBeDefined();
    });

    it("renders numeric avg and median for APAC row (AC-2)", () => {
      render(
        <TimeToFirstRedemptionTable
          regions={MOCK_REGIONS}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByText("24.5")).toBeDefined();
      expect(screen.getByText("18.0")).toBeDefined();
    });

    it("renders 'Data as of' caption below the table (AC-4, FR-08.8)", () => {
      render(
        <TimeToFirstRedemptionTable
          regions={MOCK_REGIONS}
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

  describe("null avg/median renders as 'N/A' for zero-sample rows (AC-2)", () => {
    it("renders 'N/A' for null avgHoursToFirstRedemption (AC-2)", () => {
      render(
        <TimeToFirstRedemptionTable
          regions={MOCK_REGIONS}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      const naCells = screen.getAllByText("N/A");
      expect(naCells.length).toBeGreaterThanOrEqual(2);
    });

    it("does not render '0' or empty string for null avg/median cells", () => {
      render(
        <TimeToFirstRedemptionTable
          regions={[
            {
              region: "EMEA",
              avgHoursToFirstRedemption: null,
              medianHoursToFirstRedemption: null,
              sampleCount: 5,
            },
          ]}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      // Null avg/median cells must not render as "0" -- sampleCount is 5 (non-zero) so
      // no numeric "0" should appear in the table when avg/median are both null
      expect(screen.queryByText("0")).toBeNull();
      const naCells = screen.getAllByText("N/A");
      expect(naCells.length).toBeGreaterThanOrEqual(2);
    });

    it("does not render 'null' text for null region (AC-4)", () => {
      render(
        <TimeToFirstRedemptionTable
          regions={[
            {
              region: null,
              avgHoursToFirstRedemption: null,
              medianHoursToFirstRedemption: null,
              sampleCount: 0,
            },
          ]}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.queryByText("null")).toBeNull();
      expect(screen.getByText("—")).toBeDefined();
    });
  });

  describe("loading state -- skeleton rows (AC-4)", () => {
    it("renders a loading region with aria-busy when isLoading=true", () => {
      render(
        <TimeToFirstRedemptionTable
          regions={undefined}
          lastRefreshedAt={undefined}
          isLoading={true}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      const status = screen.getByRole("status");
      expect(status.getAttribute("aria-busy")).toBe("true");
      expect(status.getAttribute("aria-label")).toContain("Loading Time to First Redemption");
    });

    it("renders a skeleton table structure (not data rows) when loading", () => {
      render(
        <TimeToFirstRedemptionTable
          regions={undefined}
          lastRefreshedAt={undefined}
          isLoading={true}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("table")).toBeDefined();
      expect(screen.queryByText("APAC")).toBeNull();
    });
  });

  describe("empty state (AC-4)", () => {
    it("renders empty-state copy when regions is an empty array", () => {
      render(
        <TimeToFirstRedemptionTable
          regions={[]}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByText("No data for the selected period")).toBeDefined();
    });

    it("does not render a table when regions is empty", () => {
      render(
        <TimeToFirstRedemptionTable
          regions={[]}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.queryByRole("table")).toBeNull();
    });
  });

  describe("error state -- inline error + Retry button (AC-4)", () => {
    it("renders the error message when isError=true", () => {
      render(
        <TimeToFirstRedemptionTable
          regions={undefined}
          lastRefreshedAt={undefined}
          isLoading={false}
          isError={true}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("alert")).toBeDefined();
      expect(screen.getByText("Unable to load time-to-first-redemption data")).toBeDefined();
    });

    it("renders a Retry button in the error state", () => {
      render(
        <TimeToFirstRedemptionTable
          regions={undefined}
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
        <TimeToFirstRedemptionTable
          regions={undefined}
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

