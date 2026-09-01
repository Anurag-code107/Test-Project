import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ProfilePrivacySection } from "@/components/settings/ProfilePrivacySection";

// ── Mock useToast ────────────────────────────────────────────────────────────

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({
    toast: vi.fn(),
  }),
}));

// ── Mock privacy service ─────────────────────────────────────────────────────

vi.mock("@/services/privacy.service", () => ({
  exportMyData: vi.fn(),
  getMyConsent: vi.fn().mockResolvedValue([]),
  updateMyConsent: vi.fn(),
  requestAccountDeletion: vi.fn(),
}));

// ── Mock formatters ──────────────────────────────────────────────────────────

vi.mock("@/utils/formatters", () => ({
  formatDateTime: vi.fn((val: string) => val),
}));

// ── Helper ───────────────────────────────────────────────────────────────────

function renderWithProviders() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ProfilePrivacySection />
    </QueryClientProvider>,
  );
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("ProfilePrivacySection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("does NOT render profile form (moved to MyProfilePage)", () => {
    renderWithProviders();

    expect(screen.queryByLabelText("First Name")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Last Name")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Phone")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /save changes/i }),
    ).not.toBeInTheDocument();
  });

  it("renders privacy section with export and deletion buttons", () => {
    renderWithProviders();

    expect(screen.getByText("Privacy & Data")).toBeInTheDocument();
    expect(screen.getByText("Export My Data")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /export/i })).toBeInTheDocument();

    expect(screen.getByText("Request Account Deletion")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /delete account/i }),
    ).toBeInTheDocument();
  });
});
