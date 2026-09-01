import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import GlobalCatalogAdminPage from "@/pages/tenx-admin/GlobalCatalogAdminPage";

const mockUseGlobalCatalogItems = vi.fn();
const mockUseUpsertItemConfig = vi.fn();
const mockUseTenantCatalogConfig = vi.fn();
const { mockActivate, mockDeactivate, mockDelete, mockToast } = vi.hoisted(() => ({
  mockActivate: vi.fn(),
  mockDeactivate: vi.fn(),
  mockDelete: vi.fn(),
  mockToast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("sonner", () => ({ toast: mockToast }));

vi.mock("@/hooks/useRedemptionCatalog", () => ({
  useGlobalCatalogItems: (...args: unknown[]) => mockUseGlobalCatalogItems(...args),
  useActivateCatalogItem: () => ({ mutate: mockActivate, isPending: false }),
  useDeactivateCatalogItem: () => ({ mutate: mockDeactivate, isPending: false }),
  useDeleteCatalogItem: () => ({ mutate: mockDelete, isPending: false }),
  useIntegrationHealth: () => ({ isLoading: false, data: null }),
  useTriggerCatalogSync: () => ({ mutate: vi.fn(), isPending: false }),
  useTenantCatalogConfig: (...args: unknown[]) => mockUseTenantCatalogConfig(...args),
  useUpsertItemConfig: () => mockUseUpsertItemConfig(),
}));

vi.mock("@/components/redemption-catalog/GlobalCatalogItemForm", () => ({
  GlobalCatalogItemForm: () => <div data-testid="catalog-item-form" />,
}));
vi.mock("@/components/redemption-catalog/SyncStatusBanner", () => ({
  SyncStatusBanner: () => <div data-testid="sync-banner" />,
}));
vi.mock("@/components/redemption-catalog/TenantRedemptionSettingsForm", () => ({
  TenantRedemptionSettingsForm: () => <div data-testid="tenant-settings-form" />,
}));
vi.mock("@/components/redemption-catalog/RegionalConfigMatrix", () => ({
  RegionalConfigMatrix: () => <div data-testid="regional-config-matrix" />,
}));

const ITEM = {
  id: "item-1",
  name: "Amazon Gift Card",
  category: "NON_CASH",
  currencyId: "points",
  defaultMinRedemptionAmount: "50.00",
  defaultProcessingMode: "INSTANT",
  geographicScope: ["US"],
  isReturnable: false,
  defaultReturnWindowDays: 0,
  isActive: true,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

const PAGINATED_ONE = {
  data: [ITEM],
  totalElements: 1,
  totalPages: 1,
  page: 0,
  pageSize: 20,
  hasNext: false,
  hasPrevious: false,
};

const TENANT_CONFIG_ONE = {
  data: [
    {
      id: "item-1",
      name: "Amazon Gift Card",
      category: "NON_CASH",
      currencyId: "points",
      defaultMinRedemptionAmount: "50.00",
      defaultProcessingMode: "INSTANT",
      geographicScope: ["US"],
      isReturnable: false,
      defaultReturnWindowDays: 0,
      isGloballyActive: true,
      configId: "cfg-1",
      enabled: true,
      createdAt: "2026-05-01T00:00:00Z",
      updatedAt: "2026-05-01T00:00:00Z",
    },
  ],
  totalElements: 1,
  totalPages: 1,
  page: 0,
  pageSize: 100,
  hasNext: false,
  hasPrevious: false,
};

function wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("GlobalCatalogAdminPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseTenantCatalogConfig.mockReturnValue({ data: undefined });
    mockUseUpsertItemConfig.mockReturnValue({ mutate: vi.fn(), isPending: false });
  });

  it("renders skeleton while loading", () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: true, isError: false, data: undefined });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.getByTestId("catalog-skeleton")).toBeDefined();
  });

  it("renders empty state when no items", () => {
    mockUseGlobalCatalogItems.mockReturnValue({
      isLoading: false,
      isError: false,
      data: { data: [], totalElements: 0, totalPages: 0, page: 0, pageSize: 20, hasNext: false, hasPrevious: false },
    });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.getByTestId("catalog-empty")).toBeDefined();
    expect(screen.getByText(/no catalog items yet/i)).toBeDefined();
  });

  it("renders item row with a single Active toggle and no duplicate Status column (Model 2)", () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.getByTestId("catalog-item-row")).toBeDefined();
    expect(screen.getByText("Amazon Gift Card")).toBeDefined();
    // isActive is shown once, by the toggle — a Status badge repeated the same state.
    expect(screen.queryByTestId("status-active")).toBeNull();
    expect(screen.queryByTestId("status-inactive")).toBeNull();
    expect(screen.queryByRole("columnheader", { name: /^status$/i })).toBeNull();
    // Single visibility toggle = isActive; the item is active → "Deactivate ..." affordance.
    expect(screen.getByRole("switch", { name: /deactivate amazon gift card/i })).toBeDefined();
  });

  it("Active toggle reflects isActive: active item shows checked + 'Active' label", () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    const sw = screen.getByRole("switch", { name: /deactivate amazon gift card/i });
    expect(sw.getAttribute("data-state")).toBe("checked");
    expect(screen.getAllByText("Active").length).toBeGreaterThan(0);
  });

  it("Active toggle for an inactive item shows unchecked + 'Inactive' + activate affordance", () => {
    mockUseGlobalCatalogItems.mockReturnValue({
      isLoading: false, isError: false,
      data: { ...PAGINATED_ONE, data: [{ ...ITEM, isActive: false }] },
    });

    render(<GlobalCatalogAdminPage />, { wrapper });

    const sw = screen.getByRole("switch", { name: /activate amazon gift card/i });
    expect(sw.getAttribute("data-state")).toBe("unchecked");
    expect(screen.getAllByText("Inactive").length).toBeGreaterThan(0);
  });

  it("toggling the Active switch calls deactivate (active item) / activate (inactive item)", async () => {
    // Active item → toggling calls deactivate.
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });
    const { unmount } = render(<GlobalCatalogAdminPage />, { wrapper });
    await userEvent.click(screen.getByRole("switch", { name: /deactivate amazon gift card/i }));
    expect(mockDeactivate).toHaveBeenCalledWith(
      "item-1",
      expect.objectContaining({ onError: expect.any(Function) }),
    );
    unmount();

    // Inactive item → toggling calls activate.
    mockUseGlobalCatalogItems.mockReturnValue({
      isLoading: false, isError: false,
      data: { ...PAGINATED_ONE, data: [{ ...ITEM, isActive: false }] },
    });
    render(<GlobalCatalogAdminPage />, { wrapper });
    await userEvent.click(screen.getByRole("switch", { name: /activate amazon gift card/i }));
    expect(mockActivate).toHaveBeenCalledWith(
      "item-1",
      expect.objectContaining({ onError: expect.any(Function) }),
    );
  });

  it("explains a refused activation instead of letting the toggle bounce back silently", async () => {
    mockUseGlobalCatalogItems.mockReturnValue({
      isLoading: false, isError: false,
      data: { ...PAGINATED_ONE, data: [{ ...ITEM, isActive: false }] },
    });
    render(<GlobalCatalogAdminPage />, { wrapper });
    await userEvent.click(screen.getByRole("switch", { name: /activate amazon gift card/i }));

    // Activation enforces SKU uniqueness across live items — replay the server's 409 through the
    // handler the page registered.
    const { onError } = mockActivate.mock.calls[0]?.[1] as { onError: (e: unknown) => void };
    onError({
      response: {
        status: 409,
        data: {
          errorMessage:
            "Another active catalog item with this providerItemId already exists for category CASH",
        },
      },
    });

    // Kept to the bare cause — the admin already knows which row they toggled.
    expect(mockToast.error).toHaveBeenCalledWith("SKU already in use");
  });

  it("falls back to the server's message for a non-conflict activation failure", async () => {
    mockUseGlobalCatalogItems.mockReturnValue({
      isLoading: false, isError: false,
      data: { ...PAGINATED_ONE, data: [{ ...ITEM, isActive: false }] },
    });
    render(<GlobalCatalogAdminPage />, { wrapper });
    await userEvent.click(screen.getByRole("switch", { name: /activate amazon gift card/i }));

    const { onError } = mockActivate.mock.calls[0]?.[1] as { onError: (e: unknown) => void };
    onError({
      response: {
        status: 400,
        data: { errorMessage: "Cannot activate a non-cash catalog item without a provider item ID" },
      },
    });

    expect(mockToast.error).toHaveBeenCalledWith(
      "Cannot activate a non-cash catalog item without a provider item ID",
    );
  });

  it("shows Globally inactive badge when item is not active", () => {
    mockUseGlobalCatalogItems.mockReturnValue({
      isLoading: false,
      isError: false,
      data: { ...PAGINATED_ONE, data: [{ ...ITEM, isActive: false }] },
    });
    mockUseTenantCatalogConfig.mockReturnValue({ data: TENANT_CONFIG_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.getByTestId("globally-inactive-badge")).toBeDefined();
  });

  it("Edit button opens dialog with single-form (no tabs)", async () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });
    mockUseTenantCatalogConfig.mockReturnValue({ data: TENANT_CONFIG_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    await userEvent.click(screen.getByTestId("edit-item-item-1"));

    expect(screen.getByTestId("catalog-item-form")).toBeDefined();
    expect(screen.queryByRole("tab", { name: /tenant settings/i })).toBeNull();
  });

  it("Edit action is an icon button labelled for the item", () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    const edit = screen.getByTestId("edit-item-item-1");
    expect(edit.getAttribute("aria-label")).toMatch(/edit amazon gift card/i);
  });

  it("Delete button opens a confirmation dialog naming the item", async () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.queryByTestId("delete-confirm-dialog")).toBeNull();

    await userEvent.click(screen.getByTestId("delete-item-item-1"));

    const dialog = screen.getByTestId("delete-confirm-dialog");
    expect(dialog).toBeDefined();
    expect(screen.getByText(/delete catalog item\?/i)).toBeDefined();
    // The item name is echoed inside the confirmation copy.
    expect(dialog.textContent).toMatch(/amazon gift card/i);
    expect(mockDelete).not.toHaveBeenCalled();
  });

  it("confirming the dialog calls delete with the item id", async () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    await userEvent.click(screen.getByTestId("delete-item-item-1"));
    await userEvent.click(screen.getByTestId("delete-confirm-button"));

    expect(mockDelete).toHaveBeenCalledTimes(1);
    expect(mockDelete.mock.calls[0]?.[0]).toBe("item-1");
  });

  it("cancelling the dialog does not delete", async () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    await userEvent.click(screen.getByTestId("delete-item-item-1"));
    await userEvent.click(screen.getByRole("button", { name: /cancel/i }));

    expect(mockDelete).not.toHaveBeenCalled();
  });

  it("renders Batch Settings section", () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.getByText("Batch Settings")).toBeDefined();
    expect(screen.getByTestId("tenant-settings-form")).toBeDefined();
  });

  it("hides the regional-availability expand while geographic scope is disabled", () => {
    // CATALOG_GEOGRAPHIC_SCOPE_ENABLED = false — the expand control and its regional-config
    // matrix (the dormant geo feature) are hidden. Restore the click-to-expand assertion
    // from git history when the flag is re-enabled.
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });
    mockUseTenantCatalogConfig.mockReturnValue({ data: TENANT_CONFIG_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.queryByTestId("expand-row-item-1")).toBeNull();
    expect(screen.queryByTestId("regional-config-matrix")).toBeNull();
  });
});
