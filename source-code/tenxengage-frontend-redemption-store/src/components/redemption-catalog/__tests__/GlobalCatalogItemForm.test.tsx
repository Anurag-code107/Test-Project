import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { GlobalCatalogItemForm } from "../GlobalCatalogItemForm";

const { mockUpdateMutateAsync, mockCreateMutateAsync, mockToast } = vi.hoisted(() => ({
  mockUpdateMutateAsync: vi.fn(),
  mockCreateMutateAsync: vi.fn(),
  mockToast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("sonner", () => ({ toast: mockToast }));

vi.mock("@/hooks/useRedemptionCatalog", () => ({
  useCreateCatalogItem: () => ({ mutateAsync: mockCreateMutateAsync, isPending: false }),
  useUpdateCatalogItem: () => ({ mutate: vi.fn(), mutateAsync: mockUpdateMutateAsync, isPending: false }),
  useUpsertItemConfig: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

// Replace the real SKU picker (Popover + cmdk) with a deterministic stub so the form's autofill
// logic is testable without jsdom's flaky Popover/command interactions. The stub keeps the `id` so
// the "Provider Item ID" <label htmlFor> association still resolves for getByLabelText.
const ACME_BRAND_IMAGE = "https://cdn.example.com/brands/acme.png";
const FIXED_SKU = {
  sku: "U-FIX-10", rewardName: "Acme $10", brandName: "Acme", brandImageUrl: ACME_BRAND_IMAGE,
  currencyCode: "USD", valueType: "FIXED" as const, faceValue: 10, minValue: 0, maxValue: 0,
};
const VAR_SKU = {
  // lowercase rewardName on purpose — the autofilled catalog name must be capitalized ("adidas" → "Adidas").
  // brandImageUrl null on purpose too — used to prove the previous brand's logo doesn't linger.
  sku: "U-VAR", rewardName: "adidas Gift Card", brandName: "adidas", brandImageUrl: null,
  currencyCode: "USD", valueType: "VARIABLE" as const, faceValue: 0, minValue: 5, maxValue: 500,
};
vi.mock("../GiftCardSkuCombobox", () => ({
  GiftCardSkuCombobox: ({ id, onSelect }: { id?: string; onSelect: (s: typeof FIXED_SKU | typeof VAR_SKU) => void }) => (
    <>
      <button type="button" id={id} data-testid="pick-fixed-sku" onClick={() => onSelect(FIXED_SKU)}>
        fixed
      </button>
      <button type="button" data-testid="pick-var-sku" onClick={() => onSelect(VAR_SKU)}>
        var
      </button>
    </>
  ),
  skuValueLabel: (s: { valueType: string; faceValue: number | null; minValue: number | null; maxValue: number | null }) =>
    s.valueType === "FIXED" ? `$${s.faceValue}` : `$${s.minValue}–$${s.maxValue}`,
}));

vi.mock("@/lib/axios", () => {
  const mock = {
    get: vi.fn(),
    post: vi.fn(),
  };
  return { default: mock };
});

import api from "@/lib/axios";

const mockApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
};

const DEFAULT_CURRENCIES = [
  { id: "1", code: "USD", name: "US Dollar", type: "MONETARY" },
  { id: "2", code: "POINTS", name: "Points", type: "NON_MONETARY" },
];

const DEFAULT_LOCATION_HIERARCHY = {
  levels: [],
  tree: [],
};

function makeQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={makeQueryClient()}>
      {children}
    </QueryClientProvider>
  );
}

describe("GlobalCatalogItemForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCreateMutateAsync.mockResolvedValue({ id: "created-1" });
    // Default: currencies endpoint returns 2 currencies, location returns empty tree
    mockApi.get.mockImplementation((url: string) => {
      if (url === "/currencies") {
        return Promise.resolve({ data: { data: DEFAULT_CURRENCIES } });
      }
      if (url === "/location-levels") {
        return Promise.resolve({ data: { data: DEFAULT_LOCATION_HIERARCHY } });
      }
      return Promise.resolve({ data: { data: null } });
    });
  });

  /**
   * The SKU picker is CASH-only (XTRM gift cards); NON_CASH takes a free-text Xoxoday product id.
   * Most SKU-picker tests below therefore need the category switched to CASH first.
   */
  async function selectCategory(name: RegExp) {
    fireEvent.click(screen.getByRole("combobox", { name: /category/i }));
    await waitFor(() => expect(screen.getByRole("option", { name })).toBeDefined());
    fireEvent.click(screen.getByRole("option", { name }));
  }

  async function renderAsCash() {
    render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });
    await waitFor(() => expect(screen.getByLabelText(/^name/i)).toBeDefined());
    await selectCategory(/^cash$/i);
    await waitFor(() => expect(screen.getByTestId("pick-fixed-sku")).toBeDefined());
  }

  describe("provider mapping is category-specific", () => {
    it("NON_CASH (default) shows a plain Xoxoday provider-item-id input, no SKU picker", async () => {
      render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });
      await waitFor(() => expect(screen.getByLabelText(/^name/i)).toBeDefined());

      expect(screen.getByTestId("provider-item-id-input")).toBeDefined();
      expect(screen.getByLabelText(/provider item id \(xoxoday\)/i)).toBeDefined();
      expect(screen.getByText(/fulfilled by xoxoday/i)).toBeDefined();
      // No picker and no manual-entry toggle — there is nothing to pick from for Xoxoday.
      expect(screen.queryByTestId("pick-fixed-sku")).toBeNull();
      expect(screen.queryByText(/enter sku manually/i)).toBeNull();
    });

    it("CASH shows the SKU picker instead of the free-text input", async () => {
      await renderAsCash();

      expect(screen.getByTestId("pick-fixed-sku")).toBeDefined();
      expect(screen.getByText(/enter sku manually/i)).toBeDefined();
      expect(screen.queryByTestId("provider-item-id-input")).toBeNull();
    });

    it("CASH still allows manual SKU entry via the toggle", async () => {
      await renderAsCash();
      fireEvent.click(screen.getByText(/enter sku manually/i));

      await waitFor(() => expect(screen.getByLabelText(/sku \(provider item id\)/i)).toBeDefined());
      expect(screen.queryByTestId("pick-fixed-sku")).toBeNull();
    });

    it("clears a carried-over provider id when the category is switched", async () => {
      render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });
      await waitFor(() => expect(screen.getByLabelText(/^name/i)).toBeDefined());

      const xoxoInput = screen.getByTestId("provider-item-id-input") as HTMLInputElement;
      fireEvent.change(xoxoInput, { target: { value: "XOXO-12345" } });
      expect(xoxoInput.value).toBe("XOXO-12345");

      // A Xoxoday id is never a valid XTRM SKU, so it must not survive the switch.
      await selectCategory(/^cash$/i);
      await waitFor(() => expect(screen.getByTestId("pick-fixed-sku")).toBeDefined());
      fireEvent.click(screen.getByText(/enter sku manually/i));

      await waitFor(() => {
        expect((screen.getByLabelText(/sku \(provider item id\)/i) as HTMLInputElement).value).toBe("");
      });
    });

    it("keeps the saved provider id when opening the edit form (no spurious clear on mount)", async () => {
      const existing = {
        id: "item-3",
        name: "Xoxo Reward",
        description: "",
        category: "NON_CASH" as const,
        currencyId: "POINTS",
        defaultMinRedemptionAmount: "10",
        defaultProcessingMode: "INSTANT" as const,
        geographicScope: [],
        providerItemId: "XOXO-SAVED-1",
        isReturnable: false,
        defaultReturnWindowDays: 0,
        isActive: true,
        imageUrl: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };

      render(<GlobalCatalogItemForm item={existing} onSave={vi.fn()} />, { wrapper });

      await waitFor(() => {
        expect((screen.getByTestId("provider-item-id-input") as HTMLInputElement).value).toBe(
          "XOXO-SAVED-1",
        );
      });
    });

    it("requires the provider item id for NON_CASH, worded for Xoxoday not SKU", async () => {
      render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });
      await waitFor(() => expect(screen.getByLabelText(/^name/i)).toBeDefined());

      fireEvent.change(screen.getByLabelText(/^name/i), { target: { value: "A reward" } });
      fireEvent.click(screen.getByRole("button", { name: /create item/i }));

      await waitFor(() => {
        expect(screen.getByText(/provider item id is required/i)).toBeDefined();
      });
      expect(screen.queryByText(/^sku is required$/i)).toBeNull();
    });
  });

  it("renders all fields for NON_CASH category", async () => {
    render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });
    // Wait for async queries to settle
    await waitFor(() => expect(screen.getByLabelText(/name/i)).toBeDefined());

    expect(screen.getByLabelText(/name/i)).toBeDefined();
    expect(screen.getByLabelText(/description/i)).toBeDefined();
    expect(screen.getByRole("combobox", { name: /currency/i })).toBeDefined();
    expect(screen.getByLabelText(/min redemption amount/i)).toBeDefined();
    expect(screen.getByRole("combobox", { name: /processing mode/i })).toBeDefined();
    expect(screen.getByLabelText(/provider item id/i)).toBeDefined();
    // Geographic Scope is hidden (CATALOG_GEOGRAPHIC_SCOPE_ENABLED = false) — dormant feature.
    expect(screen.queryByText(/geographic scope/i)).toBeNull();
    expect(screen.getByTestId("isReturnable-switch")).toBeDefined();
  });

  it("disables isReturnable for CASH category", async () => {
    render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });

    const categoryTrigger = screen.getByRole("combobox", { name: /category/i });
    fireEvent.click(categoryTrigger);

    await waitFor(() => {
      const cashOption = screen.getByRole("option", { name: /^cash$/i });
      fireEvent.click(cashOption);
    });

    await waitFor(() => {
      const checkbox = screen.getByTestId("isReturnable-switch");
      expect(checkbox).toHaveAttribute("disabled");
    });
  });

  it("shows validation error when name is empty", async () => {
    render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });

    const submitButton = screen.getByRole("button", { name: /create item/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/name is required/i)).toBeDefined();
    });
  });

  // NOTE: the "loads geographic scope options" and "shows unmatched From sync chips" tests were
  // removed when the Geographic Scope field was hidden (CATALOG_GEOGRAPHIC_SCOPE_ENABLED = false).
  // Restore them from git history when the field is re-enabled.

  it("excludes the `cash` currency for a NON_CASH item (Decision A)", async () => {
    mockApi.get.mockImplementation((url: string) => {
      if (url === "/currencies") {
        return Promise.resolve({ data: { data: [
          { id: "c", code: "cash", name: "Cash", type: "MONETARY" },
          { id: "p", code: "points", name: "Points", type: "NON_MONETARY" },
        ] } });
      }
      if (url === "/location-levels") {
        return Promise.resolve({ data: { data: { levels: [], tree: [] } } });
      }
      return Promise.resolve({ data: { data: null } });
    });

    // Default category is NON_CASH → the `cash` currency must not be offered.
    render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });
    const currencyTrigger = await screen.findByRole("combobox", { name: /currency/i });
    fireEvent.click(currencyTrigger);

    await waitFor(() => expect(screen.getByRole("option", { name: /Points/ })).toBeDefined());
    expect(screen.queryByRole("option", { name: /Cash/ })).toBeNull();
  });

  it("edit: submits on the FIRST click when the API returns a numeric amount", async () => {
    // Regression: the API declares defaultMinRedemptionAmount as string but serializes BigDecimal
    // as a JSON number. Previously the first submit failed zod ("Expected string, received number")
    // and only the 2nd click worked. The form must coerce it and submit on the first click.
    const existingItem = {
      id: "item-1",
      name: "Test Item",
      description: "desc",
      category: "NON_CASH" as const,
      currencyId: "POINTS",
      defaultMinRedemptionAmount: 10 as unknown as string, // number at runtime — the bug repro
      defaultProcessingMode: "INSTANT" as const,
      geographicScope: [],
      // SKU is required by the schema, and existing items carry one — so the form opens in manual
      // mode with the stored value and the first submit passes validation.
      providerItemId: "U-EXISTING-SKU",
      isReturnable: false,
      defaultReturnWindowDays: 0,
      isActive: true,
      imageUrl: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    render(<GlobalCatalogItemForm item={existingItem} onSave={vi.fn()} />, { wrapper });

    const updateBtn = await screen.findByRole("button", { name: /update item/i });
    fireEvent.click(updateBtn);

    await waitFor(() => expect(mockUpdateMutateAsync).toHaveBeenCalledTimes(1));
    expect(screen.queryByText(/expected string, received number/i)).toBeNull();
  });

  it("autofills name + min amount and shows the value type when a SKU is picked", async () => {
    await renderAsCash();

    fireEvent.click(screen.getByTestId("pick-fixed-sku"));

    await waitFor(() => {
      expect((screen.getByLabelText(/^name/i) as HTMLInputElement).value).toBe("Acme $10");
      expect(
        (screen.getByLabelText(/min redemption amount/i) as HTMLInputElement).value,
      ).toBe("10");
    });
    // FIXED value type is surfaced to the admin.
    expect(screen.getByText(/fixed value/i)).toBeDefined();
  });

  it("previews the picked SKU's brand image as the item image", async () => {
    await renderAsCash();

    expect(screen.queryByTestId("catalog-image-sku-preview")).toBeNull();
    fireEvent.click(screen.getByTestId("pick-fixed-sku"));

    await waitFor(() =>
      expect(screen.getByTestId("catalog-image-sku-preview").getAttribute("src")).toBe(
        ACME_BRAND_IMAGE,
      ),
    );
  });

  it("drops the brand-image preview when switching to a SKU that has no image", async () => {
    await renderAsCash();

    fireEvent.click(screen.getByTestId("pick-fixed-sku"));
    await waitFor(() => expect(screen.getByTestId("catalog-image-sku-preview")).toBeDefined());

    fireEvent.click(screen.getByTestId("pick-var-sku"));

    // The previous brand's logo must not linger on a card that now points at a different SKU.
    await waitFor(() => expect(screen.queryByTestId("catalog-image-sku-preview")).toBeNull());
  });

  describe("save failures", () => {
    beforeEach(() => {
      // A CASH item may only use the `cash` currency (Decision A), so the picker needs it on offer —
      // as the real /currencies response has it. Without it the form can't reach a valid state.
      mockApi.get.mockImplementation((url: string) => {
        if (url === "/currencies") {
          return Promise.resolve({
            data: { data: [...DEFAULT_CURRENCIES, { id: "3", code: "cash", name: "Cash", type: "MONETARY" }] },
          });
        }
        if (url === "/location-levels") {
          return Promise.resolve({ data: { data: DEFAULT_LOCATION_HIERARCHY } });
        }
        return Promise.resolve({ data: { data: null } });
      });
    });

    /** A valid CASH create: picking the SKU autofills name + min amount, and CASH auto-selects `cash`. */
    async function submitValidCashCreate(onSave = vi.fn()) {
      render(<GlobalCatalogItemForm onSave={onSave} />, { wrapper });
      await waitFor(() => expect(screen.getByLabelText(/^name/i)).toBeDefined());
      await selectCategory(/^cash$/i);
      await waitFor(() => expect(screen.getByTestId("pick-fixed-sku")).toBeDefined());
      fireEvent.click(screen.getByTestId("pick-fixed-sku"));
      await waitFor(() =>
        expect((screen.getByLabelText(/^name/i) as HTMLInputElement).value).toBe("Acme $10"),
      );
      fireEvent.click(screen.getByRole("button", { name: /create item/i }));
      return onSave;
    }

    it("explains a duplicate-SKU 409 on the SKU field instead of leaving the dialog silent", async () => {
      mockCreateMutateAsync.mockRejectedValueOnce({
        response: {
          status: 409,
          data: {
            errorCode: "REQUEST_ERROR",
            // The server's own wording names its internal field — the UI must not echo it.
            errorMessage:
              "An active catalog item with this providerItemId already exists for category CASH",
          },
        },
      });

      const onSave = await submitValidCashCreate();

      await waitFor(() => expect(mockCreateMutateAsync).toHaveBeenCalledTimes(1));
      // Same short text in both places: inline under the SKU field, and as a toast because the submit
      // button sits below the fold in this dialog.
      expect(await screen.findByText("SKU already in use")).toBeDefined();
      expect(mockToast.error).toHaveBeenCalledWith("SKU already in use");
      // The server's own wording names its internal field — never shown to the admin.
      expect(screen.queryByText(/providerItemId/)).toBeNull();
      // The dialog stays open so the entered values survive and can be corrected.
      expect(onSave).not.toHaveBeenCalled();
    });

    it("clears the conflict message once a different SKU is picked", async () => {
      mockCreateMutateAsync.mockRejectedValueOnce({
        response: { status: 409, data: { errorMessage: "duplicate" } },
      });
      await submitValidCashCreate();
      await screen.findByText("SKU already in use");

      fireEvent.click(screen.getByTestId("pick-var-sku"));

      await waitFor(() => expect(screen.queryByText("SKU already in use")).toBeNull());
    });

    it("names the Xoxoday field, not a SKU, when a NON_CASH item conflicts", async () => {
      // NON_CASH maps to a Xoxoday provider item id — the field is labelled that way, so calling it a
      // SKU here would point the admin at something the form never shows.
      mockCreateMutateAsync.mockRejectedValueOnce({ response: { status: 409, data: {} } });

      render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });
      fireEvent.change(await screen.findByLabelText(/^name/i), { target: { value: "Movie Voucher" } });
      fireEvent.click(await screen.findByRole("combobox", { name: /currency/i }));
      fireEvent.click(await screen.findByRole("option", { name: /Points/ }));
      fireEvent.change(screen.getByLabelText(/min redemption amount/i), { target: { value: "7" } });
      fireEvent.change(screen.getByTestId("provider-item-id-input"), { target: { value: "XOXO-9001" } });
      fireEvent.click(screen.getByRole("button", { name: /create item/i }));

      expect(await screen.findByText("Provider item ID already in use")).toBeDefined();
      expect(mockToast.error).toHaveBeenCalledWith("Provider item ID already in use");
    });

    it("surfaces the server's message for a non-conflict failure", async () => {
      mockCreateMutateAsync.mockRejectedValueOnce({
        response: { status: 400, data: { errorMessage: "CASH items cannot be returnable" } },
      });

      const onSave = await submitValidCashCreate();

      await waitFor(() =>
        expect(mockToast.error).toHaveBeenCalledWith("CASH items cannot be returnable"),
      );
      expect(onSave).not.toHaveBeenCalled();
    });

    it("falls back to a generic message when the failure carries none", async () => {
      mockCreateMutateAsync.mockRejectedValueOnce(new Error("Network Error"));

      await submitValidCashCreate();

      await waitFor(() =>
        expect(mockToast.error).toHaveBeenCalledWith(
          "Could not save the catalog item — please try again.",
        ),
      );
    });
  });

  it("updates the autofilled name when a different SKU is picked", async () => {
    await renderAsCash();

    fireEvent.click(screen.getByTestId("pick-fixed-sku"));
    await waitFor(() =>
      expect((screen.getByLabelText(/^name/i) as HTMLInputElement).value).toBe("Acme $10"),
    );

    fireEvent.click(screen.getByTestId("pick-var-sku"));
    await waitFor(() =>
      // name follows the new SKU, first letter capitalized
      expect((screen.getByLabelText(/^name/i) as HTMLInputElement).value).toBe("Adidas Gift Card"),
    );
  });

  it("preserves a manually-typed name when switching SKUs (amount still follows)", async () => {
    await renderAsCash();

    fireEvent.click(screen.getByTestId("pick-fixed-sku"));
    const nameInput = screen.getByLabelText(/^name/i) as HTMLInputElement;
    await waitFor(() => expect(nameInput.value).toBe("Acme $10"));

    fireEvent.change(nameInput, { target: { value: "My Custom Reward" } });
    fireEvent.click(screen.getByTestId("pick-var-sku"));

    // The amount follows the new SKU...
    await waitFor(() =>
      expect((screen.getByLabelText(/min redemption amount/i) as HTMLInputElement).value).toBe("5"),
    );
    // ...but the deliberately-typed name is preserved.
    expect(nameInput.value).toBe("My Custom Reward");
  });

  it("edit: switching to another SKU updates the catalog name and amount", async () => {
    const editItem = {
      id: "item-9",
      name: "Old Amazon",
      description: "",
      // CASH — the SKU picker only applies to XTRM gift cards.
      category: "CASH" as const,
      currencyId: "cash",
      defaultMinRedemptionAmount: "20",
      defaultProcessingMode: "INSTANT" as const,
      geographicScope: [],
      providerItemId: "U-OLD",
      isReturnable: false,
      defaultReturnWindowDays: 0,
      isActive: true,
      imageUrl: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    render(<GlobalCatalogItemForm item={editItem} onSave={vi.fn()} />, { wrapper });
    const nameInput = (await screen.findByLabelText(/^name/i)) as HTMLInputElement;
    expect(nameInput.value).toBe("Old Amazon");

    // The picker is the default in edit; switching SKU syncs both the name and the amount.
    fireEvent.click(screen.getByTestId("pick-var-sku"));

    await waitFor(() => {
      expect((screen.getByLabelText(/^name/i) as HTMLInputElement).value).toBe("Adidas Gift Card");
      expect((screen.getByLabelText(/min redemption amount/i) as HTMLInputElement).value).toBe("5");
    });
  });

  it("renders currency options from API instead of static input", async () => {
    mockApi.get.mockImplementation((url: string) => {
      if (url === "/currencies") {
        return Promise.resolve({
          data: {
            data: [
              { id: "1", code: "USD", name: "US Dollar", type: "MONETARY" },
              { id: "2", code: "POINTS", name: "Points", type: "NON_MONETARY" },
            ],
          },
        });
      }
      if (url === "/location-levels") {
        return Promise.resolve({ data: { data: { levels: [], tree: [] } } });
      }
      return Promise.resolve({ data: { data: null } });
    });

    render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });

    // Wait for the combobox to appear and queries to settle
    const currencyTrigger = await screen.findByRole("combobox", { name: /currency/i });

    // Open the select
    fireEvent.click(currencyTrigger);

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "US Dollar (MONETARY)" })).toBeDefined();
      expect(screen.getByRole("option", { name: "Points (NON_MONETARY)" })).toBeDefined();
    });
    // Static text input should not be present
    expect(screen.queryByPlaceholderText(/e\.g\. cash|points/i)).toBeNull();
  });
});
