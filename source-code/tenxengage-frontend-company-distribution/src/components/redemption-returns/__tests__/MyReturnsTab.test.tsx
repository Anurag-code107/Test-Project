import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";

// ── Module mocks ──────────────────────────────────────────────────────────────

vi.mock("@/hooks/useMyReturns", () => ({
  useMyReturns: vi.fn(),
}));

vi.mock("@/hooks/useCancelReturn", () => ({
  useCancelReturn: vi.fn(),
}));

vi.mock("@/hooks/useReturn", () => ({
  useReturn: vi.fn(() => ({ data: undefined, isLoading: false, isError: false, refetch: vi.fn() })),
}));

vi.mock("@/hooks/useApproveReturn", () => ({
  useApproveReturn: vi.fn(() => ({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
    isError: false,
    isSuccess: false,
    reset: vi.fn(),
  })),
}));

vi.mock("@/hooks/useRejectReturn", () => ({
  useRejectReturn: vi.fn(() => ({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
    isError: false,
    isSuccess: false,
    reset: vi.fn(),
  })),
}));

vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: () => ({ can: () => true, canAny: () => true, canAll: () => true, permissions: new Set() }),
}));

vi.mock("@/config/currencies", () => ({
  getCurrency: vi.fn(() => ({
    rewardFormat: (v: string) => `${parseFloat(v).toLocaleString()} pts`,
  })),
}));

vi.mock("@/utils/formatters", () => ({
  formatDate: (d: string) => d,
  formatDateTime: (d: string) => d,
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import { useMyReturns } from "@/hooks/useMyReturns";
import { useCancelReturn } from "@/hooks/useCancelReturn";
import { MyReturnsTab } from "@/components/redemption-returns/MyReturnsTab";
import type { ReturnSummaryResponse } from "@/types/redemption-returns.types";

const mockUseMyReturns = vi.mocked(useMyReturns);
const mockUseCancelReturn = vi.mocked(useCancelReturn);

// shape: contracts/models/redemption-return.md
const MOCK_RETURN: ReturnSummaryResponse = {
  id: "return-001",
  redemptionId: "redeem-001",
  catalogItemName: "Amazon Gift Card",
  amount: "150.00",
  currencyId: "points",
  status: "PENDING_APPROVAL",
  createdAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-01T10:00:00Z",
};

function makeCancelMutation(overrides = {}) {
  return {
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
    isError: false,
    isSuccess: false,
    reset: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useCancelReturn>;
}

function makeQuery(overrides = {}) {
  return {
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useMyReturns>;
}

describe("MyReturnsTab", () => {
  it("shows skeleton on isLoading", () => {
    mockUseMyReturns.mockReturnValue(makeQuery({ isLoading: true }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    const { container } = render(<MyReturnsTab  />);

    // role="status" aria-busy is on the loading state container
    expect(container.querySelector('[aria-busy="true"]')).toBeDefined();
  });

  it("shows empty state when data is empty", () => {
    mockUseMyReturns.mockReturnValue(makeQuery({
      data: { data: [], page: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false },
    }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(<MyReturnsTab  />);
    expect(screen.getByText("You have no return requests yet.")).toBeDefined();
  });

  it("renders table rows with data", () => {
    mockUseMyReturns.mockReturnValue(makeQuery({
      data: {
        data: [MOCK_RETURN],
        page: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false,
      },
    }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(<MyReturnsTab  />);
    expect(screen.getByText("Amazon Gift Card")).toBeDefined();
  });

  it("shows Cancel Return button for PENDING_APPROVAL rows", () => {
    mockUseMyReturns.mockReturnValue(makeQuery({
      data: {
        data: [MOCK_RETURN],
        page: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false,
      },
    }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(<MyReturnsTab  />);
    expect(screen.getByRole("button", { name: /cancel return for amazon gift card/i })).toBeDefined();
  });

  // CR-01: Cancel Return must read as an interactive button (bordered/outline), not plain red text.
  it("renders Cancel Return with a bordered button affordance", () => {
    mockUseMyReturns.mockReturnValue(makeQuery({
      data: {
        data: [MOCK_RETURN],
        page: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false,
      },
    }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(<MyReturnsTab  />);
    const btn = screen.getByRole("button", { name: /cancel return for amazon gift card/i });
    // outline variant + destructive-tinted border = clear button affordance (was variant="ghost" red text)
    expect(btn.className).toContain("border-destructive");
    expect(btn.getAttribute("aria-label")).toBe("Cancel Return for Amazon Gift Card");
  });

  it("does NOT show Cancel Return for non-PENDING_APPROVAL rows", () => {
    mockUseMyReturns.mockReturnValue(makeQuery({
      data: {
        data: [{ ...MOCK_RETURN, status: "APPROVED" as const }],
        page: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false,
      },
    }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(<MyReturnsTab  />);
    expect(screen.queryByRole("button", { name: /cancel return/i })).toBeNull();
  });

  it("shows error state with retry button", () => {
    mockUseMyReturns.mockReturnValue(makeQuery({ isError: true }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(<MyReturnsTab  />);
    expect(screen.getByText("Failed to load return requests.")).toBeDefined();
    expect(screen.getByRole("button", { name: /try again/i })).toBeDefined();
  });
});
