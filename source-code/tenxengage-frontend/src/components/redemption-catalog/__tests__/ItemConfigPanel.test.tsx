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

  it("sends maxTransactionAmountOverride, symmetric with the min override", async () => {
    const mutate = vi.fn();
    mockUseUpsertItemConfig.mockReturnValue({ mutate, isPending: false });
    mockUseCatalogItemConfig.mockReturnValue({
      data: paginatedResponse([{ ...BASE_ITEM, defaultMaxRedemptionAmount: "2000.00" }]),
    });

    render(<ItemConfigPanel catalogItemId="item-1" enabled={true} onClose={vi.fn()} />, { wrapper });

    await userEvent.type(await screen.findByLabelText(/min transaction amount override/i), "50.00");
    await userEvent.type(screen.getByLabelText(/max transaction amount override/i), "500.00");
    await userEvent.click(screen.getByRole("button", { name: /save/i }));

    await waitFor(() => {
      const [{ request }] = mutate.mock.calls[0] as [{ request: Record<string, unknown> }];
      expect(request.minTransactionAmountOverride).toBe("50.00");
      expect(request.maxTransactionAmountOverride).toBe("500.00");
    });
  });

  it("prefills the max override from the saved config", async () => {
    mockUseUpsertItemConfig.mockReturnValue({ mutate: vi.fn(), isPending: false });
    mockUseCatalogItemConfig.mockReturnValue({
      data: paginatedResponse([{ ...BASE_ITEM, maxTransactionAmountOverride: "750.00" }]),
    });

    render(<ItemConfigPanel catalogItemId="item-1" enabled={true} onClose={vi.fn()} />, { wrapper });

    const input = (await screen.findByLabelText(/max transaction amount override/i)) as HTMLInputElement;
    await waitFor(() => expect(input.value).toBe("750.00"));
  });

  it("omits the max override when the field is left blank (inherit)", async () => {
    const mutate = vi.fn();
    mockUseUpsertItemConfig.mockReturnValue({ mutate, isPending: false });
    mockUseCatalogItemConfig.mockReturnValue({ data: paginatedResponse([BASE_ITEM]) });

    render(<ItemConfigPanel catalogItemId="item-1" enabled={true} onClose={vi.fn()} />, { wrapper });

    await userEvent.click(await screen.findByRole("button", { name: /save/i }));

    await waitFor(() => {
      const [{ request }] = mutate.mock.calls[0] as [{ request: Record<string, unknown> }];
      expect(request.maxTransactionAmountOverride).toBeUndefined();
    });
  });

  it("anchors a 'Maximum …' 422 to the max field and a 'Minimum …' 422 to the min field", async () => {
    mockUseCatalogItemConfig.mockReturnValue({ data: paginatedResponse([BASE_ITEM]) });

    function rejectWith(errorMessage: string) {
      return vi.fn((_vars: unknown, opts?: { onError?: (e: unknown) => void }) =>
        opts?.onError?.({ response: { status: 422, data: { errorMessage } } }),
      );
    }

    mockUseUpsertItemConfig.mockReturnValue({
      mutate: rejectWith("Maximum transaction amount cannot be set above the catalog item's platform maximum of 2000.00"),
      isPending: false,
    });
    const first = render(<ItemConfigPanel catalogItemId="item-1" enabled={true} onClose={vi.fn()} />, { wrapper });
    await userEvent.click(await screen.findByRole("button", { name: /save/i }));

    await waitFor(() => {
      expect(screen.getByTestId("max-amount-error").textContent).toContain("platform maximum");
    });
    expect(screen.queryByTestId("min-amount-error")).toBeNull();
    first.unmount();

    mockUseUpsertItemConfig.mockReturnValue({
      mutate: rejectWith("Minimum transaction amount cannot be set below the catalog item's platform minimum of 10.00"),
      isPending: false,
    });
    render(<ItemConfigPanel catalogItemId="item-1" enabled={true} onClose={vi.fn()} />, { wrapper });
    await userEvent.click(await screen.findByRole("button", { name: /save/i }));

    await waitFor(() => {
      expect(screen.getByTestId("min-amount-error").textContent).toContain("platform minimum");
    });
    expect(screen.queryByTestId("max-amount-error")).toBeNull();
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
