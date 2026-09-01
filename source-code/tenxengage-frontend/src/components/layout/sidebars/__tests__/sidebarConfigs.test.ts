import { describe, it, expect } from "vitest";
import { sidebarConfig } from "../sidebarConfigs";

describe("sidebarConfig", () => {
  it('topLabel is "tenXengage"', () => {
    expect(sidebarConfig.topLabel).toBe("tenXengage");
  });

  it("has primary navigation items", () => {
    expect(sidebarConfig.primaryItems.length).toBeGreaterThan(0);
  });

  it("has configuration sections", () => {
    expect(sidebarConfig.sections!.length).toBeGreaterThan(0);
  });
});

describe("sidebarConfig — consolidated Redemption nav (CR-03/CR-04)", () => {
  const redemptionGroup = sidebarConfig
    .sections!.flatMap((s) => s.groups ?? [])
    .find((g) => g.label === "Redemption");

  it("exposes a single 'Redemption' group with the 7 redemption sub-items in order", () => {
    expect(redemptionGroup).toBeDefined();
    expect(redemptionGroup!.items.map((i) => i.label)).toEqual([
      "Redemption Store",
      "Transaction History",
      "Tenant History",
      "Approval Queue",
      "Analytics",
      "Breakage",
      "Balance Expiration",
    ]);
  });

  it("no longer keeps any redemption item at the top level (primaryItems)", () => {
    const topLevelRoutes = sidebarConfig.primaryItems.map((i) => i.to);
    expect(
      topLevelRoutes.some(
        (r) => r.startsWith("/redemption") || r.startsWith("/settings/redemption"),
      ),
    ).toBe(false);
    // non-redemption primary items are untouched
    expect(topLevelRoutes).toContain("/home");
  });

  it("gates Redemption Store on module.redemption_store AND the redeem capability (CR-04 preserved)", () => {
    const store = redemptionGroup!.items.find((i) => i.to === "/redemption-store")!;
    // Module gate → disabling module.redemption_store (company/user override) hides the store,
    // matching the BE which gates the store endpoints on the same key.
    expect(store.permissionKey).toBe("module.redemption_store");
    // Redeem capability still required → a Client Admin holds the module umbrella but cannot redeem,
    // so the storefront must still not appear for them (CR-04).
    expect(store.anyPermission).toEqual([
      "action.redemption.redeem",
      "action.redemption.redeem_company",
    ]);
  });

  it("the group activates on any redemption route", () => {
    expect(redemptionGroup!.activePrefixes).toEqual(["/redemption", "/settings/redemption"]);
  });
});
