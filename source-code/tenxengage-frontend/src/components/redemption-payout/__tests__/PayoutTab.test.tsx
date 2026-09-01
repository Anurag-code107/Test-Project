import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
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
function renderTab(section?: "profile" | "banks") {
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
  });

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
});
