import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { PayoutTab } from "@/components/redemption-payout/PayoutTab";
import type { RedemptionProfileResponse, LinkedBank } from "@/types/redemption-payout/redemption-payout.types";

const { profileState, banksState, noopMutation } = vi.hoisted(() => ({
  profileState: { data: undefined as RedemptionProfileResponse | undefined, isLoading: false, isError: false },
  banksState: { data: [] as LinkedBank[], isLoading: false, isError: false },
  noopMutation: () => ({ mutate: vi.fn(), isPending: false, isError: false, error: null, variables: undefined }),
}));

vi.mock("@/hooks/redemption-payout/useRedemptionProfile", () => ({
  useRedemptionProfile: () => profileState,
  useLinkedBanks: () => banksState,
}));

vi.mock("@/hooks/redemption-payout/useRedemptionProfileMutations", async (importActual) => {
  const actual = await importActual<typeof import("@/hooks/redemption-payout/useRedemptionProfileMutations")>();
  return {
    ...actual,
    // ProfileAddressSection + LinkBankForm call these on render — mock them so no QueryClient is needed.
    useSaveAddress: noopMutation,
    useLinkBankAccount: noopMutation,
    useRemoveBankAccount: noopMutation,
  };
});

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

// The Company Payout tab needs BOTH action.redemption.distribute and a company profile the server is
// willing to return — the permission is shared by every company admin, so only the server can say which one
// of them owns the payout setup. Denied by default so these cases keep describing what a seller sees; the
// cases that need the tab grant it explicitly.
// Activating the tab also renders the setup page, which is a react-query consumer. Mocked rather than
// wrapped in a provider, matching how this file handles its other hooks.
const companyProfileState = vi.hoisted(() => ({
  data: undefined as Record<string, unknown> | undefined,
  isLoading: false,
  error: null as unknown,
}));

vi.mock("@/hooks/useCompanyAdminProfile", () => ({
  useCompanyAdminProfile: () => companyProfileState,
  useCompleteCompanyAdminProfile: () => ({ mutateAsync: vi.fn(), isPending: false }),
  // The setup page rendered inside the tab imports this one; a factory replaces the module wholesale, so
  // anything it imports has to be here or it is undefined at render time.
  isNotYourPayoutSetup: (e: unknown) =>
    (e as { response?: { status?: number } })?.response?.status === 403,
}));

const canDistribute = { value: false };
vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: () => ({
    can: (key: string) =>
      key === "action.redemption.distribute" ? canDistribute.value : true,
    canAny: () => true,
    canAll: () => true,
    permissions: new Set<string>(),
  }),
}));

function profile(overrides: Partial<RedemptionProfileResponse> = {}): RedemptionProfileResponse {
  return {
    enrollmentStatus: "ENROLLED",
    payoutMethod: "BANK",
    bankLinked: false,
    linkedBankLabel: null,
    cardLinked: false,
    linkedCardLabel: null,
    identityLevel: "Standard",
    addressLine1: "1 Market St",
    addressLine2: null,
    city: "San Francisco",
    region: null,
    postalCode: null,
    countryIso2: "US",
    ...overrides,
  };
}

function bank(overrides: Partial<LinkedBank> = {}): LinkedBank {
  return { id: crypto.randomUUID(), label: "Wells Fargo ••1898", currency: "USD", isDefault: false, ...overrides };
}

// PayoutTab reads the active sub-tab from ?section=profile|banks, so it needs a Router.
function renderTab(section?: "profile" | "banks" | "company") {
  const path = section ? `/?section=${section}` : "/";
  return render(
    <MemoryRouter initialEntries={[path]}>
      <PayoutTab />
    </MemoryRouter>,
  );
}

describe("PayoutTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    profileState.data = profile();
    profileState.isLoading = false;
    profileState.isError = false;
    banksState.data = [];
    canDistribute.value = false;
    companyProfileState.data = undefined;
    companyProfileState.error = null;
  });

  /** Both conditions the Company Payout tab needs: the permission, and a profile the server returned. */
  const asCompanyAdmin = () => {
    canDistribute.value = true;
    companyProfileState.data = {
      companyName: "Acme Corp",
      adminEmail: "admin@acme.test",
      complete: false,
    };
  };

  it("shows a loading state while the profile loads", () => {
    profileState.isLoading = true;
    profileState.data = undefined;
    renderTab();
    expect(screen.getByText(/loading payout profile/i)).toBeDefined();
  });

  it("shows an error state when the profile fails to load", () => {
    profileState.isError = true;
    profileState.data = undefined;
    renderTab();
    expect(screen.getByText(/couldn't load your payout profile/i)).toBeDefined();
  });

  it("renders the Ready enrollment status", () => {
    renderTab();
    expect(screen.getByText(/payout status: ready/i)).toBeDefined();
  });

  it("has Payout profile and Bank accounts sub-tabs — no Card or Digital Wallet tabs", () => {
    renderTab();
    expect(screen.getByRole("tab", { name: /payout profile/i })).toBeDefined();
    expect(screen.getByRole("tab", { name: /bank accounts/i })).toBeDefined();
    expect(screen.queryByRole("tab", { name: /cards/i })).toBeNull();
    expect(screen.queryByRole("tab", { name: /digital wallet/i })).toBeNull();
  });

  it("does not render the Wallet withdrawals section", () => {
    renderTab();
    expect(screen.queryByText(/wallet withdrawals/i)).toBeNull();
  });

  it("Bank accounts tab lists banks with Remove only — no default selection", () => {
    banksState.data = [
      bank({ label: "Wells Fargo ••1898", isDefault: true }),
      bank({ label: "SBI ••7820", isDefault: false }),
    ];
    renderTab("banks");
    expect(screen.getByText("Wells Fargo ••1898")).toBeDefined();
    expect(screen.getByText("SBI ••7820")).toBeDefined();
    expect(screen.getAllByRole("button", { name: /^remove$/i })).toHaveLength(2);
    // Default-bank selection was removed — the bank is chosen at redemption time instead.
    expect(screen.queryByRole("button", { name: /set as default/i })).toBeNull();
    expect(screen.queryByText(/^default$/i)).toBeNull();
  });

  it("Bank accounts tab shows an empty state + link button when no bank is linked", () => {
    renderTab("banks");
    expect(screen.getByText(/no bank accounts linked yet/i)).toBeDefined();
    expect(screen.getByRole("button", { name: /link a bank account/i })).toBeDefined();
  });

  it("offers Company Payout to the company's own admin, and not to a seller", async () => {
    // The company payout account pays this admin's sellers — a seller has none, so the tab is theirs only.
    asCompanyAdmin();
    renderTab();
    expect(await screen.findByRole("tab", { name: /company payout/i })).toBeInTheDocument();
  });

  it("hides Company Payout from a second admin the server will not serve", () => {
    // The whole point of this case. Every company admin holds action.redemption.distribute, so the
    // permission alone would show this tab to all of them — while only one admin owns the payout account
    // the company's beneficiary is bound to. The server answers 403 to the others; that refusal, not the
    // permission, is what decides the tab.
    canDistribute.value = true;
    companyProfileState.data = undefined;
    companyProfileState.error = { response: { status: 403 } };

    renderTab();

    expect(screen.queryByRole("tab", { name: /company payout/i })).toBeNull();
  });

  it("keeps the section on Payout profile when the URL asks for a tab that is not there", () => {
    // A hand-typed ?section=company must not reach a tab this admin was refused.
    canDistribute.value = true;
    companyProfileState.data = undefined;
    companyProfileState.error = { response: { status: 403 } };

    renderTab("company");

    expect(screen.getByRole("tab", { name: /payout profile/i })).toHaveAttribute(
      "data-state",
      "active",
    );
  });

  it("actually switches to Company Payout when the tab is clicked", async () => {
    // The tab rendered and the click registered, but the section reader recognised only "banks" and sent
    // everything else back to "profile" — so it looked like a tab that did nothing.
    asCompanyAdmin();
    const user = userEvent.setup();
    renderTab();

    await user.click(await screen.findByRole("tab", { name: /company payout/i }));

    expect(
      await screen.findByRole("tab", { name: /company payout/i }),
    ).toHaveAttribute("data-state", "active");
  });
});