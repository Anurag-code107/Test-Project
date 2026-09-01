import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { GiftCardPicker } from "@/components/distribution/GiftCardPicker";
import type { DistributionCatalogItem } from "@/types/company-distribution.types";

/**
 * The provider catalogue is a few hundred SKUs, so the picker caps what it renders and offers search.
 * These tests pin the two things that make capping safe: everything is still reachable by searching, and a
 * chosen card never vanishes off screen while the admin looks around for something else.
 */
function card(id: string, name: string, extra: Partial<DistributionCatalogItem> = {}) {
  return {
    id,
    name,
    description: null,
    imageUrl: null,
    providerImageUrl: null,
    currencyId: "cash",
    valueType: "VARIABLE",
    minAmount: "5",
    maxAmount: "500",
    ...extra,
  } as DistributionCatalogItem;
}

/** 20 cards — more than the 12 the picker renders at once. */
const many = Array.from({ length: 20 }, (_, i) => card(`sku-${i}`, `Brand ${i} Gift Card`));

describe("GiftCardPicker", () => {
  it("caps the list and says how many of the total are shown", () => {
    render(<GiftCardPicker items={many} isLoading={false} selectedId={null} onSelect={vi.fn()} />);

    expect(screen.getAllByRole("radio")).toHaveLength(12);
    expect(screen.getByTestId("gift-card-count").textContent).toMatch(/Showing 12 of 20/);
  });

  /** A card beyond the cap must still be reachable, or the cap would be hiding inventory. */
  it("finds a card that the cap hid", async () => {
    const user = userEvent.setup();
    render(<GiftCardPicker items={many} isLoading={false} selectedId={null} onSelect={vi.fn()} />);

    expect(screen.queryByTestId("gift-card-sku-19")).toBeNull();

    await user.type(screen.getByTestId("gift-card-search"), "Brand 19");

    expect(screen.getByTestId("gift-card-sku-19")).toBeDefined();
    expect(screen.getAllByRole("radio")).toHaveLength(1);
  });

  it("says so when nothing matches, rather than showing an empty grid", async () => {
    const user = userEvent.setup();
    render(<GiftCardPicker items={many} isLoading={false} selectedId={null} onSelect={vi.fn()} />);

    await user.type(screen.getByTestId("gift-card-search"), "zzzznope");

    expect(screen.getByTestId("gift-card-count").textContent).toMatch(/No cards match/);
    expect(screen.queryAllByRole("radio")).toHaveLength(0);
  });

  /**
   * The selection is what the admin is about to spend company money on. It must stay visible even when the
   * current search excludes it, or they lose track of what is actually chosen.
   */
  it("keeps the selected card on screen even when the search excludes it", async () => {
    const user = userEvent.setup();
    render(<GiftCardPicker items={many} isLoading={false} selectedId="sku-19" onSelect={vi.fn()} />);

    await user.type(screen.getByTestId("gift-card-search"), "Brand 3 ");

    const selected = screen.getByTestId("gift-card-sku-19");
    expect(selected).toBeDefined();
    expect(selected.getAttribute("aria-checked")).toBe("true");
  });

  it("reports the chosen sku on click", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    render(<GiftCardPicker items={many} isLoading={false} selectedId={null} onSelect={onSelect} />);

    await user.click(screen.getByTestId("gift-card-sku-2"));

    expect(onSelect).toHaveBeenCalledWith("sku-2");
  });

  it("shows a plain count when everything already fits", () => {
    render(
      <GiftCardPicker items={many.slice(0, 3)} isLoading={false} selectedId={null} onSelect={vi.fn()} />,
    );

    expect(screen.getByTestId("gift-card-count").textContent).toMatch(/^3 cards$/);
  });
});
