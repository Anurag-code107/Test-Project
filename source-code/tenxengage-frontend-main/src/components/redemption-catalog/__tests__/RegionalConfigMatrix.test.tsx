import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { RegionalConfigMatrix } from "@/components/redemption-catalog/RegionalConfigMatrix";

const mockUseRegionalConfig = vi.fn();
const mockUseUpsertRegionConfig = vi.fn();
const mockUseDeleteRegionConfig = vi.fn();

vi.mock("@/hooks/useRedemptionCatalog", () => ({
  useRegionalConfig: (...args: unknown[]) => mockUseRegionalConfig(...args),
  useUpsertRegionConfig: () => mockUseUpsertRegionConfig(),
  useDeleteRegionConfig: () => mockUseDeleteRegionConfig(),
}));

const CATALOG_ITEM_ID = "item-abc";
const GEO_SCOPE = ["US", "GB"];

const US_REGION_CONFIG = {
  id: "rc-1",
  redemptionCatalogItemId: CATALOG_ITEM_ID,
  regionCode: "US",
  enabled: true,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

function wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("RegionalConfigMatrix", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseUpsertRegionConfig.mockReturnValue({ mutate: vi.fn(), isPending: false });
    mockUseDeleteRegionConfig.mockReturnValue({ mutate: vi.fn(), isPending: false });
  });

  it("renders enabled toggle for region in geographicScope", () => {
    mockUseRegionalConfig.mockReturnValue({
      isLoading: false,
      data: [US_REGION_CONFIG],
    });

    render(
      <RegionalConfigMatrix catalogItemId={CATALOG_ITEM_ID} geographicScope={GEO_SCOPE} />,
      { wrapper },
    );

    expect(screen.getByTestId("region-row-US")).toBeDefined();
    expect(screen.getByTestId("region-toggle-US")).toBeDefined();
    expect(screen.getByTestId("region-row-GB")).toBeDefined();
  });

  it("toggle calls upsertRegionConfig with correct regionCode", async () => {
    const mutate = vi.fn();
    mockUseUpsertRegionConfig.mockReturnValue({ mutate, isPending: false });
    mockUseRegionalConfig.mockReturnValue({
      isLoading: false,
      data: [],
    });

    render(
      <RegionalConfigMatrix catalogItemId={CATALOG_ITEM_ID} geographicScope={GEO_SCOPE} />,
      { wrapper },
    );

    await userEvent.click(screen.getByTestId("region-toggle-US"));

    expect(mutate).toHaveBeenCalledWith(
      { catalogItemId: CATALOG_ITEM_ID, regionCode: "US", request: { enabled: true } },
      expect.any(Object),
    );
  });

  it("delete calls deleteRegionConfig", async () => {
    const mutate = vi.fn();
    mockUseDeleteRegionConfig.mockReturnValue({ mutate, isPending: false });
    mockUseRegionalConfig.mockReturnValue({
      isLoading: false,
      data: [US_REGION_CONFIG],
    });

    render(
      <RegionalConfigMatrix catalogItemId={CATALOG_ITEM_ID} geographicScope={GEO_SCOPE} />,
      { wrapper },
    );

    await userEvent.click(screen.getByTestId("region-delete-US"));

    expect(mutate).toHaveBeenCalledWith(
      { catalogItemId: CATALOG_ITEM_ID, regionCode: "US" },
      expect.any(Object),
    );
  });

  it("shows 422 error for region not in scope", async () => {
    const mutate = vi.fn().mockImplementation((_vars, options) => {
      options.onError({
        response: {
          status: 422,
          data: {
            errorMessage:
              "Region not supported by this catalog item",
          },
        },
      });
    });
    mockUseUpsertRegionConfig.mockReturnValue({ mutate, isPending: false });
    mockUseRegionalConfig.mockReturnValue({
      isLoading: false,
      data: [],
    });

    render(
      <RegionalConfigMatrix catalogItemId={CATALOG_ITEM_ID} geographicScope={GEO_SCOPE} />,
      { wrapper },
    );

    await userEvent.click(screen.getByTestId("region-toggle-US"));

    expect(screen.getByTestId("region-error-US")).toBeDefined();
    expect(screen.getByText(/region not supported/i)).toBeDefined();
  });
});
