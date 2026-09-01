import { describe, expect, it } from "vitest";
import { RAILS, DEFAULT_RAIL, railSendBlocked } from "../distributionRails";

/**
 * The wallet rail is retired from the store.
 *
 * DEFAULT_RAIL used to fall back to "WALLET_CREDIT" by name when every rail was blocked — with the entry
 * gone, that fallback would name a rail the page does not list. These pin that it always resolves to
 * something actually offered.
 */
describe("Distribution store rails", () => {
  it("offers gift card and bank transfer only", () => {
    expect(RAILS.map((r) => r.value)).toEqual(["GIFT_CARD", "BANK_TRANSFER"]);
  });

  it("never defaults to a rail it does not offer", () => {
    expect(RAILS.map((r) => r.value)).toContain(DEFAULT_RAIL);
  });

  it("treats every offered rail as needing XTRM", () => {
    // Both remaining rails go through the vendor, which is why switching the payout rails off now disables
    // distribution entirely rather than falling back to an internal rail.
    expect(RAILS.every((r) => r.needsXtrm)).toBe(true);
  });

  it("blocks sending only on rails that need XTRM", () => {
    // WALLET_CREDIT is not offered, so it is not blocked either — it simply is not a choice.
    expect(railSendBlocked("WALLET_CREDIT")).toBe(false);
  });
});
