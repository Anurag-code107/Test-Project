import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { WithdrawDialog } from "@/components/redemption-payout/WithdrawDialog";
import type { LinkedBank, LinkedCard } from "@/types/redemption-payout/redemption-payout.types";

const { banksState, cardsState, initiate, confirm } = vi.hoisted(() => ({
  banksState: { data: [] as LinkedBank[] },
  cardsState: { data: [] as LinkedCard[] },
  initiate: {
    mutate: vi.fn((_vars: unknown, opts?: { onSuccess?: (r: unknown) => void }) =>
      opts?.onSuccess?.({ otpRequired: true, transactionId: null, status: null, amountGross: null, fee: null, amountNet: null, currency: null, destinationLabel: null }),
    ),
    isPending: false,
    isError: false,
    error: null as unknown,
  },
  confirm: {
    mutate: vi.fn((_vars: unknown, opts?: { onSuccess?: (r: unknown) => void }) =>
      opts?.onSuccess?.({ otpRequired: false, transactionId: "t1", status: "COMPLETED", amountGross: 50, fee: 0.36, amountNet: 49.64, currency: "USD", destinationLabel: "Visa ••1111" }),
    ),
    isPending: false,
    isError: false,
    error: null as unknown,
  },
}));

vi.mock("@/hooks/redemption-payout/useRedemptionProfile", () => ({
  useLinkedBanks: () => banksState,
  useLinkedCards: () => cardsState,
}));

vi.mock("@/hooks/redemption-payout/useRedemptionProfileMutations", async (importActual) => {
  const actual = await importActual<typeof import("@/hooks/redemption-payout/useRedemptionProfileMutations")>();
  return { ...actual, useInitiateWithdrawal: () => initiate, useConfirmWithdrawal: () => confirm };
});

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

function bank(o: Partial<LinkedBank> = {}): LinkedBank {
  return { id: crypto.randomUUID(), label: "KOTAK ••8943", currency: "USD", isDefault: true, ...o };
}
function card(o: Partial<LinkedCard> = {}): LinkedCard {
  return { id: crypto.randomUUID(), label: "Visa ••1111", cardType: "Visa", status: "Active", isDefault: false, ...o };
}

describe("WithdrawDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    banksState.data = [bank()];
    cardsState.data = [card()];
  });

  it("does not render content when closed", () => {
    render(<WithdrawDialog open={false} onOpenChange={vi.fn()} />);
    expect(screen.queryByText(/withdraw from your wallet/i)).toBeNull();
  });

  it("renders the title, destinations, and amount when open", () => {
    render(<WithdrawDialog open onOpenChange={vi.fn()} />);
    expect(screen.getByText(/withdraw from your wallet/i)).toBeDefined();
    expect(screen.getByText("KOTAK ••8943")).toBeDefined();
    expect(screen.getByText("Visa ••1111")).toBeDefined();
    expect(screen.getByLabelText(/amount/i)).toBeDefined();
  });

  it("shows a no-destination message when nothing is linked", () => {
    banksState.data = [];
    cardsState.data = [];
    render(<WithdrawDialog open onOpenChange={vi.fn()} />);
    expect(screen.getByText(/no withdrawal destination/i)).toBeDefined();
  });

  it("initiate → OTP → confirm reaches the success state", () => {
    render(<WithdrawDialog open onOpenChange={vi.fn()} />);
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: "50" } });
    fireEvent.click(screen.getByRole("button", { name: /continue/i }));
    expect(initiate.mutate).toHaveBeenCalledTimes(1);
    // OTP step
    const otp = screen.getByLabelText(/one-time code/i);
    fireEvent.change(otp, { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm withdrawal/i }));
    expect(confirm.mutate).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/withdrawal complete/i)).toBeDefined();
    expect(screen.getByText(/\$49\.64/)).toBeDefined();
  });

  it("blocks continue on a non-positive amount", () => {
    render(<WithdrawDialog open onOpenChange={vi.fn()} />);
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: "0" } });
    fireEvent.click(screen.getByRole("button", { name: /continue/i }));
    expect(initiate.mutate).not.toHaveBeenCalled();
    expect(screen.getByText(/greater than 0/i)).toBeDefined();
  });
});
