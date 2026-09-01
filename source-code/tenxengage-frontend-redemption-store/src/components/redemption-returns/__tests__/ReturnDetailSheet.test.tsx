import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";

// ── Module mocks ──────────────────────────────────────────────────────────────

vi.mock("@/hooks/useReturn", () => ({
  useReturn: vi.fn(),
}));

vi.mock("@/hooks/useCancelReturn", () => ({
  useCancelReturn: vi.fn(),
}));

vi.mock("@/hooks/useApproveReturn", () => ({
  useApproveReturn: vi.fn(),
}));

vi.mock("@/hooks/useRejectReturn", () => ({
  useRejectReturn: vi.fn(),
}));

vi.mock("@/hooks/useResolveTimedOutReturn", () => ({
  useResolveTimedOutReturn: vi.fn(),
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

import { useReturn } from "@/hooks/useReturn";
import { useCancelReturn } from "@/hooks/useCancelReturn";
import { useApproveReturn } from "@/hooks/useApproveReturn";
import { useRejectReturn } from "@/hooks/useRejectReturn";
import { useResolveTimedOutReturn } from "@/hooks/useResolveTimedOutReturn";
import { ReturnDetailSheet } from "@/components/redemption-returns/ReturnDetailSheet";
import type { ReturnDetailResponse } from "@/types/redemption-returns.types";

const mockUseReturn = vi.mocked(useReturn);
const mockUseCancelReturn = vi.mocked(useCancelReturn);
const mockUseApproveReturn = vi.mocked(useApproveReturn);
const mockUseRejectReturn = vi.mocked(useRejectReturn);
const mockUseResolveTimedOutReturn = vi.mocked(useResolveTimedOutReturn);

// shape: contracts/models/redemption-return.md (ReturnDetailResponse)
const MOCK_RETURN_DETAIL: ReturnDetailResponse = {
  id: "return-001",
  redemptionId: "redeem-001",
  catalogItemName: "Amazon Gift Card",
  partnerDisplayName: "Alice Smith",
  amount: "150.00",
  currencyId: "points",
  status: "PENDING_APPROVAL",
  createdAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-01T10:00:00Z",
};

function makeReturnQuery(overrides = {}) {
  return {
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useReturn>;
}

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

function makeApproveMutation(overrides = {}) {
  return {
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
    isError: false,
    isSuccess: false,
    reset: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useApproveReturn>;
}

function makeRejectMutation(overrides = {}) {
  return {
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
    isError: false,
    isSuccess: false,
    reset: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useRejectReturn>;
}

function makeResolveMutation(overrides = {}) {
  return {
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
    isError: false,
    isSuccess: false,
    reset: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useResolveTimedOutReturn>;
}

describe("ReturnDetailSheet", () => {
  beforeEach(() => {
    mockUseApproveReturn.mockReturnValue(makeApproveMutation());
    mockUseRejectReturn.mockReturnValue(makeRejectMutation());
    mockUseResolveTimedOutReturn.mockReturnValue(makeResolveMutation());
  });

  it("shows skeleton while loading", () => {
    mockUseReturn.mockReturnValue(makeReturnQuery({ isLoading: true }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    const { container } = render(
      <ReturnDetailSheet returnId="return-001" role="partner" open={true} onClose={vi.fn()} />,
    );

    expect(container.querySelector('[aria-busy="true"]')).toBeDefined();
  });

  it("renders return info when data is loaded", () => {
    mockUseReturn.mockReturnValue(makeReturnQuery({ data: MOCK_RETURN_DETAIL }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(
      <ReturnDetailSheet returnId="return-001" role="partner" open={true} onClose={vi.fn()} />,
    );

    expect(screen.getByText("Amazon Gift Card")).toBeDefined();
    expect(screen.getByText("Alice Smith")).toBeDefined();
  });

  it("shows Cancel Return footer button for partner with PENDING_APPROVAL status", () => {
    mockUseReturn.mockReturnValue(makeReturnQuery({ data: MOCK_RETURN_DETAIL }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(
      <ReturnDetailSheet returnId="return-001" role="partner" open={true} onClose={vi.fn()} />,
    );

    expect(screen.getByRole("button", { name: /cancel return/i })).toBeDefined();
  });

  it("does NOT show Cancel Return footer when status is not PENDING_APPROVAL", () => {
    mockUseReturn.mockReturnValue(makeReturnQuery({
      data: { ...MOCK_RETURN_DETAIL, status: "APPROVED" as const },
    }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(
      <ReturnDetailSheet returnId="return-001" role="partner" open={true} onClose={vi.fn()} />,
    );

    expect(screen.queryByRole("button", { name: /cancel return/i })).toBeNull();
  });

  it("does NOT show admin-only fields (reviewNotes, vendorReturnReference) in partner view", () => {
    mockUseReturn.mockReturnValue(makeReturnQuery({
      data: {
        ...MOCK_RETURN_DETAIL,
        reviewNotes: "Approved by admin",
        vendorReturnReference: "xox-ref-123",
      },
    }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(
      <ReturnDetailSheet returnId="return-001" role="partner" open={true} onClose={vi.fn()} />,
    );

    expect(screen.queryByText("Admin notes")).toBeNull();
    expect(screen.queryByText("Approved by admin")).toBeNull();
    expect(screen.queryByText("Vendor reference")).toBeNull();
    expect(screen.queryByText("xox-ref-123")).toBeNull();
  });

  it("shows admin-only fields when role is admin", () => {
    mockUseReturn.mockReturnValue(makeReturnQuery({
      data: {
        ...MOCK_RETURN_DETAIL,
        reviewNotes: "Approved by admin",
        vendorReturnReference: "xox-ref-123",
      },
    }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(
      <ReturnDetailSheet returnId="return-001" role="admin" open={true} onClose={vi.fn()} />,
    );

    expect(screen.getByText("Admin notes")).toBeDefined();
    expect(screen.getByText("Approved by admin")).toBeDefined();
    expect(screen.getByText("Vendor reference")).toBeDefined();
    expect(screen.getByText("xox-ref-123")).toBeDefined();
  });

  it("shows error state with retry", () => {
    mockUseReturn.mockReturnValue(makeReturnQuery({ isError: true }));
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(
      <ReturnDetailSheet returnId="return-001" role="partner" open={true} onClose={vi.fn()} />,
    );

    expect(screen.getByText("Unable to load return details.")).toBeDefined();
    expect(screen.getByRole("button", { name: /try again/i })).toBeDefined();
  });

  it("useReturn is called with isAdmin=false for partner role", () => {
    mockUseReturn.mockReturnValue(makeReturnQuery());
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(
      <ReturnDetailSheet returnId="return-001" role="partner" open={true} onClose={vi.fn()} />,
    );

    expect(mockUseReturn).toHaveBeenCalledWith("return-001", false);
  });

  it("useReturn is called with isAdmin=true for admin role", () => {
    mockUseReturn.mockReturnValue(makeReturnQuery());
    mockUseCancelReturn.mockReturnValue(makeCancelMutation());

    render(
      <ReturnDetailSheet returnId="return-001" role="admin" open={true} onClose={vi.fn()} />,
    );

    expect(mockUseReturn).toHaveBeenCalledWith("return-001", true);
  });
});
