import { describe, it, expect, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import DistributionStorePage from "@/pages/distribution/DistributionStorePage";

/**
 * The Distribution Store while XTRM is unavailable.
 *
 * XTRM has no company-to-user transfer API, so Gift Card and Bank Transfer cannot be SENT — but they stay
 * fully browsable, because an admin still needs to see the gift-card picker and their sellers' readiness.
 * Only the send button is withheld.
 *
 * Lives in its own file because `DEFAULT_RAIL` is computed when the page module is first evaluated, so
 * flipping the flag inside the main suite would not move the default.
 */
vi.mock("@/config/redemptionFeatures", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  XTRM_PAYOUT_RAILS_ENABLED: false,
}));

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => ({ user: { partnerCompanyId: "company-1" } }),
}));

vi.mock("@/hooks/useWallet", () => ({
  useCompanyWallet: () => ({
    data: [
      {
        id: "wallet-1",
        walletType: "COMPANY",
        currencyId: "cash",
        availableBalance: "100.00",
        reservedBalance: "0.00",
      },
    ],
    isLoading: false,
  }),
}));

vi.mock("@/hooks/useCompanyDistribution", () => ({
  useDistributionRecipients: () => ({
    data: [
      {
        userId: "s1",
        fullName: "Ana Silva",
        email: "ana@acme.test",
        eligible: true,
        ineligibleReason: null,
        destination: "Cash wallet",
      },
    ],
    isLoading: false,
  }),
  useDistributableGiftCards: () => ({ data: [], isLoading: false }),
  useCreateDistribution: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
// The store reads the company's payout status so it can point at the setup page rather than silently
// showing every recipient as ineligible. CONNECTED here keeps these cases about the rail switch.
vi.mock("@/hooks/useCompanyAdminProfile", () => ({
  useCompanyAdminProfile: () => ({
    data: { xtrmAccount: { status: "CONNECTED" } },
    isLoading: false,
  }),
}));

function renderPage(path = "/redemption/distribution") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <DistributionStorePage />
    </MemoryRouter>,
  );
}

describe("DistributionStorePage — XTRM rails unavailable", () => {
  /** The admin asked to keep seeing these screens — browsing must stay open on every rail. */
  it("leaves every rail clickable even though none can be sent", () => {
    renderPage();

    // Browsable, not hidden: an admin can still open a rail and see their sellers' readiness.
    expect(screen.getByTestId("rail-GIFT_CARD")).not.toBeDisabled();
    expect(screen.getByTestId("rail-BANK_TRANSFER")).not.toBeDisabled();
  });

  it("no longer offers the retired wallet rail", () => {
    renderPage();

    expect(screen.queryByTestId("rail-WALLET_CREDIT")).toBeNull();
  });

  /** Browsing is open; sending is not. The button carries the reason so the block is never mysterious. */
  it("blocks only the send button on a blocked rail, and says why", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByTestId("rail-GIFT_CARD"));

    const strip = screen.getByTestId("review-strip");
    expect(within(strip).getByRole("button", { name: /send/i })).toBeDisabled();
    expect(strip.textContent).toMatch(/temporarily unavailable/i);
  });

  /** ...and the working rail is still fully sendable, which is what keeps the feature usable. */
  it("blocks the send button, because no rail is sendable any more", () => {
    renderPage();

    // Both remaining rails need XTRM, and the wallet rail that used to carry this state is retired — so
    // switching the payout rails off now disables distribution entirely.
    const strip = screen.getByTestId("review-strip");
    expect(strip.textContent).toMatch(/temporarily unavailable/i);
  });

  /** Open on a rail that can actually be sent, so the default path is a working one. */
  it("defaults to a rail it actually offers, even when none can be sent", () => {
    renderPage();

    // The old default named WALLET_CREDIT by name; with no sendable rail it now falls back to the first
    // listed one rather than to a rail the page does not show.
    expect(screen.getByTestId("rail-GIFT_CARD")).toHaveAttribute("data-state", "active");
  });

  /** A link straight to a blocked rail now opens it — the admin wanted to be able to look. */
  it("honours a URL pointing at a blocked rail", () => {
    renderPage("/redemption/distribution?rail=GIFT_CARD");

    expect(screen.getByTestId("rail-GIFT_CARD")).toHaveAttribute("data-state", "active");
  });

  /** The notice belongs on the rail it describes, not permanently above a rail that works fine. */
  it("shows the explanation on a blocked rail and not on the working one", async () => {
    const user = userEvent.setup();
    renderPage();


    await user.click(screen.getByTestId("rail-BANK_TRANSFER"));
    const alert = screen.getByTestId("rails-unavailable");
    expect(alert.textContent).toMatch(/temporarily unavailable/i);
    // No longer points at the wallet rail — there is nothing to point at.
    expect(alert.textContent).not.toMatch(/wallet transfer/i);
  });
});
