import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CatalogItemCard } from "@/components/redemption-catalog/CatalogItemCard";
import type { CatalogBrowseItemResponse } from "@/types/redemption-catalog.types";

vi.mock("@/components/redemption-catalog/ShortfallBadge", () => ({
  ShortfallBadge: ({ shortfallAmount, currencyId }: { shortfallAmount: string; currencyId: string }) => (
    <span data-testid="shortfall-badge">{shortfallAmount} {currencyId} short</span>
  ),
}));

// The real fallback chain (upload → SKU brand image → illustration) is covered in
// CatalogCardIllustration.test.tsx; here we only assert the card hands both sources down.
vi.mock("@/components/redemption-catalog/CatalogCardIllustration", () => ({
  CatalogCardIllustration: ({
    category,
    imageUrl,
    providerImageUrl,
  }: {
    category: string;
    imageUrl?: string | null;
    providerImageUrl?: string | null;
  }) => (
    <div
      data-testid={imageUrl ? "catalog-card-custom-image" : `catalog-card-illustration-${category.toLowerCase()}`}
      data-provider-image={providerImageUrl ?? ""}
    />
  ),
}));

const BASE_ITEM: CatalogBrowseItemResponse = {
  id: "item-1",
  name: "Amazon Gift Card",
  category: "NON_CASH",
  currencyId: "points",
  imageUrl: null,
  effectiveMinTransactionAmount: "50",
  effectiveProcessingMode: "INSTANT",
  estimatedPayoutTimeline: "Instant delivery",
  canAfford: true,
  shortfallAmount: "0",
  geographicScope: ["US"],
  isReturnable: false,
  effectiveReturnWindowDays: 0,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

describe("CatalogItemCard", () => {
  it("renders item name and payout timeline", () => {
    render(<CatalogItemCard item={BASE_ITEM} />);
    expect(screen.getByTestId("item-name-item-1").textContent).toBe("Amazon Gift Card");
    expect(screen.getByTestId("payout-timeline-item-1").textContent).toContain("Instant delivery");
  });

  it("shows category-themed illustration when imageUrl is null", () => {
    render(<CatalogItemCard item={BASE_ITEM} />);
    expect(screen.getByTestId("catalog-card-illustration-non_cash")).toBeDefined();
  });

  it("shows category-themed illustration for CASH when imageUrl is null", () => {
    render(<CatalogItemCard item={{ ...BASE_ITEM, category: "CASH" }} />);
    expect(screen.getByTestId("catalog-card-illustration-cash")).toBeDefined();
  });

  it("shows custom image when imageUrl is provided", () => {
    render(<CatalogItemCard item={{ ...BASE_ITEM, imageUrl: "https://cdn.example.com/img.jpg" }} />);
    expect(screen.getByTestId("catalog-card-custom-image")).toBeDefined();
  });

  it("passes the item's SKU brand image down as the illustration's fallback", () => {
    render(
      <CatalogItemCard
        item={{ ...BASE_ITEM, providerImageUrl: "https://cdn.example.com/brands/sling.png" }}
      />,
    );
    expect(
      screen.getByTestId("catalog-card-illustration-non_cash").getAttribute("data-provider-image"),
    ).toBe("https://cdn.example.com/brands/sling.png");
  });

  it("renders ShortfallBadge when canAfford is false", () => {
    render(<CatalogItemCard item={{ ...BASE_ITEM, canAfford: false, shortfallAmount: "25" }} />);
    expect(screen.getByTestId("shortfall-badge")).toBeDefined();
  });

  it("does not render ShortfallBadge when canAfford is true", () => {
    render(<CatalogItemCard item={BASE_ITEM} />);
    expect(screen.queryByTestId("shortfall-badge")).toBeNull();
  });

  it("shows the exact denomination for a FIXED item (no 'Starting at')", () => {
    render(
      <CatalogItemCard
        item={{ ...BASE_ITEM, valueType: "FIXED", effectiveMinTransactionAmount: "50", effectiveMaxTransactionAmount: "50" }}
      />,
    );
    const amount = screen.getByTestId("item-amount-item-1");
    expect(amount.textContent).not.toContain("Starting at");
    expect(amount.textContent).toContain("50");
  });

  it("shows a min–max range for a VARIABLE item", () => {
    render(
      <CatalogItemCard
        item={{ ...BASE_ITEM, valueType: "VARIABLE", effectiveMinTransactionAmount: "50", effectiveMaxTransactionAmount: "500" }}
      />,
    );
    const amount = screen.getByTestId("item-amount-item-1").textContent ?? "";
    expect(amount).toContain("–");
    expect(amount).toContain("50");
    expect(amount).toContain("500");
  });

  it("falls back to 'Starting at' for legacy items without a value type", () => {
    render(<CatalogItemCard item={BASE_ITEM} />);
    expect(screen.getByTestId("item-amount-item-1").textContent).toContain("Starting at");
  });

  describe("inactive state (nothing redeemable yet, e.g. payout profile not set up)", () => {
    const REASON = "Set up payouts to redeem gift cards.";

    it("is clickable and not marked disabled by default", () => {
      const onClick = vi.fn();
      render(<CatalogItemCard item={BASE_ITEM} onClick={onClick} />);
      const card = screen.getByTestId("catalog-item-card-item-1");

      expect(card).not.toHaveAttribute("aria-disabled");
      expect(card.className).toContain("cursor-pointer");
      fireEvent.click(card);
      expect(onClick).toHaveBeenCalledTimes(1);
    });

    it("renders dimmed and desaturated when disabled", () => {
      render(<CatalogItemCard item={BASE_ITEM} disabled disabledReason={REASON} />);
      const card = screen.getByTestId("catalog-item-card-item-1");

      expect(card).toHaveAttribute("aria-disabled", "true");
      expect(card.className).toContain("opacity-60");
      expect(card.className).toContain("grayscale");
      expect(card.className).toContain("cursor-not-allowed");
      expect(card.className).not.toContain("cursor-pointer");
    });

    it("does not fire onClick when disabled, so the drawer can't open", () => {
      const onClick = vi.fn();
      render(<CatalogItemCard item={BASE_ITEM} disabled disabledReason={REASON} onClick={onClick} />);

      fireEvent.click(screen.getByTestId("catalog-item-card-item-1"));
      expect(onClick).not.toHaveBeenCalled();
    });

    it("still shows the item's details so the catalog stays browsable", () => {
      render(<CatalogItemCard item={BASE_ITEM} disabled disabledReason={REASON} />);

      expect(screen.getByTestId("item-name-item-1").textContent).toBe("Amazon Gift Card");
      expect(screen.getByTestId("item-amount-item-1")).toBeDefined();
      expect(screen.getByTestId("payout-timeline-item-1")).toBeDefined();
    });

    it("exposes the reason on hover", async () => {
      const user = userEvent.setup();
      render(<CatalogItemCard item={BASE_ITEM} disabled disabledReason={REASON} />);

      await user.hover(screen.getByTestId("catalog-item-card-item-1"));
      await waitFor(() => {
        expect(screen.getAllByText(REASON).length).toBeGreaterThan(0);
      });
    });
  });
});
