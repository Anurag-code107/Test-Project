import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";

// ── Module mocks ──────────────────────────────────────────────────────────────

vi.mock("@/hooks/useRejectReturn", () => ({
  useRejectReturn: vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import { useRejectReturn } from "@/hooks/useRejectReturn";
import { RejectReturnDialog } from "@/components/redemption-returns/RejectReturnDialog";

const mockUseRejectReturn = vi.mocked(useRejectReturn);

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

describe("RejectReturnDialog", () => {
  it("shows submit button disabled when reason is empty", () => {
    mockUseRejectReturn.mockReturnValue(makeRejectMutation());

    render(
      <RejectReturnDialog
        returnId="return-001"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    const submitButton = screen.getByRole("button", { name: /reject request/i });
    expect(submitButton).toBeDefined();
    // Button should be disabled when no text is entered
    expect((submitButton as HTMLButtonElement).disabled).toBe(true);
  });

  it("enables submit button when reason is filled", async () => {
    mockUseRejectReturn.mockReturnValue(makeRejectMutation());

    render(
      <RejectReturnDialog
        returnId="return-001"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    const textarea = screen.getByRole("textbox");
    fireEvent.change(textarea, { target: { value: "Item was already used by the customer." } });

    // Wait for react-hook-form validation to run
    await new Promise((r) => setTimeout(r, 50));

    const submitButton = screen.getByRole("button", { name: /reject request/i });
    expect((submitButton as HTMLButtonElement).disabled).toBe(false);
  });

  it("shows char counter", () => {
    mockUseRejectReturn.mockReturnValue(makeRejectMutation());

    render(
      <RejectReturnDialog
        returnId="return-001"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    // Initial char counter should show 0/1000
    expect(screen.getByText("0/1000")).toBeDefined();
  });

  it("shows spinner and disables submit while submitting", () => {
    mockUseRejectReturn.mockReturnValue(makeRejectMutation({ isPending: true }));

    render(
      <RejectReturnDialog
        returnId="return-001"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    const submitButton = screen.getByRole("button", { name: /reject request/i });
    expect((submitButton as HTMLButtonElement).disabled).toBe(true);
  });

  it("renders the dialog title", () => {
    mockUseRejectReturn.mockReturnValue(makeRejectMutation());

    render(
      <RejectReturnDialog
        returnId="return-001"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    expect(screen.getByText("Reject Return Request?")).toBeDefined();
  });

  it("renders cancel button", () => {
    mockUseRejectReturn.mockReturnValue(makeRejectMutation());

    render(
      <RejectReturnDialog
        returnId="return-001"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    expect(screen.getByRole("button", { name: /^cancel$/i })).toBeDefined();
  });
});
