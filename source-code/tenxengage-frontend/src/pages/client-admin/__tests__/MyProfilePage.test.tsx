import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import MyProfilePage from "@/pages/client-admin/MyProfilePage";
import type { ProfileFieldResponse } from "@/types/profile.types";

// ── Configurable permission mock ────────────────────────────────────────────

let mockPermissions: string[] = [];

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => ({
    user: {
      id: "1",
      email: "test@example.com",
      firstName: "John",
      lastName: "Doe",
      roles: [],
      permissions: mockPermissions,
      clientRoleId: null,
      clientRoleName: "Partner Admin",
      organizationId: null,
      clientId: "client-1",
      clientName: "Test Corp",
      partnerCompanyId: "partner-1",
      partnerCompanyName: "Partner Co",
      status: "ACTIVE" as const,
    },
    isAuthenticated: true,
    isLoading: false,
    appRole: null,
    enabledFeatures: [],
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

vi.mock("@/services/privacy.service", () => ({
  exportMyData: vi.fn(),
  getMyConsent: vi.fn().mockResolvedValue([]),
  updateMyConsent: vi.fn(),
  requestAccountDeletion: vi.fn(),
}));

vi.mock("@/utils/formatters", () => ({
  formatDateTime: vi.fn((val: string) => val),
}));

// ── Mock profile fields API ─────────────────────────────────────────────────

const mockProfileFields: ProfileFieldResponse[] = [
  {
    fieldId: "f1",
    fieldName: "Employee First Name",
    dataType: "TEXT",
    value: "John",
    editable: true,
    sortOrder: 1,
    sampleValues: null,
  },
  {
    fieldId: "f2",
    fieldName: "Employee Last Name",
    dataType: "TEXT",
    value: "Doe",
    editable: true,
    sortOrder: 2,
    sampleValues: null,
  },
  {
    fieldId: "f3",
    fieldName: "Partner Name",
    dataType: "TEXT",
    value: "Partner Co",
    editable: false,
    sortOrder: 0,
    sampleValues: null,
  },
];

let mockFieldsLoading = false;

vi.mock("@/hooks/useProfileApi", () => ({
  useProfileFields: () => ({
    data: mockFieldsLoading ? undefined : mockProfileFields,
    isLoading: mockFieldsLoading,
  }),
  useUpdateProfile: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}));

vi.mock("@/components/PageBanner", () => ({
  PageBanner: () => <div data-testid="page-banner" />,
}));

vi.mock("@/components/NotificationPreferencesPanel", () => ({
  NotificationPreferencesPanel: () => (
    <div data-testid="notification-panel">Notifications</div>
  ),
}));

// ── Helper ──────────────────────────────────────────────────────────────────

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <MyProfilePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

// ── Tests ───────────────────────────────────────────────────────────────────

describe("MyProfilePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPermissions = [];
    mockFieldsLoading = false;
  });

  it("renders My Profile, Notifications, and Support tabs", () => {
    renderPage();
    expect(screen.getByRole("tab", { name: "My Profile" })).toBeInTheDocument();
    expect(
      screen.getByRole("tab", { name: "Notifications" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Support" })).toBeInTheDocument();
  });

  it("renders Privacy & Data tab when user has action.profile.export_data permission", () => {
    mockPermissions = ["action.profile.export_data"];
    renderPage();
    expect(
      screen.getByRole("tab", { name: "Privacy & Data" }),
    ).toBeInTheDocument();
  });

  it("does NOT render Privacy & Data tab when user lacks permission", () => {
    mockPermissions = [];
    renderPage();
    expect(
      screen.queryByRole("tab", { name: "Privacy & Data" }),
    ).not.toBeInTheDocument();
  });

  it("default tab is My Profile", () => {
    renderPage();
    const profileTab = screen.getByRole("tab", { name: "My Profile" });
    expect(profileTab).toHaveAttribute("data-state", "active");
  });

  // ── Dynamic profile fields ──────────────────────────────────────────────

  it("renders dynamic profile fields from API", () => {
    mockPermissions = ["action.profile.edit"];
    renderPage();

    expect(screen.getByText("Employee First Name")).toBeInTheDocument();
    expect(screen.getByText("Employee Last Name")).toBeInTheDocument();
    expect(screen.getByText("Partner Name")).toBeInTheDocument();
  });

  it("renders editable inputs for editable fields", () => {
    mockPermissions = ["action.profile.edit"];
    renderPage();

    const firstNameInput = screen.getByLabelText("Employee First Name");
    expect(firstNameInput).not.toHaveAttribute("readonly");

    const lastNameInput = screen.getByLabelText("Employee Last Name");
    expect(lastNameInput).not.toHaveAttribute("readonly");
  });

  it("renders read-only inputs for non-editable fields", () => {
    mockPermissions = ["action.profile.edit"];
    renderPage();

    const partnerInput = screen.getByLabelText("Partner Name");
    expect(partnerInput).toHaveAttribute("readonly");
  });

  it("shows Save Changes button when editable fields exist", () => {
    mockPermissions = ["action.profile.edit"];
    renderPage();

    expect(
      screen.getByRole("button", { name: /save changes/i }),
    ).toBeInTheDocument();
  });

  it("shows loading state while fields are being fetched", () => {
    mockFieldsLoading = true;
    renderPage();

    expect(screen.getByText("Loading profile fields...")).toBeInTheDocument();
  });

  it("always renders Profile Header with client name", () => {
    renderPage();
    expect(screen.getByText("Test Corp")).toBeInTheDocument();
    expect(screen.getByText("John Doe")).toBeInTheDocument();
  });

  it("always renders Password & Security section", () => {
    renderPage();
    expect(screen.getByText("Password & Security")).toBeInTheDocument();
  });
});
