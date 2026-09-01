import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import OnboardingPage from "@/pages/OnboardingPage";

// ── Mock onboarding service ──────────────────────────────────────────────────

const mockValidateOnboardingToken = vi.fn();
const mockSetPassword = vi.fn();
const mockCompleteProfile = vi.fn();
const mockAcceptPolicies = vi.fn();
const mockSetConsent = vi.fn();
const mockCompleteOnboarding = vi.fn();
const mockGetPolicies = vi.fn();
const mockGetConsentPreferences = vi.fn();

vi.mock("@/services/onboarding.service", () => ({
  validateOnboardingToken: (...args: unknown[]) =>
    mockValidateOnboardingToken(...args),
  setPassword: (...args: unknown[]) => mockSetPassword(...args),
  completeProfile: (...args: unknown[]) => mockCompleteProfile(...args),
  acceptPolicies: (...args: unknown[]) => mockAcceptPolicies(...args),
  setConsent: (...args: unknown[]) => mockSetConsent(...args),
  completeOnboarding: (...args: unknown[]) => mockCompleteOnboarding(...args),
  getPolicies: (...args: unknown[]) => mockGetPolicies(...args),
  getConsentPreferences: (...args: unknown[]) =>
    mockGetConsentPreferences(...args),
}));

// ── Mock react-router-dom ────────────────────────────────────────────────────

const mockSearchParams = new URLSearchParams("token=test-token");
const mockNavigate = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useSearchParams: () => [mockSearchParams, vi.fn()],
    useNavigate: () => mockNavigate,
  };
});

// ── Mock logo asset ──────────────────────────────────────────────────────────

vi.mock("@/assets/web_logo.png", () => ({ default: "mock-logo.png" }));

// ── Tests ────────────────────────────────────────────────────────────────────

describe("OnboardingPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders loading state initially while validating token", () => {
    // Keep the promise pending to observe loading state
    mockValidateOnboardingToken.mockReturnValue(new Promise(() => {}));

    render(<OnboardingPage />);

    expect(
      screen.getByText("Setting up your onboarding..."),
    ).toBeInTheDocument();
  });

  it("shows error when token parameter is missing", () => {
    mockSearchParams.delete("token");

    render(<OnboardingPage />);

    expect(screen.getByText("Invalid or Expired Link")).toBeInTheDocument();
    expect(
      screen.getByText(/No onboarding token was provided/),
    ).toBeInTheDocument();

    // Restore token for subsequent tests
    mockSearchParams.set("token", "test-token");
  });

  it("shows error when token validation fails (invalid/expired)", async () => {
    mockValidateOnboardingToken.mockRejectedValue(new Error("Token expired"));

    render(<OnboardingPage />);

    await waitFor(() => {
      expect(screen.getByText("Invalid or Expired Link")).toBeInTheDocument();
    });

    expect(screen.getByText(/invalid or has expired/)).toBeInTheDocument();
  });

  it("renders Set Password step when token is valid at step 0", async () => {
    mockValidateOnboardingToken.mockResolvedValue({
      userId: "user-1",
      email: "test@example.com",
      firstName: "Jane",
      lastName: "Doe",
      currentStep: 0,
      completed: false,
      region: "US",
      complianceConfig: null,
    });

    render(<OnboardingPage />);

    await waitFor(() => {
      expect(screen.getByText("Create Your Password")).toBeInTheDocument();
    });

    expect(
      screen.getByPlaceholderText("Min. 8 characters"),
    ).toBeInTheDocument();
    expect(screen.getByText("Set Password & Continue")).toBeInTheDocument();
  });
});
