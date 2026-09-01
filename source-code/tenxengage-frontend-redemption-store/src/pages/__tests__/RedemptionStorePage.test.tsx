import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import RedemptionStorePage from "@/pages/RedemptionStorePage";

const { readiness } = vi.hoisted(() => ({
  readiness: { isReady: true, isKnown: true, enrollmentStatus: "ENROLLED" as string | null },
}));

vi.mock("@/hooks/redemption-payout/useRedemptionProfile", () => ({
  useGiftCardPayoutReadiness: () => readiness,
}));

vi.mock("@/components/redemption-catalog/CatalogBrowseGrid", () => ({
  CatalogBrowseGrid: ({ disabledReason }: { disabledReason?: string | null }) => (
    <div data-testid="catalog-grid" data-disabled-reason={disabledReason ?? ""} />
  ),
}));

vi.mock("@/components/redemption-catalog/BankTransferPanel", () => ({
  BankTransferPanel: () => <div data-testid="bank-panel" />,
}));

vi.mock("@/components/redemption-catalog/CatalogItemDetailSheet", () => ({
  CatalogItemDetailSheet: () => null,
}));

vi.mock("@/components/redemption-catalog/GiftCardEnrollmentNotice", () => ({
  GiftCardEnrollmentNotice: () => null,
}));

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <RedemptionStorePage />
    </MemoryRouter>,
  );
}

// `readiness` is a shared mutable module mock — reset it for every test in the file so a case that
// flips it to "not set up" can't leak into the next describe block.
beforeEach(() => {
  readiness.isReady = true;
  readiness.isKnown = true;
  readiness.enrollmentStatus = "ENROLLED";
});

describe("RedemptionStorePage — gift-card availability gating", () => {
  it("leaves the catalog active when the payout profile is ready", () => {
    renderAt("/store");
    expect(screen.getByTestId("catalog-grid").getAttribute("data-disabled-reason")).toBe("");
  });

  it("marks every card inactive when the payout profile is not set up", () => {
    readiness.isReady = false;
    readiness.enrollmentStatus = "NOT_ENROLLED";
    renderAt("/store");
    expect(screen.getByTestId("catalog-grid").getAttribute("data-disabled-reason")).toBe(
      "Set up payouts to redeem gift cards.",
    );
  });

  it("marks the catalog inactive when enrollment FAILED too", () => {
    readiness.isReady = false;
    readiness.enrollmentStatus = "FAILED";
    renderAt("/store");
    expect(screen.getByTestId("catalog-grid").getAttribute("data-disabled-reason")).not.toBe("");
  });

  // Bank transfer needs a linked bank, which needs the payout profile first.
  it("enables the Bank Transfer tab when the payout profile is ready", () => {
    renderAt("/store");
    expect(screen.getByTestId("bank-mode-tab")).not.toBeDisabled();
  });

  it("disables the Bank Transfer tab when the payout profile is not set up", () => {
    readiness.isReady = false;
    renderAt("/store");
    expect(screen.getByTestId("bank-mode-tab")).toBeDisabled();
  });

  it("does not switch to bank mode when the disabled tab is clicked", async () => {
    const user = userEvent.setup();
    readiness.isReady = false;
    renderAt("/store");

    await user.click(screen.getByTestId("bank-mode-tab"));

    expect(screen.getByTestId("catalog-grid")).toBeDefined();
    expect(screen.queryByTestId("bank-panel")).toBeNull();
  });

  it("falls back to gift cards for a ?mode=bank deep-link without a payout profile", () => {
    readiness.isReady = false;
    renderAt("/store?mode=bank");

    expect(screen.getByTestId("catalog-grid")).toBeDefined();
    expect(screen.queryByTestId("bank-panel")).toBeNull();
    expect(screen.getByText(/browse available rewards/i)).toBeDefined();
  });

  it("still honors ?mode=bank once the payout profile is ready", () => {
    renderAt("/store?mode=bank");
    expect(screen.getByTestId("bank-panel")).toBeDefined();
  });

  it("shows why the tab is unavailable on hover", async () => {
    const user = userEvent.setup();
    readiness.isReady = false;
    renderAt("/store");

    await user.hover(screen.getByTestId("bank-mode-tab").parentElement!);
    await waitFor(() => {
      expect(screen.getAllByText(/set up payouts to transfer to your bank/i).length).toBeGreaterThan(0);
    });
  });
});

describe("RedemptionStorePage — payment-mode toggle (FE-1)", () => {
  it("defaults to Gift Card mode (catalog grid) with the gift-card subheading", () => {
    renderAt("/store");
    expect(screen.getByTestId("catalog-grid")).toBeDefined();
    expect(screen.queryByTestId("bank-panel")).toBeNull();
    expect(screen.getByText(/browse available rewards/i)).toBeDefined();
  });

  it("toggling to Bank Transfer swaps the grid + subheading for the bank panel", async () => {
    const user = userEvent.setup();
    renderAt("/store");
    await user.click(screen.getByRole("tab", { name: /bank transfer/i }));
    expect(screen.getByTestId("bank-panel")).toBeDefined();
    expect(screen.queryByTestId("catalog-grid")).toBeNull();
    // Subheading switches to a bank-transfer message; the gift-card copy is gone.
    expect(screen.getByText(/redeem your cash balance directly to your linked bank account/i)).toBeDefined();
    expect(screen.queryByText(/browse available rewards/i)).toBeNull();
  });

  it("honors ?mode=bank on load (deep-link / refresh survives)", () => {
    renderAt("/store?mode=bank");
    expect(screen.getByTestId("bank-panel")).toBeDefined();
    expect(screen.queryByTestId("catalog-grid")).toBeNull();
  });
});
