import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";

// --- Mocks ---

vi.mock("@/hooks/useExpiringSoon", () => ({
  useExpiringSoon: vi.fn(),
}));

vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: () => ({ can: () => true, canAny: () => true, canAll: () => true, permissions: new Set() }),
}));

// --- Imports ---

import { useExpiringSoon } from "@/hooks/useExpiringSoon";
import { ExpiringSoonPreviewCard } from "@/components/balanceExpiration/ExpiringSoonPreviewCard";
import type { ExpiringBalancePreviewResponse } from "@/types/balanceExpiration.types";

const mockUseExpiringSoon = vi.mocked(useExpiringSoon);

// shape: contracts/models/balance-breakage-report.md (ExpiringBalancePreviewResponse)
const PREVIEW_DATA: ExpiringBalancePreviewResponse[] = [
  {
    currencyId: "points",
    currencyDisplayName: "Points",
    scheduledExpiryDate: "2026-09-01",
    affectedWalletCount: 12,
    totalAmountAtRisk: "3400.00",
  },
];

beforeEach(() => {
  mockUseExpiringSoon.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useExpiringSoon>);
});

describe("ExpiringSoonPreviewCard", () => {
  it("renders loading skeleton when isLoading=true", () => {
    mockUseExpiringSoon.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useExpiringSoon>);

    render(<ExpiringSoonPreviewCard />);

    // role="status" with aria-busy on the loading container
    const statusEls = screen.getAllByRole("status");
    expect(statusEls.length).toBeGreaterThan(0);
  });

  it("renders error state with Try again button when isError=true", () => {
    mockUseExpiringSoon.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useExpiringSoon>);

    render(<ExpiringSoonPreviewCard />);

    expect(screen.getByText(/could not load/i)).toBeDefined();
    expect(screen.getByRole("button", { name: /try again/i })).toBeDefined();
  });

  it("renders empty state when data is empty array", () => {
    mockUseExpiringSoon.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useExpiringSoon>);

    render(<ExpiringSoonPreviewCard />);

    expect(screen.getByText(/no balances approaching expiry/i)).toBeDefined();
  });

  it("renders affectedWalletCount and currency label when data is present (AC-7)", () => {
    mockUseExpiringSoon.mockReturnValue({
      data: PREVIEW_DATA,
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useExpiringSoon>);

    render(<ExpiringSoonPreviewCard />);

    // getCurrency('points').label = 'Points'
    expect(screen.getByText(/points/i)).toBeDefined();
    // Wallet count from spec scenario: 12
    expect(screen.getByText(/12 wallets/i)).toBeDefined();
  });
});
