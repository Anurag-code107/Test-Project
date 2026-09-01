import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { ItemConfigPanel } from "@/components/redemption-catalog/ItemConfigPanel";

const mockUseCatalogItemConfig = vi.fn();
const mockUseUpsertItemConfig = vi.fn();

vi.mock("@/hooks/useRedemptionCatalog", () => ({
  useCatalogItemConfig: (...args: unknown[]) => mockUseCatalogItemConfig(...args),
  useUpsertItemConfig: () => mockUseUpsertItemConfig(),
}));

const BASE_CONFIG = {
  id: "cfg-1",
  redemptionCatalogItemId: "item-1",
  enabled: true,
  returnWindowDaysOverride: 14,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

const BASE_ITEM = {
  id: "item-1",
  name: "Gift Card",
  category: "NON_CASH",
  currencyId: "points",
  defaultMinRedemptionAmount: "10.00",
  defaultProcessingMode: "INSTANT",
  geographicScope: [],
  isReturnable: true,
  defaultReturnWindowDays: 30,
  isGloballyActive: true,
  tenantConfig: BASE_CONFIG,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

function paginatedResponse(items: unknown[]) {
  return { data: items, page: 0, pageSize: 20, totalElements: items.length, totalPages: 1, hasNext: false, hasPrevious: false };
}

function wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("ItemConfigPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("does not send returnWindowDaysOverride when the field is blank", async () => {
    const mutate = vi.fn();
    mockUseUpsertItemConfig.mockReturnValue({ mutate, isPending: false });
    mockUseCatalogItemConfig.mockReturnValue({
      data: paginatedResponse([{ ...BASE_ITEM, tenantConfig: { ...BASE_CONFIG, returnWindowDaysOverride: undefined } }]),
    });

    render(<ItemConfigPanel catalogItemId="item-1" enabled={true} onClose={vi.fn()} />, { wrapper });

    const saveBtn = await screen.findByRole("button", { name: /save/i });
    await userEvent.click(saveBtn);

    await waitFor(() => {
      expect(mutate).toHaveBeenCalled();
      const [{ request }] = mutate.mock.calls[0] as [{ catalogItemId: string; request: Record<string, unknown> }];
      expect(request).not.toHaveProperty("returnWindowDaysOverride");
    });
  });

  it("does not coerce blank return window to 0", async () => {
    const mutate = vi.fn();
    mockUseUpsertItemConfig.mockReturnValue({ mutate, isPending: false });
    mockUseCatalogItemConfig.mockReturnValue({
      data: paginatedResponse([BASE_ITEM]),
    });

    render(<ItemConfigPanel catalogItemId="item-1" enabled={true} onClose={vi.fn()} />, { wrapper });

    const input = await screen.findByLabelText(/return window override/i);
    await userEvent.clear(input);

    const saveBtn = screen.getByRole("button", { name: /save/i });
    await userEvent.click(saveBtn);

    await waitFor(() => {
      expect(mutate).toHaveBeenCalled();
      const [{ request }] = mutate.mock.calls[0] as [{ catalogItemId: string; request: Record<string, unknown> }];
      expect(request.returnWindowDaysOverride).not.toBe(0);
      expect(request).not.toHaveProperty("returnWindowDaysOverride");
    });
  });
});
