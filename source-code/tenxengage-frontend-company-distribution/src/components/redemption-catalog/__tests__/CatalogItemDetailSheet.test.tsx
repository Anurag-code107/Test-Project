import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CatalogItemDetailSheet } from "@/components/redemption-catalog/CatalogItemDetailSheet";

const { navigateMock, itemState, walletsState } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  itemState: { data: undefined as unknown, isLoading: false, isError: false, error: null as unknown },
  walletsState: { data: [] as unknown[] },
}));

vi.mock("react-router-dom", async (importActual) => {
  const actual = await importActual<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => navigateMock };
});

vi.mock("@/hooks/useRedemptionCatalog", () => ({
  usePartnerCatalogItem: () => itemState,
}));

vi.mock("@/hooks/useWallet", () => ({
  useMyWallets: () => walletsState,
  useCompanyWallet: () => ({ data: undefined }),
}));

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => ({ user: { id: "u1", permissions: ["action.redemption.redeem"], partnerCompanyId: null } }),
}));

vi.mock("sonner", () => ({ toast: Object.assign(vi.fn(), { error: vi.fn() }) }));

vi.mock("@/services/redemption-flow.service", () => ({
  submitPersonalRedemption: vi.fn(),
}));

import * as service from "@/services/redemption-flow.service";
import { toast } from "sonner";

const ITEM = {
  id: "item-1",
  name: "Amazon Gift Card",
  description: "A gift card",
  category: "NON_CASH",
  currencyId: "points",
  effectiveMinTransactionAmount: "50",
  effectiveProcessingMode: "INSTANT",
  estimatedPayoutTimeline: "Instant delivery",
  canAfford: true,
  shortfallAmount: "0",
  isReturnable: false,
  effectiveReturnWindowDays: 0,
};

const WALLET = {
  id: "wallet-1",
  walletType: "INDIVIDUAL",
  currencyId: "points",
  availableBalance: "500",
  reservedBalance: "0",
};

function renderSheet() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <CatalogItemDetailSheet itemId="item-1" open={true} onOpenChange={vi.fn()} />
    </QueryClientProvider>,
  );
}

describe("CatalogItemDetailSheet — inline redeem drawer (FE-6)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    itemState.data = ITEM;
    itemState.isLoading = false;
    itemState.isError = false;
    walletsState.data = [WALLET];
  });

  it("renders a 'Desired Amount' input pre-filled to the minimum, with no secondary modal", () => {
    renderSheet();
    const input = screen.getByLabelText(/desired amount/i) as HTMLInputElement;
    expect(input.value).toBe("50");
    // The old secondary popup ("Redeem Reward" dialog) is gone from the store flow.
    expect(screen.queryByText(/^redeem reward$/i)).toBeNull();
  });

  it("submits inline, then closes the drawer and navigates to the confirmation on success", async () => {
    const onOpenChange = vi.fn();
    vi.mocked(service.submitPersonalRedemption).mockResolvedValueOnce({
      id: "req-1",
      status: "RESERVED",
      amount: "50",
      currencyId: "points",
      processingMode: "INSTANT",
      estimatedDelivery: "Instant delivery",
      submittedAt: "2026-05-22T00:00:00Z",
      createdAt: "2026-05-22T00:00:00Z",
      updatedAt: "2026-05-22T00:00:00Z",
    });

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <CatalogItemDetailSheet itemId="item-1" open={true} onOpenChange={onOpenChange} />
      </QueryClientProvider>,
    );

    fireEvent.click(screen.getByTestId("redeem-button"));

    await waitFor(() => {
      expect(service.submitPersonalRedemption).toHaveBeenCalledTimes(1);
      expect(onOpenChange).toHaveBeenCalledWith(false);
      expect(navigateMock).toHaveBeenCalledWith("/redemption/confirmation/req-1");
    });
  });

  it("shows an inline field error on a 422 (below minimum / insufficient)", async () => {
    const err = Object.assign(new Error("422"), {
      response: { status: 422, data: { errorMessage: "Amount is below the minimum allowed: 50.00" } },
    });
    vi.mocked(service.submitPersonalRedemption).mockRejectedValueOnce(err);

    renderSheet();
    fireEvent.click(screen.getByTestId("redeem-button"));

    await waitFor(() => {
      expect(screen.getByTestId("field-error").textContent).toContain(
        "Amount is below the minimum allowed: 50.00",
      );
    });
  });

  it("locks the amount for a FIXED-value item (read-only, labelled 'fixed')", () => {
    itemState.data = {
      ...ITEM,
      valueType: "FIXED",
      effectiveMinTransactionAmount: "50",
      effectiveMaxTransactionAmount: "50",
    };
    renderSheet();
    const input = screen.getByLabelText(/amount \(fixed\)/i) as HTMLInputElement;
    expect(input.value).toBe("50");
    expect(input).toHaveAttribute("readonly");
  });

  it("shows the allowed range for a VARIABLE-value item", () => {
    itemState.data = {
      ...ITEM,
      valueType: "VARIABLE",
      effectiveMinTransactionAmount: "50",
      effectiveMaxTransactionAmount: "100",
    };
    renderSheet();
    expect(screen.getByText(/enter an amount between/i)).toBeDefined();
  });

  it("blocks an amount above the VARIABLE max before calling the server", async () => {
    itemState.data = {
      ...ITEM,
      valueType: "VARIABLE",
      effectiveMinTransactionAmount: "50",
      effectiveMaxTransactionAmount: "100",
    };
    renderSheet();
    fireEvent.change(screen.getByLabelText(/desired amount/i), { target: { value: "250" } });
    fireEvent.click(screen.getByTestId("redeem-button"));

    await waitFor(() => {
      expect(screen.getByTestId("field-error").textContent).toMatch(/at most/i);
    });
    expect(service.submitPersonalRedemption).not.toHaveBeenCalled();
  });

  // Regression: the real API sends these BigDecimal fields as JSON numbers, not the declared
  // decimal strings. A numeric pre-filled amount previously threw during render (blank page).
  it("renders when the API sends numeric amounts instead of decimal strings", () => {
    itemState.data = {
      ...ITEM,
      currencyId: "cash",
      category: "CASH",
      valueType: "VARIABLE",
      effectiveMinTransactionAmount: 20.0,
      effectiveMaxTransactionAmount: 2000.0,
      shortfallAmount: 0,
    };
    walletsState.data = [{ ...WALLET, currencyId: "cash", availableBalance: 4479.0 }];

    renderSheet();

    const input = screen.getByLabelText(/desired amount/i) as HTMLInputElement;
    expect(input.value).toBe("20");
    expect(screen.queryByTestId("field-error")).toBeNull();
    expect(screen.getByTestId("redeem-button")).not.toBeDisabled();
    expect(screen.getByText(/enter an amount between/i)).toBeDefined();
  });

  describe("amount validation (client-side mirror of the server rules)", () => {
    const VARIABLE = {
      ...ITEM,
      valueType: "VARIABLE",
      effectiveMinTransactionAmount: "50",
      effectiveMaxTransactionAmount: "1000",
    };

    function typeAmount(value: string) {
      fireEvent.change(screen.getByLabelText(/desired amount/i), { target: { value } });
    }

    it("blocks an amount below the VARIABLE min", () => {
      itemState.data = VARIABLE;
      renderSheet();
      typeAmount("10");

      expect(screen.getByTestId("field-error").textContent).toBe("Amount must be at least 50 pts.");
      expect(screen.getByTestId("redeem-button")).toBeDisabled();
      fireEvent.click(screen.getByTestId("redeem-button"));
      expect(service.submitPersonalRedemption).not.toHaveBeenCalled();
    });

    it("blocks an amount above the VARIABLE max", () => {
      itemState.data = VARIABLE;
      renderSheet();
      typeAmount("1001");

      expect(screen.getByTestId("field-error").textContent).toBe("Amount must be at most 1,000 pts.");
      expect(screen.getByTestId("redeem-button")).toBeDisabled();
    });

    it("blocks zero and negative amounts", () => {
      itemState.data = VARIABLE;
      renderSheet();

      typeAmount("0");
      expect(screen.getByTestId("field-error").textContent).toBe("Amount must be greater than 0.");
      expect(screen.getByTestId("redeem-button")).toBeDisabled();

      typeAmount("-100");
      expect(screen.getByTestId("field-error").textContent).toBe("Amount must be greater than 0.");
    });

    it("blocks a cleared amount", () => {
      itemState.data = VARIABLE;
      renderSheet();
      typeAmount("");

      expect(screen.getByTestId("field-error").textContent).toBe("Enter an amount.");
      expect(screen.getByTestId("redeem-button")).toBeDisabled();
    });

    it("blocks an amount above the wallet's available balance", () => {
      itemState.data = VARIABLE; // max 1000, wallet holds 500
      renderSheet();
      typeAmount("600");

      expect(screen.getByTestId("field-error").textContent).toBe(
        "Amount exceeds your available balance of 500 pts.",
      );
      expect(screen.getByTestId("redeem-button")).toBeDisabled();
      fireEvent.click(screen.getByTestId("redeem-button"));
      expect(service.submitPersonalRedemption).not.toHaveBeenCalled();
    });

    it("accepts an in-range amount and re-enables the button", () => {
      itemState.data = VARIABLE;
      renderSheet();
      typeAmount("0");
      expect(screen.getByTestId("redeem-button")).toBeDisabled();

      typeAmount("250");
      expect(screen.queryByTestId("field-error")).toBeNull();
      expect(screen.getByText(/available:/i)).toBeDefined();
      expect(screen.getByTestId("redeem-button")).not.toBeDisabled();
    });

    it("ignores edits to a FIXED-value amount, so it can never leave its denomination", () => {
      itemState.data = {
        ...ITEM,
        valueType: "FIXED",
        effectiveMinTransactionAmount: "50",
        effectiveMaxTransactionAmount: "50",
      };
      renderSheet();
      const input = screen.getByLabelText(/amount \(fixed\)/i) as HTMLInputElement;

      fireEvent.change(input, { target: { value: "999" } });

      expect(input.value).toBe("50");
      expect(screen.getByTestId("redeem-button")).not.toBeDisabled();
    });

    it("blocks a FIXED denomination the wallet cannot cover", () => {
      itemState.data = {
        ...ITEM,
        valueType: "FIXED",
        effectiveMinTransactionAmount: "800",
        effectiveMaxTransactionAmount: "800",
      };
      renderSheet();

      expect(screen.getByTestId("field-error").textContent).toBe(
        "Amount exceeds your available balance of 500 pts.",
      );
      expect(screen.getByTestId("redeem-button")).toBeDisabled();
    });
  });

  it("toasts on a 409 (max in-flight) — in-flight handling preserved", async () => {
    const err = Object.assign(new Error("409"), {
      response: { status: 409, data: { errorMessage: "Maximum in-flight redemptions reached" } },
    });
    vi.mocked(service.submitPersonalRedemption).mockRejectedValueOnce(err);

    renderSheet();
    fireEvent.click(screen.getByTestId("redeem-button"));

    await waitFor(() => {
      expect(vi.mocked(toast.error)).toHaveBeenCalledWith("Maximum in-flight redemptions reached");
    });
  });
});
