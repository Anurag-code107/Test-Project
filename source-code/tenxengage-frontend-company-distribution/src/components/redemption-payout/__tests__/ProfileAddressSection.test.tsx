import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ProfileAddressSection } from "@/components/redemption-payout/ProfileAddressSection";
import type { RedemptionProfileResponse } from "@/types/redemption-payout/redemption-payout.types";

const { saveAddress } = vi.hoisted(() => ({
  saveAddress: { mutate: vi.fn(), isPending: false },
}));

vi.mock("@/hooks/redemption-payout/useRedemptionProfileMutations", () => ({
  useSaveAddress: () => saveAddress,
}));

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

function profile(o: Partial<RedemptionProfileResponse> = {}): RedemptionProfileResponse {
  return {
    enrollmentStatus: "ENROLLED",
    payoutMethod: "ANYPAY",
    bankLinked: false,
    linkedBankLabel: null,
    cardLinked: false,
    linkedCardLabel: null,
    identityLevel: "Standard",
    addressLine1: "1234 Market St",
    addressLine2: null,
    city: "San Francisco",
    region: null,
    postalCode: null,
    countryIso2: "US",
    ...o,
  };
}

describe("ProfileAddressSection", () => {
  beforeEach(() => vi.clearAllMocks());

  it("collapses to a one-line summary once enrolled with a saved address", () => {
    render(<ProfileAddressSection profile={profile()} />);
    expect(screen.getByText(/payout profile/i)).toBeDefined();
    expect(screen.getByText(/1234 Market St, San Francisco, US/)).toBeDefined();
    // Form is collapsed — no address input visible.
    expect(screen.queryByLabelText(/address line 1/i)).toBeNull();
    expect(screen.getByRole("button", { name: /^edit$/i })).toBeDefined();
  });

  it("expands to the form when Edit is clicked", () => {
    render(<ProfileAddressSection profile={profile()} />);
    fireEvent.click(screen.getByRole("button", { name: /^edit$/i }));
    expect(screen.getByLabelText(/address line 1/i)).toBeDefined();
    expect(screen.getByRole("button", { name: /update address/i })).toBeDefined();
    expect(screen.getByRole("button", { name: /cancel/i })).toBeDefined();
  });

  it("shows the enroll form (not collapsed) when not yet enrolled", () => {
    render(<ProfileAddressSection profile={profile({ enrollmentStatus: "NOT_ENROLLED", addressLine1: null, city: null, countryIso2: null })} />);
    expect(screen.getByLabelText(/address line 1/i)).toBeDefined();
    expect(screen.getByRole("button", { name: /save & enroll/i })).toBeDefined();
  });
});
