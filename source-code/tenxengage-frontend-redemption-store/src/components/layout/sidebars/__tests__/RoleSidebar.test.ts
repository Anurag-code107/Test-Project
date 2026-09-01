import { describe, it, expect } from "vitest";
import { derivePortalLabel } from "@/components/layout/sidebars/RoleSidebar";

function canWith(...granted: string[]): (key: string) => boolean {
  const set = new Set(granted);
  return (key) => set.has(key);
}

const FALLBACK = "tenXengage";

describe("derivePortalLabel", () => {
  it("returns 'Client Admin Portal' when the user can manage incentives", () => {
    const can = canWith("module.manage_incentives", "module.home");
    expect(derivePortalLabel(can, FALLBACK)).toBe("Client Admin Portal");
  });

  it("returns 'Approver Portal' for users with activity review/approve but no incentive management", () => {
    const can = canWith("action.activity.review");
    expect(derivePortalLabel(can, FALLBACK)).toBe("Approver Portal");

    const canApprove = canWith("action.activity.approve");
    expect(derivePortalLabel(canApprove, FALLBACK)).toBe("Approver Portal");
  });

  it("returns 'Partner Portal' when the user has partner-only rewards permissions", () => {
    const can = canWith("module.rewards.balances");
    expect(derivePortalLabel(can, FALLBACK)).toBe("Partner Portal");

    const canClaims = canWith("module.rewards.claims");
    expect(derivePortalLabel(canClaims, FALLBACK)).toBe("Partner Portal");
  });

  it("prefers 'Client Admin Portal' when a role happens to hold both manage-incentives and rewards perms", () => {
    const can = canWith("module.manage_incentives", "module.rewards.claims");
    expect(derivePortalLabel(can, FALLBACK)).toBe("Client Admin Portal");
  });

  it("prefers 'Approver Portal' over 'Partner Portal' when both are granted", () => {
    const can = canWith("action.activity.review", "module.rewards.balances");
    expect(derivePortalLabel(can, FALLBACK)).toBe("Approver Portal");
  });

  it("falls back to the provided label when no category matches", () => {
    const can = canWith("module.home", "module.settings.profile");
    expect(derivePortalLabel(can, FALLBACK)).toBe(FALLBACK);
  });

  it("falls back when the user has no permissions at all", () => {
    const can = canWith();
    expect(derivePortalLabel(can, FALLBACK)).toBe(FALLBACK);
  });
});
