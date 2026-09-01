import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TransactionHistoryTable } from "@/components/redemption-history/TransactionHistoryTable";
import type { RedemptionRequestResponse } from "@/types/redemption-history/redemption-history.types";

vi.mock("@/config/currencies", () => ({
  getCurrency: vi.fn(() => ({
    format: (v: string) => `$${parseFloat(v).toLocaleString()}`,
    rewardFormat: (v: string) => `${parseFloat(v).toLocaleString()} pts`,
  })),
}));

const MOCK_ROW: RedemptionRequestResponse = {
  id: "tx-001",
  status: "COMPLETED",
  amount: "150.00",
  currencyId: "points",
  catalogItemId: "item-001",
  catalogItemName: "Amazon Gift Card",
  processingMode: "INSTANT",
  submittedAt: "2026-06-01T10:00:00Z",
  completedAt: "2026-06-01T10:05:00Z",
  estimatedDelivery: "Instant",
  createdAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-01T10:05:00Z",
};

describe("TransactionHistoryTable", () => {
  it("renders rows from data", () => {
    render(
      <TransactionHistoryTable data={[MOCK_ROW]} isLoading={false} onRowClick={vi.fn()} />,
    );
    expect(screen.getByText("Amazon Gift Card")).toBeDefined();
    expect(screen.getAllByText(/Completed/i).length).toBeGreaterThan(0);
  });

  it("shows skeleton on isLoading", () => {
    const { container } = render(
      <TransactionHistoryTable data={[]} isLoading={true} onRowClick={vi.fn()} />,
    );
    const skeletons = container.querySelectorAll("[class*='animate'], [class*='skeleton']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("shows 'No transactions yet' empty state when no filters", () => {
    render(
      <TransactionHistoryTable data={[]} isLoading={false} onRowClick={vi.fn()} hasActiveFilters={false} />,
    );
    expect(screen.getByText("No transactions yet")).toBeDefined();
  });

  it("shows filter-mismatch empty state when filters active", () => {
    render(
      <TransactionHistoryTable data={[]} isLoading={false} onRowClick={vi.fn()} hasActiveFilters={true} />,
    );
    expect(screen.getByText("No transactions match your filters")).toBeDefined();
  });

  it("calls onRowClick with the correct id on row click", async () => {
    const onRowClick = vi.fn();
    const user = userEvent.setup();

    render(
      <TransactionHistoryTable data={[MOCK_ROW]} isLoading={false} onRowClick={onRowClick} />,
    );

    await user.click(screen.getByText("Amazon Gift Card"));
    expect(onRowClick).toHaveBeenCalledWith("tx-001");
  });
});
