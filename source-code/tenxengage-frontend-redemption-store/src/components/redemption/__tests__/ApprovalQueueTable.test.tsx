import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApprovalQueueTable } from "@/components/redemption/ApprovalQueueTable";
import type { ApprovalQueueItem, PaginationMeta } from "@/types/redemption/redemption.types";

vi.mock("@/config/currencies", () => ({
  getCurrency: vi.fn(() => ({
    format: (v: string) => `$${parseFloat(v).toLocaleString()}`,
  })),
}));

const MOCK_ITEM: ApprovalQueueItem = {
  id: "00000000-0000-0000-0000-000000000001",
  requestingUserDisplayName: "Test User",
  catalogItemId: "00000000-0000-0000-0000-000000000010",
  catalogItemName: "Amazon Gift Card",
  currencyId: "points",
  amount: "150.00",
  walletType: "INDIVIDUAL",
  submittedAt: "2026-05-28T10:00:00Z",
  createdAt: "2026-05-28T10:00:00Z",
  updatedAt: "2026-05-28T10:00:00Z",
};

const EMPTY_PAGINATION: PaginationMeta = {
  page: 0,
  pageSize: 20,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
  hasPrevious: false,
};

const SINGLE_PAGE_PAGINATION: PaginationMeta = {
  page: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};

describe("ApprovalQueueTable", () => {
  it("renders table column headers when items are present", () => {
    render(
      <ApprovalQueueTable
        items={[MOCK_ITEM]}
        pagination={SINGLE_PAGE_PAGINATION}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        isLoading={false}
      />,
    );

    expect(screen.getByText("Requester")).toBeDefined();
    expect(screen.getByText("Item")).toBeDefined();
    expect(screen.getByText("Currency")).toBeDefined();
    expect(screen.getByText("Amount")).toBeDefined();
    expect(screen.getByText("Wallet")).toBeDefined();
    expect(screen.getByText("Submitted")).toBeDefined();
    expect(screen.getByText("Actions")).toBeDefined();
  });

  it("renders item data in table rows", () => {
    render(
      <ApprovalQueueTable
        items={[MOCK_ITEM]}
        pagination={SINGLE_PAGE_PAGINATION}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        isLoading={false}
      />,
    );

    expect(screen.getByText("Test User")).toBeDefined();
    expect(screen.getByText("Amazon Gift Card")).toBeDefined();
    expect(screen.getByText("points")).toBeDefined();
  });

  it("shows skeleton rows when isLoading is true", () => {
    const { container } = render(
      <ApprovalQueueTable
        items={[]}
        pagination={EMPTY_PAGINATION}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        isLoading={true}
      />,
    );

    // Skeleton rows rendered — 5 rows × 7 cells = 35 skeleton divs
    const skeletons = container.querySelectorAll("[class*='animate-pulse'], [class*='skeleton']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("shows empty state when items array is empty and not loading", () => {
    render(
      <ApprovalQueueTable
        items={[]}
        pagination={EMPTY_PAGINATION}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        isLoading={false}
      />,
    );

    expect(screen.getByText("No pending redemptions")).toBeDefined();
    expect(screen.getByText("No redemption requests are pending approval.")).toBeDefined();
  });

  it("calls onApprove with the correct id when Approve is clicked", async () => {
    const onApprove = vi.fn();
    const user = userEvent.setup();

    render(
      <ApprovalQueueTable
        items={[MOCK_ITEM]}
        pagination={SINGLE_PAGE_PAGINATION}
        onApprove={onApprove}
        onReject={vi.fn()}
        isLoading={false}
      />,
    );

    await user.click(screen.getByRole("button", { name: /approve redemption for test user/i }));
    expect(onApprove).toHaveBeenCalledWith(MOCK_ITEM.id);
  });

  it("calls onReject with the correct id when Reject is clicked", async () => {
    const onReject = vi.fn();
    const user = userEvent.setup();

    render(
      <ApprovalQueueTable
        items={[MOCK_ITEM]}
        pagination={SINGLE_PAGE_PAGINATION}
        onApprove={vi.fn()}
        onReject={onReject}
        isLoading={false}
      />,
    );

    await user.click(screen.getByRole("button", { name: /reject redemption for test user/i }));
    expect(onReject).toHaveBeenCalledWith(MOCK_ITEM.id);
  });
});
