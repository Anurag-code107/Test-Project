// shape: contracts/models/redemption-advanced-analytics.md → SegmentBreakdownResponse
// Covers: AC-3, AC-4 (story US-02)
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SegmentBreakdownTable } from "@/components/analytics/advanced/SegmentBreakdownTable";
import type { SegmentRedemptionDto } from "@/types/redemption-analytics-advanced.types";

// ─── Fixtures ─────────────────────────────────────────────────────────────────
// shape: contracts/models/redemption-advanced-analytics.md → SegmentRedemptionDto

const MOCK_SEGMENTS: SegmentRedemptionDto[] = [
  {
    region: "APAC",
    role: "MANAGER",
    currencyId: "POINTS",
    totalRedeemedCount: 42,
    totalRedeemedAmount: "2100.00",
    redemptionRate: 35.0,
  },
  {
    region: null,
    role: null,
    currencyId: "CASH",
    totalRedeemedCount: 10,
    totalRedeemedAmount: "500.00",
    redemptionRate: 12.5,
  },
];

const LAST_REFRESHED_AT = "2026-06-20T06:00:00Z";

// ─── Tests ─────────────────────────────────────────────────────────────────────

describe("SegmentBreakdownTable", () => {
  describe("data state — renders all columns with mock data (AC-4)", () => {
    it("renders all column headers per AC-4", () => {
      render(
        <SegmentBreakdownTable
          segments={MOCK_SEGMENTS}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("columnheader", { name: "Region" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Role" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Currency" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Redeemed Count" })).toBeDefined();
      expect(
        screen.getByRole("columnheader", { name: "Redemption Rate (%)" }),
      ).toBeDefined();
    });

    it("renders segment rows in the order returned by the server", () => {
      render(
        <SegmentBreakdownTable
          segments={MOCK_SEGMENTS}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      const rows = screen.getAllByRole("row");
      // rows[0] = header, rows[1] = APAC/MANAGER, rows[2] = null/null
      expect(rows).toHaveLength(3);
      expect(rows[1]!.textContent).toContain("APAC");
      expect(rows[1]!.textContent).toContain("MANAGER");
    });

    it("renders redemption rate formatted as percentage", () => {
      render(
        <SegmentBreakdownTable
          segments={MOCK_SEGMENTS}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByText("35.00%")).toBeDefined();
      expect(screen.getByText("12.50%")).toBeDefined();
    });

    it("renders 'Data as of' caption below the table (AC-4, FR-08.8)", () => {
      render(
        <SegmentBreakdownTable
          segments={MOCK_SEGMENTS}
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

  describe("null region / role render as '—' (AC-4)", () => {
    it("renders '—' for null region and null role", () => {
      render(
        <SegmentBreakdownTable
          segments={MOCK_SEGMENTS}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      // MOCK_SEGMENTS[1] has region=null, role=null → both should render "—"
      const dashCells = screen.getAllByText("—");
      // Expect at least 2 em-dashes (region and role for the null-row)
      expect(dashCells.length).toBeGreaterThanOrEqual(2);
    });

    it("does not render empty string or 'null' text for null region/role", () => {
      render(
        <SegmentBreakdownTable
          segments={[{ region: null, role: null, currencyId: "POINTS", totalRedeemedCount: 5, totalRedeemedAmount: "250.00", redemptionRate: 8.0 }]}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.queryByText("null")).toBeNull();
      // Both region and role cells show "—"
      const dashes = screen.getAllByText("—");
      expect(dashes.length).toBeGreaterThanOrEqual(2);
    });
  });

  describe("loading state — skeleton rows (AC-4)", () => {
    it("renders a loading region with aria-busy when isLoading=true", () => {
      render(
        <SegmentBreakdownTable
          segments={undefined}
          lastRefreshedAt={undefined}
          isLoading={true}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      const status = screen.getByRole("status");
      expect(status.getAttribute("aria-busy")).toBe("true");
      expect(status.getAttribute("aria-label")).toContain("Loading Segment Breakdown");
    });

    it("renders a skeleton table structure (not data rows) when loading", () => {
      render(
        <SegmentBreakdownTable
          segments={undefined}
          lastRefreshedAt={undefined}
          isLoading={true}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      // A <table> is present but contains no real segment data
      expect(screen.getByRole("table")).toBeDefined();
      // No region data should be rendered
      expect(screen.queryByText("APAC")).toBeNull();
    });
  });

  describe("empty state (AC-4)", () => {
    it("renders empty-state copy when segments is an empty array", () => {
      render(
        <SegmentBreakdownTable
          segments={[]}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByText("No data for the selected period")).toBeDefined();
    });

    it("does not render a table when segments is empty", () => {
      render(
        <SegmentBreakdownTable
          segments={[]}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.queryByRole("table")).toBeNull();
    });
  });

  describe("error state — inline error + Retry button (AC-4)", () => {
    it("renders the error message when isError=true", () => {
      render(
        <SegmentBreakdownTable
          segments={undefined}
          lastRefreshedAt={undefined}
          isLoading={false}
          isError={true}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("alert")).toBeDefined();
      expect(screen.getByText("Unable to load segment breakdown")).toBeDefined();
    });

    it("renders a Retry button in the error state", () => {
      render(
        <SegmentBreakdownTable
          segments={undefined}
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
        <SegmentBreakdownTable
          segments={undefined}
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
