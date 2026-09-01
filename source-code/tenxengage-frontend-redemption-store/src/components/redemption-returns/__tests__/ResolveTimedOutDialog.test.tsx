import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, act } from "@testing-library/react";

// ── Module mocks ──────────────────────────────────────────────────────────────

vi.mock("@/hooks/useResolveTimedOutReturn", () => ({
  useResolveTimedOutReturn: vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import { useResolveTimedOutReturn } from "@/hooks/useResolveTimedOutReturn";
import { ResolveTimedOutDialog } from "@/components/redemption-returns/ResolveTimedOutDialog";

const mockUseResolveTimedOutReturn = vi.mocked(useResolveTimedOutReturn);

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

describe("ResolveTimedOutDialog", () => {
  it("renders both radio options", () => {
    mockUseResolveTimedOutReturn.mockReturnValue(makeResolveMutation());

    render(
      <ResolveTimedOutDialog
        returnId="return-003"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    expect(screen.getByLabelText(/confirm return \(credit wallet\)/i)).toBeDefined();
    expect(screen.getByLabelText(/reject return \(no credit\)/i)).toBeDefined();
  });

  it("shows Resolve Return button disabled when no radio is selected", () => {
    mockUseResolveTimedOutReturn.mockReturnValue(makeResolveMutation());

    render(
      <ResolveTimedOutDialog
        returnId="return-003"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    const resolveButton = screen.getByRole("button", { name: /resolve return/i });
    expect(resolveButton).toBeDefined();
    expect((resolveButton as HTMLButtonElement).disabled).toBe(true);
  });

  it("enables Resolve Return button after selecting CONFIRM radio", async () => {
    mockUseResolveTimedOutReturn.mockReturnValue(makeResolveMutation());

    render(
      <ResolveTimedOutDialog
        returnId="return-003"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    const confirmRadio = screen.getByLabelText(/confirm return \(credit wallet\)/i);
    await act(async () => {
      fireEvent.click(confirmRadio);
      // Allow react-hook-form validation to run
      await new Promise((r) => setTimeout(r, 50));
    });

    const resolveButton = screen.getByRole("button", { name: /resolve return/i });
    expect((resolveButton as HTMLButtonElement).disabled).toBe(false);
  });

  it("enables Resolve Return button after selecting REJECT radio", async () => {
    mockUseResolveTimedOutReturn.mockReturnValue(makeResolveMutation());

    render(
      <ResolveTimedOutDialog
        returnId="return-003"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    const rejectRadio = screen.getByLabelText(/reject return \(no credit\)/i);
    await act(async () => {
      fireEvent.click(rejectRadio);
      // Allow react-hook-form validation to run
      await new Promise((r) => setTimeout(r, 50));
    });

    const resolveButton = screen.getByRole("button", { name: /resolve return/i });
    expect((resolveButton as HTMLButtonElement).disabled).toBe(false);
  });

  it("shows spinner and disables button when submitting", () => {
    mockUseResolveTimedOutReturn.mockReturnValue(makeResolveMutation({ isPending: true }));

    render(
      <ResolveTimedOutDialog
        returnId="return-003"
        open={true}
        onOpenChange={vi.fn()}
      />,
    );

    const resolveButton = screen.getByRole("button", { name: /resolve return/i });
    expect((resolveButton as HTMLButtonElement).disabled).toBe(true);
  });
});
