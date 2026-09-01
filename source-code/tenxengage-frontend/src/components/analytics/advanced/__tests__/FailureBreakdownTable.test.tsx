// shape: contracts/models/redemption-advanced-analytics.md → FailureBreakdownResponse
// Covers: AC-2, AC-4 (story US-07)
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FailureBreakdownTable } from "@/components/analytics/advanced/FailureBreakdownTable";
import type { FailureModeDto } from "@/types/redemption-analytics-advanced.types";

// ─── Fixtures ─────────────────────────────────────────────────────────────────
// failureRate from BE is a percentage (0–100) per contracts/models/redemption-advanced-analytics.md.
// Same range as ItemRedemptionDto.redemptionRate — no × 100 conversion needed.
// Note: the story E2E mock uses 0.35/0.12 (0–1 range) which conflicts with the contract;
// the contract is the ground truth — the component renders failureRate directly as a
// percentage string, e.g. 35.0 → "35.0%".

const MOCK_FAILURE_MODES: FailureModeDto[] = [
  {
    processingMode: "MANUAL",
    catalogItemId: "00000000-0000-0000-0000-000000000001",
    catalogItemName: "Gold Ring",
    currencyId: "POINTS",
    failedCount: 30,
    cancelledCount: 5,
    totalCount: 100,
    failureRate: 35.0,
  },
  {
    processingMode: "AUTOMATED",
    catalogItemId: "00000000-0000-0000-0000-000000000002",
    catalogItemName: "Silver Coin",
    currencyId: "POINTS",
    failedCount: 10,
    cancelledCount: 2,
    totalCount: 100,
    failureRate: 12.0,
  },
];

const LAST_REFRESHED_AT = "2026-06-20T06:00:00Z";

// ─── Tests ─────────────────────────────────────────────────────────────────────

describe("FailureBreakdownTable", () => {
  describe("data state — renders columns with mock data (AC-2, AC-4)", () => {
    it("renders all column headers", () => {
      render(
        <FailureBreakdownTable
          failureModes={MOCK_FAILURE_MODES}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("columnheader", { name: "Processing Mode" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Item Name" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Currency" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Failed" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Cancelled" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Total" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Failure Rate (%)" })).toBeDefined();
    });

    it("renders rows in the order returned by the server (sorted by failure rate desc)", () => {
      render(
        <FailureBreakdownTable
          failureModes={MOCK_FAILURE_MODES}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      const rows = screen.getAllByRole("row");
      // rows[0] = header, rows[1] = Gold Ring (0.35), rows[2] = Silver Coin (0.12)
      expect(rows).toHaveLength(3);
      expect(rows[1]!.textContent).toContain("Gold Ring");
      expect(rows[2]!.textContent).toContain("Silver Coin");
    });

    it("renders 'Manual' display value for MANUAL processingMode (AC-4)", () => {
      render(
        <FailureBreakdownTable
          failureModes={MOCK_FAILURE_MODES}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByText("Manual")).toBeDefined();
    });

    it("renders 'Automated' display value for AUTOMATED processingMode (AC-4)", () => {
      render(
        <FailureBreakdownTable
          failureModes={MOCK_FAILURE_MODES}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByText("Automated")).toBeDefined();
    });

    it("renders failure rate as a percentage with 1 decimal place (AC-4)", () => {
      render(
        <FailureBreakdownTable
          failureModes={MOCK_FAILURE_MODES}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      // failureRate 35.0 → "35.0%"; 12.0 → "12.0%" (contract: 0–100 range, no × 100 needed)
      expect(screen.getByText("35.0%")).toBeDefined();
      expect(screen.getByText("12.0%")).toBeDefined();
    });

    it("renders 'Data as of' caption with UTC (AC-4, FR-08.8)", () => {
      render(
        <FailureBreakdownTable
          failureModes={MOCK_FAILURE_MODES}
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

    it("renders the table with aria-label for accessibility", () => {
      render(
        <FailureBreakdownTable
          failureModes={MOCK_FAILURE_MODES}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("table", { name: "Failure Breakdown" })).toBeDefined();
    });
  });

  describe("loading state — skeleton rows (AC-4)", () => {
    it("renders a loading region with aria-busy when isLoading=true", () => {
      render(
        <FailureBreakdownTable
          failureModes={undefined}
          lastRefreshedAt={undefined}
          isLoading={true}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      const status = screen.getByRole("status");
      expect(status.getAttribute("aria-busy")).toBe("true");
      expect(status.getAttribute("aria-label")).toContain("Loading Failure Breakdown");
    });

    it("renders a skeleton table structure (not data rows) when loading", () => {
      render(
        <FailureBreakdownTable
          failureModes={undefined}
          lastRefreshedAt={undefined}
          isLoading={true}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("table")).toBeDefined();
      // No failure mode data should be rendered in the skeleton
      expect(screen.queryByText("Gold Ring")).toBeNull();
    });
  });

  describe("empty state (AC-4)", () => {
    it("renders empty-state copy when failureModes is an empty array", () => {
      render(
        <FailureBreakdownTable
          failureModes={[]}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByText("No data for the selected period")).toBeDefined();
    });

    it("does not render a table when failureModes is empty", () => {
      render(
        <FailureBreakdownTable
          failureModes={[]}
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
        <FailureBreakdownTable
          failureModes={undefined}
          lastRefreshedAt={undefined}
          isLoading={false}
          isError={true}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("alert")).toBeDefined();
      expect(screen.getByText("Unable to load failure breakdown")).toBeDefined();
    });

    it("renders a Retry button in the error state", () => {
      render(
        <FailureBreakdownTable
          failureModes={undefined}
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
        <FailureBreakdownTable
          failureModes={undefined}
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
