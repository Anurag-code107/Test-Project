import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { CatalogItemCard } from "@/components/redemption-catalog/CatalogItemCard";
import type { CatalogBrowseItemResponse } from "@/types/redemption-catalog.types";

vi.mock("@/components/redemption-catalog/ShortfallBadge", () => ({
  ShortfallBadge: ({ shortfallAmount, currencyId }: { shortfallAmount: string; currencyId: string }) => (
    <span data-testid="shortfall-badge">{shortfallAmount} {currencyId} short</span>
  ),
}));

const BASE_ITEM: CatalogBrowseItemResponse = {
  id: "item-1",
  name: "Amazon Gift Card",
  category: "NON_CASH",
  currencyId: "points",
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

  it("renders ShortfallBadge when canAfford is false", () => {
    const item = { ...BASE_ITEM, canAfford: false, shortfallAmount: "25" };
    render(<CatalogItemCard item={item} />);
    expect(screen.getByTestId("shortfall-badge")).toBeDefined();
  });

  it("does not render ShortfallBadge when canAfford is true", () => {
    render(<CatalogItemCard item={BASE_ITEM} />);
    expect(screen.queryByTestId("shortfall-badge")).toBeNull();
  });
});
