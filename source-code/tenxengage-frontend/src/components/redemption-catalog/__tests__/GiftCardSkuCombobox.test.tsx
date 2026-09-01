import { describe, it, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  GiftCardSkuCombobox,
  skuValueLabel,
} from "@/components/redemption-catalog/GiftCardSkuCombobox";
import type { GiftCardSkuResponse } from "@/types/redemption-catalog.types";

const { catalogState } = vi.hoisted(() => ({
  catalogState: {
    data: undefined as GiftCardSkuResponse[] | undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  },
}));

vi.mock("@/hooks/useRedemptionCatalog", () => ({
  useGiftCardCatalog: () => catalogState,
}));

const BRAND_URL = "https://cdn.example.com/brands/acme.png";

const FIXED: GiftCardSkuResponse = {
  sku: "U-FIX-10", rewardName: "Acme $10 Card", brandName: "Acme", brandImageUrl: BRAND_URL,
  currencyCode: "USD", valueType: "FIXED", faceValue: 10, minValue: 0, maxValue: 0,
};
const VARIABLE: GiftCardSkuResponse = {
  sku: "U-VAR", rewardName: "Globex Flex", brandName: "Globex", brandImageUrl: null,
  currencyCode: "USD", valueType: "VARIABLE", faceValue: 0, minValue: 5, maxValue: 500,
};

describe("skuValueLabel", () => {
  it("formats FIXED as an exact amount and VARIABLE as a range", () => {
    expect(skuValueLabel(FIXED)).toBe("$10");
    expect(skuValueLabel(VARIABLE)).toBe("$5–$500");
  });
});

describe("GiftCardSkuCombobox", () => {
  beforeEach(() => {
    catalogState.data = [FIXED, VARIABLE];
    catalogState.isLoading = false;
    catalogState.isError = false;
  });

  it("shows a placeholder when nothing is selected", () => {
    render(<GiftCardSkuCombobox value="" onSelect={vi.fn()} />);
    expect(screen.getByTestId("gift-card-sku-trigger").textContent).toContain(
      "Select a gift-card SKU",
    );
  });

  it("reflects the selected SKU's reward name and value on the trigger", () => {
    render(<GiftCardSkuCombobox value="U-FIX-10" onSelect={vi.fn()} />);
    const trigger = screen.getByTestId("gift-card-sku-trigger");
    expect(trigger.textContent).toContain("Acme $10 Card");
    expect(trigger.textContent).toContain("$10");
  });

  it("lists SKUs grouped by brand and fires onSelect with the chosen SKU", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<GiftCardSkuCombobox value="" onSelect={onSelect} />);

    await user.click(screen.getByTestId("gift-card-sku-trigger"));

    expect(await screen.findByTestId("gift-card-sku-option-U-FIX-10")).toBeDefined();
    expect(screen.getByTestId("gift-card-sku-option-U-VAR")).toBeDefined();
    // Brand group headings.
    expect(screen.getByText("Acme")).toBeDefined();
    expect(screen.getByText("Globex")).toBeDefined();

    await user.click(screen.getByTestId("gift-card-sku-option-U-VAR"));
    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ sku: "U-VAR", valueType: "VARIABLE" }),
    );
  });

  it("shows each SKU's brand image as a thumbnail, with a placeholder when it has none", async () => {
    const user = userEvent.setup();
    render(<GiftCardSkuCombobox value="" onSelect={vi.fn()} />);

    await user.click(screen.getByTestId("gift-card-sku-trigger"));

    expect((await screen.findByTestId("sku-thumb-U-FIX-10")).getAttribute("src")).toBe(BRAND_URL);
    // VARIABLE has brandImageUrl: null → gift glyph rather than a broken image.
    expect(screen.getByTestId("sku-thumb-placeholder-U-VAR")).toBeDefined();
    expect(screen.queryByTestId("sku-thumb-U-VAR")).toBeNull();
  });

  it("falls back to the placeholder when a SKU's brand image fails to load", async () => {
    const user = userEvent.setup();
    render(<GiftCardSkuCombobox value="" onSelect={vi.fn()} />);

    await user.click(screen.getByTestId("gift-card-sku-trigger"));
    fireEvent.error(await screen.findByTestId("sku-thumb-U-FIX-10"));

    expect(screen.getByTestId("sku-thumb-placeholder-U-FIX-10")).toBeDefined();
  });

  it("shows the selected SKU's image as a thumbnail on the trigger", () => {
    render(<GiftCardSkuCombobox value="U-FIX-10" onSelect={vi.fn()} />);

    const trigger = screen.getByTestId("gift-card-sku-trigger");
    const thumb = screen.getByTestId("sku-thumb-U-FIX-10");
    expect(thumb.getAttribute("src")).toBe(BRAND_URL);
    expect(trigger.contains(thumb)).toBe(true);
  });

  it("shows no thumbnail on the trigger while nothing is selected", () => {
    render(<GiftCardSkuCombobox value="" onSelect={vi.fn()} />);

    expect(screen.queryByTestId("sku-thumb-U-FIX-10")).toBeNull();
    expect(screen.queryByTestId("sku-thumb-placeholder-U-FIX-10")).toBeNull();
  });

  it("shows an error state with a retry when the catalog fails to load", async () => {
    catalogState.data = undefined;
    catalogState.isError = true;
    const user = userEvent.setup();
    render(<GiftCardSkuCombobox value="" onSelect={vi.fn()} />);

    await user.click(screen.getByTestId("gift-card-sku-trigger"));
    expect(await screen.findByText(/could not load gift-card catalog/i)).toBeDefined();
    expect(screen.getByRole("button", { name: /retry/i })).toBeDefined();
  });
});
