import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { GiftCardEnrollmentNotice } from "@/components/redemption-catalog/GiftCardEnrollmentNotice";

const { navigateMock, profileState } = vi.hoisted(() => ({
  navigateMock: vi.fn(),
  profileState: { data: undefined as unknown, isLoading: false, isError: false },
}));

vi.mock("react-router-dom", async (importActual) => {
  const actual = await importActual<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => navigateMock };
});

// The notice consumes the shared readiness rule (tested directly in
// hooks/redemption-payout/__tests__/useGiftCardPayoutReadiness.test.tsx); these tests drive it from
// the same profile shape so the cases below still read as profile states.
vi.mock("@/hooks/redemption-payout/useRedemptionProfile", () => ({
  useRedemptionProfile: () => profileState,
  useGiftCardPayoutReadiness: () => {
    const profile = profileState.data as { enrollmentStatus?: string } | undefined;
    if (profileState.isLoading || profileState.isError || !profile) {
      return { isReady: true, isKnown: false, enrollmentStatus: null };
    }
    return {
      isReady: profile.enrollmentStatus === "ENROLLED",
      isKnown: true,
      enrollmentStatus: profile.enrollmentStatus ?? null,
    };
  },
}));

describe("GiftCardEnrollmentNotice", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    profileState.data = undefined;
    profileState.isLoading = false;
    profileState.isError = false;
  });

  it("prompts an unenrolled (pending) user and links to payout settings", () => {
    profileState.data = { enrollmentStatus: "NOT_ENROLLED" };
    render(<GiftCardEnrollmentNotice />);

    expect(screen.getByTestId("giftcard-enrollment-notice")).toBeDefined();
    expect(screen.getByText(/finish setting up payouts/i)).toBeDefined();

    fireEvent.click(screen.getByRole("button", { name: /set up payouts/i }));
    expect(navigateMock).toHaveBeenCalledWith("/settings/profile?tab=payout&section=profile");
  });

  it("shows an attention message when enrollment FAILED", () => {
    profileState.data = { enrollmentStatus: "FAILED" };
    render(<GiftCardEnrollmentNotice />);
    expect(screen.getByText(/needs attention/i)).toBeDefined();
  });

  it("renders nothing once ENROLLED", () => {
    profileState.data = { enrollmentStatus: "ENROLLED" };
    const { container } = render(<GiftCardEnrollmentNotice />);
    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing while loading or on error (e.g. a non-payout role)", () => {
    profileState.isLoading = true;
    const { container, rerender } = render(<GiftCardEnrollmentNotice />);
    expect(container).toBeEmptyDOMElement();

    profileState.isLoading = false;
    profileState.isError = true;
    rerender(<GiftCardEnrollmentNotice />);
    expect(container).toBeEmptyDOMElement();
  });
});
