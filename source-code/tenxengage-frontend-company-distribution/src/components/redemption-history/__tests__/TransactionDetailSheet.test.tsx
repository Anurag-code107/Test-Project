import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/hooks/redemption-history/useRedemptionDetail", () => ({
  useRedemptionDetail: vi.fn(),
}));

vi.mock("@/config/currencies", () => ({
  getCurrency: vi.fn(() => ({
    format: (v: string) => `$${parseFloat(v).toLocaleString()}`,
    rewardFormat: (v: string) => `${parseFloat(v).toLocaleString()} pts`,
  })),
}));

import { useRedemptionDetail } from "@/hooks/redemption-history/useRedemptionDetail";
import { TransactionDetailSheet } from "@/components/redemption-history/TransactionDetailSheet";

const mockUseRedemptionDetail = vi.mocked(useRedemptionDetail);

const BASE_DETAIL = {
  id: "tx-001",
  status: "COMPLETED" as const,
  amount: "150.00",
  currencyId: "points",
  catalogItemId: "item-001",
  catalogItemName: "Amazon Gift Card",
  processingMode: "INSTANT" as const,
  category: "NON_CASH" as const,
  walletType: "INDIVIDUAL" as const,
  vendorReferenceId: "VND-ABC123",
  submittedAt: "2026-06-01T10:00:00Z",
  completedAt: "2026-06-01T10:05:00Z",
  estimatedDelivery: "Instant",
  linkedReturnId: undefined,
  createdAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-01T10:05:00Z",
};

function makeHook(overrides: Partial<ReturnType<typeof useRedemptionDetail>> = {}) {
  return {
    data: BASE_DETAIL,
    isLoading: false,
    isError: false,
    ...overrides,
  } as unknown as ReturnType<typeof useRedemptionDetail>;
}

describe("TransactionDetailSheet", () => {
  it("renders field rows when open with COMPLETED status", () => {
    mockUseRedemptionDetail.mockReturnValue(makeHook());

    render(
      <TransactionDetailSheet redemptionId="tx-001" open={true} onClose={vi.fn()} />,
    );

    expect(screen.getByText("Transaction detail")).toBeDefined();
    expect(screen.getByText("Amazon Gift Card")).toBeDefined();
    expect(screen.getByText("VND-ABC123")).toBeDefined();
  });

  it("COMPLETED status shows vendorReferenceId, not failureReason", () => {
    mockUseRedemptionDetail.mockReturnValue(makeHook());

    render(
      <TransactionDetailSheet redemptionId="tx-001" open={true} onClose={vi.fn()} />,
    );

    expect(screen.getByText("VND-ABC123")).toBeDefined();
    expect(screen.queryByText("Failure reason")).toBeNull();
  });

  it("FAILED status shows failureReason, not vendorReferenceId", () => {
    mockUseRedemptionDetail.mockReturnValue(makeHook({
      data: {
        ...BASE_DETAIL,
        status: "FAILED",
        vendorReferenceId: undefined,
        failureReason: "Vendor processing failed",
        completedAt: "2026-06-01T10:10:00Z",
      },
    }));

    render(
      <TransactionDetailSheet redemptionId="tx-001" open={true} onClose={vi.fn()} />,
    );

    expect(screen.getByText("Vendor processing failed")).toBeDefined();
    expect(screen.queryByText("Vendor reference")).toBeNull();
  });

  it("null linkedReturnId omits the Linked return row", () => {
    mockUseRedemptionDetail.mockReturnValue(makeHook({ data: { ...BASE_DETAIL, linkedReturnId: undefined } }));

    render(
      <TransactionDetailSheet redemptionId="tx-001" open={true} onClose={vi.fn()} />,
    );

    expect(screen.queryByText("Linked return")).toBeNull();
  });

  it("non-null linkedReturnId shows Linked return row", () => {
    mockUseRedemptionDetail.mockReturnValue(makeHook({
      data: { ...BASE_DETAIL, linkedReturnId: "return-uuid-001" },
    }));

    render(
      <TransactionDetailSheet redemptionId="tx-001" open={true} onClose={vi.fn()} />,
    );

    expect(screen.getByText("Linked return")).toBeDefined();
    expect(screen.getByText("return-uuid-001")).toBeDefined();
  });

  it("shows skeleton when loading and hides field rows", () => {
    mockUseRedemptionDetail.mockReturnValue(makeHook({ data: undefined, isLoading: true }));

    render(
      <TransactionDetailSheet redemptionId="tx-001" open={true} onClose={vi.fn()} />,
    );

    // Transaction detail title is shown; item name is not rendered while loading
    expect(screen.getByText("Transaction detail")).toBeDefined();
    expect(screen.queryByText("Amazon Gift Card")).toBeNull();
  });

  it("calls onClose when X is clicked", async () => {
    mockUseRedemptionDetail.mockReturnValue(makeHook());
    const onClose = vi.fn();
    const user = userEvent.setup();

    render(
      <TransactionDetailSheet redemptionId="tx-001" open={true} onClose={onClose} />,
    );

    await user.click(screen.getByRole("button", { name: /close transaction detail/i }));
    expect(onClose).toHaveBeenCalled();
  });
});
