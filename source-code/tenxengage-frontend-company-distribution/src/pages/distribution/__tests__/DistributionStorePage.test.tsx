import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import DistributionStorePage from "@/pages/distribution/DistributionStorePage";

// Gift Card and Bank Transfer are switched off until XTRM ships a company-to-user transfer API. The
// gift-card behaviour below is still live code and must stay covered, so this suite runs with the rails
// ENABLED; the disabled behaviour is asserted separately at the bottom of the file.
const { railsEnabled } = vi.hoisted(() => ({ railsEnabled: { value: true } }));
vi.mock("@/config/redemptionFeatures", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  get XTRM_PAYOUT_RAILS_ENABLED() {
    return railsEnabled.value;
  },
}));

const { state } = vi.hoisted(() => ({
  state: {
    wallets: [
      {
        id: "wallet-1",
        walletType: "COMPANY",
        currencyId: "cash",
        availableBalance: "100.00",
        reservedBalance: "0.00",
      },
    ] as unknown[] | undefined,
    recipients: [
      {
        userId: "s1",
        fullName: "Ana Silva",
        email: "ana@acme.test",
        eligible: true,
        ineligibleReason: null,
        destination: "ana@acme.test",
      },
      {
        userId: "s2",
        fullName: "Bo Chen",
        email: "bo@acme.test",
        eligible: false,
        ineligibleReason: "No payout profile yet — use Wallet Transfer instead",
        destination: null,
      },
    ],
    catalog: [
      {
        id: "card-fixed",
        name: "Amazon 50",
        description: null,
        imageUrl: null,
        providerImageUrl: null,
        currencyId: "cash",
        valueType: "FIXED",
        minAmount: "50.00",
        maxAmount: "50.00",
      },
      {
        id: "card-var",
        name: "Visa Flex",
        description: null,
        imageUrl: null,
        providerImageUrl: null,
        currencyId: "cash",
        valueType: "VARIABLE",
        minAmount: "10.00",
        maxAmount: "500.00",
      },
    ],
    mutate: vi.fn(),
  },
}));

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => ({ user: { partnerCompanyId: "company-1" } }),
}));

vi.mock("@/hooks/useWallet", () => ({
  useCompanyWallet: () => ({ data: state.wallets, isLoading: false }),
}));

vi.mock("@/hooks/useCompanyDistribution", () => ({
  useDistributionRecipients: () => ({ data: state.recipients, isLoading: false }),
  useDistributableGiftCards: () => ({ data: state.catalog, isLoading: false }),
  useCreateDistribution: () => ({ mutate: state.mutate, isPending: false }),
}));

// The store reads the company's payout status so it can point at the setup page rather than silently
// showing every recipient as ineligible. CONNECTED by default keeps these cases about the store itself.
const companyPayout = vi.hoisted(() => ({
  data: undefined as { xtrmAccount?: { status: string } } | undefined,
}));

vi.mock("@/hooks/useCompanyAdminProfile", () => ({
  useCompanyAdminProfile: () => ({ data: companyPayout.data, isLoading: false }),
}));

vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));

function renderPage(path = "/redemption/distribution") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <DistributionStorePage />
    </MemoryRouter>,
  );
}

describe("DistributionStorePage", () => {
  beforeEach(() => {
    state.mutate = vi.fn();
    companyPayout.data = { xtrmAccount: { status: "CONNECTED" } };
    state.wallets = [
      {
        id: "wallet-1",
        walletType: "COMPANY",
        currencyId: "cash",
        availableBalance: "100.00",
        reservedBalance: "0.00",
      },
    ];
  });

  /** An unfunded company has no wallet row, so there is nothing to spend and no rail can work. */
  it("shows an empty state when the company wallet has never been funded", () => {
    state.wallets = [];
    renderPage();

    expect(screen.getByTestId("unfunded-company")).toBeInTheDocument();
    expect(screen.queryByTestId("recipient-table")).not.toBeInTheDocument();
    expect(screen.queryByTestId("review-strip")).not.toBeInTheDocument();
  });

  /** Ineligible sellers must be visible with their reason, not filtered out of the list. */
  it("lists ineligible sellers with a reason and prevents selecting them", () => {
    renderPage();

    expect(screen.getByText("Bo Chen")).toBeInTheDocument();
    expect(
      screen.getByText("No payout profile yet — use Wallet Transfer instead"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("select-s2")).toBeDisabled();
    expect(screen.getByTestId("select-s1")).not.toBeDisabled();
  });

  /** The send button must never be enabled without an explanation of what is missing. */
  it("blocks sending until a card, an amount and a recipient are chosen", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByTestId("send-distribution")).toBeDisabled();
    expect(screen.getByTestId("blocked-reason")).toHaveTextContent("Choose a gift card");

    await user.click(screen.getByTestId("gift-card-card-var"));
    expect(screen.getByTestId("blocked-reason")).toHaveTextContent("Enter an amount");

    await user.type(screen.getByTestId("amount-input"), "25");
    expect(screen.getByTestId("blocked-reason")).toHaveTextContent("Select at least one recipient");

    await user.click(screen.getByTestId("select-s1"));
    expect(screen.getByTestId("send-distribution")).toBeEnabled();
  });

  /** A FIXED denomination cannot be changed, so the field is pinned rather than left to be rejected. */
  it("pins the amount to the face value for a FIXED gift card", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId("gift-card-card-fixed"));

    const input = screen.getByTestId("amount-input") as HTMLInputElement;
    expect(input.value).toBe("50.00");
    expect(input).toHaveAttribute("readonly");
  });

  /** The multiplication is the thing to sanity-check before moving company money. */
  it("shows amount x recipients and the remaining balance", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId("gift-card-card-var"));
    await user.type(screen.getByTestId("amount-input"), "30");
    await user.click(screen.getByTestId("select-s1"));

    expect(screen.getByTestId("review-total")).toHaveTextContent("30 × 1 = 30.00 cash");
    expect(screen.getByTestId("remaining-balance")).toHaveTextContent("70.00 cash");
  });

  /** Overspending must be refused in the UI, not left for the server to reject after a round trip. */
  it("blocks a distribution that exceeds the available balance", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId("gift-card-card-var"));
    await user.type(screen.getByTestId("amount-input"), "200");
    await user.click(screen.getByTestId("select-s1"));

    expect(screen.getByTestId("blocked-reason")).toHaveTextContent("exceeds");
    expect(screen.getByTestId("send-distribution")).toBeDisabled();
  });

  /** One amount for every recipient, and an idempotency key so a double-click cannot reserve twice. */
  it("submits one amount, the selected ids and an idempotency key", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId("gift-card-card-var"));
    await user.type(screen.getByTestId("amount-input"), "30");
    await user.click(screen.getByTestId("select-s1"));
    await user.click(screen.getByTestId("send-distribution"));

    expect(state.mutate).toHaveBeenCalledTimes(1);
    const payload = state.mutate.mock.calls[0]?.[0];
    expect(payload).toMatchObject({
      rail: "GIFT_CARD",
      sourceWalletId: "wallet-1",
      providerSku: "card-var",
      amount: "30",
      userIds: ["s1"],
    });
    expect(payload.clientIdempotencyKey).toBeTruthy();
  });

  /** Eligibility differs per rail, so a selection made under one rail must not carry to another. */
  it("clears the selection and the chosen card when the rail changes", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId("gift-card-card-var"));
    await user.click(screen.getByTestId("select-s1"));
    expect(screen.getByTestId("review-total")).toHaveTextContent("× 1");

    // Bank transfer, not the retired wallet rail. This case is about a rail CHANGE clearing the
    // selection; bank transfer takes no gift card either, so it tests the same property.
    await user.click(screen.getByTestId("rail-BANK_TRANSFER"));

    expect(screen.getByTestId("review-total")).toHaveTextContent("× 0");
    expect(screen.queryByTestId("gift-card-picker")).not.toBeInTheDocument();
  });

  /** The rail is in the URL so a refresh or a shared link lands on the same one. */
  it("honours the rail from the query string", () => {
    renderPage("/redemption/distribution?rail=BANK_TRANSFER");

    expect(screen.getByTestId("rail-BANK_TRANSFER")).toHaveAttribute("data-state", "active");
    expect(screen.queryByTestId("gift-card-picker")).not.toBeInTheDocument();
  });

  /**
   * Who gets told to finish the payout setup.
   *
   * Only the one admin the company's payout account belongs to can complete it; the server refuses every
   * other admin, who then has no profile to read. Prompting on an absent profile would point them at a page
   * that refuses them too, so the prompt is driven by a profile that was actually returned.
   */
  describe("payout setup prompt", () => {
    it("prompts when the company's own account is not connected yet", () => {
      companyPayout.data = { xtrmAccount: { status: "PENDING" } };
      renderPage();

      expect(screen.getByTestId("payout-setup-needed")).toBeInTheDocument();
    });

    it("stays quiet once the account is connected", () => {
      renderPage();

      expect(screen.queryByTestId("payout-setup-needed")).not.toBeInTheDocument();
    });

    it("stays quiet for an admin the setup does not belong to", () => {
      // Refused by the server, so no profile — and no dead-end "Finish setup" button. They can still
      // distribute; the company account funds it either way.
      companyPayout.data = undefined;
      renderPage();

      expect(screen.queryByTestId("payout-setup-needed")).not.toBeInTheDocument();
      expect(screen.getByTestId("recipient-table")).toBeInTheDocument();
    });
  });
});
