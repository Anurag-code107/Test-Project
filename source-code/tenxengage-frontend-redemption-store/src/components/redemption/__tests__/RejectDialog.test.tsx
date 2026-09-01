import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/hooks/redemption/useRedemptionApproval", () => ({
  useRejectRedemption: vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import { useRejectRedemption } from "@/hooks/redemption/useRedemptionApproval";
import { RejectDialog } from "@/components/redemption/RejectDialog";

const mockUseRejectRedemption = vi.mocked(useRejectRedemption);

function makeMutation(overrides: Partial<ReturnType<typeof useRejectRedemption>> = {}) {
  return {
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    isSuccess: false,
    reset: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useRejectRedemption>;
}

describe("RejectDialog", () => {
  it("renders dialog title, textarea, and both buttons when open", () => {
    mockUseRejectRedemption.mockReturnValue(makeMutation());

    render(
      <RejectDialog redemptionId="test-id" open={true} onOpenChange={vi.fn()} />,
    );

    expect(screen.getByText("Reject redemption")).toBeDefined();
    expect(screen.getByPlaceholderText("Enter reason for rejection...")).toBeDefined();
    expect(screen.getByRole("button", { name: /reject/i })).toBeDefined();
    expect(screen.getByRole("button", { name: /cancel/i })).toBeDefined();
  });

  it("submit button disabled when reason is empty", () => {
    mockUseRejectRedemption.mockReturnValue(makeMutation());

    render(
      <RejectDialog redemptionId="test-id" open={true} onOpenChange={vi.fn()} />,
    );

    expect(screen.getByRole("button", { name: /^reject$/i }).hasAttribute("disabled")).toBe(true);
  });

  it("submit button enabled after typing a reason", async () => {
    mockUseRejectRedemption.mockReturnValue(makeMutation());
    const user = userEvent.setup();

    render(
      <RejectDialog redemptionId="test-id" open={true} onOpenChange={vi.fn()} />,
    );

    await user.type(screen.getByPlaceholderText("Enter reason for rejection..."), "Duplicate request");
    expect(screen.getByRole("button", { name: /^reject$/i }).hasAttribute("disabled")).toBe(false);
  });

  it("calls mutate with correct redemptionId and rejectionReason on submit", async () => {
    const mutate = vi.fn();
    mockUseRejectRedemption.mockReturnValue(makeMutation({ mutate }));
    const user = userEvent.setup();

    render(
      <RejectDialog redemptionId="abc-123" open={true} onOpenChange={vi.fn()} />,
    );

    await user.type(screen.getByPlaceholderText("Enter reason for rejection..."), "Test reason");
    await user.click(screen.getByRole("button", { name: /^reject$/i }));

    expect(mutate).toHaveBeenCalledWith(
      { redemptionId: "abc-123", rejectionReason: "Test reason" },
      expect.any(Object),
    );
  });

  it("Cancel button closes dialog without calling mutate", async () => {
    const mutate = vi.fn();
    const onOpenChange = vi.fn();
    mockUseRejectRedemption.mockReturnValue(makeMutation({ mutate }));
    const user = userEvent.setup();

    render(
      <RejectDialog redemptionId="test-id" open={true} onOpenChange={onOpenChange} />,
    );

    await user.click(screen.getByRole("button", { name: /cancel/i }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
    expect(mutate).not.toHaveBeenCalled();
  });

  it("shows spinner and disables buttons while pending", () => {
    mockUseRejectRedemption.mockReturnValue(makeMutation({ isPending: true }));

    render(
      <RejectDialog redemptionId="test-id" open={true} onOpenChange={vi.fn()} />,
    );

    expect(screen.getByRole("button", { name: /reject/i }).hasAttribute("disabled")).toBe(true);
    expect(screen.getByRole("button", { name: /cancel/i }).hasAttribute("disabled")).toBe(true);
  });

  it("submit button disabled when reason exceeds 1000 characters", async () => {
    mockUseRejectRedemption.mockReturnValue(makeMutation());
    const user = userEvent.setup({ writeToClipboard: false });

    render(
      <RejectDialog redemptionId="test-id" open={true} onOpenChange={vi.fn()} />,
    );

    const textarea = screen.getByPlaceholderText("Enter reason for rejection...");
    await user.click(textarea);
    // Paste a 1001-char string directly into the textarea
    const longReason = "a".repeat(1001);
    await user.paste(longReason);
    expect(screen.getByRole("button", { name: /^reject$/i }).hasAttribute("disabled")).toBe(true);
  });
});
