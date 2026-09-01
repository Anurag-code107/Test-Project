import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { RecentWithdrawals } from "@/components/redemption-payout/RecentWithdrawals";
import type {
  WithdrawalHistoryItem,
  WithdrawalHistoryPage,
} from "@/types/redemption-payout/redemption-payout.types";

const { historyMock } = vi.hoisted(() => ({ historyMock: vi.fn() }));

vi.mock("@/hooks/redemption-payout/useRedemptionProfile", () => ({
  useWithdrawals: (enabled: boolean, page: number) => historyMock(enabled, page),
}));

function item(o: Partial<WithdrawalHistoryItem> = {}): WithdrawalHistoryItem {
  return {
    id: crypto.randomUUID(),
    amountGross: 50,
    fee: 0.36,
    amountNet: 49.64,
    currency: "USD",
    destinationType: "CARD",
    destinationLabel: "Visa ••1111",
    status: "Completed",
    createdAt: "2026-07-17T10:00:00Z",
    ...o,
  };
}

function pageOf(items: WithdrawalHistoryItem[], over: Partial<WithdrawalHistoryPage> = {}): WithdrawalHistoryPage {
  return {
    data: items,
    page: 0,
    pageSize: 5,
    totalElements: items.length,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
    ...over,
  };
}

describe("RecentWithdrawals", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    historyMock.mockReturnValue({ data: undefined, isLoading: false });
  });

  it("prompts to enroll when not enrolled", () => {
    render(<RecentWithdrawals enrolled={false} />);
    expect(screen.getByText(/complete your payout profile to see your withdrawals/i)).toBeDefined();
  });

  it("shows the empty state when there is no history", () => {
    historyMock.mockReturnValue({ data: pageOf([]), isLoading: false });
    render(<RecentWithdrawals enrolled={true} />);
    expect(screen.getByText(/no withdrawals yet/i)).toBeDefined();
  });

  it("lists a single page without pagination controls", () => {
    historyMock.mockReturnValue({
      data: pageOf([item({ destinationLabel: "Visa ••1111" }), item({ destinationType: "BANK", destinationLabel: "KOTAK ••8943" })]),
      isLoading: false,
    });
    render(<RecentWithdrawals enrolled={true} />);
    expect(screen.getByText("Visa ••1111")).toBeDefined();
    expect(screen.getByText("KOTAK ••8943")).toBeDefined();
    expect(screen.queryByRole("button", { name: /next/i })).toBeNull();
  });

  it("shows Prev/Next + 'Page X of Y' and advances the page on Next", () => {
    historyMock.mockImplementation((_enabled: boolean, page: number) =>
      page === 0
        ? { data: pageOf([item(), item(), item(), item(), item()], { totalElements: 7, totalPages: 2, hasNext: true }), isLoading: false }
        : { data: pageOf([item(), item()], { page: 1, totalElements: 7, totalPages: 2, hasPrevious: true }), isLoading: false },
    );
    render(<RecentWithdrawals enrolled={true} />);

    expect(screen.getByText(/page 1 of 2/i)).toBeDefined();
    expect(screen.getByRole("button", { name: /previous/i })).toHaveProperty("disabled", true);
    const next = screen.getByRole("button", { name: /next/i });
    expect(next).toHaveProperty("disabled", false);

    fireEvent.click(next);
    // Page advanced → hook requested page 1, and the label reflects it.
    expect(historyMock).toHaveBeenCalledWith(true, 1);
    expect(screen.getByText(/page 2 of 2/i)).toBeDefined();
    expect(screen.getByRole("button", { name: /next/i })).toHaveProperty("disabled", true);
    expect(screen.getByRole("button", { name: /previous/i })).toHaveProperty("disabled", false);
  });
});
