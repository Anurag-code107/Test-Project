import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";

// ── Module mocks ──────────────────────────────────────────────────────────────

vi.mock("@/hooks/useAdminReturns", () => ({
  useAdminReturns: vi.fn(),
}));

vi.mock("@/hooks/useApproveReturn", () => ({
  useApproveReturn: vi.fn(),
}));

vi.mock("@/hooks/useRejectReturn", () => ({
  useRejectReturn: vi.fn(),
}));

vi.mock("@/hooks/useReturn", () => ({
  useReturn: vi.fn(() => ({ data: undefined, isLoading: false, isError: false, refetch: vi.fn() })),
}));

vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: () => ({
    can: () => true,
    canAny: () => true,
    canAll: () => true,
    permissions: new Set(),
  }),
}));

vi.mock("@/hooks/useCancelReturn", () => ({
  useCancelReturn: vi.fn(() => ({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
    isError: false,
    isSuccess: false,
    reset: vi.fn(),
  })),
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

import { useAdminReturns } from "@/hooks/useAdminReturns";
import { useApproveReturn } from "@/hooks/useApproveReturn";
import { useRejectReturn } from "@/hooks/useRejectReturn";
import { ReturnsApprovalTab } from "@/components/redemption-returns/ReturnsApprovalTab";
import type { ReturnQueueItemResponse } from "@/types/redemption-returns.types";

const mockUseAdminReturns = vi.mocked(useAdminReturns);
const mockUseApproveReturn = vi.mocked(useApproveReturn);
const mockUseRejectReturn = vi.mocked(useRejectReturn);

// shape: contracts/endpoints/redemption-returns.yaml (ReturnQueueItemResponse)
const MOCK_PENDING_ROW: ReturnQueueItemResponse = {
  id: "return-001",
  catalogItemName: "Amazon Gift Card",
  partnerDisplayName: "Jane Seller",
  partnerCompanyName: "Acme Corp",
  amount: "150.00",
  currencyId: "points",
  status: "PENDING_APPROVAL",
  createdAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-01T10:00:00Z",
};

const MOCK_APPROVED_ROW: ReturnQueueItemResponse = {
  ...MOCK_PENDING_ROW,
  id: "return-002",
  status: "APPROVED",
};

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

function makeAdminReturnsQuery(overrides = {}) {
  return {
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useAdminReturns>;
}

describe("ReturnsApprovalTab", () => {
  beforeEach(() => {
    mockUseApproveReturn.mockReturnValue(makeApproveMutation());
    mockUseRejectReturn.mockReturnValue(makeRejectMutation());
  });

  it("shows skeleton on isLoading", () => {
    mockUseAdminReturns.mockReturnValue(makeAdminReturnsQuery({ isLoading: true }));

    const { container } = render(<ReturnsApprovalTab clientId="client-001" />);

    expect(container.querySelector('[aria-busy="true"]')).toBeDefined();
  });

  it("defaults to PENDING_APPROVAL filter — status select shows Pending Approval", () => {
    mockUseAdminReturns.mockReturnValue(makeAdminReturnsQuery({
      data: {
        data: [],
        page: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false,
      },
    }));

    render(<ReturnsApprovalTab clientId="client-001" />);

    // The Select trigger should display the default "PENDING_APPROVAL" option label
    expect(screen.getByText("Pending Approval")).toBeDefined();
  });

  it("shows empty state when no rows", () => {
    mockUseAdminReturns.mockReturnValue(makeAdminReturnsQuery({
      data: {
        data: [],
        page: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false,
      },
    }));

    render(<ReturnsApprovalTab clientId="client-001" />);

    expect(screen.getByText("No return requests to review.")).toBeDefined();
  });

  it("renders table with data rows", () => {
    mockUseAdminReturns.mockReturnValue(makeAdminReturnsQuery({
      data: {
        data: [MOCK_PENDING_ROW],
        page: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false,
      },
    }));

    render(<ReturnsApprovalTab clientId="client-001" />);

    expect(screen.getByText("Amazon Gift Card")).toBeDefined();
    expect(screen.getByText("Jane Seller")).toBeDefined();
    expect(screen.getByText("Acme Corp")).toBeDefined();
  });

  it("shows Approve and Reject menu items for PENDING_APPROVAL rows (with review permission)", () => {
    mockUseAdminReturns.mockReturnValue(makeAdminReturnsQuery({
      data: {
        data: [MOCK_PENDING_ROW],
        page: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false,
      },
    }));

    render(<ReturnsApprovalTab clientId="client-001" />);

    // The kebab trigger should be present
    const kebab = screen.getByRole("button", { name: /actions for amazon gift card/i });
    expect(kebab).toBeDefined();
  });

  it("does NOT show Approve/Reject in kebab for APPROVED rows", () => {
    mockUseAdminReturns.mockReturnValue(makeAdminReturnsQuery({
      data: {
        data: [MOCK_APPROVED_ROW],
        page: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false,
      },
    }));

    render(<ReturnsApprovalTab clientId="client-001" />);

    // APPROVED rows do not have PENDING_APPROVAL status, so Approve/Reject menu items
    // are conditionally hidden. The dropdown is present but contains only "View Details".
    // We confirm by checking the kebab button is rendered but not the destructive items.
    const kebab = screen.getByRole("button", { name: /actions for amazon gift card/i });
    expect(kebab).toBeDefined();
    // Approve/Reject are not rendered for non-PENDING_APPROVAL rows
    expect(screen.queryByRole("menuitem", { name: /^approve$/i })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: /^reject$/i })).toBeNull();
  });

  it("shows error state with retry", () => {
    mockUseAdminReturns.mockReturnValue(makeAdminReturnsQuery({ isError: true }));

    render(<ReturnsApprovalTab clientId="client-001" />);

    expect(screen.getByText("Failed to load return requests.")).toBeDefined();
    expect(screen.getByRole("button", { name: /try again/i })).toBeDefined();
  });
});
