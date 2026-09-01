import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BankTransferPanel } from "@/components/redemption-catalog/BankTransferPanel";
import type { LinkedBank } from "@/types/redemption-payout/redemption-payout.types";

const { navigateMock, banksState, walletsState } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  banksState: { data: [] as LinkedBank[], isLoading: false },
  walletsState: { data: [] as unknown[] },
}));

vi.mock("react-router-dom", async (importActual) => {
  const actual = await importActual<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => navigateMock };
});

vi.mock("@/hooks/redemption-payout/useRedemptionProfile", () => ({
  useLinkedBanks: () => banksState,
}));

vi.mock("@/hooks/useWallet", () => ({
  useMyWallets: () => walletsState,
}));

vi.mock("sonner", () => ({ toast: Object.assign(vi.fn(), { error: vi.fn() }) }));

vi.mock("@/services/redemption-flow.service", () => ({
  submitBankTransferRedemption: vi.fn(),
}));

import * as service from "@/services/redemption-flow.service";

const CASH_WALLET = {
  id: "wallet-cash",
  walletType: "INDIVIDUAL",
  currencyId: "cash",
  availableBalance: "500",
  reservedBalance: "0",
};

function bank(overrides: Partial<LinkedBank> = {}): LinkedBank {
  return { id: crypto.randomUUID(), label: "Wells Fargo ••1898", currency: "USD", isDefault: true, ...overrides };
}

function renderPanel() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <BankTransferPanel />
    </QueryClientProvider>,
  );
}

describe("BankTransferPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    banksState.data = [];
    banksState.isLoading = false;
    walletsState.data = [CASH_WALLET];
  });

  it("shows the empty state + CTA that navigates to the Payout tab when no bank is linked (FE-2)", () => {
    banksState.data = [];
    renderPanel();
    expect(screen.getByTestId("bank-transfer-empty")).toBeDefined();
    fireEvent.click(screen.getByRole("button", { name: /link a bank account/i }));
    expect(navigateMock).toHaveBeenCalledWith("/settings/profile?tab=payout&section=banks");
  });

  it("shows the default bank + amount input and submits a bank transfer (FE-3)", async () => {
    banksState.data = [bank({ label: "Wells Fargo ••1898", isDefault: true })];
    vi.mocked(service.submitBankTransferRedemption).mockResolvedValueOnce({
      id: "req-9",
      status: "PROCESSING",
      amount: "1",
      currencyId: "cash",
      processingMode: "INSTANT",
      estimatedDelivery: "1-2 business days",
      submittedAt: "2026-07-23T00:00:00Z",
      createdAt: "2026-07-23T00:00:00Z",
      updatedAt: "2026-07-23T00:00:00Z",
    });

    renderPanel();
    expect(screen.getByText("Wells Fargo ••1898")).toBeDefined();
    const input = screen.getByLabelText(/amount/i) as HTMLInputElement;
    expect(input.value).toBe("1");

    fireEvent.click(screen.getByTestId("bank-transfer-submit"));

    await waitFor(() => {
      expect(service.submitBankTransferRedemption).toHaveBeenCalledWith({ walletId: "wallet-cash", amount: "1" });
      expect(navigateMock).toHaveBeenCalledWith("/redemption/confirmation/req-9");
    });
  });

  it("with multiple banks, lets the user pick a non-default bank and sends its bankId", async () => {
    const user = userEvent.setup();
    banksState.data = [
      bank({ id: "bank-1", label: "Wells Fargo ••1898", isDefault: true }),
      bank({ id: "bank-2", label: "Chase ••7777", isDefault: false }),
    ];
    vi.mocked(service.submitBankTransferRedemption).mockResolvedValueOnce({
      id: "req-10",
      status: "PROCESSING",
      amount: "1",
      currencyId: "cash",
      processingMode: "INSTANT",
      estimatedDelivery: "1-2 business days",
      submittedAt: "2026-07-27T00:00:00Z",
      createdAt: "2026-07-27T00:00:00Z",
      updatedAt: "2026-07-27T00:00:00Z",
    });

    renderPanel();
    await user.click(screen.getByTestId("bank-select"));
    await user.click(screen.getByRole("option", { name: /Chase/i }));
    fireEvent.click(screen.getByTestId("bank-transfer-submit"));

    await waitFor(() => {
      expect(service.submitBankTransferRedemption).toHaveBeenCalledWith(
        expect.objectContaining({ walletId: "wallet-cash", bankId: "bank-2" }),
      );
    });
  });

  describe("amount validation", () => {
    function typeAmount(value: string) {
      fireEvent.change(screen.getByLabelText(/amount/i), { target: { value } });
    }

    beforeEach(() => {
      banksState.data = [bank()];
    });

    it("blocks a zero transfer", () => {
      renderPanel();
      typeAmount("0");

      expect(screen.getByTestId("field-error").textContent).toBe("Amount must be greater than 0.");
      expect(screen.getByTestId("bank-transfer-submit")).toBeDisabled();
      fireEvent.click(screen.getByTestId("bank-transfer-submit"));
      expect(service.submitBankTransferRedemption).not.toHaveBeenCalled();
    });

    it("blocks a negative transfer", () => {
      renderPanel();
      typeAmount("-25");

      expect(screen.getByTestId("field-error").textContent).toBe("Amount must be greater than 0.");
      expect(screen.getByTestId("bank-transfer-submit")).toBeDisabled();
    });

    it("blocks a cleared amount", () => {
      renderPanel();
      typeAmount("");

      expect(screen.getByTestId("field-error").textContent).toBe("Enter an amount.");
      expect(screen.getByTestId("bank-transfer-submit")).toBeDisabled();
    });

    it("blocks more than the user's available cash balance", () => {
      renderPanel(); // cash wallet holds 500
      typeAmount("501");

      expect(screen.getByTestId("field-error").textContent).toBe(
        "Amount exceeds your available balance of $500.",
      );
      expect(screen.getByTestId("bank-transfer-submit")).toBeDisabled();
      fireEvent.click(screen.getByTestId("bank-transfer-submit"));
      expect(service.submitBankTransferRedemption).not.toHaveBeenCalled();
    });

    it("allows transferring the entire balance", () => {
      renderPanel();
      typeAmount("500");

      expect(screen.queryByTestId("field-error")).toBeNull();
      expect(screen.getByTestId("bank-transfer-submit")).not.toBeDisabled();
    });

    it("caps the input's native max at the available balance", () => {
      renderPanel();
      expect(screen.getByLabelText(/amount/i)).toHaveAttribute("max", "500");
    });
  });

  it("shows an inline field error on a 422", async () => {
    banksState.data = [bank()];
    const err = Object.assign(new Error("422"), {
      response: { status: 422, data: { errorMessage: "Amount is below the minimum allowed: 1.00" } },
    });
    vi.mocked(service.submitBankTransferRedemption).mockRejectedValueOnce(err);

    renderPanel();
    fireEvent.click(screen.getByTestId("bank-transfer-submit"));

    await waitFor(() => {
      expect(screen.getByTestId("field-error").textContent).toContain("Amount is below the minimum allowed: 1.00");
    });
  });
});
