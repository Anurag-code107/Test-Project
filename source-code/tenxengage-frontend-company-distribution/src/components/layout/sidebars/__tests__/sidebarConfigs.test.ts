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

  it("exposes a single 'Redemption' group with the 10 redemption sub-items in order", () => {
    expect(redemptionGroup).toBeDefined();
    // The label was "Tenant History" when this test was written and is now "All Redemptions" — the
    // rename was never reflected here, which is why this test was already failing before the three
    // distribution items were added.
    expect(redemptionGroup!.items.map((i) => i.label)).toEqual([
      "Redemption Store",
      "Redemption History",
      "Distribution Store",
      "Distribution History",
      "Company Awards",
      "All Redemptions",
      "Approval Queue",
      "Analytics",
      "Breakage",
      "Balance Expiration",
    ]);
  });

  it("gates the three distribution items on their own permissions and the feature flag", () => {
    const byLabel = (label: string) => redemptionGroup!.items.find((i) => i.label === label)!;

    // Each is visible to exactly one role, so a seller never sees the store and an admin never sees
    // Company Awards — the sidebar's empty-permission filter does the role split, no role code needed.
    expect(byLabel("Distribution Store").permissionKey).toBe("action.redemption.distribute");
    expect(byLabel("Distribution History").permissionKey).toBe(
      "action.redemption.view_distribution_history",
    );
    expect(byLabel("Company Awards").permissionKey).toBe("action.redemption.view_company_awards");

    // All three behind the flag, so the whole surface can be switched off without a deploy.
    for (const label of ["Distribution Store", "Distribution History", "Company Awards"]) {
      expect(byLabel(label).featureKey).toBe("company_distribution");
    }
  });

  it("keeps the Distribution Store on its own route, not a tab inside the personal store", () => {
    const store = redemptionGroup!.items.find((i) => i.label === "Distribution Store")!;
    const personal = redemptionGroup!.items.find((i) => i.label === "Redemption Store")!;
    // Different wallets fund these two screens; sharing one page would mix a personal and a company
    // balance in the same view.
    expect(store.to).toBe("/redemption/distribution");
    expect(personal.to).toBe("/redemption-store");
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
    expect(store.anyPermission).toEqual(["action.redemption.redeem"]);
  });

  it("the group activates on any redemption route", () => {
    expect(redemptionGroup!.activePrefixes).toEqual(["/redemption", "/settings/redemption"]);
  });
});
