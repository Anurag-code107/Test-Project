import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { DigitalWalletsPanel } from "@/components/redemption-payout/DigitalWalletsPanel";
import type { DigitalWallet } from "@/types/redemption-payout/redemption-payout.types";

const { walletsState } = vi.hoisted(() => ({
  walletsState: {
    data: undefined as DigitalWallet[] | undefined,
    isLoading: false,
    isError: false,
    error: null as unknown,
  },
}));

vi.mock("@/hooks/redemption-payout/useRedemptionProfile", () => ({
  useDigitalWallets: () => walletsState,
}));

function wallet(o: Partial<DigitalWallet> = {}): DigitalWallet {
  return { id: crypto.randomUUID(), name: "Wallet - USD", currency: "USD", balance: 25, ...o };
}

function renderPanel(props: Partial<React.ComponentProps<typeof DigitalWalletsPanel>> = {}) {
  return render(
    <DigitalWalletsPanel
      enrolled={true}
      isDefault={false}
      onWithdraw={vi.fn()}
      onSetDefault={vi.fn()}
      settingDefault={false}
      {...props}
    />,
  );
}

describe("DigitalWalletsPanel", () => {
  beforeEach(() => {
    walletsState.data = undefined;
    walletsState.isLoading = false;
    walletsState.isError = false;
    walletsState.error = null;
  });

  it("prompts to complete the profile when not enrolled", () => {
    renderPanel({ enrolled: false });
    expect(screen.getByText(/complete your payout profile to view your digital wallet/i)).toBeDefined();
  });

  it("shows a loading state", () => {
    walletsState.isLoading = true;
    renderPanel();
    expect(screen.getByText(/loading wallet/i)).toBeDefined();
  });

  it("renders the balance and a Withdraw + Set-as-default action when not the default", () => {
    walletsState.data = [wallet({ name: "Wallet - USD", currency: "USD", balance: 25 })];
    renderPanel({ isDefault: false });
    expect(screen.getByText("Wallet - USD")).toBeDefined();
    expect(screen.getByText(/\$25\.00/)).toBeDefined();
    expect(screen.getByRole("button", { name: /withdraw/i })).toBeDefined();
    expect(screen.getByRole("button", { name: /set as default/i })).toBeDefined();
    // No "Default" badge when the wallet is not the payout destination.
    expect(screen.queryByText(/^default$/i)).toBeNull();
  });

  it("shows the Default badge and no Set-default action when the wallet is the destination", () => {
    walletsState.data = [wallet({ currency: "USD", balance: 25 })];
    renderPanel({ isDefault: true });
    expect(screen.getByText(/^default$/i)).toBeDefined();
    expect(screen.getByText(/receives your payouts/i)).toBeDefined();
    expect(screen.queryByRole("button", { name: /set as default/i })).toBeNull();
  });

  it("fires callbacks for Withdraw and Set as default", () => {
    const onWithdraw = vi.fn();
    const onSetDefault = vi.fn();
    walletsState.data = [wallet()];
    renderPanel({ onWithdraw, onSetDefault });
    fireEvent.click(screen.getByRole("button", { name: /withdraw/i }));
    fireEvent.click(screen.getByRole("button", { name: /set as default/i }));
    expect(onWithdraw).toHaveBeenCalledTimes(1);
    expect(onSetDefault).toHaveBeenCalledTimes(1);
  });

  it("hides non-USD wallets such as an auto-provisioned INR wallet", () => {
    walletsState.data = [
      wallet({ name: "Wallet - USD", currency: "USD", balance: 171 }),
      wallet({ name: "Wallet - INR", currency: "INR", balance: 0 }),
    ];
    renderPanel();
    expect(screen.getByText("Wallet - USD")).toBeDefined();
    expect(screen.queryByText("Wallet - INR")).toBeNull();
  });

  it("shows an empty state when there are no wallets", () => {
    walletsState.data = [];
    renderPanel();
    expect(screen.getByText(/no wallet yet/i)).toBeDefined();
  });
});
