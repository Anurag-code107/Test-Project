import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import GlobalCatalogAdminPage from "@/pages/tenx-admin/GlobalCatalogAdminPage";

const mockUseGlobalCatalogItems = vi.fn();

vi.mock("@/hooks/useRedemptionCatalog", () => ({
  useGlobalCatalogItems: (...args: unknown[]) => mockUseGlobalCatalogItems(...args),
  useActivateCatalogItem: () => ({ mutate: vi.fn(), isPending: false }),
  useDeactivateCatalogItem: () => ({ mutate: vi.fn(), isPending: false }),
  useIntegrationHealth: () => ({ isLoading: false, data: null }),
  useTriggerCatalogSync: () => ({ mutate: vi.fn(), isPending: false }),
}));

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

  it("renders item list when data loaded", () => {
    mockUseGlobalCatalogItems.mockReturnValue({
      isLoading: false,
      isError: false,
      data: {
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
            isActive: true,
            createdAt: "2026-05-01T00:00:00Z",
            updatedAt: "2026-05-01T00:00:00Z",
          },
        ],
        totalElements: 1,
        totalPages: 1,
        page: 0,
        pageSize: 20,
        hasNext: false,
        hasPrevious: false,
      },
    });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.getByTestId("catalog-item-row")).toBeDefined();
    expect(screen.getByText("Amazon Gift Card")).toBeDefined();
    expect(screen.getByTestId("status-active")).toBeDefined();
  });
});
