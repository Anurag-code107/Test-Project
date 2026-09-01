import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
// ── Mock hooks ──────────────────────────────────────────────────────────────

const mockCreateMutateAsync = vi.fn();
const mockCloneMutateAsync = vi.fn();
const mockUpdatePermsMutateAsync = vi.fn();
const mockToast = vi.fn();

vi.mock("@/hooks/usePermissionApi", () => ({
  usePermissionCatalog: () => ({ data: [], isLoading: false }),
  useClientRoles: () => ({ data: [], isLoading: false }),
  useCreateClientRole: () => ({
    mutateAsync: mockCreateMutateAsync,
    isPending: false,
  }),
  useCloneClientRole: () => ({
    mutateAsync: mockCloneMutateAsync,
    isPending: false,
  }),
  useUpdateClientRole: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
  useUpdateRolePermissions: () => ({
    mutateAsync: mockUpdatePermsMutateAsync,
    isPending: false,
  }),
  useDeleteClientRole: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
  useCompanyOverrides: () => ({ data: undefined }),
  useUpdateCompanyOverrides: () => ({ mutateAsync: vi.fn() }),
  useUserOverrides: () => ({ data: undefined }),
  useUpdateUserOverrides: () => ({ mutateAsync: vi.fn() }),
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: mockToast }),
}));

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => ({
    user: {
      id: "u1",
      email: "admin@test.com",
      firstName: "Admin",
      lastName: "User",
      roles: [],
      permissions: [],
      clientRoleId: null,
      clientRoleName: null,
      organizationId: null,
      clientId: null,
      clientName: null,
      partnerCompanyId: null,
      partnerCompanyName: null,
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

vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: () => ({
    can: () => true,
    canAny: () => true,
    canAll: () => true,
    permissions: new Set(["action.roles.view", "action.roles.create"]),
  }),
}));

vi.mock("@/hooks/useApi", () => ({
  useUsers: () => ({ data: [], isLoading: false }),
  useCreateUser: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateUser: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteUser: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/usePartnerCompanyApi", () => ({
  usePartnerCompanies: () => ({ data: [], isLoading: false }),
  useCreatePartnerCompany: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdatePartnerCompany: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeletePartnerCompany: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/useDataObjectApi", () => ({
  useDataObjectByName: () => ({ data: null, isLoading: false }),
}));

vi.mock("sonner", () => ({
  toast: vi.fn(),
}));

vi.mock("@/components/PageBanner", () => ({
  PageBanner: () => null,
}));

vi.mock("@/components/FlipTransition", () => ({
  FlipTransition: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
}));

// We import the page after all mocks are set up, since CreateRoleSheet is
// an internal component of UserSettingsPage. We'll open the sheet via the
// "Create Role" button on the Roles tab.
import UserSettingsPage from "@/pages/client-admin/UserSettingsPage";

// ── Helpers ──────────────────────────────────────────────────────────────────

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <UserSettingsPage />
    </QueryClientProvider>,
  );
}

async function openCreateRoleSheet() {
  const user = userEvent.setup();
  renderPage();

  // Navigate to the Roles tab
  const rolesTab = screen.getByRole("tab", { name: /roles/i });
  await user.click(rolesTab);

  // Click "Create Role" button
  const createBtn = screen.getByRole("button", { name: /create role/i });
  await user.click(createBtn);

  return user;
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("CreateRoleSheet", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCreateMutateAsync.mockResolvedValue({
      id: "new-role-id",
      name: "New Role",
      roleType: "EXTERNAL",
      permissions: {},
    });
    mockCloneMutateAsync.mockResolvedValue({
      id: "cloned-role-id",
      name: "Cloned Role",
      roleType: "EXTERNAL",
      permissions: { "module.home": true, "action.claim.submit": true },
    });
    mockUpdatePermsMutateAsync.mockResolvedValue({});
  });

  it("renders sheet with mode toggle and form fields", async () => {
    await openCreateRoleSheet();

    expect(screen.getByText("Create Custom Role")).toBeInTheDocument();
    expect(screen.getByText("Start from Scratch")).toBeInTheDocument();
    expect(screen.getByText("Clone Existing Role")).toBeInTheDocument();
    expect(screen.getByLabelText(/role name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
  });

  it("shows role type select in scratch mode", async () => {
    await openCreateRoleSheet();

    expect(screen.getByLabelText(/role type/i)).toBeInTheDocument();
  });

  it("shows clone source dropdown in clone mode", async () => {
    const user = await openCreateRoleSheet();

    await user.click(screen.getByText("Clone Existing Role"));

    expect(screen.getByText("Clone From *")).toBeInTheDocument();
    expect(screen.getByText("Select a role to clone...")).toBeInTheDocument();
  });

  it("disables create button when name is empty", async () => {
    await openCreateRoleSheet();

    // The "Create Role" button inside the sheet footer (not the trigger button)
    const sheet = screen.getByRole("dialog");
    const createBtn = within(sheet).getByRole("button", {
      name: /create role/i,
    });
    expect(createBtn).toBeDisabled();
  });

  it("disables create button in clone mode when no source selected", async () => {
    const user = await openCreateRoleSheet();

    await user.click(screen.getByText("Clone Existing Role"));

    // Type a name
    const nameInput = screen.getByLabelText(/role name/i);
    await user.type(nameInput, "My New Role");

    const sheet = screen.getByRole("dialog");
    const createBtn = within(sheet).getByRole("button", {
      name: /create role/i,
    });
    expect(createBtn).toBeDisabled();
  });

  it("calls createRole with roleType and permissions for scratch mode", async () => {
    const user = await openCreateRoleSheet();

    // Fill in name
    const nameInput = screen.getByLabelText(/role name/i);
    await user.type(nameInput, "Test Role");

    // Fill in description
    const descInput = screen.getByLabelText(/description/i);
    await user.type(descInput, "A test role");

    // Submit — default roleType is EXTERNAL
    const sheet = screen.getByRole("dialog");
    const createBtn = within(sheet).getByRole("button", {
      name: /create role/i,
    });
    await user.click(createBtn);

    expect(mockCreateMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        name: "Test Role",
        description: "A test role",
        roleType: "EXTERNAL",
        permissions: expect.any(Object),
      }),
    );
  });

  it("shows success toast after creation", async () => {
    const user = await openCreateRoleSheet();

    const nameInput = screen.getByLabelText(/role name/i);
    await user.type(nameInput, "Test Role");

    const sheet = screen.getByRole("dialog");
    const createBtn = within(sheet).getByRole("button", {
      name: /create role/i,
    });
    await user.click(createBtn);

    expect(mockToast).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "Role Created",
      }),
    );
  });

  it("shows error toast on failure", async () => {
    mockCreateMutateAsync.mockRejectedValueOnce(new Error("fail"));

    const user = await openCreateRoleSheet();

    const nameInput = screen.getByLabelText(/role name/i);
    await user.type(nameInput, "Test Role");

    const sheet = screen.getByRole("dialog");
    const createBtn = within(sheet).getByRole("button", {
      name: /create role/i,
    });
    await user.click(createBtn);

    expect(mockToast).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "Error",
        variant: "destructive",
      }),
    );
  });

  it("resets state when switching from clone to scratch mode", async () => {
    const user = await openCreateRoleSheet();

    // Switch to clone mode
    await user.click(screen.getByText("Clone Existing Role"));
    expect(screen.getByText("Select a role to clone...")).toBeInTheDocument();

    // Switch back to scratch
    await user.click(screen.getByText("Start from Scratch"));
    expect(screen.getByLabelText(/role type/i)).toBeInTheDocument();

    // Clone combobox should not be visible
    expect(
      screen.queryByText("Select a role to clone..."),
    ).not.toBeInTheDocument();
  });
});
