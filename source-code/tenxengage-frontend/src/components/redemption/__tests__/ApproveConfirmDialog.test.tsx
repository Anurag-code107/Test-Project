import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/hooks/redemption/useRedemptionApproval", () => ({
  useApproveRedemption: vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import { useApproveRedemption } from "@/hooks/redemption/useRedemptionApproval";
import { ApproveConfirmDialog } from "@/components/redemption/ApproveConfirmDialog";

const mockUseApproveRedemption = vi.mocked(useApproveRedemption);

function makeMutation(overrides: Partial<ReturnType<typeof useApproveRedemption>> = {}) {
  return {
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    isSuccess: false,
    reset: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useApproveRedemption>;
}

describe("ApproveConfirmDialog", () => {
  it("renders dialog title and both buttons when open", () => {
    mockUseApproveRedemption.mockReturnValue(makeMutation());

    render(
      <ApproveConfirmDialog
        redemptionId="test-id"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    expect(screen.getByText("Approve this redemption?")).toBeDefined();
    expect(screen.getByRole("button", { name: /approve/i })).toBeDefined();
    expect(screen.getByRole("button", { name: /cancel/i })).toBeDefined();
  });

  it("calls mutate with the correct redemptionId when Approve is clicked", async () => {
    const mutate = vi.fn();
    mockUseApproveRedemption.mockReturnValue(makeMutation({ mutate }));
    const user = userEvent.setup();

    render(
      <ApproveConfirmDialog
        redemptionId="abc-123"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: /^approve$/i }));
    expect(mutate).toHaveBeenCalledWith("abc-123", expect.any(Object));
  });

  it("Cancel button calls onOpenChange(false) without calling mutate", async () => {
    const mutate = vi.fn();
    const onOpenChange = vi.fn();
    mockUseApproveRedemption.mockReturnValue(makeMutation({ mutate }));
    const user = userEvent.setup();

    render(
      <ApproveConfirmDialog
        redemptionId="abc-123"
        open={true}
        onOpenChange={onOpenChange}
      />,
    );

    await user.click(screen.getByRole("button", { name: /cancel/i }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
    expect(mutate).not.toHaveBeenCalled();
  });

  it("shows loading spinner and disables buttons while pending", () => {
    mockUseApproveRedemption.mockReturnValue(makeMutation({ isPending: true }));

    render(
      <ApproveConfirmDialog
        redemptionId="abc-123"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    expect(screen.getByRole("button", { name: /approve/i }).hasAttribute("disabled")).toBe(true);
    expect(screen.getByRole("button", { name: /cancel/i }).hasAttribute("disabled")).toBe(true);
  });
});
