import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { TenantCatalogConfigTable } from "@/components/redemption-catalog/TenantCatalogConfigTable";

const mockUseTenantCatalogConfig = vi.fn();
const mockUseUpsertItemConfig = vi.fn();

vi.mock("@/hooks/useRedemptionCatalog", () => ({
  useTenantCatalogConfig: (...args: unknown[]) => mockUseTenantCatalogConfig(...args),
  useUpsertItemConfig: () => mockUseUpsertItemConfig(),
  useRegionalConfig: () => ({ isLoading: false, data: [] }),
  useUpsertRegionConfig: () => ({ mutate: vi.fn(), isPending: false }),
  useDeleteRegionConfig: () => ({ mutate: vi.fn(), isPending: false }),
}));

const ITEM_GLOBALLY_INACTIVE = {
  id: "item-1",
  name: "Test Gift Card",
  description: "A test item",
  category: "NON_CASH",
  currencyId: "points",
  defaultMinRedemptionAmount: "50.00",
  defaultProcessingMode: "INSTANT",
  geographicScope: ["US"],
  isReturnable: false,
  defaultReturnWindowDays: 0,
  isGloballyActive: false,
  tenantConfig: null,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

const ITEM_ACTIVE = {
  ...ITEM_GLOBALLY_INACTIVE,
  id: "item-2",
  name: "Coffee Voucher",
  isGloballyActive: true,
  tenantConfig: {
    id: "cfg-1",
    redemptionCatalogItemId: "item-2",
    enabled: false,
    createdAt: "2026-05-01T00:00:00Z",
    updatedAt: "2026-05-01T00:00:00Z",
  },
};

function paginatedResponse(items: unknown[]) {
  return {
    data: items,
    page: 0,
    pageSize: 20,
    totalElements: items.length,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  };
}

function wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("TenantCatalogConfigTable", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseUpsertItemConfig.mockReturnValue({ mutate: vi.fn(), isPending: false });
  });

  it("renders globally inactive warning badge when isGloballyActive is false", () => {
    mockUseTenantCatalogConfig.mockReturnValue({
      isLoading: false,
      isError: false,
      data: paginatedResponse([ITEM_GLOBALLY_INACTIVE]),
    });

    render(<TenantCatalogConfigTable />, { wrapper });

    expect(screen.getByTestId("globally-inactive-badge")).toBeDefined();
    expect(screen.getByText(/globally inactive/i)).toBeDefined();
  });

  it("toggle calls upsertItemConfig with enabled=true", async () => {
    const mutate = vi.fn();
    mockUseUpsertItemConfig.mockReturnValue({ mutate, isPending: false });
    mockUseTenantCatalogConfig.mockReturnValue({
      isLoading: false,
      isError: false,
      data: paginatedResponse([ITEM_ACTIVE]),
    });

    render(<TenantCatalogConfigTable />, { wrapper });

    const toggle = screen.getByTestId("enable-switch");
    await userEvent.click(toggle);

    expect(mutate).toHaveBeenCalledWith(
      { catalogItemId: "item-2", request: { enabled: true } },
      expect.any(Object),
    );
  });

  it("renders skeleton while loading", () => {
    mockUseTenantCatalogConfig.mockReturnValue({
      isLoading: true,
      isError: false,
      data: undefined,
    });

    render(<TenantCatalogConfigTable />, { wrapper });

    expect(screen.getByTestId("catalog-config-skeleton")).toBeDefined();
  });
});
