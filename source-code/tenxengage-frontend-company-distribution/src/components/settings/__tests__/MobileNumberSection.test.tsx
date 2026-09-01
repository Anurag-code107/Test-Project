import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { MobileNumberSection } from "@/components/settings/MobileNumberSection";

const { authState, initiateState, confirmState } = vi.hoisted(() => ({
  authState: { user: { phone: null as string | null }, refreshUser: vi.fn() },
  initiateState: {
    result: { otpRequired: true, phone: null as string | null, phoneCountryIso2: null as string | null },
    isPending: false,
    isError: false,
    error: null as unknown,
  },
  confirmState: {
    result: { otpRequired: false, phone: "4085551284", phoneCountryIso2: "US" },
    isPending: false,
    isError: false,
    error: null as unknown,
  },
}));

vi.mock("@/hooks/useAuth", () => ({ useAuth: () => authState }));

vi.mock("@/hooks/useProfilePhone", async (importActual) => {
  const actual = await importActual<typeof import("@/hooks/useProfilePhone")>();
  return {
    ...actual,
    useInitiatePhoneUpdate: () => ({
      mutate: (v: unknown, opts?: { onSuccess?: (r: unknown, v: unknown) => void; onError?: (e: unknown) => void }) => {
        if (initiateState.isError) opts?.onError?.(initiateState.error);
        else opts?.onSuccess?.(initiateState.result, v);
      },
      isPending: initiateState.isPending,
      isError: initiateState.isError,
      error: initiateState.error,
    }),
    useConfirmPhoneUpdate: () => ({
      mutate: (v: unknown, opts?: { onSuccess?: (r: unknown, v: unknown) => void; onError?: (e: unknown) => void }) => {
        if (confirmState.isError) opts?.onError?.(confirmState.error);
        else opts?.onSuccess?.(confirmState.result, v);
      },
      isPending: confirmState.isPending,
      isError: confirmState.isError,
      error: confirmState.error,
    }),
  };
});

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

describe("MobileNumberSection", () => {
  beforeEach(() => {
    authState.user = { phone: null };
    authState.refreshUser = vi.fn();
    initiateState.result = { otpRequired: true, phone: null, phoneCountryIso2: null };
    initiateState.isError = false;
    confirmState.isError = false;
    vi.clearAllMocks();
  });

  it("shows 'Not set' and an Add action when no number is stored", () => {
    render(<MobileNumberSection />);
    expect(screen.getByText("Not set")).toBeDefined();
    expect(screen.getByRole("button", { name: /^add$/i })).toBeDefined();
  });

  it("shows the current number and a Change action when set", () => {
    authState.user = { phone: "5551234567" };
    render(<MobileNumberSection />);
    expect(screen.getByText("5551234567")).toBeDefined();
    expect(screen.getByRole("button", { name: /^change$/i })).toBeDefined();
  });

  it("validates the number before sending", async () => {
    const user = userEvent.setup();
    render(<MobileNumberSection />);
    await user.click(screen.getByRole("button", { name: /^add$/i }));
    await user.type(screen.getByLabelText(/mobile number/i), "12");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(screen.getByText(/7–20 digits/i)).toBeDefined();
    // Still on the form step (no OTP input).
    expect(screen.queryByLabelText(/one-time code/i)).toBeNull();
  });

  it("runs the 2-step OTP change and refreshes the user", async () => {
    const user = userEvent.setup();
    render(<MobileNumberSection />);
    await user.click(screen.getByRole("button", { name: /^add$/i }));
    await user.type(screen.getByLabelText(/mobile number/i), "4085551284");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    const otp = await screen.findByLabelText(/one-time code/i);
    await user.type(otp, "123456");
    await user.click(screen.getByRole("button", { name: /^confirm$/i }));

    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(authState.refreshUser).toHaveBeenCalled();
  });

  it("saves immediately (no OTP) for a not-yet-enrolled user", async () => {
    initiateState.result = { otpRequired: false, phone: "4085551284", phoneCountryIso2: "US" };
    const user = userEvent.setup();
    render(<MobileNumberSection />);
    await user.click(screen.getByRole("button", { name: /^add$/i }));
    await user.type(screen.getByLabelText(/mobile number/i), "4085551284");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(authState.refreshUser).toHaveBeenCalled();
    expect(screen.queryByLabelText(/one-time code/i)).toBeNull();
  });
});
