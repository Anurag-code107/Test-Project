import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RedemptionSubmitModal } from "@/components/redemption-flow/RedemptionSubmitModal";
import type { CatalogBrowseItemResponse } from "@/types/redemption-catalog.types";
import type { RewardWalletResponse } from "@/types/wallet.types";

vi.mock("sonner", () => ({
  toast: Object.assign(vi.fn(), { error: vi.fn() }),
}));

vi.mock("@/services/redemption-flow.service", () => ({
  submitPersonalRedemption: vi.fn(),
}));

import * as service from "@/services/redemption-flow.service";

const ITEM: CatalogBrowseItemResponse = {
  id: "item-1",
  name: "Amazon Gift Card",
  category: "NON_CASH",
  currencyId: "points",
  effectiveMinTransactionAmount: "50",
  effectiveProcessingMode: "INSTANT",
  estimatedPayoutTimeline: "Instant delivery",
  canAfford: true,
  shortfallAmount: "0",
  geographicScope: ["US"],
  isReturnable: false,
  effectiveReturnWindowDays: 0,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

const WALLET: RewardWalletResponse = {
  id: "wallet-1",
  walletType: "INDIVIDUAL",
  currencyId: "points",
  availableBalance: "500",
  reservedBalance: "0",
};

function renderModal(props: Partial<Parameters<typeof RedemptionSubmitModal>[0]> = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <RedemptionSubmitModal
        open={true}
        onOpenChange={vi.fn()}
        item={ITEM}
        wallet={WALLET}
        onSuccess={vi.fn()}
        {...props}
      />
    </QueryClientProvider>,
  );
}

describe("RedemptionSubmitModal", () => {
  it("renders_withMinimumAmountPreFilled", () => {
    renderModal();
    const input = screen.getByLabelText("Amount") as HTMLInputElement;
    expect(input.value).toBe("50");
    expect(screen.getByText(/Available: 500 pts/)).toBeDefined();
  });

  it("showsInlineError_onAmountBelowMinimum", async () => {
    const err = Object.assign(new Error("422"), {
      response: { status: 422, data: { errorMessage: "Amount is below the minimum allowed: 50.00" } },
    });
    vi.mocked(service.submitPersonalRedemption).mockRejectedValueOnce(err);

    renderModal();
    fireEvent.click(screen.getByTestId("submit-button"));

    await waitFor(() => {
      expect(screen.getByTestId("field-error").textContent).toContain(
        "Amount is below the minimum allowed: 50.00",
      );
    });
  });

  it("showsLoadingState_whileSubmitting", async () => {
    vi.mocked(service.submitPersonalRedemption).mockImplementation(
      () => new Promise(() => {}),
    );

    renderModal();
    fireEvent.click(screen.getByTestId("submit-button"));

    await waitFor(() => {
      expect(screen.getByText("Submitting…")).toBeDefined();
    });
  });

  it("closesModal_onSuccess", async () => {
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

    renderModal({ onOpenChange });
    fireEvent.click(screen.getByTestId("submit-button"));

    await waitFor(() => {
      expect(onOpenChange).toHaveBeenCalledWith(false);
    });
  });
});
