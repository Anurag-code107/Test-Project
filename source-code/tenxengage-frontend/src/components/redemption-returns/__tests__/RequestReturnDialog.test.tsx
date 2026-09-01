import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// ── Module mocks ──────────────────────────────────────────────────────────────

vi.mock("@/hooks/useSubmitReturn", () => ({
  useSubmitReturn: vi.fn(),
}));

vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: () => ({ can: () => true, canAny: () => true, canAll: () => true, permissions: new Set() }),
}));

vi.mock("@/config/currencies", () => ({
  getCurrency: vi.fn(() => ({
    rewardFormat: (v: string) => `${parseFloat(v).toLocaleString()} pts`,
  })),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import { useSubmitReturn } from "@/hooks/useSubmitReturn";
import { RequestReturnDialog } from "@/components/redemption-returns/RequestReturnDialog";

const mockUseSubmitReturn = vi.mocked(useSubmitReturn);

function makeMutation(overrides: Partial<ReturnType<typeof useSubmitReturn>> = {}) {
  return {
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
    isError: false,
    isSuccess: false,
    reset: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useSubmitReturn>;
}

const DEFAULT_PROPS = {
  open: true,
  onOpenChange: vi.fn(),
  redemptionId: "redemption-001",
  amount: "150.00",
  currencyId: "points",
  catalogItemName: "Amazon Gift Card",
  onSuccess: vi.fn(),
};

describe("RequestReturnDialog", () => {
  beforeEach(() => {
    mockUseSubmitReturn.mockReturnValue(makeMutation());
  });

  it("renders dialog title, item name, and buttons", () => {
    render(<RequestReturnDialog {...DEFAULT_PROPS} />);

    expect(screen.getByText("Request Return")).toBeDefined();
    expect(screen.getByText(/Amazon Gift Card/)).toBeDefined();
    expect(screen.getByRole("button", { name: /submit return request/i })).toBeDefined();
    expect(screen.getByRole("button", { name: /cancel/i })).toBeDefined();
  });

  it("character counter is hidden below 400 chars and visible at 400+", async () => {
    render(<RequestReturnDialog {...DEFAULT_PROPS} />);

    const textarea = screen.getByPlaceholderText(/Describe why you're returning/);

    // Under 400: counter should NOT appear
    fireEvent.change(textarea, { target: { value: "Short" } });
    expect(screen.queryByText(/\/500/)).toBeNull();

    // At exactly 400 chars: counter should appear
    fireEvent.change(textarea, { target: { value: "a".repeat(400) } });
    await waitFor(() => expect(screen.getByText("400/500")).toBeDefined());
  });

  it("disables submit when reason exceeds 500 chars", async () => {
    render(<RequestReturnDialog {...DEFAULT_PROPS} />);

    const textarea = screen.getByPlaceholderText(/Describe why you're returning/);
    fireEvent.change(textarea, { target: { value: "a".repeat(501) } });

    await waitFor(() => {
      const submitBtn = screen.getByRole("button", { name: /submit return request/i });
      expect(submitBtn).toHaveProperty("disabled", true);
    });
  });

  it("shows spinner and disables submit while pending", () => {
    mockUseSubmitReturn.mockReturnValue(makeMutation({ isPending: true }));
    render(<RequestReturnDialog {...DEFAULT_PROPS} />);

    expect(screen.getByRole("button", { name: /submit return request/i })).toHaveProperty("disabled", true);
  });

  it("calls mutate with redemptionId and no reason when textarea is empty", async () => {
    const mutate = vi.fn();
    mockUseSubmitReturn.mockReturnValue(makeMutation({ mutate }));
    const user = userEvent.setup();

    render(<RequestReturnDialog {...DEFAULT_PROPS} />);

    // Don't type anything — submit with empty reason
    const submitBtn = screen.getByRole("button", { name: /submit return request/i });
    await user.click(submitBtn);

    await waitFor(() => expect(mutate).toHaveBeenCalled());
    const [dto] = mutate.mock.calls[0] as [{ redemptionId: string; reason: string | undefined }];
    expect(dto.redemptionId).toBe("redemption-001");
    expect(dto.reason).toBeUndefined();
  });

  it("calls mutate with reason when reason is provided", async () => {
    const mutate = vi.fn();
    mockUseSubmitReturn.mockReturnValue(makeMutation({ mutate }));
    const user = userEvent.setup();

    render(<RequestReturnDialog {...DEFAULT_PROPS} />);

    await user.type(screen.getByPlaceholderText(/Describe why you're returning/), "Wrong item");
    await user.click(screen.getByRole("button", { name: /submit return request/i }));

    await waitFor(() => expect(mutate).toHaveBeenCalled());
    const [dto] = mutate.mock.calls[0] as [{ redemptionId: string; reason: string | undefined }];
    expect(dto.redemptionId).toBe("redemption-001");
    expect(dto.reason).toBe("Wrong item");
  });

  it("does not call onOpenChange when cancel is clicked while isPending", async () => {
    const onOpenChange = vi.fn();
    mockUseSubmitReturn.mockReturnValue(makeMutation({ isPending: true }));
    const user = userEvent.setup();

    render(<RequestReturnDialog {...DEFAULT_PROPS} onOpenChange={onOpenChange} />);

    await user.click(screen.getByRole("button", { name: /cancel/i }));
    expect(onOpenChange).not.toHaveBeenCalled();
  });
});
