// shape: contracts/models/redemption-advanced-analytics.md → ItemBreakdownResponse
// Covers: AC-5, AC-6 (story US-01)
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ItemBreakdownTable } from "@/components/analytics/advanced/ItemBreakdownTable";
import type { ItemRedemptionDto } from "@/types/redemption-analytics-advanced.types";

// ─── Fixtures ─────────────────────────────────────────────────────────────────

const MOCK_ITEMS: ItemRedemptionDto[] = [
  {
    catalogItemId: "00000000-0000-0000-0000-000000000001",
    catalogItemName: "Gold Ring",
    currencyId: "POINTS",
    totalRedeemedCount: 150,
    totalRedeemedAmount: "7500.00",
    redemptionRate: 75.5,
  },
  {
    catalogItemId: "00000000-0000-0000-0000-000000000002",
    catalogItemName: "Silver Coin",
    currencyId: "POINTS",
    totalRedeemedCount: 75,
    totalRedeemedAmount: "3750.00",
    redemptionRate: 60.0,
  },
];

const LAST_REFRESHED_AT = "2026-06-20T06:00:00Z";

// ─── Tests ─────────────────────────────────────────────────────────────────────

describe("ItemBreakdownTable", () => {
  describe("data state — renders columns with mock data (AC-5)", () => {
    it("renders all column headers", () => {
      render(
        <ItemBreakdownTable
          items={MOCK_ITEMS}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("columnheader", { name: "Item Name" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Currency" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Redeemed Count" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Amount" })).toBeDefined();
      expect(screen.getByRole("columnheader", { name: "Rate (%)" })).toBeDefined();
    });

    it("renders item rows in the order returned by the server", () => {
      render(
        <ItemBreakdownTable
          items={MOCK_ITEMS}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      const rows = screen.getAllByRole("row");
      // rows[0] = header, rows[1] = Gold Ring, rows[2] = Silver Coin
      expect(rows).toHaveLength(3);
      expect(rows[1]!.textContent).toContain("Gold Ring");
      expect(rows[2]!.textContent).toContain("Silver Coin");
    });

    it("renders redemption rate formatted as percentage", () => {
      render(
        <ItemBreakdownTable
          items={MOCK_ITEMS}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByText("75.50%")).toBeDefined();
      expect(screen.getByText("60.00%")).toBeDefined();
    });

    it("renders 'Data as of' caption below the table (AC-5)", () => {
      render(
        <ItemBreakdownTable
          items={MOCK_ITEMS}
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

  describe("loading state — skeleton rows (AC-6)", () => {
    it("renders a loading region with aria-busy when isLoading=true", () => {
      render(
        <ItemBreakdownTable
          items={undefined}
          lastRefreshedAt={undefined}
          isLoading={true}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      const status = screen.getByRole("status");
      expect(status.getAttribute("aria-busy")).toBe("true");
      expect(status.getAttribute("aria-label")).toContain("Loading Item Breakdown");
    });

    it("renders a skeleton table structure (not data rows) when loading", () => {
      render(
        <ItemBreakdownTable
          items={undefined}
          lastRefreshedAt={undefined}
          isLoading={true}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      // The loading state renders a shadcn Table with skeleton cells — a <table> is present
      // but contains no real item data (no rowgroup body rows with text content).
      expect(screen.getByRole("table")).toBeDefined();
      // No item name text should be rendered in the skeleton
      expect(screen.queryByText("Gold Ring")).toBeNull();
    });
  });

  describe("empty state (AC-6)", () => {
    it("renders empty-state copy when items is an empty array", () => {
      render(
        <ItemBreakdownTable
          items={[]}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByText("No data for the selected period")).toBeDefined();
    });

    it("does not render a table when items is empty", () => {
      render(
        <ItemBreakdownTable
          items={[]}
          lastRefreshedAt={LAST_REFRESHED_AT}
          isLoading={false}
          isError={false}
          refetch={vi.fn()}
        />,
      );

      expect(screen.queryByRole("table")).toBeNull();
    });
  });

  describe("error state — inline error + Retry button (AC-6)", () => {
    it("renders the error message when isError=true", () => {
      render(
        <ItemBreakdownTable
          items={undefined}
          lastRefreshedAt={undefined}
          isLoading={false}
          isError={true}
          refetch={vi.fn()}
        />,
      );

      expect(screen.getByRole("alert")).toBeDefined();
      expect(screen.getByText("Unable to load item breakdown")).toBeDefined();
    });

    it("renders a Retry button in the error state", () => {
      render(
        <ItemBreakdownTable
          items={undefined}
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
        <ItemBreakdownTable
          items={undefined}
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
